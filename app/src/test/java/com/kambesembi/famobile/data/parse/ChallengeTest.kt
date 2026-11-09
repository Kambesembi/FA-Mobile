package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    private val challenge = Fixtures.text("cloudflare-challenge.html")
    private val page = Fixtures.text("header-loggedin.html")

    @Test
    fun cfMitigatedHeaderAlone() = assertTrue(Challenge.isChallenge(200, "challenge", ""))

    @Test
    fun interstitialOn403() = assertTrue(Challenge.isChallenge(403, null, challenge))

    @Test
    fun interstitialOn503() = assertTrue(Challenge.isChallenge(503, null, challenge))

    @Test
    fun normalPageIsNot() = assertFalse(Challenge.isChallenge(200, null, page))

    @Test
    fun plain403IsNot() = assertFalse(Challenge.isChallenge(403, null, page))

    @Test
    fun interstitialOn200IsNotTrustedWithoutHeader() = assertFalse(Challenge.isChallenge(200, null, challenge))
}
