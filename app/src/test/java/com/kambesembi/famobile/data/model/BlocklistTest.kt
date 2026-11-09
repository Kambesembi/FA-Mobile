package com.kambesembi.famobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {

    private val meta = listOf("u_artist1", "c_artwork_digital", "t_all", "s_unspecified_any")

    @Test
    fun blockedTagHides() {
        val list = Blocklist.of("gore vore", "", "0")
        assertTrue(list.blocks(meta + listOf("dragon", "vore")))
        assertFalse(list.blocks(meta + listOf("dragon")))
    }

    @Test
    fun blockedUserHidesTheirWork() {
        val list = Blocklist.of("", "Artist1", "0")
        assertTrue(list.blocks(meta + "dragon"))
        assertFalse(list.blocks(listOf("u_artist2", "c_all", "t_all", "s_any", "dragon")))
    }

    @Test
    fun usernamesBecomeFaTags() {
        assertEquals("u_some_artist", Blocklist.userTag("some..artist"))
        assertEquals("u_a-b", Blocklist.userTag("a-b"))
    }

    @Test
    fun taglessOnlyWhenAsked() {
        assertFalse(Blocklist.of("gore", "", "0").blocks(meta))
        assertTrue(Blocklist.of("", "", "1").blocks(meta))
        assertFalse(Blocklist.of("", "", "1").blocks(meta + "dragon"))
    }

    @Test
    fun unknownTagsNeverBlock() {
        assertFalse(Blocklist.of("gore", "", "1").blocks(null))
    }
}
