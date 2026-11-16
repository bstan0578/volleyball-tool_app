package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.VolleyFormDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ScoreboardTeamStanding
import com.example.ui.viewmodel.VolleyViewModel
import kotlinx.coroutines.delay
import kotlin.math.ceil

private fun triggerScoreHaptic(view: android.view.View, haptic: HapticFeedback, strong: Boolean = false) {
    val didPerform = view.performHapticFeedback(
        if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
    )
    if (!didPerform) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreboardScreen(
    viewModel: VolleyViewModel,
    modifier: Modifier = Modifier
) {
    val currentMatch by viewModel.currentMatch.collectAsState()
    val teams by viewModel.allTeams.collectAsState()
    val scoreboardStandings by viewModel.scoreboardStandings.collectAsState()
    val rotationConfigA by viewModel.rotationConfigA.collectAsState()
    val rotationConfigB by viewModel.rotationConfigB.collectAsState()

    val timeoutPresetSeconds by viewModel.timeoutPresetSeconds.collectAsState()
    val activeTimeoutSide by viewModel.activeTimeoutSide.collectAsState()
    val activeTimeoutRemaining by viewModel.activeTimeoutRemaining.collectAsState()
    val timeoutExpiredNotice by viewModel.timeoutExpiredNotice.collectAsState()

    var showNewMatchDialog by remember { mutableStateOf(false) }
    var showManualServerDialog by remember { mutableStateOf<TeamSide?>(null) }
    var showFinishSetConfirmDialog by remember { mutableStateOf(false) }
    var showResetStandingsDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    // Live ticking timestamp for real-time set duration calculation
    var liveTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(currentMatch?.isTimerRunning) {
        if (currentMatch?.isTimerRunning == true) {
            while (true) {
                delay(1000)
                liveTimestamp = System.currentTimeMillis()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolleyBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Clean compact top action row
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Scoreboard",
                color = VolleyYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isLinked = currentMatch?.rotationLinked == true
                OutlinedButton(
                    onClick = { viewModel.openRotationSetupFromScoreboard() },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_set_rotation"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isLinked) VolleyYellow.copy(alpha = 0.14f) else VolleyDarkSurfaceVariant,
                        contentColor = VolleyYellow
                    ),
                    border = BorderStroke(if (isLinked) 1.5.dp else 1.dp, if (isLinked) VolleyYellow else VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = VolleyYellow)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isLinked) "Rotation ✓" else "Set Rotation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = { showNewMatchDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_new_match"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyYellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Neues Spiel", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        val match = currentMatch
        if (match == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VolleyGrayBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Kein aktives Spiel",
                            color = VolleyWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Starte ein neues Einzelspiel, verknüpfe bei Bedarf die Team-Rotation oder sieh dir die bisherigen Ergebnisse an.",
                            color = VolleyTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showNewMatchDialog = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_start_first_match"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VolleyYellow,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("Spiel starten", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                ScoreboardStandingsCard(
                    standings = scoreboardStandings,
                    onResetClick = { showResetStandingsDialog = true }
                )
            }
        } else {
            val matchId = match.id
            var selectedTimelineSet by remember(matchId, match.currentSet) { mutableIntStateOf(match.currentSet) }

            // Single scrollable container so short/landscape screens NEVER crush action buttons below scoreboard
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info banner for current set, net timer & timeout preset selector
                val elapsedNetSecs = remember(match, liveTimestamp) {
                    val start = match.setStartTime
                    if (start == null) 0L
                    else {
                        val currentOrEnd = if (match.status == MatchStatus.FINISHED) (match.setEndTime ?: liveTimestamp) else liveTimestamp
                        val totalElapsed = (currentOrEnd - start).coerceAtLeast(0L)
                        val timeoutElapsed = match.totalTimeoutMillis + (if (match.timeoutStartTime != null) (currentOrEnd - match.timeoutStartTime).coerceAtLeast(0L) else 0L)
                        ((totalElapsed - timeoutElapsed).coerceAtLeast(0L)) / 1000
                    }
                }
                val formattedTimer = String.format("%02d:%02d", elapsedNetSecs / 60, elapsedNetSecs % 60)

                Surface(
                    color = VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(end = 8.dp)) {
                            Text(
                                "Satz ${match.currentSet} (${match.matchConfig.setsMode.label}) · Ziel: ${match.matchConfig.pointsPerSet}" +
                                        (if (match.matchConfig.winByTwo) " (+2)" else ""),
                                color = VolleyYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Zeit: $formattedTimer Netto" +
                                        (if (match.timeoutStartTime != null) " · Auszeit" else if (!match.isTimerRunning && match.setStartTime != null) " · Pause" else ""),
                                color = if (match.timeoutStartTime != null) VolleyYellow else VolleyWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (match.status == MatchStatus.FINISHED) {
                            Surface(color = VolleyGreen, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "BEENDET",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (match.status == MatchStatus.PAUSED) {
                                    Surface(color = VolleyYellow, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            "PAUSIERT",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                // Timeout Duration Presets (20s / 30s / 60s)
                                Icon(Icons.Default.Timer, contentDescription = "Auszeitdauer", tint = VolleyTextSecondary, modifier = Modifier.size(14.dp))
                                listOf(20, 30, 60).forEach { secs ->
                                    val selected = timeoutPresetSeconds == secs
                                    Surface(
                                        onClick = { viewModel.setTimeoutPresetSeconds(secs) },
                                        color = if (selected) VolleyYellow else VolleyDarkSurface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (selected) VolleyYellow else VolleyGrayBorder),
                                        modifier = Modifier.testTag("btn_timeout_preset_$secs")
                                    ) {
                                        Text(
                                            "${secs}s",
                                            color = if (selected) Color.Black else VolleyWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Non-Blocking Timeout Countdown Banner (Points & Undo stay interactive!)
                if (activeTimeoutSide != null && activeTimeoutRemaining != null) {
                    val side = activeTimeoutSide!!
                    val rem = activeTimeoutRemaining!!
                    val sideColor = if (side == TeamSide.TEAM_A) VolleyRed else VolleyBlue
                    val teamLabel = if (side == TeamSide.TEAM_A) match.teamAName else match.teamBName
                    Surface(
                        color = sideColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.5.dp, sideColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("banner_active_timeout")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = sideColor, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        "AUSZEIT · $teamLabel",
                                        color = VolleyWhite,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        String.format("00:%02d · Punkte/Undo bleiben aktiv", rem),
                                        color = VolleyYellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.stopTimeoutCountdown() },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(40.dp).testTag("btn_stop_timeout"),
                                colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyWhite),
                                border = BorderStroke(1.dp, sideColor),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = sideColor)
                                Spacer(Modifier.width(4.dp))
                                Text("Beenden", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                } else if (match.status == MatchStatus.PAUSED) {
                    Surface(
                        color = VolleyYellow.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.5.dp, VolleyYellow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("banner_match_paused")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        "SPIELPAUSIERUNG AKTIV",
                                        color = VolleyYellow,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Spieluhr angehalten · Punkte/Fortsetzen aktiv",
                                        color = VolleyWhite,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    triggerScoreHaptic(view, haptic)
                                    viewModel.toggleMatchPause()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(40.dp).testTag("btn_resume_match_banner"),
                                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(4.dp))
                                Text("Fortsetzen", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                } else if (timeoutExpiredNotice != null) {
                    Surface(
                        color = VolleyYellow.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.5.dp, VolleyYellow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("banner_timeout_expired")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(18.dp))
                            Text(
                                timeoutExpiredNotice!!,
                                color = VolleyYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Primary scoreboard panels: Team 1 = Red (TEAM_A), Team 2 = Blue (TEAM_B)
                val serverInfoA = remember(rotationConfigA, match.servingPlayerIndexA, match.matchConfig.fieldFormatA) {
                    RotationEngine.resolveServerInfo(
                        config = rotationConfigA,
                        serverIndex = match.servingPlayerIndexA,
                        playerCountOverride = match.matchConfig.fieldFormatA.fieldPlayerCount
                    )
                }
                val serverInfoB = remember(rotationConfigB, match.servingPlayerIndexB, match.matchConfig.fieldFormatB) {
                    RotationEngine.resolveServerInfo(
                        config = rotationConfigB,
                        serverIndex = match.servingPlayerIndexB,
                        playerCountOverride = match.matchConfig.fieldFormatB.fieldPlayerCount
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScoreTeamPanel(
                        teamName = match.teamAName,
                        teamColor = VolleyRed,
                        score = match.scoreA,
                        isServing = match.servingTeam == TeamSide.TEAM_A,
                        serverIndex = match.servingPlayerIndexA,
                        serverInfo = serverInfoA,
                        fieldFormat = match.matchConfig.fieldFormatA,
                        timeouts = match.timeoutsA,
                        rotationLinked = match.rotationLinked,
                        rotationStep = match.rotationStepA,
                        modifier = Modifier.weight(1f),
                        onAddPoint = {
                            triggerScoreHaptic(view, haptic)
                            viewModel.scorePoint(TeamSide.TEAM_A)
                        },
                        onTimeout = {
                            triggerScoreHaptic(view, haptic, strong = true)
                            viewModel.recordTimeout(TeamSide.TEAM_A)
                        },
                        onServerClick = { showManualServerDialog = TeamSide.TEAM_A },
                        testTagPrefix = "team_a"
                    )

                    ScoreTeamPanel(
                        teamName = match.teamBName,
                        teamColor = VolleyBlue,
                        score = match.scoreB,
                        isServing = match.servingTeam == TeamSide.TEAM_B,
                        serverIndex = match.servingPlayerIndexB,
                        serverInfo = serverInfoB,
                        fieldFormat = match.matchConfig.fieldFormatB,
                        timeouts = match.timeoutsB,
                        rotationLinked = match.rotationLinked,
                        rotationStep = match.rotationStepB,
                        modifier = Modifier.weight(1f),
                        onAddPoint = {
                            triggerScoreHaptic(view, haptic)
                            viewModel.scorePoint(TeamSide.TEAM_B)
                        },
                        onTimeout = {
                            triggerScoreHaptic(view, haptic, strong = true)
                            viewModel.recordTimeout(TeamSide.TEAM_B)
                        },
                        onServerClick = { showManualServerDialog = TeamSide.TEAM_B },
                        testTagPrefix = "team_b"
                    )
                }

                // RESULT OF COMPLETED SETS - CLICKABLE TO INSPECT TIMELINE FOR THAT SET
                if (match.completedSets.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VolleyYellowDark, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Beendete Sätze (antippen für Timeline):",
                                color = VolleyYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                match.completedSets.forEach { res ->
                                    val timeLabel = if (res.timeInfo != null) " (${res.timeInfo.formattedNetTime})" else ""
                                    val isTimelineSelected = selectedTimelineSet == res.setNumber
                                    Surface(
                                        onClick = {
                                            triggerScoreHaptic(view, haptic)
                                            selectedTimelineSet = res.setNumber
                                        },
                                        color = if (isTimelineSelected) VolleyYellow.copy(alpha = 0.16f) else VolleyDarkSurfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(if (isTimelineSelected) 1.5.dp else 1.dp, if (isTimelineSelected) VolleyYellow else VolleyGrayBorder),
                                        modifier = Modifier.testTag("chip_completed_set_${res.setNumber}")
                                    ) {
                                        Text(
                                            "Satz ${res.setNumber}: ${match.teamAName} ${res.scoreA} : ${res.scoreB} ${match.teamBName}$timeLabel",
                                            color = if (isTimelineSelected) VolleyYellow else VolleyWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // In-match action controls (Undo ALWAYS usable, even during timeout!)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            triggerScoreHaptic(view, haptic)
                            viewModel.undoScore()
                        },
                        enabled = match.undoHistory.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 100.dp)
                            .height(48.dp)
                            .testTag("btn_undo_score"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VolleyDarkSurfaceVariant,
                            contentColor = if (match.undoHistory.isNotEmpty()) VolleyYellow else VolleyGrayBorder
                        ),
                        border = BorderStroke(1.dp, VolleyGrayBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Rückgängig", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Undo (${match.undoHistory.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            triggerScoreHaptic(view, haptic)
                            showFinishSetConfirmDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 106.dp)
                            .height(48.dp)
                            .testTag("btn_finish_set"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VolleyDarkSurfaceVariant,
                            contentColor = VolleyWhite
                        ),
                        border = BorderStroke(1.dp, VolleyGrayBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Satz-Ende", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    if (match.status != MatchStatus.FINISHED) {
                        val isPaused = match.status == MatchStatus.PAUSED
                        Button(
                            onClick = {
                                triggerScoreHaptic(view, haptic, strong = true)
                                viewModel.toggleMatchPause()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 100.dp)
                                .height(48.dp)
                                .testTag("btn_pause_match"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) VolleyYellow else VolleyDarkSurfaceVariant,
                                contentColor = if (isPaused) Color.Black else VolleyYellow
                            ),
                            border = BorderStroke(1.dp, if (isPaused) VolleyYellow else VolleyGrayBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Spiel fortsetzen" else "Spiel pausieren",
                                tint = if (isPaused) Color.Black else VolleyYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isPaused) "Fortsetzen" else "Pause",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = {
                                triggerScoreHaptic(view, haptic, strong = true)
                                viewModel.forceFinishMatch()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 112.dp)
                                .height(48.dp)
                                .testTag("btn_finish_match"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VolleyDarkSurfaceVariant,
                                contentColor = VolleyRed
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = VolleyRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Match-Ende", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Spielverlauf-Timeline in Red (Team 1) & Blue (Team 2) with net-time X-axis (20 min default, expands dynamically)
                MatchTimelineCard(
                    match = match,
                    selectedSet = selectedTimelineSet,
                    currentSetNetSeconds = elapsedNetSecs,
                    onSelectSet = { selectedTimelineSet = it }
                )

                // Persistent Standings Table
                ScoreboardStandingsCard(
                    standings = scoreboardStandings,
                    onResetClick = { showResetStandingsDialog = true }
                )
            }
        }
    }

    // New Match Dialog
    if (showNewMatchDialog) {
        NewMatchConfigDialog(
            availableTeams = teams,
            onDismiss = { showNewMatchDialog = false },
            onConfirm = { nameA, nameB, formatA, formatB, setsMode, points, winByTwo, timeLimit ->
                viewModel.createNewMatch(
                    teamAName = nameA,
                    teamBName = nameB,
                    fieldFormatA = formatA,
                    fieldFormatB = formatB,
                    setsMode = setsMode,
                    pointsPerSet = points,
                    winByTwo = winByTwo,
                    timeLimitMinutes = timeLimit
                )
                showNewMatchDialog = false
            }
        )
    }

    // Manual Server Dialog
    showManualServerDialog?.let { side ->
        val m = currentMatch
        if (m != null) {
            val fieldCount = if (side == TeamSide.TEAM_A) m.matchConfig.fieldFormatA.fieldPlayerCount else m.matchConfig.fieldFormatB.fieldPlayerCount
            val currentIndex = if (side == TeamSide.TEAM_A) m.servingPlayerIndexA else m.servingPlayerIndexB
            val teamName = if (side == TeamSide.TEAM_A) m.teamAName else m.teamBName

            VolleyFormDialog(
                onDismissRequest = { showManualServerDialog = null },
                title = {
                    Text(
                        "Aufschläger wechseln ($teamName)",
                        color = VolleyYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = { showManualServerDialog = null },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                        border = BorderStroke(1.dp, VolleyGrayBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Abbrechen")
                    }
                }
            ) {
                val rotCfgForSide = if (side == TeamSide.TEAM_A) rotationConfigA else rotationConfigB
                Text(
                    "Wähle den aktuellen Aufschläger unter den $fieldCount Feldspielern:",
                    color = VolleyWhite,
                    fontSize = 14.sp
                )
                (0 until fieldCount).forEach { idx ->
                    val info = RotationEngine.resolveServerInfo(rotCfgForSide, idx, fieldCount)
                    val label = if (info.playerName.isNotBlank()) {
                        "P${idx + 1} · ${info.roleKey} (#${info.jerseyNumber}): ${info.playerName}"
                    } else {
                        "Spieler Position ${idx + 1} (${info.roleKey} · #${info.jerseyNumber})"
                    }
                    Button(
                        onClick = {
                            viewModel.manualChangeServer(side, idx)
                            showManualServerDialog = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_select_server_$idx"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentIndex == idx) VolleyYellow else VolleyDarkSurfaceVariant,
                            contentColor = if (currentIndex == idx) Color.Black else VolleyWhite
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            label + (if (currentIndex == idx) " (Aktuell)" else ""),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // Confirm Set Finish Dialog
    if (showFinishSetConfirmDialog) {
        val m = currentMatch
        AlertDialog(
            onDismissRequest = { showFinishSetConfirmDialog = false },
            containerColor = VolleyDarkSurface,
            title = {
                Text("Satz beenden?", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Möchtest du Satz ${m?.currentSet ?: 1} mit dem Spielstand ${m?.teamAName} ${m?.scoreA} : ${m?.scoreB} ${m?.teamBName} beenden?",
                    color = VolleyWhite,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishCurrentSet()
                        showFinishSetConfirmDialog = false
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyYellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Satz beenden", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showFinishSetConfirmDialog = false },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Reset Standings Confirm Dialog
    if (showResetStandingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetStandingsDialog = false },
            containerColor = VolleyDarkSurface,
            title = {
                Text("Ergebnisliste zurücksetzen?", color = VolleyRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Möchtest du die Rangliste aller beendeten Einzelspiele unwiderruflich löschen?",
                    color = VolleyWhite,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetScoreboardResults()
                        showResetStandingsDialog = false
                    },
                    modifier = Modifier.height(48.dp).testTag("btn_confirm_reset_standings"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyRed,
                        contentColor = VolleyWhite
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Löschen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetStandingsDialog = false },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun ScoreTeamPanel(
    teamName: String,
    teamColor: Color,
    score: Int,
    isServing: Boolean,
    serverIndex: Int,
    serverInfo: ServerDisplayInfo,
    fieldFormat: FieldFormat,
    timeouts: Int,
    rotationLinked: Boolean,
    rotationStep: Int,
    modifier: Modifier = Modifier,
    onAddPoint: () -> Unit,
    onTimeout: () -> Unit,
    onServerClick: () -> Unit,
    testTagPrefix: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(
                if (isServing) 2.5.dp else 1.dp,
                if (isServing) teamColor else teamColor.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color bar + Team Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(teamColor, CircleShape)
                )
                Text(
                    teamName,
                    color = VolleyWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))

            // Serve & Rotation indicator badge with player name (Clickable to manually select server)
            val rotText = if (rotationLinked) " · Rot${rotationStep + 1}" else ""
            val hasName = serverInfo.playerName.isNotBlank()
            if (isServing) {
                Surface(
                    color = VolleyYellow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable { onServerClick() }
                        .testTag("${testTagPrefix}_server_badge")
                ) {
                    Text(
                        text = if (hasName) {
                            "AUFSCHLAG: ${serverInfo.playerName} (#${serverInfo.jerseyNumber})"
                        } else {
                            "AUFSCHLAG: P${serverIndex + 1}/${fieldFormat.fieldPlayerCount} · ${serverInfo.roleKey}$rotText"
                        },
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Surface(
                    color = VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable { onServerClick() }
                        .testTag("${testTagPrefix}_server_badge")
                ) {
                    Text(
                        text = if (hasName) {
                            "Nächster: ${serverInfo.playerName} (P${serverIndex + 1})"
                        } else {
                            "Annahme (P${serverIndex + 1} · ${serverInfo.roleKey})$rotText"
                        },
                        color = VolleyTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // HUGE POINT DIGIT IN TEAM COLOR (Clickable to score!)
            Surface(
                color = teamColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clickable { onAddPoint() }
                    .testTag("${testTagPrefix}_score_display"),
                border = BorderStroke(1.5.dp, teamColor.copy(alpha = 0.7f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$score",
                        color = teamColor,
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Direct +1 Point Button in team color
            Button(
                onClick = onAddPoint,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_${testTagPrefix}_add_point"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = teamColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Punkt erhöhen", tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "+1 Punkt",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(Modifier.height(6.dp))

            // Timeout Button
            OutlinedButton(
                onClick = onTimeout,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_${testTagPrefix}_timeout"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = VolleyDarkSurfaceVariant,
                    contentColor = VolleyWhite
                ),
                border = BorderStroke(1.dp, teamColor.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Pause, contentDescription = "Auszeit nehmen", tint = teamColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Timeout ($timeouts)", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Spielverlauf-Timeline in team colors (Team 1 = Red, Team 2 = Blue)
 * with net playing time on X-axis (20 min default, expanding dynamically; paused on timeout)
 * and semi-transparent vertical bars marking every timeout.
 */
@Composable
fun MatchTimelineCard(
    match: Match,
    selectedSet: Int,
    currentSetNetSeconds: Long,
    onSelectSet: (Int) -> Unit
) {
    val availableSets = remember(match.events, match.completedSets, match.currentSet) {
        val setNums = match.events.map { it.setNumber }.toMutableSet()
        match.completedSets.forEach { setNums.add(it.setNumber) }
        setNums.add(match.currentSet)
        setNums.sorted()
    }

    val setEvents = remember(match.events, selectedSet) {
        match.events.filter { it.setNumber == selectedSet }
    }
    val timeoutsInSet = remember(setEvents) {
        setEvents.filter { it.type == MatchEventType.TIMEOUT }
    }

    val completedSetSecs = remember(match.completedSets, selectedSet) {
        match.completedSets.firstOrNull { it.setNumber == selectedSet }?.timeInfo?.netSeconds ?: 0L
    }
    val observedMaxSecs = remember(setEvents, selectedSet, match.currentSet, currentSetNetSeconds, completedSetSecs) {
        val maxEv = setEvents.maxOfOrNull { it.elapsedNetSeconds } ?: 0L
        val activeDur = if (selectedSet == match.currentSet) currentSetNetSeconds else completedSetSecs
        maxOf(maxEv, activeDur)
    }
    // Always scale X-axis to 100% of chart width using actual observed set duration (never squished to left)
    val axisMaxSecs = remember(observedMaxSecs) {
        maxOf(observedMaxSecs, 1L).toFloat()
    }
    val durationLabel = remember(observedMaxSecs) {
        val m = observedMaxSecs / 60L
        val s = observedMaxSecs % 60L
        String.format("%02d:%02d", m, s)
    }
    val pointEvents = remember(setEvents) {
        setEvents.filter { it.type == MatchEventType.POINT }
    }
    val textMeasurer = rememberTextMeasurer()

    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VolleyGrayBorder, RoundedCornerShape(12.dp))
            .testTag("card_match_timeline")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Spielverlauf-Timeline (Satz $selectedSet)",
                        color = VolleyYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "X-Achse: Volle Breite ($durationLabel Netto · Starke 5-Min-Phase markiert)",
                        color = VolleyTextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (availableSets.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableSets.forEach { sNum ->
                            Surface(
                                onClick = { onSelectSet(sNum) },
                                color = if (selectedSet == sNum) VolleyYellow else VolleyDarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, VolleyGrayBorder)
                            ) {
                                Text(
                                    "S$sNum",
                                    color = if (selectedSet == sNum) Color.Black else VolleyWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(VolleyRed, CircleShape))
                    Text("${match.teamAName} (Rot)", color = VolleyWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(VolleyBlue, CircleShape))
                    Text("${match.teamBName} (Blau)", color = VolleyWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.width(8.dp).height(12.dp).background(VolleyWhite.copy(alpha = 0.35f)))
                    Text("Timeout", color = VolleyTextSecondary, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.width(10.dp).height(4.dp).background(VolleyRed))
                    Box(modifier = Modifier.width(10.dp).height(4.dp).background(VolleyBlue))
                    Text("Starke 5m-Phase", color = VolleyTextSecondary, fontSize = 11.sp)
                }
            }

            // Time-Scaled Full-Width Timeline Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
                    .background(Color(0xFF0E1014), RoundedCornerShape(8.dp))
                    .border(1.dp, VolleyGrayBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (setEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Noch keine Punkte oder Timeouts in Satz $selectedSet",
                            color = VolleyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val labelBandH = 18.dp.toPx()
                        val chartH = (h - labelBandH).coerceAtLeast(30f)

                        val maxScore = maxOf(
                            match.matchConfig.pointsPerSet.coerceAtLeast(10),
                            setEvents.maxOfOrNull { maxOf(it.scoreA, it.scoreB) } ?: 10
                        ).toFloat()

                        val gridStroke = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                            val gy = chartH * frac
                            drawLine(
                                color = VolleyGrayBorder.copy(alpha = 0.3f),
                                start = Offset(0f, gy),
                                end = Offset(w, gy),
                                strokeWidth = 1.5f,
                                pathEffect = gridStroke
                            )
                        }

                        // Dynamic full-width vertical time grid lines & labels
                        val tickLabelStyle = TextStyle(color = Color(0xFFA0A8B8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        val totalSecsInt = axisMaxSecs.toInt().coerceAtLeast(1)
                        val tickStepSecs = when {
                            totalSecsInt <= 90 -> 30
                            totalSecsInt <= 240 -> 60
                            totalSecsInt <= 600 -> 120
                            totalSecsInt <= 1500 -> 300
                            else -> 600
                        }
                        var tickSec = 0
                        while (tickSec <= totalSecsInt) {
                            val tx = (tickSec.toFloat() / axisMaxSecs).coerceIn(0f, 1f) * w
                            drawLine(
                                color = VolleyGrayBorder.copy(alpha = if (tickSec == 0) 0.55f else 0.25f),
                                start = Offset(tx, 0f),
                                end = Offset(tx, chartH),
                                strokeWidth = 1.5f,
                                pathEffect = if (tickSec == 0) null else gridStroke
                            )
                            val lbl = if (tickStepSecs < 60) {
                                String.format("%d:%02d", tickSec / 60, tickSec % 60)
                            } else {
                                "${tickSec / 60}'"
                            }
                            val layout = textMeasurer.measure(lbl, style = tickLabelStyle)
                            val lx = when {
                                tickSec == 0 -> 0f
                                tx + layout.size.width / 2f > w -> (w - layout.size.width).coerceAtLeast(0f)
                                else -> tx - layout.size.width / 2f
                            }
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(lx, chartH + 3.dp.toPx())
                            )
                            tickSec += tickStepSecs
                        }
                        // Right-edge exact end-time tick label if enough space from last regular tick
                        val lastTickDrawn = ((totalSecsInt / tickStepSecs) * tickStepSecs)
                        if (totalSecsInt - lastTickDrawn >= (tickStepSecs / 3)) {
                            val endLbl = String.format("%d:%02d", totalSecsInt / 60, totalSecsInt % 60)
                            val endLayout = textMeasurer.measure(endLbl, style = tickLabelStyle)
                            drawText(
                                textLayoutResult = endLayout,
                                topLeft = Offset((w - endLayout.size.width).coerceAtLeast(0f), chartH + 3.dp.toPx())
                            )
                        }

                        // Neutral X-axis baseline
                        drawLine(
                            color = VolleyGrayBorder.copy(alpha = 0.55f),
                            start = Offset(0f, chartH),
                            end = Offset(w, chartH),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Strong-phase X-axis highlighting (rolling trailing 5-minute = 300s window, integer logic)
                        val segments = 72
                        val segWidth = w / segments.toFloat()
                        val glowBandHeight = 8.dp.toPx()
                        for (seg in 0 until segments) {
                            val windowEndSec = ((seg + 1L) * totalSecsInt) / segments
                            val windowStartSec = (windowEndSec - 300L).coerceAtLeast(0L)
                            var ptsA = 0
                            var ptsB = 0
                            for (ev in pointEvents) {
                                if (ev.elapsedNetSeconds in windowStartSec..windowEndSec) {
                                    if (ev.teamSide == TeamSide.TEAM_A) ptsA++ else ptsB++
                                }
                            }
                            val windowTotal = ptsA + ptsB
                            val phaseColor = when {
                                windowTotal >= 2 && (ptsA - ptsB) >= 2 && ptsA * 10 >= windowTotal * 6 -> VolleyRed
                                windowTotal >= 2 && (ptsB - ptsA) >= 2 && ptsB * 10 >= windowTotal * 6 -> VolleyBlue
                                else -> null
                            }
                            if (phaseColor != null) {
                                val sx = seg * segWidth
                                val ex = sx + segWidth + 0.5f
                                drawRect(
                                    color = phaseColor.copy(alpha = 0.22f),
                                    topLeft = Offset(sx, (chartH - glowBandHeight).coerceAtLeast(0f)),
                                    size = Size(ex - sx, glowBandHeight)
                                )
                                drawLine(
                                    color = phaseColor,
                                    start = Offset(sx, chartH),
                                    end = Offset(ex, chartH),
                                    strokeWidth = 4.5.dp.toPx()
                                )
                            }
                        }

                        fun xForTime(sec: Long): Float {
                            if (observedMaxSecs == 0L && setEvents.size > 1) {
                                val idx = setEvents.indexOfFirst { it.elapsedNetSeconds == sec }.coerceAtLeast(0)
                                return (idx.toFloat() / (setEvents.size - 1).coerceAtLeast(1)) * w
                            }
                            return ((sec.toFloat() / axisMaxSecs).coerceIn(0f, 1f)) * w
                        }
                        fun yForScore(sc: Int): Float = chartH - (sc.toFloat() / maxScore) * (chartH - 8f) - 2f

                        // 1. Draw semi-transparent vertical bars for all TIMEOUT events at their net time
                        val barWidth = 10.dp.toPx()
                        setEvents.forEach { ev ->
                            if (ev.type == MatchEventType.TIMEOUT) {
                                val cx = xForTime(ev.elapsedNetSeconds)
                                val barColor = if (ev.teamSide == TeamSide.TEAM_A) VolleyRed else VolleyBlue
                                val barLeft = (cx - barWidth / 2f).coerceIn(0f, (w - barWidth).coerceAtLeast(0f))
                                drawRect(
                                    color = barColor.copy(alpha = 0.35f),
                                    topLeft = Offset(barLeft, 0f),
                                    size = Size(barWidth, chartH)
                                )
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(barLeft, 0f),
                                    size = Size(barWidth, 5f)
                                )
                            }
                        }

                        // 2. Build progression paths for Team 1 (Red) and Team 2 (Blue) from (0s, 0)
                        val pathA = Path().apply { moveTo(xForTime(0L), yForScore(0)) }
                        val pathB = Path().apply { moveTo(xForTime(0L), yForScore(0)) }

                        setEvents.forEach { ev ->
                            val x = xForTime(ev.elapsedNetSeconds)
                            pathA.lineTo(x, yForScore(ev.scoreA))
                            pathB.lineTo(x, yForScore(ev.scoreB))
                        }

                        drawPath(
                            path = pathB,
                            color = VolleyBlue,
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = pathA,
                            color = VolleyRed,
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // 3. Point event dots
                        setEvents.forEach { ev ->
                            if (ev.type == MatchEventType.POINT) {
                                val x = xForTime(ev.elapsedNetSeconds)
                                val y = yForScore(if (ev.teamSide == TeamSide.TEAM_A) ev.scoreA else ev.scoreB)
                                val dotColor = if (ev.teamSide == TeamSide.TEAM_A) VolleyRed else VolleyBlue
                                drawCircle(color = dotColor, radius = 4.5f, center = Offset(x, y))
                            }
                        }
                    }
                }
            }

            // Documented Timeout Log Entries
            if (timeoutsInSet.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timeoutsInSet.forEach { to ->
                        val sideColor = if (to.teamSide == TeamSide.TEAM_A) VolleyRed else VolleyBlue
                        val name = if (to.teamSide == TeamSide.TEAM_A) match.teamAName else match.teamBName
                        val mins = to.elapsedNetSeconds / 60
                        val secs = to.elapsedNetSeconds % 60
                        val timeStr = String.format("%02d:%02d", mins, secs)
                        Surface(
                            color = sideColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, sideColor.copy(alpha = 0.7f))
                        ) {
                            Text(
                                "TO $name · ${to.scoreA}:${to.scoreB} ($timeStr)",
                                color = VolleyWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreboardStandingsCard(
    standings: List<ScoreboardTeamStanding>,
    onResetClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VolleyGrayBorder, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Rangliste beendeter Einzelspiele",
                    color = VolleyYellow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (standings.isEmpty()) {
                Text(
                    "Noch keine beendeten Einzelspiele aufgezeichnet.",
                    color = VolleyTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val hScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScrollState)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .background(VolleyDarkSurfaceVariant, RoundedCornerShape(4.dp))
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rang", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                            Text("Teamname", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                            Text("Spiele gew.", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                            Text("Spiele verl.", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                            Text("Satzverhältnis", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
                            Text("gew. Pkt", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                            Text("verl. Pkt", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                            Text("Punktdiff.", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                        }

                        standings.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                    .border(0.5.dp, VolleyGrayBorder, RoundedCornerShape(2.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${row.rank}", color = VolleyWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                                Text(row.teamName, color = VolleyWhite, fontSize = 14.sp, modifier = Modifier.width(130.dp), maxLines = 1)
                                Text("${row.wins}", color = VolleyGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                                Text("${row.losses}", color = VolleyRed, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                                Text("${row.setsWon} : ${row.setsLost}", color = VolleyWhite, fontSize = 14.sp, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
                                Text("${row.pointsFor}", color = VolleyWhite, fontSize = 14.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                                Text("${row.pointsAgainst}", color = VolleyWhite, fontSize = 14.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                                val diffStr = if (row.pointsDiff > 0) "+${row.pointsDiff}" else "${row.pointsDiff}"
                                Text(diffStr, color = if (row.pointsDiff >= 0) VolleyGreen else VolleyRed, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onResetClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_reset_standings"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyDarkSurfaceVariant,
                        contentColor = VolleyRed
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = VolleyRed)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Ergebnisliste zurücksetzen",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMatchConfigDialog(
    availableTeams: List<Team>,
    onDismiss: () -> Unit,
    onConfirm: (
        nameA: String,
        nameB: String,
        formatA: FieldFormat,
        formatB: FieldFormat,
        setsMode: SetsMode,
        points: Int,
        winByTwo: Boolean,
        timeLimit: Int?
    ) -> Unit
) {
    var teamAName by remember { mutableStateOf(if (availableTeams.isNotEmpty()) availableTeams[0].name else "Team Rot") }
    var teamBName by remember { mutableStateOf(if (availableTeams.size > 1) availableTeams[1].name else "Team Blau") }

    var formatA by remember { mutableStateOf(FieldFormat.FORMAT_6V6) }
    var formatB by remember { mutableStateOf(FieldFormat.FORMAT_6V6) }
    var setsMode by remember { mutableStateOf(SetsMode.BEST_OF_3) }
    var pointsPerSet by remember { mutableIntStateOf(25) }
    var winByTwo by remember { mutableStateOf(true) }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Neues Spiel starten", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        teamAName,
                        teamBName,
                        formatA,
                        formatB,
                        setsMode,
                        pointsPerSet,
                        winByTwo,
                        null
                    )
                },
                modifier = Modifier.height(48.dp).testTag("btn_confirm_new_match"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VolleyYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Spiel starten", fontWeight = FontWeight.Bold)
            }
        }
    ) {
                OutlinedTextField(
                    value = teamAName,
                    onValueChange = { teamAName = it },
                    label = { Text("Team 1 (Rot)", color = VolleyRed) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VolleyWhite,
                        unfocusedTextColor = VolleyWhite,
                        focusedBorderColor = VolleyRed,
                        unfocusedBorderColor = VolleyRed.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_team_a_name")
                )

                OutlinedTextField(
                    value = teamBName,
                    onValueChange = { teamBName = it },
                    label = { Text("Team 2 (Blau)", color = VolleyBlue) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VolleyWhite,
                        unfocusedTextColor = VolleyWhite,
                        focusedBorderColor = VolleyBlue,
                        unfocusedBorderColor = VolleyBlue.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_team_b_name")
                )

                Text("Feldformat:", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(FieldFormat.FORMAT_2V2, FieldFormat.FORMAT_4V4, FieldFormat.FORMAT_6V6).forEach { fmt ->
                        Button(
                            onClick = {
                                formatA = fmt
                                formatB = fmt
                                pointsPerSet = if (fmt == FieldFormat.FORMAT_6V6) 25 else 21
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).widthIn(min = 84.dp).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (formatA == fmt) VolleyYellow else VolleyDarkSurfaceVariant,
                                contentColor = if (formatA == fmt) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(fmt.label, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Text("Satzmodus:", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        SetsMode.ONE_SET to "1 Satz",
                        SetsMode.BEST_OF_2 to "2 Sätze",
                        SetsMode.BEST_OF_3 to "2 Gew.",
                        SetsMode.BEST_OF_5 to "3 Gew."
                    ).forEach { (mode, lbl) ->
                        Button(
                            onClick = { setsMode = mode },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.weight(1f).widthIn(min = 72.dp).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (setsMode == mode) VolleyYellow else VolleyDarkSurfaceVariant,
                                contentColor = if (setsMode == mode) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(lbl, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Text("Zielpunkte pro Satz: $pointsPerSet", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15, 21, 25).forEach { pts ->
                        Button(
                            onClick = { pointsPerSet = pts },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).widthIn(min = 76.dp).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pointsPerSet == pts) VolleyYellow else VolleyDarkSurfaceVariant,
                                contentColor = if (pointsPerSet == pts) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$pts", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zwei Punkte Vorsprung:", color = VolleyWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = winByTwo,
                        onCheckedChange = { winByTwo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VolleyYellow,
                            checkedTrackColor = VolleyDarkSurfaceVariant
                        )
                    )
                }
    }
}
