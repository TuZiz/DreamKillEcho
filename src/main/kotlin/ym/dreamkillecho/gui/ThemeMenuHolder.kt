package ym.dreamkillecho.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class ThemeMenuHolder(private val themeBySlot: Map<Int, String>) : InventoryHolder {
    private var inventory: Inventory? = null

    fun bind(inventory: Inventory) {
        this.inventory = inventory
    }

    fun themeId(slot: Int): String? = themeBySlot[slot]

    override fun getInventory(): Inventory {
        return inventory ?: error("Theme menu inventory is not bound.")
    }
}
