package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.VolleyViewModel
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaktikScreen(
    viewModel: VolleyViewModel,
    modifier: Modifier = Modifier
) {
    val strokes by viewModel.tacticStrokes.collectAsState()
    val redoStack by viewModel.tacticRedoStack.collectAsState()
    val isBeachCourt by viewModel.isBeachCourt.collectAsState()

    var activeColor by remember { mutableStateOf(TacticColor.YELLOW) }
    var activeWidth by remember { mutableStateOf(StrokeWidthLevel.MEDIUM) }
    var activeTool by remember { mutableStateOf(TacticTool.PEN) }

    // Live points for current stroke being actively drawn with Pen
    var currentDrawingPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolleyBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Clean compact top header row without thick TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isBeachCourt) "Taktik: Beachfeld (8x16m)" else "Taktik: Hallenfeld (9x18m + 3m)",
                color = VolleyYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            Button(
                onClick = { viewModel.toggleCourtType() },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_toggle_court"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VolleyDarkSurfaceVariant,
                    contentColor = VolleyYellow
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = VolleyYellow)
                Spacer(Modifier.width(6.dp))
                Text(if (isBeachCourt) "Zu Halle" else "Zu Beach", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        }

        // Adaptive Canvas + Controls layout (Side-by-side in landscape so canvas & all tools remain 100% visible)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isSideBySide = maxWidth > maxHeight * 1.15f && maxWidth >= 480.dp
            val portraitControlsMaxH = (maxHeight * 0.42f).coerceAtLeast(130.dp)

            val canvasPane: @Composable (Modifier) -> Unit = { paneMod ->
                Box(
                    modifier = paneMod
                        .background(Color(0xFF0F1115), RoundedCornerShape(8.dp))
                        .border(2.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
                ) {
            val eraserRadiusPx = activeWidth.widthDp * 4f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeTool, activeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (activeTool == TacticTool.ERASER) {
                                    viewModel.eraseStrokesNear(offset.x, offset.y, eraserRadiusPx)
                                } else {
                                    currentDrawingPoints = listOf(StrokePoint(offset.x, offset.y))
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (activeTool == TacticTool.ERASER) {
                                    viewModel.eraseStrokesNear(change.position.x, change.position.y, eraserRadiusPx)
                                } else {
                                    currentDrawingPoints = currentDrawingPoints + StrokePoint(change.position.x, change.position.y)
                                }
                            },
                            onDragEnd = {
                                if (activeTool == TacticTool.PEN && currentDrawingPoints.isNotEmpty()) {
                                    val stroke = TacticStroke(
                                        id = "stroke_${UUID.randomUUID()}",
                                        color = activeColor,
                                        width = activeWidth,
                                        points = currentDrawingPoints,
                                        isEraser = false
                                    )
                                    viewModel.addTacticStroke(stroke)
                                    currentDrawingPoints = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentDrawingPoints = emptyList()
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                // ==========================================
                // 1. FIELD LAYER (Static court lines with STRICT fixed aspect ratio)
                // ==========================================
                // Outer canvas background
                drawRect(color = Color(0xFF0D0E12))

                // Volleyball court is 2:1 (18x9m or 16x8m)
                val isLandscape = w >= h
                val targetAspectRatio = if (isLandscape) 2.0f else 0.5f // width / height

                val padding = 20f
                val maxAvailW = (w - 2 * padding).coerceAtLeast(10f)
                val maxAvailH = (h - 2 * padding).coerceAtLeast(10f)

                val courtWidth: Float
                val courtHeight: Float

                if (maxAvailW / maxAvailH > targetAspectRatio) {
                    // Height is the limiting factor
                    courtHeight = maxAvailH
                    courtWidth = maxAvailH * targetAspectRatio
                } else {
                    // Width is the limiting factor
                    courtWidth = maxAvailW
                    courtHeight = maxAvailW / targetAspectRatio
                }

                val courtLeft = (w - courtWidth) / 2f
                val courtTop = (h - courtHeight) / 2f
                val courtRight = courtLeft + courtWidth
                val courtBottom = courtTop + courtHeight

                // Fill actual court area with court surface color
                val courtBgColor = if (isBeachCourt) Color(0xFF1E1A14) else Color(0xFF141A22)
                drawRect(
                    color = courtBgColor,
                    topLeft = Offset(courtLeft, courtTop),
                    size = Size(courtWidth, courtHeight)
                )

                val lineColor = Color(0xFFD0D4DE)
                val lineStroke = Stroke(width = 4f)

                // Outer boundary lines (Grundlinien & Seitenlinien)
                drawRect(
                    color = lineColor,
                    topLeft = Offset(courtLeft, courtTop),
                    size = Size(courtWidth, courtHeight),
                    style = lineStroke
                )

                val netColor = VolleyYellow

                if (isLandscape) {
                    // HORIZONTAL ORIENTATION (2:1 aspect ratio)
                    // Net runs vertically through the middle
                    val centerX = courtLeft + courtWidth / 2f
                    drawLine(
                        color = netColor,
                        start = Offset(centerX, courtTop - 10f),
                        end = Offset(centerX, courtBottom + 10f),
                        strokeWidth = 8f
                    )
                    drawCircle(color = netColor, radius = 6f, center = Offset(centerX, courtTop - 10f))
                    drawCircle(color = netColor, radius = 6f, center = Offset(centerX, courtBottom + 10f))

                    // Attack lines (3m from net for indoor volleyball; half court is 9m -> 3/9)
                    if (!isBeachCourt) {
                        val attackDist = (courtWidth / 2f) * (3f / 9f)
                        val leftAttackX = centerX - attackDist
                        val rightAttackX = centerX + attackDist

                        drawLine(
                            color = Color(0xFFA0A6B5),
                            start = Offset(leftAttackX, courtTop),
                            end = Offset(leftAttackX, courtBottom),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = Color(0xFFA0A6B5),
                            start = Offset(rightAttackX, courtTop),
                            end = Offset(rightAttackX, courtBottom),
                            strokeWidth = 3f
                        )
                    }
                } else {
                    // VERTICAL ORIENTATION (1:2 aspect ratio)
                    // Net runs horizontally through the middle
                    val centerY = courtTop + courtHeight / 2f
                    drawLine(
                        color = netColor,
                        start = Offset(courtLeft - 10f, centerY),
                        end = Offset(courtRight + 10f, centerY),
                        strokeWidth = 8f
                    )
                    drawCircle(color = netColor, radius = 6f, center = Offset(courtLeft - 10f, centerY))
                    drawCircle(color = netColor, radius = 6f, center = Offset(courtRight + 10f, centerY))

                    // Attack lines (3m from net for indoor volleyball)
                    if (!isBeachCourt) {
                        val attackDist = (courtHeight / 2f) * (3f / 9f)
                        val topAttackY = centerY - attackDist
                        val bottomAttackY = centerY + attackDist

                        drawLine(
                            color = Color(0xFFA0A6B5),
                            start = Offset(courtLeft, topAttackY),
                            end = Offset(courtRight, topAttackY),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = Color(0xFFA0A6B5),
                            start = Offset(courtLeft, bottomAttackY),
                            end = Offset(courtRight, bottomAttackY),
                            strokeWidth = 3f
                        )
                    }
                }

                // ==========================================
                // 2. USER STROKES LAYER (Drawn on top of fieldLayer)
                // ==========================================
                strokes.forEach { stroke ->
                    if (!stroke.isEraser && stroke.points.size > 1) {
                        val path = Path()
                        path.moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            path.lineTo(stroke.points[i].x, stroke.points[i].y)
                        }

                        drawPath(
                            path = path,
                            color = stroke.color.color,
                            style = Stroke(
                                width = stroke.width.widthDp * 1.5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // Draw active in-progress pen stroke
                if (activeTool == TacticTool.PEN && currentDrawingPoints.size > 1) {
                    val path = Path()
                    path.moveTo(currentDrawingPoints[0].x, currentDrawingPoints[0].y)
                    for (i in 1 until currentDrawingPoints.size) {
                        path.lineTo(currentDrawingPoints[i].x, currentDrawingPoints[i].y)
                    }
                    drawPath(
                        path = path,
                        color = activeColor.color,
                        style = Stroke(
                            width = activeWidth.widthDp * 1.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
                }
            }

            val controlsPane: @Composable (Modifier) -> Unit = { paneMod ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = paneMod.border(1.dp, VolleyGrayBorder, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Group 1: Tool Selection & History Actions (FlowRow -> ZERO off-screen horizontal clipping)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { activeTool = TacticTool.PEN },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_tool_pen"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeTool == TacticTool.PEN) VolleyYellow else VolleyDarkSurfaceVariant,
                                    contentColor = if (activeTool == TacticTool.PEN) Color.Black else VolleyWhite
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stift", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { activeTool = TacticTool.ERASER },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_tool_eraser"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeTool == TacticTool.ERASER) VolleyYellow else VolleyDarkSurfaceVariant,
                                    contentColor = if (activeTool == TacticTool.ERASER) Color.Black else VolleyWhite
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Radierer", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.undoTactic() },
                                enabled = strokes.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_tactic_undo"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolleyDarkSurfaceVariant,
                                    contentColor = if (strokes.isNotEmpty()) VolleyWhite else VolleyGrayBorder
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo", fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.redoTactic() },
                                enabled = redoStack.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_tactic_redo"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolleyDarkSurfaceVariant,
                                    contentColor = if (redoStack.isNotEmpty()) VolleyWhite else VolleyGrayBorder
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Redo", fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.clearTacticBoard() },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_tactic_clear"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolleyDarkSurfaceVariant,
                                    contentColor = VolleyRed
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = VolleyRed)
                                Spacer(Modifier.width(4.dp))
                                Text("Löschen", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }

                        // Group 2: Colors and Widths (FlowRow -> ZERO off-screen horizontal clipping)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TacticColor.values().forEach { tc ->
                                Button(
                                    onClick = {
                                        activeTool = TacticTool.PEN
                                        activeColor = tc
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_color_${tc.name}"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeColor == tc && activeTool == TacticTool.PEN) tc.color else VolleyDarkSurfaceVariant,
                                        contentColor = if (activeColor == tc && activeTool == TacticTool.PEN) Color.Black else tc.color
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, tc.color),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(tc.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }

                            StrokeWidthLevel.values().forEach { sw ->
                                Button(
                                    onClick = { activeWidth = sw },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_width_${sw.name}"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeWidth == sw) VolleyYellow else VolleyDarkSurfaceVariant,
                                        contentColor = if (activeWidth == sw) Color.Black else VolleyWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(sw.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }

            if (isSideBySide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    canvasPane(Modifier.weight(1f).fillMaxHeight())
                    controlsPane(Modifier.widthIn(min = 220.dp, max = 290.dp).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    canvasPane(Modifier.weight(1f).fillMaxWidth())
                    controlsPane(Modifier.fillMaxWidth().heightIn(max = portraitControlsMaxH))
                }
            }
        }
    }
}
