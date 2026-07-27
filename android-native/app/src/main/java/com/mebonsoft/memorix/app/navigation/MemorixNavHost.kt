package com.mebonsoft.memorix.app.navigation

import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mebonsoft.memorix.core.designsystem.theme.MemorixCardDark
import com.mebonsoft.memorix.core.designsystem.theme.MemorixCardLight
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMutedDark
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMutedLight
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixSurfaceDark
import com.mebonsoft.memorix.core.designsystem.theme.MemorixSurfaceLight
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.app.share.PendingShareImportHolder
import com.mebonsoft.memorix.feature.albums.AlbumDetailScreen
import com.mebonsoft.memorix.feature.auth.AuthViewModel
import com.mebonsoft.memorix.feature.auth.LockScreen
import com.mebonsoft.memorix.core.auth.AuthGateState
import com.mebonsoft.memorix.feature.detail.MediaDetailScreen
import com.mebonsoft.memorix.feature.hidden.HiddenVaultScreen
import com.mebonsoft.memorix.feature.home.HomeScreen
import com.mebonsoft.memorix.feature.personal.PersonalScreen
import com.mebonsoft.memorix.feature.onboarding.OnboardingScreen
import com.mebonsoft.memorix.feature.onboarding.OnboardingViewModel
import com.mebonsoft.memorix.feature.search.SearchScreen
import com.mebonsoft.memorix.core.locale.MemorixStrings
import com.mebonsoft.memorix.feature.settings.SettingsBackupViewModel
import com.mebonsoft.memorix.feature.settings.ProBillingViewModel
import com.mebonsoft.memorix.feature.settings.SettingsViewModel
import com.mebonsoft.memorix.feature.settings.SettingsScreen
import com.mebonsoft.memorix.feature.work.WorkScreen
import com.mebonsoft.memorix.feature.work.compose.MediaComposeScreen
import com.mebonsoft.memorix.feature.work.compose.PendingMediaHolder

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
    const val PersonalCompose = "personal/compose"
    const val HiddenVault = "settings/hidden-vault"

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
    authState: com.mebonsoft.memorix.feature.auth.AuthUiState,
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingDone by onboardingViewModel.onboardingDone.collectAsStateWithLifecycle()
    if (onboardingDone != true) {
        if (onboardingDone == false) {
            OnboardingScreen(onDone = onboardingViewModel::markDone)
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            )
        }
        return
    }

    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val strings = MemorixStrings.forLanguage(settingsState.selectedLanguage)
    val destinations = listOf(
        TopLevelDestination(Routes.Home, strings.navHome) { Icon(Icons.Outlined.Home, contentDescription = null) },
        TopLevelDestination(Routes.Work, strings.navWork) { Icon(Icons.Outlined.Work, contentDescription = null) },
        TopLevelDestination(Routes.Personal, strings.navPersonal) { Icon(Icons.Outlined.Favorite, contentDescription = null) },
        TopLevelDestination(Routes.Settings, strings.navSettings) { Icon(Icons.Outlined.Settings, contentDescription = null) },
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val pendingShareUris by PendingShareImportHolder.pending.collectAsStateWithLifecycle()
    var shareImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val isDark = colorScheme.background == MemorixCardDark || colorScheme.background.red < 0.2f

    fun openSharedImport(space: MediaSpace) {
        val sharedUris = shareImportUris
        if (sharedUris.isEmpty()) return
        val baseRoute = if (space == MediaSpace.PERSONAL) Routes.Personal else Routes.Work
        val composeRoute = if (space == MediaSpace.PERSONAL) Routes.PersonalCompose else Routes.WorkCompose
        val poppedToBase = navController.popBackStack(baseRoute, inclusive = false)
        if (!poppedToBase) {
            navController.navigateTopLevel(baseRoute)
        }
        PendingMediaHolder.set(sharedUris)
        shareImportUris = emptyList()
        navController.navigate(composeRoute) { launchSingleTop = true }
    }

    LaunchedEffect(pendingShareUris) {
        if (pendingShareUris.isEmpty()) return@LaunchedEffect
        val sharedUris = PendingShareImportHolder.consume()
        if (sharedUris.isEmpty()) return@LaunchedEffect
        shareImportUris = sharedUris
    }

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
                    val selected = currentDestination?.hierarchy?.any {
                        isRouteInTopLevelSection(it.route, destination.route)
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigateTopLevel(destination.route)
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
                    onNavigateToCompose = { uris ->
                        PendingMediaHolder.set(uris)
                        navController.navigate(Routes.WorkCompose)
                    },
                )
            }
            composable(Routes.WorkCompose) {
                MediaComposeScreen(
                    initialUris = PendingMediaHolder.consume(),
                    space = MediaSpace.WORK,
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
                    onNavigateToCompose = { uris ->
                        PendingMediaHolder.set(uris)
                        navController.navigate(Routes.PersonalCompose)
                    },
                )
            }
            composable(Routes.PersonalCompose) {
                MediaComposeScreen(
                    initialUris = PendingMediaHolder.consume(),
                    space = MediaSpace.PERSONAL,
                    onBack = { navController.popBackStack() },
                    onSaveComplete = {
                        navController.popBackStack(Routes.Personal, inclusive = false)
                    },
                )
            }
            composable(Routes.Search) {
                SearchScreen(onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) })
            }
            composable(Routes.Settings) {
                val settingsBackupViewModel: SettingsBackupViewModel = hiltViewModel()
                val proBillingViewModel: ProBillingViewModel = hiltViewModel()
                val backupState by settingsBackupViewModel.uiState.collectAsStateWithLifecycle()
                val billingState by proBillingViewModel.billingState.collectAsStateWithLifecycle()
                val entitlement by proBillingViewModel.entitlement.collectAsStateWithLifecycle()
                SettingsScreen(
                    authState = authState,
                    backupState = backupState,
                    settingsState = settingsState,
                    billingState = billingState,
                    entitlement = entitlement,
                    strings = strings,
                    onSetPin = authViewModel::setPin,
                    onClearPin = authViewModel::clearPin,
                    onBiometricEnabledChange = authViewModel::setBiometricEnabled,
                    onPersonalLockEnabledChange = authViewModel::setPersonalLockEnabled,
                    onConsumeMessages = authViewModel::consumeMessages,
                    onExportBackup = settingsBackupViewModel::exportBackup,
                    onRestoreBackup = settingsBackupViewModel::restoreBackup,
                    onCreateDriveSignInIntent = settingsBackupViewModel::createDriveSignInIntent,
                    onDriveSignInResult = settingsBackupViewModel::onDriveSignInResult,
                    onCloudBackup = settingsBackupViewModel::uploadCloudBackup,
                    onCloudRestore = settingsBackupViewModel::restoreLatestCloudBackup,
                    onDisconnectDrive = settingsBackupViewModel::disconnectDrive,
                    onResetAllData = settingsBackupViewModel::resetAllData,
                    onConsumeBackupMessages = settingsBackupViewModel::consumeMessages,
                    onOpenHiddenVault = { navController.navigate(Routes.HiddenVault) },
                    onDeleteTag = settingsViewModel::deleteTag,
                    onLanguageSelected = settingsViewModel::setLanguage,
                    onBuyPro = proBillingViewModel::buyPro,
                    onRestoreProPurchase = proBillingViewModel::restorePurchases,
                    onConsumeBillingMessages = proBillingViewModel::consumeMessage,
                )
            }
            composable(Routes.HiddenVault) {
                HiddenVaultScreen(
                    onBack = { navController.popBackStack() },
                    onMediaClick = { mediaId -> navController.navigate(Routes.mediaDetail(mediaId)) },
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
                MediaDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (shareImportUris.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { shareImportUris = emptyList() },
            title = { Text("어디에 저장할까요?") },
            text = {
                Text("공유한 사진/영상 ${shareImportUris.size}개를 Work 또는 Personal 중 어디에 등록할지 선택하세요.")
            },
            confirmButton = {
                TextButton(onClick = { openSharedImport(MediaSpace.WORK) }) {
                    Text("Work")
                }
            },
            dismissButton = {
                TextButton(onClick = { openSharedImport(MediaSpace.PERSONAL) }) {
                    Text("Personal")
                }
            },
        )
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    val poppedToExistingTopLevel = popBackStack(route, inclusive = false)
    if (poppedToExistingTopLevel) return

    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
