package ym.dreamkillecho.review

import org.bukkit.entity.Player
import ym.dreamkillecho.config.CustomMessageSettings
import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.storage.PlayerProfile
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.util.PerKeyCooldown

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
        val profile = storage.profile(player.uniqueId, player.name)
        profile.customMessage = message
        profile.customMessageUpdatedAt = System.currentTimeMillis()
        profile.customMessageStatus = if (settings.requireReview) CustomMessageStatus.PENDING else CustomMessageStatus.APPROVED
        storage.markProfileDirty(player.uniqueId)
        return if (settings.requireReview) CustomMessageResult.PENDING else CustomMessageResult.SAVED
    }

    fun reset(player: Player) {
        val profile = storage.profile(player.uniqueId, player.name)
        profile.customMessage = null
        profile.customMessageStatus = CustomMessageStatus.NONE
        profile.customMessageUpdatedAt = System.currentTimeMillis()
        storage.markProfileDirty(player.uniqueId)
    }

    fun pending(): List<PlayerProfile> {
        return storage.cachedProfiles().filter { it.customMessageStatus == CustomMessageStatus.PENDING }
            .sortedByDescending { it.customMessageUpdatedAt }
    }

    fun approve(profile: PlayerProfile): Boolean {
        if (profile.customMessageStatus != CustomMessageStatus.PENDING) return false
        profile.customMessageStatus = CustomMessageStatus.APPROVED
        profile.customMessageUpdatedAt = System.currentTimeMillis()
        storage.markProfileDirty(profile.uuid)
        return true
    }

    fun deny(profile: PlayerProfile): Boolean {
        if (profile.customMessageStatus != CustomMessageStatus.PENDING) return false
        profile.customMessageStatus = CustomMessageStatus.DENIED
        profile.customMessageUpdatedAt = System.currentTimeMillis()
        storage.markProfileDirty(profile.uuid)
        return true
    }

    private fun containsDeniedTag(message: String): Boolean {
        val regex = Regex("<\\s*/?\\s*([a-zA-Z0-9_-]+)")
        return regex.findAll(message).any { match ->
            match.groupValues.getOrNull(1)?.lowercase() in settings.deniedTags
        }
    }
}
