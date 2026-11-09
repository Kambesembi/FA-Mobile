package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.FaSetting
import com.kambesembi.famobile.data.model.FaSettingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaSettingsParserTest {

    private val doc = Fixtures.document("controls-settings.html")

    private fun read(id: String) = FaSettingsParser.read(doc, FaSetting.byId.getValue(id))

    @Test
    fun selectShowsTheChosenOption() {
        assertEquals(FaSettingValue.Text("General, Mature, Adult"), read("viewmature"))
        assertEquals(FaSettingValue.Text("72"), read("perpage"))
    }

    @Test
    fun radioShowsItsLabel() {
        assertEquals(FaSettingValue.Text("24 hour"), read("hour_format"))
        assertEquals(FaSettingValue.Text("Yes"), read("accept_commissions"))
    }

    @Test
    fun checkboxesAndTicks() {
        assertEquals(FaSettingValue.Flag(true), read("timezone_dst"))
        assertEquals(FaSettingValue.Items(listOf("General", "Mature")), read("search_ratings"))
    }

    @Test
    fun textAreasKeepTheirLines() {
        val profile = read("profileinfo") as FaSettingValue.Text
        assertTrue(profile.long)
        assertTrue(profile.text.contains("\n"))
        assertEquals(FaSettingValue.Text("artist9\nartist10", long = true), read("blocklist"))
    }

    @Test
    fun emptyAndMissingFields() {
        assertEquals(FaSettingValue.Empty, read("quote"))
        assertEquals(FaSettingValue.Empty, read("favoritefood"))
    }

    @Test
    fun blockedTagsAndContacts() {
        assertEquals(FaSettingValue.Items(listOf("gore", "Vore")), read("blocked_tags"))
        assertEquals(
            FaSettingValue.Items(listOf("Website: https://example.com/testuser", "Bluesky: testuser.example")),
            read("contacts"),
        )
    }

    @Test
    fun noSensitiveFieldsInTheCatalogue() {
        val names = FaSetting.all.map { it.id }
        for (secret in listOf("oldpassword", "newpassword", "fa_useremail", "paypal_email", "user-birthday")) assertTrue(secret !in names)
    }
}
