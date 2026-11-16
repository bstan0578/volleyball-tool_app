package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Tournament
import com.example.domain.tournament.*
import com.example.ui.components.VolleyFormDialog
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecScheduleControlsCard(
    tournament: Tournament,
    fairnessReport: RefereeFairnessReport,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSetLiveDelay: (Int) -> Unit,
    onOptimizeSchedule: () -> Unit,
    onOpenAuditLog: () -> Unit,
    onEditSettings: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(18.dp))
                    Text(
                        "Zeitplan & Schiedsrichter",
                        color = VolleyYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    "SR-Spread: ${fairnessReport.minCount}–${fairnessReport.maxCount}",
                    color = if (fairnessReport.spread <= 1) VolleyGreen else VolleyTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Live-Delay & Optimization Controls
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val delay = tournament.specConfig.liveDelayMinutes
                Surface(
                    color = if (delay > 0) VolleyYellow.copy(alpha = 0.2f) else VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (delay > 0) VolleyYellow else VolleyGrayBorder)
                ) {
                    Text(
                        text = if (delay > 0) "Verzug: +$delay Min" else "Im Zeitplan",
                        color = if (delay > 0) VolleyYellow else VolleyWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }

                OutlinedButton(
                    onClick = { onSetLiveDelay(delay + 5) },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_delay_plus_5"),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("+5m", color = VolleyWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onSetLiveDelay(delay + 15) },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_delay_plus_15"),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("+15m", color = VolleyWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (delay > 0) {
                    OutlinedButton(
                        onClick = { onSetLiveDelay(0) },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(40.dp).testTag("btn_delay_reset"),
                        border = BorderStroke(1.dp, VolleyRed),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Reset", color = VolleyRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onOptimizeSchedule,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_optimize_schedule"),
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
                    border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = VolleyYellow)
                    Spacer(Modifier.width(4.dp))
                    Text("Plan/SR optimieren", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, if (canUndo) VolleyGrayBorder else VolleyGrayBorder.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .testTag("btn_tournament_undo")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Rückgängig",
                        tint = if (canUndo) VolleyYellow else VolleyTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, if (canRedo) VolleyGrayBorder else VolleyGrayBorder.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .testTag("btn_tournament_redo")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Wiederholen",
                        tint = if (canRedo) VolleyYellow else VolleyTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = onOpenAuditLog,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_tournament_audit_log"),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = VolleyWhite)
                    Spacer(Modifier.width(4.dp))
                    Text("Log (${tournament.specConfig.auditLog.size})", color = VolleyWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (onEditSettings != null) {
                    OutlinedButton(
                        onClick = onEditSettings,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(40.dp).testTag("btn_open_tournament_settings"),
                        border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Regeln & Felder bearbeiten", modifier = Modifier.size(16.dp), tint = VolleyYellow)
                        Spacer(Modifier.width(4.dp))
                        Text("Regeln / Felder", color = VolleyYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
fun SpecWarningsBanner(warnings: List<TournamentWarning>) {
    if (warnings.isEmpty()) return
    Surface(
        color = VolleyYellow.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(16.dp))
                Text("Hinweise (${warnings.size})", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            warnings.take(4).forEach { w ->
                Text(
                    "• [${w.specId}] ${w.message}",
                    color = if (w.isError) VolleyRed else VolleyWhite,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SpecStandingsTableCard(
    groupName: String,
    groupKey: String,
    rows: List<SpecStandingRow>,
    scoringSystem: ScoringSystem,
    onResolveTie: (tiedTeamIds: List<String>) -> Unit
) {
    val unresolvedRows = remember(rows) { rows.filter { it.decisionRequired } }
    val isProvisional = remember(rows) { rows.any { it.isProvisional } }

    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (unresolvedRows.isNotEmpty()) VolleyYellow else VolleyGrayBorder, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    groupName,
                    color = VolleyYellow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    scoringSystem.label,
                    color = VolleyTextSecondary,
                    fontSize = 11.sp
                )
            }

            if (isProvisional) {
                Text(
                    "Vorläufige Wertung (ungleiche Anzahl gespielter Spiele).",
                    color = VolleyTextSecondary,
                    fontSize = 11.sp
                )
            }

            if (unresolvedRows.isNotEmpty()) {
                Surface(
                    color = VolleyRed.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, VolleyRed.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Gleichstand („Entscheidung erf.“) – K.-o./Platzierung blockiert!",
                            color = VolleyRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onResolveTie(unresolvedRows.map { it.teamId }) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(40.dp).testTag("btn_resolve_tie_$groupKey"),
                            colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Entscheidung erf.", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .background(VolleyDarkSurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                        Text("Team", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(126.dp))
                        Text("Sp", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                        Text("MP", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
                        Text("S:N", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                        Text("Sätze", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                        Text("Bälle", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                        Text("Kriterium", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(116.dp))
                    }

                    rows.forEach { r ->
                        Row(
                            modifier = Modifier
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#${r.rank}", color = if (r.rank <= 2) VolleyYellow else VolleyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                            Text(r.teamName, color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(126.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.matchesPlayed}", color = VolleyWhite, fontSize = 13.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                            Text("${r.matchPoints}", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
                            Text("${r.wins}:${r.losses}", color = VolleyWhite, fontSize = 12.sp, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                            Text("${r.setsWon}:${r.setsLost}", color = VolleyWhite, fontSize = 12.sp, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                            Text("${r.pointsFor}:${r.pointsAgainst}", color = VolleyTextSecondary, fontSize = 12.sp, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                            Text(
                                text = if (r.decisionRequired) "Entscheidung erf." else r.decidedBy,
                                color = if (r.decisionRequired) VolleyRed else VolleyGreen,
                                fontSize = 11.sp,
                                fontWeight = if (r.decisionRequired) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(116.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(color = VolleyGrayBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpecTeamStatsCard(stats: List<DerivedTeamStats>) {
    if (stats.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Abgeleitete Team-Statistiken (Pausen, B2B, Ø Pkt/Satz, SR)",
                color = VolleyYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .background(VolleyDarkSurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Team", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(126.dp))
                        Text("Serie", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                        Text("Ø/Satz", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
                        Text("Pause", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.Center)
                        Text("B2B", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        Text("SR", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        Text("BHZ", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                    }
                    stats.forEach { s ->
                        Row(
                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.teamName, color = VolleyWhite, fontSize = 12.sp, modifier = Modifier.width(126.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.longestWinStreak}S", color = VolleyGreen, fontSize = 12.sp, modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                            Text(s.avgPointsPerSet, color = VolleyWhite, fontSize = 12.sp, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
                            Text("${s.minRestSlots}/${s.avgRestSlotsX10 / 10}.${s.avgRestSlotsX10 % 10}", color = VolleyWhite, fontSize = 12.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.Center)
                            Text("${s.b2bCount}", color = if (s.b2bCount > 0) VolleyRed else VolleyGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                            Text("${s.refDuties}", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                            Text("${s.buchholz}", color = VolleyTextSecondary, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                        }
                        HorizontalDivider(color = VolleyGrayBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ManualTieBreakDialog(
    groupKey: String,
    tiedTeamIds: List<String>,
    teamNames: Map<String, String>,
    onDismiss: () -> Unit,
    onSaveOrder: (List<String>) -> Unit
) {
    val order = remember(groupKey, tiedTeamIds) {
        mutableStateListOf<String>().apply { addAll(tiedTeamIds) }
    }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Gleichstand auflösen (${if (groupKey.isBlank()) "Gesamt" else "Gruppe $groupKey"})",
                color = VolleyYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen")
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveOrder(order.toList()) },
                modifier = Modifier.height(48.dp).testTag("btn_confirm_manual_tie_break"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Reihenfolge übernehmen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text(
            "Sortiere die punktgleichen Teams per Pfeiltasten nach geloster bzw. entschiedener Platzierung:",
            color = VolleyWhite,
            fontSize = 13.sp
        )
        order.forEachIndexed { idx, tid ->
            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${idx + 1}.",
                        color = VolleyYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.width(26.dp)
                    )
                    Text(
                        teamNames[tid] ?: tid,
                        color = VolleyWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            if (idx > 0) {
                                val item = order.removeAt(idx)
                                order.add(idx - 1, item)
                            }
                        },
                        enabled = idx > 0,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Hoch", tint = if (idx > 0) VolleyYellow else VolleyGrayBorder)
                    }
                    IconButton(
                        onClick = {
                            if (idx < order.lastIndex) {
                                val item = order.removeAt(idx)
                                order.add(idx + 1, item)
                            }
                        },
                        enabled = idx < order.lastIndex,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Runter", tint = if (idx < order.lastIndex) VolleyYellow else VolleyGrayBorder)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualRefereeDialog(
    targetMatch: SpecMatch,
    allMatches: List<SpecMatch>,
    teams: List<SpecTeam>,
    teamNames: Map<String, String>,
    onDismiss: () -> Unit,
    onSaveReferees: (List<String>) -> Unit
) {
    val selected = remember(targetMatch.id) {
        mutableStateListOf<String>().apply { addAll(targetMatch.refereeTeamIds) }
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Schiedsrichter zuweisen", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen")
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveReferees(selected.toList()) },
                modifier = Modifier.height(48.dp).testTag("btn_confirm_manual_referee"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Übernehmen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text(
            "${targetMatch.roundLabel}: ${targetMatch.teamAId?.let { teamNames[it] } ?: "?"} vs ${targetMatch.teamBId?.let { teamNames[it] } ?: "?"}",
            color = VolleyWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            "Regelkonforme Auswahl (nie aktiver Spieler, keine Doppelbelegung im selben Slot):",
            color = VolleyTextSecondary,
            fontSize = 12.sp
        )
        if (validationError != null) {
            Text(validationError!!, color = VolleyRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            teams.filter { it.present && !it.withdrawn }.forEach { t ->
                val isSelected = t.id in selected
                val err = SpecSchedulerAndReferees.validateManualRefereeChange(targetMatch, t.id, allMatches, teams)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selected.remove(t.id)
                            validationError = null
                        } else if (err != null) {
                            validationError = err
                        } else {
                            validationError = null
                            if (selected.size >= 2) selected.removeAt(0)
                            selected.add(t.id)
                        }
                    },
                    label = {
                        Text(
                            t.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else if (err != null) VolleyTextSecondary.copy(alpha = 0.5f) else VolleyWhite
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VolleyYellow,
                        selectedLabelColor = Color.Black,
                        containerColor = VolleyDarkSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun TournamentAuditLogDialog(
    entries: List<AuditEntry>,
    onDismiss: () -> Unit
) {
    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Turnier-Audit-Log (${entries.size})", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        if (entries.isEmpty()) {
            Text("Noch keine Änderungen protokolliert.", color = VolleyTextSecondary, fontSize = 13.sp)
        } else {
            entries.asReversed().take(50).forEach { e ->
                Surface(
                    color = VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(e.reason, color = VolleyYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(e.summary, color = VolleyWhite, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TournamentExportMenuDialog(
    tournament: Tournament,
    standingsByGroup: Map<String, List<SpecStandingRow>>,
    teamNames: Map<String, String>,
    hasCertificates: Boolean = false,
    onOpenCertificates: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Turnier exportieren", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        if (hasCertificates && onOpenCertificates != null) {
            Button(
                onClick = {
                    onDismiss()
                    onOpenCertificates()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_export_certificates_pdf"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sieger-Urkunden (PDF · Plätze 1–3)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (copiedLabel != null) {
            Surface(
                color = VolleyGreen.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, VolleyGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "$copiedLabel in die Zwischenablage kopiert!",
                    color = VolleyGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        val items = listOf(
            "ASCII-Tabelle & Spielplan" to {
                SpecExportAndPersistence.exportAsciiTable(
                    tournament.name, standingsByGroup, tournament.specMatches, teamNames, tournament.specConfig
                )
            },
            "CSV (Excel / RFC 4180 + Formelschutz)" to {
                SpecExportAndPersistence.exportCsvRfc4180(
                    tournament.name, standingsByGroup, tournament.specMatches, teamNames, tournament.specConfig
                )
            },
            "Ticker JSON (generisch)" to {
                SpecExportAndPersistence.exportGenericTickerJson(
                    tournament.id, tournament.name, standingsByGroup, tournament.specMatches, teamNames, tournament.specConfig
                )
            },
            "Ticker XML (generisch)" to {
                SpecExportAndPersistence.exportGenericTickerXml(
                    tournament.id, tournament.name, standingsByGroup, tournament.specMatches, teamNames, tournament.specConfig
                )
            },
            "Vollständiges Backup JSON (v2)" to {
                SpecExportAndPersistence.exportBackupJson(tournament)
            }
        )

        items.forEachIndexed { idx, (label, builder) ->
            Button(
                onClick = {
                    val out = builder()
                    clipboard.setText(AnnotatedString(out))
                    copiedLabel = label
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_export_option_$idx"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun BackupImportDialog(
    onDismiss: () -> Unit,
    onImportJson: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var rawJson by remember { mutableStateOf("") }
    var clipError by remember { mutableStateOf<String?>(null) }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Turnier-Backup (JSON) importieren", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen")
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportJson(rawJson) },
                enabled = rawJson.isNotBlank(),
                modifier = Modifier.height(48.dp).testTag("btn_confirm_backup_import"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Importieren", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Button(
            onClick = {
                val txt = clipboard.getText()?.text
                if (txt.isNullOrBlank()) {
                    clipError = "Zwischenablage ist leer."
                } else {
                    rawJson = txt
                    clipError = null
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
            border = BorderStroke(1.dp, VolleyGrayBorder),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = VolleyYellow)
            Spacer(Modifier.width(6.dp))
            Text("JSON aus Zwischenablage einfügen", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (clipError != null) {
            Text(clipError!!, color = VolleyRed, fontSize = 12.sp)
        }
        OutlinedTextField(
            value = rawJson,
            onValueChange = { rawJson = it },
            label = { Text("Backup JSON (<= 1 MB)", color = VolleyWhite) },
            minLines = 5,
            maxLines = 9,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_backup_json")
        )
    }
}

@Composable
fun TournamentSelfTestTab(
    tournament: Tournament?,
    report: SelfTestReport?,
    isRunning: Boolean,
    progress: Pair<Float, String>,
    onStartSelfTest: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, VolleyYellow, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = VolleyYellow)
                        Text(
                            "Diagnose / Selbsttest (SPEC v1.0)",
                            color = VolleyYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        "Prüft alle 12 Golden Tests (GT-01..GT-12), 1.000 deterministische Property-Tests (n=3..32, c=1..4) und die Invarianten I-01..I-14 auf dem aktuellen Turnier.",
                        color = VolleyWhite,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = onStartSelfTest,
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_start_self_test"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isRunning) "Selbsttest läuft..." else "Selbsttest starten (12 Golden + 1.000 Property)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isRunning) {
                        LinearProgressIndicator(
                            progress = { progress.first.coerceIn(0f, 1f) },
                            color = VolleyYellow,
                            trackColor = VolleyDarkSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text(progress.second, color = VolleyTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (report != null) {
            item {
                Surface(
                    color = if (report.allPassed) VolleyGreen.copy(alpha = 0.18f) else VolleyRed.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, if (report.allPassed) VolleyGreen else VolleyRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (report.allPassed) "ALLE CHECKS BESTANDEN (PASS)" else "FEHLER ERKANNT (FAIL)",
                                color = if (report.allPassed) VolleyGreen else VolleyRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Text(
                                "Golden: ${report.goldenResults.count { it.passed }}/${report.goldenResults.size} · Property: ${report.propertyRunsCompleted} Läufe · Invarianten: ${report.stateInvariantResults.count { it.passed }}/${report.stateInvariantResults.size}",
                                color = VolleyWhite,
                                fontSize = 12.sp
                            )
                            if (copiedFeedback) {
                                Text("Protokoll in Zwischenablage kopiert!", color = VolleyYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(SpecSelfTestRunner.formatReportForClipboard(report)))
                                copiedFeedback = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(1.dp, VolleyYellow),
                            modifier = Modifier.height(48.dp).testTag("btn_copy_self_test_report")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ergebnis kopieren", color = VolleyYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            item {
                Text("12 Golden Tests", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            items(report.goldenResults, key = { it.id }) { res ->
                SelfTestResultRow(res)
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Property Tests (${report.propertyRunsCompleted} Zufallsturniere)", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SelfTestResultRow(
                    SelfTestCheckResult(
                        id = "PROP-1000",
                        category = "PROPERTY",
                        title = "${report.propertyRunsCompleted} zufällige Turniere (n=3..32, c=1..4)",
                        passed = report.propertyPassed,
                        details = report.propertyFailureDetails ?: "Alle Invarianten über ${report.propertyRunsCompleted} Läufe ohne Widerspruch erfüllt."
                    )
                )
            }

            if (report.stateInvariantResults.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Aktives Turnier (${tournament?.name ?: ""}): Invarianten", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                items(report.stateInvariantResults, key = { it.id }) { res ->
                    SelfTestResultRow(res)
                }
            }
        }
    }
}

@Composable
private fun SelfTestResultRow(res: SelfTestCheckResult) {
    Surface(
        color = VolleyDarkSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (res.passed) VolleyGreen.copy(alpha = 0.5f) else VolleyRed),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = if (res.passed) VolleyGreen else VolleyRed,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    if (res.passed) "PASS" else "FAIL",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${res.id} · ${res.title}", color = VolleyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(res.details, color = VolleyTextSecondary, fontSize = 11.sp)
            }
        }
    }
}
