package ym.dreamkillecho.storage

import java.util.UUID

enum class CustomMessageStatus {
    NONE, PENDING, APPROVED, DENIED
}

data class PlayerProfile(
    val uuid: UUID,
    var name: String,
    var selectedTheme: String = "default",
    var customMessage: String? = null,
    var customMessageStatus: CustomMessageStatus = CustomMessageStatus.NONE,
    var customMessageUpdatedAt: Long = 0L,
    var receiveBroadcast: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

data class PlayerStats(
    val uuid: UUID,
    var kills: Int = 0,
    var deaths: Int = 0,
    var currentStreak: Int = 0,
    var maxStreak: Int = 0,
    var lastVictimUuid: UUID? = null,
    var lastKillTime: Long = 0L,
    var updatedAt: Long = System.currentTimeMillis()
)

data class LeaderboardRow(
    val uuid: UUID,
    val name: String,
    val kills: Int,
    val deaths: Int,
    val currentStreak: Int,
    val maxStreak: Int
)

data class KillLog(
    val killerUuid: UUID?,
    val victimUuid: UUID,
    val weapon: String,
    val world: String,
    val deathCause: String,
    val distance: Double,
    val createdAt: Long = System.currentTimeMillis()
)

data class StatsUpdateResult(
    val previousVictimStreak: Int,
    val killerStreak: Int,
    val killerMaxStreak: Int,
    val victimMaxStreak: Int
)
