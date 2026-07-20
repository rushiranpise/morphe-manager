/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AutomationProfiles {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Profile(
        val id: String,
        val name: String,
        val packageName: String,
        val source: String,
        val patchAction: String,
        val allowMultipleSources: Boolean,
        val prePatchInstaller: String = AutomationIntents.PrePatchInstaller.MANAGER.value,
        val patchSelectionMode: String = AutomationIntents.PatchSelectionMode.SAVED.value,
        val patchSourceUids: List<Int> = emptyList(),
        val customPatchSelection: Map<String, List<String>> = emptyMap(),
        val createdAt: Long
    ) {
        fun toRequest() = AutomationIntents.PatchRequest(
            packageName = packageName,
            source = AutomationIntents.Source.from(source),
            patchAction = AutomationIntents.PatchAction.from(patchAction),
            allowMultipleSources = allowMultipleSources,
            prePatchInstaller = AutomationIntents.PrePatchInstaller.from(prePatchInstaller),
            patchSourceUids = patchSourceUids.toSet(),
            patchSelectionMode = AutomationIntents.PatchSelectionMode.from(patchSelectionMode),
            customPatchSelection = customPatchSelection.mapNotNull { (uid, patches) ->
                uid.toIntOrNull()?.let { it to patches.toSet() }
            }.toMap()
        )
    }

    fun decode(raw: String): List<Profile> =
        runCatching { json.decodeFromString<List<Profile>>(raw) }.getOrDefault(emptyList())

    fun encode(profiles: List<Profile>): String = json.encodeToString(profiles)

    fun profileUri(id: String): String =
        "${AutomationIntents.URI_SCHEME}://${AutomationIntents.URI_HOST_PROFILE}/$id"

    fun createId(packageName: String): String =
        "$packageName-${System.currentTimeMillis()}"

    fun upsert(raw: String, profile: Profile): String {
        val existing = decode(raw)
        val next = existing
            .filterNot { it.id == profile.id }
            .plus(profile)
            .sortedBy { it.name.lowercase() }
        return encode(next)
    }

    fun remove(raw: String, id: String): String =
        encode(decode(raw).filterNot { it.id == id })
}
