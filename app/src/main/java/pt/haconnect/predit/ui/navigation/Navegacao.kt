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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pt.haconnect.predit.ui.escala.AusenciasScreen
import pt.haconnect.predit.ui.escala.EditorAusenciaScreen
import pt.haconnect.predit.ui.escala.EscalaScreen
import pt.haconnect.predit.ui.turnos.AplicarRotacaoScreen
import pt.haconnect.predit.ui.turnos.EditorRotacaoScreen
import pt.haconnect.predit.ui.turnos.EditorTipoTurnoScreen
import pt.haconnect.predit.ui.turnos.TurnosScreen

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

    val exibeBarraInferior = Destino.entries.any { it.rota == rotaAtual }

    Scaffold(
        bottomBar = {
            if (exibeBarraInferior) {
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destino.CALENDARIO.rota,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destino.CALENDARIO.rota) {
                EscalaScreen(
                    onNavegarParaTurnos = {
                        navController.navigate(Destino.TURNOS.rota) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavegarParaMarcarAusencia = { epochDay ->
                        navController.navigate("escala/ausencia/nova?dataInicio=$epochDay")
                    }
                )
            }
            composable(Destino.HORARIO.rota) { EcraVazio(Destino.HORARIO.titulo) }
            composable(Destino.TURNOS.rota) {
                TurnosScreen(
                    onNavegarParaEditorTipoTurno = { id ->
                        if (id == null || id == 0L) {
                            navController.navigate("turnos/tipo/novo")
                        } else {
                            navController.navigate("turnos/tipo/$id")
                        }
                    },
                    onNavegarParaEditorRotacao = { id ->
                        if (id == null || id == 0L) {
                            navController.navigate("turnos/rotacao/nova")
                        } else {
                            navController.navigate("turnos/rotacao/$id")
                        }
                    },
                    onNavegarParaAplicarRotacao = { id ->
                        navController.navigate("turnos/rotacao/$id/aplicar")
                    }
                )
            }
            composable(Destino.MAIS.rota) {
                AusenciasScreen(
                    onNavegarParaCriarAusencia = {
                        navController.navigate("escala/ausencia/nova")
                    },
                    onNavegarParaEditarAusencia = { id ->
                        navController.navigate("escala/ausencia/$id")
                    }
                )
            }

            // Rotas de ecrã completo para Ausências
            composable(
                route = "escala/ausencia/nova?dataInicio={dataInicio}",
                arguments = listOf(
                    navArgument("dataInicio") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val inicio = backStackEntry.arguments?.getLong("dataInicio")?.takeIf { it != -1L }
                EditorAusenciaScreen(
                    ausenciaId = 0L,
                    dataInicioInicialEpochDay = inicio,
                    onVoltar = { navController.popBackStack() }
                )
            }

            composable(
                route = "escala/ausencia/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                EditorAusenciaScreen(
                    ausenciaId = id,
                    onVoltar = { navController.popBackStack() }
                )
            }

            // Rotas de ecrã completo para Tipos de Turno
            composable("turnos/tipo/novo") {
                EditorTipoTurnoScreen(
                    tipoId = 0L,
                    onVoltar = { navController.popBackStack() }
                )
            }
            composable(
                route = "turnos/tipo/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                EditorTipoTurnoScreen(
                    tipoId = id,
                    onVoltar = { navController.popBackStack() }
                )
            }

            // Rotas de ecrã completo para Rotações
            composable("turnos/rotacao/nova") {
                EditorRotacaoScreen(
                    rotacaoId = 0L,
                    onVoltar = { navController.popBackStack() }
                )
            }
            composable(
                route = "turnos/rotacao/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                EditorRotacaoScreen(
                    rotacaoId = id,
                    onVoltar = { navController.popBackStack() }
                )
            }
            composable(
                route = "turnos/rotacao/{id}/aplicar",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                AplicarRotacaoScreen(
                    rotacaoId = id,
                    onVoltar = { navController.popBackStack() }
                )
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
