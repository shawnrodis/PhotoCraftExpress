package com.shawnrodis.photocraftexpress.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Second set of tool glyphs for the v0.2.0 carousel, same light-outline
 * house style as ToolIcons.kt (drawn from scratch, no third-party art).
 */
private val Ink2 = SolidColor(Color.Black)
private const val SW2 = 1.8f

private fun icon2(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

private fun ImageVector.Builder.strokePath2(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = Ink2,
        strokeLineWidth = SW2,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block
    )
}

private fun ImageVector.Builder.fillPath2(block: PathBuilder.() -> Unit) {
    path(
        fill = Ink2,
        stroke = null,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block
    )
}

private fun PathBuilder.dot2(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, false, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, false, true, -2 * r, 0f)
    close()
}

/** Smooth: three soft waves. */
val SmoothIcon: ImageVector = icon2("Smooth") {
    strokePath2 {
        moveTo(3f, 8f)
        quadTo(6f, 5f, 9f, 8f)
        quadTo(12f, 11f, 15f, 8f)
        quadTo(18f, 5f, 21f, 8f)
        moveTo(3f, 13.5f)
        quadTo(6f, 10.5f, 9f, 13.5f)
        quadTo(12f, 16.5f, 15f, 13.5f)
        quadTo(18f, 10.5f, 21f, 13.5f)
        moveTo(3f, 19f)
        quadTo(6f, 16f, 9f, 19f)
        quadTo(12f, 22f, 15f, 19f)
        quadTo(18f, 16f, 21f, 19f)
    }
}

/** Face: an oval face with two eyes and a mouth. */
val FaceIcon: ImageVector = icon2("Face") {
    strokePath2 {
        moveTo(12f, 3f)
        arcToRelative(7f, 9f, 0f, false, true, 0f, 18f)
        arcToRelative(7f, 9f, 0f, false, true, 0f, -18f)
        close()
        moveTo(9f, 15.5f)
        quadTo(12f, 17.5f, 15f, 15.5f)
    }
    fillPath2 {
        dot2(9f, 10f, 1.2f)
        dot2(15f, 10f, 1.2f)
    }
}

/** Reshape: a grid being pushed by an arrow. */
val ReshapeIcon: ImageVector = icon2("Reshape") {
    strokePath2 {
        moveTo(4f, 4f)
        horizontalLineTo(20f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        close()
        moveTo(4f, 12f)
        horizontalLineTo(20f)
        moveTo(12f, 4f)
        verticalLineTo(20f)
        moveTo(14f, 15f)
        quadTo(17f, 12f, 15f, 8f)
        moveTo(15f, 8f)
        lineTo(13.4f, 9.2f)
        moveTo(15f, 8f)
        lineTo(16.6f, 9.8f)
    }
}

/** Makeup: a lipstick. */
val MakeupIcon: ImageVector = icon2("Makeup") {
    strokePath2 {
        moveTo(9.5f, 9f)
        verticalLineTo(4.5f)
        quadTo(12f, 2.5f, 14.5f, 4.5f)
        verticalLineTo(9f)
        moveTo(8f, 9f)
        horizontalLineTo(16f)
        verticalLineTo(19.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
        horizontalLineTo(9.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
        close()
    }
}

/** Draw: a paintbrush. */
val DrawIcon: ImageVector = icon2("Draw") {
    strokePath2 {
        moveTo(15f, 3f)
        quadTo(19f, 5f, 17.5f, 9f)
        lineTo(9f, 17.5f)
        lineTo(5f, 19f)
        lineTo(6.5f, 15f)
        lineTo(15f, 6.5f)
        close()
        moveTo(5f, 19f)
        lineTo(8.5f, 15.5f)
    }
}

/** Background: a person in front of a backdrop panel. */
val BackgroundIcon: ImageVector = icon2("Background") {
    strokePath2 {
        moveTo(3f, 21f)
        verticalLineTo(4f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        moveTo(12f, 8f)
        arcToRelative(2.2f, 2.2f, 0f, false, true, 0f, 4.4f)
        arcToRelative(2.2f, 2.2f, 0f, false, true, 0f, -4.4f)
        moveTo(8f, 20f)
        quadTo(8f, 14.5f, 12f, 14.5f)
        quadTo(16f, 14.5f, 16f, 20f)
    }
}

/** Insert Objects: an image with a plus badge. */
val InsertIcon: ImageVector = icon2("Insert") {
    strokePath2 {
        moveTo(3.5f, 5f)
        horizontalLineTo(20.5f)
        verticalLineTo(17f)
        horizontalLineTo(3.5f)
        close()
        moveTo(6f, 15.5f)
        lineTo(10f, 11.5f)
        lineTo(13f, 14.5f)
        moveTo(15f, 17f)
        horizontalLineTo(21f)
        moveTo(18f, 14f)
        verticalLineTo(20f)
    }
    fillPath2 {
        dot2(8f, 9f, 1.3f)
    }
}

/** Overlays: three stacked layers. */
val OverlaysIcon: ImageVector = icon2("Overlays") {
    strokePath2 {
        moveTo(12f, 3.5f)
        lineTo(20.5f, 8f)
        lineTo(12f, 12.5f)
        lineTo(3.5f, 8f)
        close()
        moveTo(3.5f, 13f)
        lineTo(12f, 17.5f)
        lineTo(20.5f, 13f)
        moveTo(3.5f, 18f)
        lineTo(12f, 22f)
        lineTo(20.5f, 18f)
    }
}

/** Stickers: a star. */
val StickersIcon: ImageVector = icon2("Stickers") {
    strokePath2 {
        moveTo(12f, 3f)
        lineTo(14.8f, 9.2f)
        lineTo(21.5f, 9.8f)
        lineTo(16.6f, 14.3f)
        lineTo(18f, 21f)
        lineTo(12f, 17.6f)
        lineTo(6f, 21f)
        lineTo(7.4f, 14.3f)
        lineTo(2.5f, 9.8f)
        lineTo(9.2f, 9.2f)
        close()
    }
}

/** Eyes: an eye outline with an iris. */
val EyesIcon: ImageVector = icon2("Eyes") {
    strokePath2 {
        moveTo(2.5f, 12f)
        quadTo(7f, 5.5f, 12f, 5.5f)
        quadTo(17f, 5.5f, 21.5f, 12f)
        quadTo(17f, 18.5f, 12f, 18.5f)
        quadTo(7f, 18.5f, 2.5f, 12f)
        close()
        moveTo(12f, 8.5f)
        arcToRelative(3.5f, 3.5f, 0f, false, true, 0f, 7f)
        arcToRelative(3.5f, 3.5f, 0f, false, true, 0f, -7f)
    }
}
