package com.royalchance.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Convierte en enlaces pulsables los fragmentos [links] que aparecen dentro de [text].
 * Así la frase completa se traduce como una sola cadena y los enlaces se localizan por su texto.
 */
@Composable
fun linkedText(text: String, vararg links: Pair<String, () -> Unit>): AnnotatedString {
    val style = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
    )
    return buildAnnotatedString {
        append(text)
        links.forEach { (label, onClick) ->
            val start = text.indexOf(label)
            if (start >= 0) {
                addLink(LinkAnnotation.Clickable(tag = label, styles = style) { onClick() }, start, start + label.length)
            }
        }
    }
}
