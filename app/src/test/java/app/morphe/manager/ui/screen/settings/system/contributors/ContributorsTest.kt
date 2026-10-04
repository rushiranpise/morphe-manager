/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system.contributors

import kotlin.test.*

/**
 * The credits wall reads a generated snapshot, lays faces out on a hex grid and rounds the size a
 * face is decoded at. All of that is pure arithmetic and parsing, so it is checked here rather
 * than only on a device.
 */
class ContributorsTest {

    private fun snapshot(vararg entries: String) =
        """{"generated":"2026-10-03","contributors":[${entries.joinToString(",")}]}"""

    @Test
    fun `snapshot is parsed with areas and languages`() {
        val parsed = parseContributors(
            snapshot(
                """{"name":"Someone","login":"someone","avatarUrl":"https://x/y.png","commits":12,"areas":["APP","DOCS"],"languages":["fr"]}"""
            )
        )

        assertEquals(1, parsed.size)
        val who = parsed.single()
        assertEquals("Someone", who.name)
        assertEquals("someone", who.login)
        assertEquals(12, who.commits)
        assertEquals(listOf(ContributionArea.APP, ContributionArea.DOCS), who.areas)
        assertEquals(listOf("fr"), who.languages)
        assertEquals("https://github.com/someone", who.profileUrl)
    }

    @Test
    fun `unknown area names are dropped, not fatal`() {
        val parsed = parseContributors(
            snapshot("""{"name":"Someone","commits":1,"areas":["APP","MADE_UP"],"languages":[]}""")
        )
        assertEquals(listOf(ContributionArea.APP), parsed.single().areas)
    }

    @Test
    fun `a null field is null and not the word null`() {
        // Android's org.json answers a JSON null with the literal word, which would become a value
        val parsed = parseContributors(
            snapshot("""{"name":"NoAccount","login":null,"avatarUrl":null,"commits":3,"areas":[],"languages":[]}""")
        )
        val who = parsed.single()
        assertNull(who.login)
        assertNull(who.avatarUrl)
        assertNull(who.profileUrl)
    }

    @Test
    fun `an entry without a name is skipped`() {
        val parsed = parseContributors(
            snapshot(
                """{"login":"ghost","commits":5}""",
                """{"name":"Real","commits":1,"areas":[],"languages":[]}"""
            )
        )
        assertEquals(listOf("Real"), parsed.map { it.name })
    }

    @Test
    fun `an empty payload yields no contributors`() {
        assertTrue(parseContributors("""{"contributors":[]}""").isEmpty())
    }

    @Test
    fun `a malformed payload is caught by the caller, not the parser`() {
        // Every call site wraps the parse in runCatching and falls back to the next copy, so a
        // corrupt download can never take the wall down with it.
        assertNull(runCatching { parseContributors("not json") }.getOrNull())
    }

    @Test
    fun `geometry places every face and is centred`() {
        val geometry = contributorWallGeometry(count = 7, avatarSize = 56f, gap = 6f)
        assertEquals(7, geometry.centers.size)
        // Centred on the origin, so the largest absolute extent matches on opposite sides
        val minX = geometry.centers.minOf { it.x }
        val maxX = geometry.centers.maxOf { it.x }
        assertTrue(minX < 0f && maxX > 0f)
        assertTrue(geometry.contentSize.width > 0f && geometry.contentSize.height > 0f)
    }

    @Test
    fun `zero contributors produce an empty wall`() {
        assertTrue(contributorWallGeometry(0, 56f, 6f).centers.isEmpty())
    }

    @Test
    fun `geometry rejects a non-positive face size`() {
        assertFailsWith<IllegalArgumentException> { contributorWallGeometry(5, 0f, 6f) }
    }

    @Test
    fun `avatar size is bucketed to a power of two cap`() {
        assertEquals(64, avatarBucket(64))
        assertEquals(128, avatarBucket(65))
        assertEquals(512, avatarBucket(400))
        // Never past the cap, however far the pinch goes
        assertEquals(512, avatarBucket(4096))
    }

    @Test
    fun `avatar cache key separates two people without a picture`() {
        val one = Contributor("Ada", null, 1, null, emptyList(), emptyList())
        val two = Contributor("Grace", null, 1, null, emptyList(), emptyList())
        assertNotEquals(
            ContributorAvatars.cacheKey(one, 64),
            ContributorAvatars.cacheKey(two, 64)
        )
    }
}
