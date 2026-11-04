package com.veylo.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.screens.AddExpenseScreen
import com.veylo.app.ui.screens.CreateGroupScreen
import com.veylo.app.ui.screens.ExpenseDetailScreen
import com.veylo.app.ui.screens.GroupDetailScreen
import com.veylo.app.ui.screens.JoinScreen
import com.veylo.app.ui.screens.MainScreen
import com.veylo.app.ui.screens.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val JOIN = "join"
    fun create(type: String) = "create/$type"
    fun group(id: String) = "group/$id"
    fun expense(id: String) = "expense/$id"
    fun editExpense(groupId: String? = null, expenseId: String? = null) =
        "edit?groupId=${groupId ?: ""}&expenseId=${expenseId ?: ""}"
}

/** Thin wrapper so screens don't depend on NavController directly. */
class Nav(private val nav: NavHostController) {
    fun back() { nav.popBackStack() }
    fun group(id: String) = nav.navigate(Routes.group(id)) { launchSingleTop = true }
    fun expense(id: String) = nav.navigate(Routes.expense(id))
    fun addExpense(groupId: String? = null) = nav.navigate(Routes.editExpense(groupId))
    fun editExpense(groupId: String, expenseId: String) = nav.navigate(Routes.editExpense(groupId, expenseId))
    fun create(type: String) = nav.navigate(Routes.create(type))
    fun join() = nav.navigate(Routes.JOIN)
    fun groupReplacing(id: String) = nav.navigate(Routes.group(id)) {
        popUpTo(Routes.MAIN)
        launchSingleTop = true
    }
    fun toMain() = nav.navigate(Routes.MAIN) { popUpTo(0) }
}

@Composable
fun VeyloNavHost(vm: AppViewModel) {
    val controller = rememberNavController()
    val nav = Nav(controller)
    val profile by vm.profile.collectAsStateWithLifecycle()
    val start = androidx.compose.runtime.remember { if (profile.onboarded) Routes.MAIN else Routes.ONBOARDING }
    val slide = tween<androidx.compose.ui.unit.IntOffset>(320)

    NavHost(
        navController = controller,
        startDestination = start,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slide) + fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slide) + fadeOut(tween(200)) },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onStart = { name ->
                vm.finishOnboarding(name)
                nav.toMain()
            })
        }
        composable(Routes.MAIN) { MainScreen(vm, nav) }
        composable(Routes.JOIN) { JoinScreen(vm, nav) }
        composable(
            "create/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            CreateGroupScreen(vm, nav, entry.arguments?.getString("type") ?: "TRIP")
        }
        composable("group/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            GroupDetailScreen(vm, nav, entry.arguments?.getString("id") ?: "")
        }
        composable("expense/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ExpenseDetailScreen(vm, nav, entry.arguments?.getString("id") ?: "")
        }
        composable(
            "edit?groupId={groupId}&expenseId={expenseId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType; defaultValue = "" },
                navArgument("expenseId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            AddExpenseScreen(
                vm, nav,
                initialGroupId = entry.arguments?.getString("groupId")?.ifBlank { null },
                expenseId = entry.arguments?.getString("expenseId")?.ifBlank { null },
            )
        }
    }
}
