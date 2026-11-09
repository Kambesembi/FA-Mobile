package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.FaSetting
import com.kambesembi.famobile.data.model.FaSettingEditor
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaSettingsFormTest {

    private val doc = Fixtures.document("controls-settings.html")

    private fun setting(id: String) = FaSetting.byId.getValue(id)

    @Test
    fun editorsShowTheCurrentState() {
        val perPage = FaSettingsParser.editor(doc, setting("perpage")) as FaSettingEditor.Choice
        assertEquals(listOf("24", "48", "72"), perPage.options.map { it.first })
        assertEquals("72", perPage.selected)
        assertEquals(FaSettingEditor.Choice(listOf("12" to "12 hour", "24" to "24 hour"), "24"), FaSettingsParser.editor(doc, setting("hour_format")))
        assertEquals(FaSettingEditor.Toggle(true), FaSettingsParser.editor(doc, setting("timezone_dst")))
        assertEquals(setOf("search_rating_general", "search_rating_mature"), (FaSettingsParser.editor(doc, setting("search_ratings")) as FaSettingEditor.Multi).ticked)
        assertTrue((FaSettingsParser.editor(doc, setting("profileinfo")) as FaSettingEditor.Free).multiline)
    }

    @Test
    fun notEditable() {
        assertNull(FaSettingsParser.editor(doc, setting("contacts")))
        assertNull(FaSettingsParser.editor(doc, setting("account_disabled")))
    }

    @Test
    fun blockedTagsAreEditableWithFasKey() {
        assertEquals(FaSettingEditor.Tags(listOf("gore", "Vore")), FaSettingsParser.editor(doc, setting("blocked_tags")))
        assertEquals("testkey", FaSettingsParser.tagKey(doc))
        // Without the key there is nothing to send them with.
        assertNull(FaSettingsParser.editor(Jsoup.parse("<body data-tag-blocklist=\"gore\"></body>"), setting("blocked_tags")))
    }

    @Test
    fun onlyTheChangedFieldDiffers() {
        val form = FaSettingsForm.fill(doc, setting("perpage"), FaSettingEditor.Choice(emptyList(), "48"), "/controls/site-settings/")!!
        assertEquals("/controls/site-settings/", form.action)
        assertEquals(listOf("hour_format" to "24", "perpage" to "48", "search_rating_general" to "on", "search_rating_mature" to "on"), form.fields)
    }

    @Test
    fun ticksAndToggles() {
        val ticks = FaSettingsForm.fill(
            doc, setting("search_ratings"), FaSettingEditor.Multi(emptyList(), setOf("search_rating_adult")), "/controls/site-settings/",
        )!!
        assertEquals(listOf("hour_format" to "24", "perpage" to "72", "search_rating_adult" to "on"), ticks.fields)
        val off = FaSettingsForm.fill(doc, setting("timezone_dst"), FaSettingEditor.Toggle(false), "/controls/settings/")!!
        assertFalse(off.fields.any { it.first == "timezone_dst" })
        // The empty password field goes along as a browser sends it, and nothing else is touched.
        assertTrue(off.fields.contains("oldpassword" to ""))
        assertTrue(off.fields.contains("viewmature" to "2"))
    }

    @Test
    fun hiddenFieldsAndTheSaveButtonGoAlong() {
        val page = Jsoup.parse(
            """<form method="post" action="/controls/user-settings/">
              <input type="hidden" name="key" value="abc">
              <input type="radio" name="accept_trades" value="1" checked> <input type="radio" name="accept_trades" value="0">
              <button type="submit" name="delete" value="1">Delete everything</button>
              <input type="submit" name="save" value="Save">
            </form>""",
        )
        val form = FaSettingsForm.fill(page, setting("accept_trades"), FaSettingEditor.Choice(emptyList(), "0"), "/controls/user-settings/")!!
        assertEquals(listOf("key" to "abc", "accept_trades" to "0", "save" to "Save"), form.fields)
    }
}
