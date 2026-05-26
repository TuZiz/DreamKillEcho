package ym.dreamkillecho.death

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import ym.dreamkillecho.message.MessageService
import kotlin.math.roundToInt

class DeathAnalyzer(
    private val messages: MessageService,
    private val weaponNames: WeaponNameService,
    platformName: String
) {
    private val foliaMode = platformName.contains("Folia", ignoreCase = true)

    fun analyze(event: PlayerDeathEvent): DeathContext {
        val victim = event.entity
        val damage = victim.lastDamageCause
        val killer = findKiller(victim, damage)
        val mobName = findMobName(damage)
        val weapon = findWeapon(killer, damage)
        val distance = if (!foliaMode && killer != null && killer.world == victim.world) killer.location.distance(victim.location) else 0.0
        val cause = mapCause(damage?.cause)
        return DeathContext(
            victim = victim,
            killer = killer,
            victimName = victim.name,
            killerName = killer?.name,
            mobName = mobName,
            weapon = weapon.fallbackText,
            world = victim.world.name,
            deathCause = cause,
            broadcastKey = if (killer != null) "player" else if (cause == "projectile") "projectile" else if (mobName != null) "mob" else cause,
            distance = distance,
            victimHealth = victim.health,
            killerHealth = if (!foliaMode) killer?.health else null,
            killerIp = if (!foliaMode) killer?.address?.address?.hostAddress else null,
            victimIp = victim.address?.address?.hostAddress,
            componentPlaceholders = linkedMapOf("weapon" to weapon.component)
        )
    }

    private fun findKiller(victim: Player, damage: EntityDamageEvent?): Player? {
        victim.killer?.let { return it }
        if (damage !is EntityDamageByEntityEvent) return null
        val damager = damage.damager
        if (damager is Player) return damager
        if (damager is Projectile) {
            val shooter = damager.shooter
            if (shooter is Player) return shooter
        }
        return null
    }

    private fun findMobName(damage: EntityDamageEvent?): String? {
        if (damage !is EntityDamageByEntityEvent) return null
        val damager = damage.damager
        if (damager is Projectile) {
            val shooter = damager.shooter
            if (shooter is LivingEntity && shooter !is Player) return shooter.name
            return damager.type.name.lowercase().replace('_', ' ')
        }
        if (damager is LivingEntity && damager !is Player) return damager.name
        return null
    }

    private fun findWeapon(killer: Player?, damage: EntityDamageEvent?): WeaponDisplay {
        if (damage is EntityDamageByEntityEvent && damage.damager is Projectile) {
            return weaponNames.projectileName(damage.damager as Projectile)
        }
        if (foliaMode && killer != null) {
            return weaponNames.unknown()
        }
        return weaponNames.heldItemName(killer?.inventory?.itemInMainHand)
    }

    private fun mapCause(cause: EntityDamageEvent.DamageCause?): String {
        return when (cause) {
            EntityDamageEvent.DamageCause.FALL -> "fall"
            EntityDamageEvent.DamageCause.LAVA -> "lava"
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.HOT_FLOOR -> "fire"
            EntityDamageEvent.DamageCause.DROWNING -> "drowning"
            EntityDamageEvent.DamageCause.VOID -> "void"
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION -> "explosion"
            EntityDamageEvent.DamageCause.CONTACT -> "cactus"
            EntityDamageEvent.DamageCause.SUFFOCATION -> "suffocation"
            EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.POISON,
            EntityDamageEvent.DamageCause.WITHER -> "magic"
            EntityDamageEvent.DamageCause.LIGHTNING -> "lightning"
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK -> "entity"
            EntityDamageEvent.DamageCause.PROJECTILE -> "projectile"
            else -> "unknown"
        }
    }

    fun fillPlaceholders(context: DeathContext, streak: Int, maxStreak: Int, prefix: String, theme: String, server: String) {
        context.placeholders += mapOf(
            "killer" to (context.killerName ?: messages.raw("unknown-player")),
            "victim" to context.victimName,
            "mob" to (context.mobName ?: messages.raw("unknown-player")),
            "weapon" to context.weapon,
            "world" to context.world,
            "killer_health" to formatHealth(context.killerHealth ?: 0.0),
            "victim_health" to formatHealth(context.victimHealth),
            "distance" to "%.1f".format(context.distance),
            "streak" to streak.toString(),
            "max_streak" to maxStreak.toString(),
            "death_cause" to context.deathCause,
            "prefix" to prefix,
            "theme" to theme,
            "server" to server
        )
    }

    private fun formatHealth(health: Double): String {
        return health.roundToInt().toString()
    }
}
