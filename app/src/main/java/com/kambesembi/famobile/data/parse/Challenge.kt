package com.kambesembi.famobile.data.parse

/** Recognises Cloudflare's "Just a moment…" interstitial, which FA puts in front of pages now and then. */
object Challenge {

    fun isChallenge(status: Int, cfMitigated: String?, body: String): Boolean {
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true
        if (status != 403 && status != 503 && status != 429) return false
        return isChallengeHtml(body)
    }

    fun isChallengeHtml(body: String): Boolean =
        "_cf_chl_opt" in body || "/cdn-cgi/challenge-platform/" in body || "<title>Just a moment" in body

    /** Run in the challenge WebView: "challenge" while Cloudflare's page is up, "ok" once the real page has loaded. */
    const val PROBE_JS = """(function(){
        if (location.hostname.indexOf('furaffinity.net') < 0) return 'loading';
        if (typeof window._cf_chl_opt !== 'undefined' || document.title.indexOf('Just a moment') === 0) return 'challenge';
        return document.readyState === 'complete' ? 'ok' : 'loading';
    })()"""
}
