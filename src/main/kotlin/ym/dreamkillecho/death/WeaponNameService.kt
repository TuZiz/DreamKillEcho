package ym.dreamkillecho.death

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Projectile
import org.bukkit.inventory.ItemStack
import ym.dreamkillecho.message.MessageService

data class WeaponDisplay(
    val fallbackText: String,
    val component: Component
)

class WeaponNameService(private val messages: MessageService) {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun unknown(): WeaponDisplay {
        val fallback = messages.raw("unknown-weapon")
        return WeaponDisplay(fallback, Component.text(fallback))
    }

    fun heldItemName(item: ItemStack?): WeaponDisplay {
        if (item == null || item.type == Material.AIR) {
            val fallback = messages.raw("bare-hand")
            return WeaponDisplay(fallback, Component.text(fallback))
        }
        val meta = item.itemMeta
        val displayName = if (meta != null && meta.hasDisplayName()) meta.displayName else null
        if (!displayName.isNullOrBlank()) {
            val fallback = ChatColor.stripColor(displayName) ?: displayName
            return WeaponDisplay(fallback, legacySerializer.deserialize(displayName))
        }
        val fallback = localizedItemName(item.type)
        return WeaponDisplay(fallback, Component.text(fallback))
    }

    fun projectileName(projectile: Projectile): WeaponDisplay {
        val fallback = localizedProjectileName(projectile)
        return WeaponDisplay(fallback, Component.text(fallback))
    }

    private fun localizedItemName(material: Material): String {
        return messages.rawOrNull("weapon.material.${material.key.key}")
            ?: messages.rawOrNull("weapon.material.${material.name.lowercase()}")
            ?: readableKey(material.name)
    }

    private fun localizedProjectileName(projectile: Projectile): String {
        return messages.rawOrNull("weapon.projectile.${projectile.type.key.key}")
            ?: messages.rawOrNull("weapon.projectile.${projectile.type.name.lowercase()}")
            ?: readableKey(projectile.type.name)
    }

    private fun readableKey(value: String): String {
        return value.lowercase().split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
