package ym.dreamkillecho.util

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object Resources {
    fun ensureDefaults(plugin: JavaPlugin, names: List<String>) {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        for (name in names) {
            val target = File(plugin.dataFolder, name)
            if (target.exists()) continue
            target.parentFile?.mkdirs()
            plugin.getResource(name)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Missing bundled resource: $name")
        }
    }
}
