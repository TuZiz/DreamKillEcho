package ym.dreamkillecho.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.config.StorageSettings
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class StorageService(private val plugin: JavaPlugin, private val settings: StorageSettings) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DreamKillEcho-Storage").apply { isDaemon = true }
    }
    private val profiles = ConcurrentHashMap<UUID, PlayerProfile>()
    private val stats = ConcurrentHashMap<UUID, PlayerStats>()
    private val dirtyProfiles = ConcurrentHashMap.newKeySet<UUID>()
    private val dirtyStats = ConcurrentHashMap.newKeySet<UUID>()
    private var dataSource: HikariDataSource? = null

    @Volatile
    var degraded: Boolean = false
        private set

    fun start() {
        try {
            dataSource = createDataSource()
            dataSource!!.connection.use { connection ->
                createSchema(connection)
                applySchemaVersion(connection, 1)
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
                loadOrCreateStats(uuid).also { stats[uuid] = it }
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
        return stats.computeIfAbsent(uuid) { PlayerStats(uuid) }
    }

    fun markProfileDirty(uuid: UUID) {
        profile(uuid).updatedAt = System.currentTimeMillis()
        dirtyProfiles += uuid
    }

    fun markStatsDirty(uuid: UUID) {
        stats(uuid).updatedAt = System.currentTimeMillis()
        dirtyStats += uuid
    }

    fun logKill(log: KillLog) {
        if (degraded) return
        CompletableFuture.runAsync({
            runCatching {
                dataSource?.connection?.use { connection ->
                    connection.prepareStatement(
                        "INSERT INTO kill_logs(killer_uuid,victim_uuid,weapon,world,death_cause,distance,created_at) VALUES(?,?,?,?,?,?,?)"
                    ).use { ps ->
                        ps.setString(1, log.killerUuid?.toString())
                        ps.setString(2, log.victimUuid.toString())
                        ps.setString(3, log.weapon)
                        ps.setString(4, log.world)
                        ps.setString(5, log.deathCause)
                        ps.setDouble(6, log.distance)
                        ps.setLong(7, log.createdAt)
                        ps.executeUpdate()
                    }
                }
            }.onFailure { plugin.logger.warning("[DreamKillEcho] Failed to write kill log: ${it.message}") }
        }, executor)
    }

    fun flushAsync(): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            if (degraded) return@runAsync
            val profileIds = dirtyProfiles.toList()
            val statIds = dirtyStats.toList()
            dataSource?.connection?.use { connection ->
                connection.autoCommit = false
                try {
                    for (uuid in profileIds) {
                        profiles[uuid]?.let { saveProfile(connection, it) }
                        dirtyProfiles -= uuid
                    }
                    for (uuid in statIds) {
                        stats[uuid]?.let { saveStats(connection, it) }
                        dirtyStats -= uuid
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
                stats.values.sortedByDescending { if (column == "max_streak") it.maxStreak else it.kills }.take(limit)
            } else {
                dataSource?.connection?.use { connection ->
                    connection.prepareStatement("SELECT * FROM stats ORDER BY $column DESC LIMIT ?").use { ps ->
                        ps.setInt(1, limit)
                        ps.executeQuery().use { rs ->
                            val result = mutableListOf<PlayerStats>()
                            while (rs.next()) result += readStats(rs)
                            result
                        }
                    }
                } ?: emptyList()
            }
        }, executor)
    }

    fun resetStats(uuid: UUID) {
        stats[uuid] = PlayerStats(uuid)
        markStatsDirty(uuid)
    }

    fun shutdown() {
        flushAsync().join()
        dataSource?.close()
        executor.shutdown()
    }

    private fun createDataSource(): HikariDataSource {
        val config = HikariConfig()
        when (settings.type) {
            "mysql" -> {
                Class.forName("com.mysql.cj.jdbc.Driver")
                config.jdbcUrl = "jdbc:mysql://${settings.mysql.host}:${settings.mysql.port}/${settings.mysql.database}?useSSL=false&characterEncoding=utf8&serverTimezone=UTC"
                config.username = settings.mysql.username
                config.password = settings.mysql.password
                config.maximumPoolSize = settings.mysql.maximumPoolSize
                config.minimumIdle = settings.mysql.minimumIdle
                config.connectionTimeout = settings.mysql.connectionTimeout
            }
            else -> {
                Class.forName("org.sqlite.JDBC")
                val dbFile = File(plugin.dataFolder, settings.sqliteFile)
                dbFile.parentFile?.mkdirs()
                config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                config.maximumPoolSize = 1
            }
        }
        config.poolName = "DreamKillEchoPool"
        return HikariDataSource(config)
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players(uuid VARCHAR(36) PRIMARY KEY,name VARCHAR(32) NOT NULL,selected_theme VARCHAR(64) NOT NULL,custom_message TEXT,custom_message_status VARCHAR(16) NOT NULL,custom_message_updated_at BIGINT NOT NULL,receive_broadcast BOOLEAN NOT NULL,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stats(uuid VARCHAR(36) PRIMARY KEY,kills INTEGER NOT NULL,deaths INTEGER NOT NULL,current_streak INTEGER NOT NULL,max_streak INTEGER NOT NULL,last_victim_uuid VARCHAR(36),last_kill_time BIGINT NOT NULL,updated_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kill_logs(id INTEGER PRIMARY KEY ${if (settings.type == "mysql") "AUTO_INCREMENT" else "AUTOINCREMENT"},killer_uuid VARCHAR(36),victim_uuid VARCHAR(36) NOT NULL,weapon VARCHAR(128) NOT NULL,world VARCHAR(128) NOT NULL,death_cause VARCHAR(64) NOT NULL,distance DOUBLE NOT NULL,created_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER PRIMARY KEY,applied_at BIGINT NOT NULL)")
        }
    }

    private fun applySchemaVersion(connection: Connection, version: Int) {
        val sql = if (settings.type == "mysql") {
            "INSERT IGNORE INTO schema_version(version,applied_at) VALUES(?,?)"
        } else {
            "INSERT OR IGNORE INTO schema_version(version,applied_at) VALUES(?,?)"
        }
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, version)
            ps.setLong(2, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    private fun loadOrCreateProfile(uuid: UUID, name: String): PlayerProfile {
        dataSource!!.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM players WHERE uuid=?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    if (rs.next()) return readProfile(rs).also {
                        if (it.name != name) {
                            it.name = name
                            profiles[uuid] = it
                            dirtyProfiles += uuid
                        }
                    }
                }
            }
            return PlayerProfile(uuid, name).also { saveProfile(connection, it) }
        }
    }

    private fun loadOrCreateStats(uuid: UUID): PlayerStats {
        dataSource!!.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM stats WHERE uuid=?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs -> if (rs.next()) return readStats(rs) }
            }
            return PlayerStats(uuid).also { saveStats(connection, it) }
        }
    }

    private fun saveProfile(connection: Connection, profile: PlayerProfile) {
        connection.prepareStatement("REPLACE INTO players(uuid,name,selected_theme,custom_message,custom_message_status,custom_message_updated_at,receive_broadcast,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)").use { ps ->
            ps.setString(1, profile.uuid.toString())
            ps.setString(2, profile.name)
            ps.setString(3, profile.selectedTheme)
            ps.setString(4, profile.customMessage)
            ps.setString(5, profile.customMessageStatus.name)
            ps.setLong(6, profile.customMessageUpdatedAt)
            ps.setBoolean(7, profile.receiveBroadcast)
            ps.setLong(8, profile.createdAt)
            ps.setLong(9, profile.updatedAt)
            ps.executeUpdate()
        }
    }

    private fun saveStats(connection: Connection, value: PlayerStats) {
        connection.prepareStatement("REPLACE INTO stats(uuid,kills,deaths,current_streak,max_streak,last_victim_uuid,last_kill_time,updated_at) VALUES(?,?,?,?,?,?,?,?)").use { ps ->
            ps.setString(1, value.uuid.toString())
            ps.setInt(2, value.kills)
            ps.setInt(3, value.deaths)
            ps.setInt(4, value.currentStreak)
            ps.setInt(5, value.maxStreak)
            ps.setString(6, value.lastVictimUuid?.toString())
            ps.setLong(7, value.lastKillTime)
            ps.setLong(8, value.updatedAt)
            ps.executeUpdate()
        }
    }

    private fun readProfile(rs: ResultSet): PlayerProfile {
        return PlayerProfile(
            uuid = UUID.fromString(rs.getString("uuid")),
            name = rs.getString("name"),
            selectedTheme = rs.getString("selected_theme"),
            customMessage = rs.getString("custom_message"),
            customMessageStatus = runCatching { CustomMessageStatus.valueOf(rs.getString("custom_message_status")) }.getOrDefault(CustomMessageStatus.NONE),
            customMessageUpdatedAt = rs.getLong("custom_message_updated_at"),
            receiveBroadcast = rs.getBoolean("receive_broadcast"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    private fun readStats(rs: ResultSet): PlayerStats {
        val lastVictim = rs.getString("last_victim_uuid")
        return PlayerStats(
            uuid = UUID.fromString(rs.getString("uuid")),
            kills = rs.getInt("kills"),
            deaths = rs.getInt("deaths"),
            currentStreak = rs.getInt("current_streak"),
            maxStreak = rs.getInt("max_streak"),
            lastVictimUuid = lastVictim?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            lastKillTime = rs.getLong("last_kill_time"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    fun cachedProfiles(): Collection<PlayerProfile> = profiles.values
}
