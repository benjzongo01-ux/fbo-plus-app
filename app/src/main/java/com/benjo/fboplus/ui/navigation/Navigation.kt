package com.benjo.fboplus.ui.navigation

import com.benjo.fboplus.ui.theme.*

import com.benjo.fboplus.ui.screens.PinEntryScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.benjo.fboplus.ui.screens.VerificationScreen
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.benjo.fboplus.ui.screens.ModifierProfilScreen
import com.benjo.fboplus.ui.screens.SecuriteScreen
import com.benjo.fboplus.ui.screens.HomeScreen
import com.benjo.fboplus.ui.screens.VentesScreen
import com.benjo.fboplus.ui.screens.StatsScreen
import com.benjo.fboplus.ui.screens.ProfilScreen
import com.benjo.fboplus.ui.screens.PathologieScreen
import com.benjo.fboplus.ui.screens.HistoriqueScreen
import com.benjo.fboplus.ui.screens.NouvelleCommandeScreen
import com.benjo.fboplus.ui.screens.ModifierCommandeScreen

import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.benjo.fboplus.viewmodel.ProductViewModel
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel
import androidx.compose.ui.platform.LocalContext
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.ui.screens.ConnexionScreen
import com.benjo.fboplus.ui.screens.InscriptionScreen
import com.benjo.fboplus.ui.screens.CompleteProfileScreen
import com.benjo.fboplus.ui.screens.PinCreationScreen
import com.benjo.fboplus.ui.screens.BiometricSuggestionScreen
import com.benjo.fboplus.ui.screens.PinVerificationScreen
import com.benjo.fboplus.viewmodel.AuthViewModel

@Composable
fun FBONavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val tokenStorage = remember { SecureTokenStorage(context) }
    val startDestination = if (tokenStorage.hasToken()) "catalogue" else "connexion"
    val productViewModel: ProductViewModel = viewModel()
    val ventesViewModel: VentesViewModel = viewModel()
    val profilViewModel: ProfilViewModel = viewModel()
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (tokenStorage.hasToken()) {
                        tokenStorage.saveLastBackgroundTime()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (tokenStorage.hasToken() && tokenStorage.shouldLock()) {
                        navController.navigate("pin_verification") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    tokenStorage.clearLastBackgroundTime()
                }
                else -> {}
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }

    val routesSansBarre = setOf(
        "connexion", "inscription", "verification/{email}", "pin_entry/{token}", "complete_profile",
        "pin_creation/{token}", "biometric_suggestion", "pin_verification",
        "modifier_profil", "securite",
        "nouvelle_commande", "modifier_commande"
    )

    Scaffold(
        containerColor = BlackDeep,
        bottomBar = {
            val currentRoute = navController
                .currentBackStackEntryAsState().value?.destination?.route

            if (currentRoute !in routesSansBarre) {
                NavigationBar(
                    containerColor = BlackCard,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        Triple("catalogue",   Icons.Filled.Home,        "Accueil"),
                        Triple("pathologies", Icons.Filled.Favorite,    "Santé"),
                        Triple("ventes",      Icons.Filled.ShoppingCart, "Ventes"),
                        Triple("stats",       Icons.Filled.BarChart,     "Stats"),
                        Triple("profil",      Icons.Filled.Person,       "Profil"),
                    )
                    items.forEach { (route, icon, label) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    tint = if (selected) GoldPrimary else WhiteFaint,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    label,
                                    fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) GoldPrimary else WhiteFaint
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (route == "catalogue" && currentRoute == "catalogue") {
                                    coroutineScope.launch {
                                        homeListState.animateScrollToItem(0)
                                    }
                                } else {
                                    navController.navigate(route) {
                                        popUpTo("catalogue") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = GoldGlow
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut()
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn()
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut()
            }
        ) {
            composable("connexion") {
                ConnexionScreen(
                    authViewModel = authViewModel,
                    onCodeEnvoye = { email -> navController.navigate("verification/$email") },
                                        onAllerVersInscription = { navController.navigate("inscription") }
                )
            }
            composable("inscription") {
                InscriptionScreen(
                    authViewModel = authViewModel,
                    onCodeEnvoye = { email -> navController.navigate("verification/$email") },
                    onNeedsPinEntry = { token -> navController.navigate("pin_entry/$token") },
                    onAllerVersConnexion = { navController.popBackStack() }
                )
            }
            composable(
                route = "verification/{email}",
                arguments = listOf(navArgument("email") { type = NavType.StringType })
            ) { backStackEntry ->
                val email = backStackEntry.arguments?.getString("email") ?: ""
                VerificationScreen(
                    authViewModel = authViewModel,
                    email = email,
                    onNeedsPinCreation = { token -> navController.navigate("pin_creation/$token") },
                    onNeedsPinEntry = { token -> navController.navigate("pin_entry/$token") }
                )
            }
            composable("complete_profile") {
                CompleteProfileScreen(
                    profilViewModel = profilViewModel,
                    onProfilComplete = {
                        navController.navigate("pin_creation") {
                            popUpTo("complete_profile") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = "pin_creation/{token}",
                arguments = listOf(navArgument("token") { type = NavType.StringType })
            ) { backStackEntry ->
                val token = backStackEntry.arguments?.getString("token") ?: ""
                PinCreationScreen(
                    authViewModel = authViewModel,
                    token = token,
                    onPinCreated = {
                        navController.navigate("biometric_suggestion") {
                            popUpTo("connexion") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = "pin_entry/{token}",
                arguments = listOf(navArgument("token") { type = NavType.StringType })
            ) { backStackEntry ->
                val token = backStackEntry.arguments?.getString("token") ?: ""
                PinEntryScreen(
                    authViewModel = authViewModel,
                    token = token,
                    onSuccess = {
                        navController.navigate("biometric_suggestion") {
                            popUpTo("connexion") { inclusive = true }
                        }
                    }
                )
            }
            composable("biometric_suggestion") {
                BiometricSuggestionScreen(
                    onTermine = {
                        navController.navigate("catalogue") {
                            popUpTo("biometric_suggestion") { inclusive = true }
                        }
                    }
                )
            }
            composable("pin_verification") {
                PinVerificationScreen(
                    onVerifie = {
                        navController.navigate("catalogue") {
                            popUpTo("pin_verification") { inclusive = true }
                        }
                    }
                )
            }
            composable("catalogue") {
                HomeScreen(
                    viewModel = productViewModel,
                    ventesViewModel = ventesViewModel,
                    onNouvelleCommande = { navController.navigate("nouvelle_commande") },
                    listState = homeListState
                )
            }
            composable("pathologies") {
                PathologieScreen()
            }
            composable("ventes") {
                VentesScreen(
                    ventesViewModel = ventesViewModel,
                    onNouvelleCommande = { navController.navigate("nouvelle_commande") },
                    onModifierCommande = { navController.navigate("modifier_commande") },
                    profilViewModel = profilViewModel
                )
            }
            composable(route = "nouvelle_commande") {
                NouvelleCommandeScreen(
                    ventesViewModel = ventesViewModel,
                    profilViewModel = profilViewModel,
                    onCommandeValidee = { navController.popBackStack() },
                    onAnnuler = {
                        ventesViewModel.annulerCommande()
                        navController.popBackStack()
                    }
                )
            }

            composable("modifier_commande") {
                ModifierCommandeScreen(
                    ventesViewModel = ventesViewModel,
                    profilViewModel = profilViewModel,
                    onCommandeMiseAJour = { navController.popBackStack() },
                    onAnnuler = {
                        ventesViewModel.annulerModification()
                        navController.popBackStack()
                    }
                )
            }
            composable("stats") {
                StatsScreen(ventesViewModel = ventesViewModel)
            }
            composable("profil") {
                ProfilScreen(
                    profilViewModel = profilViewModel,
                    ventesViewModel = ventesViewModel,
                    onModifierProfil = { navController.navigate("modifier_profil") },
                    onSecurite = { navController.navigate("securite") },
                    onDeconnexion = {
                        tokenStorage.clearAll()
                        authViewModel.resetState()
                        navController.navigate("connexion") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("modifier_profil") {
                ModifierProfilScreen(
                    profilViewModel = profilViewModel,
                    onRetour = { navController.popBackStack() }
                )
            }
            composable("securite") {
                SecuriteScreen(
                    onRetour = { navController.popBackStack() }
                )
            }
        }
    }
}