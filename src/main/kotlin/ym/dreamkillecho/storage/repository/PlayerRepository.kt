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
        connection.prepareStatement("SELECT * FROM players WHERE LOWER(name)=LOWER(?) ORDER BY updated_at DESC LIMIT 1").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> if (rs.next()) return read(rs) }
        }
        return null
    }

    fun save(connection: Connection, profile: PlayerProfile) {
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
