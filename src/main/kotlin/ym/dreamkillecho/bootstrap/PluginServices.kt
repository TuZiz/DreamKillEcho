package ym.dreamkillecho.bootstrap

import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.config.ConfigService
import ym.dreamkillecho.death.AntiAbuseService
import ym.dreamkillecho.death.BroadcastService
import ym.dreamkillecho.death.DeathAnalyzer
import ym.dreamkillecho.death.WeaponNameService
import ym.dreamkillecho.effect.EffectService
import ym.dreamkillecho.gui.ThemeMenuService
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.review.CustomMessageService
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.theme.ThemeService

class PluginServices(
    val plugin: JavaPlugin,
    val scheduler: SchedulerAdapter,
    val config: ConfigService,
    val messages: MessageService,
    val storage: StorageService,
    val themes: ThemeService,
    val themeMenu: ThemeMenuService,
    val deathAnalyzer: DeathAnalyzer,
    val antiAbuse: AntiAbuseService,
    val broadcast: BroadcastService,
    val effects: EffectService,
    val customMessages: CustomMessageService
) {
    fun startTimers() {
        val period = config.settings.flushIntervalSeconds.coerceAtLeast(30L) * 20L
        scheduler.runTimer(period, period) { storage.flushAsync() }
    }

    fun shutdown() {
        messages.close()
        storage.shutdown()
    }

    companion object {
        fun create(
            plugin: JavaPlugin,
            scheduler: SchedulerAdapter,
            config: ConfigService,
            messages: MessageService,
            storage: StorageService
        ): PluginServices {
            val themes = ThemeService(plugin, config.themes)
            val themeMenu = ThemeMenuService(plugin, scheduler, messages, themes, storage)
            val analyzer = DeathAnalyzer(messages, WeaponNameService(messages), scheduler.platformName)
            val antiAbuse = AntiAbuseService(config.settings)
            if (scheduler.platformName.contains("Folia", ignoreCase = true) && config.settings.antiFarm.sameIpNoStats) {
                plugin.logger.warning("[DreamKillEcho] anti-farm.same-ip-no-stats is degraded on Folia because cross-region killer address access is not reliable.")
            }
            val broadcast = BroadcastService(scheduler, config.settings, messages, themes, storage)
            val effects = EffectService(scheduler, messages, config.settings)
            val custom = CustomMessageService(config.settings.custom, storage)
            return PluginServices(plugin, scheduler, config, messages, storage, themes, themeMenu, analyzer, antiAbuse, broadcast, effects, custom)
        }
    }
}
