package ym.dreamkillecho.death

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

data class DeathContext(
    val victim: Player,
    val killer: Player?,
    val victimUuid: UUID,
    val killerUuid: UUID?,
    val victimName: String,
    var killerName: String?,
    val mobName: String?,
    var weapon: String,
    val world: String,
    val deathCause: String,
    val broadcastKey: String,
    var distance: Double,
    val victimHealth: Double,
    var killerHealth: Double?,
    val killerIp: String?,
    val victimIp: String?,
    val victimLocation: Location?,
    val placeholders: MutableMap<String, String> = linkedMapOf(),
    val componentPlaceholders: MutableMap<String, Component> = linkedMapOf()
)

data class KillProcessResult(
    val countStats: Boolean,
    val shouldBroadcast: Boolean,
    val shouldEffect: Boolean,
    val shutdownStreak: Int,
    val revenge: Boolean
)
