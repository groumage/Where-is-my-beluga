package com.whereismybeluga.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The Beluga silhouette, side view, nose to the left. Same drawing as the app
 * logo (assets/source/beluga.svg); shared by the Compose icon and the map markers.
 */
object BelugaShape {
    val BODY_PATHS = listOf(
        // Tail fin
        "M770 470 L840 290 L910 290 L905 560 Z",
        // Fuselage: cargo hump on top, flat belly underneath
        "M150 600 C150 540 200 520 250 510 C260 390 370 330 500 330 C650 330 760 390 870 500 " +
            "L905 560 C880 600 840 620 780 625 L230 640 C180 640 150 625 150 600 Z",
        // Wing
        "M440 600 L620 600 L560 760 L500 760 Z",
    )
    const val SMILE_PATH = "M195 560 Q235 595 285 570"
    const val SMILE_WIDTH = 22f
    const val EYE_X = 330f
    const val EYE_Y = 460f
    const val EYE_RADIUS = 30f

    // Bounds of the drawing, with room for an outline stroke.
    const val LEFT = 120f
    const val TOP = 260f
    const val WIDTH = 820f
    const val HEIGHT = 530f
    const val OUTLINE_WIDTH = 50f
}

/** Flat Beluga icon; the eye and smile are drawn in [faceColor] (usually the background). */
@Composable
fun BelugaIcon(width: Dp, color: Color, faceColor: Color, modifier: Modifier = Modifier) {
    val body = remember { BelugaShape.BODY_PATHS.map { PathParser().parsePathString(it).toPath() } }
    val smile = remember { PathParser().parsePathString(BelugaShape.SMILE_PATH).toPath() }
    Canvas(modifier.size(width, width * (BelugaShape.HEIGHT / BelugaShape.WIDTH))) {
        val factor = size.width / BelugaShape.WIDTH
        scale(factor, pivot = Offset.Zero) {
            translate(-BelugaShape.LEFT, -BelugaShape.TOP) {
                body.forEach { drawPath(it, color) }
                drawCircle(faceColor, BelugaShape.EYE_RADIUS, Offset(BelugaShape.EYE_X, BelugaShape.EYE_Y))
                drawPath(smile, faceColor, style = Stroke(BelugaShape.SMILE_WIDTH, cap = StrokeCap.Round))
            }
        }
    }
}
