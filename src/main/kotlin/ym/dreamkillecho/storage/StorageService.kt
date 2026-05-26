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
                loadOrCreateProfile(uuid, name).also { profiles[uuid] = it }
                loadOrCreateStats(uuid).also { loaded -> synchronized(statsLock) { stats[uuid] = loaded } }
                profiles[uuid]!!
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
                synchronized(statsLock) { stats.putIfAbsent(uuid, loaded) }
            }.copy()
        }, executor).exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to load stats for $uuid: ${throwable.message}")
            PlayerStats(uuid)
        }
    }

    fun markProfileDirty(uuid: UUID) {
        profile(uuid).updatedAt = System.currentTimeMillis()
        dirtyProfiles += uuid
    }

    fun markStatsDirty(uuid: UUID) {
        synchronized(statsLock) {
            stats.computeIfAbsent(uuid) { PlayerStats(uuid) }.updatedAt = System.currentTimeMillis()
            dirtyStats += uuid
        }
    }

    fun findProfileAsync(nameOrUuid: String): CompletableFuture<PlayerProfile?> {
        uuidFromStringOrNull(nameOrUuid)?.let { uuid ->
            return CompletableFuture.completedFuture(profile(uuid))
        }
        profiles.values.firstOrNull { it.name.equals(nameOrUuid, ignoreCase = true) }?.let {
            return CompletableFuture.completedFuture(it)
        }
        return CompletableFuture.supplyAsync({
            if (degraded) null else dataSource?.connection?.use { connection -> playerRepository.findByName(connection, nameOrUuid) }
        }, executor).thenApply { loaded ->
            loaded?.also { profiles[it.uuid] = it }
        }.exceptionally { throwable ->
            plugin.logger.warning("[DreamKillEcho] Failed to find player $nameOrUuid: ${throwable.message}")
            null
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
            val profileIds = dirtyProfiles.toList()
            val statCopies = synchronized(statsLock) {
                dirtyStats.toList().mapNotNull { uuid -> stats[uuid]?.copy() }
            }
            dataSource?.connection?.use { connection ->
                connection.autoCommit = false
                try {
                    for (uuid in profileIds) {
                        profiles[uuid]?.let { playerRepository.save(connection, it) }
                        dirtyProfiles -= uuid
                    }
                    for (copy in statCopies) {
                        statsRepository.save(connection, copy)
                        dirtyStats -= copy.uuid
                    }
                    connection.commit()
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

    fun topStats(type: String, limit: Int): CompletableFuture<List<PlayerStats>> {
        return CompletableFuture.supplyAsync({
            val column = if (type.equals("streak", true)) "max_streak" else "kills"
            if (degraded) {
                synchronized(statsLock) {
                    stats.values.map { it.copy() }
                        .sortedByDescending { if (column == "max_streak") it.maxStreak else it.kills }
                        .take(limit)
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
                    profiles[uuid] = loaded
                    dirtyProfiles += uuid
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
}
