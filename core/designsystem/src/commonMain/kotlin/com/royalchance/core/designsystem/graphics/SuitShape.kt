package com.royalchance.core.designsystem.graphics

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.PathParser

/** Trazado propio de la pica, en una caja de 100 × 100 (también se usa en el icono de la app). */
internal const val SPADE_PATH =
    "M50,8 C44,20 16,38 16,58 C16,71 26,79 37,79 C42,79 46,77 48.5,73 C47.5,81 43,88 36,92 L64,92 " +
        "C57,88 52.5,81 51.5,73 C54,77 58,79 63,79 C74,79 84,71 84,58 C84,38 56,20 50,8 Z"

private const val HEART_PATH =
    "M50,90 C20,68 8,50 8,33 C8,20 18,10 30,10 C39,10 46,15 50,23 C54,15 61,10 70,10 C82,10 92,20 92,33 " +
        "C92,50 80,68 50,90 Z"

private const val DIAMOND_PATH = "M50,6 Q66,30 86,50 Q66,70 50,94 Q34,70 14,50 Q34,30 50,6 Z"

private const val CLUB_STEM_PATH = "M47,60 C47,78 43,86 36,92 L64,92 C57,86 53,78 53,60 Z"

/**
 * Palos de la baraja dibujados con trazados vectoriales propios: nítidos a cualquier tamaño y
 * reutilizables en avatares, cartas y decoración de mesas.
 */
enum class SuitShape {
    Spades,
    Hearts,
    Diamonds,
    Clubs,
    ;

    /** Trazado del palo escalado al tamaño indicado (manteniendo la proporción y centrado). */
    fun createPath(size: Size): Path {
        val unit = unitPath()
        val scale = minOf(size.width, size.height) / 100f
        val matrix = Matrix().apply {
            translate((size.width - 100f * scale) / 2f, (size.height - 100f * scale) / 2f)
            scale(scale, scale)
        }
        return Path().apply {
            addPath(unit)
            transform(matrix)
        }
    }

    private fun unitPath(): Path = when (this) {
        Spades -> parse(SPADE_PATH)
        Hearts -> parse(HEART_PATH)
        Diamonds -> parse(DIAMOND_PATH)
        Clubs -> listOf(
            oval(Rect(32f, 10f, 68f, 46f)),
            oval(Rect(12f, 38f, 48f, 74f)),
            oval(Rect(52f, 38f, 88f, 74f)),
            oval(Rect(38f, 34f, 62f, 64f)),
            parse(CLUB_STEM_PATH),
        ).reduce { acc, path -> Path.combine(PathOperation.Union, acc, path) }
    }

    private fun parse(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

    private fun oval(rect: Rect): Path = Path().apply { addOval(rect) }
}

/** Palo pintado con un pincel (color sólido o degradado). El trazado se calcula una vez por tamaño. */
@Composable
fun SuitGlyph(
    suit: SuitShape,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.drawWithCache {
            val path = suit.createPath(size)
            onDrawBehind { drawPath(path, brush) }
        },
    )
}
