package com.whereismybeluga.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.whereismybeluga.ui.BelugaShape
import kotlin.math.roundToInt

enum class MarkerKind { FLYING, GROUND, NEAREST }

/** Colors of the Beluga silhouettes on the map, as ARGB ints. */
data class MarkerPalette(
    val flying: Int,
    val ground: Int,
    val nearest: Int,
    val outline: Int,
    val badge: Int,
    val badgeText: Int,
)

/** Draws the Beluga silhouette into bitmaps used as map marker images. */
class MarkerIcons(private val density: Float, private val palette: MarkerPalette) {
    private val body: List<Path> = BelugaShape.BODY_PATHS.map(PathParser::createPathFromPathData)
    private val smile: Path = PathParser.createPathFromPathData(BelugaShape.SMILE_PATH)

    /**
     * @param facingRight mirror the drawing so the nose points east.
     * @param count shown in a badge when the marker stands for several aircraft.
     */
    fun render(kind: MarkerKind, facingRight: Boolean, count: Int): Bitmap {
        val planeWidth = (if (kind == MarkerKind.NEAREST) 60 else 46) * density
        val planeHeight = planeWidth * BelugaShape.HEIGHT / BelugaShape.WIDTH
        // Same margin on every side keeps the plane centered on its coordinate.
        val margin = if (count > 1) 8 * density else 0f
        val bitmap = Bitmap.createBitmap(
            (planeWidth + 2 * margin).roundToInt(),
            (planeHeight + 2 * margin).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)

        canvas.save()
        canvas.translate(margin, margin)
        if (facingRight) canvas.scale(-1f, 1f, planeWidth / 2, 0f)
        val factor = planeWidth / BelugaShape.WIDTH
        canvas.scale(factor, factor)
        canvas.translate(-BelugaShape.LEFT, -BelugaShape.TOP)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Outline first, then the fill on top, so overlapping shapes merge cleanly.
        paint.color = palette.outline
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = BelugaShape.OUTLINE_WIDTH
        paint.strokeJoin = Paint.Join.ROUND
        body.forEach { canvas.drawPath(it, paint) }

        paint.style = Paint.Style.FILL
        paint.color = when (kind) {
            MarkerKind.FLYING -> palette.flying
            MarkerKind.GROUND -> palette.ground
            MarkerKind.NEAREST -> palette.nearest
        }
        body.forEach { canvas.drawPath(it, paint) }

        paint.color = palette.outline
        canvas.drawCircle(BelugaShape.EYE_X, BelugaShape.EYE_Y, BelugaShape.EYE_RADIUS, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = BelugaShape.SMILE_WIDTH
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(smile, paint)
        canvas.restore()

        if (count > 1) drawBadge(canvas, bitmap.width.toFloat(), count)
        return bitmap
    }

    private fun drawBadge(canvas: Canvas, bitmapWidth: Float, count: Int) {
        val radius = 10 * density
        val cx = bitmapWidth - radius - density
        val cy = radius + density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.outline
        canvas.drawCircle(cx, cy, radius + 1.5f * density, paint)
        paint.color = palette.badge
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = palette.badgeText
        paint.textSize = 11 * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(count.toString(), cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
    }
}
