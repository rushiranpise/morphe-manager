/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system.contributors

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Someone credited on the wall.
 *
 * [login] and [avatarUrl] come from the GitHub account itself; the account is the identity, so the
 * same person contributing to several repositories is one face, counted once. [projects] are the
 * repositories they worked in, most-contributed first, and are read off the organisation's own
 * repositories rather than typed anywhere.
 *
 * The picture is the account's own, at the URL it lives at; nothing is carried in the app, so what
 * is drawn is whatever that person is using now rather than a copy from whenever the credits were
 * last generated.
 */
internal data class Contributor(
    val name: String,
    val login: String?,
    val commits: Int,
    val avatarUrl: String?,
    /** Repository keys, e.g. `patches` for `morphe-patches`. */
    val projects: List<String>
) {
    val profileUrl: String? get() = login?.let { "https://github.com/$it" }
}

/**
 * What a project key is called on screen.
 *
 * Composed from the key, so a repository the organisation adds tomorrow is named without a string
 * of its own. The few names that title-casing would mangle - an acronym, or a name that keeps its
 * own capitalisation - are given here instead.
 */
internal fun projectDisplayName(key: String): String =
    ProjectNameOverrides[key] ?: key.split('-', '_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

private val ProjectNameOverrides = mapOf(
    "microg-re" to "MicroG-RE",
    "arsclib" to "ARSCLib",
    "jadb" to "jadb",
    "smali" to "smali",
    "multidexlib2" to "multidexlib2",
    "nowinandroid" to "Now in Android",
    "pothelper" to "PotHelper"
)

/** A glyph to recognise a project by; anything unrecognised gets a folder rather than nothing. */
internal fun projectIcon(key: String): ImageVector = when (key) {
    "manager" -> Icons.Outlined.PhoneAndroid
    "patches" -> Icons.Outlined.Extension
    "patcher" -> Icons.Outlined.Build
    "website" -> Icons.Outlined.Language
    "documentation", "library" -> Icons.Outlined.MenuBook
    "desktop" -> Icons.Outlined.DesktopWindows
    "branding" -> Icons.Outlined.Palette
    "jadb" -> Icons.Outlined.Terminal
    else -> Icons.Outlined.Folder
}

/** A color to tell projects apart at a glance; the fallback stays neutral. */
internal fun projectTint(key: String): Color = when (key) {
    "manager" -> Color(0xFF3DDC84)
    "patches" -> Color(0xFF4C8DFF)
    "patcher" -> Color(0xFFB388FF)
    "website" -> Color(0xFFEF6C00)
    "documentation", "library" -> Color(0xFF26A69A)
    "desktop" -> Color(0xFFF9A825)
    "branding" -> Color(0xFFEC407A)
    "jadb" -> Color(0xFF7E57C2)
    else -> Color(0xFF90A4AE)
}

/**
 * The credits snapshot, and how it is kept current.
 *
 * The file is generated from the organisation's repositories by `ci/update-contributors.py` and
 * published to a data branch by the contributors workflow, so the wall follows the organisation
 * without needing a release: a new contributor shows up on their own. Three copies are in play, in
 * order of preference - the fetched copy, the last one fetched, and the one shipped in the APK -
 * and a failure at any step simply falls back to the next, because a credits screen is never worth
 * failing over.
 */
internal object ContributorCredits {

    /** Shipped in the APK, so the wall is populated before anything has been fetched. */
    private const val SNAPSHOT_ASSET = "contributors.json"

    /**
     * Where the contributors workflow publishes the regenerated snapshot.
     *
     * A branch rather than a release asset or a gist: it needs no extra setup, it is fetched
     * straight from raw.githubusercontent.com, and the file's own history is the record of how the
     * credits changed.
     */
    private const val SNAPSHOT_URL =
        "https://raw.githubusercontent.com/MorpheApp/morphe-manager/contributors-data/contributors.json"

    /** Checking once a day is often enough for a list that changes when somebody commits. */
    private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    private fun cacheFile(context: Context) = File(context.filesDir, SNAPSHOT_ASSET)

    /** The snapshot to draw right now: what was fetched last, or what was shipped. */
    fun initial(context: Context): List<Contributor> =
        cached(context) ?: bundled(context)

    fun cached(context: Context): List<Contributor>? =
        cacheFile(context)
            .takeIf { it.isFile }
            ?.let { runCatching { parseContributors(it.readText()) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }

    fun bundled(context: Context): List<Contributor> =
        runCatching {
            context.assets.open(SNAPSHOT_ASSET).bufferedReader().use { it.readText() }
        }.mapCatching { parseContributors(it) }.getOrDefault(emptyList())

    /**
     * Fetch the published snapshot, unless one was fetched recently.
     *
     * Returns the new list when it arrived, and null when there was nothing to do or nothing came
     * back - the caller keeps whatever it was already drawing.
     */
    suspend fun refresh(context: Context, force: Boolean = false): List<Contributor>? =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(context)
            if (!force && cache.isFile &&
                System.currentTimeMillis() - cache.lastModified() < REFRESH_INTERVAL_MS
            ) {
                return@withContext null
            }

            val payload = runCatching { download() }.getOrNull() ?: return@withContext null
            val fetched = runCatching { parseContributors(payload) }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: return@withContext null

            // Written to a sibling and renamed, so a download cut short can never leave a half
            // file behind for the next launch to read.
            runCatching {
                val staging = File(cache.parentFile, "$SNAPSHOT_ASSET.tmp")
                staging.writeText(payload)
                if (!staging.renameTo(cache)) {
                    cache.writeText(payload)
                    staging.delete()
                }
            }
            fetched
        }

    private fun download(): String {
        val connection = (URL(SNAPSHOT_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            // raw.githubusercontent.com will serve a cached copy, which is exactly what is wanted
            // for a file that changes weekly.
            setRequestProperty("User-Agent", "Morphe/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("snapshot fetch returned ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Read a snapshot.
 *
 * Unknown project keys are kept rather than dropped: a project is whatever the generator found, and
 * an app that has never heard of one still owes the person the credit.
 */
internal fun parseContributors(payload: String): List<Contributor> {
    val array = JSONObject(payload).optJSONArray("contributors") ?: return emptyList()

    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optionalText("name") ?: continue

            add(
                Contributor(
                    name = name,
                    login = item.optionalText("login"),
                    commits = item.optInt("commits"),
                    avatarUrl = item.optionalText("avatarUrl"),
                    projects = item.optJSONArray("projects").toStringList()
                )
            )
        }
    }
}

/**
 * A text field of the snapshot, or null where the generator wrote a null.
 *
 * Not `optString`: Android answers a JSON null with the *word* "null", which is then a value
 * everywhere it is used - a contributor without a picture would be offered a link to
 * github.com/null, and every one of them would share a cache entry.
 */
private fun JSONObject.optionalText(key: String): String? {
    if (isNull(key)) return null
    val value = optString(key).trim()
    // The word itself is not a value either, whichever library answered with it.
    return if (value.isEmpty() || value.equals("null", ignoreCase = true)) null else value
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotEmpty()) add(value)
        }
    }
}

/**
 * The credits wall as a section: a heading, the wall itself, and the details dialog a face opens.
 *
 * The snapshot is read off the main thread, cached, and refreshed at most once a day, so this can
 * be dropped straight into a dialog.
 */
@Composable
internal fun ContributorWallSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Contributor?>(null) }

    val contributors by produceState(initialValue = emptyList<Contributor>(), context) {
        value = withContext(Dispatchers.IO) { ContributorCredits.initial(context) }
        withContext(Dispatchers.IO) { ContributorCredits.refresh(context) }?.let { value = it }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        Text(
            text = stringResource(R.string.settings_contributors),
            style = MaterialTheme.typography.labelMedium,
            color = LocalDialogSecondaryTextColor.current
        )
        if (contributors.isNotEmpty()) {
            ContributorWall(
                contributors = contributors,
                onSelect = { selected = it }
            )
            Text(
                text = stringResource(R.string.settings_contributors_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }

    selected?.let { contributor ->
        ContributorDetailsDialog(
            contributor = contributor,
            onDismiss = { selected = null }
        )
    }
}

/**
 * What to say about a contributor, composed from what the generator found.
 *
 * A sentence built out of the projects rather than one per person, so a new contributor is
 * described the moment the generator sees them and there is no list of strings to keep in step
 * with the list of people.
 */
@Composable
internal fun contributorSummary(contributor: Contributor): String {
    val projects = contributor.projects.map { projectDisplayName(it) }.joinToString(", ")

    return if (projects.isEmpty()) {
        stringResource(R.string.contributor_summary_default)
    } else {
        stringResource(R.string.contributor_summary_projects, projects)
    }
}
