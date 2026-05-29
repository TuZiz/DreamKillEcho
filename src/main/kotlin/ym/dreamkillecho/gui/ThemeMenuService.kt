package ym.dreamkillecho.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
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
    private val storage: StorageService,
    private val config: ThemeMenuConfig
) {
    private val legacy = LegacyComponentSerializer.legacySection()

    fun open(player: Player, page: Int = 0) {
        scheduler.runEntity(player) {
            if (!player.isOnline) return@runEntity
            player.openInventory(buildInventory(player, page))
        }
    }

    fun refresh(player: Player) {
        refresh(player, currentPage(player))
    }

    fun refresh(player: Player, page: Int) {
        scheduler.runLater(1L) {
            scheduler.runEntity(player) {
                if (!player.isOnline) return@runEntity
                player.openInventory(buildInventory(player, page))
            }
        }
    }

    fun select(player: Player, themeId: String) {
        select(player, themeId, currentPage(player))
    }

    fun select(player: Player, themeId: String, page: Int) {
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
            refresh(player, page)
        }
    }

    private fun buildInventory(player: Player, requestedPage: Int): Inventory {
        val orderedThemes = themes.all()
        val profile = storage.profile(player.uniqueId, player.name)
        val currentTheme = themes.firstAvailable(player, profile.selectedTheme)
        val unlockedCount = orderedThemes.count { themes.isUnlocked(player, it) }
        val pageSize = config.pageSlots.size.coerceAtLeast(1)
        val pageCount = pageCount(orderedThemes.size, pageSize)
        val safePage = requestedPage.coerceIn(0, pageCount - 1)
        val pageThemes = orderedThemes.drop(safePage * pageSize).take(pageSize)
        val placeholders = placeholders(
            player = player,
            total = orderedThemes.size,
            unlocked = unlockedCount,
            page = safePage,
            pages = pageCount,
            pageSize = pageSize,
            pageStart = if (orderedThemes.isEmpty()) 0 else (safePage * pageSize) + 1,
            pageEnd = minOf((safePage * pageSize) + pageThemes.size, orderedThemes.size)
        )
        val title = render(
            config.title,
            player,
            placeholders,
            mapOf(
                "current" to messages.component(currentTheme.displayName, player, placeholders),
                "current_theme" to messages.component(currentTheme.displayName, player, placeholders)
            )
        )
        val themeBySlot = linkedMapOf<Int, String>()
        val actionBySlot = linkedMapOf<Int, ThemeMenuAction>()
        val holder = ThemeMenuHolder(themeBySlot, actionBySlot, safePage)
        val inventory = Bukkit.createInventory(holder, config.size, title)
        holder.bind(inventory)
        fillStaticSlots(inventory, player, currentTheme, placeholders, safePage, pageCount, actionBySlot)
        fillThemeSlots(inventory, player, safePage, pageThemes, currentTheme, placeholders, themeBySlot, pageCount)
        return inventory
    }

    private fun fillStaticSlots(
        inventory: Inventory,
        player: Player,
        currentTheme: KillTheme,
        placeholders: Map<String, String>,
        page: Int,
        pageCount: Int,
        actionBySlot: MutableMap<Int, ThemeMenuAction>
    ) {
        val staticComponents = mapOf(
            "current" to messages.component(currentTheme.displayName, player, placeholders),
            "current_theme" to messages.component(currentTheme.displayName, player, placeholders)
        )
        for (slot in config.staticSlots) {
            val function = slot.key.iconFunction
            val itemConfig = when {
                function.equals("last", true) -> {
                    if (page > 0) {
                        actionBySlot[slot.slot] = ThemeMenuAction.PREVIOUS_PAGE
                        slot.key.has ?: slot.key.base
                    } else {
                        slot.key.normal ?: slot.key.base
                    }
                }
                function.equals("next", true) -> {
                    if (page < pageCount - 1) {
                        actionBySlot[slot.slot] = ThemeMenuAction.NEXT_PAGE
                        slot.key.has ?: slot.key.base
                    } else {
                        slot.key.normal ?: slot.key.base
                    }
                }
                function.equals("back", true) || function.equals("close", true) -> {
                    actionBySlot[slot.slot] = ThemeMenuAction.CLOSE
                    slot.key.base
                }
                else -> slot.key.base
            } ?: continue
            inventory.setItem(
                slot.slot,
                buildItem(
                    itemConfig,
                    player,
                    placeholders,
                    staticComponents,
                    fallbackMaterial = Material.BLACK_STAINED_GLASS_PANE
                )
            )
        }
    }

    private fun fillThemeSlots(
        inventory: Inventory,
        player: Player,
        page: Int,
        pageThemes: List<KillTheme>,
        currentTheme: KillTheme,
        placeholders: Map<String, String>,
        themeBySlot: MutableMap<Int, String>,
        pageCount: Int
    ) {
        val pageSize = config.pageSlots.size.coerceAtLeast(1)
        for ((index, slot) in config.pageSlots.withIndex()) {
            val theme = pageThemes.getOrNull(index)
            if (theme == null) {
                continue
            }
            themeBySlot[slot] = theme.id
            inventory.setItem(
                slot,
                buildThemeItem(
                    player = player,
                    theme = theme,
                    currentTheme = currentTheme,
                    absoluteIndex = (page * pageSize) + index + 1,
                    page = page,
                    pageCount = pageCount,
                    placeholders = placeholders
                )
            )
        }
    }

    private fun buildThemeItem(
        player: Player,
        theme: KillTheme,
        currentTheme: KillTheme,
        absoluteIndex: Int,
        page: Int,
        pageCount: Int,
        placeholders: Map<String, String>
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
        val itemPlaceholders = placeholders + mapOf(
            "theme" to theme.id,
            "theme_id" to theme.id,
            "theme_display" to theme.displayName,
            "theme_name" to theme.displayName,
            "permission" to theme.permission,
            "theme_permission" to theme.permission,
            "rarity" to theme.rarity,
            "theme_rarity" to theme.rarity,
            "theme_status" to status,
            "theme_message" to theme.message,
            "index" to absoluteIndex.toString(),
            "page" to (page + 1).toString(),
            "pages" to pageCount.toString()
        )
        val componentPlaceholders = mapOf(
            "display" to messages.component(theme.displayName, player, itemPlaceholders),
            "theme_display" to messages.component(theme.displayName, player, itemPlaceholders),
            "theme_name" to messages.component(theme.displayName, player, itemPlaceholders),
            "status" to messages.component(status, player, itemPlaceholders),
            "theme_status" to messages.component(status, player, itemPlaceholders),
            "current" to messages.component(currentTheme.displayName, player, itemPlaceholders),
            "current_theme" to messages.component(currentTheme.displayName, player, itemPlaceholders),
            "theme_preview" to messages.component(theme.message, player, themePreviewPlaceholders(player, theme))
        )
        return buildItem(
            template,
            player,
            itemPlaceholders,
            componentPlaceholders,
            if (unlocked) Material.LIME_DYE else Material.GRAY_DYE
        )
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
        val normalized = normalizeGuiText(template, placeholders.keys + componentPlaceholders.keys)
        return legacy.serialize(messages.component(normalized, player, placeholders, componentPlaceholders))
    }

    private fun normalizeGuiText(template: String, placeholderNames: Set<String>): String {
        var result = template.replace(Regex("%([A-Za-z0-9_.-]+)%")) { match ->
            val key = match.groupValues[1]
            if (key in placeholderNames) "<$key>" else match.value
        }
        result = result.replace(Regex("&#([A-Fa-f0-9]{6})")) { match -> "<#${match.groupValues[1]}>" }
        return result.replace(Regex("&([0-9A-FK-ORa-fk-or])")) { match ->
            legacyMiniMessageTag(match.groupValues[1][0])
        }
    }

    private fun legacyMiniMessageTag(code: Char): String {
        return when (code.lowercaseChar()) {
            '0' -> "<black>"
            '1' -> "<dark_blue>"
            '2' -> "<dark_green>"
            '3' -> "<dark_aqua>"
            '4' -> "<dark_red>"
            '5' -> "<dark_purple>"
            '6' -> "<gold>"
            '7' -> "<gray>"
            '8' -> "<dark_gray>"
            '9' -> "<blue>"
            'a' -> "<green>"
            'b' -> "<aqua>"
            'c' -> "<red>"
            'd' -> "<light_purple>"
            'e' -> "<yellow>"
            'f' -> "<white>"
            'k' -> "<obfuscated>"
            'l' -> "<bold>"
            'm' -> "<strikethrough>"
            'n' -> "<underlined>"
            'o' -> "<italic>"
            'r' -> "<reset>"
            else -> ""
        }
    }

    private fun currentPage(player: Player): Int {
        val holder = player.openInventory.topInventory.holder as? ThemeMenuHolder ?: return 0
        return holder.page
    }

    private fun placeholders(
        player: Player,
        total: Int,
        unlocked: Int,
        page: Int,
        pages: Int,
        pageSize: Int,
        pageStart: Int,
        pageEnd: Int
    ): Map<String, String> {
        return mapOf(
            "player" to player.name,
            "total" to total.toString(),
            "unlocked" to unlocked.toString(),
            "page" to (page + 1).toString(),
            "pages" to pages.toString(),
            "page_size" to pageSize.toString(),
            "page_start" to pageStart.toString(),
            "page_end" to pageEnd.toString()
        )
    }

    private fun themePreviewPlaceholders(player: Player, theme: KillTheme): Map<String, String> {
        return mapOf(
            "killer" to player.name,
            "victim" to "Steve",
            "mob" to "Zombie",
            "weapon" to (messages.rawOrNull("weapon.material.diamond_sword") ?: "Diamond Sword"),
            "world" to player.world.name,
            "killer_health" to "20",
            "victim_health" to "0",
            "distance" to "8.0",
            "streak" to "3",
            "max_streak" to "8",
            "death_cause" to "preview",
            "prefix" to messages.prefix,
            "theme" to theme.displayName,
            "theme_id" to theme.id,
            "rarity" to theme.rarity,
            "theme_rarity" to theme.rarity,
            "server" to plugin.server.name
        )
    }

    private fun pageCount(totalThemes: Int, pageSize: Int): Int {
        return ((totalThemes + pageSize - 1) / pageSize).coerceAtLeast(1)
    }
}
