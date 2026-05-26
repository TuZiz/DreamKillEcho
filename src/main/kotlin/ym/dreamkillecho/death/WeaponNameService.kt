package ym.dreamkillecho.death

import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Projectile
import org.bukkit.inventory.ItemStack
import ym.dreamkillecho.message.MessageService

class WeaponNameService(private val messages: MessageService) {
    fun heldItemName(item: ItemStack?): String {
        if (item == null || item.type == Material.AIR) return messages.raw("bare-hand")
        val displayName = item.itemMeta?.displayName
        if (!displayName.isNullOrBlank()) return ChatColor.stripColor(displayName) ?: displayName
        return materialName(item.type)
    }

    fun projectileName(projectile: Projectile): String {
        return messages.rawOrNull("weapon.projectile.${projectile.type.name.lowercase()}")
            ?: messages.rawOrNull("weapon.projectile.generic")
            ?: readableKey(projectile.type.name)
    }

    private fun materialName(material: Material): String {
        return messages.rawOrNull("weapon.material.${material.name.lowercase()}") ?: readableKey(material.name)
    }

    private fun readableKey(value: String): String {
        return value.lowercase().split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
