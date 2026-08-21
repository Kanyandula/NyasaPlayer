package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarOutline
import com.example.nyasaplayer.auto.ui.theme.CarPillButtonHeight
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.auto.viewmodel.AutomotiveSearchUiState
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold

private val SectionSpacing = 32.dp
private val HeaderSpacing = 24.dp
private val LabelSpacing = 12.dp
private val ChipSpacing = 12.dp
private val FieldPadding = 20.dp
private val FieldBorderWidth = 1.dp
private val IconSize = 28.dp
private val SearchCtaWidth = 176.dp
private val FieldShape = RoundedCornerShape(CarCardCornerRadius)
private val FieldTextSize = 22.sp
private val LabelSize = 22.sp
private val BodySize = 20.sp

private const val FieldPlaceholder = "Search songs, artists, albums"
private const val VoicePromptText =
    "Typing is off while the vehicle is moving. Ask your assistant to play something instead."
private const val RecentEmptyText = "Searches you make on this trip show up here."
private val LibraryShortcuts = listOf("Albums", "Artists", "Playlists")

/**
 * Screen 5 — the search sheet's idle and text-entry states.
 *
 * [canType] is the platform's `NO_KEYBOARD` answer inverted, not a driving guess. When it is
 * false the screen has no editable field at all: it renders a prompt pointing at system/Assistant
 * voice search, which is the only voice path the app has. Nothing here records audio, and the
 * prompt is deliberately not clickable — a voice button with no wired action would be exactly the
 * silent no-op FR-2.6 prohibits (spec D31).
 *
 * Submitted results are screen 6's job; this screen never renders them.
 */
@Composable
fun CarSearchScreen(
    state: AutomotiveSearchUiState,
    canType: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onRecentClick: (String) -> Unit,
    onBrowseGenres: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldFocus = remember { FocusRequester() }
    // Remembered so the browse-by row keeps a stable callback and can skip recomposition while
    // the driver types — an inline lambda here is a new object on every keystroke.
    val focusField = remember(fieldFocus) { { fieldFocus.requestFocus() } }

    Column(
        // Opaque for the same reason as CarQueueScreen: a sheet occludes the shell behind it.
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .padding(CarScreenMargin),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        SearchHeader(
            query = state.query,
            canType = canType,
            fieldFocus = fieldFocus,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onClearQuery = onClearQuery,
            onEditingChange = onEditingChange,
            onClose = onClose,
        )

        RecentQueries(queries = state.recentQueries, onRecentClick = onRecentClick)

        BrowseByShortcuts(
            canType = canType,
            onFocusField = focusField,
            onBrowseGenres = onBrowseGenres,
            onBrowseLibrary = onBrowseLibrary,
        )
    }
}

@Composable
private fun SearchHeader(
    query: String,
    canType: Boolean,
    fieldFocus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeaderSpacing),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(CarTouchTargetSize)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                tint = Color.White,
                modifier = Modifier.size(IconSize),
            )
        }

        if (canType) {
            SearchField(
                query = query,
                fieldFocus = fieldFocus,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClearQuery = onClearQuery,
                onEditingChange = onEditingChange,
                modifier = Modifier.weight(1f),
            )

            // Fixed slot. The CTA only exists once there is something to submit — a Search
            // button over a blank field would refuse every press — but the field must not
            // resize under the driver's finger the moment the first character makes it appear.
            Box(modifier = Modifier.width(SearchCtaWidth), contentAlignment = Alignment.Center) {
                if (query.isNotBlank()) {
                    CarPillButton(
                        label = "Search",
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            // No CTA beside the prompt. A query typed before the vehicle moved survives in
            // state, so the button would still render — offering to run a search whose terms
            // the driver can no longer see, because the field it was typed into is gone.
            VoicePrompt(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    fieldFocus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compose does not deliver isFocused = false when a focused node leaves composition, so the
    // flag has to be cleared where the field is owned. Disposal covers every way the field can
    // go away — sheet closed, query submitted, NO_KEYBOARD arrived, tab switched, gate eviction
    // — which is why none of those need to remember to clear it themselves.
    val editingChange by rememberUpdatedState(onEditingChange)
    DisposableEffect(Unit) {
        onDispose { editingChange(false) }
    }

    Row(
        modifier = modifier
            .height(CarPillButtonHeight)
            .background(color = CarGlass, shape = FieldShape)
            .border(width = FieldBorderWidth, color = CarOutline, shape = FieldShape)
            .padding(start = FieldPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SearchIcon,
            contentDescription = null,
            tint = CarTextSecondary,
            modifier = Modifier.size(IconSize),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = FieldTextSize),
            cursorBrush = SolidColor(NyasaGold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = FieldPadding)
                .focusRequester(fieldFocus)
                // Focus is what the restriction gate means by text entry — not a non-empty
                // query, which survives a parked search into the next drive.
                .onFocusChanged { onEditingChange(it.isFocused) },
            decorationBox = { field ->
                if (query.isEmpty()) {
                    Text(
                        text = FieldPlaceholder,
                        color = CarTextSecondary,
                        fontSize = FieldTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClearQuery, modifier = Modifier.size(CarTouchTargetSize)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear query",
                    tint = CarTextSecondary,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}

/** Not clickable, and no microphone icon: the app offers no in-app capture to imply one. */
@Composable
private fun VoicePrompt(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(CarPillButtonHeight)
            .background(color = CarGlass, shape = FieldShape)
            .border(width = FieldBorderWidth, color = CarOutline, shape = FieldShape)
            .padding(horizontal = FieldPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FieldPadding),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = CarTextSecondary,
            modifier = Modifier.size(IconSize),
        )
        Text(
            text = VoicePromptText,
            color = CarTextSecondary,
            fontSize = BodySize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentQueries(
    queries: List<String>,
    onRecentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LabelSpacing),
    ) {
        SectionLabel(text = "Recent searches")
        if (queries.isEmpty()) {
            Text(text = RecentEmptyText, color = CarTextSecondary, fontSize = BodySize)
        } else {
            // Capped at five by the ViewModel, so the row scrolls only when the queries are long.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
                items(items = queries, key = { it }) { query ->
                    CarPillButton(
                        label = query,
                        onClick = { onRecentClick(query) },
                        filled = false,
                    )
                }
            }
        }
    }
}

/**
 * Navigation shortcuts, not result filters — each one leaves the driver somewhere real.
 *
 * The design's component table calls for a `CarChip` here. [CarPillButton]'s ghost variant is
 * already that shape at the same 76dp target, so this reuses it rather than adding a second
 * primitive with one consumer.
 */
@Composable
private fun BrowseByShortcuts(
    canType: Boolean,
    onFocusField: () -> Unit,
    onBrowseGenres: () -> Unit,
    onBrowseLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LabelSpacing),
    ) {
        SectionLabel(text = "Browse by")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
            // Songs' only action is focusing the field, so it ships only when there is a field
            // to focus. Under NO_KEYBOARD it would be the clickable no-op FR-2.6 prohibits.
            if (canType) {
                item {
                    CarPillButton(label = "Songs", onClick = onFocusField, filled = false)
                }
            }
            item {
                CarPillButton(label = "Genres", onClick = onBrowseGenres, filled = false)
            }
            items(items = LibraryShortcuts) { label ->
                CarPillButton(label = label, onClick = onBrowseLibrary, filled = false)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = LabelSize,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
