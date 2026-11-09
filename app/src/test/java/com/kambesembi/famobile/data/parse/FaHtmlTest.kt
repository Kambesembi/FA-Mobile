package com.kambesembi.famobile.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaHtmlTest {

    @Test
    fun relativeLinksBecomeAbsolute() {
        val out = FaHtml.prepare("""see <a href="/view/123/">this</a>""")
        assertTrue(out.contains("""href="https://www.furaffinity.net/view/123/""""))
    }

    @Test
    fun userIconsKeepTheName() {
        val out = FaHtml.prepare("""<a href="/user/abc/" class="iconusername"><img src="//a.furaffinity.net/1/abc.gif" alt="abc" title="abc"></a>""")
        assertTrue(out.contains(">abc</a>"))
        assertFalse(out.contains("<img"))
    }

    @Test
    fun iconOnlyStaysIconOnly() {
        // :abcicon: is the avatar alone.
        val out = FaHtml.prepareWithIcons("""hi <a href="/user/abc/" class="iconusername"><img src="//a.furaffinity.net/1/abc.gif" alt="abc"></a>""")
        assertEquals(listOf("https://a.furaffinity.net/1/abc.gif"), out.icons)
        assertTrue(out.html.contains("${FaHtml.ICON_START}0${FaHtml.ICON_END}</a>"))
        assertFalse(out.html.contains("<img"))
    }

    @Test
    fun iconWithNameKeepsTheName() {
        // :iconabc: is the avatar, then the name.
        val html = """<a href="/user/abc/" class="iconusername"><img src="//a.furaffinity.net/1/abc.gif" alt="abc">&nbsp;abc</a>"""
        val icons = FaHtml.prepareWithIcons(html)
        assertTrue(icons.html, icons.html.contains("${FaHtml.ICON_END}&nbsp;abc</a>") || icons.html.contains("${FaHtml.ICON_END} abc</a>"))
        val text = FaHtml.prepare(html)
        assertEquals(text, 1, Regex("abc").findAll(text.substringAfter(">")).count())
    }

    @Test
    fun otherImagesAndScriptsGo() {
        val out = FaHtml.prepare("""a<img src="x.png"><script>alert(1)</script>b""")
        assertEquals("ab", out)
    }

    @Test
    fun ruleBecomesALine() {
        val out = FaHtml.prepare("""one<span class="bbcode bbcode_hr"></span>two""")
        assertTrue(out.contains("———"))
        assertFalse(out.contains("bbcode_hr"))
        // The rule sits on a line of its own, with a break on both sides.
        assertTrue(out, Regex("""one\s*<br>\s*—+\s*<br>\s*two""").containsMatchIn(out))
    }

    @Test
    fun linkTargets() {
        assertEquals(FaLink.Submission(42), FaLink.of("https://www.furaffinity.net/view/42/"))
        assertEquals(FaLink.Submission(42), FaLink.of("/full/42/"))
        assertEquals(FaLink.User("some-one"), FaLink.of("https://furaffinity.net/user/some-one/"))
        assertEquals(FaLink.External("https://example.com/x"), FaLink.of("https://example.com/x"))
    }

    @Test
    fun faLeavingPageUnwrapsToTheRealLink() {
        assertEquals(
            FaLink.External("https://example.com/shop?a=1&b=2"),
            FaLink.of("https://www.furaffinity.net/externalurl/?q=https%3A%2F%2Fexample.com%2Fshop%3Fa%3D1%26b%3D2"),
        )
        // A wrapped FA link opens in the app.
        assertEquals(FaLink.Submission(7), FaLink.of("/externalurl/?q=https%3A%2F%2Fwww.furaffinity.net%2Fview%2F7%2F"))
        // Without a usable target it stays FA's page.
        assertEquals(FaLink.External("https://www.furaffinity.net/externalurl/?q=nothing"), FaLink.of("/externalurl/?q=nothing"))
    }

    @Test
    fun centeredBlocksAreMarked() {
        val html = """a<code class="bbcode bbcode_center">mid <code class="bbcode bbcode_center">inner</code></code><code class="bbcode bbcode_right">end</code>"""
        val out = FaHtml.prepareWithIcons(html).html
        assertEquals(1, out.count { it == FaHtml.CENTER_START })
        assertEquals(1, out.count { it == FaHtml.CENTER_END })
        assertTrue(out.indexOf(FaHtml.CENTER_START) < out.indexOf("mid") && out.indexOf("inner") < out.indexOf(FaHtml.CENTER_END))
        assertTrue(out.contains("${FaHtml.RIGHT_START}end${FaHtml.RIGHT_END}"))
        // Plain text has no markers.
        assertFalse(FaHtml.prepare(html).contains(FaHtml.CENTER_START))
    }
}
