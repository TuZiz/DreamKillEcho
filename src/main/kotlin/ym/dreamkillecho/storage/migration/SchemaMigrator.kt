package ym.dreamkillecho.storage.migration

import java.sql.Connection

object SchemaMigrator {
    private val migrations = listOf(
        Migration(1) { _, _ -> },
        Migration(2) { connection, _ ->
            addNameLowerColumn(connection)
            connection.createStatement().use { st ->
                st.executeUpdate("UPDATE players SET name_lower=LOWER(name) WHERE name_lower IS NULL OR name_lower=''")
            }
            createIndex(connection, "idx_players_name_lower", "players", "name_lower")
            createIndex(connection, "idx_players_custom_status", "players", "custom_message_status, custom_message_updated_at")
            createIndex(connection, "idx_stats_kills", "stats", "kills, updated_at")
            createIndex(connection, "idx_stats_max_streak", "stats", "max_streak, updated_at")
            createIndex(connection, "idx_kill_logs_created_at", "kill_logs", "created_at")
            createIndex(connection, "idx_kill_logs_killer_uuid", "kill_logs", "killer_uuid")
            createIndex(connection, "idx_kill_logs_victim_uuid", "kill_logs", "victim_uuid")
        }
    )

    fun initialize(connection: Connection, storageType: String) {
        createSchema(connection, storageType)
        val current = currentVersion(connection)
        for (migration in migrations.filter { it.version > current }.sortedBy { it.version }) {
            migration.apply(connection, storageType)
            applySchemaVersion(connection, storageType, migration.version)
        }
    }

    private fun createSchema(connection: Connection, storageType: String) {
        connection.createStatement().use { st ->
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players(uuid VARCHAR(36) PRIMARY KEY,name VARCHAR(32) NOT NULL,selected_theme VARCHAR(64) NOT NULL,custom_message TEXT,custom_message_status VARCHAR(16) NOT NULL,custom_message_updated_at BIGINT NOT NULL,receive_broadcast BOOLEAN NOT NULL,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stats(uuid VARCHAR(36) PRIMARY KEY,kills INTEGER NOT NULL,deaths INTEGER NOT NULL,current_streak INTEGER NOT NULL,max_streak INTEGER NOT NULL,last_victim_uuid VARCHAR(36),last_kill_time BIGINT NOT NULL,updated_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kill_logs(id INTEGER PRIMARY KEY ${if (storageType == "mysql") "AUTO_INCREMENT" else "AUTOINCREMENT"},killer_uuid VARCHAR(36),victim_uuid VARCHAR(36) NOT NULL,weapon VARCHAR(128) NOT NULL,world VARCHAR(128) NOT NULL,death_cause VARCHAR(64) NOT NULL,distance DOUBLE NOT NULL,created_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER PRIMARY KEY,applied_at BIGINT NOT NULL)")
        }
    }

    private fun currentVersion(connection: Connection): Int {
        connection.createStatement().use { st ->
            st.executeQuery("SELECT MAX(version) FROM schema_version").use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    private fun addNameLowerColumn(connection: Connection) {
        if (columnExists(connection, "players", "name_lower")) return
        connection.createStatement().use { st ->
            st.executeUpdate("ALTER TABLE players ADD COLUMN name_lower VARCHAR(32)")
        }
    }

    private fun createIndex(connection: Connection, indexName: String, tableName: String, columns: String) {
        if (indexExists(connection, tableName, indexName)) return
        connection.createStatement().use { st ->
            st.executeUpdate("CREATE INDEX $indexName ON $tableName($columns)")
        }
    }

    private fun columnExists(connection: Connection, tableName: String, columnName: String): Boolean {
        val metadata = connection.metaData
        val candidates = listOf(tableName, tableName.uppercase())
        for (candidate in candidates) {
            metadata.getColumns(connection.catalog, null, candidate, columnName).use { rs ->
                if (rs.next()) return true
            }
            metadata.getColumns(null, null, candidate, columnName).use { rs ->
                if (rs.next()) return true
            }
        }
        return false
    }

    private fun indexExists(connection: Connection, tableName: String, indexName: String): Boolean {
        val metadata = connection.metaData
        val candidates = listOf(tableName, tableName.uppercase())
        for (candidate in candidates) {
            metadata.getIndexInfo(connection.catalog, null, candidate, false, false).use { rs ->
                while (rs.next()) {
                    if (indexName.equals(rs.getString("INDEX_NAME"), ignoreCase = true)) return true
                }
            }
            metadata.getIndexInfo(null, null, candidate, false, false).use { rs ->
                while (rs.next()) {
                    if (indexName.equals(rs.getString("INDEX_NAME"), ignoreCase = true)) return true
                }
            }
        }
        return false
    }

    private fun applySchemaVersion(connection: Connection, storageType: String, version: Int) {
        val sql = if (storageType == "mysql") {
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
}

private data class Migration(
    val version: Int,
    val apply: (Connection, String) -> Unit
)
