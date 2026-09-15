package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold

/**
 * The gold chip that tells a driver why a control on this screen is locked.
 *
 * One implementation for the queue and for Downloads. They carried a copy each, which is how the
 * fill opacity below came to matter twice.
 *
 * **The fill is opaque [CarGlass], not a gold wash.** A translucent wash takes its contrast from
 * whatever is behind it, and this chip is drawn in two very different places: the queue sits on an
 * opaque background, Downloads sits on the ambient gradient. Measured over the ambient layer the
 * old 12% gold wash came out at 6.94:1 — under NFR-2 — while the same chip over the queue passed.
 * An opaque surface makes the pair the same everywhere: gold on [CarGlass] measures 7.69:1.
 */
@Composable
fun CarDrivingHelperChip(
    message: String,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(ChipCornerRadius)
    Row(
        modifier = modifier
            .height(ChipHeight)
            .clip(chipShape)
            .background(CarGlass)
            .border(1.dp, NyasaGold.copy(alpha = BorderOpacity), chipShape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = NyasaGold,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            color = NyasaGold,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private const val BorderOpacity = 0.3f
private val ChipHeight = 56.dp
private val ChipCornerRadius = 24.dp
