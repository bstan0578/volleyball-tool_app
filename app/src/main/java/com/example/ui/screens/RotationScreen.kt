package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.VolleyStepperButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.VolleyViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotationScreen(
    viewModel: VolleyViewModel,
    modifier: Modifier = Modifier
) {
    val currentMatch by viewModel.currentMatch.collectAsState()
    val returnToScore by viewModel.returnToScoreAfterRotation.collectAsState()
    val configA by viewModel.rotationConfigA.collectAsState()
    val configB by viewModel.rotationConfigB.collectAsState()
    val teamAName by viewModel.rotationTeamNameA.collectAsState()
    val teamBName by viewModel.rotationTeamNameB.collectAsState()
    val allTeams by viewModel.allTeams.collectAsState()
    val trackedTarget by viewModel.trackedCourtTarget.collectAsState()

    BackHandler(enabled = returnToScore) {
        viewModel.returnFromRotationToScoreboard()
    }

    var selectedControlTeam by remember { mutableStateOf(TeamSide.TEAM_A) }
    var editingLineupSide by remember { mutableStateOf<TeamSide?>(null) }
    var substitutingSide by remember { mutableStateOf<TeamSide?>(null) }

    val isLinked = currentMatch?.rotationLinked == true

    val tokensA = remember(configA) { RotationEngine.calculatePositions(configA) }
    val tokensB = remember(configB) { RotationEngine.calculatePositions(configB) }

    val baseTokensA = remember(configA) { RotationEngine.calculatePositions(configA.copy(phase = RotationPhase.BASE)) }
    val baseTokensB = remember(configB) { RotationEngine.calculatePositions(configB.copy(phase = RotationPhase.BASE)) }
    val serveTokensA = remember(configA) { RotationEngine.calculatePositions(configA.copy(phase = RotationPhase.SERVE)) }
    val serveTokensB = remember(configB) { RotationEngine.calculatePositions(configB.copy(phase = RotationPhase.SERVE)) }

    val countA = configA.playerCountOnCourt.coerceIn(2, 6)
    val countB = configB.playerCountOnCourt.coerceIn(2, 6)
    val prevRotTokensA = remember(configA) {
        RotationEngine.calculatePositions(
            configA.copy(rotationStep = ((configA.rotationStep - 1) % countA + countA) % countA)
        )
    }
    val prevRotTokensB = remember(configB) {
        RotationEngine.calculatePositions(
            configB.copy(rotationStep = ((configB.rotationStep - 1) % countB + countB) % countB)
        )
    }

    val latestTokensA by rememberUpdatedState(tokensA)
    val latestTokensB by rememberUpdatedState(tokensB)
    val latestTrackedTarget by rememberUpdatedState(trackedTarget)

    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolleyBlack)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Top Header with Back to Scoreboard + Link Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (returnToScore || isLinked) {
                    OutlinedButton(
                        onClick = { viewModel.returnFromRotationToScoreboard() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_back_to_scoreboard"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = VolleyDarkSurfaceVariant,
                            contentColor = VolleyYellow
                        ),
                        border = BorderStroke(1.dp, VolleyYellow),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zum Scoreboard", modifier = Modifier.size(16.dp), tint = VolleyYellow)
                        Spacer(Modifier.width(4.dp))
                        Text("Scoreboard", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
                Text(
                    "Rotation & Aufstellung",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (currentMatch != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (isLinked) VolleyYellow else VolleyTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Link",
                        color = if (isLinked) VolleyYellow else VolleyTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = isLinked,
                        onCheckedChange = { viewModel.toggleRotationLinked(it) },
                        modifier = Modifier.testTag("switch_rotation_linked"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VolleyYellow,
                            checkedTrackColor = VolleyDarkSurfaceVariant
                        )
                    )
                }
            }
        }

        // Scoreboard Link Status Banner
        if (currentMatch != null && isLinked) {
            val match = currentMatch!!
            Surface(
                color = VolleyYellow.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Verknüpft: ${match.teamAName} ${match.scoreA}:${match.scoreB} ${match.teamBName}",
                        color = VolleyYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Auto-Rotation aktiv",
                        color = VolleyGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Adaptive content area: Side-by-side on Landscape so Court Canvas never gets crushed & Controls stay visible
        val activeTrackedInfo: Pair<TeamSide, CourtPlayerToken>? = trackedTarget?.let { target ->
            val list = if (target.side == TeamSide.TEAM_A) tokensA else tokensB
            list.firstOrNull { target.matches(target.side, it) }?.let { target.side to it }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isSideBySide = maxWidth > maxHeight * 1.15f && maxWidth >= 480.dp
            val controlsMaxHeightPortrait = (maxHeight * 0.28f).coerceAtLeast(122.dp)

            val courtAndBanner: @Composable (Modifier) -> Unit = { paneMod ->
                // COMPLETE TWO-TEAM VOLLEYBALL COURT CANVAS
                // Tap token = track; Tap empty half-court = select Red/Blue; Long-press half-court = open Kader
                BoxWithConstraints(
                    modifier = paneMod
                        .background(Color(0xFF0E1117), RoundedCornerShape(10.dp))
                        .border(1.5.dp, VolleyGrayBorder, RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            fun sideForOffset(offset: Offset, geom: CourtGeometry): TeamSide {
                                return if (geom.isLandscape) {
                                    if (offset.x <= geom.centerX) TeamSide.TEAM_A else TeamSide.TEAM_B
                                } else {
                                    if (offset.y >= geom.centerY) TeamSide.TEAM_A else TeamSide.TEAM_B
                                }
                            }
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                                    val geom = computeCourtGeometry(canvasSize, this)
                                    val hitRadius = maxOf(geom.tokenRadius * 1.55f, 26.dp.toPx())

                                    val hitA = latestTokensA.map { it to geom.mapTeamAToken(it) }
                                        .minByOrNull { (_, pos) -> hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) }
                                    val hitB = latestTokensB.map { it to geom.mapTeamBToken(it) }
                                        .minByOrNull { (_, pos) -> hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) }

                                    val distA = hitA?.let { hypot(tapOffset.x - it.second.x, tapOffset.y - it.second.y) } ?: Float.MAX_VALUE
                                    val distB = hitB?.let { hypot(tapOffset.x - it.second.x, tapOffset.y - it.second.y) } ?: Float.MAX_VALUE

                                    when {
                                        distA <= hitRadius && distA <= distB && hitA != null -> {
                                            selectedControlTeam = TeamSide.TEAM_A
                                            viewModel.toggleTrackedCourtTarget(TeamSide.TEAM_A, hitA.first)
                                        }
                                        distB <= hitRadius && hitB != null -> {
                                            selectedControlTeam = TeamSide.TEAM_B
                                            viewModel.toggleTrackedCourtTarget(TeamSide.TEAM_B, hitB.first)
                                        }
                                        else -> {
                                            selectedControlTeam = sideForOffset(tapOffset, geom)
                                            if (latestTrackedTarget != null) {
                                                viewModel.clearTrackedCourtTarget()
                                            }
                                        }
                                    }
                                },
                                onLongPress = { pressOffset ->
                                    val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                                    val geom = computeCourtGeometry(canvasSize, this)
                                    val side = sideForOffset(pressOffset, geom)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedControlTeam = side
                                    editingLineupSide = side
                                }
                            )
                        }
                        .testTag("canvas_rotation_court")
                ) {
                    val courtIsLandscape = maxWidth >= maxHeight
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val geom = computeCourtGeometry(size, this)
                        val courtLeft = geom.courtLeft
                        val courtTop = geom.courtTop
                        val courtRight = geom.courtRight
                        val courtBottom = geom.courtBottom
                        val courtWidth = geom.courtWidth
                        val courtHeight = geom.courtHeight
                        val centerX = geom.centerX
                        val centerY = geom.centerY
                        val isLandscape = geom.isLandscape

                        val alphaA = if (selectedControlTeam == TeamSide.TEAM_A) 0.14f else 0.06f
                        val alphaB = if (selectedControlTeam == TeamSide.TEAM_B) 0.14f else 0.06f

                        // Half-court background tints (Red vs Blue) + subtle active side indicator
                        if (isLandscape) {
                            drawRect(
                                color = VolleyRed.copy(alpha = alphaA),
                                topLeft = Offset(courtLeft, courtTop),
                                size = Size(courtWidth / 2f, courtHeight)
                            )
                            drawRect(
                                color = VolleyBlue.copy(alpha = alphaB),
                                topLeft = Offset(centerX, courtTop),
                                size = Size(courtWidth / 2f, courtHeight)
                            )
                            if (selectedControlTeam == TeamSide.TEAM_A) {
                                drawRect(
                                    color = VolleyRed.copy(alpha = 0.75f),
                                    topLeft = Offset(courtLeft, courtTop),
                                    size = Size(courtWidth / 2f, courtHeight),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            } else {
                                drawRect(
                                    color = VolleyBlue.copy(alpha = 0.75f),
                                    topLeft = Offset(centerX, courtTop),
                                    size = Size(courtWidth / 2f, courtHeight),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        } else {
                            drawRect(
                                color = VolleyBlue.copy(alpha = alphaB),
                                topLeft = Offset(courtLeft, courtTop),
                                size = Size(courtWidth, courtHeight / 2f)
                            )
                            drawRect(
                                color = VolleyRed.copy(alpha = alphaA),
                                topLeft = Offset(courtLeft, centerY),
                                size = Size(courtWidth, courtHeight / 2f)
                            )
                            if (selectedControlTeam == TeamSide.TEAM_B) {
                                drawRect(
                                    color = VolleyBlue.copy(alpha = 0.75f),
                                    topLeft = Offset(courtLeft, courtTop),
                                    size = Size(courtWidth, courtHeight / 2f),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            } else {
                                drawRect(
                                    color = VolleyRed.copy(alpha = 0.75f),
                                    topLeft = Offset(courtLeft, centerY),
                                    size = Size(courtWidth, courtHeight / 2f),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }

                        // Subtle team side watermarks inside corners
                        val cornerStyleA = TextStyle(
                            color = VolleyRed.copy(alpha = if (selectedControlTeam == TeamSide.TEAM_A) 0.9f else 0.55f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        val cornerStyleB = TextStyle(
                            color = VolleyBlue.copy(alpha = if (selectedControlTeam == TeamSide.TEAM_B) 0.9f else 0.55f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        val labelA = textMeasurer.measure(teamAName.take(14), style = cornerStyleA)
                        val labelB = textMeasurer.measure(teamBName.take(14), style = cornerStyleB)
                        if (isLandscape) {
                            drawText(labelA, topLeft = Offset(courtLeft + 6.dp.toPx(), courtTop + 4.dp.toPx()))
                            drawText(labelB, topLeft = Offset(courtRight - labelB.size.width - 6.dp.toPx(), courtTop + 4.dp.toPx()))
                        } else {
                            drawText(labelB, topLeft = Offset(courtLeft + 6.dp.toPx(), courtTop + 4.dp.toPx()))
                            drawText(labelA, topLeft = Offset(courtLeft + 6.dp.toPx(), courtBottom - labelA.size.height - 4.dp.toPx()))
                        }

                        // Faint dashed horizontal and vertical alignment helper lines
                        val dashGridEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        val helperGridColor = Color(0xFFB0BEC5).copy(alpha = 0.25f)

                        if (isLandscape) {
                            listOf(1f / 3f, 2f / 3f).forEach { frac ->
                                val ly = courtTop + courtHeight * frac
                                drawLine(
                                    color = helperGridColor,
                                    start = Offset(courtLeft, ly),
                                    end = Offset(courtRight, ly),
                                    strokeWidth = 2f,
                                    pathEffect = dashGridEffect
                                )
                            }
                            listOf(0.22f, 0.78f).forEach { frac ->
                                val lx = courtLeft + courtWidth * frac
                                drawLine(
                                    color = helperGridColor,
                                    start = Offset(lx, courtTop),
                                    end = Offset(lx, courtBottom),
                                    strokeWidth = 2f,
                                    pathEffect = dashGridEffect
                                )
                            }
                        } else {
                            listOf(1f / 3f, 2f / 3f).forEach { frac ->
                                val lx = courtLeft + courtWidth * frac
                                drawLine(
                                    color = helperGridColor,
                                    start = Offset(lx, courtTop),
                                    end = Offset(lx, courtBottom),
                                    strokeWidth = 2f,
                                    pathEffect = dashGridEffect
                                )
                            }
                            listOf(0.22f, 0.78f).forEach { frac ->
                                val ly = courtTop + courtHeight * frac
                                drawLine(
                                    color = helperGridColor,
                                    start = Offset(courtLeft, ly),
                                    end = Offset(courtRight, ly),
                                    strokeWidth = 2f,
                                    pathEffect = dashGridEffect
                                )
                            }
                        }

                        // Court boundary lines
                        drawRect(
                            color = Color(0xFFD0D4DE),
                            topLeft = Offset(courtLeft, courtTop),
                            size = Size(courtWidth, courtHeight),
                            style = Stroke(width = 3.5f)
                        )

                        // 3m Attack Lines + Net
                        if (isLandscape) {
                            val attackDist = (courtWidth / 2f) * (3f / 9f)
                            drawLine(
                                color = Color(0xFFA0A6B5),
                                start = Offset(centerX - attackDist, courtTop),
                                end = Offset(centerX - attackDist, courtBottom),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Color(0xFFA0A6B5),
                                start = Offset(centerX + attackDist, courtTop),
                                end = Offset(centerX + attackDist, courtBottom),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = VolleyYellow,
                                start = Offset(centerX, courtTop - 8f),
                                end = Offset(centerX, courtBottom + 8f),
                                strokeWidth = 7f
                            )
                        } else {
                            val attackDist = (courtHeight / 2f) * (3f / 9f)
                            drawLine(
                                color = Color(0xFFA0A6B5),
                                start = Offset(courtLeft, centerY - attackDist),
                                end = Offset(courtRight, centerY - attackDist),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Color(0xFFA0A6B5),
                                start = Offset(courtLeft, centerY + attackDist),
                                end = Offset(courtRight, centerY + attackDist),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = VolleyYellow,
                                start = Offset(courtLeft - 8f, centerY),
                                end = Offset(courtRight + 8f, centerY),
                                strokeWidth = 7f
                            )
                        }

                        val posMapA = tokensA.associate { it.zoneNumber to geom.mapTeamAToken(it) }
                        val posMapB = tokensB.associate { it.zoneNumber to geom.mapTeamBToken(it) }
                        val selectedTokenA = trackedTarget?.let { t -> tokensA.firstOrNull { t.matches(TeamSide.TEAM_A, it) } }
                        val selectedTokenB = trackedTarget?.let { t -> tokensB.firstOrNull { t.matches(TeamSide.TEAM_B, it) } }

                        // FIVB overlap helper lines only apply in 6v6 BASE/SERVE/RECEIVE (not in IN_GAME active rally)
                        if (configA.playerCountOnCourt == 6 && configA.phase != RotationPhase.IN_GAME) {
                            drawOverlapHelperWeb(
                                posMap = posMapA,
                                color = VolleyRed.copy(alpha = 0.35f),
                                dashEffect = dashGridEffect,
                                highlightedZone = selectedTokenA?.zoneNumber
                            )
                        }
                        if (configB.playerCountOnCourt == 6 && configB.phase != RotationPhase.IN_GAME) {
                            drawOverlapHelperWeb(
                                posMap = posMapB,
                                color = VolleyBlue.copy(alpha = 0.35f),
                                dashEffect = dashGridEffect,
                                highlightedZone = selectedTokenB?.zoneNumber
                            )
                        }

                        val tokenRadius = geom.tokenRadius
                        val hasSelection = trackedTarget != null

                        // Draw progression trails ("Verlauf") for the highlighted player
                        if (selectedTokenA != null && trackedTarget != null) {
                            drawTeamProgressionTrails(
                                selectedToken = selectedTokenA,
                                config = configA,
                                posMap = posMapA,
                                prevRotTokens = prevRotTokensA,
                                baseTokens = baseTokensA,
                                serveTokens = serveTokensA,
                                trackedTarget = trackedTarget!!,
                                teamSide = TeamSide.TEAM_A,
                                tokenRadius = tokenRadius,
                                teamColor = VolleyRed,
                                mapToken = { geom.mapTeamAToken(it) },
                                textMeasurer = textMeasurer
                            )
                        }
                        if (selectedTokenB != null && trackedTarget != null) {
                            drawTeamProgressionTrails(
                                selectedToken = selectedTokenB,
                                config = configB,
                                posMap = posMapB,
                                prevRotTokens = prevRotTokensB,
                                baseTokens = baseTokensB,
                                serveTokens = serveTokensB,
                                trackedTarget = trackedTarget!!,
                                teamSide = TeamSide.TEAM_B,
                                tokenRadius = tokenRadius,
                                teamColor = VolleyBlue,
                                mapToken = { geom.mapTeamBToken(it) },
                                textMeasurer = textMeasurer
                            )
                        }

                        // Draw Team A (Red) & Team B (Blue) player tokens
                        tokensA.forEach { token ->
                            val center = posMapA[token.zoneNumber] ?: return@forEach
                            val isHighlighted = selectedTokenA?.zoneNumber == token.zoneNumber
                            drawPlayerToken(
                                token = token,
                                center = center,
                                radius = tokenRadius,
                                teamColor = VolleyRed,
                                isHighlighted = isHighlighted,
                                dimmed = hasSelection && !isHighlighted,
                                textMeasurer = textMeasurer
                            )
                        }

                        tokensB.forEach { token ->
                            val center = posMapB[token.zoneNumber] ?: return@forEach
                            val isHighlighted = selectedTokenB?.zoneNumber == token.zoneNumber
                            drawPlayerToken(
                                token = token,
                                center = center,
                                radius = tokenRadius,
                                teamColor = VolleyBlue,
                                isHighlighted = isHighlighted,
                                dimmed = hasSelection && !isHighlighted,
                                textMeasurer = textMeasurer
                            )
                        }
                    }

                    // GRAY OVERLAY TRACKER STRIP ON OPPONENT'S HALF BORDER INSIDE COURT (no layout shift!)
                    if (activeTrackedInfo != null) {
                        val (side, token) = activeTrackedInfo
                        val trackTeamName = if (side == TeamSide.TEAM_A) teamAName else teamBName
                        val trackCfg = if (side == TeamSide.TEAM_A) configA else configB
                        val nameSuffix = if (token.playerName.isNotBlank()) " · ${token.playerName}" else ""
                        val detailText = if (trackCfg.phase == RotationPhase.IN_GAME && token.inGameTargetZone != null) {
                            "Pos. P${token.zoneNumber} → Im Spiel auf P${token.inGameTargetZone} (${RotationEngine.zoneLabel(token.inGameTargetZone)})"
                        } else {
                            "Pos. ${RotationEngine.zoneLabel(token.zoneNumber)} · Gelb = Nachbarn & Verlauf"
                        }

                        // Opponent half placement:
                        // Portrait: Team Rot (bottom) -> Top edge (Team Blau half); Team Blau (top) -> Bottom edge (Team Rot half)
                        // Landscape: Team Rot (left) -> Right half; Team Blau (right) -> Left half
                        val overlayAlignmentModifier = if (courtIsLandscape) {
                            if (side == TeamSide.TEAM_A) {
                                Modifier.align(Alignment.TopEnd).fillMaxWidth(0.49f)
                            } else {
                                Modifier.align(Alignment.BottomStart).fillMaxWidth(0.49f)
                            }
                        } else {
                            if (side == TeamSide.TEAM_A) {
                                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            } else {
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            }
                        }

                        Surface(
                            color = Color(0xFF2B303A).copy(alpha = 0.90f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF707888)),
                            modifier = overlayAlignmentModifier
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("banner_tracked_position")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = VolleyYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "$trackTeamName · #${token.jerseyNumber} ${token.roleLabel}$nameSuffix",
                                        color = VolleyWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        detailText,
                                        color = Color(0xFFD5DAE3),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearTrackedCourtTarget() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_clear_tracked_position")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Markierung aufheben",
                                        tint = VolleyWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
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
                    courtAndBanner(Modifier.weight(1f).fillMaxHeight())
                    RotationControlsCard(
                        selectedControlTeam = selectedControlTeam,
                        teamAName = teamAName,
                        teamBName = teamBName,
                        configA = configA,
                        configB = configB,
                        onStepRotation = { delta -> viewModel.stepTeamRotation(selectedControlTeam, delta) },
                        onToggleLibero = { viewModel.toggleLiberoActive(selectedControlTeam) },
                        onOpenKader = { editingLineupSide = selectedControlTeam },
                        onOpenSubstitutions = { substitutingSide = selectedControlTeam },
                        onSelectSystem = { sys -> viewModel.setRotationSystem(selectedControlTeam, sys) },
                        onSelectPhase = { ph -> viewModel.setRotationPhase(selectedControlTeam, ph) },
                        modifier = Modifier
                            .widthIn(min = 240.dp, max = 300.dp)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    courtAndBanner(Modifier.weight(1f).fillMaxWidth())
                    RotationControlsCard(
                        selectedControlTeam = selectedControlTeam,
                        teamAName = teamAName,
                        teamBName = teamBName,
                        configA = configA,
                        configB = configB,
                        onStepRotation = { delta -> viewModel.stepTeamRotation(selectedControlTeam, delta) },
                        onToggleLibero = { viewModel.toggleLiberoActive(selectedControlTeam) },
                        onOpenKader = { editingLineupSide = selectedControlTeam },
                        onOpenSubstitutions = { substitutingSide = selectedControlTeam },
                        onSelectSystem = { sys -> viewModel.setRotationSystem(selectedControlTeam, sys) },
                        onSelectPhase = { ph -> viewModel.setRotationPhase(selectedControlTeam, ph) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = controlsMaxHeightPortrait)
                    )
                }
            }
        }
    }

    // Lineup, Names, Jerseys (1-6 / LB 77), Court Count (2-6), & Bench Setup (max 10 total)
    editingLineupSide?.let { side ->
        val cfg = if (side == TeamSide.TEAM_A) configA else configB
        val tName = if (side == TeamSide.TEAM_A) teamAName else teamBName
        EditLineupDialog(
            initialTeamName = tName,
            teamColor = if (side == TeamSide.TEAM_A) VolleyRed else VolleyBlue,
            config = cfg,
            availableMixerTeams = allTeams,
            hasTwoMixerTeams = allTeams.size == 2,
            onLoadFromMixer = { viewModel.loadFinalizedTeamsIntoRotation() },
            onAssignMixerTeam = { mixerTeam -> viewModel.assignMixerTeamToRotationSide(side, mixerTeam) },
            onOpenTeamMixer = { viewModel.openTeamMixerFromRotation() },
            onDismiss = { editingLineupSide = null },
            onSave = { newName, courtCount, jerseys, names, bench ->
                viewModel.updateTeamLineup(
                    side = side,
                    teamName = newName,
                    playerCountOnCourt = courtCount,
                    jerseys = jerseys,
                    names = names,
                    benchPlayers = bench
                )
                editingLineupSide = null
            }
        )
    }

    // Substitution Dialog (Wechsel eingeben + Wechsel-Protokoll)
    substitutingSide?.let { side ->
        val cfg = if (side == TeamSide.TEAM_A) configA else configB
        val tName = if (side == TeamSide.TEAM_A) teamAName else teamBName
        SubstitutionDialog(
            teamName = tName,
            teamColor = if (side == TeamSide.TEAM_A) VolleyRed else VolleyBlue,
            config = cfg,
            onSubstitute = { courtRoleKey, benchPlayerId ->
                viewModel.substitutePlayer(side, courtRoleKey, benchPlayerId)
            },
            onOpenLineupSetup = { editingLineupSide = side },
            onDismiss = { substitutingSide = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RotationControlsCard(
    selectedControlTeam: TeamSide,
    teamAName: String,
    teamBName: String,
    configA: TeamRotationConfig,
    configB: TeamRotationConfig,
    onStepRotation: (Int) -> Unit,
    onToggleLibero: () -> Unit,
    onOpenKader: () -> Unit,
    onOpenSubstitutions: () -> Unit,
    onSelectSystem: (RotationSystem) -> Unit,
    onSelectPhase: (RotationPhase) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCfg = if (selectedControlTeam == TeamSide.TEAM_A) configA else configB
    val activeColor = if (selectedControlTeam == TeamSide.TEAM_A) VolleyRed else VolleyBlue
    val activeName = if (selectedControlTeam == TeamSide.TEAM_A) teamAName else teamBName
    val activeCourtCount = activeCfg.playerCountOnCourt.coerceIn(2, 6)

    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.border(1.dp, VolleyGrayBorder, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Compact active-team indicator (team selection is directly via tapping empty space on Red/Blue half-court; long-press opens Kader)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.size(9.dp).background(activeColor, CircleShape))
                    Text(
                        activeName,
                        color = VolleyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "Feld tippen = Team · Halten = Kader",
                    color = VolleyTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 1: Cyclic System Toggle (5-1 -> 4-2 -> 6-2) left of '-' + Rotation Stepper + LB + Kader + Wechsel
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (activeCourtCount == 6) {
                    Button(
                        onClick = { onSelectSystem(activeCfg.system.next()) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
                            .height(42.dp)
                            .testTag("btn_system_toggle"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VolleyYellow,
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(1.dp, VolleyYellow),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            activeCfg.system.shortLabel,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Surface(
                        color = VolleyYellow.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, VolleyYellow),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                            Text("${activeCourtCount}er", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VolleyStepperButton(
                        isIncrement = false,
                        sizeDp = 42.dp,
                        accentColor = activeColor,
                        onClick = { onStepRotation(-1) },
                        testTagName = "btn_rotation_prev"
                    )
                    Surface(
                        color = activeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, activeColor),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                "${activeCfg.rotationStep + 1}/$activeCourtCount",
                                color = VolleyWhite,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    VolleyStepperButton(
                        isIncrement = true,
                        sizeDp = 42.dp,
                        accentColor = activeColor,
                        onClick = { onStepRotation(1) },
                        testTagName = "btn_rotation_next"
                    )
                }

                if (activeCourtCount == 6) {
                    Button(
                        onClick = onToggleLibero,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 36.dp, minHeight = 42.dp)
                            .height(42.dp)
                            .testTag("btn_toggle_libero"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeCfg.liberoActive) Color(0xFFFFB300) else VolleyDarkSurfaceVariant,
                            contentColor = if (activeCfg.liberoActive) Color.Black else VolleyWhite
                        ),
                        border = BorderStroke(1.dp, if (activeCfg.liberoActive) Color(0xFFFFB300) else VolleyGrayBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("LB", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }

                Button(
                    onClick = onOpenKader,
                    contentPadding = PaddingValues(horizontal = 7.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 42.dp)
                        .height(42.dp)
                        .testTag("btn_edit_jerseys"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyDarkSurfaceVariant,
                        contentColor = VolleyYellow
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Kader & Trikots", modifier = Modifier.size(14.dp), tint = VolleyYellow)
                    Spacer(Modifier.width(3.dp))
                    Text("Kader", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = onOpenSubstitutions,
                    contentPadding = PaddingValues(horizontal = 7.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
                        .height(42.dp)
                        .testTag("btn_substitutions"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeCfg.benchPlayers.isNotEmpty()) VolleyYellow.copy(alpha = 0.16f) else VolleyDarkSurfaceVariant,
                        contentColor = VolleyYellow
                    ),
                    border = BorderStroke(1.dp, if (activeCfg.benchPlayers.isNotEmpty()) VolleyYellow else VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Spielerwechsel", modifier = Modifier.size(14.dp), tint = VolleyYellow)
                    Spacer(Modifier.width(3.dp))
                    Text("${activeCfg.benchPlayers.size}", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }

            // Row 2: Phase selector (Grund / Aufschlag / Annahme / Im Spiel) on one clean row when wide enough
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val singleRowPhases = maxWidth >= 290.dp
                if (singleRowPhases) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RotationPhase.values().forEach { ph ->
                            val active = activeCfg.phase == ph
                            Button(
                                onClick = { onSelectPhase(ph) },
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_phase_${ph.name.lowercase()}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) activeColor else VolleyDarkSurfaceVariant,
                                    contentColor = if (active) Color.White else VolleyWhite
                                ),
                                border = BorderStroke(1.dp, if (active) activeColor else VolleyGrayBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(ph.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RotationPhase.values().forEach { ph ->
                            val active = activeCfg.phase == ph
                            Button(
                                onClick = { onSelectPhase(ph) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("btn_phase_${ph.name.lowercase()}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) activeColor else VolleyDarkSurfaceVariant,
                                    contentColor = if (active) Color.White else VolleyWhite
                                ),
                                border = BorderStroke(1.dp, if (active) activeColor else VolleyGrayBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(ph.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CourtGeometry(
    val isLandscape: Boolean,
    val courtLeft: Float,
    val courtTop: Float,
    val courtRight: Float,
    val courtBottom: Float,
    val courtWidth: Float,
    val courtHeight: Float,
    val centerX: Float,
    val centerY: Float,
    val tokenRadius: Float
) {
    fun mapTeamAToken(token: CourtPlayerToken): Offset {
        return if (isLandscape) {
            Offset(
                x = centerX - token.yNetNorm * (courtWidth / 2f),
                y = courtTop + token.xHalfNorm * courtHeight
            )
        } else {
            Offset(
                x = courtLeft + token.xHalfNorm * courtWidth,
                y = centerY + token.yNetNorm * (courtHeight / 2f)
            )
        }
    }

    fun mapTeamBToken(token: CourtPlayerToken): Offset {
        return if (isLandscape) {
            Offset(
                x = centerX + token.yNetNorm * (courtWidth / 2f),
                y = courtBottom - token.xHalfNorm * courtHeight
            )
        } else {
            Offset(
                x = courtRight - token.xHalfNorm * courtWidth,
                y = centerY - token.yNetNorm * (courtHeight / 2f)
            )
        }
    }
}

private fun computeCourtGeometry(canvasSize: Size, density: Density): CourtGeometry = with(density) {
    val w = canvasSize.width
    val h = canvasSize.height
    val isLandscape = w >= h
    val targetAspect = if (isLandscape) 2.0f else 0.5f

    val outerPadX = if (isLandscape) 32.dp.toPx() else 10.dp.toPx()
    val outerPadY = if (isLandscape) 18.dp.toPx() else 26.dp.toPx()
    val maxW = (w - 2 * outerPadX).coerceAtLeast(10f)
    val maxH = (h - 2 * outerPadY).coerceAtLeast(10f)

    val courtWidth: Float
    val courtHeight: Float
    if (maxW / maxH > targetAspect) {
        courtHeight = maxH
        courtWidth = maxH * targetAspect
    } else {
        courtWidth = maxW
        courtHeight = maxW / targetAspect
    }

    val courtLeft = (w - courtWidth) / 2f
    val courtTop = (h - courtHeight) / 2f
    val courtRight = courtLeft + courtWidth
    val courtBottom = courtTop + courtHeight
    val centerX = courtLeft + courtWidth / 2f
    val centerY = courtTop + courtHeight / 2f
    val tokenRadius = (minOf(courtWidth, courtHeight) * 0.095f).coerceIn(18.dp.toPx(), 28.dp.toPx())

    CourtGeometry(
        isLandscape = isLandscape,
        courtLeft = courtLeft,
        courtTop = courtTop,
        courtRight = courtRight,
        courtBottom = courtBottom,
        courtWidth = courtWidth,
        courtHeight = courtHeight,
        centerX = centerX,
        centerY = centerY,
        tokenRadius = tokenRadius
    )
}

private fun DrawScope.drawTeamProgressionTrails(
    selectedToken: CourtPlayerToken,
    config: TeamRotationConfig,
    posMap: Map<Int, Offset>,
    prevRotTokens: List<CourtPlayerToken>,
    baseTokens: List<CourtPlayerToken>,
    serveTokens: List<CourtPlayerToken>,
    trackedTarget: TrackedCourtTarget,
    teamSide: TeamSide,
    tokenRadius: Float,
    teamColor: Color,
    mapToken: (CourtPlayerToken) -> Offset,
    textMeasurer: TextMeasurer
) {
    val curPos = posMap[selectedToken.zoneNumber] ?: return
    val prevTok = prevRotTokens.firstOrNull { trackedTarget.matches(teamSide, it) }
    val originTok = if (config.phase == RotationPhase.IN_GAME && selectedToken.zoneNumber == 1) {
        serveTokens.firstOrNull { it.zoneNumber == selectedToken.zoneNumber }
    } else {
        baseTokens.firstOrNull { it.zoneNumber == selectedToken.zoneNumber }
    }
    val originLabel = if (config.phase == RotationPhase.IN_GAME && selectedToken.zoneNumber == 1) {
        "Auf P1"
    } else {
        "Grund"
    }

    if (prevTok != null && prevTok.zoneNumber != selectedToken.zoneNumber) {
        drawProgressionGhostAndArrow(
            fromCenter = mapToken(prevTok),
            toCenter = curPos,
            radius = tokenRadius * 0.72f,
            color = VolleyYellow.copy(alpha = 0.55f),
            ghostLabel = "P${prevTok.zoneNumber}",
            textMeasurer = textMeasurer
        )
    }
    if (config.phase != RotationPhase.BASE && originTok != null) {
        val originPos = mapToken(originTok)
        if (hypot(originPos.x - curPos.x, originPos.y - curPos.y) > tokenRadius * 1.1f) {
            drawProgressionGhostAndArrow(
                fromCenter = originPos,
                toCenter = curPos,
                radius = tokenRadius * 0.65f,
                color = teamColor.copy(alpha = 0.72f),
                ghostLabel = originLabel,
                textMeasurer = textMeasurer
            )
        }
    }
}

private fun DrawScope.drawOverlapHelperWeb(
    posMap: Map<Int, Offset>,
    color: Color,
    dashEffect: PathEffect,
    highlightedZone: Int? = null
) {
    RotationEngine.OVERLAP_PAIRS.forEach { (z1, z2) ->
        val p1 = posMap[z1]
        val p2 = posMap[z2]
        if (p1 != null && p2 != null) {
            val isHighlightedLink = highlightedZone != null && (z1 == highlightedZone || z2 == highlightedZone)
            drawLine(
                color = if (isHighlightedLink) VolleyYellow.copy(alpha = 0.9f) else color,
                start = p1,
                end = p2,
                strokeWidth = if (isHighlightedLink) 2.5.dp.toPx() else 2f,
                pathEffect = if (isHighlightedLink) null else dashEffect
            )
        }
    }
}

private fun DrawScope.drawProgressionGhostAndArrow(
    fromCenter: Offset,
    toCenter: Offset,
    radius: Float,
    color: Color,
    ghostLabel: String,
    textMeasurer: TextMeasurer
) {
    val dx = toCenter.x - fromCenter.x
    val dy = toCenter.y - fromCenter.y
    val dist = hypot(dx, dy)
    if (dist < 4f) return

    drawCircle(
        color = color.copy(alpha = 0.18f),
        radius = radius,
        center = fromCenter
    )
    drawCircle(
        color = color,
        radius = radius,
        center = fromCenter,
        style = Stroke(
            width = 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    )

    val labelStyle = TextStyle(
        color = VolleyWhite.copy(alpha = 0.85f),
        fontSize = (radius * 0.36f).toSp(),
        fontWeight = FontWeight.Bold
    )
    val labelLayout = textMeasurer.measure(ghostLabel, style = labelStyle)
    drawText(
        textLayoutResult = labelLayout,
        topLeft = Offset(
            x = fromCenter.x - labelLayout.size.width / 2f,
            y = fromCenter.y - labelLayout.size.height / 2f
        )
    )

    val ux = dx / dist
    val uy = dy / dist
    val startPt = Offset(fromCenter.x + ux * radius, fromCenter.y + uy * radius)
    val endPt = Offset(toCenter.x - ux * (radius * 1.15f), toCenter.y - uy * (radius * 1.15f))
    if (hypot(endPt.x - startPt.x, endPt.y - startPt.y) > 4f) {
        drawLine(
            color = color,
            start = startPt,
            end = endPt,
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
        val angle = atan2(dy, dx)
        val headLen = 9.dp.toPx()
        val spread = 0.48f
        val headPath = Path().apply {
            moveTo(endPt.x, endPt.y)
            lineTo(
                endPt.x - headLen * cos(angle - spread),
                endPt.y - headLen * sin(angle - spread)
            )
            lineTo(
                endPt.x - headLen * cos(angle + spread),
                endPt.y - headLen * sin(angle + spread)
            )
            close()
        }
        drawPath(path = headPath, color = color)
    }
}

private fun DrawScope.drawPlayerToken(
    token: CourtPlayerToken,
    center: Offset,
    radius: Float,
    teamColor: Color,
    isHighlighted: Boolean,
    dimmed: Boolean,
    textMeasurer: TextMeasurer
) {
    val alphaFactor = if (dimmed) 0.55f else 1f
    val fillColor = (if (token.isLibero) Color(0xFFFFA000) else teamColor).copy(alpha = alphaFactor)
    val effectiveRadius = if (isHighlighted) radius * 1.12f else radius

    if (isHighlighted) {
        drawCircle(
            color = VolleyYellow.copy(alpha = 0.28f),
            radius = effectiveRadius * 1.38f,
            center = center
        )
    }

    val isSetter = !token.isLibero && (token.originalRole == "SE" || token.originalRole == "SE1" || token.originalRole == "SE2")
    val ringColor = when {
        isHighlighted -> VolleyYellow
        token.isServer -> VolleyYellow.copy(alpha = alphaFactor)
        token.isLibero -> VolleyWhite.copy(alpha = alphaFactor)
        else -> VolleyWhite.copy(alpha = 0.85f * alphaFactor)
    }

    drawCircle(
        color = fillColor,
        radius = effectiveRadius,
        center = center
    )
    drawCircle(
        color = ringColor,
        radius = effectiveRadius,
        center = center,
        style = Stroke(
            width = when {
                isHighlighted -> 3.5.dp.toPx()
                isSetter -> 2.4.dp.toPx()
                token.isServer -> 2.dp.toPx()
                else -> 1.2.dp.toPx()
            },
            pathEffect = if (isSetter) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null
        )
    )

    val textColor = (if (token.isLibero) Color.Black else Color.White).copy(alpha = alphaFactor)
    val jerseyStr = "#${token.jerseyNumber}"
    val jerseyStyle = TextStyle(
        color = textColor,
        fontSize = (effectiveRadius * 0.42f).toSp(),
        fontWeight = FontWeight.Black
    )
    val roleStyle = TextStyle(
        color = textColor.copy(alpha = 0.92f * alphaFactor),
        fontSize = (effectiveRadius * 0.30f).toSp(),
        fontWeight = FontWeight.Bold
    )

    val jerseyLayout = textMeasurer.measure(jerseyStr, style = jerseyStyle)
    val roleLayout = textMeasurer.measure(token.roleLabel, style = roleStyle)

    val totalTextHeight = jerseyLayout.size.height + roleLayout.size.height
    val topY = center.y - totalTextHeight / 2f

    drawText(
        textLayoutResult = jerseyLayout,
        topLeft = Offset(
            x = center.x - jerseyLayout.size.width / 2f,
            y = topY
        )
    )
    drawText(
        textLayoutResult = roleLayout,
        topLeft = Offset(
            x = center.x - roleLayout.size.width / 2f,
            y = topY + jerseyLayout.size.height - 2f
        )
    )

    // Player Name pill right below the token circle (e.g. from TeamMixer or Kader setup)
    if (token.playerName.isNotBlank()) {
        val shortName = token.playerName.trim().let {
            if (it.length > 10) it.take(9) + "…" else it
        }
        val nameStyle = TextStyle(
            color = if (isHighlighted) VolleyYellow else VolleyWhite.copy(alpha = 0.94f * alphaFactor),
            fontSize = (effectiveRadius * 0.31f).toSp(),
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold
        )
        val nameLayout = textMeasurer.measure(shortName, style = nameStyle)
        val padH = 4.dp.toPx()
        val padV = 1.dp.toPx()
        val nw = nameLayout.size.width + padH * 2f
        val nh = nameLayout.size.height + padV * 2f
        val nx = center.x - nw / 2f
        val ny = center.y + effectiveRadius + 2.dp.toPx()
        drawRoundRect(
            color = Color(0xFF0A0D12).copy(alpha = 0.85f * alphaFactor),
            topLeft = Offset(nx, ny),
            size = Size(nw, nh),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawText(
            textLayoutResult = nameLayout,
            topLeft = Offset(nx + padH, ny + padV)
        )
    }

    // Zone pill ("P1".."P6" or target Pos) badge above highlighted player
    if (isHighlighted) {
        val zoneTag = if (token.inGameTargetZone != null) {
            "P${token.zoneNumber}→${token.inGameTargetZone}"
        } else {
            "P${token.zoneNumber}"
        }
        val badgeStyle = TextStyle(
            color = Color.Black,
            fontSize = (effectiveRadius * 0.31f).toSp(),
            fontWeight = FontWeight.Black
        )
        val badgeLayout = textMeasurer.measure(zoneTag, style = badgeStyle)
        val padH = 4.dp.toPx()
        val padV = 1.5.dp.toPx()
        val bw = badgeLayout.size.width + padH * 2f
        val bh = badgeLayout.size.height + padV * 2f
        val bx = center.x - bw / 2f
        val by = center.y - effectiveRadius - bh * 0.72f
        drawRoundRect(
            color = VolleyYellow,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawText(
            textLayoutResult = badgeLayout,
            topLeft = Offset(bx + padH, by + padV)
        )
    }
}
