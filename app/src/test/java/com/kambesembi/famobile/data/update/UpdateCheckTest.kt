package com.kambesembi.famobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(UpdateCheck.newer("0.1.31", "0.1.30"))
        assertTrue(UpdateCheck.newer("v0.2.1", "0.1.99"))
        assertTrue(UpdateCheck.newer("0.1.100", "0.1.99"))
        assertFalse(UpdateCheck.newer("0.1.30", "0.1.30"))
        assertFalse(UpdateCheck.newer("0.1.29", "0.1.30"))
    }

    @Test
    fun latestReleaseFromGitHubsAnswer() {
        val json = """
            {"tag_name": "v0.1.31", "html_url": "https://github.com/Kambesembi/FA-Mobile/releases/tag/v0.1.31",
             "assets": [{"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
                        {"name": "FA-Mobile-0.1.31.apk", "browser_download_url": "https://example.com/FA-Mobile-0.1.31.apk"}]}
        """.trimIndent()
        assertEquals(
            Release("0.1.31", "https://github.com/Kambesembi/FA-Mobile/releases/tag/v0.1.31", "https://example.com/FA-Mobile-0.1.31.apk"),
            UpdateCheck.parse(json),
        )
        assertNull(UpdateCheck.parse("{}"))
        assertNull(UpdateCheck.parse("not json"))
    }
}
