package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CelulaPosto(
    texto: String,
    corTipo: Color,
    tamanho: Dp,
    modifier: Modifier = Modifier,
    formato: FormatoCelula = FormatoCelula.CIRCULAR
) {
    val corTexto = if (corTipo.luminance() > 0.55f) Color.Black else Color.White
    // 28k: no Horario os postos ("cc", "P1", "Staff") passam a pilula retangular, alinhada com
    // os crachas do turno. O default mantem o circulo do calendario da Escala.
    val shape = if (formato == FormatoCelula.RETANGULAR) RoundedCornerShape(4.dp) else CircleShape

    val modifierFinal = if (formato == FormatoCelula.RETANGULAR) {
        modifier
            .height(tamanho)
            .widthIn(min = tamanho * 1.5f) // forca aspeto de pilula
            .clip(shape)
            .background(corTipo) // cor solida, sem alpha, igual ao CelulaTipoTurno
    } else {
        modifier
            .size(tamanho)
            .clip(shape)
            .background(corTipo.copy(alpha = 0.85f))
            .border(1.5.dp, corTipo, shape)
    }

    Box(
        modifier = modifierFinal,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            fontSize = when {
                texto.length == 1 -> 9.sp
                texto.length in 2..3 -> 8.sp
                texto.length == 4 -> 6.5.sp
                else -> 6.5.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = corTexto,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                letterSpacing = (-0.3).sp
            )
        )
    }
}
