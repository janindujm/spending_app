package com.spendplan.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.ui.home.HomeScreen
import com.spendplan.app.ui.plan.PlanScreen
import com.spendplan.app.ui.plan.SummaryScreen
import com.spendplan.app.ui.settings.CategoriesScreen
import com.spendplan.app.ui.settings.RecurringScreen
import com.spendplan.app.ui.settings.SettingsScreen
import com.spendplan.app.ui.transactions.TransactionEditScreen
import com.spendplan.app.ui.transactions.TransactionsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Type-safe navigation routes.
@Serializable object HomeRoute
@Serializable object TransactionsRoute
@Serializable object PlanRoute
@Serializable object SettingsRoute
@Serializable data class EditTransactionRoute(val id: Long = 0, val type: String = TransactionType.EXPENSE.name)
@Serializable data class SummaryRoute(val month: String)
@Serializable object CategoriesRoute
@Serializable object RecurringRoute

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(HomeRoute, HomeRoute::class, "Home", Icons.Filled.Home),
    TopLevel(TransactionsRoute, TransactionsRoute::class, "Transactions", Icons.AutoMirrored.Filled.List),
    TopLevel(PlanRoute, PlanRoute::class, "Plan", Icons.Filled.DateRange),
    TopLevel(SettingsRoute, SettingsRoute::class, "Settings", Icons.Filled.Settings),
)

@Composable
fun SpendPlanAppUi() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val container = appContainer()
    val messenger = remember(snackbar, container) { AppMessenger(snackbar, container) }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    val currentTop = topLevels.firstOrNull { top -> destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }
    val showFab = currentTop?.routeClass == HomeRoute::class || currentTop?.routeClass == TransactionsRoute::class

    CompositionLocalProvider(LocalAppMessenger provides messenger) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = { navController.navigate(EditTransactionRoute()) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                    }
                }
            },
            bottomBar = {
                if (currentTop != null) {
                    NavigationBar {
                        topLevels.forEach { top ->
                            NavigationBarItem(
                                selected = top == currentTop,
                                onClick = { navController.navigateTopLevel(top.route) },
                                icon = { Icon(top.icon, contentDescription = null) },
                                label = { Text(top.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSummary = { navController.navigate(SummaryRoute(BudgetPeriod.key(it))) },
                        onOpenPlan = { navController.navigateTopLevel(PlanRoute) },
                    )
                }
                composable<TransactionsRoute> {
                    TransactionsScreen(onEdit = { navController.navigate(EditTransactionRoute(id = it)) })
                }
                composable<PlanRoute> {
                    PlanScreen(onOpenSummary = { navController.navigate(SummaryRoute(BudgetPeriod.key(it))) })
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenCategories = { navController.navigate(CategoriesRoute) },
                        onOpenRecurring = { navController.navigate(RecurringRoute) },
                    )
                }
                composable<EditTransactionRoute> { entry ->
                    val route = entry.toRoute<EditTransactionRoute>()
                    TransactionEditScreen(
                        id = route.id,
                        initialType = runCatching { TransactionType.valueOf(route.type) }.getOrDefault(TransactionType.EXPENSE),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<SummaryRoute> { entry ->
                    val route = entry.toRoute<SummaryRoute>()
                    SummaryScreen(month = BudgetPeriod.parseKey(route.month), onBack = { navController.popBackStack() })
                }
                composable<CategoriesRoute> { CategoriesScreen(onBack = { navController.popBackStack() }) }
                composable<RecurringRoute> { RecurringScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
