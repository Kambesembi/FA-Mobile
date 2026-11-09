package com.kambesembi.famobile.data.model

/** One choice in one of FA's filter lists ("Artwork (Digital)" in the "Visual Art" group). */
data class FilterOption(val value: String, val label: String, val group: String? = null)

/** The lists FA offers on the browse and search pages, read from the page so they stay current. */
data class FilterOptions(
    val categories: List<FilterOption> = emptyList(),
    val types: List<FilterOption> = emptyList(),
    val species: List<FilterOption> = emptyList(),
    val genders: List<FilterOption> = emptyList(),
    /** The ratings this account may see (FA greys out Mature and Adult unless they're enabled). */
    val ratings: Set<Rating> = setOf(Rating.General),
    /** The ratings ticked on this page: the account's defaults on a first visit. */
    val checkedRatings: Set<Rating> = setOf(Rating.General),
    /** Results per page as FA used them (the account's setting on a first visit). */
    val perPage: Int? = null,
) {
    val isEmpty: Boolean get() = categories.isEmpty() && types.isEmpty() && species.isEmpty()
}

/** /browse/: the newest submissions site-wide, narrowed down by FA's filters. */
data class BrowseFilters(
    val category: String = "1",
    val type: String = "1",
    val species: String = "1",
    val gender: String = "",
    val ratings: Set<Rating> = setOf(Rating.General),
) {
    fun path(page: Int, perPage: Int): String = buildString {
        append("/browse/?cat=").append(category)
        append("&atype=").append(type)
        append("&species=").append(species)
        if (gender.isNotEmpty()) append("&gender=").append(gender)
        append("&rating_general=").append(if (Rating.General in ratings) 1 else 0)
        append("&rating_mature=").append(if (Rating.Mature in ratings) 1 else 0)
        append("&rating_adult=").append(if (Rating.Adult in ratings) 1 else 0)
        append("&perpage=").append(perPage)
        append("&page=").append(page)
        append("&go=Apply")
    }

    val isDefault: Boolean get() = this == BrowseFilters(ratings = ratings)
}

enum class SearchOrder(val value: String) { Relevancy("relevancy"), Date("date"), Popularity("popularity") }

enum class SearchRange(val value: String) {
    Day("1day"), ThreeDays("3days"), Week("7days"), Month("30days"), ThreeMonths("90days"),
    Year("1year"), ThreeYears("3years"), FiveYears("5years"), All("all"),
}

/** How the words combine: all of them, any of them, or FA's extended syntax (the default). */
enum class SearchMode(val value: String) { All("all"), Any("any"), Extended("extended") }

/** The kinds of submission FA's search can include. */
enum class SearchType(val param: String) { Art("type-art"), Music("type-music"), Flash("type-flash"), Story("type-story"), Photo("type-photo"), Poetry("type-poetry") }

data class SearchQuery(
    val text: String,
    val order: SearchOrder = SearchOrder.Relevancy,
    val descending: Boolean = true,
    val range: SearchRange = SearchRange.All,
    val mode: SearchMode = SearchMode.Extended,
    val types: Set<SearchType> = SearchType.entries.toSet(),
    val category: String = "1",
    val type: String = "1",
    val species: String = "1",
    val ratings: Set<Rating> = setOf(Rating.General),
) {
    fun path(page: Int, perPage: Int): String = buildString {
        append("/search/?q=").append(java.net.URLEncoder.encode(text, "UTF-8"))
        append("&order-by=").append(order.value)
        append("&order-direction=").append(if (descending) "desc" else "asc")
        append("&range=").append(range.value)
        append("&mode=").append(mode.value)
        append("&category=").append(category)
        append("&arttype=").append(this@SearchQuery.type)
        append("&species=").append(species)
        if (Rating.General in ratings) append("&rating-general=1")
        if (Rating.Mature in ratings) append("&rating-mature=1")
        if (Rating.Adult in ratings) append("&rating-adult=1")
        types.forEach { append('&').append(it.param).append("=1") }
        append("&perpage=").append(perPage)
        append("&page=").append(page)
    }

    /** True when only the words differ from a plain search. */
    val isDefault: Boolean get() = this == SearchQuery(text, ratings = ratings)
}

data class ResultsPage(
    val items: List<SubmissionPreview>,
    val hasNext: Boolean,
    /** "of 492588" on search pages. */
    val total: Int?,
    val options: FilterOptions,
)
