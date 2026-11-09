package com.kambesembi.famobile

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Saved pages from `fixtures/` (on the unit-test classpath). */
object Fixtures {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }.readText()

    fun document(name: String, url: String = "https://www.furaffinity.net/"): Document = Jsoup.parse(text(name), url)
}
