package com.signalpro.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.signalpro.app.AppContainer
import com.signalpro.app.ui.screens.CampaignScreen
import com.signalpro.app.ui.screens.CommunityScreen
import com.signalpro.app.ui.screens.DashboardScreen
import com.signalpro.app.ui.screens.DetectScreen
import com.signalpro.app.ui.screens.LinkDeviceScreen
import com.signalpro.app.ui.screens.LoginScreen
import com.signalpro.app.ui.screens.ModerationScreen
import com.signalpro.app.ui.screens.RegisterScreen
import com.signalpro.app.ui.screens.ReportDetailScreen
import com.signalpro.app.ui.screens.ReportListScreen
import com.signalpro.app.ui.screens.ReportNewScreen
import com.signalpro.app.ui.screens.SettingsScreen
import com.signalpro.app.ui.screens.VerifyScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val VERIFY = "verify"
    const val LINK = "link"
    const val HOME = "home"
    const val REPORTS = "reports"
    const val REPORT_NEW = "reports/new"
    const val REPORT_DETAIL = "reports/{id}"
    const val COMMUNITY = "community"
    const val DETECT = "detect"
    const val CAMPAIGN = "campaign"
    const val SETTINGS = "settings"
    const val MODERATION = "moderation"

    fun reportDetail(id: Int) = "reports/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNavHost(
    container: AppContainer,
    startLoggedIn: Boolean,
    isModerator: Boolean,
    displayName: String,
    cameraGranted: Boolean,
    requestCamera: () -> Unit,
    sharedText: String?,
    onConsumeSharedText: () -> Unit,
) {
    val navController = rememberNavController()
    var loggedIn by remember { mutableStateOf(startLoggedIn) }

    val start = if (startLoggedIn) Routes.HOME else Routes.LOGIN

    Scaffold(
        bottomBar = {
            if (loggedIn) {
                AppBottomBar(navController, isModerator)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = start) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        container = container,
                        onLoggedIn = {
                            loggedIn = true
                            navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        },
                        onGoRegister = { navController.navigate(Routes.REGISTER) },
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        container = container,
                        onRegistered = { email ->
                            navController.navigate("${Routes.VERIFY}?email=$email")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "${Routes.VERIFY}?email={email}",
                    arguments = listOf(androidx.navigation.navArgument("email") { defaultValue = "" }),
                ) { entry ->
                    VerifyScreen(
                        container = container,
                        email = entry.arguments?.getString("email").orEmpty(),
                        onVerified = {
                            loggedIn = true
                            navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.HOME) {
                    DashboardScreen(
                        container = container,
                        displayName = displayName,
                        onOpenLink = { navController.navigate(Routes.LINK) },
                        onOpenReport = { navController.navigate(Routes.REPORT_NEW) },
                        onOpenCampaign = { navController.navigate(Routes.CAMPAIGN) },
                        onOpenReports = { navController.navigate(Routes.REPORTS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.LINK) {
                    LinkDeviceScreen(
                        container = container,
                        cameraGranted = cameraGranted,
                        requestCamera = requestCamera,
                        onLinked = { navController.popBackStack() },
                    )
                }
                composable(Routes.REPORTS) {
                    ReportListScreen(
                        container = container,
                        onOpen = { navController.navigate(Routes.reportDetail(it)) },
                        onNew = { navController.navigate(Routes.REPORT_NEW) },
                    )
                }
                composable(Routes.REPORT_NEW) {
                    ReportNewScreen(
                        container = container,
                        sharedText = sharedText,
                        onConsumeSharedText = onConsumeSharedText,
                        onCreated = { id ->
                            navController.navigate(Routes.reportDetail(id)) { popUpTo(Routes.REPORTS) }
                        },
                    )
                }
                composable(
                    route = Routes.REPORT_DETAIL,
                    arguments = listOf(androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.IntType }),
                ) { entry ->
                    ReportDetailScreen(
                        container = container,
                        reportId = entry.arguments?.getInt("id") ?: 0,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.COMMUNITY) { CommunityScreen(container = container) }
                composable(Routes.DETECT) { DetectScreen(container = container) }
                composable(Routes.CAMPAIGN) { CampaignScreen(container = container) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        onLoggedOut = {
                            loggedIn = false
                            navController.navigate(Routes.LOGIN) { popUpTo(0) }
                        },
                    )
                }
                composable(Routes.MODERATION) { ModerationScreen(container = container) }
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController, isModerator: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route

    val tabs = buildList {
        add(Tab(Routes.HOME, "Accueil", Icons.Filled.Home))
        add(Tab(Routes.REPORTS, "Signalements", Icons.Filled.Shield))
        add(Tab(Routes.DETECT, "Détection", Icons.Filled.Search))
        add(Tab(Routes.COMMUNITY, "Communauté", Icons.Filled.Groups))
        if (isModerator) add(Tab(Routes.MODERATION, "Modération", Icons.Filled.AccountCircle))
        else add(Tab(Routes.SETTINGS, "Réglages", Icons.Filled.AccountCircle))
    }

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
