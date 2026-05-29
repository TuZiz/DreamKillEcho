package ym.dreamkillecho.review

import org.bukkit.entity.Player
import ym.dreamkillecho.config.CustomMessageSettings
import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.storage.PlayerProfile
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.util.PerKeyCooldown
import ym.dreamkillecho.util.Permissions
import java.util.concurrent.CompletableFuture

enum class CustomMessageResult {
    SAVED, PENDING, COOLDOWN, TOO_LONG, BLOCKED_WORD, INVALID_TAG
}

data class CustomMessageSetResult(
    val result: CustomMessageResult,
    val cooldownSeconds: Long = 0L
)

data class CustomMessagePreviewResult(
    val result: CustomMessageResult,
    val message: String? = null
)

class CustomMessageService(
    private val settings: CustomMessageSettings,
    private val storage: StorageService
) {
    private val cooldown = PerKeyCooldown(settings.cooldownSeconds * 1000L)

    fun set(player: Player, message: String): CustomMessageSetResult {
        val key = player.uniqueId.toString()
        if (message.length > settings.maxLength) return CustomMessageResult.TOO_LONG.asSetResult()
        if (settings.blockedWords.any { it.isNotBlank() && message.contains(it, ignoreCase = true) }) {
            return CustomMessageResult.BLOCKED_WORD.asSetResult()
        }
        val sanitized = sanitizeByPermission(player, message) ?: return CustomMessageResult.INVALID_TAG.asSetResult()
        if (!cooldown.tryAcquire(key)) {
            return CustomMessageSetResult(CustomMessageResult.COOLDOWN, cooldown.remainingMillis(key).ceilSeconds())
        }
        storage.updateProfile(player.uniqueId, player.name) { profile ->
            profile.customMessage = sanitized
            profile.customMessageUpdatedAt = System.currentTimeMillis()
            profile.customMessageStatus = if (settings.requireReview) CustomMessageStatus.PENDING else CustomMessageStatus.APPROVED
        }
        return (if (settings.requireReview) CustomMessageResult.PENDING else CustomMessageResult.SAVED).asSetResult()
    }

    fun reset(player: Player) {
        storage.updateProfile(player.uniqueId, player.name) { profile ->
            profile.customMessage = null
            profile.customMessageStatus = CustomMessageStatus.NONE
            profile.customMessageUpdatedAt = System.currentTimeMillis()
        }
    }

    fun preview(player: Player, message: String): CustomMessagePreviewResult {
        if (message.length > settings.maxLength) return CustomMessagePreviewResult(CustomMessageResult.TOO_LONG)
        if (settings.blockedWords.any { it.isNotBlank() && message.contains(it, ignoreCase = true) }) {
            return CustomMessagePreviewResult(CustomMessageResult.BLOCKED_WORD)
        }
        val sanitized = sanitizeByPermission(player, message) ?: return CustomMessagePreviewResult(CustomMessageResult.INVALID_TAG)
        return CustomMessagePreviewResult(CustomMessageResult.SAVED, sanitized)
    }

    fun pending(): List<PlayerProfile> {
        return storage.cachedProfiles().filter { it.customMessageStatus == CustomMessageStatus.PENDING }
            .sortedByDescending { it.customMessageUpdatedAt }
    }

    fun pendingAsync(): CompletableFuture<List<PlayerProfile>> {
        return storage.pendingCustomMessagesAsync()
    }

    fun approve(profile: PlayerProfile): Boolean {
        var changed = false
        storage.updateProfile(profile.uuid, profile.name) {
            if (it.customMessageStatus == CustomMessageStatus.PENDING) {
                it.customMessageStatus = CustomMessageStatus.APPROVED
                it.customMessageUpdatedAt = System.currentTimeMillis()
                changed = true
            }
        }
        return changed
    }

    fun deny(profile: PlayerProfile): Boolean {
        var changed = false
        storage.updateProfile(profile.uuid, profile.name) {
            if (it.customMessageStatus == CustomMessageStatus.PENDING) {
                it.customMessageStatus = CustomMessageStatus.DENIED
                it.customMessageUpdatedAt = System.currentTimeMillis()
                changed = true
            }
        }
        return changed
    }

    private fun containsDeniedTag(message: String): Boolean {
        val regex = Regex("<\\s*/?\\s*([a-zA-Z0-9_-]+)")
        return regex.findAll(message).any { match ->
            match.groupValues.getOrNull(1)?.lowercase() in effectiveDeniedTags
        }
    }

    private fun sanitizeByPermission(player: Player, message: String): String? {
        if (player.hasPermission(Permissions.CUSTOM_MINIMESSAGE)) {
            return if (containsDeniedTag(message)) null else message
        }
        if (player.hasPermission(Permissions.CUSTOM_COLOR)) {
            return if (containsDisallowedNonColorTag(message)) null else message
        }
        return escapeMiniMessage(message)
    }

    private fun containsDisallowedNonColorTag(message: String): Boolean {
        val tagRegex = Regex("<\\s*/?\\s*([a-zA-Z0-9_#:-]+)")
        return tagRegex.findAll(message).any { match ->
            val tag = match.groupValues.getOrNull(1)?.lowercase().orEmpty()
            !isColorTag(tag)
        }
    }

    private fun isColorTag(tag: String): Boolean {
        val base = tag.substringBefore(':')
        return base in allowedColorTags || hexColor.matches(base)
    }

    private fun escapeMiniMessage(message: String): String {
        return message.replace("\\", "\\\\").replace("<", "\\<")
    }

    private fun CustomMessageResult.asSetResult(): CustomMessageSetResult = CustomMessageSetResult(this)

    private fun Long.ceilSeconds(): Long {
        return if (this <= 0L) 0L else ((this + 999L) / 1000L)
    }

    private companion object {
        private val alwaysDeniedTags = setOf(
            "click", "hover", "insertion", "selector", "score", "nbt", "keybind", "translatable", "font", "transition"
        )
        private val allowedColorTags = setOf(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
            "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow",
            "white", "color", "colour", "gradient", "rainbow"
        )
        private val hexColor = Regex("#[0-9a-f]{6}")
    }

    private val effectiveDeniedTags = settings.deniedTags + alwaysDeniedTags
}
