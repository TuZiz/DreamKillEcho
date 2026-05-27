package ym.dreamkillecho.message

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

class PlaceholderBridge(private val plugin: JavaPlugin) {
    @Volatile
    private var checked = false

    @Volatile
    private var setPlaceholders: Method? = null

    fun apply(player: Player?, template: String): String {
        if (player == null || !plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) return template
        val method = resolveMethod() ?: return template
        return runCatching {
            method.invoke(null, player, template) as? String ?: template
        }.getOrDefault(template)
    }

    private fun resolveMethod(): Method? {
        setPlaceholders?.let { return it }
        if (checked) return null
        return synchronized(this) {
            setPlaceholders?.let { return@synchronized it }
            if (checked) return@synchronized null
            checked = true
            setPlaceholders = runCatching {
                Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    .getMethod("setPlaceholders", Player::class.java, String::class.java)
            }.getOrNull()
            setPlaceholders
        }
    }
}
