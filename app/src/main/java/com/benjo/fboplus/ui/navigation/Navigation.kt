package com.benjo.fboplus.ui.navigation

import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.ui.screens.BoutiqueScreen
import com.benjo.fboplus.ui.screens.ClientDetailScreen
import com.benjo.fboplus.ui.screens.ClientFormulaireScreen
import com.benjo.fboplus.ui.screens.ClientsScreen
import com.benjo.fboplus.ui.screens.CommandeDetailScreen
import com.benjo.fboplus.ui.screens.MesCommandesScreen
import com.benjo.fboplus.ui.screens.NotificationsScreen

import com.benjo.fboplus.ui.screens.PinEntryScreen
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
    val startDestination = remember {
        when {
            !authViewModel.isLoggedIn() -> "connexion"
            tokenStorage.shouldLock() -> "pin_verification"
            else -> "catalogue"
        }
    }
    val productViewModel: ProductViewModel = viewModel()
    val ventesViewModel: VentesViewModel = viewModel()
    val profilViewModel: ProfilViewModel = viewModel()
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Consomme l'état avant de naviguer : sinon le bouton retour relancerait la même navigation.
    fun allerVers(route: String) {
        authViewModel.resetState()
        navController.navigate(route)
    }
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (tokenStorage.sessionOuverte()) {
                        tokenStorage.saveLastBackgroundTime()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // À l'ouverture de l'application, la navigation n'existe pas encore :
                    // ce cas est traité par l'écran de départ.
                    val navigationPrete = navController.currentBackStackEntry != null
                    if (navigationPrete && tokenStorage.sessionOuverte() && tokenStorage.shouldLock()) {
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
        "connexion", "pin_entry", "complete_profile",
        "pin_creation", "biometric_suggestion", "pin_verification",
        "modifier_profil", "securite",
        "nouvelle_commande", "modifier_commande",
        "commandes", "commande/{id}", "clients", "client/{id}", "client_formulaire?id={id}", "boutique", "notifications"
    )

    // Android 13 et plus : sans cet accord, aucune notification ne s'affiche.
    val demandeNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val propositionImport by VentesRepository.propositionImport.collectAsState()
    val routeActive = navController.currentBackStackEntryAsState().value?.destination?.route
    // Jamais avant le déverrouillage : seul le titulaire de la session peut répondre.
    propositionImport?.takeIf { routeActive != null && routeActive !in routesSansBarre }?.let { nombre ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = BlackCard,
            title = { Text("Reprendre les données de ce téléphone ?", color = WhitePrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Ce téléphone contient un profil et $nombre vente(s) enregistrés auparavant. " +
                        "Acceptez uniquement s'ils sont à vous : ils seront rattachés à ce compte.",
                    color = WhiteSub
                )
            },
            confirmButton = {
                TextButton(onClick = { VentesRepository.repondreImport(context, true) }) {
                    Text("Reprendre", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { VentesRepository.repondreImport(context, false) }) {
                    Text("Ce n'est pas à moi", color = WhiteSub)
                }
            }
        )
    }

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
                    onNeedsPinCreation = { allerVers("pin_creation") },
                    onNeedsPinEntry = { allerVers("pin_entry") }
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
            composable("pin_creation") {
                PinCreationScreen(
                    authViewModel = authViewModel,
                    onPinCreated = {
                        navController.navigate("biometric_suggestion") {
                            popUpTo("connexion") { inclusive = true }
                        }
                    }
                )
            }
            composable("pin_entry") {
                PinEntryScreen(
                    authViewModel = authViewModel,
                    onSuccess = {
                        navController.navigate("biometric_suggestion") {
                            popUpTo("connexion") { inclusive = true }
                        }
                    },
                    onOublie = {
                        if (authViewModel.connexionRecente()) {
                            // La connexion est toute récente : choisir directement un nouveau PIN.
                            authViewModel.resetState()
                            navController.navigate("pin_creation") {
                                popUpTo("pin_entry") { inclusive = true }
                            }
                        } else {
                            authViewModel.deconnecter()
                            navController.navigate("connexion") {
                                popUpTo(0) { inclusive = true }
                            }
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
                    },
                    onOublie = {
                        authViewModel.deconnecter()
                        navController.navigate("connexion") {
                            popUpTo(0) { inclusive = true }
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
                    onOuvrirCommande = { navController.navigate("commande/$it") },
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
                    onCommandes = { navController.navigate("commandes") },
                    onClients = { navController.navigate("clients") },
                    onBoutique = { navController.navigate("boutique") },
                    onNotifications = { navController.navigate("notifications") },
                    onDeconnexion = {
                        authViewModel.deconnecter()
                        navController.navigate("connexion") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("commandes") {
                MesCommandesScreen(
                    onOuvrir = { navController.navigate("commande/$it") },
                    onRetour = { navController.popBackStack() }
                )
            }
            composable(
                route = "commande/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entree ->
                CommandeDetailScreen(
                    docId = entree.arguments?.getString("id").orEmpty(),
                    ventesViewModel = ventesViewModel,
                    onModifier = { commande ->
                        ventesViewModel.chargerCommandePourModification(commande)
                        navController.navigate("modifier_commande")
                    },
                    onRetour = { navController.popBackStack() }
                )
            }
            composable("clients") {
                ClientsScreen(
                    onOuvrir = { navController.navigate("client/$it") },
                    onNouveau = { navController.navigate("client_formulaire") },
                    onRetour = { navController.popBackStack() }
                )
            }
            composable(
                route = "client/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entree ->
                val id = entree.arguments?.getString("id").orEmpty()
                ClientDetailScreen(
                    clientId = id,
                    onModifier = { navController.navigate("client_formulaire?id=$id") },
                    onCommande = { navController.navigate("commande/$it") },
                    onRetour = { navController.popBackStack() }
                )
            }
            composable(
                route = "client_formulaire?id={id}",
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { entree ->
                ClientFormulaireScreen(
                    clientId = entree.arguments?.getString("id"),
                    onTermine = { navController.popBackStack("clients", inclusive = false) },
                    onRetour = { navController.popBackStack() }
                )
            }
            composable("notifications") {
                NotificationsScreen(
                    onOuvrirCommande = { navController.navigate("commande/$it") },
                    onRetour = { navController.popBackStack() }
                )
            }
            composable("boutique") {
                BoutiqueScreen(
                    profilViewModel = profilViewModel,
                    onRetour = { navController.popBackStack() }
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
                    authViewModel = authViewModel,
                    onRetour = { navController.popBackStack() }
                )
            }
        }
    }
}