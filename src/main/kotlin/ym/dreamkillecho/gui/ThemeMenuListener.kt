package ym.dreamkillecho.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import ym.dreamkillecho.DreamKillEcho

class ThemeMenuListener(private val plugin: DreamKillEcho) : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ThemeMenuHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val topSize = event.view.topInventory.size
        if (event.rawSlot !in 0 until topSize) return
        val themeId = holder.themeId(event.rawSlot) ?: return
        val services = plugin.services ?: return
        services.themeMenu.select(player, themeId)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        event.view.topInventory.holder as? ThemeMenuHolder ?: return
        val topSize = event.view.topInventory.size
        if (event.rawSlots.any { it in 0 until topSize }) {
            event.isCancelled = true
        }
    }
}
