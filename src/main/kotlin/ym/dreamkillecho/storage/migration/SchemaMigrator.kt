package ym.dreamkillecho.storage.migration

import java.sql.Connection

object SchemaMigrator {
    fun initialize(connection: Connection, storageType: String) {
        createSchema(connection, storageType)
        applySchemaVersion(connection, storageType, 1)
    }

    private fun createSchema(connection: Connection, storageType: String) {
        connection.createStatement().use { st ->
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players(uuid VARCHAR(36) PRIMARY KEY,name VARCHAR(32) NOT NULL,selected_theme VARCHAR(64) NOT NULL,custom_message TEXT,custom_message_status VARCHAR(16) NOT NULL,custom_message_updated_at BIGINT NOT NULL,receive_broadcast BOOLEAN NOT NULL,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stats(uuid VARCHAR(36) PRIMARY KEY,kills INTEGER NOT NULL,deaths INTEGER NOT NULL,current_streak INTEGER NOT NULL,max_streak INTEGER NOT NULL,last_victim_uuid VARCHAR(36),last_kill_time BIGINT NOT NULL,updated_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kill_logs(id INTEGER PRIMARY KEY ${if (storageType == "mysql") "AUTO_INCREMENT" else "AUTOINCREMENT"},killer_uuid VARCHAR(36),victim_uuid VARCHAR(36) NOT NULL,weapon VARCHAR(128) NOT NULL,world VARCHAR(128) NOT NULL,death_cause VARCHAR(64) NOT NULL,distance DOUBLE NOT NULL,created_at BIGINT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER PRIMARY KEY,applied_at BIGINT NOT NULL)")
        }
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
