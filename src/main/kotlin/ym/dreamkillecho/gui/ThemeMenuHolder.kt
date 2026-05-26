package ym.dreamkillecho.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class ThemeMenuHolder(
    private val themeBySlot: Map<Int, String>,
    private val actionBySlot: Map<Int, ThemeMenuAction>,
    val page: Int
) : InventoryHolder {
    private var inventory: Inventory? = null

    fun bind(inventory: Inventory) {
        this.inventory = inventory
    }

    fun themeId(slot: Int): String? = themeBySlot[slot]

    fun action(slot: Int): ThemeMenuAction? = actionBySlot[slot]

    override fun getInventory(): Inventory {
        return inventory ?: error("Theme menu inventory is not bound.")
    }
}

enum class ThemeMenuAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    CLOSE
}
