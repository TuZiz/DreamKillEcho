package ym.dreamkillecho.gui

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.theme.KillTheme
import ym.dreamkillecho.theme.ThemeService

class ThemeMenuService(
    private val plugin: JavaPlugin,
    private val scheduler: SchedulerAdapter,
    private val messages: MessageService,
    private val themes: ThemeService,
    private val storage: StorageService
) {
    private val config: ThemeMenuConfig = ThemeMenuConfig.load(plugin)
    private val legacy = LegacyComponentSerializer.legacySection()

    fun open(player: Player) {
        scheduler.runEntity(player) {
            player.openInventory(buildInventory(player))
        }
    }

    fun refresh(player: Player) {
        scheduler.runLater(1L) {
            scheduler.runEntity(player) {
                player.openInventory(buildInventory(player))
            }
        }
    }

    fun select(player: Player, themeId: String) {
        val theme = themes.require(themeId)
        if (theme == null) {
            messages.send(player, "theme-not-found")
            return
        }
        if (!themes.isUnlocked(player, theme)) {
            messages.send(player, "theme-no-permission")
            return
        }
        themes.select(player, theme, storage)
        messages.send(player, "theme-set-success", mapOf("theme" to theme.displayName))
        if (config.closeOnSelect) {
            player.closeInventory()
        } else {
            refresh(player)
        }
    }

    private fun buildInventory(player: Player): Inventory {
        val orderedThemes = themes.all()
        val profile = storage.profile(player.uniqueId, player.name)
        val currentTheme = themes.firstAvailable(player, profile.selectedTheme)
        val unlockedCount = orderedThemes.count { themes.isUnlocked(player, it) }
        val titlePlaceholders = mapOf(
            "player" to player.name,
            "total" to orderedThemes.size.toString(),
            "unlocked" to unlockedCount.toString()
        )
        val title = render(
            config.title,
            player,
            titlePlaceholders,
            mapOf("current" to messages.component(currentTheme.displayName, player, titlePlaceholders))
        )
        val themeBySlot = buildThemeSlotMap(orderedThemes, currentTheme.id)
        val holder = ThemeMenuHolder(themeBySlot)
        val inventory = Bukkit.createInventory(holder, config.size, title)
        holder.bind(inventory)
        fillBackground(inventory, player, currentTheme, orderedThemes.size, unlockedCount)
        for ((slot, themeId) in themeBySlot) {
            val theme = themes.require(themeId) ?: continue
            inventory.setItem(slot, buildThemeItem(player, theme, currentTheme, orderedThemes.size, unlockedCount))
        }
        return inventory
    }

    private fun buildThemeSlotMap(orderedThemes: List<KillTheme>, currentThemeId: String): Map<Int, String> {
        val visibleThemes = orderedThemes.take(config.themeSlots.size).toMutableList()
        if (visibleThemes.none { it.id == currentThemeId }) {
            val currentTheme = orderedThemes.firstOrNull { it.id == currentThemeId }
            if (currentTheme != null && visibleThemes.isNotEmpty()) {
                visibleThemes[visibleThemes.lastIndex] = currentTheme
            }
        }
        if (orderedThemes.size > config.themeSlots.size) {
            plugin.logger.warning("[DreamKillEcho] Theme menu has ${orderedThemes.size} themes but only ${config.themeSlots.size} theme-slots.")
        }
        return visibleThemes
            .mapIndexed { index, theme -> config.themeSlots[index] to theme.id }
            .toMap()
    }

    private fun fillBackground(
        inventory: Inventory,
        player: Player,
        currentTheme: KillTheme,
        totalThemes: Int,
        unlockedCount: Int
    ) {
        val fillItem = buildItem(
            config.fill,
            player,
            mapOf(
                "player" to player.name,
                "total" to totalThemes.toString(),
                "unlocked" to unlockedCount.toString()
            ),
            mapOf("current" to messages.component(currentTheme.displayName, player)),
            Material.BLACK_STAINED_GLASS_PANE
        )
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, fillItem.clone())
        }
    }

    private fun buildThemeItem(
        player: Player,
        theme: KillTheme,
        currentTheme: KillTheme,
        totalThemes: Int,
        unlockedCount: Int
    ): ItemStack {
        val unlocked = themes.isUnlocked(player, theme)
        val selected = unlocked && currentTheme.id == theme.id
        val template = when {
            selected -> config.selected
            unlocked -> config.available
            else -> config.locked
        }
        val status = when {
            selected -> config.selectedStatus
            unlocked -> config.availableStatus
            else -> config.lockedStatus
        }
        val placeholders = mapOf(
            "theme" to theme.id,
            "permission" to theme.permission,
            "priority" to theme.priority.toString(),
            "player" to player.name,
            "total" to totalThemes.toString(),
            "unlocked" to unlockedCount.toString()
        )
        val components = mapOf(
            "display" to messages.component(theme.displayName, player, placeholders),
            "status" to messages.component(status, player, placeholders),
            "current" to messages.component(currentTheme.displayName, player, placeholders)
        )
        return buildItem(template, player, placeholders, components, if (unlocked) Material.LIME_DYE else Material.GRAY_DYE)
    }

    private fun buildItem(
        itemConfig: ThemeMenuItemConfig,
        player: Player,
        placeholders: Map<String, String>,
        componentPlaceholders: Map<String, Component>,
        fallbackMaterial: Material
    ): ItemStack {
        val material = Material.matchMaterial(itemConfig.material) ?: fallbackMaterial
        val item = ItemStack(material, itemConfig.amount.coerceIn(1, 64))
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(render(itemConfig.name, player, placeholders, componentPlaceholders))
        if (itemConfig.lore.isNotEmpty()) {
            meta.setLore(itemConfig.lore.map { render(it, player, placeholders, componentPlaceholders) })
        }
        itemConfig.customModelData?.let { meta.setCustomModelData(it) }
        if (itemConfig.glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
        }
        item.itemMeta = meta
        return item
    }

    private fun render(
        template: String,
        player: Player,
        placeholders: Map<String, String>,
        componentPlaceholders: Map<String, Component> = emptyMap()
    ): String {
        return legacy.serialize(messages.component(template, player, placeholders, componentPlaceholders))
    }
}
