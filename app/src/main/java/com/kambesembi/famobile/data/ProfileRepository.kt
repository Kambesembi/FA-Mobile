package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.GalleryKind
import com.kambesembi.famobile.data.model.GalleryPage
import com.kambesembi.famobile.data.model.JournalPage
import com.kambesembi.famobile.data.model.PostOutcome
import com.kambesembi.famobile.data.model.ProfileResult
import com.kambesembi.famobile.data.model.UserProfile
import com.kambesembi.famobile.data.parse.ProfileParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * User pages. Profiles stay in memory while the app runs, so going back to one doesn't reload it;
 * gallery and journal pages are loaded on demand by the screen that shows them.
 */
class ProfileRepository(private val fa: FaClient) {

    private val _profiles = MutableStateFlow<Map<String, ProfileResult>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileResult>> = _profiles.asStateFlow()

    /** Throws IOException on network trouble. */
    suspend fun load(name: String, force: Boolean = false): ProfileResult {
        val key = name.lowercase()
        if (!force) _profiles.value[key]?.let { return it }
        val result = fa.read("/user/$key/") { ProfileParser.profile(it, key) }
        _profiles.update { it.keepRecent(key, result) }
        return result
    }

    /**
     * Watches or unwatches with FA's own link. FA redirects back to the user page, so the new state
     * usually comes with the answer; if not, the profile is loaded again.
     */
    suspend fun toggleWatch(profile: UserProfile): ProfileResult {
        val watch = profile.watch ?: return ProfileResult.Loaded(profile)
        val key = profile.user.name.lowercase()
        val answer = fa.read(watch.path) { ProfileParser.profile(it, key) }
        val changed = (answer as? ProfileResult.Loaded)?.profile?.watch?.let { it.watching != watch.watching } == true
        if (!changed) return load(key, force = true)
        _profiles.update { it + (key to answer) }
        return answer
    }

    /** Posts a shout on [profile]'s page. FA lists the newest shouts first and only a few, so "new" means an id not seen before. */
    suspend fun shout(profile: UserProfile, text: String): PostOutcome<UserProfile> {
        val form = profile.shoutForm ?: return PostOutcome.Failed(null)
        val key = profile.user.name.lowercase()
        return fa.postReply(
            form = form,
            text = text,
            replyTo = null,
            reloadPath = "/user/$key/",
            before = profile.shouts.map { it.id }.toSet(),
            parse = { doc ->
                when (val result = ProfileParser.profile(doc, key)) {
                    is ProfileResult.Loaded -> Parsed(result.profile, null)
                    is ProfileResult.Unavailable -> Parsed(null, result.message)
                }
            },
            ids = { page -> page.shouts.map { it.id }.toSet() },
            keep = { page -> _profiles.update { it + (key to ProfileResult.Loaded(page)) } },
        )
    }

    /** The first page, or the one [path] points to ("Next"). */
    suspend fun gallery(name: String, kind: GalleryKind, path: String? = null): GalleryPage =
        fa.read(path ?: kind.firstPage(name.lowercase())) { ProfileParser.gallery(it, name) }

    suspend fun journals(name: String, path: String? = null): JournalPage =
        fa.read(path ?: "/journals/${name.lowercase()}/") { ProfileParser.journals(it) }

    fun clear() {
        _profiles.value = emptyMap()
    }
}
