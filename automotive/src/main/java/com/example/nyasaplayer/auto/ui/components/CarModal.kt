package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarScrim

private const val ModalWidthFraction = 0.5f
private val ModalCornerRadius = 24.dp
private val ModalPadding = 48.dp

/** Below this much room for the card, the compact values apply (T30). */
private val CompactModalHeight = 420.dp
private val CompactModalPadding = 24.dp
private const val CompactModalWidthFraction = 0.72f

/**
 * The dimmed backdrop every modal in the shell sits on, and the tap that dismisses it.
 *
 * All three modals — sign-out, playback error, restriction refusal — carried their own copy of
 * this until T22. Their *cards* are not all the same, so this owns the backdrop only and each
 * modal brings its own content: two of them use [CarModalCard], the restriction dialog has its
 * own geometry.
 *
 * Anything placed in [content] that should not dismiss on tap must consume touches itself —
 * a tap that no child consumes bubbles here.
 */
@Composable
fun CarModalScrim(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The glass card the sign-out, error and remove-all modals share: half the slot's width, or
 * [CompactModalWidthFraction] of it with tighter padding when the slot is short (T30).
 *
 * Its content is measured at its own height, so a child must not use `Modifier.weight` — there is
 * no bounded height to share out.
 *
 * [carConsumeTouches] is what keeps a tap on the card from bubbling to [CarModalScrim] and
 * dismissing the modal underneath the driver's finger. A card with no guard at all does leak that
 * way — measured, not assumed.
 *
 * `CarErrorOverlay` previously used `clickable(enabled = false)`, which blocks the tap just as
 * effectively; the reason to prefer this one is semantics, not dismissal. A disabled clickable
 * publishes an interactive-but-disabled node to accessibility services, announcing a control that
 * was never there (FR-2.6); `detectTapGestures` publishes nothing.
 *
 * The restriction dialog deliberately does not use this: it is a fixed 780dp wide with its own
 * corner radius and padding.
 */
@Composable
fun CarModalCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        // A short slot buys height twice over: less padding, and a wider card, which costs the
        // body a line of wrapping.
        val compact = maxHeight < CompactModalHeight
        val padding = if (compact) CompactModalPadding else ModalPadding
        val width = if (compact) CompactModalWidthFraction else ModalWidthFraction
        Column(
            modifier = Modifier
                .fillMaxWidth(width)
                .clip(RoundedCornerShape(ModalCornerRadius))
                .background(CarGlass)
                .carConsumeTouches()
                // The last resort when even compact padding does not fit. Without it a Column hands
                // its children whatever height is left, so a 76dp button in a cramped slot is
                // measured at 28dp and a driver gets a squashed control that still takes a tap
                // (T30). With it the card keeps every child its own size and moves the overflow.
                .verticalScroll(rememberScrollState())
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
