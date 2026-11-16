package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.domain.engine.TeamMixerEngine
import com.example.domain.engine.TeamRating
import com.example.ui.components.VolleyFormDialog
import com.example.ui.components.VolleyStepperButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.VolleyViewModel

@Composable
fun TeamScreen(
    viewModel: VolleyViewModel,
    modifier: Modifier = Modifier
) {
    val players by viewModel.allPlayers.collectAsState()
    val filteredPlayers by viewModel.filteredPlayers.collectAsState()
    val searchQuery by viewModel.playerSearchQuery.collectAsState()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsState()
    val availableTags by viewModel.allAvailableTags.collectAsState()
    val selectedIdsForMixer by viewModel.selectedPlayerIdsForMixer.collectAsState()
    val selectedPlayersForMixer by viewModel.selectedPlayersForMixer.collectAsState()

    val teams by viewModel.allTeams.collectAsState()
    val proposedTeams by viewModel.mixerProposedTeams.collectAsState()
    val createdDummies by viewModel.mixerCreatedDummies.collectAsState()
    val autoBalanceEnabled by viewModel.autoBalanceEnabled.collectAsState()

    val selectedFormat by viewModel.mixerFormat.collectAsState()
    val userOverrideTeamCount by viewModel.mixerTeamCountOverride.collectAsState()
    val minFemales by viewModel.minFemalesPerTeam.collectAsState()
    val minMales by viewModel.minMalesPerTeam.collectAsState()
    val mixerErrorMessage by viewModel.mixerErrorMessage.collectAsState()

    var showPlayerDialog by remember { mutableStateOf<Player?>(null) }
    var isNewPlayerDialog by remember { mutableStateOf(false) }
    var formatChangePending by remember { mutableStateOf<FieldFormat?>(null) }

    var showSwapDialog by remember { mutableStateOf(false) }
    var swapTeamA by remember { mutableStateOf<Team?>(null) }
    var swapPlayerA by remember { mutableStateOf<Player?>(null) }
    var swapTeamB by remember { mutableStateOf<Team?>(null) }
    var swapPlayerB by remember { mutableStateOf<Player?>(null) }

    var selectedTab by remember { mutableStateOf(0) } // 0: Spielerpool, 1: Teams & Mixer
    val requestedSubTab by viewModel.requestedTeamSubTab.collectAsState()
    LaunchedEffect(requestedSubTab) {
        requestedSubTab?.let { target ->
            selectedTab = target.coerceIn(0, 1)
            viewModel.consumeRequestedTeamSubTab()
        }
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val playerMap = remember(players, createdDummies) {
        val map = players.associateBy { it.id }.toMutableMap()
        createdDummies.forEach { map[it.id] = it }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolleyBlack)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = VolleyDarkSurface,
            contentColor = VolleyYellow,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = VolleyYellow,
                    height = 4.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "Spieler (${players.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) VolleyYellow else VolleyWhite,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier
                    .height(52.dp)
                    .testTag("tab_players")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Teams & Mixer (${teams.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 1) VolleyYellow else VolleyWhite,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier
                    .height(52.dp)
                    .testTag("tab_teams")
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedTab == 0) {
                PlayerPoolView(
                    allPlayersCount = players.size,
                    filteredPlayers = filteredPlayers,
                    searchQuery = searchQuery,
                    selectedTagFilter = selectedTagFilter,
                    availableTags = availableTags,
                    selectedIdsForMixer = selectedIdsForMixer,
                    onSearchQueryChange = { viewModel.setPlayerSearchQuery(it) },
                    onSelectTagFilter = { viewModel.setSelectedTagFilter(it) },
                    onTogglePlayerSelected = { viewModel.togglePlayerSelectionForMixer(it) },
                    onSelectAllFiltered = { viewModel.selectAllPlayersForMixer(filteredPlayers) },
                    onDeselectAllFiltered = { viewModel.deselectAllPlayersForMixer(filteredPlayers) },
                    onAddPlayer = {
                        isNewPlayerDialog = true
                        showPlayerDialog = null
                    },
                    onEditPlayer = {
                        isNewPlayerDialog = false
                        showPlayerDialog = it
                    },
                    onDeletePlayer = { viewModel.deletePlayer(it.id) }
                )
            } else {
                TeamMixerView(
                    totalPoolCount = players.size,
                    selectedPlayers = selectedPlayersForMixer,
                    teams = proposedTeams ?: teams,
                    isProposed = proposedTeams != null,
                    autoBalanceEnabled = autoBalanceEnabled,
                    selectedFormat = selectedFormat,
                    userOverrideTeamCount = userOverrideTeamCount,
                    minFemales = minFemales,
                    minMales = minMales,
                    mixerErrorMessage = mixerErrorMessage,
                    playerMap = playerMap,
                    onFormatChangeRequested = { newFormat ->
                        if (newFormat != selectedFormat) {
                            if (teams.isNotEmpty() || proposedTeams != null) {
                                formatChangePending = newFormat
                            } else {
                                viewModel.setMixerFormat(newFormat)
                            }
                        }
                    },
                    onSetOverrideTeamCount = { count -> viewModel.setMixerTeamCountOverride(count) },
                    onSetMinFemales = { viewModel.setMinFemalesPerTeam(it) },
                    onSetMinMales = { viewModel.setMinMalesPerTeam(it) },
                    onToggleAutoBalance = { viewModel.setAutoBalance(!autoBalanceEnabled) },
                    onMixTeams = { count ->
                        viewModel.generateTeamProposal(
                            availablePlayers = selectedPlayersForMixer,
                            format = selectedFormat,
                            teamCount = count
                        )
                    },
                    onConfirmProposal = { viewModel.confirmTeamProposal() },
                    onDiscardProposal = { viewModel.discardTeamProposal() },
                    onStartSwap = { showSwapDialog = true },
                    onToggleFinalize = { team -> viewModel.finalizeTeam(team.id, !team.isFinalized) },
                    onClearTeams = { viewModel.clearAllTeams() },
                    onGoToPlayers = { selectedTab = 0 },
                    onStartMatchFromMixer = { viewModel.startMatchFromFinalizedMixerTeams() }
                )
            }
        }
    }

    // Format Change Confirmation Dialog
    if (formatChangePending != null) {
        val targetFmt = formatChangePending!!
        AlertDialog(
            onDismissRequest = { formatChangePending = null },
            containerColor = VolleyDarkSurface,
            title = {
                Text(
                    "Formatwechsel bestätigen",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "Formatwechsel setzt alle aktuellen Teams zurück.",
                    color = VolleyWhite,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeMixerFormat(targetFmt)
                        formatChangePending = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VolleyRed, contentColor = VolleyWhite),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = VolleyWhite)
                    Spacer(Modifier.width(6.dp))
                    Text("Format wechseln", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { formatChangePending = null },
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Abbrechen", color = VolleyWhite, fontSize = 15.sp, maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // Player Create / Edit Dialog
    if (isNewPlayerDialog || showPlayerDialog != null) {
        PlayerEditDialog(
            player = showPlayerDialog,
            existingTags = availableTags,
            onDismiss = {
                isNewPlayerDialog = false
                showPlayerDialog = null
            },
            onSave = { name, skill, height, gender, tags, selectedForMixer ->
                viewModel.addOrUpdatePlayer(
                    id = showPlayerDialog?.id,
                    name = name,
                    skill = skill,
                    heightClass = height,
                    gender = gender,
                    tags = tags,
                    isSelectedForMixer = selectedForMixer
                )
                isNewPlayerDialog = false
                showPlayerDialog = null
            }
        )
    }

    // Player Swap Dialog
    val activeTeams = proposedTeams ?: teams
    if (showSwapDialog && activeTeams.size >= 2) {
        val safeTeamA = swapTeamA?.let { t -> activeTeams.find { it.id == t.id } } ?: activeTeams[0]
        val safeTeamB = swapTeamB?.let { t -> activeTeams.find { it.id == t.id } }
            ?: activeTeams.firstOrNull { it.id != safeTeamA.id } ?: activeTeams[1]

        PlayerSwapDialog(
            teams = activeTeams,
            playerMap = playerMap,
            teamA = safeTeamA,
            teamB = safeTeamB,
            playerA = swapPlayerA,
            playerB = swapPlayerB,
            onSelectTeamA = { swapTeamA = it; swapPlayerA = null },
            onSelectTeamB = { swapTeamB = it; swapPlayerB = null },
            onSelectPlayerA = { swapPlayerA = it },
            onSelectPlayerB = { swapPlayerB = it },
            onConfirmSwap = {
                val pA = swapPlayerA
                val pB = swapPlayerB
                if (pA != null && pB != null && safeTeamA.id != safeTeamB.id) {
                    viewModel.swapPlayers(safeTeamA.id, pA.id, safeTeamB.id, pB.id)
                }
                showSwapDialog = false
                swapTeamA = null
                swapTeamB = null
                swapPlayerA = null
                swapPlayerB = null
            },
            onDismiss = {
                showSwapDialog = false
                swapTeamA = null
                swapTeamB = null
                swapPlayerA = null
                swapPlayerB = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerPoolView(
    allPlayersCount: Int,
    filteredPlayers: List<Player>,
    searchQuery: String,
    selectedTagFilter: String?,
    availableTags: List<String>,
    selectedIdsForMixer: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onSelectTagFilter: (String?) -> Unit,
    onTogglePlayerSelected: (String) -> Unit,
    onSelectAllFiltered: () -> Unit,
    onDeselectAllFiltered: () -> Unit,
    onAddPlayer: () -> Unit,
    onEditPlayer: (Player) -> Unit,
    onDeletePlayer: (Player) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top header row + Quick add buttons (wraps gracefully on ultra-narrow / large-font screens)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.weight(1f).widthIn(min = 120.dp)) {
                Text(
                    "Spieler-Pool ($allPlayersCount)",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${selectedIdsForMixer.size} für Mixer markiert",
                    color = VolleyTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onAddPlayer,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_add_player"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VolleyYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Neu", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        }

        // Top Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Spieler oder Tag suchen...", color = VolleyTextSecondary, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suchen", tint = VolleyYellow) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Suche leeren", tint = VolleyWhite)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VolleyWhite,
                unfocusedTextColor = VolleyWhite,
                focusedContainerColor = VolleyDarkSurface,
                unfocusedContainerColor = VolleyDarkSurface,
                focusedBorderColor = VolleyYellow,
                unfocusedBorderColor = VolleyGrayBorder
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_player_search")
        )

        // Tag filter FlowRow so all tags stay visible without hidden horizontal overflow
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedTagFilter == null,
                onClick = { onSelectTagFilter(null) },
                label = { Text("Alle Tags", fontSize = 12.sp, maxLines = 1, softWrap = false) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VolleyYellow,
                    selectedLabelColor = Color.Black,
                    containerColor = VolleyDarkSurfaceVariant,
                    labelColor = VolleyWhite
                )
            )
            availableTags.forEach { tag ->
                val isSelected = selectedTagFilter.equals(tag, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectTagFilter(if (isSelected) null else tag) },
                    label = { Text("#$tag", fontSize = 12.sp, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VolleyYellow,
                        selectedLabelColor = Color.Black,
                        containerColor = VolleyDarkSurfaceVariant,
                        labelColor = VolleyWhite
                    )
                )
            }
        }

        if (filteredPlayers.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Für Mixer (${filteredPlayers.size} angezeigt):",
                    color = VolleyTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).widthIn(min = 120.dp).align(Alignment.CenterVertically)
                )
                OutlinedButton(
                    onClick = onSelectAllFiltered,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_select_all_players"),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyYellow),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Alle wählen", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onDeselectAllFiltered,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_deselect_all_players"),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Keine", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        if (allPlayersCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Noch keine Spieler im Pool vorhanden.\nLege Spieler an — alle Spieler bleiben dauerhaft gespeichert und können per Checkbox für den Teammixer gewählt werden.",
                    color = VolleyTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (filteredPlayers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Keine Spieler für diese Suche/Tag-Auswahl gefunden.",
                        color = VolleyTextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = { onSearchQueryChange(""); onSelectTagFilter(null) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Filter zurücksetzen", color = VolleyYellow)
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VolleyDarkSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mix", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                        Text("Name / Tags", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2.2f))
                        Text("Sk", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                        Text("Größe", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center)
                        Text("G", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                        Text("Aktion", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(88.dp), textAlign = TextAlign.Center)
                    }

                    HorizontalDivider(color = VolleyGrayBorder, thickness = 1.dp)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredPlayers, key = { _, p -> p.id }) { index, player ->
                            val isChecked = selectedIdsForMixer.contains(player.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 1) VolleyBlack.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable { onTogglePlayerSelected(player.id) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onTogglePlayerSelected(player.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = VolleyYellow,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = VolleyTextSecondary
                                    ),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("chk_player_${player.id}")
                                )

                                Column(modifier = Modifier.weight(2.2f).padding(end = 4.dp)) {
                                    Text(
                                        player.name,
                                        color = if (isChecked) VolleyWhite else VolleyTextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (player.tags.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            player.tags.forEach { tag ->
                                                Surface(
                                                    color = VolleyDarkSurfaceVariant,
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(0.5.dp, VolleyGrayBorder)
                                                ) {
                                                    Text(
                                                        tag,
                                                        color = VolleyYellow,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Text(
                                    "${player.skill}/5",
                                    color = VolleyYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(34.dp),
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    player.heightClass.label,
                                    color = VolleyWhite,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1.1f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    when (player.gender) {
                                        Gender.MALE -> "M"
                                        Gender.FEMALE -> "W"
                                        Gender.DIVERSE -> "D"
                                    },
                                    color = VolleyTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(24.dp),
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    modifier = Modifier.width(88.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onEditPlayer(player) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("btn_edit_${player.name}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Bearbeiten",
                                            tint = VolleyYellow,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeletePlayer(player) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("btn_delete_${player.name}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = VolleyRed,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = VolleyGrayBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamMixerView(
    totalPoolCount: Int,
    selectedPlayers: List<Player>,
    teams: List<Team>,
    isProposed: Boolean,
    autoBalanceEnabled: Boolean,
    selectedFormat: FieldFormat,
    userOverrideTeamCount: Int?,
    minFemales: Int,
    minMales: Int,
    mixerErrorMessage: String?,
    playerMap: Map<String, Player>,
    onFormatChangeRequested: (FieldFormat) -> Unit,
    onSetOverrideTeamCount: (Int?) -> Unit,
    onSetMinFemales: (Int) -> Unit,
    onSetMinMales: (Int) -> Unit,
    onToggleAutoBalance: () -> Unit,
    onMixTeams: (Int) -> Unit,
    onConfirmProposal: () -> Unit,
    onDiscardProposal: () -> Unit,
    onStartSwap: () -> Unit,
    onToggleFinalize: (Team) -> Unit,
    onClearTeams: () -> Unit,
    onGoToPlayers: () -> Unit,
    onStartMatchFromMixer: () -> Unit
) {
    val squadSize = selectedFormat.squadSize
    val autoCalculatedTeams = if (selectedPlayers.isEmpty()) 2 else maxOf(2, (selectedPlayers.size + squadSize - 1) / squadSize)
    val targetTeamCount = userOverrideTeamCount ?: autoCalculatedTeams
    val neededDummies = TeamMixerEngine.dummyCountNeeded(selectedPlayers.size, squadSize * targetTeamCount)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VolleyGrayBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "1. Mannschaftsformat wählen",
                        color = VolleyYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Nur angehakte Spieler (${selectedPlayers.size} von $totalPoolCount Pool-Spielern) werden gemischt:",
                        color = VolleyTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    if (selectedPlayers.isEmpty()) {
                        Surface(
                            color = VolleyYellow.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Keine Checkbox angehakt",
                                    color = VolleyYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = onGoToPlayers,
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    border = BorderStroke(1.dp, VolleyYellow),
                                    modifier = Modifier.height(40.dp).testTag("btn_go_to_player_checkboxes")
                                ) {
                                    Text("Spieler wählen", color = VolleyYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedPlayers.forEach { p ->
                                Surface(
                                    color = VolleyDarkSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        "${p.name} (S${p.skill})",
                                        color = VolleyWhite,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldFormat.values().forEach { fmt ->
                            OutlinedButton(
                                onClick = { onFormatChangeRequested(fmt) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedFormat == fmt) VolleyYellow else Color.Transparent,
                                    contentColor = if (selectedFormat == fmt) Color.Black else VolleyWhite
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedFormat == fmt) VolleyYellow else VolleyGrayBorder
                                )
                            ) {
                                Text(
                                    fmt.description,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedFormat == fmt) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // AUTOMATIC TEAM COUNT DERIVATION SECTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VolleyDarkSurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Teams: $targetTeamCount",
                                    color = VolleyYellow,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${selectedPlayers.size} Spieler ÷ $squadSize pro Team" +
                                        if (userOverrideTeamCount != null) " (manuell)" else " (minimal Dummies)",
                                    color = VolleyTextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (userOverrideTeamCount != null) {
                                    OutlinedButton(
                                        onClick = { onSetOverrideTeamCount(null) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        border = BorderStroke(1.dp, VolleyYellow),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Auto", color = VolleyYellow, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }
                                }

                                VolleyStepperButton(
                                    isIncrement = false,
                                    onClick = { if (targetTeamCount > 2) onSetOverrideTeamCount(targetTeamCount - 1) },
                                    enabled = targetTeamCount > 2,
                                    testTagName = "btn_decrease_teams"
                                )

                                VolleyStepperButton(
                                    isIncrement = true,
                                    onClick = { onSetOverrideTeamCount(targetTeamCount + 1) },
                                    testTagName = "btn_increase_teams"
                                )
                            }
                        }

                        if (neededDummies > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Minimaler Dummy-Einsatz: Erst werden alle ${selectedPlayers.size} Spieler verteilt, dann $neededDummies offene Plätze mit Dummies aufgefüllt.",
                                color = VolleyYellow,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // MIXED RULES SECTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VolleyDarkSurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Mixed-Regeln (Optional)",
                            color = VolleyYellow,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Mind. Frauen pro Team:",
                                color = VolleyWhite,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            VolleyStepperButton(
                                isIncrement = false,
                                enabled = minFemales > 0,
                                onClick = { if (minFemales > 0) onSetMinFemales(minFemales - 1) },
                                testTagName = "btn_decrease_min_females"
                            )
                            Text(
                                "$minFemales",
                                color = VolleyYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                            VolleyStepperButton(
                                isIncrement = true,
                                enabled = minFemales < squadSize,
                                onClick = { if (minFemales < squadSize) onSetMinFemales(minFemales + 1) },
                                testTagName = "btn_increase_min_females"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Mind. Männer pro Team:",
                                color = VolleyWhite,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            VolleyStepperButton(
                                isIncrement = false,
                                enabled = minMales > 0,
                                onClick = { if (minMales > 0) onSetMinMales(minMales - 1) },
                                testTagName = "btn_decrease_min_males"
                            )
                            Text(
                                "$minMales",
                                color = VolleyYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                            VolleyStepperButton(
                                isIncrement = true,
                                enabled = minMales < squadSize,
                                onClick = { if (minMales < squadSize) onSetMinMales(minMales + 1) },
                                testTagName = "btn_increase_min_males"
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Balance aktiv:", color = VolleyWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Gleicht Skill, Größe & Geschlecht aus", color = VolleyTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoBalanceEnabled,
                            onCheckedChange = { onToggleAutoBalance() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VolleyYellow,
                                checkedTrackColor = VolleyDarkSurfaceVariant
                            )
                        )
                    }

                    if (mixerErrorMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VolleyRed.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, VolleyRed),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = VolleyRed)
                                Spacer(Modifier.width(8.dp))
                                Text(mixerErrorMessage, color = VolleyRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onMixTeams(targetTeamCount) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_mix_teams"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VolleyYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Teams mischen ($targetTeamCount Teams)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        if (isProposed) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VolleyDarkSurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, VolleyYellow, RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Neuer Vorschlag für Teamverteilung:",
                            color = VolleyYellow,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Überprüfe die Zusammenstellung. Bei Bedarf kannst du Spieler tauschen oder erneut mischen.",
                            color = VolleyWhite,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onConfirmProposal,
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_confirm_teams"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolleyYellow,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(4.dp))
                                Text("Bestätigen", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = onDiscardProposal,
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_discard_teams"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                                border = BorderStroke(1.dp, VolleyGrayBorder)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = VolleyWhite)
                                Spacer(Modifier.width(4.dp))
                                Text("Verwerfen", fontSize = 14.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }

        if (!isProposed && teams.size == 2 && teams.all { it.isFinalized }) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VolleyGreen.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, VolleyGreen, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Beide Teams sind finalisiert!",
                            color = VolleyGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${teams[0].name} vs ${teams[1].name} · Spielernamen und Startpositionen werden automatisch ins Scoreboard & in die Rotation übernommen.",
                            color = VolleyWhite,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = onStartMatchFromMixer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_start_match_from_mixer"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VolleyGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Direkt ins Scoreboard & Rotation starten",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (teams.isNotEmpty()) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Aktuelle Teams (${teams.size})",
                        color = VolleyWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 120.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (teams.size >= 2) {
                            Button(
                                onClick = onStartSwap,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_swap_players"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolleyDarkSurfaceVariant,
                                    contentColor = VolleyYellow
                                ),
                                border = BorderStroke(1.dp, VolleyGrayBorder)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tausch", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        OutlinedButton(
                            onClick = onClearTeams,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyRed),
                            border = BorderStroke(1.dp, VolleyRed)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = VolleyRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Leeren", fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            items(teams, key = { it.id }) { team ->
                val rating = remember(team, playerMap) {
                    TeamMixerEngine.calculateTeamRating(team, playerMap)
                }
                TeamCard(
                    team = team,
                    rating = rating,
                    playerMap = playerMap,
                    onToggleFinalize = { onToggleFinalize(team) }
                )
            }
        }
    }
}

@Composable
fun TeamCard(
    team: Team,
    rating: TeamRating,
    playerMap: Map<String, Player>,
    onToggleFinalize: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (team.isFinalized) VolleyGreen else VolleyGrayBorder,
                RoundedCornerShape(8.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        team.name,
                        color = VolleyWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = if (team.isFinalized) VolleyGreen else VolleyYellowDark,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (team.isFinalized) " FINAL " else " ENTWURF ",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Button(
                    onClick = onToggleFinalize,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_finalize_${team.name}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (team.isFinalized) VolleyDarkSurfaceVariant else VolleyYellow,
                        contentColor = if (team.isFinalized) VolleyWhite else Color.Black
                    )
                ) {
                    Text(
                        if (team.isFinalized) "Entsperren" else "Finalisieren",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Skill-Ø: ${"%.1f".format(rating.averageSkill)} / 5.0",
                    color = VolleyYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Kader: ${team.playerIds.size}/${team.squadSize}",
                    color = VolleyWhite,
                    fontSize = 13.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Größen: ${rating.largeCount}G · ${rating.mediumCount}M · ${rating.smallCount}K · ${rating.verySmallCount}SK",
                    color = VolleyTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    "Geschl.: ${rating.maleCount}M · ${rating.femaleCount}W · ${rating.diverseCount}D",
                    color = VolleyTextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            team.playerIds.forEachIndexed { index, pId ->
                val p = playerMap[pId]
                val isSub = index >= team.fieldPlayerCount
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}. ",
                        color = VolleyYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        p?.name ?: "Unbekannt",
                        color = if (p?.isDummy == true) VolleyYellow else VolleyWhite,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (p?.isDummy == true) {
                        Spacer(Modifier.width(4.dp))
                        Text("(Dummy)", color = VolleyYellow, fontSize = 11.sp)
                    }
                    if (isSub) {
                        Spacer(Modifier.width(4.dp))
                        Text("[Bank]", color = VolleyTextSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${p?.heightClass?.label ?: ""} · ${p?.gender?.label?.take(1) ?: ""} · S${p?.skill ?: "-"}",
                        color = VolleyTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerEditDialog(
    player: Player?,
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Int, HeightClass, Gender, List<String>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var skill by remember { mutableIntStateOf(player?.skill ?: 3) }
    var heightClass by remember { mutableStateOf(player?.heightClass ?: HeightClass.MEDIUM) }
    var gender by remember { mutableStateOf(player?.gender ?: Gender.DIVERSE) }
    var selectedForMixer by remember { mutableStateOf(player?.isSelectedForMixer ?: false) }
    val selectedTags = remember { mutableStateListOf<String>().apply { addAll(player?.tags ?: emptyList()) } }
    var newTagInput by remember { mutableStateOf("") }

    val quickSuggestions = remember(existingTags) {
        (listOf("Beach", "Halle", "Dienstagsgruppe", "Liga", "Freizeit", "Zuspiel", "Angriff", "Block") + existingTags)
            .distinct()
    }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (player == null) "Neuen Spieler anlegen" else "Spieler bearbeiten",
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
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), skill, heightClass, gender, selectedTags.toList(), selectedForMixer)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_save_player"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VolleyYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Speichern", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
        }
    ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = VolleyWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VolleyWhite,
                        unfocusedTextColor = VolleyWhite,
                        focusedBorderColor = VolleyYellow,
                        unfocusedBorderColor = VolleyGrayBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_player_name")
                )

                Text("Skill Level (1 = Anfänger, 5 = Profi): $skill", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..5).forEach { lvl ->
                        Button(
                            onClick = { skill = lvl },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_skill_$lvl"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (skill == lvl) VolleyYellow else VolleyDarkSurfaceVariant,
                                contentColor = if (skill == lvl) Color.Black else VolleyWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$lvl", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Text("Körpergröße:", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HeightClass.values().forEach { hc ->
                        OutlinedButton(
                            onClick = { heightClass = hc },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_height_${hc.name}"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (heightClass == hc) VolleyYellow else Color.Transparent,
                                contentColor = if (heightClass == hc) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, if (heightClass == hc) VolleyYellow else VolleyGrayBorder)
                        ) {
                            Text(hc.label, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Text("Geschlecht:", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Gender.values().forEach { g ->
                        val shortLabel = when (g) {
                            Gender.MALE -> "Männlich"
                            Gender.FEMALE -> "Weiblich"
                            Gender.DIVERSE -> "Divers"
                        }
                        OutlinedButton(
                            onClick = { gender = g },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 84.dp)
                                .height(46.dp)
                                .testTag("btn_gender_${g.name}"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (gender == g) VolleyYellow else Color.Transparent,
                                contentColor = if (gender == g) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, if (gender == g) VolleyYellow else VolleyGrayBorder)
                        ) {
                            Text(shortLabel, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Tags Section
                Text("Tags / Gruppen:", color = VolleyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text("Neuer Tag (z.B. Gruppe A)", color = VolleyTextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("input_player_tag")
                    )
                    VolleyStepperButton(
                        isIncrement = true,
                        onClick = {
                            val trimmed = newTagInput.trim()
                            if (trimmed.isNotBlank() && selectedTags.none { it.equals(trimmed, ignoreCase = true) }) {
                                selectedTags.add(trimmed)
                                newTagInput = ""
                            }
                        },
                        testTagName = "btn_add_player_tag"
                    )
                }

                // Assigned & quick-toggle tag chips wrap cleanly without horizontal clipping
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickSuggestions.forEach { tag ->
                        val active = selectedTags.any { it.equals(tag, ignoreCase = true) }
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) {
                                    selectedTags.removeAll { it.equals(tag, ignoreCase = true) }
                                } else {
                                    selectedTags.add(tag)
                                }
                            },
                            label = { Text(tag, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VolleyYellow,
                                selectedLabelColor = Color.Black,
                                containerColor = VolleyDarkSurfaceVariant,
                                labelColor = VolleyWhite
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedForMixer = !selectedForMixer }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedForMixer,
                        onCheckedChange = { selectedForMixer = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VolleyYellow,
                            checkmarkColor = Color.Black,
                            uncheckedColor = VolleyTextSecondary
                        ),
                        modifier = Modifier.testTag("chk_dialog_player_mixer")
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Für Teammixer berücksichtigen (anhaken)",
                        color = VolleyWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerSwapDialog(
    teams: List<Team>,
    playerMap: Map<String, Player>,
    teamA: Team,
    teamB: Team,
    playerA: Player?,
    playerB: Player?,
    onSelectTeamA: (Team) -> Unit,
    onSelectTeamB: (Team) -> Unit,
    onSelectPlayerA: (Player) -> Unit,
    onSelectPlayerB: (Player) -> Unit,
    onConfirmSwap: () -> Unit,
    onDismiss: () -> Unit
) {
    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Manueller Spielertausch", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen", fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSwap,
                enabled = playerA != null && playerB != null && teamA.id != teamB.id,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_confirm_swap"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VolleyYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Tausch ausführen", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        }
    ) {
                Text("Wähle zwei Teams und je einen Spieler:", color = VolleyWhite, fontSize = 14.sp)

                if (teams.size > 2) {
                    Text("Erstes Team:", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        teams.forEach { t ->
                            FilterChip(
                                selected = t.id == teamA.id,
                                onClick = { if (t.id != teamB.id) onSelectTeamA(t) },
                                label = { Text(t.name, fontSize = 12.sp) }
                            )
                        }
                    }
                    Text("Zweites Team:", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        teams.forEach { t ->
                            FilterChip(
                                selected = t.id == teamB.id,
                                onClick = { if (t.id != teamA.id) onSelectTeamB(t) },
                                label = { Text(t.name, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Text("Team 1: ${teamA.name}", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    teamA.playerIds.forEach { pId ->
                        val p = playerMap[pId] ?: return@forEach
                        OutlinedButton(
                            onClick = { onSelectPlayerA(p) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (playerA?.id == p.id) VolleyYellow else Color.Transparent,
                                contentColor = if (playerA?.id == p.id) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, if (playerA?.id == p.id) VolleyYellow else VolleyGrayBorder)
                        ) {
                            Text("${p.name} (Skill ${p.skill}, ${p.heightClass.label}, ${p.gender.label})", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text("Team 2: ${teamB.name}", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    teamB.playerIds.forEach { pId ->
                        val p = playerMap[pId] ?: return@forEach
                        OutlinedButton(
                            onClick = { onSelectPlayerB(p) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (playerB?.id == p.id) VolleyYellow else Color.Transparent,
                                contentColor = if (playerB?.id == p.id) Color.Black else VolleyWhite
                            ),
                            border = BorderStroke(1.dp, if (playerB?.id == p.id) VolleyYellow else VolleyGrayBorder)
                        ) {
                            Text("${p.name} (Skill ${p.skill}, ${p.heightClass.label}, ${p.gender.label})", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
    }
}
