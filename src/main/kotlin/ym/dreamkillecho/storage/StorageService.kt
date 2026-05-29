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
    private val loadedProfiles = ConcurrentHashMap.newKeySet<UUID>()
    private val loadedStats = ConcurrentHashMap.newKeySet<UUID>()
    private val pendingProfileUpdates = HashMap<UUID, MutableList<(PlayerProfile) -> Unit>>()
    private val profileLock = Any()
    private val statsLock = Any()
    private val playerRepository = PlayerRepository()
    private val statsRepository = StatsRepository()
    private val killLogRepository = KillLogRepository()
    private var dataSource: HikariDataSource? = null
    @Volatile
    private var shuttingDown: Boolean = false
    @Volatile
    private var lastStorageWarningAt: Long = 0L

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
                loadedProfiles += uuid
                this.loadedStats += uuid
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
            if (uuid !in loadedProfiles) {
                pendingProfileUpdates.computeIfAbsent(uuid) { mutableListOf() } += update
            }
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
            stats[uuid]?.let {
                if (uuid in loadedStats || uuid in dirtyStats) {
                    return CompletableFuture.completedFuture(it.copy())
                }
            }
        }
        return CompletableFuture.supplyAsync({
            val loaded = openConnectionOrNull()?.use { connection ->
                (statsRepository.load(connection, uuid) ?: PlayerStats(uuid).also { statsRepository.save(connection, it) })
            } ?: return@supplyAsync stats(uuid)
            mergeStats(uuid, loaded)
            loadedStats += uuid
            loaded.copy()
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load stats for $uuid: ${throwable.message}")
            stats(uuid)
        }
    }

    fun markStatsDirty(uuid: UUID) {
        synchronized(statsLock) {
            val value = stats[uuid] ?: return
            value.updatedAt = System.currentTimeMillis()
            dirtyStats += uuid
        }
    }

    fun findProfileAsync(nameOrUuid: String): CompletableFuture<PlayerProfile?> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(null)
        }
        uuidFromStringOrNull(nameOrUuid)?.let { uuid ->
            synchronized(profileLock) {
                profiles[uuid]?.copy()?.let {
                    if (uuid in loadedProfiles || uuid in dirtyProfiles) {
                        return CompletableFuture.completedFuture(it)
                    }
                }
            }
            return CompletableFuture.supplyAsync({
                openConnectionOrNull()?.use { connection -> playerRepository.load(connection, uuid) }
            }, executor).thenApply { loaded ->
                loaded?.also {
                    mergeProfile(it.uuid, it)
                    loadedProfiles += it.uuid
                }
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
            loaded?.also {
                mergeProfile(it.uuid, it)
                loadedProfiles += it.uuid
            }
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
                playerRepository.pendingCustomMessages(connection).forEach {
                    mergeProfile(it.uuid, it)
                    loadedProfiles += it.uuid
                }
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

    fun recordDeathAsync(victimUuid: UUID, killerUuid: UUID?, countStats: Boolean): CompletableFuture<StatsUpdateResult> {
        if (shuttingDown || executor.isShutdown) {
            return CompletableFuture.completedFuture(StatsUpdateResult(stats(victimUuid).currentStreak, 0, 0, stats(victimUuid).maxStreak))
        }
        return CompletableFuture.supplyAsync({
            if (!ensureStatsLoaded(victimUuid) || (killerUuid != null && !ensureStatsLoaded(killerUuid))) {
                return@supplyAsync StatsUpdateResult(stats(victimUuid).currentStreak, 0, 0, stats(victimUuid).maxStreak)
            }
            recordDeathLoaded(victimUuid, killerUuid, countStats)
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to record death stats safely: ${throwable.message}")
            StatsUpdateResult(stats(victimUuid).currentStreak, 0, 0, stats(victimUuid).maxStreak)
        }
    }

    private fun recordDeathLoaded(victimUuid: UUID, killerUuid: UUID?, countStats: Boolean): StatsUpdateResult {
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
        val profileIds = synchronized(profileLock) { dirtyProfiles.toList() }
        val statIds = synchronized(statsLock) { dirtyStats.toList() }
        if (profileIds.isEmpty() && statIds.isEmpty()) return
        val connection = openConnectionOrNull() ?: return
        connection.use {
            it.autoCommit = false
            try {
                val profileCopies = profileIds.mapNotNull { uuid -> profileForFlush(it, uuid) }
                val statCopies = statIds.mapNotNull { uuid -> statForFlush(it, uuid) }
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
            val safeLimit = limit.coerceIn(1, 100)
            val dbRows = openConnectionOrNull()?.use { connection ->
                statsRepository.top(connection, column, safeLimit)
            } ?: synchronized(statsLock) {
                stats.values.map { it.copy() }
                    .sortedByDescending { if (column == "max_streak") it.maxStreak else it.kills }
                    .take(safeLimit)
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
            mergeDirtyLeaderboard(dbRows, column, safeLimit)
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
        val timeoutSeconds = settings.shutdownTimeoutSeconds.coerceAtLeast(1)
        plugin.logger.info(
            "[DreamKillEcho] Saving storage data before shutdown (dirtyProfiles=${dirtyProfiles.size}, dirtyStats=${dirtyStats.size}, timeout=${timeoutSeconds}s)..."
        )
        val flushFuture = try {
            flushAsyncInternal(force = true)
        } catch (ex: Exception) {
            plugin.logger.warning("[DreamKillEcho] Failed to submit final storage flush: ${ex.message}")
            null
        }
        runCatching { flushFuture?.get(timeoutSeconds, TimeUnit.SECONDS) }
            .onFailure { plugin.logger.warning("[DreamKillEcho] Timed out while flushing storage on shutdown: ${it.message}") }
        executor.shutdown()
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        dataSource?.close()
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

    private fun ensureStatsLoaded(uuid: UUID): Boolean {
        if (uuid in loadedStats) return true
        val loaded = openConnectionOrNull()?.use { connection -> loadOrCreateStats(connection, uuid) }
            ?: run {
                synchronized(statsLock) {
                    stats.computeIfAbsent(uuid) { PlayerStats(uuid) }
                }
                return true
            }
        mergeStats(uuid, loaded)
        loadedStats += uuid
        return true
    }

    private fun mergeDirtyLeaderboard(rows: List<LeaderboardRow>, column: String, limit: Int): List<LeaderboardRow> {
        val byUuid = linkedMapOf<UUID, LeaderboardRow>()
        rows.forEach { byUuid[it.uuid] = it }
        val dirtyCopies = synchronized(statsLock) {
            dirtyStats.toList().mapNotNull { uuid -> stats[uuid]?.copy() }
        }
        for (stat in dirtyCopies) {
            byUuid[stat.uuid] = LeaderboardRow(
                uuid = stat.uuid,
                name = synchronized(profileLock) { profiles[stat.uuid]?.name } ?: rows.firstOrNull { it.uuid == stat.uuid }?.name ?: stat.uuid.toString(),
                kills = stat.kills,
                deaths = stat.deaths,
                currentStreak = stat.currentStreak,
                maxStreak = stat.maxStreak
            )
        }
        return byUuid.values
            .sortedWith(
                compareByDescending<LeaderboardRow> { if (column == "max_streak") it.maxStreak else it.kills }
                    .thenBy { it.name.lowercase() }
            )
            .take(limit)
    }

    private fun openConnectionOrNull(): Connection? {
        if (!ensureDataSource()) return null
        return try {
            dataSource?.connection?.also { degraded = false }
        } catch (ex: Exception) {
            degraded = true
            warnStorageUnavailable("[DreamKillEcho] Storage connection unavailable, will retry later: ${ex.message}")
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
            warnStorageUnavailable("[DreamKillEcho] Storage unavailable, degraded mode active: ${ex.message}")
            false
        }
    }

    private fun warnStorageUnavailable(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastStorageWarningAt < 30_000L) return
        lastStorageWarningAt = now
        plugin.logger.warning(message)
    }

    private fun uuidFromStringOrNull(value: String): UUID? {
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private fun mergeProfile(uuid: UUID, loaded: PlayerProfile) {
        synchronized(profileLock) {
            val current = profiles[uuid]
            if (uuid in dirtyProfiles && uuid !in loadedProfiles) {
                val merged = loaded.copy()
                pendingProfileUpdates.remove(uuid).orEmpty().forEach { it(merged) }
                merged.updatedAt = System.currentTimeMillis()
                profiles[uuid] = merged
                loadedProfiles += uuid
                dirtyProfiles += uuid
                return
            }
            if (uuid in dirtyProfiles || (uuid in loadedProfiles && current != null && current.updatedAt >= loaded.updatedAt)) return
            profiles[uuid] = loaded
        }
    }

    private fun profileForFlush(connection: Connection, uuid: UUID): PlayerProfile? {
        val cached = synchronized(profileLock) { profiles[uuid]?.copy() } ?: return null
        if (uuid in loadedProfiles) return cached
        val loaded = playerRepository.load(connection, uuid)
        val merged = loaded?.copy() ?: cached
        synchronized(profileLock) {
            val current = profiles[uuid] ?: cached
            if (loaded != null) {
                pendingProfileUpdates.remove(uuid).orEmpty().forEach { it(merged) }
                if (current.name.isNotBlank() && current.name != uuid.toString()) {
                    merged.name = current.name
                }
                merged.updatedAt = current.updatedAt.coerceAtLeast(System.currentTimeMillis())
            } else {
                pendingProfileUpdates.remove(uuid)
            }
            profiles[uuid] = merged
            loadedProfiles += uuid
            return merged.copy()
        }
    }

    private fun mergeStats(uuid: UUID, loaded: PlayerStats) {
        synchronized(statsLock) {
            val current = stats[uuid]
            if (uuid in dirtyStats || (uuid in loadedStats && current != null && current.updatedAt >= loaded.updatedAt)) return
            stats[uuid] = loaded
        }
    }

    private fun statForFlush(connection: Connection, uuid: UUID): PlayerStats? {
        val cached = synchronized(statsLock) { stats[uuid]?.copy() } ?: return null
        if (uuid in loadedStats) return cached
        val loaded = statsRepository.load(connection, uuid)
        val merged = if (loaded != null) mergeUnloadedStats(loaded, cached) else cached
        synchronized(statsLock) {
            stats[uuid] = merged
            loadedStats += uuid
            return merged.copy()
        }
    }

    private fun mergeUnloadedStats(loaded: PlayerStats, cached: PlayerStats): PlayerStats {
        val merged = loaded.copy()
        merged.kills += cached.kills
        merged.deaths += cached.deaths
        merged.currentStreak = if (cached.deaths > 0) {
            cached.currentStreak
        } else {
            merged.currentStreak + cached.currentStreak
        }
        merged.maxStreak = maxOf(merged.maxStreak, merged.currentStreak, cached.maxStreak)
        if (cached.lastKillTime > 0L) {
            merged.lastVictimUuid = cached.lastVictimUuid
            merged.lastKillTime = cached.lastKillTime
        }
        merged.updatedAt = maxOf(merged.updatedAt, cached.updatedAt)
        return merged
    }
}
