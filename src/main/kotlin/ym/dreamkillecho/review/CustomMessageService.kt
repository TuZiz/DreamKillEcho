package ym.dreamkillecho.review

import org.bukkit.entity.Player
import ym.dreamkillecho.config.CustomMessageSettings
import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.storage.PlayerProfile
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.util.PerKeyCooldown
import java.util.concurrent.CompletableFuture

enum class CustomMessageResult {
    SAVED, PENDING, COOLDOWN, TOO_LONG, BLOCKED_WORD, INVALID_TAG
}

class CustomMessageService(
    private val settings: CustomMessageSettings,
    private val storage: StorageService
) {
    private val cooldown = PerKeyCooldown(settings.cooldownSeconds * 1000L)

    fun set(player: Player, message: String): CustomMessageResult {
        if (!cooldown.tryAcquire(player.uniqueId.toString())) return CustomMessageResult.COOLDOWN
        if (message.length > settings.maxLength) return CustomMessageResult.TOO_LONG
        if (settings.blockedWords.any { it.isNotBlank() && message.contains(it, ignoreCase = true) }) return CustomMessageResult.BLOCKED_WORD
        if (containsDeniedTag(message)) return CustomMessageResult.INVALID_TAG
        storage.updateProfile(player.uniqueId, player.name) { profile ->
            profile.customMessage = message
            profile.customMessageUpdatedAt = System.currentTimeMillis()
            profile.customMessageStatus = if (settings.requireReview) CustomMessageStatus.PENDING else CustomMessageStatus.APPROVED
        }
        return if (settings.requireReview) CustomMessageResult.PENDING else CustomMessageResult.SAVED
    }

    fun reset(player: Player) {
        storage.updateProfile(player.uniqueId, player.name) { profile ->
            profile.customMessage = null
            profile.customMessageStatus = CustomMessageStatus.NONE
            profile.customMessageUpdatedAt = System.currentTimeMillis()
        }
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
            match.groupValues.getOrNull(1)?.lowercase() in settings.deniedTags
        }
    }
}
