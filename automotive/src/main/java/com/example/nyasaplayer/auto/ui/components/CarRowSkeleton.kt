package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarListRowHeight
import com.example.nyasaplayer.auto.ui.theme.CarRaised

private const val SkeletonRowCount = 4

/**
 * The loading placeholder for a list of rows: four full-width blocks at [CarListRowHeight].
 *
 * Static placeholders, no shimmer. The ambient layer is the app's only decorative motion, and it
 * is gated on vehicle state; a shimmer that ignored that gating would reintroduce exactly the
 * motion A2 removed.
 *
 * [spacing] varies by screen; the row height does not, and is not a parameter — see D60 in
 * `docs/aaos-DESIGN.md`. Pass outer padding through [modifier].
 */
@Composable
fun CarRowSkeleton(
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(SkeletonRowCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarListRowHeight)
                    .clip(RoundedCornerShape(CarCardCornerRadius))
                    .background(CarRaised),
            )
        }
    }
}
