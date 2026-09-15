package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    modifier: Modifier = Modifier
) {
    val corTexto = if (corTipo.luminance() > 0.55f) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(corTipo.copy(alpha = 0.85f))
            .border(1.5.dp, corTipo, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            fontSize = when {
                texto.length <= 2 -> 9.sp
                texto.length <= 4 -> 8.sp
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
