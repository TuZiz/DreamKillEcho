package ym.dreamkillecho.stats

import ym.dreamkillecho.storage.PlayerStats

object StatsFormatter {
    fun placeholders(stats: PlayerStats): Map<String, String> = mapOf(
        "kills" to stats.kills.toString(),
        "deaths" to stats.deaths.toString(),
        "streak" to stats.currentStreak.toString(),
        "max_streak" to stats.maxStreak.toString()
    )
}
