package ym.dreamkillecho.death

import org.bukkit.entity.Player

data class DeathContext(
    val victim: Player,
    val killer: Player?,
    val mobName: String?,
    val weapon: String,
    val world: String,
    val deathCause: String,
    val broadcastKey: String,
    val distance: Double,
    val killerIp: String?,
    val victimIp: String?,
    val placeholders: MutableMap<String, String> = linkedMapOf()
)

data class KillProcessResult(
    val countStats: Boolean,
    val shouldBroadcast: Boolean,
    val shouldEffect: Boolean,
    val shutdownStreak: Int,
    val revenge: Boolean
)
