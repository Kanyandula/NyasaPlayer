package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
 * The half-width glass card the sign-out and error modals share.
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
    Column(
        modifier = modifier
            .fillMaxWidth(ModalWidthFraction)
            .clip(RoundedCornerShape(ModalCornerRadius))
            .background(CarGlass)
            .carConsumeTouches()
            // Scrolls only when the slot is too short for the card. Without it a Column hands its
            // children whatever height is left, so a 76dp button in a cramped slot is measured at
            // 28dp and a driver gets a squashed control that still takes a tap (T30). With it the
            // card keeps every child its own size and moves the overflow rather than crushing it.
            .verticalScroll(rememberScrollState())
            .padding(ModalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
