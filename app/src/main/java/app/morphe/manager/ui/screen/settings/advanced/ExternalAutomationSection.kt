/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.AutomationIntents
import app.morphe.manager.util.AutomationProfiles

@Composable
fun ColumnScope.ExternalAutomationSettingsItem(
    settingsViewModel: SettingsViewModel,
    onOpen: () -> Unit
) {
    val prefs = settingsViewModel.prefs
    val enabled by prefs.externalAutomationEnabled.getAsState()
    val profilesRaw by prefs.automationProfiles.getAsState()
    val profileCount = remember(profilesRaw) { AutomationProfiles.decode(profilesRaw).size }
    val stateLabel = stringResource(if (enabled) R.string.enabled else R.string.disabled)

    MorpheSettingsDivider()

    SettingsItem(
        onClick = onOpen,
        leadingContent = {
            MorpheIcon(icon = Icons.Outlined.Extension)
        },
        title = stringResource(R.string.settings_advanced_external_automation),
        subtitle = stringResource(
            R.string.settings_advanced_external_automation_summary,
            stateLabel,
            profileCount
        )
    )
}

@Composable
fun ExternalAutomationScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRunAutomationProfile: (AutomationIntents.PatchRequest) -> Unit
) {
    BackHandler(onBack = onBack)

    val prefs = settingsViewModel.prefs
    val externalAutomationEnabled by prefs.externalAutomationEnabled.getAsState()
    val allowPrepare by prefs.externalAutomationAllowPrepare.getAsState()
    val allowStart by prefs.externalAutomationAllowStart.getAsState()
    val allowSavedSource by prefs.externalAutomationAllowSavedSource.getAsState()
    val allowInstalledSource by prefs.externalAutomationAllowInstalledSource.getAsState()
    val allowMultipleSources by prefs.externalAutomationAllowMultipleSources.getAsState()
    val allowRootMount by prefs.externalAutomationAllowRootMount.getAsState()
    val profilesRaw by prefs.automationProfiles.getAsState()
    val profiles = remember(profilesRaw) { AutomationProfiles.decode(profilesRaw) }
    val profileOptions by settingsViewModel.automationProfileOptions.collectAsStateWithLifecycle()

    val showWarningDialog = remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AutomationProfiles.Profile?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    if (showWarningDialog.value) {
        AutomationIntentsWarningDialog(
            onDismiss = { showWarningDialog.value = false },
            onConfirm = {
                settingsViewModel.setExternalAutomationEnabled(true)
                showWarningDialog.value = false
            }
        )
    }

    if (showProfileEditor) {
        AutomationProfileEditorDialog(
            profile = editingProfile,
            profiles = profiles,
            options = profileOptions,
            onDismiss = {
                showProfileEditor = false
                editingProfile = null
            },
            onSave = { profile, name, packageName, source, action, allowMultiple, prePatch, mode, sourceUids, customSelection ->
                settingsViewModel.saveAutomationProfile(
                    existingProfile = profile,
                    name = name,
                    packageName = packageName,
                    source = source,
                    patchAction = action,
                    allowMultipleSources = allowMultiple,
                    prePatchInstaller = prePatch,
                    patchSelectionMode = mode,
                    patchSourceUids = sourceUids,
                    customPatchSelection = customSelection
                )
                showProfileEditor = false
                editingProfile = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }

            Text(
                text = stringResource(R.string.settings_advanced_external_automation),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = rememberWindowSize().contentPadding,
                    vertical = MorpheDefaults.ContentPadding
                ),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            SectionTitle(
                text = stringResource(R.string.automation_access),
                icon = Icons.Outlined.Security
            )

            SettingsGroup {
                SettingsItem(
                    onClick = {
                        if (!externalAutomationEnabled) showWarningDialog.value = true
                        else settingsViewModel.setExternalAutomationEnabled(false)
                    },
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Extension)
                    },
                    title = stringResource(R.string.settings_advanced_external_automation),
                    subtitle = stringResource(R.string.settings_advanced_external_automation_description),
                    trailingContent = {
                        MorpheSwitch(
                            checked = externalAutomationEnabled,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription =
                                    if (externalAutomationEnabled) enabledState else disabledState
                            }
                        )
                    }
                )

                if (externalAutomationEnabled) {
                    MorpheSettingsDivider()

                    AutomationToggleItem(
                        title = stringResource(R.string.settings_advanced_external_automation_allow_prepare),
                        subtitle = stringResource(R.string.settings_advanced_external_automation_allow_prepare_description),
                        checked = allowPrepare,
                        enabledState = enabledState,
                        disabledState = disabledState,
                        onClick = { settingsViewModel.setExternalAutomationAllowPrepare(!allowPrepare) }
                    )

                    MorpheSettingsDivider()

                    AutomationToggleItem(
                        title = stringResource(R.string.settings_advanced_external_automation_allow_start),
                        subtitle = stringResource(R.string.settings_advanced_external_automation_allow_start_description),
                        checked = allowStart,
                        enabledState = enabledState,
                        disabledState = disabledState,
                        onClick = { settingsViewModel.setExternalAutomationAllowStart(!allowStart) }
                    )

                    MorpheSettingsDivider()

                    AutomationToggleItem(
                        title = stringResource(R.string.settings_advanced_external_automation_allow_saved_source),
                        subtitle = stringResource(R.string.settings_advanced_external_automation_allow_saved_source_description),
                        checked = allowSavedSource,
                        enabledState = enabledState,
                        disabledState = disabledState,
                        onClick = { settingsViewModel.setExternalAutomationAllowSavedSource(!allowSavedSource) }
                    )

                    MorpheSettingsDivider()

                    AutomationToggleItem(
                        title = stringResource(R.string.settings_advanced_external_automation_allow_installed_source),
                        subtitle = stringResource(R.string.settings_advanced_external_automation_allow_installed_source_description),
                        checked = allowInstalledSource,
                        enabledState = enabledState,
                        disabledState = disabledState,
                        onClick = {
                            settingsViewModel.setExternalAutomationAllowInstalledSource(!allowInstalledSource)
                        }
                    )

                    MorpheSettingsDivider()

                    AutomationToggleItem(
                        title = stringResource(R.string.settings_advanced_external_automation_allow_multiple_sources),
                        subtitle = stringResource(R.string.settings_advanced_external_automation_allow_multiple_sources_description),
                        checked = allowMultipleSources,
                        enabledState = enabledState,
                        disabledState = disabledState,
                        onClick = {
                            settingsViewModel.setExternalAutomationAllowMultipleSources(!allowMultipleSources)
                        }
                    )

                    MorpheSettingsDivider()

                    AutomationToggleItem(
                        title = stringResource(R.string.settings_advanced_external_automation_allow_root_mount),
                        subtitle = stringResource(R.string.settings_advanced_external_automation_allow_root_mount_description),
                        checked = allowRootMount,
                        enabledState = enabledState,
                        disabledState = disabledState,
                        onClick = { settingsViewModel.setExternalAutomationAllowRootMount(!allowRootMount) }
                    )
                }
            }

            SectionTitle(
                text = stringResource(R.string.settings_advanced_automation_profiles),
                icon = Icons.Outlined.Bookmarks
            )

            InfoBadge(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.automation_profiles_description),
                isExpanded = true
            )

            SettingsGroup {
                SettingsItem(
                    onClick = {
                        editingProfile = null
                        showProfileEditor = true
                    },
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Add)
                    },
                    title = stringResource(R.string.automation_profiles_create),
                    subtitle = stringResource(R.string.automation_profiles_create_item_description)
                )
            }

            if (profiles.isEmpty()) {
                SettingsItemCard(onClick = null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MorpheDefaults.ContentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
                    ) {
                        MorpheIcon(icon = Icons.Outlined.Bookmarks)
                        Text(
                            text = stringResource(R.string.automation_profiles_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                profiles.forEach { profile ->
                    AutomationProfileRow(
                        profile = profile,
                        onRun = {
                            settingsViewModel.runAutomationProfile(profile) { request ->
                                onRunAutomationProfile(request)
                            }
                        },
                        onEdit = {
                            editingProfile = profile
                            showProfileEditor = true
                        },
                        onShortcut = {
                            settingsViewModel.requestAutomationProfileShortcut(profile)
                        },
                        onDelete = {
                            settingsViewModel.deleteAutomationProfile(profile.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabledState: String,
    disabledState: String,
    onClick: () -> Unit
) {
    SettingsItem(
        onClick = onClick,
        title = title,
        subtitle = subtitle,
        trailingContent = {
            MorpheSwitch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    stateDescription = if (checked) enabledState else disabledState
                }
            )
        }
    )
}

@Composable
private fun AutomationProfileRow(
    profile: AutomationProfiles.Profile,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onShortcut: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceLabel = when (AutomationIntents.Source.from(profile.source)) {
        AutomationIntents.Source.DEFAULT -> stringResource(R.string.automation_intents_source_default)
        AutomationIntents.Source.SAVED -> stringResource(R.string.automation_intents_source_saved)
        AutomationIntents.Source.INSTALLED -> stringResource(R.string.automation_intents_source_installed)
    }
    val actionLabel = when (AutomationIntents.PatchAction.from(profile.patchAction)) {
        AutomationIntents.PatchAction.PREPARE -> stringResource(R.string.automation_intents_action_prepare)
        AutomationIntents.PatchAction.START -> stringResource(R.string.automation_intents_action_start)
    }
    val installerLabel = when (AutomationIntents.PrePatchInstaller.from(profile.prePatchInstaller)) {
        AutomationIntents.PrePatchInstaller.MANAGER -> stringResource(R.string.automation_profiles_pre_patch_manager)
        AutomationIntents.PrePatchInstaller.PROMPT -> stringResource(R.string.automation_profiles_pre_patch_prompt)
        AutomationIntents.PrePatchInstaller.STANDARD -> stringResource(R.string.automation_profiles_pre_patch_standard)
        AutomationIntents.PrePatchInstaller.MOUNT -> stringResource(R.string.automation_profiles_pre_patch_mount)
    }
    val selectionLabel = when (AutomationIntents.PatchSelectionMode.from(profile.patchSelectionMode)) {
        AutomationIntents.PatchSelectionMode.SAVED -> stringResource(R.string.automation_profiles_patch_selection_saved)
        AutomationIntents.PatchSelectionMode.RECOMMENDED -> stringResource(R.string.automation_profiles_patch_selection_recommended)
        AutomationIntents.PatchSelectionMode.CUSTOM -> stringResource(R.string.automation_profiles_patch_selection_custom)
    }
    val sourceCountLabel = if (profile.patchSourceUids.isEmpty()) {
        stringResource(R.string.automation_profiles_all_sources)
    } else {
        stringResource(R.string.automation_profiles_selected_sources, profile.patchSourceUids.size)
    }

    SettingsItemCard(onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            MorpheIcon(icon = Icons.Outlined.Bookmark)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$sourceLabel - $actionLabel - $selectionLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$sourceCountLabel - $installerLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalIconButton(
                onClick = onRun,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(R.string.automation_profiles_run),
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalIconButton(
                onClick = onShortcut,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.automation_profiles_shortcut),
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalIconButton(
                onClick = onDelete,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AutomationProfileEditorDialog(
    profile: AutomationProfiles.Profile?,
    profiles: List<AutomationProfiles.Profile>,
    options: SettingsViewModel.AutomationProfileOptions,
    onDismiss: () -> Unit,
    onSave: (
        profile: AutomationProfiles.Profile?,
        name: String,
        packageName: String,
        source: AutomationIntents.Source,
        action: AutomationIntents.PatchAction,
        allowMultipleSources: Boolean,
        prePatchInstaller: AutomationIntents.PrePatchInstaller,
        patchSelectionMode: AutomationIntents.PatchSelectionMode,
        patchSourceUids: Set<Int>,
        customPatchSelection: Map<Int, Set<String>>
    ) -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var sourceSearch by rememberSaveable(profile?.id) { mutableStateOf("") }
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var packageName by rememberSaveable(profile?.id) { mutableStateOf(profile?.packageName.orEmpty()) }
    var source by rememberSaveable(profile?.id) {
        mutableStateOf(AutomationIntents.Source.from(profile?.source))
    }
    var action by rememberSaveable(profile?.id) {
        mutableStateOf(AutomationIntents.PatchAction.from(profile?.patchAction))
    }
    var allowMultipleSources by rememberSaveable(profile?.id) {
        mutableStateOf(profile?.allowMultipleSources ?: false)
    }
    var prePatchInstaller by rememberSaveable(profile?.id) {
        mutableStateOf(AutomationIntents.PrePatchInstaller.from(profile?.prePatchInstaller))
    }
    var patchSelectionMode by rememberSaveable(profile?.id) {
        mutableStateOf(AutomationIntents.PatchSelectionMode.from(profile?.patchSelectionMode))
    }
    var selectedSourceUids by remember(profile?.id) {
        mutableStateOf(profile?.patchSourceUids.orEmpty().toSet())
    }
    var customPatchSelection by remember(profile?.id) {
        mutableStateOf(
            profile?.customPatchSelection
                ?.mapNotNull { (uid, patches) -> uid.toIntOrNull()?.let { it to patches.toSet() } }
                ?.toMap()
                .orEmpty()
        )
    }
    var initializedSources by remember(profile?.id) { mutableStateOf(false) }

    val selectedApp = remember(options.apps, packageName) {
        options.apps.firstOrNull { it.packageName == packageName }
    }
    val sourcesForApp = remember(options.sourcesByPackage, packageName) {
        options.sourcesByPackage[packageName].orEmpty()
    }
    val defaultSourcesForApp = remember(sourcesForApp) {
        sourcesForApp.filter { it.hasTargetPatches }
    }
    val sourceOptions = remember(sourcesForApp, defaultSourcesForApp, selectedSourceUids, sourceSearch, allowMultipleSources) {
        if (!allowMultipleSources) {
            defaultSourcesForApp
        } else {
            val query = sourceSearch.trim().lowercase()
            sourcesForApp.filter { sourceOption ->
                val alreadySelected = sourceOption.uid in selectedSourceUids
                val defaultVisible = sourceOption.hasTargetPatches
                val matchesSearch = query.isNotBlank() && (
                        sourceOption.name.lowercase().contains(query) ||
                                sourceOption.patches.any { it.name.lowercase().contains(query) }
                        )
                alreadySelected || defaultVisible || matchesSearch
            }
        }
    }
    val selectedSources = remember(sourcesForApp, selectedSourceUids) {
        sourcesForApp.filter { it.uid in selectedSourceUids }
    }
    val hasDuplicatePackage = packageName.isNotBlank() &&
            profiles.any { it.id != profile?.id && it.packageName == packageName }

    fun recommendedSelectionFor(
        sources: List<SettingsViewModel.AutomationProfileSourceOption>
    ): Map<Int, Set<String>> =
        sources.associate { sourceOption ->
            sourceOption.uid to sourceOption.patches
                .filter { it.recommended }
                .mapTo(mutableSetOf()) { it.name }
        }.filterValues { it.isNotEmpty() }

    LaunchedEffect(packageName, sourcesForApp.map { it.uid }) {
        val availableUids = sourcesForApp.mapTo(mutableSetOf()) { it.uid }
        selectedSourceUids = selectedSourceUids.filter { it in availableUids }.toSet()
        customPatchSelection = customPatchSelection.filterKeys { it in availableUids }

        if (!initializedSources && packageName.isNotBlank() && sourcesForApp.isNotEmpty()) {
            if (selectedSourceUids.isEmpty()) {
                selectedSourceUids = defaultSourcesForApp
                    .mapTo(mutableSetOf()) { it.uid }
                    .ifEmpty { availableUids }
            }
            if (customPatchSelection.isEmpty()) {
                customPatchSelection = recommendedSelectionFor(
                    sourcesForApp.filter { it.uid in selectedSourceUids }
                )
            }
            initializedSources = true
        }
    }

    LaunchedEffect(selectedSourceUids.size) {
        if (selectedSourceUids.size > 1) allowMultipleSources = true
    }

    if (showAppPicker) {
        AutomationAppPickerDialog(
            apps = options.apps,
            onDismiss = { showAppPicker = false },
            onSelect = { app ->
                packageName = app.packageName
                if (name.isBlank()) name = app.displayName
                val appSources = options.sourcesByPackage[app.packageName].orEmpty()
                val defaultSources = appSources.filter { it.hasTargetPatches }
                selectedSourceUids = defaultSources
                    .mapTo(mutableSetOf()) { it.uid }
                    .ifEmpty { appSources.mapTo(mutableSetOf()) { it.uid } }
                sourceSearch = ""
                customPatchSelection = recommendedSelectionFor(appSources.filter { it.uid in selectedSourceUids })
                initializedSources = true
                showAppPicker = false
            }
        )
    }

    val selectedCustomPatchSelection = remember(customPatchSelection, selectedSourceUids) {
        customPatchSelection
            .filterKeys { it in selectedSourceUids }
            .filterValues { it.isNotEmpty() }
    }
    val hasCustomPatches = selectedCustomPatchSelection.values.sumOf { it.size } > 0
    val canSave = selectedApp != null &&
            sourcesForApp.isNotEmpty() &&
            selectedSourceUids.isNotEmpty() &&
            (patchSelectionMode != AutomationIntents.PatchSelectionMode.CUSTOM || hasCustomPatches) &&
            !hasDuplicatePackage

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (profile == null) {
                R.string.automation_profiles_create_title
            } else {
                R.string.automation_profiles_edit_title
            }
        ),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.automation_profiles_save),
                onPrimaryClick = {
                    onSave(
                        profile,
                        name,
                        packageName,
                        source,
                        action,
                        allowMultipleSources,
                        prePatchInstaller,
                        patchSelectionMode,
                        selectedSourceUids,
                        if (patchSelectionMode == AutomationIntents.PatchSelectionMode.CUSTOM) {
                            selectedCustomPatchSelection
                        } else {
                            emptyMap()
                        }
                    )
                },
                primaryEnabled = canSave,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            Text(
                text = stringResource(R.string.automation_profiles_create_description),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            MorpheDialogTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.automation_profiles_name)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = null
                    )
                },
                showClearButton = true
            )

            ProfileSelectorItem(
                title = stringResource(R.string.automation_profiles_target_app),
                subtitle = selectedApp?.let { "${it.displayName} - ${it.packageName}" }
                    ?: stringResource(R.string.automation_profiles_target_app_empty),
                icon = Icons.Outlined.Apps,
                onClick = { showAppPicker = true }
            )

            if (selectedApp != null && sourcesForApp.isEmpty()) {
                InfoBadge(
                    icon = Icons.Outlined.SearchOff,
                    text = stringResource(R.string.automation_profiles_no_sources_for_app),
                    style = InfoBadgeStyle.Warning,
                    isExpanded = true
                )
            }

            if (hasDuplicatePackage) {
                InfoBadge(
                    icon = Icons.Outlined.Warning,
                    text = stringResource(R.string.automation_profiles_duplicate_package),
                    style = InfoBadgeStyle.Warning,
                    isExpanded = true
                )
            }

            ProfileChoiceGroup(
                title = stringResource(R.string.automation_profiles_source),
                options = listOf(
                    AutomationIntents.Source.SAVED to stringResource(R.string.automation_intents_source_saved),
                    AutomationIntents.Source.INSTALLED to stringResource(R.string.automation_intents_source_installed),
                    AutomationIntents.Source.DEFAULT to stringResource(R.string.automation_intents_source_default)
                ),
                selected = source,
                onSelect = { source = it }
            )

            ProfileChoiceGroup(
                title = stringResource(R.string.automation_profiles_action),
                options = listOf(
                    AutomationIntents.PatchAction.START to stringResource(R.string.automation_intents_action_start),
                    AutomationIntents.PatchAction.PREPARE to stringResource(R.string.automation_intents_action_prepare)
                ),
                selected = action,
                onSelect = { action = it }
            )

            ProfileChoiceGroup(
                title = stringResource(R.string.automation_profiles_patch_selection),
                options = listOf(
                    AutomationIntents.PatchSelectionMode.SAVED to
                            stringResource(R.string.automation_profiles_patch_selection_saved),
                    AutomationIntents.PatchSelectionMode.RECOMMENDED to
                            stringResource(R.string.automation_profiles_patch_selection_recommended),
                    AutomationIntents.PatchSelectionMode.CUSTOM to
                            stringResource(R.string.automation_profiles_patch_selection_custom)
                ),
                selected = patchSelectionMode,
                onSelect = {
                    patchSelectionMode = it
                    if (it == AutomationIntents.PatchSelectionMode.CUSTOM && customPatchSelection.isEmpty()) {
                        customPatchSelection = recommendedSelectionFor(selectedSources)
                    }
                }
            )

            ProfileChoiceGroup(
                title = stringResource(R.string.automation_profiles_pre_patch),
                options = listOf(
                    AutomationIntents.PrePatchInstaller.MANAGER to
                            stringResource(R.string.automation_profiles_pre_patch_manager),
                    AutomationIntents.PrePatchInstaller.PROMPT to
                            stringResource(R.string.automation_profiles_pre_patch_prompt),
                    AutomationIntents.PrePatchInstaller.MOUNT to
                            stringResource(R.string.automation_profiles_pre_patch_mount)
                ),
                selected = prePatchInstaller,
                onSelect = { prePatchInstaller = it }
            )

            SettingsGroup {
                SettingsItem(
                    onClick = { allowMultipleSources = !allowMultipleSources },
                    title = stringResource(R.string.automation_profiles_allow_multiple_sources),
                    subtitle = stringResource(
                        R.string.settings_advanced_external_automation_allow_multiple_sources_description
                    ),
                    trailingContent = {
                        MorpheSwitch(
                            checked = allowMultipleSources,
                            onCheckedChange = null
                        )
                    }
                )
            }

            if (allowMultipleSources && sourcesForApp.size > defaultSourcesForApp.size) {
                MorpheDialogTextField(
                    value = sourceSearch,
                    onValueChange = { sourceSearch = it },
                    placeholder = { Text(stringResource(R.string.automation_profiles_source_search)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    showClearButton = true
                )
            }

            ProfileMultiChoiceGroup(
                title = stringResource(R.string.automation_profiles_patch_sources),
                options = sourceOptions.map { sourceOption ->
                    ProfileChoice(
                        value = sourceOption.uid,
                        title = sourceOption.name,
                        subtitle = stringResource(
                            R.string.automation_profiles_patch_count,
                            sourceOption.patches.size
                        )
                    )
                },
                selected = selectedSourceUids,
                emptyText = stringResource(R.string.automation_profiles_select_app_first),
                onToggle = { uid ->
                    val sourceOption = sourcesForApp.firstOrNull { it.uid == uid }
                    selectedSourceUids = if (uid in selectedSourceUids) {
                        selectedSourceUids - uid
                    } else {
                        selectedSourceUids + uid
                    }
                    if (sourceOption != null && uid in selectedSourceUids && uid !in customPatchSelection) {
                        val recommended = sourceOption.patches
                            .filter { it.recommended }
                            .mapTo(mutableSetOf()) { it.name }
                        if (recommended.isNotEmpty()) {
                            customPatchSelection = customPatchSelection + (uid to recommended)
                        }
                    }
                }
            )

            if (patchSelectionMode == AutomationIntents.PatchSelectionMode.CUSTOM) {
                CustomPatchSelectionSection(
                    sources = selectedSources,
                    selectedPatches = customPatchSelection,
                    onToggle = { uid, patchName ->
                        val current = customPatchSelection[uid].orEmpty()
                        val next = if (patchName in current) current - patchName else current + patchName
                        customPatchSelection = if (next.isEmpty()) {
                            customPatchSelection - uid
                        } else {
                            customPatchSelection + (uid to next)
                        }
                    }
                )
            }

        }
    }
}

@Composable
private fun AutomationAppPickerDialog(
    apps: List<SettingsViewModel.AutomationProfileAppOption>,
    onDismiss: () -> Unit,
    onSelect: (SettingsViewModel.AutomationProfileAppOption) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.displayName.lowercase().contains(normalized) ||
                        app.packageName.lowercase().contains(normalized)
            }
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.automation_profiles_select_app),
        scrollable = false,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            MorpheDialogTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                showClearButton = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (filteredApps.isEmpty()) {
                SettingsItemCard(onClick = null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MorpheDefaults.ContentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
                    ) {
                        MorpheIcon(icon = Icons.Outlined.SearchOff)
                        Text(
                            text = stringResource(R.string.automation_profiles_no_matching_apps),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        SettingsItem(
                            onClick = { onSelect(app) },
                            leadingContent = { MorpheIcon(icon = Icons.Outlined.Android) },
                            title = app.displayName,
                            subtitle = app.packageName
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSelectorItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    SettingsGroup {
        SettingsItem(
            onClick = onClick,
            leadingContent = { MorpheIcon(icon = icon) },
            title = title,
            subtitle = subtitle
        )
    }
}

private data class ProfileChoice<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null
)

@Composable
private fun <T> ProfileChoiceGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalDialogSecondaryTextColor.current
        )

        SettingsGroup {
            options.forEachIndexed { index, (value, label) ->
                if (index > 0) MorpheSettingsDivider()
                SettingsItem(
                    onClick = { onSelect(value) },
                    title = label,
                    trailingContent = {
                        if (value == selected) {
                            MorpheIcon(icon = Icons.Outlined.Check)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> ProfileMultiChoiceGroup(
    title: String,
    options: List<ProfileChoice<T>>,
    selected: Set<T>,
    emptyText: String,
    onToggle: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalDialogSecondaryTextColor.current
        )

        if (options.isEmpty()) {
            InfoBadge(
                icon = Icons.Outlined.Info,
                text = emptyText,
                isExpanded = true
            )
        } else {
            SettingsGroup {
                options.forEachIndexed { index, option ->
                    if (index > 0) MorpheSettingsDivider()
                    SettingsItem(
                        onClick = { onToggle(option.value) },
                        title = option.title,
                        subtitle = option.subtitle,
                        trailingContent = {
                            if (option.value in selected) {
                                MorpheIcon(icon = Icons.Outlined.Check)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomPatchSelectionSection(
    sources: List<SettingsViewModel.AutomationProfileSourceOption>,
    selectedPatches: Map<Int, Set<String>>,
    onToggle: (uid: Int, patchName: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
        Text(
            text = stringResource(R.string.automation_profiles_patch_selection_custom),
            style = MaterialTheme.typography.labelLarge,
            color = LocalDialogSecondaryTextColor.current
        )

        if (sources.isEmpty()) {
            InfoBadge(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.automation_profiles_select_source_first),
                isExpanded = true
            )
        } else {
            sources.forEach { source ->
                Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalDialogSecondaryTextColor.current
                    )

                    SettingsGroup {
                        source.patches.forEachIndexed { index, patch ->
                            if (index > 0) MorpheSettingsDivider()
                            SettingsItem(
                                onClick = { onToggle(source.uid, patch.name) },
                                title = patch.name,
                                subtitle = if (patch.recommended) {
                                    stringResource(R.string.automation_profiles_patch_recommended)
                                } else {
                                    null
                                },
                                trailingContent = {
                                    if (patch.name in selectedPatches[source.uid].orEmpty()) {
                                        MorpheIcon(icon = Icons.Outlined.Check)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationIntentsWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_external_automation_warning_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.installer_play_store_warning_continue),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            Text(
                text = stringResource(R.string.settings_advanced_external_automation_warning_message),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            InfoBadge(
                icon = Icons.Outlined.Warning,
                text = stringResource(R.string.settings_advanced_external_automation_warning_risk),
                style = InfoBadgeStyle.Warning,
                isExpanded = true
            )
        }
    }
}
