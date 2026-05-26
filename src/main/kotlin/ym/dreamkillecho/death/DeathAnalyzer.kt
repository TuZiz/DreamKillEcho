package ym.dreamkillecho.death

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import ym.dreamkillecho.message.MessageService
import kotlin.math.roundToInt

class DeathAnalyzer(private val messages: MessageService) {
    fun analyze(event: PlayerDeathEvent): DeathContext {
        val victim = event.entity
        val damage = victim.lastDamageCause
        val killer = findKiller(victim, damage)
        val weapon = findWeapon(killer, damage)
        val distance = if (killer != null && killer.world == victim.world) killer.location.distance(victim.location) else 0.0
        return DeathContext(
            victim = victim,
            killer = killer,
            weapon = weapon,
            world = victim.world.name,
            deathCause = mapCause(damage?.cause),
            distance = distance
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

    private fun findWeapon(killer: Player?, damage: EntityDamageEvent?): String {
        if (damage is EntityDamageByEntityEvent && damage.damager is Projectile) {
            return damage.damager.type.name.lowercase().replace('_', ' ')
        }
        val item: ItemStack? = killer?.inventory?.itemInMainHand
        if (item == null || item.type == Material.AIR) return messages.raw("bare-hand")
        return item.itemMeta?.displayName ?: item.type.name.lowercase().replace('_', ' ')
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
        val killer = context.killer
        context.placeholders += mapOf(
            "killer" to (killer?.name ?: messages.raw("unknown-player")),
            "victim" to context.victim.name,
            "weapon" to context.weapon,
            "world" to context.world,
            "killer_health" to formatHealth(killer?.health ?: 0.0),
            "victim_health" to formatHealth(context.victim.health),
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
