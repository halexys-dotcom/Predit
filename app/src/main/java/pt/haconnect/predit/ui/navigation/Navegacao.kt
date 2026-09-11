package pt.haconnect.predit.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

enum class Destino(val rota: String, val titulo: String, val icone: ImageVector) {
    CALENDARIO("calendario", "Escala", Icons.Default.DateRange),
    HORARIO("horario", "Horário", Icons.AutoMirrored.Filled.List),
    TURNOS("turnos", "Turnos", Icons.Default.Refresh),
    MAIS("mais", "Mais", Icons.Default.MoreVert)
}

@Composable
fun PreditApp() {
    val navController = rememberNavController()
    val entradaAtual by navController.currentBackStackEntryAsState()
    val rotaAtual = entradaAtual?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destino.entries.forEach { destino ->
                    NavigationBarItem(
                        selected = rotaAtual == destino.rota,
                        onClick = {
                            navController.navigate(destino.rota) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destino.icone, contentDescription = destino.titulo) },
                        label = {
                            Text(
                                text = destino.titulo,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destino.CALENDARIO.rota,
            modifier = Modifier.padding(padding)
        ) {
            Destino.entries.forEach { destino ->
                composable(destino.rota) { EcraVazio(destino.titulo) }
            }
        }
    }
}

@Composable
private fun EcraVazio(titulo: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(titulo)
    }
}
