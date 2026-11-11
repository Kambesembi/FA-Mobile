package com.kambesembi.famobile.ui.viewer

import kotlin.random.Random

/**
 * The lists the viewer swipes through, kept here under a ticket so a route carries only that: a
 * few thousand ids in a route are saved with every screen behind it. After the app was closed in the
 * background the lists are gone, and the viewer shows the one submission it was opened at.
 */
class ViewerLists {
    private val lists = LinkedHashMap<Long, List<Long>>()

    @Synchronized
    fun put(ids: List<Long>): Long {
        var ticket: Long
        do ticket = Random.nextLong(1, Long.MAX_VALUE) while (ticket in lists)
        lists[ticket] = ids
        while (lists.size > KEPT) lists.remove(lists.keys.first())
        return ticket
    }

    @Synchronized
    fun get(ticket: Long): List<Long>? = lists[ticket]

    private companion object {
        /** More than the screens one can stack up in practice. */
        const val KEPT = 40
    }
}
