package com.kambesembi.famobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadModelTest {

    @Test
    fun keywordsSplitOnSpacesAndCommas() {
        assertEquals(listOf("fox", "sunset", "digital_art"), Keywords.split(" fox, sunset  #digital_art ,,"))
        // Single letters mean nothing to FA's search.
        assertEquals(listOf("oc"), Keywords.split("a oc b"))
        // FA takes a-z, 0-9 and _ only.
        assertEquals(listOf("cafe_art", "dont", "fox_2", "ych"), Keywords.split("Café-Art don't fox.2 YCH! ?!"))
    }

    @Test
    fun keywordsStopAtFasLimit() {
        val long = List(60) { "keyword$it" }
        val merged = Keywords.merge(emptyList(), long)
        assertEquals(true, Keywords.join(merged).length <= Keywords.MAX_CHARS)
        assertEquals(true, merged.size in 40 until 60)
    }

    @Test
    fun keywordsMergeWithoutRepeats() {
        assertEquals(listOf("Fox", "sunset", "sky"), Keywords.merge(listOf("Fox", "sunset"), listOf("fox", "sky", "SUNSET")))
        assertEquals("fox sky", Keywords.join(listOf("fox", "sky")))
    }

    @Test
    fun eachFileWithItsOwnRatingAndGenders() {
        val file = UploadFile("/x/a.png", "a.png", "image/png", 1)
        val details = UploadDetails(rating = Rating.General, keywords = listOf("fox", "male", "sunset"))
        val follows = UploadItem(file, "A", UploadKind.Artwork)
        assertEquals(details, details.forItem(follows))
        val own = follows.copy(rating = Rating.Adult, genders = listOf("female", "intersex"))
        val sent = details.forItem(own)
        assertEquals(Rating.Adult, sent.rating)
        assertEquals(listOf("fox", "sunset", "female", "intersex"), sent.keywords)
        assertEquals(listOf("male"), details.genders)
        // A file without a rating of its own needs the form's.
        assertEquals(true, UploadDraft(listOf(follows, own), details.copy(rating = null)).missingRating)
        assertEquals(false, UploadDraft(listOf(own), details.copy(rating = null)).missingRating)
    }

    @Test
    fun filesSetApartGetFormsOfTheirOwnOneAfterAnother() {
        fun item(name: String, separate: Boolean = false) =
            UploadItem(UploadFile("/x/$name", name, "image/png", 1), name, UploadKind.Artwork, separate = separate)
        val preset = UploadDetails(rating = Rating.General)
        val draft = UploadDraft(
            listOf(item("a"), item("b", separate = true), item("c"), item("d", separate = true)),
            UploadDetails(rating = Rating.Mature, description = "Shared", keywords = listOf("fox", "sky", "sun")),
        )
        assertEquals(listOf("a", "c"), draft.sending.map { it.title })
        assertEquals(listOf("b", "d"), draft.waiting.map { it.title })
        // Next: b's own form (fresh details), d still waiting.
        val second = draft.next(preset)
        assertEquals(preset, second.details)
        assertEquals(listOf("b"), second.sending.map { it.title })
        assertEquals(listOf("d"), second.waiting.map { it.title })
        val third = second.next(preset)
        assertEquals(listOf("d"), third.sending.map { it.title })
        assertEquals(true, third.next(preset).items.isEmpty())
        // All set apart: the first goes with the form.
        val allApart = UploadDraft(listOf(item("x", true), item("y", true)), preset)
        assertEquals(listOf("x"), allApart.sending.map { it.title })
    }

    @Test
    fun titlesFromFileNames() {
        assertEquals("My new pic final", UploadNames.titleFrom("my_new-pic_final.png"))
        assertEquals("", UploadNames.titleFrom("IMG_20261104_123456.jpg"))
        assertEquals("", UploadNames.titleFrom("PXL_20261104_123456789.jpg"))
        assertEquals("Chapter 3", UploadNames.titleFrom("chapter 3.docx"))
        assertEquals(UploadNames.MAX_TITLE, UploadNames.titleFrom("a".repeat(80) + ".png").length)
    }

    @Test
    fun kindFollowsTheFile() {
        assertEquals(UploadKind.Artwork, UploadKind.of("image/webp", "pic.webp"))
        assertEquals(UploadKind.Music, UploadKind.of("audio/mpeg", "song.mp3"))
        assertEquals(UploadKind.Music, UploadKind.of("application/octet-stream", "tune.mid"))
        assertEquals(UploadKind.Story, UploadKind.of("application/pdf", "story.pdf"))
        assertEquals(UploadKind.Story, UploadKind.of("application/octet-stream", "story.odt"))
        assertNull(UploadKind.of("application/zip", "files.zip"))
    }

    @Test
    fun storeSurvivesTheRoundTrip() {
        val file = UploadFile("/data/uploads/a/pic.png", "pic.png", "image/png", 12345, 1600, 1200)
        val details = UploadDetails(
            rating = Rating.Mature,
            description = "Line one\n\"quoted\"",
            keywords = listOf("fox", "sunset"),
            category = UploadChoice("2", "Artwork (Digital)"),
            folders = listOf(UploadChoice("1000001", "Warmups")),
            scraps = true,
            stripLocation = false,
        )
        val store = UploadStore(
            draft = UploadDraft(
                listOf(
                    UploadItem(file, "Pic", UploadKind.Artwork),
                    UploadItem(file, "Own", UploadKind.Artwork, rating = Rating.Adult, genders = listOf("female")),
                    UploadItem(file, "Later", UploadKind.Artwork, separate = true),
                ),
                details,
            ),
            jobs = listOf(
                UploadJob(
                    "id-1",
                    UploadItem(file.copy(mimeType = "text/plain", name = "s.txt"), "Story", UploadKind.Story, thumbnail = file),
                    details,
                    status = UploadStatus.Failed,
                    message = "FA said no",
                    needsCheck = true,
                    attempts = 2,
                    created = 99,
                ),
                UploadJob("id-2", UploadItem(file, "Done", UploadKind.Artwork), details, status = UploadStatus.Done, submissionId = 10001100, created = 100),
            ),
            preset = details.preset(),
            recentKeywords = listOf("fox", "sunset"),
            lists = UploadLists(categories = listOf(FilterOption("2", "Artwork (Digital)", "Visual Art")), exact = true),
            folders = listOf(FilterOption("1000001", "Warmups", "Sketches")),
            lastUploadAt = 1_762_300_000_000,
        )
        assertEquals(store, UploadCodec.decode(UploadCodec.encode(store)))
    }

    @Test
    fun brokenStoreFallsBackToEmpty() {
        assertEquals(UploadStore(), UploadCodec.decode("{not json"))
        assertEquals(UploadStore(), UploadCodec.decode(null))
    }

    @Test
    fun presetKeepsChoicesNotWords() {
        val details = UploadDetails(rating = Rating.General, description = "x", keywords = listOf("fox"), scraps = true)
        assertEquals(UploadDetails(rating = Rating.General, scraps = true), details.preset())
    }
}
