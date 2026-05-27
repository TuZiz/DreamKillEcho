package ym.dreamkillecho.storage.repository

import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.storage.PlayerProfile
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class PlayerRepository {
    fun load(connection: Connection, uuid: UUID): PlayerProfile? {
        connection.prepareStatement("SELECT * FROM players WHERE uuid=?").use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs -> if (rs.next()) return read(rs) }
        }
        return null
    }

    fun findByName(connection: Connection, name: String): PlayerProfile? {
        connection.prepareStatement("SELECT * FROM players WHERE name_lower=? ORDER BY updated_at DESC LIMIT 1").use { ps ->
            ps.setString(1, name.lowercase())
            ps.executeQuery().use { rs -> if (rs.next()) return read(rs) }
        }
        return null
    }

    fun pendingCustomMessages(connection: Connection): List<PlayerProfile> {
        connection.prepareStatement("SELECT * FROM players WHERE custom_message_status=? ORDER BY custom_message_updated_at DESC").use { ps ->
            ps.setString(1, CustomMessageStatus.PENDING.name)
            ps.executeQuery().use { rs ->
                val result = mutableListOf<PlayerProfile>()
                while (rs.next()) result += read(rs)
                return result
            }
        }
    }

    fun save(connection: Connection, profile: PlayerProfile) {
        connection.prepareStatement(upsertSql(connection)).use { ps ->
            ps.setString(1, profile.uuid.toString())
            ps.setString(2, profile.name)
            ps.setString(3, profile.name.lowercase())
            ps.setString(4, profile.selectedTheme)
            ps.setString(5, profile.customMessage)
            ps.setString(6, profile.customMessageStatus.name)
            ps.setLong(7, profile.customMessageUpdatedAt)
            ps.setBoolean(8, profile.receiveBroadcast)
            ps.setLong(9, profile.createdAt)
            ps.setLong(10, profile.updatedAt)
            ps.executeUpdate()
        }
    }

    private fun upsertSql(connection: Connection): String {
        return if (connection.metaData.databaseProductName.contains("mysql", ignoreCase = true)) {
            """
            INSERT INTO players(uuid,name,name_lower,selected_theme,custom_message,custom_message_status,custom_message_updated_at,receive_broadcast,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
              name=VALUES(name),
              name_lower=VALUES(name_lower),
              selected_theme=VALUES(selected_theme),
              custom_message=VALUES(custom_message),
              custom_message_status=VALUES(custom_message_status),
              custom_message_updated_at=VALUES(custom_message_updated_at),
              receive_broadcast=VALUES(receive_broadcast),
              created_at=VALUES(created_at),
              updated_at=VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO players(uuid,name,name_lower,selected_theme,custom_message,custom_message_status,custom_message_updated_at,receive_broadcast,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(uuid) DO UPDATE SET
              name=excluded.name,
              name_lower=excluded.name_lower,
              selected_theme=excluded.selected_theme,
              custom_message=excluded.custom_message,
              custom_message_status=excluded.custom_message_status,
              custom_message_updated_at=excluded.custom_message_updated_at,
              receive_broadcast=excluded.receive_broadcast,
              created_at=excluded.created_at,
              updated_at=excluded.updated_at
            """.trimIndent()
        }
    }

    private fun read(rs: ResultSet): PlayerProfile {
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
}
