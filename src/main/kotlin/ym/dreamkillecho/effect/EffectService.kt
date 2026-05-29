package ym.dreamkillecho.effect

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
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
                repeat(settings.effects.firework.maxPerKill.coerceIn(0, 2)) { index ->
                    scheduler.runLocation(location) { spawnFireworkBurst(location, index) }
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

    private fun spawnFireworkBurst(location: Location, index: Int) {
        val world = location.world ?: return
        val burst = location.clone().add(0.0, 1.2 + (index * 0.35), 0.0)
        world.spawnParticle(Particle.FIREWORK, burst, 24, 0.45, 0.35, 0.45, 0.02)
        world.spawnParticle(Particle.END_ROD, burst, 10, 0.25, 0.25, 0.25, 0.01)
        world.playSound(burst, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.2f)
    }
}
