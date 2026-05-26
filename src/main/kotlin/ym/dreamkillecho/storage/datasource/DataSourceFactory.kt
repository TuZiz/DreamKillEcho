package ym.dreamkillecho.storage.datasource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.config.StorageSettings
import java.io.File

object DataSourceFactory {
    fun create(plugin: JavaPlugin, settings: StorageSettings): HikariDataSource {
        val config = HikariConfig()
        when (settings.type) {
            "mysql" -> {
                Class.forName("com.mysql.cj.jdbc.Driver")
                config.jdbcUrl = "jdbc:mysql://${settings.mysql.host}:${settings.mysql.port}/${settings.mysql.database}?useSSL=false&characterEncoding=utf8&serverTimezone=UTC"
                config.username = settings.mysql.username
                config.password = settings.mysql.password
                config.maximumPoolSize = settings.mysql.maximumPoolSize
                config.minimumIdle = settings.mysql.minimumIdle
                config.connectionTimeout = settings.mysql.connectionTimeout
            }
            else -> {
                Class.forName("org.sqlite.JDBC")
                val dbFile = File(plugin.dataFolder, settings.sqliteFile)
                dbFile.parentFile?.mkdirs()
                config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                config.maximumPoolSize = 1
            }
        }
        config.poolName = "DreamKillEchoPool"
        return HikariDataSource(config)
    }
}
