package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.domain.engine.TournamentEngine
import com.example.domain.tournament.*
import com.example.ui.components.VolleyFormDialog
import com.example.ui.components.VolleyStepperButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.VolleyViewModel
import java.util.UUID

@Composable
fun TournamentScreen(
    viewModel: VolleyViewModel,
    modifier: Modifier = Modifier
) {
    val tournaments by viewModel.allTournaments.collectAsState()
    val selectedTournamentId by viewModel.selectedTournamentId.collectAsState()
    val tournamentMatches by viewModel.selectedTournamentMatches.collectAsState()
    val allTeams by viewModel.allTeams.collectAsState()
    val canUndo by viewModel.canUndoTournament.collectAsState()
    val canRedo by viewModel.canRedoTournament.collectAsState()
    val activeWarnings by viewModel.activeTournamentWarnings.collectAsState()
    val feedbackMessage by viewModel.tournamentFeedbackMessage.collectAsState()
    val selfTestReport by viewModel.selfTestReport.collectAsState()
    val selfTestRunning by viewModel.selfTestRunning.collectAsState()
    val selfTestProgress by viewModel.selfTestProgress.collectAsState()
    val autoOpenCertTournamentId by viewModel.autoOpenCertificatesTournamentId.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showBackupImportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showExportMenuDialog by remember { mutableStateOf(false) }
    var showCertificatesDialog by remember(selectedTournamentId) { mutableStateOf(false) }
    var editingTournamentMeta by remember { mutableStateOf<Tournament?>(null) }
    var deletingListTournament by remember { mutableStateOf<Tournament?>(null) }

    val activeTournament = remember(selectedTournamentId, tournaments) {
        tournaments.find { it.id == selectedTournamentId }
    }
    val podiumCertificates = remember(activeTournament, tournamentMatches, allTeams) {
        if (activeTournament == null) {
            emptyList()
        } else {
            TournamentCertificateEngine.extractTopThreeCertificates(
                tournament = activeTournament,
                matches = tournamentMatches,
                dbTeams = allTeams
            )
        }
    }

    LaunchedEffect(autoOpenCertTournamentId, activeTournament?.id, podiumCertificates) {
        if (activeTournament != null &&
            autoOpenCertTournamentId == activeTournament.id &&
            podiumCertificates.isNotEmpty()
        ) {
            showCertificatesDialog = true
            viewModel.consumeAutoOpenCertificates()
        }
    }

    BackHandler(enabled = activeTournament != null) {
        viewModel.selectTournament(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolleyBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Compact Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeTournament == null) {
                Text(
                    "Turniere",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showBackupImportDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
                        .testTag("btn_open_backup_import")
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Backup importieren",
                        tint = VolleyYellow
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_create_tournament"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyYellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Neues Turnier", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.selectTournament(null) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = VolleyYellow
                        )
                    }
                    Text(
                        activeTournament.name,
                        color = VolleyYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showExportMenuDialog = true },
                        modifier = Modifier.size(48.dp).testTag("btn_export_tournament")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Turnierdaten exportieren", tint = VolleyYellow)
                    }

                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Turnier löschen", tint = VolleyRed)
                    }
                }
            }
        }

        // Feedback Banner (e.g. Unresolved tie blocking KO, Backup feedback, Referee conflict)
        if (feedbackMessage != null) {
            Surface(
                color = VolleyYellow.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, VolleyYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        feedbackMessage!!,
                        color = VolleyWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearTournamentFeedback() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Hinweis schließen", tint = VolleyYellow, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeTournament == null) {
                TournamentListView(
                    tournaments = tournaments,
                    onSelect = { viewModel.selectTournament(it.id) },
                    onEditTournament = { editingTournamentMeta = it },
                    onDeleteTournament = { deletingListTournament = it },
                    onCreateNew = { showCreateDialog = true }
                )
            } else {
                ActiveTournamentDetailView(
                    tournament = activeTournament,
                    matches = tournamentMatches,
                    teams = allTeams,
                    warnings = activeWarnings,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    selfTestReport = selfTestReport,
                    selfTestRunning = selfTestRunning,
                    selfTestProgress = selfTestProgress,
                    onUndo = { viewModel.undoTournamentAction() },
                    onRedo = { viewModel.redoTournamentAction() },
                    onSetLiveDelay = { viewModel.setTournamentLiveDelay(activeTournament.id, it) },
                    onOptimizeSchedule = { viewModel.rescheduleAndReassignReferees(activeTournament.id) },
                    onEditTournamentMeta = { editingTournamentMeta = activeTournament },
                    onPlayInScoreboard = { viewModel.playTournamentMatchInScoreboard(it) },
                    onEnterDirectResult = { match, sets -> viewModel.enterDirectTournamentMatchResult(match, sets) },
                    onForfeitMatch = { match, winnerSide -> viewModel.recordTournamentForfeit(match, winnerSide) },
                    onChangeMatchReferee = { matchId, refIds -> viewModel.manualChangeMatchReferee(activeTournament.id, matchId, refIds) },
                    onResolveManualTie = { grpKey, ordered -> viewModel.setManualTieResolution(activeTournament.id, grpKey, ordered) },
                    onGenerateKO = { viewModel.generateKoMatchesForTournament(activeTournament) },
                    onGeneratePlacementMatches = { viewModel.generatePlacementMatchesForTournament(activeTournament) },
                    onAdvanceKoRound = { viewModel.advanceTournamentKoRound(activeTournament) },
                    onNextSwissRound = { viewModel.generateNextSwissRound(activeTournament) },
                    onAddPostCreationTeam = { team -> viewModel.addTournamentTeamPostCreation(activeTournament.id, team) },
                    onUpdateManualTeam = { updated -> viewModel.updateTournamentTeam(activeTournament.id, updated) },
                    onRemoveTeam = { teamId -> viewModel.removeTournamentTeam(activeTournament.id, teamId) },
                    onRenameMixerTeam = { teamId, newName -> viewModel.renameTournamentMixerTeam(activeTournament.id, teamId, newName) },
                    onStartSelfTest = { viewModel.runTournamentSelfTests(activeTournament) },
                    podiumCertificates = podiumCertificates,
                    onOpenCertificates = { showCertificatesDialog = true }
                )
            }
        }
    }

    if (showExportMenuDialog && activeTournament != null) {
        val teamNames = remember(allTeams, activeTournament) {
            val map = allTeams.associate { it.id to it.name }.toMutableMap()
            activeTournament.manualTeams.forEach { map[it.id] = it.name }
            map
        }
        val standingsByGroup = remember(activeTournament, tournamentMatches, teamNames) {
            if (activeTournament.groupAssignments.isNotEmpty() && activeTournament.mode != TournamentMode.ROUND_ROBIN && activeTournament.mode != TournamentMode.SWISS_SYSTEM) {
                activeTournament.groupAssignments.keys.sorted().associateWith { grp ->
                    TournamentEngine.calculateSpecStandings(
                        activeTournament,
                        grp,
                        activeTournament.groupAssignments[grp].orEmpty(),
                        teamNames,
                        tournamentMatches
                    )
                }
            } else {
                mapOf(
                    "Gesamt" to TournamentEngine.calculateSpecStandings(
                        activeTournament,
                        "",
                        activeTournament.teamIds,
                        teamNames,
                        tournamentMatches
                    )
                )
            }
        }
        TournamentExportMenuDialog(
            tournament = activeTournament,
            standingsByGroup = standingsByGroup,
            teamNames = teamNames,
            hasCertificates = podiumCertificates.isNotEmpty(),
            onOpenCertificates = { showCertificatesDialog = true },
            onDismiss = { showExportMenuDialog = false }
        )
    }

    if (showCertificatesDialog && activeTournament != null) {
        TournamentCertificatesDialog(
            tournamentName = activeTournament.name,
            certificates = podiumCertificates,
            onDismiss = { showCertificatesDialog = false }
        )
    }

    if (showDeleteConfirmDialog && activeTournament != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = VolleyDarkSurface,
            title = {
                Text("Turnier löschen?", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Möchtest du das Turnier \"${activeTournament.name}\" wirklich unwiderruflich löschen? Alle zugehörigen Spiele und Ergebnisse werden entfernt.",
                    color = VolleyWhite,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteTournament(activeTournament.id)
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Löschen", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Abbrechen", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showBackupImportDialog) {
        BackupImportDialog(
            onDismiss = { showBackupImportDialog = false },
            onImportJson = { raw ->
                viewModel.importTournamentFromBackupJson(raw)
                showBackupImportDialog = false
            }
        )
    }

    // Edit full tournament settings (from long-press menu, active tournament chips, or schedule controls)
    editingTournamentMeta?.let { target ->
        EditTournamentMetadataDialog(
            tournament = target,
            onDismiss = { editingTournamentMeta = null },
            onSave = { newName, newLoc, newDate, newCourts, newStatus, newMatchCfg, newGroupsCount, newPointCap, newSpecCfg ->
                viewModel.updateTournamentFullSettings(
                    tournamentId = target.id,
                    name = newName,
                    location = newLoc,
                    date = newDate,
                    courtCount = newCourts,
                    status = newStatus,
                    matchConfig = newMatchCfg,
                    groupsCount = newGroupsCount,
                    pointCap = newPointCap,
                    specConfig = newSpecCfg
                )
                editingTournamentMeta = null
            }
        )
    }

    // Delete tournament confirmation from long-press menu
    deletingListTournament?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingListTournament = null },
            containerColor = VolleyDarkSurface,
            title = {
                Text("Turnier löschen?", color = VolleyRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Möchtest du das Turnier \"${target.name}\" wirklich unwiderruflich löschen?",
                    color = VolleyWhite,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTournament(target.id)
                        deletingListTournament = null
                    },
                    modifier = Modifier.height(48.dp).testTag("btn_confirm_delete_list_tournament"),
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Löschen", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deletingListTournament = null },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Abbrechen", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateTournamentDialog(
            availableTeams = allTeams,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, sport, format, config, mode, groupsCount, seeding, manualTeams, useMixerTeams,
                          location, date, courtCount, templateId, pointCap, pointsForWin, pointsForDraw, pointsForLoss,
                          minFemales, minMales, doubleRoundRobin, specCfgOverride ->
                val participating = if (useMixerTeams) {
                    allTeams.filter { it.isFinalized }.ifEmpty { allTeams }
                } else {
                    emptyList()
                }
                viewModel.createTournament(
                    name = name,
                    sportType = sport,
                    fieldFormat = format,
                    matchConfig = config,
                    mode = mode,
                    groupsCount = groupsCount,
                    seedingMode = seeding,
                    participatingTeams = participating,
                    manualTeams = if (!useMixerTeams) manualTeams else emptyList(),
                    location = location,
                    date = date,
                    courtCount = courtCount,
                    templateId = templateId,
                    pointCap = pointCap,
                    pointsForWin = pointsForWin,
                    pointsForDraw = pointsForDraw,
                    pointsForLoss = pointsForLoss,
                    minFemales = minFemales,
                    minMales = minMales,
                    doubleRoundRobin = doubleRoundRobin,
                    specConfigOverride = specCfgOverride
                )
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TournamentListView(
    tournaments: List<Tournament>,
    onSelect: (Tournament) -> Unit,
    onEditTournament: (Tournament) -> Unit,
    onDeleteTournament: (Tournament) -> Unit,
    onCreateNew: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var longPressedTournament by remember { mutableStateOf<Tournament?>(null) }

    if (tournaments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Keine Turniere vorhanden.",
                    color = VolleyWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Erstelle ein neues Turnier mit Gruppenphase, Multi-Feld-Zeitplan, Schiedsrichter-Fairness und K.-o.-Baum.",
                    color = VolleyTextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_empty_create_tournament"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VolleyYellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Turnier erstellen", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tipp: Lange auf ein Turnier drücken zum Auswählen, Bearbeiten oder Löschen.",
                    color = VolleyTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            items(tournaments, key = { it.id }) { t ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VolleyGrayBorder, RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { onSelect(t) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPressedTournament = t
                            }
                        )
                        .testTag("card_tournament_${t.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t.name,
                                color = VolleyWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${t.sportType.label} · ${t.fieldFormat.label} · ${t.mode.label}",
                                color = VolleyYellow,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Status: ${t.status.label} · ${t.teamIds.size} Teams · ${t.courtCount} ${if (t.courtCount == 1) "Feld" else "Felder"}",
                                color = VolleyTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { onSelect(t) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_open_tournament_${t.name}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VolleyYellow,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                            Spacer(Modifier.width(4.dp))
                            Text("Öffnen", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    longPressedTournament?.let { t ->
        AlertDialog(
            onDismissRequest = { longPressedTournament = null },
            containerColor = VolleyDarkSurface,
            title = {
                Text(
                    t.name,
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${t.mode.label} · ${t.teamIds.size} Teams · ${t.courtCount} Felder · ${t.status.label}",
                        color = VolleyTextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            longPressedTournament = null
                            onSelect(t)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_context_open_tournament"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Turnier auswählen / öffnen", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            longPressedTournament = null
                            onEditTournament(t)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_context_edit_tournament"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyWhite),
                        border = BorderStroke(1.dp, VolleyGrayBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = VolleyYellow)
                        Spacer(Modifier.width(8.dp))
                        Text("Turnier bearbeiten", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            longPressedTournament = null
                            onDeleteTournament(t)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_context_delete_tournament"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyRed),
                        border = BorderStroke(1.dp, VolleyRed.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = VolleyRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Turnier löschen", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = { longPressedTournament = null },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTournamentMetadataDialog(
    tournament: Tournament,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        location: String,
        date: String,
        courtCount: Int,
        status: TournamentStatus,
        matchConfig: MatchConfig,
        groupsCount: Int,
        pointCap: Int?,
        specConfig: SpecTournamentConfig
    ) -> Unit
) {
    var selectedDialogTab by remember(tournament.id) { mutableIntStateOf(0) } // 0 = Basis & Zeitplan, 1 = Regeln & Modus
    var name by remember(tournament.id) { mutableStateOf(tournament.name) }
    var location by remember(tournament.id) { mutableStateOf(tournament.location) }
    var date by remember(tournament.id) { mutableStateOf(tournament.date) }
    var courtCount by remember(tournament.id) { mutableIntStateOf(tournament.courtCount) }
    var status by remember(tournament.id) { mutableStateOf(tournament.status) }

    var startTimeMinutes by remember(tournament.id) { mutableIntStateOf(tournament.specConfig.startTimeMinutes) }
    var slotDurationMinutes by remember(tournament.id) { mutableIntStateOf(tournament.specConfig.slotDurationMinutes) }
    var setsMode by remember(tournament.id) { mutableStateOf(tournament.matchConfig.setsMode) }
    var pointsPerSet by remember(tournament.id) { mutableIntStateOf(tournament.matchConfig.pointsPerSet) }
    var winByTwo by remember(tournament.id) { mutableStateOf(tournament.matchConfig.winByTwo) }
    var timeLimitMinutes by remember(tournament.id) { mutableStateOf(tournament.matchConfig.timeLimitMinutes) }
    var pointCap by remember(tournament.id) { mutableStateOf(tournament.pointCap ?: tournament.specConfig.pointCap) }
    var groupsCount by remember(tournament.id) { mutableIntStateOf(tournament.groupsCount.coerceIn(2, 4)) }
    var doubleRoundRobin by remember(tournament.id) { mutableStateOf(tournament.specConfig.doubleRoundRobin) }
    var restStrictness by remember(tournament.id) { mutableStateOf(tournament.specConfig.restStrictness) }
    var minRestSlots by remember(tournament.id) { mutableIntStateOf(tournament.specConfig.minRestSlots) }
    var refereeMode by remember(tournament.id) { mutableStateOf(tournament.specConfig.refereeMode) }
    var refTeamsPerMatch by remember(tournament.id) { mutableIntStateOf(tournament.specConfig.refTeamsPerMatch) }
    var scoringSystem by remember(tournament.id) { mutableStateOf(tournament.specConfig.scoringSystem) }
    var withdrawalPolicy by remember(tournament.id) { mutableStateOf(tournament.specConfig.withdrawalPolicy) }

    val startHour = (startTimeMinutes / 60).coerceIn(0, 23)
    val startMin = (startTimeMinutes % 60).coerceIn(0, 59)
    val formattedStart = "%02d:%02d".format(startHour, startMin)
    val supportsGroups = tournament.mode == TournamentMode.GROUPS_KO ||
        tournament.mode == TournamentMode.PLACEMENT ||
        tournament.mode == TournamentMode.DOUBLE_ELIMINATION ||
        tournament.mode == TournamentMode.KING_OF_THE_COURT

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Turnier & Regeln bearbeiten", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedMatchCfg = tournament.matchConfig.copy(
                        setsMode = setsMode,
                        pointsPerSet = pointsPerSet,
                        winByTwo = winByTwo,
                        timeLimitMinutes = timeLimitMinutes
                    )
                    val updatedSpecCfg = tournament.specConfig.copy(
                        startTimeMinutes = startTimeMinutes,
                        slotDurationMinutes = slotDurationMinutes,
                        restStrictness = restStrictness,
                        minRestSlots = minRestSlots,
                        refereeMode = refereeMode,
                        refTeamsPerMatch = refTeamsPerMatch,
                        scoringSystem = scoringSystem,
                        withdrawalPolicy = withdrawalPolicy,
                        doubleRoundRobin = doubleRoundRobin,
                        pointCap = pointCap,
                        pointsPerSet = pointsPerSet,
                        setsToWin = setsMode.setsToWin
                    )
                    onSave(
                        name,
                        location,
                        date,
                        courtCount,
                        status,
                        updatedMatchCfg,
                        groupsCount,
                        pointCap,
                        updatedSpecCfg
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.height(48.dp).testTag("btn_save_tournament_meta"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Speichern & Optimieren", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0 to "Basis & Zeitplan", 1 to "Regeln & Modus").forEach { (idx, label) ->
                val active = selectedDialogTab == idx
                Button(
                    onClick = { selectedDialogTab = idx },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("btn_edit_tournament_tab_$idx"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) VolleyYellow else VolleyDarkSurfaceVariant,
                        contentColor = if (active) Color.Black else VolleyWhite
                    ),
                    border = BorderStroke(1.dp, if (active) VolleyYellow else VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        if (selectedDialogTab == 0) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Turniername", color = VolleyWhite) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VolleyWhite,
                    unfocusedTextColor = VolleyWhite,
                    focusedBorderColor = VolleyYellow,
                    unfocusedBorderColor = VolleyGrayBorder
                ),
                modifier = Modifier.fillMaxWidth().testTag("input_edit_tournament_name")
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ort / Anlage", color = VolleyWhite) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VolleyWhite,
                    unfocusedTextColor = VolleyWhite,
                    focusedBorderColor = VolleyYellow,
                    unfocusedBorderColor = VolleyGrayBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Datum", color = VolleyWhite) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VolleyWhite,
                    unfocusedTextColor = VolleyWhite,
                    focusedBorderColor = VolleyYellow,
                    unfocusedBorderColor = VolleyGrayBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Anzahl Spielfelder: $courtCount", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VolleyStepperButton(
                        isIncrement = false,
                        enabled = courtCount > 1,
                        onClick = { if (courtCount > 1) courtCount-- }
                    )
                    Text("$courtCount", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    VolleyStepperButton(
                        isIncrement = true,
                        enabled = courtCount < 16,
                        onClick = { if (courtCount < 16) courtCount++ }
                    )
                }
                listOf(1, 2, 3, 4).forEach { preset ->
                    FilterChip(
                        selected = courtCount == preset,
                        onClick = { courtCount = preset },
                        label = { Text("$preset", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }

            Text("Turnierbeginn: $formattedStart Uhr", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                VolleyStepperButton(
                    isIncrement = false,
                    enabled = startTimeMinutes >= 360,
                    onClick = { startTimeMinutes = (startTimeMinutes - 15).coerceAtLeast(360) }
                )
                Text(formattedStart, color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                VolleyStepperButton(
                    isIncrement = true,
                    enabled = startTimeMinutes <= 1320,
                    onClick = { startTimeMinutes = (startTimeMinutes + 15).coerceAtMost(1320) }
                )
            }

            Text("Slot-Dauer pro Spiel: $slotDurationMinutes Min", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VolleyStepperButton(
                        isIncrement = false,
                        enabled = slotDurationMinutes > 15,
                        onClick = { slotDurationMinutes = (slotDurationMinutes - 5).coerceAtLeast(15) }
                    )
                    Text("$slotDurationMinutes Min", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    VolleyStepperButton(
                        isIncrement = true,
                        enabled = slotDurationMinutes < 90,
                        onClick = { slotDurationMinutes = (slotDurationMinutes + 5).coerceAtMost(90) }
                    )
                }
                listOf(20, 30, 35, 45).forEach { dur ->
                    FilterChip(
                        selected = slotDurationMinutes == dur,
                        onClick = { slotDurationMinutes = dur },
                        label = { Text("${dur}m", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }

            Text("Turnierstatus:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TournamentStatus.values().forEach { st ->
                    val active = status == st
                    Button(
                        onClick = { status = st },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) VolleyYellow else VolleyDarkSurfaceVariant,
                            contentColor = if (active) Color.Black else VolleyWhite
                        ),
                        border = BorderStroke(1.dp, VolleyGrayBorder),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(st.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        } else {
            Surface(
                color = VolleyBlue.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, VolleyBlue.copy(alpha = 0.5f))
            ) {
                Text(
                    "Offene Spiele, Zeiten, Felder & Schiedsrichter werden beim Speichern automatisch neu berechnet. Bereits beendete Spiele bleiben unverändert.",
                    color = VolleyWhite,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Text("Satzmodus:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SetsMode.values().forEach { sm ->
                    FilterChip(
                        selected = setsMode == sm,
                        onClick = { setsMode = sm },
                        label = { Text(sm.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }

            Text("Satzpunkte & Vorsprung:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(11, 15, 21, 25).forEach { pts ->
                    FilterChip(
                        selected = pointsPerSet == pts,
                        onClick = { pointsPerSet = pts },
                        label = { Text("$pts Pkt", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
                FilterChip(
                    selected = winByTwo,
                    onClick = { winByTwo = !winByTwo },
                    label = { Text(if (winByTwo) "2 Pkt Vorsprung" else "1 Pkt reicht", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VolleyYellow,
                        selectedLabelColor = Color.Black,
                        containerColor = VolleyDarkSurfaceVariant,
                        labelColor = VolleyWhite
                    )
                )
            }

            Text("Point-Cap & Zeitspiel:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Pair<Int?, String>>(null to "Cap Aus", 21 to "Cap 21", 28 to "Cap 28", 30 to "Cap 30").forEach { (cap, lbl) ->
                    FilterChip(
                        selected = pointCap == cap,
                        onClick = { pointCap = cap },
                        label = { Text(lbl, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
                listOf<Pair<Int?, String>>(null to "Ohne Zeitlimit", 10 to "10 Min", 12 to "12 Min", 15 to "15 Min").forEach { (tl, lbl) ->
                    FilterChip(
                        selected = timeLimitMinutes == tl,
                        onClick = { timeLimitMinutes = tl },
                        label = { Text(lbl, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }

            if (supportsGroups) {
                Text("Gruppenanzahl (Neuaufteilung vor erstem Spiel):", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4).forEach { gc ->
                        FilterChip(
                            selected = groupsCount == gc,
                            onClick = { groupsCount = gc },
                            label = { Text("$gc Gruppen", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VolleyYellow,
                                selectedLabelColor = Color.Black,
                                containerColor = VolleyDarkSurfaceVariant,
                                labelColor = VolleyWhite
                            )
                        )
                    }
                }
            }

            if (tournament.mode == TournamentMode.ROUND_ROBIN || supportsGroups) {
                FilterChip(
                    selected = doubleRoundRobin,
                    onClick = { doubleRoundRobin = !doubleRoundRobin },
                    label = { Text(if (doubleRoundRobin) "Hin- & Rückrunde: Aktiv" else "Nur Hinspiel", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VolleyYellow,
                        selectedLabelColor = Color.Black,
                        containerColor = VolleyDarkSurfaceVariant,
                        labelColor = VolleyWhite
                    )
                )
            }

            Text("Pausenstriktigkeit & Mindestpause ($minRestSlots Slots):", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RestStrictness.values().forEach { rs ->
                    FilterChip(
                        selected = restStrictness == rs,
                        onClick = { restStrictness = rs },
                        label = { Text(rs.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                VolleyStepperButton(
                    isIncrement = false,
                    enabled = minRestSlots > 0,
                    onClick = { if (minRestSlots > 0) minRestSlots-- }
                )
                Text("Mindestpause: $minRestSlots Slot(s)", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                VolleyStepperButton(
                    isIncrement = true,
                    enabled = minRestSlots < 3,
                    onClick = { if (minRestSlots < 3) minRestSlots++ }
                )
            }

            Text("Schiedsrichter-Regel:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RefereeMode.values().forEach { rm ->
                    FilterChip(
                        selected = refereeMode == rm,
                        onClick = { refereeMode = rm },
                        label = { Text(rm.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }
            if (refereeMode == RefereeMode.AUTO_FAIR || refereeMode == RefereeMode.LOSER_REFS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VolleyStepperButton(
                        isIncrement = false,
                        enabled = refTeamsPerMatch > 1,
                        onClick = { if (refTeamsPerMatch > 1) refTeamsPerMatch-- }
                    )
                    Text("$refTeamsPerMatch SR-Team(s) pro Spiel", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    VolleyStepperButton(
                        isIncrement = true,
                        enabled = refTeamsPerMatch < 2,
                        onClick = { if (refTeamsPerMatch < 2) refTeamsPerMatch++ }
                    )
                }
            }

            Text("Punktesystem (Tabelle):", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScoringSystem.values().forEach { ss ->
                    FilterChip(
                        selected = scoringSystem == ss,
                        onClick = { scoringSystem = ss },
                        label = { Text(ss.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }

            Text("Verhalten bei Rückzug (W.O.):", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WithdrawalPolicy.values().forEach { wp ->
                    FilterChip(
                        selected = withdrawalPolicy == wp,
                        onClick = { withdrawalPolicy = wp },
                        label = { Text(wp.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveTournamentDetailView(
    tournament: Tournament,
    matches: List<Match>,
    teams: List<Team>,
    warnings: List<TournamentWarning> = emptyList(),
    canUndo: Boolean,
    canRedo: Boolean,
    selfTestReport: SelfTestReport?,
    selfTestRunning: Boolean,
    selfTestProgress: Pair<Float, String>,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSetLiveDelay: (Int) -> Unit,
    onOptimizeSchedule: () -> Unit,
    onEditTournamentMeta: () -> Unit,
    onPlayInScoreboard: (Match) -> Unit,
    onEnterDirectResult: (Match, List<SetResult>) -> Unit,
    onForfeitMatch: (Match, TeamSide) -> Unit,
    onChangeMatchReferee: (String, List<String>) -> Unit,
    onResolveManualTie: (String, List<String>) -> Unit,
    onGenerateKO: () -> Unit,
    onGeneratePlacementMatches: () -> Unit = {},
    onAdvanceKoRound: () -> Unit = {},
    onNextSwissRound: () -> Unit = {},
    onAddPostCreationTeam: (ManualTournamentTeam) -> Unit,
    onUpdateManualTeam: (ManualTournamentTeam) -> Unit = {},
    onRemoveTeam: (String) -> Unit,
    onRenameMixerTeam: (String, String) -> Unit = { _, _ -> },
    onStartSelfTest: () -> Unit,
    podiumCertificates: List<CertificateEntry> = emptyList(),
    onOpenCertificates: () -> Unit = {}
) {
    val teamNames = remember(teams, tournament) {
        val map = teams.associate { it.id to it.name }.toMutableMap()
        tournament.manualTeams.forEach { mt -> map[mt.id] = mt.name }
        map
    }
    val specTeams = remember(tournament, teamNames) {
        val mById = tournament.manualTeams.associateBy { it.id }
        tournament.teamIds.mapIndexed { idx, tid ->
            val mt = mById[tid]
            SpecTeam(
                id = tid,
                name = teamNames[tid] ?: mt?.name ?: "Team ${idx + 1}",
                shortName = mt?.shortName ?: (teamNames[tid] ?: "TM").take(4).uppercase(),
                clubId = mt?.club ?: "",
                skillLevel = mt?.skillLevel ?: 50,
                present = mt?.present ?: true,
                withdrawn = mt?.withdrawn ?: false,
                registrationOrder = if (mt != null && mt.registrationOrder > 0) mt.registrationOrder else idx
            )
        }
    }
    val specMatchById = remember(tournament.specMatches) {
        tournament.specMatches.associateBy { it.id }
    }
    val fairnessReport = remember(tournament.specMatches, specTeams) {
        SpecSchedulerAndReferees.computeRefereeFairnessReport(tournament.specMatches, specTeams, emptyList())
    }

    var selectedTab by remember(tournament.id) { mutableIntStateOf(0) } // 0: Spielplan, 1: Tabelle, 2: K.o./Platzierung, 3: Teams, 4: Diagnose
    var selectedGroupFilter by remember(tournament.id) { mutableStateOf<String?>(null) }
    var selectedScheduleFilter by remember(tournament.id) { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val groupMatches = remember(matches) { matches.filter { it.groupId != "KO" } }
    val koMatches = remember(matches) { matches.filter { it.groupId == "KO" } }
    val allGroupMatchesFinished = remember(groupMatches) {
        TournamentEngine.areGroupMatchesCompleted(groupMatches)
    }

    var editingMatch by remember { mutableStateOf<Match?>(null) }
    var editingRefereeForMatch by remember { mutableStateOf<SpecMatch?>(null) }
    var editingManualTeam by remember { mutableStateOf<ManualTournamentTeam?>(null) }
    var addingPostTeam by remember { mutableStateOf(false) }
    var editingMixerTeam by remember { mutableStateOf<Team?>(null) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var resolvingTieRequest by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    val hasKoTab = tournament.mode == TournamentMode.GROUPS_KO ||
        tournament.mode == TournamentMode.SINGLE_ELIMINATION ||
        tournament.mode == TournamentMode.PLACEMENT
    val endrundeTabTitle = if (tournament.mode == TournamentMode.PLACEMENT) "Platzierung" else "K.-o.-Baum"

    Column(modifier = Modifier.fillMaxSize()) {
        // Metadata chips (tapping "Felder" opens quick metadata editor)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(color = VolleyDarkSurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                Text(
                    "${tournament.fieldFormat.label} · ${tournament.matchConfig.setsMode.label}",
                    color = VolleyWhite,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Surface(color = VolleyDarkSurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                Text(
                    tournament.mode.label,
                    color = VolleyYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Surface(
                color = VolleyDarkSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { onEditTournamentMeta() }
            ) {
                Text(
                    "${tournament.courtCount} ${if (tournament.courtCount == 1) "Feld" else "Felder"} ✎",
                    color = VolleyYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (tournament.matchConfig.timeLimitMinutes != null) {
                Surface(color = VolleyDarkSurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "Zeit: ${tournament.matchConfig.timeLimitMinutes} Min",
                        color = VolleyYellow,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Always-Visible Sub-Tabs using wrapped FlowRow (NO ScrollableTabRow hiding Teams off-screen!)
        val safeTabIndex = if (!hasKoTab && selectedTab == 2) 0 else selectedTab
        val tabEntries = remember(hasKoTab, endrundeTabTitle, tournament.teamIds.size) {
            buildList {
                add(Triple(0, "Spielplan", "tab_tournament_matches"))
                add(Triple(1, "Tabelle", "tab_tournament_standings"))
                if (hasKoTab) add(Triple(2, endrundeTabTitle, "tab_tournament_ko"))
                add(Triple(3, "Teams (${tournament.teamIds.size})", "tab_tournament_teams"))
                add(Triple(4, "Diagnose", "tab_tournament_diagnostics"))
            }
        }

        Surface(
            color = VolleyDarkSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, VolleyGrayBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabEntries.forEach { (tabId, label, tag) ->
                    val active = safeTabIndex == tabId
                    Surface(
                        color = if (active) VolleyYellow else VolleyDarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (active) VolleyYellow else VolleyGrayBorder),
                        modifier = Modifier
                            .height(40.dp)
                            .clickable { selectedTab = tabId }
                            .testTag(tag)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                label,
                                color = if (active) Color.Black else VolleyWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (safeTabIndex) {
                0 -> {
                    val scheduleGroups = remember(tournament.groupAssignments, tournament.mode) {
                        if (tournament.mode == TournamentMode.ROUND_ROBIN || tournament.mode == TournamentMode.SWISS_SYSTEM || tournament.mode == TournamentMode.SINGLE_ELIMINATION) {
                            emptyList()
                        } else {
                            tournament.groupAssignments.keys.sorted()
                        }
                    }
                    val sortedGroupMatches = remember(groupMatches, specMatchById) {
                        groupMatches.sortedWith(
                            compareBy<Match> { specMatchById[it.id]?.slotIndex ?: Int.MAX_VALUE }
                                .thenBy { specMatchById[it.id]?.courtNumber ?: it.courtNumber ?: Int.MAX_VALUE }
                                .thenBy { it.id }
                        )
                    }
                    val sortedKoMatches = remember(koMatches, specMatchById) {
                        koMatches.sortedWith(
                            compareBy<Match> { specMatchById[it.id]?.slotIndex ?: Int.MAX_VALUE }
                                .thenBy { specMatchById[it.id]?.courtNumber ?: it.courtNumber ?: Int.MAX_VALUE }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            SpecScheduleControlsCard(
                                tournament = tournament,
                                fairnessReport = fairnessReport,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                onUndo = onUndo,
                                onRedo = onRedo,
                                onSetLiveDelay = onSetLiveDelay,
                                onOptimizeSchedule = onOptimizeSchedule,
                                onOpenAuditLog = { showAuditDialog = true },
                                onEditSettings = onEditTournamentMeta
                            )
                        }

                        if (podiumCertificates.isNotEmpty()) {
                            item(key = "schedule_certificates_banner") {
                                PodiumCertificatesBannerCard(
                                    certificates = podiumCertificates,
                                    onOpenCertificates = onOpenCertificates
                                )
                            }
                        }

                        if (warnings.isNotEmpty()) {
                            item(key = "schedule_warnings") {
                                SpecWarningsBanner(warnings = warnings)
                            }
                        }

                        if (scheduleGroups.size > 1 || (scheduleGroups.isNotEmpty() && sortedKoMatches.isNotEmpty())) {
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedScheduleFilter == null,
                                        onClick = { selectedScheduleFilter = null },
                                        label = { Text("Alle", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VolleyYellow,
                                            selectedLabelColor = Color.Black,
                                            containerColor = VolleyDarkSurfaceVariant,
                                            labelColor = VolleyWhite
                                        )
                                    )
                                    scheduleGroups.forEach { grp ->
                                        FilterChip(
                                            selected = selectedScheduleFilter == grp,
                                            onClick = { selectedScheduleFilter = grp },
                                            label = { Text("Gruppe $grp", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = VolleyYellow,
                                                selectedLabelColor = Color.Black,
                                                containerColor = VolleyDarkSurfaceVariant,
                                                labelColor = VolleyWhite
                                            )
                                        )
                                    }
                                    if (sortedKoMatches.isNotEmpty()) {
                                        FilterChip(
                                            selected = selectedScheduleFilter == "KO",
                                            onClick = { selectedScheduleFilter = "KO" },
                                            label = { Text(if (tournament.mode == TournamentMode.PLACEMENT) "Platzierung" else "K.-o.-Phase", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = VolleyYellow,
                                                selectedLabelColor = Color.Black,
                                                containerColor = VolleyDarkSurfaceVariant,
                                                labelColor = VolleyWhite
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (tournament.mode == TournamentMode.SWISS_SYSTEM) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, VolleyYellow, RoundedCornerShape(8.dp))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Schweizer System", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (allGroupMatchesFinished) "Aktuelle Swiss-Runde beendet! Nächste Runde kann gepaart werden."
                                            else "Spiele der aktuellen Runde noch nicht alle beendet.",
                                            color = VolleyWhite,
                                            fontSize = 13.sp
                                        )
                                        val swissByes = tournament.specMatches.filter { it.isByeMatch }
                                        if (swissByes.isNotEmpty()) {
                                            Text(
                                                "Freilose: ${swissByes.joinToString(", ") { "${it.roundLabel}: ${teamNames[it.teamAId] ?: it.teamAId}" }}",
                                                color = VolleyTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (allGroupMatchesFinished) {
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = onNextSwissRound,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("btn_next_swiss_round"),
                                                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Nächste Runde paaren", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val filteredGroupMatches = if (selectedScheduleFilter == null) {
                            sortedGroupMatches
                        } else if (selectedScheduleFilter == "KO") {
                            emptyList()
                        } else {
                            sortedGroupMatches.filter { it.groupId == selectedScheduleFilter }
                        }

                        if (filteredGroupMatches.isNotEmpty()) {
                            item {
                                Surface(
                                    color = VolleyDarkSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, VolleyBlue.copy(alpha = 0.65f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val phaseHeader = when (tournament.mode) {
                                            TournamentMode.ROUND_ROBIN -> "LIGA-SPIELPLAN (Jeder gegen Jeden)"
                                            TournamentMode.SWISS_SYSTEM -> "VORRUNDE / SCHWEIZER SYSTEM"
                                            else -> "GRUPPENPHASE (Spiele nur innerhalb jeder Gruppe)"
                                        }
                                        Text(
                                            phaseHeader,
                                            color = VolleyBlue,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${filteredGroupMatches.count { it.status == MatchStatus.FINISHED }} / ${filteredGroupMatches.size} beendet",
                                            color = VolleyTextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            items(filteredGroupMatches, key = { it.id }) { match ->
                                TournamentMatchCard(
                                    match = match,
                                    specMatch = specMatchById[match.id],
                                    specConfig = tournament.specConfig,
                                    teamNames = teamNames,
                                    isKoMatch = false,
                                    showGroupLabel = tournament.mode != TournamentMode.ROUND_ROBIN && tournament.mode != TournamentMode.SWISS_SYSTEM,
                                    onPlayInScoreboard = { onPlayInScoreboard(match) },
                                    onEditScore = { editingMatch = match },
                                    onEditReferee = { specMatchById[match.id]?.let { editingRefereeForMatch = it } }
                                )
                            }
                        }

                        if (sortedKoMatches.isNotEmpty() && (selectedScheduleFilter == null || selectedScheduleFilter == "KO")) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = VolleyYellow.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, VolleyYellow),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (tournament.mode == TournamentMode.PLACEMENT) "PLATZIERUNGSSPIELE" else "K.-O.-PHASE / ENDRUNDE",
                                            color = VolleyYellow,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "${sortedKoMatches.count { it.status == MatchStatus.FINISHED }} / ${sortedKoMatches.size} beendet",
                                            color = VolleyWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            items(sortedKoMatches, key = { it.id }) { match ->
                                TournamentMatchCard(
                                    match = match,
                                    specMatch = specMatchById[match.id],
                                    specConfig = tournament.specConfig,
                                    teamNames = teamNames,
                                    isKoMatch = true,
                                    showGroupLabel = false,
                                    onPlayInScoreboard = { onPlayInScoreboard(match) },
                                    onEditScore = { editingMatch = match },
                                    onEditReferee = { specMatchById[match.id]?.let { editingRefereeForMatch = it } }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val isMultiGroup = tournament.mode != TournamentMode.ROUND_ROBIN &&
                            tournament.mode != TournamentMode.SWISS_SYSTEM &&
                            tournament.mode != TournamentMode.SINGLE_ELIMINATION
                        val sortedGroups = remember(tournament.groupAssignments) {
                            tournament.groupAssignments.keys.sorted()
                        }
                        if (isMultiGroup && sortedGroups.size > 1) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { selectedGroupFilter = null },
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    modifier = Modifier.height(48.dp).testTag("btn_standings_filter_all"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedGroupFilter == null) VolleyYellow else VolleyDarkSurfaceVariant,
                                        contentColor = if (selectedGroupFilter == null) Color.Black else VolleyWhite
                                    ),
                                    border = BorderStroke(1.dp, VolleyGrayBorder),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Alle", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                                }
                                sortedGroups.forEach { grp ->
                                    Button(
                                        onClick = { selectedGroupFilter = grp },
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier.height(48.dp).testTag("btn_standings_filter_$grp"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedGroupFilter == grp) VolleyYellow else VolleyDarkSurfaceVariant,
                                            contentColor = if (selectedGroupFilter == grp) Color.Black else VolleyWhite
                                        ),
                                        border = BorderStroke(1.dp, VolleyGrayBorder),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Gruppe $grp", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        val derivedStats = remember(specTeams, tournament.specMatches, tournament.specConfig) {
                            SpecSchedulerAndReferees.computeDerivedTeamStats(
                                teams = specTeams,
                                matches = tournament.specMatches,
                                config = tournament.specConfig
                            )
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val standingsWarnings = warnings.filter {
                                it.specId.startsWith("W-") || it.specId.startsWith("S-") || it.isError
                            }
                            if (standingsWarnings.isNotEmpty()) {
                                item(key = "standings_warnings") {
                                    SpecWarningsBanner(warnings = standingsWarnings)
                                }
                            }
                            if (!isMultiGroup || selectedGroupFilter == null) {
                                item(key = "overall_standings") {
                                    val rows = TournamentEngine.calculateSpecStandings(
                                        tournament = tournament,
                                        groupId = "",
                                        teamIds = tournament.teamIds,
                                        teamNames = teamNames,
                                        matches = matches
                                    )
                                    val tableTitle = when (tournament.mode) {
                                        TournamentMode.ROUND_ROBIN -> "Liga-Tabelle (Jeder gegen Jeden)"
                                        TournamentMode.SWISS_SYSTEM -> "Gesamttabelle (Schweizer System)"
                                        else -> "Gesamtauswertung (Alle Teams)"
                                    }
                                    SpecStandingsTableCard(
                                        groupName = tableTitle,
                                        groupKey = "OVERALL",
                                        rows = rows,
                                        scoringSystem = tournament.specConfig.scoringSystem,
                                        onResolveTie = { tiedIds -> resolvingTieRequest = "OVERALL" to tiedIds }
                                    )
                                }
                            } else {
                                item(key = "group_${selectedGroupFilter}") {
                                    val grp = selectedGroupFilter!!
                                    val teamIds = tournament.groupAssignments[grp].orEmpty()
                                    val rows = TournamentEngine.calculateSpecStandings(
                                        tournament = tournament,
                                        groupId = grp,
                                        teamIds = teamIds,
                                        teamNames = teamNames,
                                        matches = matches
                                    )
                                    SpecStandingsTableCard(
                                        groupName = "Gruppe $grp",
                                        groupKey = grp,
                                        rows = rows,
                                        scoringSystem = tournament.specConfig.scoringSystem,
                                        onResolveTie = { tiedIds -> resolvingTieRequest = grp to tiedIds }
                                    )
                                }
                            }

                            item(key = "team_stats_card") {
                                SpecTeamStatsCard(stats = derivedStats)
                            }
                        }
                    }
                }
                2 -> {
                    val canAdvanceKo = remember(tournament, koMatches, teamNames) {
                        (tournament.mode == TournamentMode.GROUPS_KO || tournament.mode == TournamentMode.SINGLE_ELIMINATION) &&
                            koMatches.isNotEmpty() &&
                            TournamentEngine.advanceKoRoundIfReady(
                                tournament = tournament,
                                koMatches = koMatches,
                                teamNames = teamNames,
                                courtCount = tournament.courtCount
                            ).isNotEmpty()
                    }
                    val hasUnresolvedGroupTie = remember(tournament, matches, teamNames) {
                        tournament.groupAssignments.any { (gid, tids) ->
                            TournamentEngine.calculateSpecStandings(tournament, gid, tids, teamNames, matches)
                                .any { it.decisionRequired }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (tournament.mode != TournamentMode.SINGLE_ELIMINATION && !allGroupMatchesFinished) {
                            Surface(
                                color = VolleyDarkSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, VolleyRed, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Gruppenphase noch unvollständig!", color = VolleyRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (tournament.mode == TournamentMode.PLACEMENT)
                                            "Platzierungsspiele dürfen erst erzeugt werden, wenn alle Gruppenspiele beendet sind."
                                        else
                                            "K.-o.-Paarungen dürfen erst erzeugt werden, wenn alle Gruppenspiele beendet sind.",
                                        color = VolleyWhite,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else if (hasUnresolvedGroupTie && koMatches.isEmpty()) {
                            Surface(
                                color = VolleyRed.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, VolleyRed, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Gleichstand blockiert K.-o.-Start!", color = VolleyRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "In mindestens einer Gruppe steht „Entscheidung erf.“. Bitte erst unter „Tabelle“ die Reihenfolge klären.",
                                        color = VolleyWhite,
                                        fontSize = 13.sp
                                    )
                                    Button(
                                        onClick = { selectedTab = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Zur Tabelle", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (koMatches.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, VolleyYellow, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Gruppenspiele abgeschlossen!", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (tournament.mode == TournamentMode.PLACEMENT)
                                            "Modus: Direkte Platzierungsspiele (1. vs 1., 2. vs 2., ...)"
                                        else
                                            "Qualifikation: ${tournament.seedingMode.label}",
                                        color = VolleyWhite,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            if (tournament.mode == TournamentMode.PLACEMENT) onGeneratePlacementMatches() else onGenerateKO()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_generate_ko_matches"),
                                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (tournament.mode == TournamentMode.PLACEMENT) "Platzierungsspiele erzeugen" else "K.-o.-Paarungen erzeugen",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        if (canAdvanceKo) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, VolleyGreen, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Aktuelle K.-o.-Runde abgeschlossen!", color = VolleyGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Button(
                                        onClick = onAdvanceKoRound,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_advance_ko_round"),
                                        colors = ButtonDefaults.buttonColors(containerColor = VolleyGreen, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Nächste K.-o.-Runde erzeugen", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }

                        if (podiumCertificates.isNotEmpty()) {
                            PodiumCertificatesBannerCard(
                                certificates = podiumCertificates,
                                onOpenCertificates = onOpenCertificates
                            )
                        }

                        if (koMatches.isNotEmpty()) {
                            Text(
                                if (tournament.mode == TournamentMode.PLACEMENT) "Platzierungsspiele" else "K.-o.-Spiele",
                                color = VolleyYellow,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            koMatches.forEach { match ->
                                TournamentMatchCard(
                                    match = match,
                                    specMatch = specMatchById[match.id],
                                    specConfig = tournament.specConfig,
                                    teamNames = teamNames,
                                    isKoMatch = true,
                                    showGroupLabel = false,
                                    onPlayInScoreboard = { onPlayInScoreboard(match) },
                                    onEditScore = { editingMatch = match },
                                    onEditReferee = { specMatchById[match.id]?.let { editingRefereeForMatch = it } }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // Participating Teams: Add post-creation, Edit, W.O. Withdrawal, Delete
                    val teamGroupLookup = remember(tournament.groupAssignments) {
                        val m = mutableMapOf<String, String>()
                        tournament.groupAssignments.forEach { (g, ids) ->
                            ids.forEach { id -> m[id] = g }
                        }
                        m
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Button(
                                onClick = { addingPostTeam = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_add_post_creation_team"),
                                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text("Team nachträglich hinzufügen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        if (tournament.manualTeams.isNotEmpty()) {
                            items(tournament.manualTeams, key = { it.id }) { team ->
                                val grp = teamGroupLookup[team.id]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            if (team.withdrawn) VolleyRed.copy(alpha = 0.7f) else VolleyGrayBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    team.name,
                                                    color = if (team.withdrawn) VolleyRed else VolleyYellow,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (grp != null && grp != "SWISS" && grp != "KO") {
                                                    Surface(color = VolleyBlue.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            "Gr. $grp",
                                                            color = VolleyBlue,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (team.withdrawn) {
                                                    Surface(color = VolleyRed.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            "W.O.",
                                                            color = VolleyRed,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                "Kürzel: ${team.shortName.ifBlank { "-" }} · Skill ${team.skill}/5 (${team.skillLevel}) · ${team.maleCount}M/${team.femaleCount}W",
                                                color = VolleyWhite,
                                                fontSize = 12.sp
                                            )
                                            if (team.club.isNotBlank() || team.note.isNotBlank()) {
                                                Text(
                                                    listOfNotNull(
                                                        if (team.club.isNotBlank()) "Verein: ${team.club}" else null,
                                                        if (team.note.isNotBlank()) "Notiz: ${team.note}" else null
                                                    ).joinToString(" · "),
                                                    color = VolleyTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { editingManualTeam = team },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .testTag("btn_edit_tournament_team_${team.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Team bearbeiten", tint = VolleyYellow)
                                        }
                                        IconButton(
                                            onClick = { onRemoveTeam(team.id) },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .testTag("btn_remove_tournament_team_${team.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Team entfernen", tint = VolleyRed)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(tournament.teamIds, key = { it }) { tid ->
                                val teamObj = teams.find { it.id == tid }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                teamObj?.name ?: "Team",
                                                color = VolleyYellow,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "Format: ${tournament.fieldFormat.label} · Kader: ${teamObj?.playerIds?.size ?: 0} Spieler",
                                                color = VolleyWhite,
                                                fontSize = 14.sp
                                            )
                                        }

                                        if (teamObj != null) {
                                            IconButton(
                                                onClick = { editingMixerTeam = teamObj },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("btn_edit_mixer_team_${teamObj.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Team umbenennen", tint = VolleyYellow)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    TournamentSelfTestTab(
                        tournament = tournament,
                        report = selfTestReport,
                        isRunning = selfTestRunning,
                        progress = selfTestProgress,
                        onStartSelfTest = onStartSelfTest
                    )
                }
            }
        }
    }

    // Match Result Entry Dialog (with SPEC pointCap, TIMED / FIXED_SETS & Forfeit support)
    editingMatch?.let { match ->
        MatchResultDialog(
            match = match,
            specConfig = tournament.specConfig,
            onDismiss = { editingMatch = null },
            onSave = { sets ->
                onEnterDirectResult(match, sets)
                editingMatch = null
            },
            onForfeit = { winnerSide ->
                onForfeitMatch(match, winnerSide)
                editingMatch = null
            }
        )
    }

    editingRefereeForMatch?.let { sm ->
        ManualRefereeDialog(
            targetMatch = sm,
            allMatches = tournament.specMatches,
            teams = specTeams,
            teamNames = teamNames,
            onDismiss = { editingRefereeForMatch = null },
            onSaveReferees = { newRefs ->
                onChangeMatchReferee(sm.id, newRefs)
                editingRefereeForMatch = null
            }
        )
    }

    if (showAuditDialog) {
        TournamentAuditLogDialog(
            entries = tournament.specConfig.auditLog,
            onDismiss = { showAuditDialog = false }
        )
    }

    resolvingTieRequest?.let { (grpKey, tiedIds) ->
        ManualTieBreakDialog(
            groupKey = grpKey,
            tiedTeamIds = tiedIds,
            teamNames = teamNames,
            onDismiss = { resolvingTieRequest = null },
            onSaveOrder = { ordered ->
                onResolveManualTie(grpKey, ordered)
                resolvingTieRequest = null
            }
        )
    }

    if (addingPostTeam) {
        ManualTeamEntryDialog(
            nextIndex = tournament.teamIds.size + 1,
            initialTeam = null,
            onDismiss = { addingPostTeam = false },
            onAdd = { added ->
                onAddPostCreationTeam(added)
                addingPostTeam = false
            }
        )
    }

    // Edit Manual Tournament Team afterwards
    editingManualTeam?.let { teamToEdit ->
        ManualTeamEntryDialog(
            nextIndex = 1,
            initialTeam = teamToEdit,
            onDismiss = { editingManualTeam = null },
            onAdd = { updated ->
                onUpdateManualTeam(updated)
                editingManualTeam = null
            }
        )
    }

    // Rename Mixer Tournament Team afterwards
    editingMixerTeam?.let { mixerTeam ->
        var newName by remember(mixerTeam.id) { mutableStateOf(mixerTeam.name) }
        VolleyFormDialog(
            onDismissRequest = { editingMixerTeam = null },
            title = {
                Text("Team umbenennen", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editingMixerTeam = null },
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
                        onRenameMixerTeam(mixerTeam.id, newName)
                        editingMixerTeam = null
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Speichern", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Teamname", color = VolleyWhite) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VolleyWhite,
                    unfocusedTextColor = VolleyWhite,
                    focusedBorderColor = VolleyYellow,
                    unfocusedBorderColor = VolleyGrayBorder
                ),
                modifier = Modifier.fillMaxWidth().testTag("input_rename_mixer_team")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentMatchCard(
    match: Match,
    specMatch: SpecMatch? = null,
    specConfig: SpecTournamentConfig = SpecTournamentConfig(),
    teamNames: Map<String, String> = emptyMap(),
    isKoMatch: Boolean = match.groupId == "KO",
    showGroupLabel: Boolean = true,
    onPlayInScoreboard: () -> Unit,
    onEditScore: () -> Unit,
    onEditReferee: () -> Unit = {}
) {
    val isForfeit = specMatch?.isForfeit == true
    val borderColor = when {
        isForfeit -> VolleyRed
        match.status == MatchStatus.FINISHED -> VolleyGreen
        isKoMatch -> VolleyYellow
        else -> VolleyGrayBorder
    }
    val courtDisp = specMatch?.courtNumber ?: match.courtNumber
    val slotStr = if (specMatch?.slotIndex != null) {
        val tStr = SpecSchedulerAndReferees.formatSlotTime(
            slotIndex = specMatch.slotIndex,
            config = specConfig,
            isScheduledFuture = match.status != MatchStatus.FINISHED
        )
        "Slot ${specMatch.slotIndex + 1} ($tStr)"
    } else null

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isKoMatch) VolleyDarkSurfaceVariant else VolleyDarkSurface
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isKoMatch) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isKoMatch) {
                    Surface(color = VolleyYellow, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "K.-o.",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (isForfeit) {
                    Surface(color = VolleyRed, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "W.O.",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (slotStr != null) {
                    Surface(color = VolleyDarkSurfaceVariant, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, VolleyGrayBorder)) {
                        Text(
                            slotStr,
                            color = VolleyWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (courtDisp != null) {
                    Surface(
                        color = VolleyYellow.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, VolleyYellow)
                    ) {
                        Text(
                            "Feld $courtDisp",
                            color = VolleyYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (match.tournamentRound != null || match.groupId != null) {
                    val grpSuffix = if (showGroupLabel && match.groupId != null && match.groupId != "KO" && match.groupId != "SWISS") {
                        "(Gruppe ${match.groupId})"
                    } else ""
                    Text(
                        "${match.tournamentRound ?: ""} $grpSuffix".trim(),
                        color = VolleyYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "${match.teamAName}  vs  ${match.teamBName}",
                color = VolleyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            val setsSummary = if (match.completedSets.isNotEmpty()) {
                match.completedSets.joinToString(", ") { "${it.scoreA}:${it.scoreB}" }
            } else {
                "${match.scoreA} : ${match.scoreB}"
            }
            Text(
                "Ergebnis: $setsSummary · Status: ${if (isForfeit) "W.O." else match.status.label}",
                color = if (match.status == MatchStatus.FINISHED) VolleyGreen else VolleyTextSecondary,
                fontSize = 13.sp
            )

            if (specMatch != null && specConfig.refereeMode != RefereeMode.OFF) {
                val refLabel = when {
                    specConfig.refereeMode == RefereeMode.EXTERNAL -> "Extern"
                    specMatch.refereeTeamIds.isEmpty() -> "kein SR zugewiesen"
                    else -> specMatch.refereeTeamIds.joinToString(", ") { teamNames[it] ?: it }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "SR: $refLabel${if (specMatch.refereeManualLocked) " (fix)" else ""}",
                        color = VolleyTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (specConfig.refereeMode != RefereeMode.EXTERNAL) {
                        TextButton(
                            onClick = onEditReferee,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("SR ändern", color = VolleyYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayInScoreboard,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_scoreboard_match_${match.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("Scoreboard", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = onEditScore,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_edit_match_${match.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = VolleyYellow)
                    Spacer(Modifier.width(4.dp))
                    Text(if (match.status == MatchStatus.FINISHED) "Ändern" else "Direkt", fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchResultDialog(
    match: Match,
    specConfig: SpecTournamentConfig = SpecTournamentConfig(),
    onDismiss: () -> Unit,
    onSave: (sets: List<SetResult>) -> Unit,
    onForfeit: (winnerSide: TeamSide) -> Unit = {}
) {
    val setsMode = match.matchConfig.setsMode
    val pointsPerSet = match.matchConfig.pointsPerSet
    val winByTwo = match.matchConfig.winByTwo
    val isTimed = match.matchConfig.timeLimitMinutes != null || specConfig.validationMode == MatchValidationMode.TIMED
    val isFixedSets = setsMode == SetsMode.BEST_OF_2 || specConfig.validationMode == MatchValidationMode.FIXED_SETS
    val decidingSetPoints = if (pointsPerSet <= 15) pointsPerSet else specConfig.decidingSetPoints

    val maxSetsPossible = when {
        isTimed -> 1
        setsMode == SetsMode.ONE_SET -> 1
        setsMode == SetsMode.BEST_OF_2 -> 2
        setsMode == SetsMode.BEST_OF_3 -> 3
        else -> 5
    }

    val initialSets = match.completedSets
    val scoresA = remember(match.id) {
        mutableStateListOf(
            initialSets.getOrNull(0)?.scoreA ?: pointsPerSet,
            initialSets.getOrNull(1)?.scoreA ?: (if (setsMode == SetsMode.BEST_OF_2) (pointsPerSet - 2).coerceAtLeast(0) else pointsPerSet),
            initialSets.getOrNull(2)?.scoreA ?: (if (setsMode == SetsMode.BEST_OF_5) pointsPerSet else decidingSetPoints),
            initialSets.getOrNull(3)?.scoreA ?: pointsPerSet,
            initialSets.getOrNull(4)?.scoreA ?: decidingSetPoints
        )
    }
    val scoresB = remember(match.id) {
        mutableStateListOf(
            initialSets.getOrNull(0)?.scoreB ?: (pointsPerSet - 2).coerceAtLeast(0),
            initialSets.getOrNull(1)?.scoreB ?: (if (setsMode == SetsMode.BEST_OF_2) pointsPerSet else (pointsPerSet - 2).coerceAtLeast(0)),
            initialSets.getOrNull(2)?.scoreB ?: (if (setsMode == SetsMode.BEST_OF_5) (pointsPerSet - 2).coerceAtLeast(0) else (decidingSetPoints - 2).coerceAtLeast(0)),
            initialSets.getOrNull(3)?.scoreB ?: (pointsPerSet - 2).coerceAtLeast(0),
            initialSets.getOrNull(4)?.scoreB ?: (decidingSetPoints - 2).coerceAtLeast(0)
        )
    }

    fun targetForSet(setIndex: Int): Int {
        return if (!isTimed && setsMode == SetsMode.BEST_OF_3 && setIndex == 2) decidingSetPoints
        else if (!isTimed && setsMode == SetsMode.BEST_OF_5 && setIndex == 4) decidingSetPoints
        else pointsPerSet
    }

    fun isSingleSetValid(scoreA: Int, scoreB: Int, target: Int): Boolean {
        if (isTimed) return SpecScoring.isSetValid(scoreA, scoreB, target, cap = specConfig.pointCap, isTimed = true)
        if (!winByTwo) return scoreA >= 0 && scoreB >= 0 && scoreA != scoreB && maxOf(scoreA, scoreB) == target
        return SpecScoring.isSetValid(
            scoreA = scoreA,
            scoreB = scoreB,
            target = target,
            cap = specConfig.pointCap,
            isTimed = false
        )
    }

    val (visibleSetsCount, isMatchValid, validationMessage) = remember(
        setsMode,
        isTimed,
        isFixedSets,
        specConfig.pointCap,
        scoresA.toList(),
        scoresB.toList()
    ) {
        var winsA = 0
        var winsB = 0
        var countShown = 0
        var firstError: String? = null
        var matchComplete = false

        for (i in 0 until maxSetsPossible) {
            countShown = i + 1
            val target = targetForSet(i)
            val sA = scoresA[i]
            val sB = scoresB[i]
            if (!isSingleSetValid(sA, sB, target)) {
                firstError = if (isTimed) {
                    "Zeitspiel: Mindestens 1 Punkt eingeben."
                } else {
                    "Satz ${i + 1} ungültig (Ziel $target Pkt${if (winByTwo) ", 2 Pkt Vorsprung" else ""}${if (specConfig.pointCap != null) ", Cap ${specConfig.pointCap}" else ""})."
                }
                break
            }
            if (sA > sB) winsA++ else if (sB > sA) winsB++

            if (isTimed || setsMode == SetsMode.ONE_SET || isFixedSets) {
                if (countShown == maxSetsPossible) matchComplete = true
            } else {
                if (winsA >= setsMode.setsToWin || winsB >= setsMode.setsToWin) {
                    matchComplete = true
                    break
                }
            }
        }

        if (firstError == null && !matchComplete) {
            firstError = "Bitte alle erforderlichen Sätze vollständig eintragen."
        }
        Triple(countShown, matchComplete && firstError == null, firstError)
    }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Ergebnis direkt eintragen", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sets = (0 until visibleSetsCount).map { idx ->
                        val sA = scoresA[idx]
                        val sB = scoresB[idx]
                        SetResult(
                            setNumber = idx + 1,
                            scoreA = sA,
                            scoreB = sB,
                            winner = if (sA > sB) TeamSide.TEAM_A else TeamSide.TEAM_B
                        )
                    }
                    onSave(sets)
                },
                enabled = isMatchValid,
                modifier = Modifier.height(48.dp).testTag("btn_save_direct_match_result"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Ergebnis speichern", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    ) {
        Text("${match.teamAName}  vs  ${match.teamBName}", color = VolleyWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            if (isTimed) "Modus: Zeitspiel (Unentschieden möglich)"
            else "Modus: ${setsMode.label} · Ziel: $pointsPerSet${if (winByTwo) " (2 Pkt)" else ""}${if (specConfig.pointCap != null) " · Cap ${specConfig.pointCap}" else ""}",
            color = VolleyTextSecondary,
            fontSize = 12.sp
        )

        for (i in 0 until visibleSetsCount) {
            val target = targetForSet(i)
            val sA = scoresA[i]
            val sB = scoresB[i]
            val valid = isSingleSetValid(sA, sB, target)
            val winnerName = if (sA > sB) match.teamAName else if (sB > sA) match.teamBName else "Remis"
            val isDeciding = !isTimed && ((setsMode == SetsMode.BEST_OF_3 && i == 2) || (setsMode == SetsMode.BEST_OF_5 && i == 4))

            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (valid) VolleyGreen else VolleyGrayBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (isTimed) "Zeitspiel-Ergebnis" else if (isDeciding) "Entscheidungssatz ${i + 1} (bis $target)" else "Satz ${i + 1} (bis $target)",
                            color = VolleyYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (valid) {
                            Text("Gültig ($winnerName)", color = VolleyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    SetScoreRow(
                        teamAName = match.teamAName,
                        scoreA = sA,
                        onScoreAChange = { scoresA[i] = it },
                        teamBName = match.teamBName,
                        scoreB = sB,
                        onScoreBChange = { scoresB[i] = it }
                    )
                }
            }
        }

        if (validationMessage != null) {
            Text(validationMessage, color = VolleyRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        if (match.tournamentId != null) {
            HorizontalDivider(color = VolleyGrayBorder.copy(alpha = 0.5f))
            Text("W.O. / Forfeit-Wertung:", color = VolleyTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onForfeit(TeamSide.TEAM_A) },
                    border = BorderStroke(1.dp, VolleyRed.copy(alpha = 0.7f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("W.O. Sieg: ${match.teamAName.take(12)}", color = VolleyRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onForfeit(TeamSide.TEAM_B) },
                    border = BorderStroke(1.dp, VolleyRed.copy(alpha = 0.7f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("W.O. Sieg: ${match.teamBName.take(12)}", color = VolleyRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SetScoreRow(
    teamAName: String,
    scoreA: Int,
    onScoreAChange: (Int) -> Unit,
    teamBName: String,
    scoreB: Int,
    onScoreBChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                teamAName,
                color = VolleyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                VolleyStepperButton(isIncrement = false, enabled = scoreA > 0, onClick = { if (scoreA > 0) onScoreAChange(scoreA - 1) })
                Text(
                    "$scoreA",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
                VolleyStepperButton(isIncrement = true, onClick = { onScoreAChange(scoreA + 1) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                teamBName,
                color = VolleyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                VolleyStepperButton(isIncrement = false, enabled = scoreB > 0, onClick = { if (scoreB > 0) onScoreBChange(scoreB - 1) })
                Text(
                    "$scoreB",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
                VolleyStepperButton(isIncrement = true, onClick = { onScoreBChange(scoreB + 1) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTournamentDialog(
    availableTeams: List<Team>,
    viewModel: VolleyViewModel,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        sport: SportType,
        format: FieldFormat,
        config: MatchConfig,
        mode: TournamentMode,
        groupsCount: Int,
        seeding: SeedingMode,
        manualTeams: List<ManualTournamentTeam>,
        useMixerTeams: Boolean,
        location: String,
        date: String,
        courtCount: Int,
        templateId: String?,
        pointCap: Int?,
        pointsForWin: Int,
        pointsForDraw: Int,
        pointsForLoss: Int,
        minFemales: Int,
        minMales: Int,
        doubleRoundRobin: Boolean,
        specConfigOverride: SpecTournamentConfig
    ) -> Unit
) {
    val templates = TournamentTemplate.ALL
    var templateCategoryFilter by remember { mutableStateOf<SportType?>(null) }
    var selectedTemplate by remember { mutableStateOf<TournamentTemplate?>(templates[0]) }

    var name by remember { mutableStateOf(templates[0].name) }
    var sport by remember { mutableStateOf(templates[0].sportType) }
    var format by remember { mutableStateOf(templates[0].fieldFormat) }
    var mode by remember { mutableStateOf(templates[0].mode) }
    var setsMode by remember { mutableStateOf(templates[0].setsMode) }
    var pointsPerSet by remember { mutableIntStateOf(templates[0].pointsPerSet) }
    var winByTwo by remember { mutableStateOf(templates[0].winByTwo) }
    var decidingSetPoints by remember { mutableIntStateOf(templates[0].decidingSetPoints) }
    var pointCap by remember { mutableStateOf<Int?>(templates[0].pointCap) }
    var timeLimitMinutes by remember { mutableStateOf<Int?>(templates[0].timeLimitMinutes) }
    var courtCount by remember { mutableIntStateOf(templates[0].defaultCourts) }
    var courtCountManuallyChanged by remember { mutableStateOf(false) }
    var minFemales by remember { mutableIntStateOf(templates[0].minFemales) }
    var minMales by remember { mutableIntStateOf(templates[0].minMales) }
    var pointsForWin by remember { mutableIntStateOf(templates[0].pointsForWin) }
    var pointsForDraw by remember { mutableIntStateOf(templates[0].pointsForDraw) }
    var pointsForLoss by remember { mutableIntStateOf(templates[0].pointsForLoss) }
    var groupsCount by remember { mutableIntStateOf(2) }
    var seeding by remember { mutableStateOf(SeedingMode.CROSS_SEEDING) }
    var doubleRoundRobin by remember { mutableStateOf(false) }

    // Additional SPEC v1.0 controls in expert accordion
    var scoringSystem by remember { mutableStateOf(ScoringSystem.DVV3) }
    var tieBreakPreset by remember { mutableStateOf(TieBreakPreset.DEFAULT_SPEC) }
    var withdrawalPolicy by remember { mutableStateOf(WithdrawalPolicy.FORFEIT_REST) }
    var zeroDivisorPolicy by remember { mutableStateOf(ZeroDivisorPolicy.NUMERATOR) }
    var groupComparisonPolicy by remember { mutableStateOf(GroupComparisonPolicy.DROP_LAST) }
    var restStrictness by remember { mutableStateOf(RestStrictness.BALANCED) }
    var minRestSlots by remember { mutableIntStateOf(1) }
    var refereeMode by remember { mutableStateOf(RefereeMode.AUTO_FAIR) }
    var consolationMode by remember { mutableStateOf(ConsolationMode.THIRD) }
    var slotDurationMinutes by remember { mutableIntStateOf(35) }
    var seedText by remember { mutableStateOf("424242") }

    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    var useMixerTeams by remember { mutableStateOf(availableTeams.isNotEmpty()) }
    val manualTeams = remember { mutableStateListOf<ManualTournamentTeam>() }
    var editingManualIndex by remember { mutableStateOf<Int?>(null) }

    var showExpertSettings by remember { mutableStateOf(false) }
    var showAddManualTeamDialog by remember { mutableStateOf(false) }
    var showClipboardImportDialog by remember { mutableStateOf(false) }
    var quickGenNotice by remember { mutableStateOf<String?>(null) }

    fun applyTemplate(t: TournamentTemplate) {
        selectedTemplate = t
        sport = t.sportType
        format = t.fieldFormat
        mode = t.mode
        setsMode = t.setsMode
        pointsPerSet = t.pointsPerSet
        winByTwo = t.winByTwo
        decidingSetPoints = t.decidingSetPoints
        pointCap = t.pointCap
        timeLimitMinutes = t.timeLimitMinutes
        // NEVER clobber user's manual court count selection!
        if (!courtCountManuallyChanged) {
            courtCount = t.defaultCourts
        }
        minFemales = t.minFemales
        minMales = t.minMales
        pointsForWin = t.pointsForWin
        pointsForDraw = t.pointsForDraw
        pointsForLoss = t.pointsForLoss
        scoringSystem = if (t.pointsForWin >= 3) ScoringSystem.DVV3 else ScoringSystem.CLASSIC2
        name = t.name
    }

    val totalTeams = if (useMixerTeams) availableTeams.size else manualTeams.size
    val canStart = totalTeams >= 2

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Neues Turnier anlegen", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = MatchConfig(
                        fieldFormatA = format,
                        fieldFormatB = format,
                        setsMode = setsMode,
                        pointsPerSet = pointsPerSet,
                        winByTwo = winByTwo,
                        timeLimitMinutes = timeLimitMinutes
                    )
                    val specOverride = SpecTournamentConfig(
                        schemaVersion = 2,
                        seed = seedText.toIntOrNull() ?: 424242,
                        scoringSystem = scoringSystem,
                        validationMode = when {
                            timeLimitMinutes != null -> MatchValidationMode.TIMED
                            setsMode == SetsMode.BEST_OF_2 -> MatchValidationMode.FIXED_SETS
                            else -> MatchValidationMode.BEST_OF
                        },
                        setsToWin = setsMode.setsToWin,
                        fixedSetsCount = if (setsMode == SetsMode.BEST_OF_2) 2 else 1,
                        pointsPerSet = pointsPerSet,
                        decidingSetPoints = decidingSetPoints,
                        pointCap = pointCap,
                        withdrawalPolicy = withdrawalPolicy,
                        zeroDivisorPolicy = zeroDivisorPolicy,
                        tieBreakPreset = tieBreakPreset,
                        groupComparisonPolicy = groupComparisonPolicy,
                        consolationMode = consolationMode,
                        doubleRoundRobin = doubleRoundRobin,
                        restStrictness = restStrictness,
                        minRestSlots = minRestSlots,
                        refereeMode = refereeMode,
                        slotDurationMinutes = slotDurationMinutes
                    )
                    onConfirm(
                        if (name.isBlank()) "Turnier ${format.label}" else name,
                        sport,
                        format,
                        config,
                        mode,
                        groupsCount,
                        seeding,
                        manualTeams.toList(),
                        useMixerTeams,
                        location.trim(),
                        date.trim(),
                        courtCount,
                        selectedTemplate?.id,
                        pointCap,
                        pointsForWin,
                        pointsForDraw,
                        pointsForLoss,
                        minFemales,
                        minMales,
                        doubleRoundRobin,
                        specOverride
                    )
                },
                enabled = canStart,
                modifier = Modifier.height(48.dp).testTag("btn_confirm_create_tournament"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Turnier starten", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Turniername", color = VolleyWhite) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_tournament_name")
        )

        // 1. Vorlage auswählen
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "1. Vorlage:",
                color = VolleyYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf<Pair<SportType?, String>>(null to "Alle", SportType.BEACH to "Beach", SportType.INDOOR to "Halle").forEach { (filterCat, lbl) ->
                    FilterChip(
                        selected = templateCategoryFilter == filterCat,
                        onClick = { templateCategoryFilter = filterCat },
                        label = { Text(lbl, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VolleyYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = VolleyDarkSurfaceVariant,
                            labelColor = VolleyWhite
                        )
                    )
                }
            }
        }

        val filteredTemplates = if (templateCategoryFilter == null) templates else templates.filter { it.sportType == templateCategoryFilter || it.sportType == SportType.ANY }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            filteredTemplates.forEach { t ->
                val isSelected = selectedTemplate?.id == t.id
                Card(
                    onClick = { applyTemplate(t) },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) VolleyYellow.copy(alpha = 0.15f) else VolleyDarkSurfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, if (isSelected) VolleyYellow else VolleyGrayBorder, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.name, color = if (isSelected) VolleyYellow else VolleyWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(t.subtitle, color = VolleyTextSecondary, fontSize = 12.sp, maxLines = 2)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = VolleyYellow)
                        }
                    }
                }
            }
        }

        // 2. Sätze & Zeitspiel
        Text("2. Sätze & Zeitspiel:", color = VolleyYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Gewinnsätze: ${setsMode.label}", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
            ).forEach { (sm, lbl) ->
                Button(
                    onClick = { setsMode = sm },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.widthIn(min = 76.dp).height(48.dp).testTag("btn_sets_mode_${sm.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (setsMode == sm) VolleyYellow else VolleyDarkSurfaceVariant,
                        contentColor = if (setsMode == sm) Color.Black else VolleyWhite
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        Text("Zeitspiel: ${if (timeLimitMinutes == null) "Aus (nach Punkten)" else "$timeLimitMinutes Min"}", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf<Pair<Int?, String>>(null to "Aus", 8 to "8 Min", 12 to "12 Min", 15 to "15 Min").forEach { (tl, lbl) ->
                Button(
                    onClick = { timeLimitMinutes = tl },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.widthIn(min = 72.dp).height(48.dp).testTag("btn_basic_time_${tl ?: "off"}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (timeLimitMinutes == tl) VolleyYellow else VolleyDarkSurfaceVariant,
                        contentColor = if (timeLimitMinutes == tl) Color.Black else VolleyWhite
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        // 3. Mannschaften
        Text("3. Mannschaften / Teams:", color = VolleyYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { useMixerTeams = true },
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.widthIn(min = 124.dp).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (useMixerTeams) VolleyYellow else VolleyDarkSurfaceVariant,
                    contentColor = if (useMixerTeams) Color.Black else VolleyWhite
                ),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Mixer (${availableTeams.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            Button(
                onClick = { useMixerTeams = false },
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.widthIn(min = 124.dp).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!useMixerTeams) VolleyYellow else VolleyDarkSurfaceVariant,
                    contentColor = if (!useMixerTeams) Color.Black else VolleyWhite
                ),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Manuell (${manualTeams.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val generated = viewModel.generateRandomTestTournamentTeams(17)
                    manualTeams.clear()
                    manualTeams.addAll(generated)
                    useMixerTeams = false
                    quickGenNotice = "17 Test-Teams generiert (nicht im Spielerpool angelegt)"
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(48.dp).testTag("btn_generate_17_test_teams"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("17 Teams", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            Button(
                onClick = { showClipboardImportDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(48.dp).testTag("btn_import_clipboard_teams"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zwischenablage", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            Button(
                onClick = {
                    editingManualIndex = null
                    showAddManualTeamDialog = true
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(48.dp).testTag("btn_add_manual_team"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = VolleyWhite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Team", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }

        if (quickGenNotice != null) {
            Text(quickGenNotice!!, color = VolleyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Editable Pre-Creation Manual Team List (with Edit & Delete buttons per team!)
        if (!useMixerTeams && manualTeams.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${manualTeams.size} manuelle Teams (direkt bearbeitbar):", color = VolleyYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    manualTeams.forEachIndexed { idx, mt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${idx + 1}. ${mt.name} (S${mt.skill}${if (mt.club.isNotBlank()) " · ${mt.club}" else ""})",
                                color = VolleyWhite,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    editingManualIndex = idx
                                    showAddManualTeamDialog = true
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Team bearbeiten", tint = VolleyYellow, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { manualTeams.removeAt(idx) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Team löschen", tint = VolleyRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Turniermodus direkt in der Grundansicht + Vollständig klickbare Trainer-Empfehlung!
        Text("Turniermodus:", color = VolleyYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                TournamentMode.GROUPS_KO,
                TournamentMode.ROUND_ROBIN,
                TournamentMode.SWISS_SYSTEM,
                TournamentMode.SINGLE_ELIMINATION,
                TournamentMode.PLACEMENT
            ).forEach { tm ->
                Button(
                    onClick = { mode = tm },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(44.dp).testTag("btn_basic_mode_${tm.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == tm) VolleyYellow else VolleyDarkSurfaceVariant,
                        contentColor = if (mode == tm) Color.Black else VolleyWhite
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(tm.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        if (totalTeams >= 2) {
            val advice = remember(totalTeams, courtCount, slotDurationMinutes) {
                SpecRoundRobinAndSwiss.coachRecommendation(
                    teamCount = totalTeams,
                    courtCount = courtCount,
                    slotDurationMinutes = slotDurationMinutes
                )
            }
            val isApplied = mode == advice.recommendedMode &&
                (advice.recommendedMode != TournamentMode.GROUPS_KO || groupsCount == advice.recommendedGroups)
            val applyAdvice = {
                mode = advice.recommendedMode
                if (advice.recommendedMode == TournamentMode.GROUPS_KO) {
                    groupsCount = advice.recommendedGroups.coerceIn(2, 4)
                }
            }
            Surface(
                color = if (isApplied) VolleyGreen.copy(alpha = 0.14f) else VolleyYellow.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, if (isApplied) VolleyGreen else VolleyYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { applyAdvice() }
                    .testTag("card_coach_recommendation")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Trainer-Empfehlung: ${advice.headline}",
                            color = if (isApplied) VolleyGreen else VolleyYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(advice.explanation, color = VolleyWhite, fontSize = 11.sp)
                        Text(
                            "Schätzung: ~${advice.feasibility.estimatedSlots} Slots · ~${advice.feasibility.estimatedTotalMinutes} Min bei $courtCount Feld(ern)",
                            color = VolleyTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = applyAdvice,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isApplied) VolleyGreen else VolleyYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(40.dp).testTag("btn_apply_coach_recommendation")
                    ) {
                        Text(
                            if (isApplied) "Aktiv ✓" else "Übernehmen",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 4. Datum, Ort & Anzahl Spielfelder (Stepper + Presets)
        Text("4. Datum, Ort & Spielfelder:", color = VolleyYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 380.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Ort / Anlage", color = VolleyWhite) },
                        placeholder = { Text("z.B. Beachcenter", color = VolleyTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("input_tournament_location")
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Datum", color = VolleyWhite) },
                        placeholder = { Text("z.B. Heute", color = VolleyTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("input_tournament_date")
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Ort / Anlage", color = VolleyWhite) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_tournament_location")
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Datum", color = VolleyWhite) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_tournament_date")
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Anzahl Plätze / Felder: $courtCount", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                VolleyStepperButton(
                    isIncrement = false,
                    enabled = courtCount > 1,
                    testTagName = "btn_courts_dec",
                    onClick = {
                        if (courtCount > 1) {
                            courtCount--
                            courtCountManuallyChanged = true
                        }
                    }
                )
                Text(
                    "$courtCount",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center
                )
                VolleyStepperButton(
                    isIncrement = true,
                    enabled = courtCount < 16,
                    testTagName = "btn_courts_inc",
                    onClick = {
                        if (courtCount < 16) {
                            courtCount++
                            courtCountManuallyChanged = true
                        }
                    }
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1, 2, 3, 4, 6, 8).forEach { c ->
                Button(
                    onClick = {
                        courtCount = c
                        courtCountManuallyChanged = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.widthIn(min = 48.dp).height(48.dp).testTag("btn_courts_preset_$c"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (courtCount == c) VolleyYellow else VolleyDarkSurfaceVariant,
                        contentColor = if (courtCount == c) Color.Black else VolleyWhite
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("$c", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        // 5. Expertenansicht (Accordion)
        Card(
            onClick = { showExpertSettings = !showExpertSettings },
            colors = CardDefaults.cardColors(containerColor = VolleyDarkSurfaceVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Erweiterte Einstellungen (Expertenansicht)",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (showExpertSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = VolleyYellow
                )
            }
        }

        if (showExpertSettings) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VolleyDarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Satzpunkte (Ziel): $pointsPerSet", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(11, 15, 21, 25).forEach { pts ->
                        Button(
                            onClick = { pointsPerSet = pts },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.widthIn(min = 58.dp).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pointsPerSet == pts) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (pointsPerSet == pts) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("$pts", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Text("Mindestvorsprung & Punktcap:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(true to "2 Pkt Vorsprung", false to "1 Pkt").forEach { (wbt, lbl) ->
                        Button(
                            onClick = { winByTwo = wbt },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (winByTwo == wbt) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (winByTwo == wbt) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    listOf<Pair<Int?, String>>(null to "Cap Aus", 21 to "Cap 21", 28 to "Cap 28", 30 to "Cap 30").forEach { (cap, lbl) ->
                        Button(
                            onClick = { pointCap = cap },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pointCap == cap) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (pointCap == cap) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (mode == TournamentMode.ROUND_ROBIN) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hin- & Rückrunde:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Jedes Team spielt zweimal gegeneinander", color = VolleyTextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = doubleRoundRobin, onCheckedChange = { doubleRoundRobin = it })
                    }
                }

                if (mode == TournamentMode.GROUPS_KO || mode == TournamentMode.PLACEMENT) {
                    Text("Anzahl Gruppen: $groupsCount (Spiele nur innerhalb der Gruppe)", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 4).forEach { g ->
                            Button(
                                onClick = { groupsCount = g },
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.widthIn(min = 56.dp).height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (groupsCount == g) VolleyYellow else VolleyDarkSurface,
                                    contentColor = if (groupsCount == g) Color.Black else VolleyWhite
                                ),
                                border = BorderStroke(1.dp, VolleyGrayBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("$g", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                if (mode != TournamentMode.ROUND_ROBIN && mode != TournamentMode.SWISS_SYSTEM) {
                    Text("Seeding & Trennung:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeedingMode.values().forEach { sm ->
                            Button(
                                onClick = { seeding = sm },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (seeding == sm) VolleyYellow else VolleyDarkSurface,
                                    contentColor = if (seeding == sm) Color.Black else VolleyWhite
                                ),
                                border = BorderStroke(1.dp, VolleyGrayBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(sm.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text("Punktesystem:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ScoringSystem.DVV3, ScoringSystem.CLASSIC2, ScoringSystem.FIXED_SETS).forEach { ss ->
                        Button(
                            onClick = {
                                scoringSystem = ss
                                pointsForWin = if (ss == ScoringSystem.DVV3) 3 else 2
                                pointsForDraw = 1
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scoringSystem == ss) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (scoringSystem == ss) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(ss.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Tie-Break-Reihenfolge:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TieBreakPreset.values().forEach { tp ->
                        Button(
                            onClick = { tieBreakPreset = tp },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tieBreakPreset == tp) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (tieBreakPreset == tp) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(tp.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Pausen & Zeitplan-Striktigkeit:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RestStrictness.values().forEach { rs ->
                        Button(
                            onClick = { restStrictness = rs },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (restStrictness == rs) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (restStrictness == rs) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(rs.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Schiedsrichter-Modus:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RefereeMode.values().forEach { rm ->
                        Button(
                            onClick = { refereeMode = rm },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (refereeMode == rm) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (refereeMode == rm) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(rm.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Rückzug / Disqualifikation:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WithdrawalPolicy.values().forEach { wp ->
                        Button(
                            onClick = { withdrawalPolicy = wp },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (withdrawalPolicy == wp) VolleyYellow else VolleyDarkSurface,
                                contentColor = if (withdrawalPolicy == wp) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, VolleyGrayBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(wp.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = seedText,
                    onValueChange = { seedText = it.filter { ch -> ch.isDigit() }.take(9) },
                    label = { Text("Zufalls-Seed (deterministisch)", color = VolleyWhite) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VolleyWhite,
                        unfocusedTextColor = VolleyWhite,
                        focusedBorderColor = VolleyYellow,
                        unfocusedBorderColor = VolleyGrayBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showClipboardImportDialog) {
        ClipboardImportDialog(
            viewModel = viewModel,
            onDismiss = { showClipboardImportDialog = false },
            onImportTeams = { imported ->
                manualTeams.clear()
                manualTeams.addAll(imported)
                useMixerTeams = false
                quickGenNotice = "${imported.size} Teams aus Zwischenablage importiert"
                showClipboardImportDialog = false
            }
        )
    }

    if (showAddManualTeamDialog) {
        val editTarget = editingManualIndex?.let { manualTeams.getOrNull(it) }
        ManualTeamEntryDialog(
            nextIndex = manualTeams.size + 1,
            initialTeam = editTarget,
            onDismiss = {
                showAddManualTeamDialog = false
                editingManualIndex = null
            },
            onAdd = { team ->
                val eIdx = editingManualIndex
                if (eIdx != null && eIdx in manualTeams.indices) {
                    manualTeams[eIdx] = team
                } else {
                    manualTeams.add(team)
                }
                useMixerTeams = false
                showAddManualTeamDialog = false
                editingManualIndex = null
            }
        )
    }
}

@Composable
fun ClipboardImportDialog(
    viewModel: VolleyViewModel,
    onDismiss: () -> Unit,
    onImportTeams: (List<ManualTournamentTeam>) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var rawText by remember { mutableStateOf("") }
    var clipboardNotice by remember { mutableStateOf<String?>(null) }
    val parsedTeams = remember(rawText) { viewModel.parseTeamsFromText(rawText) }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Mannschaftsliste aus Zwischenablage", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportTeams(parsedTeams) },
                enabled = parsedTeams.isNotEmpty(),
                modifier = Modifier.height(48.dp).testTag("btn_confirm_clipboard_import"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("${parsedTeams.size} Teams importieren", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    ) {
        Text("Füge eine Texttabelle (Excel / Google Sheets / TSV / CSV / Zeilen) ein:", color = VolleyWhite, fontSize = 13.sp)

        Button(
            onClick = {
                try {
                    val clipText = clipboardManager.getText()?.text
                    if (clipText.isNullOrBlank()) {
                        clipboardNotice = "Zwischenablage ist leer oder enthält keinen Text."
                    } else {
                        rawText = clipText.take(100_000)
                        clipboardNotice = null
                    }
                } catch (_: Throwable) {
                    clipboardNotice = "Zugriff auf Zwischenablage fehlgeschlagen – bitte manuell einfügen."
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_paste_from_clipboard"),
            colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
            border = BorderStroke(1.dp, VolleyGrayBorder),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = VolleyYellow)
            Spacer(Modifier.width(6.dp))
            Text("Aus Zwischenablage einfügen", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        }

        if (clipboardNotice != null) {
            Text(clipboardNotice!!, color = VolleyRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it.take(100_000) },
            label = { Text("Tabellentext (TSV/CSV/Zeilen)", color = VolleyWhite) },
            placeholder = { Text("Team A\t4\t2\t2\t80\tVBC Nord\nTeam B\t3\t1\t3\t95\tTSV Mitte", color = VolleyTextSecondary) },
            minLines = 4,
            maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_clipboard_text")
        )

        if (parsedTeams.isNotEmpty()) {
            Text("${parsedTeams.size} Mannschaften erfolgreich erkannt:", color = VolleyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Card(colors = CardDefaults.cardColors(containerColor = VolleyDarkSurfaceVariant), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    parsedTeams.take(5).forEachIndexed { idx, t ->
                        Text("#${idx + 1}: ${t.name} (Skill ${t.skill}, ${t.maleCount}M/${t.femaleCount}W${if (t.club.isNotBlank()) ", ${t.club}" else ""})", color = VolleyWhite, fontSize = 12.sp, maxLines = 1)
                    }
                    if (parsedTeams.size > 5) {
                        Text("... und ${parsedTeams.size - 5} weitere Teams", color = VolleyTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualTeamEntryDialog(
    nextIndex: Int,
    initialTeam: ManualTournamentTeam? = null,
    onDismiss: () -> Unit,
    onAdd: (ManualTournamentTeam) -> Unit
) {
    var teamName by remember(initialTeam?.id) { mutableStateOf(initialTeam?.name ?: "Team $nextIndex") }
    var shortName by remember(initialTeam?.id) { mutableStateOf(initialTeam?.shortName ?: "") }
    var skill by remember(initialTeam?.id) { mutableIntStateOf(initialTeam?.skill ?: 3) }
    var maleCount by remember(initialTeam?.id) { mutableIntStateOf(initialTeam?.maleCount ?: 1) }
    var femaleCount by remember(initialTeam?.id) { mutableIntStateOf(initialTeam?.femaleCount ?: 1) }
    var totalAgeText by remember(initialTeam?.id) { mutableStateOf((initialTeam?.totalAge ?: 50).toString()) }
    var club by remember(initialTeam?.id) { mutableStateOf(initialTeam?.club ?: "") }
    var note by remember(initialTeam?.id) { mutableStateOf(initialTeam?.note ?: "") }
    var present by remember(initialTeam?.id) { mutableStateOf(initialTeam?.present ?: true) }
    var withdrawn by remember(initialTeam?.id) { mutableStateOf(initialTeam?.withdrawn ?: false) }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialTeam != null) "Turnier-Team bearbeiten" else "Team manuell erfassen",
                color = VolleyYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = teamName.trim().ifBlank { "Team $nextIndex" }.take(40)
                    val savedTeam = ManualTournamentTeam(
                        id = initialTeam?.id ?: UUID.randomUUID().toString(),
                        name = finalName,
                        skill = skill,
                        maleCount = maleCount,
                        femaleCount = femaleCount,
                        totalAge = totalAgeText.toIntOrNull() ?: 0,
                        club = club.trim(),
                        note = note.trim(),
                        shortName = shortName.trim().ifBlank { finalName.take(4).uppercase() }.take(4),
                        skillLevel = (skill * 20).coerceIn(1, 100),
                        present = present,
                        withdrawn = withdrawn,
                        registrationOrder = initialTeam?.registrationOrder ?: (nextIndex - 1).coerceAtLeast(0)
                    )
                    onAdd(savedTeam)
                },
                modifier = Modifier.height(48.dp).testTag("btn_confirm_add_manual_team"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (initialTeam != null) "Speichern" else "Hinzufügen", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    ) {
        OutlinedTextField(
            value = teamName,
            onValueChange = { teamName = it.take(40) },
            label = { Text("Teamname (max. 40 Zeichen)", color = VolleyWhite) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_manual_team_name")
        )

        OutlinedTextField(
            value = shortName,
            onValueChange = { shortName = it.take(4).uppercase() },
            label = { Text("Kürzel (max. 4 Zeichen)", color = VolleyWhite) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Spielstärke (Skill 1–5): $skill", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { s ->
                Button(
                    onClick = { skill = s },
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.widthIn(min = 48.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (skill == s) VolleyYellow else VolleyDarkSurfaceVariant,
                        contentColor = if (skill == s) Color.Black else VolleyWhite
                    ),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("$s", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text("Männlich: $maleCount", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    VolleyStepperButton(isIncrement = false, enabled = maleCount > 0, onClick = { if (maleCount > 0) maleCount-- })
                    VolleyStepperButton(isIncrement = true, onClick = { maleCount++ })
                }
            }
            Column {
                Text("Weiblich: $femaleCount", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    VolleyStepperButton(isIncrement = false, enabled = femaleCount > 0, onClick = { if (femaleCount > 0) femaleCount-- })
                    VolleyStepperButton(isIncrement = true, onClick = { femaleCount++ })
                }
            }
        }

        OutlinedTextField(
            value = totalAgeText,
            onValueChange = { totalAgeText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Gesamtalter der Mannschaft", color = VolleyWhite) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_manual_team_age")
        )

        OutlinedTextField(
            value = club,
            onValueChange = { club = it },
            label = { Text("Verein / Herkunft (für Seeding-Trennung)", color = VolleyWhite) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_manual_team_club")
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Notiz (optional)", color = VolleyWhite) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            modifier = Modifier.fillMaxWidth().testTag("input_manual_team_note")
        )

        if (initialTeam != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Anwesend (Check-in):", color = VolleyWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = present, onCheckedChange = { present = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Zurückgezogen / W.O.:", color = VolleyRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = withdrawn, onCheckedChange = { withdrawn = it })
            }
        }
    }
}

@Composable
private fun PodiumCertificatesBannerCard(
    certificates: List<CertificateEntry>,
    onOpenCertificates: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, VolleyYellow, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = VolleyYellow,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "Sieger-Urkunden (Plätze 1–3) erstellt!",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                certificates.take(3).forEach { entry ->
                    val medalColor = when (entry.rank) {
                        1 -> Color(0xFFFFD54F)
                        2 -> Color(0xFFCFD8DC)
                        else -> Color(0xFFD78A4E)
                    }
                    Text(
                        "${entry.rank}. Platz: ${entry.teamName}${if (entry.club.isNotBlank()) " (${entry.club})" else ""}",
                        color = medalColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = onOpenCertificates,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_open_certificates"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Urkunden ansehen & als PDF exportieren",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
