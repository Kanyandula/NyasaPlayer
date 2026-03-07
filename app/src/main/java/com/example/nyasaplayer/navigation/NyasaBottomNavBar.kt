package com.example.nyasaplayer.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.ui.icons.HomeIcon
import com.example.nyasaplayer.core.common.ui.icons.LibraryIcon
import com.example.nyasaplayer.core.common.ui.icons.ProfileIcon
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface4
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

private val NavBarShape = RoundedCornerShape(24.dp)

private data class NavItem(
    val route: String,
    @StringRes val labelResId: Int,
    val icon: ImageVector,
)

private val NavItems = listOf(
    NavItem(route = HomeRoute, labelResId = R.string.home, icon = HomeIcon),
    NavItem(route = SearchRoute, labelResId = R.string.search, icon = SearchIcon),
    NavItem(route = LibraryRoute, labelResId = R.string.library, icon = LibraryIcon),
    NavItem(route = ProfileRoute, labelResId = R.string.profile, icon = ProfileIcon),
)

@Composable
fun NyasaBottomNavBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(NavBarShape)
            .background(NyasaSurface2)
            .border(width = 1.dp, color = NyasaSurface4, shape = NavBarShape)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItems.forEach { item ->
            val selected = currentRoute == item.route
            val tint = if (selected) NyasaPrimary else NyasaTextSecondary

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        navController.navigateToTab(item.route)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelResId),
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(item.labelResId),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
        }
    }
}
