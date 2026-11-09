package com.kambesembi.famobile.data

object FaUrls {
    const val HOST = "www.furaffinity.net"
    const val BASE = "https://$HOST"
    const val LOGIN = "$BASE/login"

    /** The smallest logged-in page that still carries the header with all unread counts. */
    const val COUNTS_PAGE = "/controls/settings/"

    /** "/msg/pms/" → full URL; full URLs pass through. Protocol-relative "//a.furaffinity.net/..." gets https. */
    fun absolute(pathOrUrl: String): String = when {
        pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("http://") -> pathOrUrl
        pathOrUrl.startsWith("//") -> "https:$pathOrUrl"
        pathOrUrl.startsWith("/") -> BASE + pathOrUrl
        else -> "$BASE/$pathOrUrl"
    }

    /** A user's avatar by name alone; FA's own avatar URLs add a timestamp that only busts caches. */
    fun avatar(name: String): String = "https://a.furaffinity.net/${name.lowercase()}.gif"

    fun isFa(host: String?): Boolean = host != null && (host == "furaffinity.net" || host.endsWith(".furaffinity.net"))
}
