package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.FilterOption
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.UploadChoice
import com.kambesembi.famobile.data.model.UploadDetails
import com.kambesembi.famobile.data.model.UploadFile
import com.kambesembi.famobile.data.model.UploadItem
import com.kambesembi.famobile.data.model.UploadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadParserTest {

    private val item = UploadItem(UploadFile("/x/pic.png", "pic.png", "image/png", 1000), "Evening sketch", UploadKind.Artwork)

    @Test
    fun submitPageHasKeyKindsAndLimit() {
        val page = UploadParser.submitPage(Fixtures.document("submit.html"))!!
        assertEquals("/submit/upload/", page.form.action)
        assertEquals(UploadKind.entries.toSet(), page.kinds)
        assertEquals(10485760L, page.maxFileSize)
        val fields = page.fields(UploadKind.Story)
        assertTrue("key" to "0demo0key0submit0" in fields)
        assertTrue("MAX_FILE_SIZE" to "10485760" in fields)
        assertEquals(listOf("story"), fields.filter { it.first == "submission_type" }.map { it.second })
    }

    @Test
    fun noSubmitFormOnOtherPages() {
        assertNull(UploadParser.submitPage(Fixtures.document("header-loggedin.html")))
        assertNull(UploadParser.finalizePage(Fixtures.document("submit.html")))
    }

    @Test
    fun finalizeListsComeFromTheForm() {
        val page = UploadParser.finalizePage(Fixtures.document("submit-finalize.html"))!!
        assertEquals("/submit/finalize/", page.form.action)
        val lists = page.lists
        assertTrue(lists.exact)
        assertTrue(lists.categories.any { it.label == "Artwork (Digital)" && it.group == "Visual Art" })
        // FA's starting entries aren't choices.
        assertFalse(lists.categories.any { it.label == "All" })
        assertFalse(lists.themes.any { it.label == "All" })
        assertFalse(lists.species.any { it.label.startsWith("Unspecified") })
        assertTrue(lists.species.isNotEmpty())
        assertEquals(
            listOf(
                FilterOption("1000001", "Warmups", "Sketches"),
                FilterOption("1000002", "Studies", "Sketches"),
                FilterOption("1000003", "Night Shift", null),
            ),
            page.folders,
        )
    }

    @Test
    fun fillKeepsFaDefaultsWhenNothingIsChosen() {
        val page = UploadParser.finalizePage(Fixtures.document("submit-finalize.html"))!!
        val form = UploadParser.fill(page, item, UploadDetails(rating = Rating.General)).toMap()
        assertEquals("0demo0key0finalize0", form["key"])
        assertEquals("Evening sketch", form["title"])
        assertEquals("1", form["cat"])
        assertEquals("1", form["atype"])
        assertEquals("1", form["species"])
        assertEquals("0", form["rating"])
        assertEquals("Finalize ", form["finalize"])
        assertEquals("", form["create_folder_name"])
        assertFalse("gender" in form)
        assertFalse("scrap" in form)
        assertFalse("lock_comments" in form)
    }

    @Test
    fun fillPutsInWhatWasChosen() {
        val page = UploadParser.finalizePage(Fixtures.document("submit-finalize.html"))!!
        val details = UploadDetails(
            rating = Rating.Adult,
            description = "A [b]warm[/b] evening.",
            keywords = listOf("sunset", "fox", "sketch"),
            category = UploadChoice("3", "Artwork (Traditional)"),
            theme = UploadChoice("11", "Scenery"),
            // Not on FA's list at all: left at FA's default.
            species = UploadChoice("999999", "Nothing like it"),
            folders = listOf(UploadChoice("1000002", "Studies")),
            scraps = true,
            lockComments = true,
        )
        val fields = UploadParser.fill(page, item, details)
        val form = fields.toMap()
        assertEquals("A [b]warm[/b] evening.", form["message"])
        assertEquals("sunset fox sketch", form["keywords"])
        assertEquals("1", form["rating"])
        assertEquals(1, fields.count { it.first == "rating" })
        assertEquals("3", form["cat"])
        assertEquals("11", form["atype"])
        assertEquals("1", form["species"])
        assertEquals(listOf("1000002"), fields.filter { it.first == "folder_ids[]" }.map { it.second })
        assertEquals("1", form["scrap"])
        assertEquals("1", form["lock_comments"])
        assertEquals(listOf("2"), UploadParser.fill(page, item, details.copy(rating = Rating.Mature)).filter { it.first == "rating" }.map { it.second })
    }

    @Test
    fun pickPrefersValueAndLabelThenLabelThenValue() {
        val options = listOf(FilterOption("1", "Red"), FilterOption("2", "Blue"), FilterOption("3", "red"))
        assertEquals("3", UploadParser.pick(options, UploadChoice("3", "Red")))
        assertEquals("1", UploadParser.pick(options, UploadChoice("9", "red")))
        assertEquals("2", UploadParser.pick(options, UploadChoice("2", "Green")))
        assertNull(UploadParser.pick(options, UploadChoice("9", "Green")))
    }

    @Test
    fun galleryFoldersWithTheirGroups() {
        val folders = UploadParser.galleryFolders(Fixtures.document("user-gallery.html"))
        assertEquals(
            listOf(
                FilterOption("1000001", "Warmups", "Sketches"),
                FilterOption("1000002", "Studies", "Sketches"),
                FilterOption("1000003", "Night Shift", "Comics"),
                // Ungrouped, though it comes after the last group.
                FilterOption("1000004", "Misc", null),
            ),
            folders,
        )
    }

    @Test
    fun theNewSubmissionFromFaRedirect() {
        assertEquals(10001100L, UploadParser.submissionId("https://www.furaffinity.net/view/10001100/?upload-successful"))
        assertNull(UploadParser.submissionId("https://www.furaffinity.net/submit/finalize/"))
    }
}
