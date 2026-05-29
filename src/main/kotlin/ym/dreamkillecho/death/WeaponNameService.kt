package ym.dreamkillecho.death

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.event.HoverEvent
import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Projectile
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
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
            val component = legacySerializer.deserialize(displayName)
            return WeaponDisplay(fallback, withHover(component, item, fallback, component))
        }
        val fallback = localizedItemName(item.type)
        val component = Component.text(fallback)
        return WeaponDisplay(fallback, withHover(component, item, fallback, component))
    }

    fun projectileName(projectile: Projectile): WeaponDisplay {
        val fallback = localizedProjectileName(projectile)
        val component = Component.text(fallback)
        return WeaponDisplay(fallback, component.hoverEvent(HoverEvent.showText(projectileHover(fallback))))
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

    private fun withHover(base: Component, item: ItemStack, fallback: String, nameComponent: Component): Component {
        return base.hoverEvent(HoverEvent.showText(itemHover(item, fallback, nameComponent)))
    }

    private fun itemHover(item: ItemStack, fallback: String, nameComponent: Component): Component {
        val meta = item.itemMeta
        val lines = mutableListOf<Component>()
        lines += labelComponent("weapon-hover-name").append(nameComponent)
        lines += labelText("weapon-hover-type", fallback)
        if (item.amount > 1) {
            lines += labelText("weapon-hover-amount", item.amount.toString())
        }
        durabilityLine(item, meta)?.let { lines += it }
        if (meta?.isUnbreakable == true) {
            lines += Component.text(messages.raw("weapon-hover-unbreakable"), NamedTextColor.AQUA)
        }
        val enchants = meta?.enchants.orEmpty()
        if (enchants.isNotEmpty()) {
            lines += Component.text(messages.raw("weapon-hover-enchantments"), NamedTextColor.GRAY)
            for ((enchantment, level) in enchants.entries.sortedBy { it.key.key.key }) {
                lines += Component.text("  ${localizedEnchantmentName(enchantment)} ${roman(level)}", NamedTextColor.AQUA)
            }
        }
        val lore = meta?.lore.orEmpty()
        if (lore.isNotEmpty()) {
            lines += Component.text(messages.raw("weapon-hover-lore"), NamedTextColor.GRAY)
            for (line in lore.take(8)) {
                lines += Component.text("  ", NamedTextColor.DARK_GRAY).append(legacySerializer.deserialize(line))
            }
        }
        return joinLines(lines)
    }

    private fun projectileHover(name: String): Component {
        return joinLines(
            listOf(
                labelText("weapon-hover-name", name),
                labelText("weapon-hover-type", name)
            )
        )
    }

    private fun durabilityLine(item: ItemStack, meta: ItemMeta?): Component? {
        val maxDurability = item.type.maxDurability.toInt()
        if (maxDurability <= 0 || meta !is Damageable) return null
        val remaining = (maxDurability - meta.damage).coerceAtLeast(0)
        return labelText("weapon-hover-durability", "$remaining/$maxDurability")
    }

    private fun labelComponent(key: String): Component {
        return Component.text(messages.raw(key), NamedTextColor.GRAY)
    }

    private fun labelText(key: String, value: String): Component {
        return labelComponent(key).append(Component.text(value, NamedTextColor.WHITE))
    }

    private fun localizedEnchantmentName(enchantment: Enchantment): String {
        return messages.rawOrNull("weapon.enchantment.${enchantment.key.key}") ?: readableKey(enchantment.key.key)
    }

    private fun roman(value: Int): String {
        return when (value) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            6 -> "VI"
            7 -> "VII"
            8 -> "VIII"
            9 -> "IX"
            10 -> "X"
            else -> value.toString()
        }
    }

    private fun joinLines(lines: List<Component>): Component {
        val builder = Component.text()
        for ((index, line) in lines.withIndex()) {
            if (index > 0) builder.append(Component.newline())
            builder.append(line)
        }
        return builder.build()
    }

    private fun readableKey(value: String): String {
        return value.lowercase().split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
