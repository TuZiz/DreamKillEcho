package ym.dreamkillecho

import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.command.CommandRouter
import ym.dreamkillecho.config.ConfigService
import ym.dreamkillecho.death.DeathListener
import ym.dreamkillecho.gui.ThemeMenuListener
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.util.Resources

class DreamKillEcho : JavaPlugin() {
    lateinit var schedulerAdapter: SchedulerAdapter
        private set

    @Volatile
    var services: PluginServices? = null
        private set

    var commandRouter: CommandRouter? = null
        private set

    override fun onEnable() {
        schedulerAdapter = SchedulerAdapter.create(this)
        val executor = CommandRouter(this)
        commandRouter = executor
        getCommand("dreamkillecho")?.setExecutor(executor)
        getCommand("dreamkillecho")?.tabCompleter = executor

        schedulerAdapter.runAsync {
            try {
                Resources.ensureDefaults(this, listOf("config.yml", "lang/zh_cn.yml", "lang/en_us.yml", "themes.yml", "storage.yml", "gui/theme-menu.yml"))
                val configService = ConfigService.load(this)
                val messageService = MessageService(this, configService.language, configService.fallbackLanguage)
                val storage = StorageService(this, configService.storage)
                storage.start()
                val built = PluginServices.create(this, schedulerAdapter, configService, messageService, storage)
                schedulerAdapter.runMain {
                    services = built
                    executor.bind(built)
                    server.pluginManager.registerEvents(DeathListener(this), this)
                    server.pluginManager.registerEvents(ThemeMenuListener(this), this)
                    built.storage.prepareOnlinePlayers()
                    built.startTimers()
                    logger.info("[DreamKillEcho] Enabled on ${schedulerAdapter.platformName}.")
                }
            } catch (ex: Exception) {
                logger.severe("[DreamKillEcho] Startup failed: ${ex.message}")
                ex.printStackTrace()
                schedulerAdapter.runMain { server.pluginManager.disablePlugin(this) }
            }
        }
    }

    override fun onDisable() {
        HandlerList.unregisterAll(this)
        services?.shutdown()
        services = null
        commandRouter?.bind(null)
        if (::schedulerAdapter.isInitialized) {
            schedulerAdapter.cancelAll()
        }
    }

    fun replaceServices(services: PluginServices?) {
        this.services = services
    }
}
