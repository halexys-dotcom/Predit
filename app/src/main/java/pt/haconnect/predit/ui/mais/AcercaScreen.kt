package pt.haconnect.predit.ui.mais

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.haconnect.predit.BuildConfig
import pt.haconnect.predit.R

/**
 * Guia de utilização publicado com o site do Predit (GitHub Pages), com os passos da app e os
 * vídeos do tutorial. Âncora `#como-usar` da secção "Como usar o Predit".
 */
private const val URL_COMO_USAR = "https://halexys-dotcom.github.io/Predit/#como-usar"

/**
 * 12e C - ecrã "Acerca": identificação da app (ícone + versão), propósito, nota de privacidade
 * e créditos. Chega-se aqui pelo cartão no fim do ecrã Mais.
 *
 * Logo abaixo do propósito fica o cartão "Como usar o Predit", que abre no browser o guia
 * publicado com o site ([URL_COMO_USAR]): os passos e os vídeos do tutorial vivem lá, não dentro
 * da app — quem vai à Internet é o browser, por isso o cartão funciona também no flavor
 * `playstore`, que não declara a permissão `INTERNET`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcercaScreen(onVoltar: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                // Fase 18d: 16 sp sem bold — todas as barras da app com o mesmo tamanho visual.
                title = {
                    Text(
                        text = "Acerca",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colorResource(id = R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Predit",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Text(
                text = "Predit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Diário de orientação pessoal para vigilantes em regime de turnos.",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "O Predit projeta a sua escala a partir do ciclo de rotação, importa o " +
                    "planeamento mensal da empresa em PDF, permite registar as horas reais de " +
                    "cada dia (incluindo atrasos, extras e trocas), e confere o recibo de " +
                    "vencimento contra os cálculos da CCT.",
                style = MaterialTheme.typography.bodyMedium
            )

            // Guia do site: os passos e os videos vivem la, nao dentro da app. Abrir o browser com
            // um Intent nao precisa da permissao INTERNET (e o browser que vai a Internet), por
            // isso o cartao funciona tambem no flavor playstore.
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("acerca-como-usar")
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(URL_COMO_USAR))
                            )
                        } catch (e: ActivityNotFoundException) {
                            // Telemovel sem browser instalado: avisar em vez de deixar o toque sem
                            // resposta (a app nao fecha).
                            Toast.makeText(
                                context,
                                "Não foi encontrado nenhum browser para abrir o guia.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Como usar o Predit",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Guia e vídeos no site (abre no browser)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Agradecimentos",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Este projeto não seria possível sem o feedback e o apoio dos primeiros " +
                    "utilizadores. Um agradecimento especial a:",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Carlos Oliveira, José Mendes, Ricardo Pinheiro, Liliana Sousa, " +
                    "Jorge Revez, Ana Pereira, Alzira Tavares, Luís Matos, " +
                    "Raquel Diebe, José Correia, Nabituque Naba, Nuno Carvalho, Susana Oliveira.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "E a todos os que participaram de forma anónima.",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Desenvolvido para uso pessoal.\nHAConnect, 2026.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Nota de privacidade: fica como ultimo bloco do ecra, depois dos agradecimentos.
            Text(
                text = "Toda a informação fica guardada apenas no telemóvel. A app não envia " +
                    "dados para nenhum servidor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
