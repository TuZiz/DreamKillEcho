package ym.dreamkillecho.effect

import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.FireworkMeta
import ym.dreamkillecho.config.PluginSettings
import ym.dreamkillecho.death.DeathContext
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.util.Permissions

class EffectService(
    private val scheduler: SchedulerAdapter,
    private val messages: MessageService,
    private val settings: PluginSettings
) {
    fun play(context: DeathContext) {
        val killer = context.killer ?: return
        if (!settings.effects.enabled) return
        scheduler.runEntity(killer) {
            if (settings.effects.title.enabled && killer.hasPermission(Permissions.EFFECT_TITLE)) {
                killer.sendTitle(
                    messages.plainString(settings.effects.title.message, killer, context.placeholders),
                    messages.plainString(settings.effects.subtitle, killer, context.placeholders),
                    10,
                    40,
                    10
                )
            }
            if (settings.effects.actionbar.enabled && killer.hasPermission(Permissions.EFFECT_ACTIONBAR)) {
                messages.actionBar(killer, settings.effects.actionbar.message, context.placeholders)
            }
            if (settings.effects.sound.enabled && killer.hasPermission(Permissions.EFFECT_SOUND)) {
                val sound = runCatching { Sound.valueOf(settings.effects.sound.name.uppercase().replace('.', '_')) }.getOrNull()
                if (sound != null) killer.playSound(killer.location, sound, settings.effects.sound.volume, settings.effects.sound.pitch)
                else killer.playSound(killer.location, settings.effects.sound.name, settings.effects.sound.volume, settings.effects.sound.pitch)
            }
            if (settings.effects.particle.enabled && killer.hasPermission(Permissions.EFFECT_PARTICLE)) {
                val particle = runCatching { Particle.valueOf(settings.effects.particle.name.uppercase()) }.getOrNull()
                if (particle != null) {
                    killer.world.spawnParticle(particle, killer.location.add(0.0, 1.0, 0.0), settings.effects.particle.count.coerceAtMost(settings.effects.particle.maxCount))
                }
            }
            if (settings.effects.firework.enabled && killer.hasPermission(Permissions.EFFECT_FIREWORK)) {
                repeat(settings.effects.firework.maxPerKill.coerceIn(0, 2)) { spawnFirework(killer) }
            }
            if (settings.effects.bossbar.enabled && killer.hasPermission(Permissions.EFFECT_BOSSBAR)) {
                val bar = killer.server.createBossBar(
                    messages.plainString(settings.effects.bossbar.message, killer, context.placeholders),
                    BarColor.YELLOW,
                    BarStyle.SOLID
                )
                bar.addPlayer(killer)
                scheduler.runLater(100L) {
                    scheduler.runEntity(killer) { bar.removeAll() }
                }
            }
        }
    }

    private fun spawnFirework(player: Player) {
        val firework = player.world.spawnEntity(player.location, EntityType.FIREWORK_ROCKET) as Firework
        val meta: FireworkMeta = firework.fireworkMeta
        meta.power = 0
        meta.addEffect(FireworkEffect.builder().withColor(Color.AQUA, Color.YELLOW).with(FireworkEffect.Type.BALL).trail(false).flicker(true).build())
        firework.fireworkMeta = meta
    }
}
