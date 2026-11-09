package com.kambesembi.famobile.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.BrowseFilters
import com.kambesembi.famobile.data.model.FilterOption
import com.kambesembi.famobile.data.model.FilterOptions
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SearchMode
import com.kambesembi.famobile.data.model.SearchOrder
import com.kambesembi.famobile.data.model.SearchQuery
import com.kambesembi.famobile.data.model.SearchRange
import com.kambesembi.famobile.data.model.SearchType
import com.kambesembi.famobile.ui.inbox.ratingColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseFilterSheet(filters: BrowseFilters, options: FilterOptions, onApply: (BrowseFilters) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(filters) }
    Sheet(
        title = stringResource(R.string.explore_browse_filters),
        onDismiss = onDismiss,
        onReset = { draft = BrowseFilters(ratings = filters.ratings) },
        onApply = {
            onApply(draft)
            onDismiss()
        },
    ) {
        Ratings(options.ratings, draft.ratings) { draft = draft.copy(ratings = it) }
        Picker(stringResource(R.string.filter_category), options.categories, draft.category) { draft = draft.copy(category = it) }
        Picker(stringResource(R.string.filter_type), options.types, draft.type) { draft = draft.copy(type = it) }
        Picker(stringResource(R.string.filter_species), options.species, draft.species) { draft = draft.copy(species = it) }
        Picker(stringResource(R.string.filter_gender), options.genders, draft.gender) { draft = draft.copy(gender = it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(query: SearchQuery, options: FilterOptions, onApply: (SearchQuery) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(query) }
    Sheet(
        title = stringResource(R.string.explore_search_filters),
        onDismiss = onDismiss,
        onReset = { draft = SearchQuery(query.text, ratings = query.ratings) },
        onApply = {
            onApply(draft)
            onDismiss()
        },
    ) {
        Label(stringResource(R.string.filter_order))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val orders = SearchOrder.entries
            orders.forEachIndexed { index, order ->
                SegmentedButton(
                    selected = draft.order == order,
                    onClick = { draft = draft.copy(order = order) },
                    shape = SegmentedButtonDefaults.itemShape(index, orders.size),
                ) { Text(stringResource(order.label)) }
            }
        }
        Spacer(Modifier.size(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(true, false).forEachIndexed { index, descending ->
                SegmentedButton(
                    selected = draft.descending == descending,
                    onClick = { draft = draft.copy(descending = descending) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(stringResource(if (descending) R.string.filter_descending else R.string.filter_ascending)) }
            }
        }

        Ratings(options.ratings, draft.ratings) { draft = draft.copy(ratings = it) }

        Label(stringResource(R.string.filter_kinds))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchType.entries.forEach { type ->
                val on = type in draft.types
                FilterChip(
                    selected = on,
                    onClick = {
                        val next = if (on) draft.types - type else draft.types + type
                        if (next.isNotEmpty()) draft = draft.copy(types = next)
                    },
                    label = { Text(stringResource(type.label)) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }

        Picker(
            stringResource(R.string.filter_range),
            SearchRange.entries.map { FilterOption(it.name, stringResource(it.label)) },
            draft.range.name,
        ) { name -> draft = draft.copy(range = SearchRange.valueOf(name)) }

        Label(stringResource(R.string.filter_mode))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = SearchMode.entries
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = draft.mode == mode,
                    onClick = { draft = draft.copy(mode = mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) { Text(stringResource(mode.label)) }
            }
        }

        Picker(stringResource(R.string.filter_category), options.categories, draft.category) { draft = draft.copy(category = it) }
        Picker(stringResource(R.string.filter_type), options.types, draft.type) { draft = draft.copy(type = it) }
        Picker(stringResource(R.string.filter_species), options.species, draft.species) { draft = draft.copy(species = it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
            Spacer(Modifier.size(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.filter_reset)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onApply) { Text(stringResource(R.string.filter_apply)) }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/**
 * Which ratings to show, as plain checkboxes: ticked means included. Ratings the account can't see
 * are greyed out, with the reason below. At least one stays ticked.
 */
@Composable
private fun Ratings(available: Set<Rating>, selected: Set<Rating>, onChange: (Set<Rating>) -> Unit) {
    Label(stringResource(R.string.filter_ratings_show))
    Rating.entries.forEach { rating ->
        val allowed = rating in available
        val on = allowed && rating in selected
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = on, enabled = allowed, role = Role.Checkbox) { checked ->
                    val next = if (checked) selected + rating else selected - rating
                    if (next.isNotEmpty()) onChange(next)
                }
                .padding(vertical = 2.dp),
        ) {
            Checkbox(checked = on, onCheckedChange = null, enabled = allowed)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(10.dp)
                    .background(ratingColor(rating).copy(alpha = if (allowed) 1f else 0.35f), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    when (rating) {
                        Rating.General -> R.string.rating_general
                        Rating.Mature -> R.string.rating_mature
                        Rating.Adult -> R.string.rating_adult
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
    if (available.size < Rating.entries.size) {
        Text(
            stringResource(R.string.filter_ratings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One of FA's lists as a dropdown, with its groups ("Visual Art", "Readable Art", …) as headings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(label: String, options: List<FilterOption>, selected: String, onSelect: (String) -> Unit) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Spacer(Modifier.size(12.dp))
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.value == selected }?.label ?: options.first().label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            var group: String? = null
            options.forEach { option ->
                if (option.group != null && option.group != group) {
                    group = option.group
                    Text(
                        option.group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.value)
                        open = false
                    },
                )
            }
        }
    }
}

private val SearchOrder.label: Int
    get() = when (this) {
        SearchOrder.Relevancy -> R.string.filter_order_relevancy
        SearchOrder.Date -> R.string.filter_order_date
        SearchOrder.Popularity -> R.string.filter_order_popularity
    }

private val SearchMode.label: Int
    get() = when (this) {
        SearchMode.All -> R.string.filter_mode_all
        SearchMode.Any -> R.string.filter_mode_any
        SearchMode.Extended -> R.string.filter_mode_extended
    }

private val SearchType.label: Int
    get() = when (this) {
        SearchType.Art -> R.string.filter_kind_art
        SearchType.Music -> R.string.filter_kind_music
        SearchType.Flash -> R.string.filter_kind_flash
        SearchType.Story -> R.string.filter_kind_story
        SearchType.Photo -> R.string.filter_kind_photo
        SearchType.Poetry -> R.string.filter_kind_poetry
    }

private val SearchRange.label: Int
    get() = when (this) {
        SearchRange.Day -> R.string.filter_range_day
        SearchRange.ThreeDays -> R.string.filter_range_3days
        SearchRange.Week -> R.string.filter_range_week
        SearchRange.Month -> R.string.filter_range_month
        SearchRange.ThreeMonths -> R.string.filter_range_3months
        SearchRange.Year -> R.string.filter_range_year
        SearchRange.ThreeYears -> R.string.filter_range_3years
        SearchRange.FiveYears -> R.string.filter_range_5years
        SearchRange.All -> R.string.filter_range_all
    }
