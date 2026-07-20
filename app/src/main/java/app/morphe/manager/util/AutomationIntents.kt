/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Intent
import android.net.Uri

object AutomationIntents {
    const val ACTION_PATCH = "app.morphe.manager.action.PATCH"
    const val EXTRA_PACKAGE_NAME = "app.morphe.manager.extra.PACKAGE_NAME"
    const val EXTRA_MODE = "app.morphe.manager.extra.MODE"
    const val EXTRA_SOURCE = "app.morphe.manager.extra.SOURCE"
    const val EXTRA_PATCH_ACTION = "app.morphe.manager.extra.ACTION"
    const val EXTRA_ALLOW_MULTIPLE_SOURCES = "app.morphe.manager.extra.ALLOW_MULTIPLE_SOURCES"
    const val EXTRA_PRE_PATCH_INSTALLER = "app.morphe.manager.extra.PRE_PATCH_INSTALLER"
    const val EXTRA_PATCH_SOURCE_UIDS = "app.morphe.manager.extra.PATCH_SOURCE_UIDS"
    const val EXTRA_PATCH_SELECTION_MODE = "app.morphe.manager.extra.PATCH_SELECTION_MODE"

    const val URI_SCHEME = "morphe"
    const val URI_HOST_PATCH = "patch"
    const val URI_HOST_PROFILE = "profile"
    const val URI_QUERY_PACKAGE = "package"
    const val URI_QUERY_PROFILE_ID = "id"
    const val URI_QUERY_MODE = "mode"
    const val URI_QUERY_SOURCE = "source"
    const val URI_QUERY_ACTION = "action"
    const val URI_QUERY_ALLOW_MULTIPLE_SOURCES = "allow_multiple_sources"
    const val URI_QUERY_PRE_PATCH_INSTALLER = "pre_patch_installer"
    const val URI_QUERY_PATCH_SOURCE_UIDS = "patch_sources"
    const val URI_QUERY_PATCH_SELECTION_MODE = "patch_selection"

    enum class Mode(val value: String) {
        INTERACTIVE("interactive"),
        SILENT("silent");

        companion object {
            fun from(value: String?): Mode =
                entries.firstOrNull { it.value == value?.trim()?.lowercase() } ?: INTERACTIVE
        }
    }

    enum class Source(val value: String) {
        DEFAULT("default"),
        SAVED("saved"),
        INSTALLED("installed");

        companion object {
            fun from(value: String?): Source =
                entries.firstOrNull { it.value == value?.trim()?.lowercase() } ?: DEFAULT
        }
    }

    enum class PatchAction(val value: String) {
        PREPARE("prepare"),
        START("start");

        companion object {
            fun from(value: String?): PatchAction =
                entries.firstOrNull { it.value == value?.trim()?.lowercase() } ?: PREPARE
        }
    }

    enum class PrePatchInstaller(val value: String) {
        MANAGER("manager"),
        PROMPT("prompt"),
        STANDARD("standard"),
        MOUNT("mount");

        companion object {
            fun from(value: String?): PrePatchInstaller =
                entries.firstOrNull { it.value == value?.trim()?.lowercase() } ?: MANAGER
        }
    }

    enum class PatchSelectionMode(val value: String) {
        SAVED("saved"),
        RECOMMENDED("recommended"),
        CUSTOM("custom");

        companion object {
            fun from(value: String?): PatchSelectionMode =
                entries.firstOrNull { it.value == value?.trim()?.lowercase() } ?: SAVED
        }
    }

    data class PatchRequest(
        val packageName: String,
        val mode: Mode = Mode.INTERACTIVE,
        val source: Source = Source.DEFAULT,
        val patchAction: PatchAction = PatchAction.PREPARE,
        val allowMultipleSources: Boolean = false,
        val prePatchInstaller: PrePatchInstaller = PrePatchInstaller.MANAGER,
        val patchSourceUids: Set<Int> = emptySet(),
        val patchSelectionMode: PatchSelectionMode = PatchSelectionMode.SAVED,
        val customPatchSelection: Map<Int, Set<String>> = emptyMap()
    )

    private val PACKAGE_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun normalizedPackageName(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        return normalized.takeIf { it.matches(PACKAGE_NAME_REGEX) }
    }

    fun patchRequest(intent: Intent?): PatchRequest? {
        if (intent == null) return null

        if (intent.action == ACTION_PATCH) {
            val uriRequest = intent.data?.patchRequest()
            val packageName = normalizedPackageName(intent.stringExtra(EXTRA_PACKAGE_NAME))
                ?: uriRequest?.packageName
                ?: return null
            return PatchRequest(
                packageName = packageName,
                mode = Mode.from(intent.stringExtra(EXTRA_MODE) ?: uriRequest?.mode?.value),
                source = Source.from(intent.stringExtra(EXTRA_SOURCE) ?: uriRequest?.source?.value),
                patchAction = PatchAction.from(
                    intent.stringExtra(EXTRA_PATCH_ACTION) ?: uriRequest?.patchAction?.value
                ),
                allowMultipleSources = intent.booleanExtra(EXTRA_ALLOW_MULTIPLE_SOURCES)
                    ?: uriRequest?.allowMultipleSources
                    ?: false,
                prePatchInstaller = PrePatchInstaller.from(
                    intent.stringExtra(EXTRA_PRE_PATCH_INSTALLER) ?: uriRequest?.prePatchInstaller?.value
                ),
                patchSourceUids = intent.intSetExtra(EXTRA_PATCH_SOURCE_UIDS)
                    ?: uriRequest?.patchSourceUids
                    ?: emptySet(),
                patchSelectionMode = PatchSelectionMode.from(
                    intent.stringExtra(EXTRA_PATCH_SELECTION_MODE) ?: uriRequest?.patchSelectionMode?.value
                ),
                customPatchSelection = uriRequest?.customPatchSelection.orEmpty()
            )
        }

        if (intent.action == Intent.ACTION_VIEW) {
            return intent.data?.patchRequest()
        }

        return null
    }

    fun profileId(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != URI_SCHEME || uri.host != URI_HOST_PROFILE) return null

        return uri.getQueryParameter(URI_QUERY_PROFILE_ID)?.takeIf { it.isNotBlank() }
            ?: uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun Uri.patchPackageName(): String? {
        val isPatchUri = scheme == URI_SCHEME && host == URI_HOST_PATCH
        if (!isPatchUri) return null

        return normalizedPackageName(getQueryParameter(URI_QUERY_PACKAGE))
            ?: normalizedPackageName(pathSegments.firstOrNull())
    }

    private fun Uri.patchRequest(): PatchRequest? {
        val packageName = patchPackageName() ?: return null
        return PatchRequest(
            packageName = packageName,
            mode = Mode.from(getQueryParameter(URI_QUERY_MODE)),
            source = Source.from(getQueryParameter(URI_QUERY_SOURCE)),
            patchAction = PatchAction.from(getQueryParameter(URI_QUERY_ACTION)),
            allowMultipleSources = getQueryParameter(URI_QUERY_ALLOW_MULTIPLE_SOURCES).asBoolean() ?: false,
            prePatchInstaller = PrePatchInstaller.from(getQueryParameter(URI_QUERY_PRE_PATCH_INSTALLER)),
            patchSourceUids = getQueryParameter(URI_QUERY_PATCH_SOURCE_UIDS).toIntSet(),
            patchSelectionMode = PatchSelectionMode.from(getQueryParameter(URI_QUERY_PATCH_SELECTION_MODE))
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.stringExtra(name: String): String? =
        extras?.get(name)?.toString()?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun Intent.booleanExtra(name: String): Boolean? =
        extras?.get(name)?.let { value ->
            when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.asBoolean()
                else -> null
            }
        }

    @Suppress("DEPRECATION")
    private fun Intent.intSetExtra(name: String): Set<Int>? =
        extras?.get(name)?.let { value ->
            when (value) {
                is IntArray -> value.toSet()
                is LongArray -> value.map { it.toInt() }.toSet()
                is Array<*> -> value.mapNotNull { it?.toString()?.toIntOrNull() }.toSet()
                is Iterable<*> -> value.mapNotNull { it?.toString()?.toIntOrNull() }.toSet()
                is Number -> setOf(value.toInt())
                is String -> value.toIntSet()
                else -> null
            }
        }?.takeIf { it.isNotEmpty() }

    private fun String?.toIntSet(): Set<Int> =
        this
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()

    private fun String?.asBoolean(): Boolean? =
        when (this?.trim()?.lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
}
