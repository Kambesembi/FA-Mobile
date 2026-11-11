package com.kambesembi.famobile.data

/** Pages kept in memory per kind (profiles, journals): the most recent ones; older ones load again when shown. */
internal const val RECENT_PAGES = 30

/** This map with [key] as its newest entry, and without its oldest ones beyond [limit]. */
internal fun <K, V> Map<K, V>.keepRecent(key: K, value: V, limit: Int = RECENT_PAGES): Map<K, V> {
    val next = LinkedHashMap(this)
    next.remove(key)
    next[key] = value
    while (next.size > limit) next.remove(next.keys.first())
    return next
}
