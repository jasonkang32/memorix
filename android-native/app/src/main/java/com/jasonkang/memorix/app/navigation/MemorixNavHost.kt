package com.jasonkang.memorix.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jasonkang.memorix.core.designsystem.theme.MemorixCardDark
import com.jasonkang.memorix.core.designsystem.theme.MemorixCardLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixMutedDark
import com.jasonkang.memorix.core.designsystem.theme.MemorixMutedLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import com.jasonkang.memorix.core.designsystem.theme.MemorixSurfaceDark
import com.jasonkang.memorix.core.designsystem.theme.MemorixSurfaceLight
import com.jasonkang.memorix.feature.albums.AlbumDetailScreen
import com.jasonkang.memorix.feature.auth.AuthViewModel
import com.jasonkang.memorix.feature.auth.LockScreen
import com.jasonkang.memorix.core.auth.AuthGateState
import com.jasonkang.memorix.feature.detail.MediaDetailScreen
import com.jasonkang.memorix.feature.home.HomeScreen
import com.jasonkang.memorix.feature.personal.PersonalScreen
import com.jasonkang.memorix.feature.search.SearchScreen
import com.jasonkang.memorix.feature.settings.SettingsScreen
import com.jasonkang.memorix.feature.work.WorkScreen
import com.jasonkang.memorix.feature.work.compose.MediaComposeScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

private object Routes {
    const val Home = "home"
    const val Work = "work"
    const val Personal = "personal"
    const val Search = "search"
    const val Settings = "settings"
    const val AlbumDetail = "album/{albumId}"
    const val MediaDetail = "media/{mediaId}"
    const val WorkCompose = "work/compose"

    fun albumDetail(albumId: Long) = "album/$albumId"
    fun mediaDetail(mediaId: Long) = "media/$mediaId"
}

@Composable
fun MemorixNavHost() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (authState.gateState) {
        AuthGateState.Checking -> {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            )
        }
        AuthGateState.Locked -> {
            LockScreen(
                state = authState,
                onDigit = authViewModel::appendPinDigit,
                onDelete = authViewModel::deletePinDigit,
                onBiometricSuccess = authViewModel::unlockByBiometric,
                onBiometricError = authViewModel::showError,
            )
        }
        AuthGateState.Unlocked -> {
            MemorixUnlockedNavHost(authViewModel = authViewModel, authState = authState)
        }
    }
}

@Composable
private fun MemorixUnlockedNavHost(
    authViewModel: AuthViewModel,
    authState: com.jasonkang.memorix.feature.auth.AuthUiState,
) {
    val navController = rememberNavController()
    val destinations = listOf(
        TopLevelDestination(Routes.Home, "Home") { Icon(Icons.Outlined.Home, contentDescription = null) },
        TopLevelDestination(Routes.Work, "Work") { Icon(Icons.Outlined.Work, contentDescription = null) },
        TopLevelDestination(Routes.Personal, "Personal") { Icon(Icons.Outlined.Favorite, contentDescription = null) },
        TopLevelDestination(Routes.Settings, "설정") { Icon(Icons.Outlined.Settings, contentDescription = null) },
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val isDark = colorScheme.background == MemorixCardDark || colorScheme.background.red < 0.2f

    Scaffold(
        containerColor = if (isDark) MemorixSurfaceDark else MemorixSurfaceLight,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .background(if (isDark) MemorixCardDark else MemorixCardLight)
                    .navigationBarsPadding()
                    .height(72.dp),
                containerColor = if (isDark) MemorixCardDark else MemorixCardLight,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = MemorixPrimary,
                            selectedTextColor = MemorixPrimary,
                            unselectedIconColor = if (isDark) MemorixMutedDark else MemorixMutedLight,
                            unselectedTextColor = if (isDark) MemorixMutedDark else MemorixMutedLight,
                            indicatorColor = MemorixPrimary.copy(alpha = if (isDark) 1f else 0.15f),
                        ),
                        icon = destination.icon,
                        label = { Text(destination.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) },
                    onSearchClick = { navController.navigate(Routes.Search) },
                    onWorkClick = { navController.navigate(Routes.Work) },
                    onPersonalClick = { navController.navigate(Routes.Personal) },
                )
            }
            composable(Routes.Work) {
                WorkScreen(
                    onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) },
                    onNavigateToCompose = { navController.navigate(Routes.WorkCompose) },
                )
            }
            composable(Routes.WorkCompose) {
                MediaComposeScreen(
                    onBack = { navController.popBackStack() },
                    onSaveComplete = {
                        navController.popBackStack(Routes.Work, inclusive = false)
                    },
                )
            }
            composable(Routes.Personal) {
                PersonalScreen(
                    onAlbumClick = { albumId -> navController.navigate(Routes.albumDetail(albumId)) },
                    onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) },
                )
            }
            composable(Routes.Search) {
                SearchScreen(onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) })
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    authState = authState,
                    onSetPin = authViewModel::setPin,
                    onClearPin = authViewModel::clearPin,
                    onBiometricEnabledChange = authViewModel::setBiometricEnabled,
                    onPersonalLockEnabledChange = authViewModel::setPersonalLockEnabled,
                    onConsumeMessages = authViewModel::consumeMessages,
                )
            }
            composable(
                route = Routes.AlbumDetail,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            ) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) },
                )
            }
            composable(
                route = Routes.MediaDetail,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) {
                MediaDetailScreen()
            }
        }
    }
}
