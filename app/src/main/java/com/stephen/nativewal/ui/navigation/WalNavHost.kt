package com.stephen.nativewal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.stephen.nativewal.MainActivity
import com.stephen.nativewal.ui.screens.AutoLoginProgressScreen
import com.stephen.nativewal.ui.screens.ConfigEditorScreen
import com.stephen.nativewal.ui.screens.DebugLogsScreen
import com.stephen.nativewal.ui.screens.DebugScreen
import com.stephen.nativewal.ui.screens.HomeScreen
import com.stephen.nativewal.ui.viewmodel.ConfigEditorViewModel
import com.stephen.nativewal.ui.viewmodel.AutoLoginProgressViewModel
import com.stephen.nativewal.ui.viewmodel.DebugViewModel
import com.stephen.nativewal.ui.viewmodel.HomeViewModel
import com.stephen.nativewal.ui.viewmodel.HomeViewModelFactory

@Composable
fun WalNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(context.applicationContext)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onAddConfig = {
                    navController.navigate(Screen.ConfigEditor.createRoute())
                },
                onEditConfig = { ssid ->
                    navController.navigate(Screen.ConfigEditor.createRoute(ssid))
                },
                onOpenDebug = {
                    navController.navigate(Screen.Debug.route)
                }
            )
        }

        composable(
            route = "config_editor?ssid={ssid}",
            arguments = listOf(
                navArgument("ssid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val ssid = backStackEntry.arguments?.getString("ssid")
            val editorViewModel: ConfigEditorViewModel = viewModel(
                factory = ConfigEditorViewModel.Factory(context.applicationContext, ssid)
            )
            ConfigEditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Debug.route) {
            val debugViewModel: DebugViewModel = viewModel(
                factory = DebugViewModel.Factory(context.applicationContext)
            )
            DebugScreen(
                viewModel = debugViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogs = { navController.navigate(Screen.DebugLogs.route) }
            )
        }

        composable(Screen.DebugLogs.route) {
            val debugViewModel: DebugViewModel = viewModel(
                factory = DebugViewModel.Factory(context.applicationContext)
            )
            DebugLogsScreen(
                viewModel = debugViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AutoLoginProgress.route,
            arguments = listOf(
                navArgument("ssid") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val ssid = backStackEntry.arguments?.getString("ssid").orEmpty()
            val progressViewModel: AutoLoginProgressViewModel = viewModel(
                factory = AutoLoginProgressViewModel.Factory(context.applicationContext, ssid)
            )
            val debugViewModel: DebugViewModel = viewModel(
                factory = DebugViewModel.Factory(context.applicationContext)
            )
            AutoLoginProgressScreen(
                viewModel = progressViewModel,
                debugViewModel = debugViewModel,
                onNavigateBack = { navController.popBackStack() },
                onEnableLocation = { activity?.enableLocationService() }
            )
        }
    }
}
