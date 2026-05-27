package ym.dreamkillecho.effect

import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.FireworkMeta
import ym.dreamkillecho.config.PluginSettings
import ym.dreamkillecho.death.DeathContext
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.util.Permissions
import ym.dreamkillecho.util.RollingWindowLimiter

class EffectService(
    private val scheduler: SchedulerAdapter,
    private val messages: MessageService,
    private val settings: PluginSettings
) {
    private val globalEffectLimiter = RollingWindowLimiter(settings.effects.globalLimitPerMinute, 60_000L)

    fun play(context: DeathContext) {
        val killer = context.killer ?: return
        if (!settings.effects.enabled) return
        if (!globalEffectLimiter.tryAcquire()) return
        scheduler.runEntity(killer) {
            if (settings.effects.title.enabled && killer.hasPermission(Permissions.EFFECT_TITLE)) {
                messages.title(killer, settings.effects.title.message, settings.effects.subtitle, context.placeholders, context.componentPlaceholders)
            }
            if (settings.effects.actionbar.enabled && killer.hasPermission(Permissions.EFFECT_ACTIONBAR)) {
                messages.actionBar(killer, settings.effects.actionbar.message, context.placeholders, context.componentPlaceholders)
            }
            if (settings.effects.sound.enabled && killer.hasPermission(Permissions.EFFECT_SOUND)) {
                val sound = runCatching { Sound.valueOf(settings.effects.sound.name.uppercase().replace('.', '_')) }.getOrNull()
                if (sound != null) killer.playSound(killer.location, sound, settings.effects.sound.volume, settings.effects.sound.pitch)
                else killer.playSound(killer.location, settings.effects.sound.name, settings.effects.sound.volume, settings.effects.sound.pitch)
            }
            if (settings.effects.particle.enabled && killer.hasPermission(Permissions.EFFECT_PARTICLE)) {
                val particle = runCatching { Particle.valueOf(settings.effects.particle.name.uppercase()) }.getOrNull()
                if (particle != null) {
                    val location = killer.location.clone().add(0.0, 1.0, 0.0)
                    scheduler.runLocation(location) {
                        location.world?.spawnParticle(particle, location, settings.effects.particle.count.coerceAtMost(settings.effects.particle.maxCount))
                    }
                }
            }
            if (settings.effects.firework.enabled && killer.hasPermission(Permissions.EFFECT_FIREWORK)) {
                val location = killer.location.clone()
                repeat(settings.effects.firework.maxPerKill.coerceIn(0, 2)) {
                    scheduler.runLocation(location) { spawnFirework(location) }
                }
            }
            if (settings.effects.bossbar.enabled && killer.hasPermission(Permissions.EFFECT_BOSSBAR)) {
                val bar = messages.showBossBar(killer, settings.effects.bossbar.message, context.placeholders, context.componentPlaceholders)
                scheduler.runLater(settings.effects.bossbar.seconds.coerceAtLeast(1).toLong() * 20L) {
                    scheduler.runEntity(killer) { messages.hideBossBar(killer, bar) }
                }
            }
        }
    }

    private fun spawnFirework(location: Location) {
        val firework = location.world?.spawnEntity(location, EntityType.FIREWORK_ROCKET) as? Firework ?: return
        val meta: FireworkMeta = firework.fireworkMeta
        meta.power = 0
        meta.addEffect(FireworkEffect.builder().withColor(Color.AQUA, Color.YELLOW).with(FireworkEffect.Type.BALL).trail(false).flicker(true).build())
        firework.fireworkMeta = meta
    }
}
