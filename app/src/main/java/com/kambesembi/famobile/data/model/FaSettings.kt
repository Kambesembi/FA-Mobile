package com.kambesembi.famobile.data.model

/** FA's control pages the "FA settings" screen reads from. */
enum class FaSettingsPage(val path: String) {
    Account("/controls/settings/"),
    Site("/controls/site-settings/"),
    User("/controls/user-settings/"),
    Profile("/controls/profile/"),
    Contacts("/controls/contacts/"),
}

/** How one entry is read from its page's form. */
sealed interface SettingSource {
    /** A select (its chosen option), radio group (the checked one), checkbox, text field or text area. */
    data class Field(val name: String) : SettingSource

    /** Several checkboxes shown as one list of the ticked ones. */
    data class Ticks(val names: List<String>) : SettingSource

    /** The blocked tags, which FA puts on `<body>` rather than in a field. */
    data object BlockedTags : SettingSource

    /** Every filled-in contact field. */
    data object Contacts : SettingSource
}

/** One entry the user can put on their FA settings page. [label] is FA's own (English) name for it. */
data class FaSetting(val id: String, val page: FaSettingsPage, val label: String, val source: SettingSource) {
    /** Changeable in the app: plain fields, tick lists and the blocked tags. Contacts are changed on FA. */
    val editable: Boolean
        get() = (source is SettingSource.Field || source is SettingSource.Ticks || source == SettingSource.BlockedTags) && id !in NOT_EDITABLE

    companion object {
        /** Disabling the account is too big a step for a tap in a list. */
        private val NOT_EDITABLE = setOf("account_disabled")

        private fun field(page: FaSettingsPage, name: String, label: String) = FaSetting(name, page, label, SettingSource.Field(name))

        /**
         * What can be shown. Left out on purpose: passwords, the account e-mail, the date of birth and
         * the tipping system's PayPal address.
         */
        val all: List<FaSetting> = buildList {
            val a = FaSettingsPage.Account
            add(field(a, "viewmature", "Enable Adult Artwork"))
            add(field(a, "timezone", "Time Zone"))
            add(field(a, "timezone_dst", "Daylight Saving Time"))
            add(field(a, "fullview", "Image Full View"))
            add(field(a, "style", "Site Layout"))
            add(field(a, "stylesheet", "Theme"))
            add(field(a, "theme_override", "Theme Overrides"))
            add(field(a, "account_disabled", "Account Status"))

            val s = FaSettingsPage.Site
            add(field(s, "disable_avatars", "Disable Avatars"))
            add(field(s, "hour_format", "Time Format"))
            add(field(s, "date_format", "Date Format"))
            add(field(s, "perpage", "Submissions Per Page"))
            add(field(s, "newsubmissions_direction", "Submission Notifications Order"))
            add(field(s, "thumbnail_size", "Preferred Thumbnail Size"))
            add(field(s, "gallery_navigation", "Submission Gallery Navigation"))
            add(field(s, "hide_favorites", "Display Favorites"))
            add(field(s, "no_guests", "Disable Guest Access"))
            add(field(s, "no_minors", "Disable Access to Minors"))
            add(field(s, "no_search_engines", "Disable Search Engine Indexing"))
            add(field(s, "block_msg_submission_sender_younger_days", "Limit Submission Comments"))
            add(field(s, "block_msg_journal_sender_younger_days", "Limit Journal Comments"))
            add(field(s, "block_msg_shout_sender_younger_days", "Limit Shouts"))
            add(field(s, "block_msg_note_sender_younger_days", "Limit Notes"))
            add(field(s, "no_notes", "Disable Notes"))
            add(field(s, "search_order_by", "Search: Order By"))
            add(field(s, "search_order_direction", "Search: Order Direction"))
            add(field(s, "search_range", "Search: Date Range"))
            add(FaSetting("search_ratings", s, "Search: Ratings", SettingSource.Ticks(listOf("search_rating_general", "search_rating_mature", "search_rating_adult"))))
            add(
                FaSetting(
                    "search_types", s, "Search: Content Types",
                    SettingSource.Ticks(listOf("art", "music", "flash", "story", "photo", "poetry").map { "search_content_type_$it" }),
                ),
            )

            val u = FaSettingsPage.User
            add(field(u, "accept_trades", "Accepting Trades"))
            add(field(u, "accept_commissions", "Accepting Commissions"))
            add(field(u, "featured_journal_id", "Featured Journal"))

            val p = FaSettingsPage.Profile
            add(field(p, "profileinfo", "Profile Description"))
            add(field(p, "display_name", "Display Name"))
            add(field(p, "custom_title", "User Title"))
            add(field(p, "species", "Species"))
            add(field(p, "mood", "Mood"))
            add(field(p, "music", "Favorite Music Genre(s)"))
            add(field(p, "favoritemovie", "Favorite Films"))
            add(field(p, "favoritegame", "Favorite Game"))
            add(field(p, "favoriteplatform", "Preferred Gaming Platform(s)"))
            add(field(p, "favoriteartist", "Favorite Artist(s)"))
            add(field(p, "favoriteanimal", "Favorite Animal"))
            add(field(p, "favoritefood", "Favorite Food"))
            add(field(p, "favoritewebsite", "Favorite Site"))
            add(field(p, "quote", "Quote"))
            add(field(p, "featured", "Featured Submission"))
            add(field(p, "profile_pic", "Profile ID"))
            add(FaSetting("blocked_tags", p, "Blocked Tags", SettingSource.BlockedTags))
            add(field(p, "hide_missing_tags", "Hide Content Without Tags"))
            add(field(p, "blocklist", "Blocked Users"))
            add(field(p, "hide_blocked_user_content", "Hide Content by Blocked Users"))
            add(field(p, "submissionfooter", "Submission Footer"))
            add(field(p, "journalheader", "Journal Header"))
            add(field(p, "journalfooter", "Journal Footer"))

            add(FaSetting("contacts", FaSettingsPage.Contacts, "Contacts and Social Media", SettingSource.Contacts))
        }

        val byId: Map<String, FaSetting> = all.associateBy { it.id }
    }
}

/** What an entry is set to. */
sealed interface FaSettingValue {
    data class Text(val text: String, val long: Boolean = false) : FaSettingValue
    data class Flag(val on: Boolean) : FaSettingValue
    data class Items(val items: List<String>) : FaSettingValue

    /** Nothing set, or FA's page has no such field (any more). */
    data object Empty : FaSettingValue
}

/** How an entry can be changed, with what it is now; a changed copy is what gets saved. */
sealed interface FaSettingEditor {
    /** A select or radio group: [options] are (value, label). */
    data class Choice(val options: List<Pair<String, String>>, val selected: String?) : FaSettingEditor

    data class Toggle(val on: Boolean) : FaSettingEditor

    data class Free(val text: String, val multiline: Boolean) : FaSettingEditor

    /** Several checkboxes: [options] are (field name, label). */
    data class Multi(val options: List<Pair<String, String>>, val ticked: Set<String>) : FaSettingEditor

    /** A list of tags, each added or removed on its own. */
    data class Tags(val tags: List<String>) : FaSettingEditor
}
