/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system.contributors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * What a face says when it is chosen: who this is, what they worked on, and where to find them.
 *
 * A large photo, the name, the handle, a line of prose and then the areas as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContributorDetailsDialog(
    contributor: Contributor,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val profileUrl = contributor.profileUrl

    fun openProfile() {
        if (profileUrl == null) return
        runCatching { uriHandler.openUri(profileUrl) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // One face, on screen, drawn large: the only place a picture is worth its full
                // size rather than a step off the wall's own scale.
                ContributorAvatar(
                    contributor = contributor,
                    onScreen = true,
                    targetPx = 512,
                    modifier = Modifier.size(104.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = contributor.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    contributor.login
                        ?.takeIf { !it.equals(contributor.name, ignoreCase = true) }
                        ?.let { login ->
                            Text(
                                text = "@$login",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    Text(
                        text = pluralStringResource(
                            R.plurals.contributor_commits,
                            contributor.commits,
                            contributor.commits
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = contributorSummary(contributor),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (contributor.areas.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        contributor.areas.forEach { ContributionChip(it) }
                    }
                }
            }
        },
        confirmButton = {
            if (profileUrl != null) {
                FilledTonalButton(onClick = { openProfile() }) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.contributor_view_github),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.contributor_details_close))
                }
            }
        },
        dismissButton = if (profileUrl != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.contributor_details_close))
                }
            }
        } else null
    )
}

@Composable
private fun ContributionChip(area: ContributionArea) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = area.icon,
                contentDescription = null,
                tint = area.tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(area.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
