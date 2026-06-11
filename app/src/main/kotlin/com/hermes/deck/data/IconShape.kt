package com.hermes.deck.data

import android.graphics.Canvas
import android.graphics.Path

enum class IconShape(val label: String) {
    NONE("Original"),
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    ROUNDED_SQUARE("Rounded square"),
    SQUARE("Square");

    fun clipCanvas(canvas: Canvas, size: Float) {
        if (this == NONE || this == SQUARE) return
        val path = Path()
        when (this) {
            CIRCLE -> path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            SQUIRCLE -> path.addRoundRect(0f, 0f, size, size, size * 0.35f, size * 0.35f, Path.Direction.CW)
            ROUNDED_SQUARE -> path.addRoundRect(0f, 0f, size, size, size * 0.2f, size * 0.2f, Path.Direction.CW)
            else -> return
        }
        canvas.clipPath(path)
    }
}
