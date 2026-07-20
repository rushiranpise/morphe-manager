/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager

object AutomationRequestGate {
    sealed interface BlockedReason {
        data object ExpertModeRequired : BlockedReason
        data object AutomationDisabled : BlockedReason
        data object SilentUnavailable : BlockedReason
        data class ActionDisabled(val action: AutomationIntents.PatchAction) : BlockedReason
        data class SourceDisabled(val source: AutomationIntents.Source) : BlockedReason
        data object MultipleSourcesDisabled : BlockedReason
        data object RootMountDisabled : BlockedReason
    }

    fun blockedReason(
        prefs: PreferencesManager,
        request: AutomationIntents.PatchRequest
    ): BlockedReason? {
        if (!prefs.useExpertMode.getBlocking()) {
            return BlockedReason.ExpertModeRequired
        }
        if (!prefs.externalAutomationEnabled.getBlocking()) {
            return BlockedReason.AutomationDisabled
        }
        if (request.mode == AutomationIntents.Mode.SILENT) {
            return BlockedReason.SilentUnavailable
        }

        val actionAllowed = when (request.patchAction) {
            AutomationIntents.PatchAction.PREPARE -> prefs.externalAutomationAllowPrepare.getBlocking()
            AutomationIntents.PatchAction.START -> prefs.externalAutomationAllowStart.getBlocking()
        }
        if (!actionAllowed) {
            return BlockedReason.ActionDisabled(request.patchAction)
        }

        val sourceAllowed = when (request.source) {
            AutomationIntents.Source.DEFAULT -> true
            AutomationIntents.Source.SAVED -> prefs.externalAutomationAllowSavedSource.getBlocking()
            AutomationIntents.Source.INSTALLED -> prefs.externalAutomationAllowInstalledSource.getBlocking()
        }
        if (!sourceAllowed) {
            return BlockedReason.SourceDisabled(request.source)
        }

        if (request.allowMultipleSources && !prefs.externalAutomationAllowMultipleSources.getBlocking()) {
            return BlockedReason.MultipleSourcesDisabled
        }

        if (
            request.prePatchInstaller == AutomationIntents.PrePatchInstaller.MOUNT &&
            !prefs.externalAutomationAllowRootMount.getBlocking()
        ) {
            return BlockedReason.RootMountDisabled
        }

        return null
    }

    fun blockedMessage(context: Context, reason: BlockedReason): String =
        when (reason) {
            BlockedReason.ExpertModeRequired ->
                context.getString(R.string.automation_intents_requires_expert_mode)
            BlockedReason.AutomationDisabled ->
                context.getString(R.string.automation_intents_disabled)
            BlockedReason.SilentUnavailable ->
                context.getString(R.string.automation_intents_silent_unavailable)
            is BlockedReason.ActionDisabled -> {
                val action = when (reason.action) {
                    AutomationIntents.PatchAction.PREPARE ->
                        context.getString(R.string.automation_intents_action_prepare)
                    AutomationIntents.PatchAction.START ->
                        context.getString(R.string.automation_intents_action_start)
                }
                context.getString(R.string.automation_intents_action_disabled, action)
            }
            is BlockedReason.SourceDisabled -> {
                val source = when (reason.source) {
                    AutomationIntents.Source.DEFAULT ->
                        context.getString(R.string.automation_intents_source_default)
                    AutomationIntents.Source.SAVED ->
                        context.getString(R.string.automation_intents_source_saved)
                    AutomationIntents.Source.INSTALLED ->
                        context.getString(R.string.automation_intents_source_installed)
                }
                context.getString(R.string.automation_intents_source_disabled, source)
            }
            BlockedReason.MultipleSourcesDisabled ->
                context.getString(R.string.automation_intents_multiple_sources_disabled)
            BlockedReason.RootMountDisabled ->
                context.getString(R.string.automation_intents_mount_disabled)
        }
}
