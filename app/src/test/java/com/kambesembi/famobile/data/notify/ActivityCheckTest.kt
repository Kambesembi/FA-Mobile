package com.kambesembi.famobile.data.notify

import com.kambesembi.famobile.data.parse.Counts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCheckTest {

    private val seen = Counts(submissions = 10, watches = 1, comments = 2, favorites = 3, journals = 0, notes = 4, tickets = 0)

    @Test
    fun firstCheckOnlyTakesStock() {
        assertTrue(ActivityCheck.news(null, seen).isEmpty())
    }

    @Test
    fun reportsOnlyWhatGrew() {
        val now = seen.copy(submissions = 14, comments = 1, notes = 6)
        assertEquals(
            listOf(News(Category.Submissions, 4, 14), News(Category.Notes, 2, 6)),
            ActivityCheck.news(seen, now),
        )
    }

    @Test
    fun readingThenNewActivityNotifiesAgain() {
        val afterReading = seen.copy(comments = 0)
        val later = afterReading.copy(comments = 1)
        assertEquals(listOf(News(Category.Comments, 1, 1)), ActivityCheck.news(afterReading, later))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(seen, ActivityCheck.decode(ActivityCheck.encode(seen)))
        assertNull(ActivityCheck.decode(null))
        assertNull(ActivityCheck.decode("1,2,3"))
        assertNull(ActivityCheck.decode("a,b,c,d,e,f,g"))
    }
}
