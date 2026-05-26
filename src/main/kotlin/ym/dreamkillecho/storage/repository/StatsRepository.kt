package ym.dreamkillecho.storage.repository

import ym.dreamkillecho.storage.PlayerStats
import ym.dreamkillecho.storage.LeaderboardRow
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class StatsRepository {
    fun load(connection: Connection, uuid: UUID): PlayerStats? {
        connection.prepareStatement("SELECT * FROM stats WHERE uuid=?").use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs -> if (rs.next()) return read(rs) }
        }
        return null
    }

    fun save(connection: Connection, value: PlayerStats) {
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

    fun top(connection: Connection, column: String, limit: Int): List<LeaderboardRow> {
        connection.prepareStatement(
            """
            SELECT s.uuid,
                   COALESCE(NULLIF(p.name, ''), s.uuid) AS player_name,
                   s.kills,
                   s.deaths,
                   s.current_streak,
                   s.max_streak
            FROM stats s
            LEFT JOIN players p ON p.uuid = s.uuid
            ORDER BY s.$column DESC, s.updated_at DESC
            LIMIT ?
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val result = mutableListOf<LeaderboardRow>()
                while (rs.next()) result += readLeaderboard(rs)
                return result
            }
        }
    }

    private fun readLeaderboard(rs: ResultSet): LeaderboardRow {
        return LeaderboardRow(
            uuid = UUID.fromString(rs.getString("uuid")),
            name = rs.getString("player_name") ?: rs.getString("uuid"),
            kills = rs.getInt("kills"),
            deaths = rs.getInt("deaths"),
            currentStreak = rs.getInt("current_streak"),
            maxStreak = rs.getInt("max_streak")
        )
    }

    private fun read(rs: ResultSet): PlayerStats {
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
}
