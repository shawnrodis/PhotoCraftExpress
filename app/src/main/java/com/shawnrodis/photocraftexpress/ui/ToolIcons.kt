package com.shawnrodis.photocraftexpress.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The editor's tool glyphs, drawn from scratch as vector paths in a light
 * outline style (no third-party icon artwork). Everything is drawn in black;
 * the Icon composable's tint decides the rendered colour.
 */
private val Ink = SolidColor(Color.Black)
private const val SW = 1.8f

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = Ink,
        strokeLineWidth = SW,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block
    )
}

private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) {
    path(
        fill = Ink,
        stroke = null,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block
    )
}

/** A four-point sparkle centred at (cx, cy) with radius r. */
private fun PathBuilder.sparkle(cx: Float, cy: Float, r: Float) {
    val k = r * 0.16f
    moveTo(cx, cy - r)
    quadTo(cx + k, cy - k, cx + r, cy)
    quadTo(cx + k, cy + k, cx, cy + r)
    quadTo(cx - k, cy + k, cx - r, cy)
    quadTo(cx - k, cy - k, cx, cy - r)
    close()
}

/** A filled circle via two semicircle arcs. */
private fun PathBuilder.dot(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, false, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, false, true, -2 * r, 0f)
    close()
}

/** Looks: a wand with sparkles. */
val LooksIcon: ImageVector = icon("Looks") {
    strokePath {
        moveTo(4.5f, 19.5f)
        lineTo(14.8f, 9.2f)
    }
    fillPath {
        sparkle(17.6f, 5.2f, 2.6f)
        sparkle(20.6f, 11.8f, 1.8f)
        sparkle(12.4f, 3.4f, 1.5f)
    }
}

/** Crop: two overlapping corner frames. */
val CropIcon: ImageVector = icon("Crop") {
    strokePath {
        moveTo(6f, 2.5f)
        verticalLineTo(15f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        horizontalLineTo(21.5f)
        moveTo(18f, 21.5f)
        verticalLineTo(9f)
        arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
        horizontalLineTo(2.5f)
    }
}

/** Adjust: three slider tracks with knobs. */
val AdjustIcon: ImageVector = icon("Adjust") {
    strokePath {
        moveTo(3.5f, 6.5f); horizontalLineTo(20.5f)
        moveTo(3.5f, 12f); horizontalLineTo(20.5f)
        moveTo(3.5f, 17.5f); horizontalLineTo(20.5f)
    }
    fillPath {
        dot(9f, 6.5f, 2.3f)
        dot(15.5f, 12f, 2.3f)
        dot(7f, 17.5f, 2.3f)
    }
}

/** Healing: a bandage, drawn rotated in a group. */
val HealingIcon: ImageVector = icon("Healing") {
    group(rotate = -40f, pivotX = 12f, pivotY = 12f) {
        strokePath {
            moveTo(6f, 9.3f)
            lineTo(18f, 9.3f)
            arcToRelative(2.7f, 2.7f, 0f, false, true, 0f, 5.4f)
            lineTo(6f, 14.7f)
            arcToRelative(2.7f, 2.7f, 0f, false, true, 0f, -5.4f)
            close()
        }
        fillPath {
            dot(9.6f, 12f, 0.95f)
            dot(12f, 12f, 0.95f)
            dot(14.4f, 12f, 0.95f)
        }
    }
}

/** Effects: sparkles. */
val EffectsIcon: ImageVector = icon("Effects") {
    fillPath {
        sparkle(9.5f, 12.5f, 6.2f)
        sparkle(18.6f, 5.4f, 2.7f)
        sparkle(19.2f, 16.4f, 1.9f)
    }
}

/** Text: a serif T. */
val TextIcon: ImageVector = icon("Text") {
    strokePath {
        moveTo(4.5f, 5f)
        horizontalLineTo(19.5f)
        moveTo(12f, 5f)
        verticalLineTo(19f)
        moveTo(8.8f, 19f)
        horizontalLineTo(15.2f)
    }
}

/** Borders: a double frame. */
val BordersIcon: ImageVector = icon("Borders") {
    strokePath {
        moveTo(4f, 4f)
        horizontalLineTo(20f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        close()
    }
    path(
        fill = null,
        stroke = Ink,
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(8f, 8f)
        horizontalLineTo(16f)
        verticalLineTo(16f)
        horizontalLineTo(8f)
        close()
    }
}

/** Resize: outward corner brackets around a small square. */
val ResizeIcon: ImageVector = icon("Resize") {
    strokePath {
        moveTo(9.3f, 3.5f); horizontalLineTo(3.5f); verticalLineTo(9.3f)
        moveTo(14.7f, 3.5f); horizontalLineTo(20.5f); verticalLineTo(9.3f)
        moveTo(20.5f, 14.7f); verticalLineTo(20.5f); horizontalLineTo(14.7f)
        moveTo(9.3f, 20.5f); horizontalLineTo(3.5f); verticalLineTo(14.7f)
        moveTo(9.2f, 9.2f)
        horizontalLineTo(14.8f)
        verticalLineTo(14.8f)
        horizontalLineTo(9.2f)
        close()
    }
}
