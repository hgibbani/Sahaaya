package com.sahaaya.feature.auth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.sahaaya.domain.model.Role
import com.sahaaya.feature.auth.forgot.ForgotPasswordScreen
import com.sahaaya.feature.auth.login.LoginScreen
import com.sahaaya.feature.auth.register.RegisterScreen
import com.sahaaya.feature.auth.role.RoleSelectionScreen

/**
 * The auth feature owns its own routes and exposes one entry point.
 *
 * `:app` calls [authGraph] and never learns the names of the screens inside.
 * A new screen here - phone sign-in, a consent step - is added without touching
 * the app module, which is what keeps features independent as the team grows.
 */
object AuthRoutes {
    const val GRAPH = "auth"
    const val ROLE_SELECTION = "auth/role"
    const val LOGIN = "auth/login"
    const val FORGOT_PASSWORD = "auth/forgot"

    private const val REGISTER_BASE = "auth/register"
    const val REGISTER_ARG_ROLE = "role"
    const val REGISTER = "$REGISTER_BASE/{$REGISTER_ARG_ROLE}"

    fun register(role: Role): String = "$REGISTER_BASE/${role.storageKey}"
}

fun NavGraphBuilder.authGraph(navController: NavController) {
    navigation(startDestination = AuthRoutes.ROLE_SELECTION, route = AuthRoutes.GRAPH) {

        composable(AuthRoutes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onRoleChosen = { role ->
                    navController.navigate(AuthRoutes.register(role))
                },
                onSignInInstead = {
                    navController.navigate(AuthRoutes.LOGIN)
                },
            )
        }

        composable(AuthRoutes.REGISTER) { entry ->
            val role = Role.fromStorageKey(
                entry.arguments?.getString(AuthRoutes.REGISTER_ARG_ROLE),
            ) ?: Role.CAREGIVER
            RegisterScreen(
                role = role,
                onNavigateBack = { navController.popBackStack() },
                onSignInInstead = {
                    navController.navigate(AuthRoutes.LOGIN) {
                        popUpTo(AuthRoutes.ROLE_SELECTION)
                    }
                },
            )
            // No success callback: the root graph observes the session and
            // routes to the right dashboard as soon as the user exists. One
            // place decides where a signed-in user belongs.
        }

        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onNavigateBack = { navController.popBackStack() },
                onForgotPassword = { navController.navigate(AuthRoutes.FORGOT_PASSWORD) },
                onCreateAccount = {
                    navController.navigate(AuthRoutes.ROLE_SELECTION) {
                        popUpTo(AuthRoutes.ROLE_SELECTION) { inclusive = true }
                    }
                },
            )
        }

        composable(AuthRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
