package com.kambesembi.famobile.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FaUserAgentTest {

    @Test
    fun keepsOnlyTheChromeMajorVersion() {
        val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/131.0.6778.39 Mobile Safari/537.36"
        val ua = FaUserAgent.reduced(webView, "0.2.45")
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 FAMobile/0.2.45",
            ua,
        )
        assertFalse(ua.contains("Pixel") || ua.contains("; wv"))
    }

    @Test
    fun fallsBackWithoutAChromeVersion() {
        assertEquals(true, FaUserAgent.reduced("something odd", "1").contains("Chrome/130.0.0.0"))
    }
}
