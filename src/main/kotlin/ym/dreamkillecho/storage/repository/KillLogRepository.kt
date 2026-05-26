package ym.dreamkillecho.storage.repository

import ym.dreamkillecho.storage.KillLog
import java.sql.Connection

class KillLogRepository {
    fun insert(connection: Connection, log: KillLog) {
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
}
