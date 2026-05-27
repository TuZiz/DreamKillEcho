package ym.dreamkillecho.storage

import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.config.StorageSettings
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.storage.datasource.DataSourceFactory
import ym.dreamkillecho.storage.migration.SchemaMigrator
import ym.dreamkillecho.storage.repository.KillLogRepository
import ym.dreamkillecho.storage.repository.PlayerRepository
import ym.dreamkillecho.storage.repository.StatsRepository
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class StorageService(private val plugin: JavaPlugin, private val settings: StorageSettings) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DreamKillEcho-Storage").apply { isDaemon = true }
    }
    private val profiles = ConcurrentHashMap<UUID, PlayerProfile>()
    private val stats = ConcurrentHashMap<UUID, PlayerStats>()
    private val dirtyProfiles = ConcurrentHashMap.newKeySet<UUID>()
    private val dirtyStats = ConcurrentHashMap.newKeySet<UUID>()
    private val profileLock = Any()
    private val statsLock = Any()
    private val playerRepository = PlayerRepository()
    private val statsRepository = StatsRepository()
    private val killLogRepository = KillLogRepository()
    private var dataSource: HikariDataSource? = null
    @Volatile
    private var shuttingDown: Boolean = false

    @Volatile
    var degraded: Boolean = false
        private set

    fun start() {
        if (!ensureDataSource()) {
            plugin.logger.severe("[DreamKillEcho] Storage unavailable, degraded mode enabled.")
        }
    }

    fun prepareOnlinePlayers(scheduler: SchedulerAdapter) {
        for (player in Bukkit.getOnlinePlayers()) {
            scheduler.runEntity(player) {
                preparePlayerAsync(player.uniqueId, player.name)
            }
        }
    }

    fun preparePlayerAsync(uuid: UUID, name: String): CompletableFuture<PlayerProfile> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(profile(uuid, name))
        }
        return CompletableFuture.supplyAsync({
            openConnectionOrNull()?.use { connection ->
                val loadedProfile = loadOrCreateProfile(connection, uuid, name)
                val loadedStats = loadOrCreateStats(connection, uuid)
                mergeProfile(uuid, loadedProfile)
                mergeStats(uuid, loadedStats)
                return@supplyAsync profiles[uuid]?.copy() ?: loadedProfile
            }
            synchronized(profileLock) {
                profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }.copy()
            }
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load player $name: ${throwable.message}")
            synchronized(profileLock) {
                profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }.copy()
            }
        }
    }

    fun profile(uuid: UUID, name: String = uuid.toString()): PlayerProfile {
        return synchronized(profileLock) {
            profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }.copy()
        }
    }

    fun updateProfile(uuid: UUID, name: String = uuid.toString(), update: (PlayerProfile) -> Unit): PlayerProfile {
        return synchronized(profileLock) {
            val profile = profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }
            if (name.isNotBlank() && profile.name != name) profile.name = name
            update(profile)
            profile.updatedAt = System.currentTimeMillis()
            dirtyProfiles += uuid
            profile.copy()
        }
    }

    fun markProfileDirty(uuid: UUID) {
        synchronized(profileLock) {
            val profile = profiles[uuid] ?: return
            profile.updatedAt = System.currentTimeMillis()
            dirtyProfiles += uuid
        }
    }

    fun stats(uuid: UUID): PlayerStats {
        return synchronized(statsLock) { stats.computeIfAbsent(uuid) { PlayerStats(uuid) }.copy() }
    }

    fun statsAsync(uuid: UUID): CompletableFuture<PlayerStats> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(stats(uuid))
        }
        synchronized(statsLock) {
            stats[uuid]?.let { return CompletableFuture.completedFuture(it.copy()) }
        }
        return CompletableFuture.supplyAsync({
            val loaded = openConnectionOrNull()?.use { connection -> statsRepository.load(connection, uuid) }
                ?: PlayerStats(uuid)
            mergeStats(uuid, loaded)
            loaded.copy()
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load stats for $uuid: ${throwable.message}")
            PlayerStats(uuid)
        }
    }

    fun markStatsDirty(uuid: UUID) {
        synchronized(statsLock) {
            stats.computeIfAbsent(uuid) { PlayerStats(uuid) }.updatedAt = System.currentTimeMillis()
            dirtyStats += uuid
        }
    }

    fun findProfileAsync(nameOrUuid: String): CompletableFuture<PlayerProfile?> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(null)
        }
        uuidFromStringOrNull(nameOrUuid)?.let { uuid ->
            synchronized(profileLock) {
                profiles[uuid]?.copy()?.let { return CompletableFuture.completedFuture(it) }
            }
            return CompletableFuture.supplyAsync({
                openConnectionOrNull()?.use { connection -> playerRepository.load(connection, uuid) }
            }, executor).thenApply { loaded ->
                loaded?.also { mergeProfile(it.uuid, it) }
                synchronized(profileLock) { profiles[uuid]?.copy() }
            }.exceptionally { throwable ->
                plugin.logger.warning("[DreamKillEcho] Failed to find player $nameOrUuid: ${throwable.message}")
                null
            }
        }
        synchronized(profileLock) {
            profiles.values.firstOrNull { it.name.equals(nameOrUuid, ignoreCase = true) }?.copy()?.let {
                return CompletableFuture.completedFuture(it)
            }
        }
        return CompletableFuture.supplyAsync({
            openConnectionOrNull()?.use { connection -> playerRepository.findByName(connection, nameOrUuid) }
        }, executor).thenApply { loaded ->
            loaded?.also { mergeProfile(it.uuid, it) }
            loaded?.uuid?.let { uuid -> synchronized(profileLock) { profiles[uuid]?.copy() } }
        }.exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to find player $nameOrUuid: ${throwable.message}")
            null
        }
    }

    fun pendingCustomMessagesAsync(): CompletableFuture<List<PlayerProfile>> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(
                cachedProfiles()
                    .filter { it.customMessageStatus == CustomMessageStatus.PENDING }
                    .sortedByDescending { it.customMessageUpdatedAt }
            )
        }
        return CompletableFuture.supplyAsync({
            openConnectionOrNull()?.use { connection ->
                playerRepository.pendingCustomMessages(connection).forEach { mergeProfile(it.uuid, it) }
            }
            cachedProfiles()
                .filter { it.customMessageStatus == CustomMessageStatus.PENDING }
                .sortedByDescending { it.customMessageUpdatedAt }
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load pending custom messages: ${throwable.message}")
            cachedProfiles()
                .filter { it.customMessageStatus == CustomMessageStatus.PENDING }
                .sortedByDescending { it.customMessageUpdatedAt }
        }
    }

    fun recordDeath(victimUuid: UUID, killerUuid: UUID?, countStats: Boolean): StatsUpdateResult {
        return synchronized(statsLock) {
            val victimStats = stats.computeIfAbsent(victimUuid) { PlayerStats(victimUuid) }
            val previousVictimStreak = victimStats.currentStreak
            if (!countStats) {
                return@synchronized StatsUpdateResult(previousVictimStreak, 0, 0, victimStats.maxStreak)
            }
            victimStats.deaths += 1
            victimStats.currentStreak = 0
            victimStats.updatedAt = System.currentTimeMillis()
            dirtyStats += victimUuid

            var killerStreak = 0
            var killerMaxStreak = 0
            if (killerUuid != null && countStats) {
                val killerStats = stats.computeIfAbsent(killerUuid) { PlayerStats(killerUuid) }
                killerStats.kills += 1
                killerStats.currentStreak += 1
                killerStats.maxStreak = killerStats.maxStreak.coerceAtLeast(killerStats.currentStreak)
                killerStats.lastVictimUuid = victimUuid
                killerStats.lastKillTime = System.currentTimeMillis()
                killerStats.updatedAt = System.currentTimeMillis()
                dirtyStats += killerUuid
                killerStreak = killerStats.currentStreak
                killerMaxStreak = killerStats.maxStreak
            }
            StatsUpdateResult(previousVictimStreak, killerStreak, killerMaxStreak, victimStats.maxStreak)
        }
    }

    fun logKill(log: KillLog) {
        if (shuttingDown || executor.isShutdown) return
        CompletableFuture.runAsync({
            runCatching {
                openConnectionOrNull()?.use { connection ->
                    killLogRepository.insert(connection, log)
                }
            }.onFailure { plugin.logger.warning("[DreamKillEcho] Failed to write kill log: ${it.message}") }
        }, executor)
    }

    fun flushAsync(): CompletableFuture<Void> {
        return flushAsyncInternal(force = false)
    }

    private fun flushAsyncInternal(force: Boolean): CompletableFuture<Void> {
        if (!force && (shuttingDown || executor.isShutdown)) {
            return CompletableFuture.completedFuture(null)
        }
        val future = try {
            CompletableFuture.runAsync({ flushDirty() }, executor)
        } catch (ex: RejectedExecutionException) {
            if (!force) {
                plugin.logger.warning("[DreamKillEcho] Flush skipped because storage executor is shutting down.")
                return CompletableFuture.completedFuture(null)
            }
            throw ex
        }
        return future.exceptionally { throwable ->
            degraded = true
            plugin.logger.warning("[DreamKillEcho] Flush failed, dirty data kept for retry: ${throwable.message}")
            null
        }
    }

    private fun flushDirty() {
        val profileCopies = synchronized(profileLock) {
            dirtyProfiles.toList().mapNotNull { uuid -> profiles[uuid]?.copy() }
        }
        val statCopies = synchronized(statsLock) {
            dirtyStats.toList().mapNotNull { uuid -> stats[uuid]?.copy() }
        }
        if (profileCopies.isEmpty() && statCopies.isEmpty()) return
        val connection = openConnectionOrNull() ?: return
        connection.use {
            it.autoCommit = false
            try {
                for (copy in profileCopies) playerRepository.save(it, copy)
                for (copy in statCopies) statsRepository.save(it, copy)
                it.commit()
                synchronized(profileLock) {
                    for (copy in profileCopies) {
                        val current = profiles[copy.uuid]
                        if (current != null && current.updatedAt == copy.updatedAt) dirtyProfiles -= copy.uuid
                    }
                }
                synchronized(statsLock) {
                    for (copy in statCopies) {
                        val current = stats[copy.uuid]
                        if (current != null && current.updatedAt == copy.updatedAt) dirtyStats -= copy.uuid
                    }
                }
            } catch (ex: Exception) {
                it.rollback()
                throw ex
            } finally {
                it.autoCommit = true
            }
        }
    }

    fun topStats(type: String, limit: Int): CompletableFuture<List<LeaderboardRow>> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(emptyList())
        }
        return CompletableFuture.supplyAsync({
            val column = if (type.equals("streak", true)) "max_streak" else "kills"
            openConnectionOrNull()?.use { connection ->
                statsRepository.top(connection, column, limit)
            } ?: synchronized(statsLock) {
                stats.values.map { it.copy() }
                    .sortedByDescending { if (column == "max_streak") it.maxStreak else it.kills }
                    .take(limit)
                    .map { row ->
                        LeaderboardRow(
                            uuid = row.uuid,
                            name = synchronized(profileLock) { profiles[row.uuid]?.name } ?: row.uuid.toString(),
                            kills = row.kills,
                            deaths = row.deaths,
                            currentStreak = row.currentStreak,
                            maxStreak = row.maxStreak
                        )
                    }
            }
        }, executor)
    }

    fun resetStats(uuid: UUID) {
        synchronized(statsLock) {
            stats[uuid] = PlayerStats(uuid).also { it.updatedAt = System.currentTimeMillis() }
            dirtyStats += uuid
        }
    }

    fun shutdown() {
        if (shuttingDown) return
        shuttingDown = true
        plugin.logger.info("[DreamKillEcho] Saving storage data before shutdown (up to 5s)...")
        val flushFuture = try {
            flushAsyncInternal(force = true)
        } catch (ex: Exception) {
            plugin.logger.warning("[DreamKillEcho] Failed to submit final storage flush: ${ex.message}")
            null
        }
        runCatching { flushFuture?.get(5, TimeUnit.SECONDS) }
            .onFailure { plugin.logger.warning("[DreamKillEcho] Timed out while flushing storage on shutdown: ${it.message}") }
        dataSource?.close()
        executor.shutdown()
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }

    fun cachedProfiles(): Collection<PlayerProfile> {
        return synchronized(profileLock) { profiles.values.map { it.copy() } }
    }

    private fun loadOrCreateProfile(connection: Connection, uuid: UUID, name: String): PlayerProfile {
        playerRepository.load(connection, uuid)?.let { loaded ->
            if (loaded.name != name) {
                loaded.name = name
                loaded.updatedAt = System.currentTimeMillis()
                playerRepository.save(connection, loaded)
            }
            return loaded
        }
        return PlayerProfile(uuid, name).also { playerRepository.save(connection, it) }
    }

    private fun loadOrCreateStats(connection: Connection, uuid: UUID): PlayerStats {
        statsRepository.load(connection, uuid)?.let { return it }
        return PlayerStats(uuid).also { statsRepository.save(connection, it) }
    }

    private fun openConnectionOrNull(): Connection? {
        if (!ensureDataSource()) return null
        return try {
            dataSource?.connection?.also { degraded = false }
        } catch (ex: Exception) {
            degraded = true
            plugin.logger.warning("[DreamKillEcho] Storage connection unavailable, will retry later: ${ex.message}")
            null
        }
    }

    private fun ensureDataSource(): Boolean {
        val existing = dataSource
        if (existing != null && !existing.isClosed) return true
        var created: HikariDataSource? = null
        return try {
            created = DataSourceFactory.create(plugin, settings)
            created.connection.use { connection ->
                SchemaMigrator.initialize(connection, settings.type)
            }
            dataSource = created
            created = null
            degraded = false
            plugin.logger.info("[DreamKillEcho] Storage connected with ${settings.type}.")
            true
        } catch (ex: Exception) {
            runCatching { created?.close() }
            degraded = true
            plugin.logger.warning("[DreamKillEcho] Storage unavailable, degraded mode active: ${ex.message}")
            false
        }
    }

    private fun uuidFromStringOrNull(value: String): UUID? {
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private fun mergeProfile(uuid: UUID, loaded: PlayerProfile) {
        synchronized(profileLock) {
            val current = profiles[uuid]
            if (uuid in dirtyProfiles || (current != null && current.updatedAt >= loaded.updatedAt)) return
            profiles[uuid] = loaded
        }
    }

    private fun mergeStats(uuid: UUID, loaded: PlayerStats) {
        synchronized(statsLock) {
            val current = stats[uuid]
            if (uuid in dirtyStats || (current != null && current.updatedAt >= loaded.updatedAt)) return
            stats[uuid] = loaded
        }
    }
}
