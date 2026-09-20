package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.haconnect.predit.domain.model.TipoTurno

/** 12d: formato do chip. CIRCULAR mantem o aspeto original; RETANGULAR e usado nas listas. */
enum class FormatoCelula { CIRCULAR, RETANGULAR }

@Composable
fun CelulaTipoTurno(
    tipo: TipoTurno?,
    tamanho: Dp,
    modifier: Modifier = Modifier,
    formato: FormatoCelula = FormatoCelula.CIRCULAR
) {
    val corFundo = tipo?.let { Color(it.cor) } ?: MaterialTheme.colorScheme.surfaceVariant
    val corTexto = tipo?.let { calcularCorTexto(it.cor) } ?: MaterialTheme.colorScheme.onSurface
    val abrev = tipo?.abreviatura ?: "?"

    val ehPequeno = tamanho < 30.dp

    val (fontSize, letterSpacing, forma) = when {
        abrev.length == 1 -> Triple(if (ehPequeno) 9.sp else 13.sp, 0.sp, CircleShape)
        abrev.length in 2..3 -> Triple(if (ehPequeno) 8.sp else 10.5.sp, (-0.2).sp, CircleShape)
        abrev.length == 4 -> Triple(if (ehPequeno) 6.5.sp else 8.5.sp, (-0.5).sp, CircleShape)
        else -> Triple(if (ehPequeno) 6.5.sp else 8.sp, (-0.5).sp, RoundedCornerShape(6.dp))
    }

    // 12d: RETANGULAR = pilula larga (min 1.5x a altura) com cantos de 4dp; a fonte mantem-se.
    val modifierFinal = if (formato == FormatoCelula.RETANGULAR) {
        modifier
            .height(tamanho)
            .widthIn(min = tamanho * 1.5f)
            .clip(RoundedCornerShape(4.dp))
            .background(corFundo)
    } else {
        modifier
            .clip(forma)
            .background(corFundo)
    }

    Box(
        modifier = modifierFinal,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = abrev,
            style = TextStyle(
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1,
            softWrap = false,
            color = corTexto
        )
    }
}

fun calcularCorTexto(corFundoHex: Long): Color {
    val r = ((corFundoHex shr 16) and 0xFF) / 255.0
    val g = ((corFundoHex shr 8) and 0xFF) / 255.0
    val b = (corFundoHex and 0xFF) / 255.0
    val luminancia = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luminancia > 0.55) Color.Black else Color.White
}
