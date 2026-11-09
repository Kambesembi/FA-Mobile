package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.SubmissionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemErrorTest {

    private val gone = "The submission you are trying to find is not in our database."

    @Test
    fun bareErrorPageHasNoHeaderSoTheLoginStays() {
        val header = HeaderParser.parse(Fixtures.document("system-error.html"))
        assertNull(header.user)
        assertFalse(header.present)
        // A real logged-out page does have FA's header.
        assertTrue(HeaderParser.parse(Fixtures.document("header-loggedout.html")).present)
        assertTrue(HeaderParser.parse(Fixtures.document("header-loggedin.html")).present)
    }

    @Test
    fun bareErrorPageMessage() {
        assertEquals(gone, SystemMessageParser.message(Fixtures.document("system-error.html")))
        val result = SubmissionParser.parse(Fixtures.document("system-error.html"), 5)
        assertEquals(gone, (result as SubmissionResult.Unavailable).message)
    }

    @Test
    fun floodProtectionWait() {
        assertEquals(16, UploadParser.floodSeconds("Flood protection. Please wait 16 seconds before trying again."))
        assertEquals(30, UploadParser.floodSeconds("Flood protection is active."))
        assertNull(UploadParser.floodSeconds("Please wait 16 seconds."))
        assertNull(UploadParser.floodSeconds(null))
    }
}
