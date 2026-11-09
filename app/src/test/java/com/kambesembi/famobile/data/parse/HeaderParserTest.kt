package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.Blocklist
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaderParserTest {

    @Test
    fun loggedInUser() {
        val header = HeaderParser.parse(Fixtures.document("header-loggedin.html"))
        assertEquals(
            LoggedInUser("testuser", "TestUser", "https://a.furaffinity.net/1700000000/testuser.gif"),
            header.user,
        )
    }

    @Test
    fun allCountsFromTheDesktopBar() {
        val counts = HeaderParser.parse(Fixtures.document("header-loggedin.html")).counts
        assertEquals(Counts(submissions = 1204, watches = 3, comments = 12, favorites = 27, journals = 2, notes = 4), counts)
        assertEquals(44, counts.activity)
    }

    @Test
    fun blocklistAndSfwFromTheBody() {
        val header = HeaderParser.parse(Fixtures.document("header-loggedin.html"))
        assertEquals(Blocklist(setOf("gore", "vore"), setOf("u_some_artist"), hideTagless = true), header.blocklist)
        assertEquals(true, header.sfw)
    }

    @Test
    fun loggedOutHasNoUserAndNoCounts() {
        val header = HeaderParser.parse(Fixtures.document("header-loggedout.html"))
        assertNull(header.user)
        assertEquals(Counts(), header.counts)
        assertNull(header.blocklist)
    }

    @Test
    fun onlyTheMobileBar() {
        val header = parse(
            """
            <div class="mobile-notification-bar">
              <a class="notification-container inline" href="/msg/submissions/" title="6 Submission Notifications">6S</a>
            </div>
            <div class="mobile-navigation"><h2><a href="/user/some-name/">Some Name</a></h2></div>
            """,
        )
        assertEquals(LoggedInUser("some-name", "Some Name", null), header.user)
        assertEquals(Counts(submissions = 6), header.counts)
    }

    @Test
    fun titleAndLinkWhenTheTextChanges() {
        val header = parse(
            """
            <a href="/user/x/"><img class="loggedin_user_avatar" alt="X" src="//a.furaffinity.net/1/x.gif"></a>
            <a class="notification-container" href="/msg/others/#comments" title="5 Comment Notifications"><i></i></a>
            <a class="notification-container" href="/msg/pms/" title="2 new"><i></i></a>
            """,
        )
        assertEquals(Counts(comments = 5, notes = 2), header.counts)
    }

    @Test
    fun displayNameFromMyFaWhenNoAvatar() {
        val header = parse("""<a id="my-username" href="/user/abc/">My FA ( Abc Def )</a>""")
        assertEquals(LoggedInUser("abc", "Abc Def", null), header.user)
    }

    private fun parse(body: String) = HeaderParser.parse(Jsoup.parse("<html><body>$body</body></html>", "https://www.furaffinity.net/"))
}
