package ym.dreamkillecho.storage

import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.config.StorageSettings
import ym.dreamkillecho.storage.datasource.DataSourceFactory
import ym.dreamkillecho.storage.migration.SchemaMigrator
import ym.dreamkillecho.storage.repository.KillLogRepository
import ym.dreamkillecho.storage.repository.PlayerRepository
import ym.dreamkillecho.storage.repository.StatsRepository
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    var degraded: Boolean = false
        private set

    fun start() {
        try {
            dataSource = DataSourceFactory.create(plugin, settings)
            dataSource!!.connection.use { connection ->
                SchemaMigrator.initialize(connection, settings.type)
            }
            plugin.logger.info("[DreamKillEcho] Storage started with ${settings.type}.")
        } catch (ex: Exception) {
            degraded = true
            plugin.logger.severe("[DreamKillEcho] Storage unavailable, degraded mode enabled: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun prepareOnlinePlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            preparePlayerAsync(player.uniqueId, player.name)
        }
    }

    fun preparePlayerAsync(uuid: UUID, name: String): CompletableFuture<PlayerProfile> {
        return CompletableFuture.supplyAsync({
            if (degraded) {
                profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }.also { it.name = name }
            } else {
                val loadedProfile = loadOrCreateProfile(uuid, name)
                val loadedStats = loadOrCreateStats(uuid)
                mergeProfile(uuid, loadedProfile)
                mergeStats(uuid, loadedStats)
                profiles[uuid] ?: loadedProfile
            }
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load player $name: ${throwable.message}")
            degraded = true
            profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }
        }
    }

    fun profile(uuid: UUID, name: String = uuid.toString()): PlayerProfile {
        return profiles.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }
    }

    fun stats(uuid: UUID): PlayerStats {
        return synchronized(statsLock) { stats.computeIfAbsent(uuid) { PlayerStats(uuid) }.copy() }
    }

    fun statsAsync(uuid: UUID): CompletableFuture<PlayerStats> {
        synchronized(statsLock) {
            stats[uuid]?.let { return CompletableFuture.completedFuture(it.copy()) }
        }
        return CompletableFuture.supplyAsync({
            if (degraded) {
                PlayerStats(uuid)
            } else {
                dataSource?.connection?.use { connection -> statsRepository.load(connection, uuid) }
                    ?: PlayerStats(uuid)
            }.also { loaded ->
                mergeStats(uuid, loaded)
            }.copy()
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load stats for $uuid: ${throwable.message}")
            PlayerStats(uuid)
        }
    }

    fun markProfileDirty(uuid: UUID) {
        synchronized(profileLock) {
            profile(uuid).updatedAt = System.currentTimeMillis()
            dirtyProfiles += uuid
        }
    }

    fun markStatsDirty(uuid: UUID) {
        synchronized(statsLock) {
            stats.computeIfAbsent(uuid) { PlayerStats(uuid) }.updatedAt = System.currentTimeMillis()
            dirtyStats += uuid
        }
    }

    fun findProfileAsync(nameOrUuid: String): CompletableFuture<PlayerProfile?> {
        uuidFromStringOrNull(nameOrUuid)?.let { uuid ->
            profiles[uuid]?.let { return CompletableFuture.completedFuture(it) }
            return CompletableFuture.supplyAsync({
                if (degraded) null else dataSource?.connection?.use { connection -> playerRepository.load(connection, uuid) }
            }, executor).thenApply { loaded ->
                loaded?.also { mergeProfile(it.uuid, it) }
                profiles[uuid]
            }.exceptionally { throwable ->
                plugin.logger.warning("[DreamKillEcho] Failed to find player $nameOrUuid: ${throwable.message}")
                null
            }
        }
        profiles.values.firstOrNull { it.name.equals(nameOrUuid, ignoreCase = true) }?.let {
            return CompletableFuture.completedFuture(it)
        }
        return CompletableFuture.supplyAsync({
            if (degraded) null else dataSource?.connection?.use { connection -> playerRepository.findByName(connection, nameOrUuid) }
        }, executor).thenApply { loaded ->
            loaded?.also { mergeProfile(it.uuid, it) }
            loaded?.uuid?.let { profiles[it] }
        }.exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to find player $nameOrUuid: ${throwable.message}")
            null
        }
    }

    fun pendingCustomMessagesAsync(): CompletableFuture<List<PlayerProfile>> {
        return CompletableFuture.supplyAsync({
            if (!degraded) {
                dataSource?.connection?.use { connection ->
                    playerRepository.pendingCustomMessages(connection).forEach { mergeProfile(it.uuid, it) }
                }
            }
            profiles.values
                .filter { it.customMessageStatus == CustomMessageStatus.PENDING }
                .sortedByDescending { it.customMessageUpdatedAt }
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load pending custom messages: ${throwable.message}")
            profiles.values
                .filter { it.customMessageStatus == CustomMessageStatus.PENDING }
                .sortedByDescending { it.customMessageUpdatedAt }
        }
    }

    fun recordDeath(victimUuid: UUID, killerUuid: UUID?, countStats: Boolean): StatsUpdateResult {
        return synchronized(statsLock) {
            val victimStats = stats.computeIfAbsent(victimUuid) { PlayerStats(victimUuid) }
            val previousVictimStreak = victimStats.currentStreak
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
        if (degraded) return
        CompletableFuture.runAsync({
            runCatching {
                dataSource?.connection?.use { connection ->
                    killLogRepository.insert(connection, log)
                }
            }.onFailure { plugin.logger.warning("[DreamKillEcho] Failed to write kill log: ${it.message}") }
        }, executor)
    }

    fun flushAsync(): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            if (degraded) return@runAsync
            val profileCopies = dirtyProfiles.toList().mapNotNull { uuid -> profiles[uuid]?.copy() }
            val statCopies = synchronized(statsLock) {
                dirtyStats.toList().mapNotNull { uuid -> stats[uuid]?.copy() }
            }
            dataSource?.connection?.use { connection ->
                connection.autoCommit = false
                try {
                    for (copy in profileCopies) {
                        playerRepository.save(connection, copy)
                    }
                    for (copy in statCopies) {
                        statsRepository.save(connection, copy)
                    }
                    connection.commit()
                    synchronized(profileLock) {
                        for (copy in profileCopies) {
                            val current = profiles[copy.uuid]
                            if (current != null && current.updatedAt == copy.updatedAt) {
                                dirtyProfiles -= copy.uuid
                            }
                        }
                    }
                    synchronized(statsLock) {
                        for (copy in statCopies) {
                            val current = stats[copy.uuid]
                            if (current != null && current.updatedAt == copy.updatedAt) {
                                dirtyStats -= copy.uuid
                            }
                        }
                    }
                } catch (ex: Exception) {
                    connection.rollback()
                    throw ex
                } finally {
                    connection.autoCommit = true
                }
            }
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Flush failed: ${throwable.message}")
            null
        }
    }

    fun topStats(type: String, limit: Int): CompletableFuture<List<LeaderboardRow>> {
        return CompletableFuture.supplyAsync({
            val column = if (type.equals("streak", true)) "max_streak" else "kills"
            if (degraded) {
                synchronized(statsLock) {
                    stats.values.map { it.copy() }
                        .sortedByDescending { if (column == "max_streak") it.maxStreak else it.kills }
                        .take(limit)
                        .map { row ->
                            LeaderboardRow(
                                uuid = row.uuid,
                                name = profiles[row.uuid]?.name ?: row.uuid.toString(),
                                kills = row.kills,
                                deaths = row.deaths,
                                currentStreak = row.currentStreak,
                                maxStreak = row.maxStreak
                            )
                        }
                }
            } else {
                dataSource?.connection?.use { connection ->
                    statsRepository.top(connection, column, limit)
                } ?: emptyList()
            }
        }, executor)
    }

    fun resetStats(uuid: UUID) {
        synchronized(statsLock) {
            stats[uuid] = PlayerStats(uuid)
            dirtyStats += uuid
        }
    }

    fun shutdown() {
        runCatching { flushAsync().get(5, TimeUnit.SECONDS) }
            .onFailure { plugin.logger.warning("[DreamKillEcho] Timed out while flushing storage on shutdown: ${it.message}") }
        dataSource?.close()
        executor.shutdown()
    }

    private fun loadOrCreateProfile(uuid: UUID, name: String): PlayerProfile {
        dataSource!!.connection.use { connection ->
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
    }

    private fun loadOrCreateStats(uuid: UUID): PlayerStats {
        dataSource!!.connection.use { connection ->
            statsRepository.load(connection, uuid)?.let { return it }
            return PlayerStats(uuid).also { statsRepository.save(connection, it) }
        }
    }

    fun cachedProfiles(): Collection<PlayerProfile> = profiles.values

    private fun uuidFromStringOrNull(value: String): UUID? {
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private fun mergeProfile(uuid: UUID, loaded: PlayerProfile) {
        synchronized(profileLock) {
            val current = profiles[uuid]
            if (uuid in dirtyProfiles || (current != null && current.updatedAt >= loaded.updatedAt)) {
                return
            }
            profiles[uuid] = loaded
        }
    }

    private fun mergeStats(uuid: UUID, loaded: PlayerStats) {
        synchronized(statsLock) {
            val current = stats[uuid]
            if (uuid in dirtyStats || (current != null && current.updatedAt >= loaded.updatedAt)) {
                return
            }
            stats[uuid] = loaded
        }
    }
}
