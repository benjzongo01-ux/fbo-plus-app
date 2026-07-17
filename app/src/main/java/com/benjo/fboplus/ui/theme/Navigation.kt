package com.benjo.fboplus.ui.theme

import kotlinx.coroutines.launch
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

@Composable
fun FBONavigation() {
    val navController = rememberNavController()
    val productViewModel: ProductViewModel = viewModel()
    val ventesViewModel: VentesViewModel = viewModel()
    val profilViewModel: ProfilViewModel = viewModel()
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = BlackDeep,
        bottomBar = {
            val currentRoute = navController
                .currentBackStackEntryAsState().value?.destination?.route

            if (currentRoute != "nouvelle_commande" && currentRoute != "modifier_commande") {
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
            startDestination = "catalogue",
            modifier = Modifier.padding(paddingValues)
        ) {
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
                    profilViewModel = profilViewModel,   // ← ligne ajoutée
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
                    ventesViewModel = ventesViewModel
                )
            }
        }
    }
}