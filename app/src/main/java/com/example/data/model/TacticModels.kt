package com.example.data.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class TacticColor(val label: String, val color: Color) {
    YELLOW("Gelb", Color(0xFFFFEB3B)),
    WHITE("Weiß", Color(0xFFFFFFFF)),
    RED("Rot", Color(0xFFFF5252)),
    BLUE("Blau", Color(0xFF448AFF))
}

enum class StrokeWidthLevel(val label: String, val widthDp: Float) {
    THIN("Dünn", 3f),
    MEDIUM("Mittel", 6f),
    THICK("Dick", 12f)
}

enum class TacticTool(val label: String) {
    PEN("Stift"),
    ERASER("Radierer")
}

data class StrokePoint(val x: Float, val y: Float) {
    fun toOffset() = Offset(x, y)
}

data class TacticStroke(
    val id: String,
    val color: TacticColor,
    val width: StrokeWidthLevel,
    val points: List<StrokePoint>,
    val isEraser: Boolean = false
)
