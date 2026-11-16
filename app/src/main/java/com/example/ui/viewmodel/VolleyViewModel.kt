package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.VolleyRepository
import com.example.domain.engine.*
import com.example.domain.tournament.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class ScoreboardTeamStanding(
    val rank: Int = 1,
    val teamName: String,
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val setsWon: Int = 0,
    val setsLost: Int = 0,
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0
) {
    val setDiff: Int get() = setsWon - setsLost
    val pointsDiff: Int get() = pointsFor - pointsAgainst
}

class VolleyViewModel(private val repository: VolleyRepository) : ViewModel() {

    // --- Tab / Navigation State ---
    val currentTab = MutableStateFlow(NavigationTab.TEAM)
    private val _returnToScoreAfterRotation = MutableStateFlow(false)
    val returnToScoreAfterRotation: StateFlow<Boolean> = _returnToScoreAfterRotation.asStateFlow()

    private val _requestedTeamSubTab = MutableStateFlow<Int?>(null)
    val requestedTeamSubTab: StateFlow<Int?> = _requestedTeamSubTab.asStateFlow()

    fun selectTab(tab: NavigationTab) {
        if (tab != NavigationTab.ROTATION) {
            _returnToScoreAfterRotation.value = false
        }
        currentTab.value = tab
    }

    fun openTeamMixerFromRotation() {
        _returnToScoreAfterRotation.value = false
        _requestedTeamSubTab.value = 1
        currentTab.value = NavigationTab.TEAM
    }

    fun consumeRequestedTeamSubTab() {
        _requestedTeamSubTab.value = null
    }

    // --- Players State (Permanent Pool + Search + Tag Filter + Mixer Checkboxes) ---
    val allPlayers: StateFlow<List<Player>> = repository.allPlayers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playerSearchQuery = MutableStateFlow("")
    val playerSearchQuery: StateFlow<String> = _playerSearchQuery.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<String?>(null)
    val selectedTagFilter: StateFlow<String?> = _selectedTagFilter.asStateFlow()

    val filteredPlayers: StateFlow<List<Player>> = combine(
        allPlayers,
        _playerSearchQuery,
        _selectedTagFilter
    ) { players, query, tagFilter ->
        val q = query.trim().lowercase()
        players.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.tags.any { it.lowercase().contains(q) }
            val matchesTag = tagFilter == null || p.tags.any { it.equals(tagFilter, ignoreCase = true) }
            matchesQuery && matchesTag
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAvailableTags: StateFlow<List<String>> = allPlayers.map { players ->
        players.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedPlayersForMixer: StateFlow<List<Player>> = allPlayers.map { players ->
        players.filter { it.isSelectedForMixer }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedPlayerIdsForMixer: StateFlow<Set<String>> = selectedPlayersForMixer.map { selected ->
        selected.map { it.id }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // --- Teams State ---
    val allTeams: StateFlow<List<Team>> = repository.allTeams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Match / Scoreboard State ---
    private val _activeMatchOverride = MutableStateFlow<Match?>(null)
    val currentMatch: StateFlow<Match?> = combine(
        repository.latestStandaloneMatch,
        _activeMatchOverride
    ) { standalone, override ->
        override ?: standalone
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allStandAloneMatches: StateFlow<List<Match>> = repository.allStandAloneMatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scoreboardStandings: StateFlow<List<ScoreboardTeamStanding>> = allStandAloneMatches.map { matches ->
        calculateScoreboardStandings(matches)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Live Timeout Countdown & Whistle State ---
    private val _timeoutPresetSeconds = MutableStateFlow(30)
    val timeoutPresetSeconds: StateFlow<Int> = _timeoutPresetSeconds.asStateFlow()

    private val _activeTimeoutSide = MutableStateFlow<TeamSide?>(null)
    val activeTimeoutSide: StateFlow<TeamSide?> = _activeTimeoutSide.asStateFlow()

    private val _activeTimeoutRemaining = MutableStateFlow<Int?>(null)
    val activeTimeoutRemaining: StateFlow<Int?> = _activeTimeoutRemaining.asStateFlow()

    private val _timeoutExpiredNotice = MutableStateFlow<String?>(null)
    val timeoutExpiredNotice: StateFlow<String?> = _timeoutExpiredNotice.asStateFlow()

    private var timeoutJob: Job? = null

    // --- Rotation State (Both Teams A=Red, B=Blue) ---
    private val _rotationTeamNameA = MutableStateFlow("Team Rot")
    val rotationTeamNameA: StateFlow<String> = combine(_rotationTeamNameA, currentMatch) { name, match ->
        match?.teamAName ?: name
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Team Rot")

    private val _rotationTeamNameB = MutableStateFlow("Team Blau")
    val rotationTeamNameB: StateFlow<String> = combine(_rotationTeamNameB, currentMatch) { name, match ->
        match?.teamBName ?: name
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Team Blau")

    private val _rotationConfigA = MutableStateFlow(
        TeamRotationConfig(
            phase = RotationPhase.SERVE,
            jerseyNumbers = TeamRotationConfig.defaultJerseys()
        )
    )
    private val _rotationConfigB = MutableStateFlow(
        TeamRotationConfig(
            phase = RotationPhase.RECEIVE,
            jerseyNumbers = TeamRotationConfig.defaultJerseys()
        )
    )
    private val _rotationPhaseOverride = MutableStateFlow<Pair<RotationPhase, RotationPhase>?>(null)

    val rotationConfigA: StateFlow<TeamRotationConfig> = combine(
        _rotationConfigA,
        currentMatch,
        _rotationPhaseOverride
    ) { cfg, match, phaseOverride ->
        if (match != null && match.rotationLinked) {
            val activePhase = phaseOverride?.first
                ?: if (match.servingTeam == TeamSide.TEAM_A) RotationPhase.SERVE else RotationPhase.RECEIVE
            val courtCount = match.matchConfig.fieldFormatA.fieldPlayerCount.coerceIn(2, 6)
            cfg.copy(
                rotationStep = ((match.rotationStepA % courtCount) + courtCount) % courtCount,
                playerCountOnCourt = courtCount,
                phase = activePhase
            )
        } else cfg
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _rotationConfigA.value)

    val rotationConfigB: StateFlow<TeamRotationConfig> = combine(
        _rotationConfigB,
        currentMatch,
        _rotationPhaseOverride
    ) { cfg, match, phaseOverride ->
        if (match != null && match.rotationLinked) {
            val activePhase = phaseOverride?.second
                ?: if (match.servingTeam == TeamSide.TEAM_B) RotationPhase.SERVE else RotationPhase.RECEIVE
            val courtCount = match.matchConfig.fieldFormatB.fieldPlayerCount.coerceIn(2, 6)
            cfg.copy(
                rotationStep = ((match.rotationStepB % courtCount) + courtCount) % courtCount,
                playerCountOnCourt = courtCount,
                phase = activePhase
            )
        } else cfg
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _rotationConfigB.value)

    private val _trackedCourtTarget = MutableStateFlow<TrackedCourtTarget?>(null)
    val trackedCourtTarget: StateFlow<TrackedCourtTarget?> = _trackedCourtTarget.asStateFlow()

    // --- Tournaments State ---
    val allTournaments: StateFlow<List<Tournament>> = repository.allTournaments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTournamentId = MutableStateFlow<String?>(null)
    val selectedTournamentId: StateFlow<String?> = _selectedTournamentId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedTournamentMatches: StateFlow<List<Match>> = _selectedTournamentId
        .flatMapLatest { id ->
            if (id != null) repository.getMatchesByTournament(id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Tournament Undo/Redo, Warnings & Self-Test State ---
    private data class TournamentUndoSnapshot(
        val tournament: Tournament,
        val matches: List<Match>
    )
    private val _tournamentUndoStack = MutableStateFlow<List<TournamentUndoSnapshot>>(emptyList())
    private val _tournamentRedoStack = MutableStateFlow<List<TournamentUndoSnapshot>>(emptyList())
    val canUndoTournament: StateFlow<Boolean> = _tournamentUndoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canRedoTournament: StateFlow<Boolean> = _tournamentRedoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val tournamentMutex = Mutex()

    private val _tournamentWarningsById = MutableStateFlow<Map<String, List<TournamentWarning>>>(emptyMap())
    val activeTournamentWarnings: StateFlow<List<TournamentWarning>> = combine(
        _selectedTournamentId,
        allTournaments,
        _tournamentWarningsById
    ) { selId, list, warnMap ->
        val t = list.find { it.id == selId } ?: return@combine emptyList()
        warnMap[t.id] ?: SpecScoring.checkScoringConfigWarnings(t.specConfig)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tournamentFeedbackMessage = MutableStateFlow<String?>(null)
    val tournamentFeedbackMessage: StateFlow<String?> = _tournamentFeedbackMessage.asStateFlow()

    private val _autoOpenCertificatesTournamentId = MutableStateFlow<String?>(null)
    val autoOpenCertificatesTournamentId: StateFlow<String?> = _autoOpenCertificatesTournamentId.asStateFlow()

    private val _selfTestReport = MutableStateFlow<SelfTestReport?>(null)
    val selfTestReport: StateFlow<SelfTestReport?> = _selfTestReport.asStateFlow()

    private val _selfTestRunning = MutableStateFlow(false)
    val selfTestRunning: StateFlow<Boolean> = _selfTestRunning.asStateFlow()

    private val _selfTestProgress = MutableStateFlow(0f to "")
    val selfTestProgress: StateFlow<Pair<Float, String>> = _selfTestProgress.asStateFlow()

    // --- Coin Toss State ---
    private val _coinTossResult = MutableStateFlow<CoinTossResult?>(null)
    val coinTossResult: StateFlow<CoinTossResult?> = _coinTossResult.asStateFlow()

    // --- Taktikboard State ---
    private val _tacticStrokes = MutableStateFlow<List<TacticStroke>>(emptyList())
    val tacticStrokes: StateFlow<List<TacticStroke>> = _tacticStrokes.asStateFlow()

    private val _tacticRedoStack = MutableStateFlow<List<TacticStroke>>(emptyList())
    val tacticRedoStack: StateFlow<List<TacticStroke>> = _tacticRedoStack.asStateFlow()

    private val _isBeachCourt = MutableStateFlow(true)
    val isBeachCourt: StateFlow<Boolean> = _isBeachCourt.asStateFlow()

    // --- Team Mixer StateFlows ---
    private val _mixerFormat = MutableStateFlow(FieldFormat.FORMAT_2V2)
    val mixerFormat: StateFlow<FieldFormat> = _mixerFormat.asStateFlow()

    private val _mixerTeamCountOverride = MutableStateFlow<Int?>(null)
    val mixerTeamCountOverride: StateFlow<Int?> = _mixerTeamCountOverride.asStateFlow()

    private val _minFemalesPerTeam = MutableStateFlow(0)
    val minFemalesPerTeam: StateFlow<Int> = _minFemalesPerTeam.asStateFlow()

    private val _minMalesPerTeam = MutableStateFlow(0)
    val minMalesPerTeam: StateFlow<Int> = _minMalesPerTeam.asStateFlow()

    private val _mixerErrorMessage = MutableStateFlow<String?>(null)
    val mixerErrorMessage: StateFlow<String?> = _mixerErrorMessage.asStateFlow()

    private val _mixerProposedTeams = MutableStateFlow<List<Team>?>(null)
    val mixerProposedTeams: StateFlow<List<Team>?> = _mixerProposedTeams.asStateFlow()

    private val _mixerCreatedDummies = MutableStateFlow<List<Player>>(emptyList())
    val mixerCreatedDummies: StateFlow<List<Player>> = _mixerCreatedDummies.asStateFlow()

    private val _autoBalanceEnabled = MutableStateFlow(true)
    val autoBalanceEnabled: StateFlow<Boolean> = _autoBalanceEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.purgeDummies()
        }
    }

    fun setAutoBalance(enabled: Boolean) {
        _autoBalanceEnabled.value = enabled
    }

    fun setMixerFormat(format: FieldFormat) {
        _mixerFormat.value = format
    }

    fun setMixerTeamCountOverride(count: Int?) {
        _mixerTeamCountOverride.value = count
    }

    fun setMinFemalesPerTeam(count: Int) {
        _minFemalesPerTeam.value = count
    }

    fun setMinMalesPerTeam(count: Int) {
        _minMalesPerTeam.value = count
    }

    fun changeMixerFormat(newFormat: FieldFormat) {
        viewModelScope.launch {
            repository.clearTeams()
            _mixerProposedTeams.value = null
            _mixerCreatedDummies.value = emptyList()
            _mixerTeamCountOverride.value = null
            _mixerFormat.value = newFormat
            _mixerErrorMessage.value = null

            val tournaments = repository.allTournaments.first()
            tournaments.forEach { t ->
                if (t.status != TournamentStatus.DRAFT) {
                    repository.saveTournament(t.copy(status = TournamentStatus.DRAFT))
                }
            }
        }
    }

    // =========================================================================
    // PLAYER POOL ACTIONS (PERMANENT STORAGE, SEARCH, CHECKBOXES & TAGS)
    // =========================================================================

    fun setPlayerSearchQuery(query: String) {
        _playerSearchQuery.value = query
    }

    fun setSelectedTagFilter(tag: String?) {
        _selectedTagFilter.value = tag
    }

    fun togglePlayerSelectionForMixer(playerId: String) {
        viewModelScope.launch {
            val player = repository.getPlayer(playerId) ?: return@launch
            repository.savePlayer(player.copy(isSelectedForMixer = !player.isSelectedForMixer))
        }
    }

    fun selectAllPlayersForMixer(playersToSelect: List<Player> = allPlayers.value) {
        if (playersToSelect.isEmpty()) return
        viewModelScope.launch {
            val updated = playersToSelect.filter { !it.isSelectedForMixer }.map { it.copy(isSelectedForMixer = true) }
            if (updated.isNotEmpty()) {
                repository.savePlayers(updated)
            }
        }
    }

    fun deselectAllPlayersForMixer(playersToDeselect: List<Player> = allPlayers.value) {
        if (playersToDeselect.isEmpty()) return
        viewModelScope.launch {
            val updated = playersToDeselect.filter { it.isSelectedForMixer }.map { it.copy(isSelectedForMixer = false) }
            if (updated.isNotEmpty()) {
                repository.savePlayers(updated)
            }
        }
    }

    fun addOrUpdatePlayer(
        id: String?,
        name: String,
        skill: Int,
        heightClass: HeightClass,
        gender: Gender,
        tags: List<String> = emptyList(),
        isSelectedForMixer: Boolean? = null
    ) {
        viewModelScope.launch {
            val playerId = id ?: "player_${UUID.randomUUID()}"
            val existingSelection = if (id != null) repository.getPlayer(id)?.isSelectedForMixer ?: false else false
            val cleanedTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val player = Player(
                id = playerId,
                name = name.trim(),
                skill = skill.coerceIn(1, 5),
                heightClass = heightClass,
                gender = gender,
                isDummy = false,
                tags = cleanedTags,
                isSelectedForMixer = isSelectedForMixer ?: existingSelection
            )
            repository.savePlayer(player)
        }
    }

    fun deletePlayer(id: String) {
        viewModelScope.launch {
            repository.deletePlayer(id)
        }
    }

    // =========================================================================
    // TEAM & TEAMMIXER ACTIONS
    // =========================================================================

    fun generateTeamProposal(
        availablePlayers: List<Player> = selectedPlayersForMixer.value,
        format: FieldFormat,
        teamCount: Int
    ) {
        _mixerErrorMessage.value = null
        // Strictly enforce that ONLY players whose checkbox is checked are included
        val checkedPlayers = availablePlayers.filter { it.isSelectedForMixer && !it.isDummy }
        if (checkedPlayers.isEmpty()) {
            _mixerErrorMessage.value = "Keine Spieler ausgewählt. Bitte markiere mindestens einen Spieler per Checkbox im Reiter „Spieler“."
            _mixerProposedTeams.value = null
            _mixerCreatedDummies.value = emptyList()
            return
        }
        val config = MixerConfig(
            format = format,
            sportType = format.sportType,
            teamCount = teamCount,
            autoBalance = _autoBalanceEnabled.value,
            minFemalesPerTeam = _minFemalesPerTeam.value,
            minMalesPerTeam = _minMalesPerTeam.value
        )
        val result = TeamMixerEngine.mix(
            inputPlayers = checkedPlayers,
            config = config
        )
        if (result.errorMessage != null) {
            _mixerErrorMessage.value = result.errorMessage
            _mixerProposedTeams.value = null
            _mixerCreatedDummies.value = emptyList()
        } else {
            _mixerProposedTeams.value = result.teams
            _mixerCreatedDummies.value = result.createdDummies
        }
    }

    fun confirmTeamProposal() {
        val proposed = _mixerProposedTeams.value ?: return
        viewModelScope.launch {
            val existingNames = repository.allTeams.first().map { it.name }.toMutableSet()
            val uniqueNamedTeams = proposed.map { team ->
                var finalName = team.name
                var suffix = 2
                while (existingNames.contains(finalName)) {
                    finalName = "${team.name} ($suffix)"
                    suffix++
                }
                existingNames.add(finalName)
                team.copy(name = finalName)
            }
            repository.saveTeams(uniqueNamedTeams)
            _mixerProposedTeams.value = null
            _mixerCreatedDummies.value = emptyList()
        }
    }

    fun discardTeamProposal() {
        _mixerProposedTeams.value = null
        _mixerCreatedDummies.value = emptyList()
        _mixerErrorMessage.value = null
    }

    fun swapPlayers(
        teamAId: String,
        playerAId: String,
        teamBId: String,
        playerBId: String
    ) {
        viewModelScope.launch {
            val currentTeams = _mixerProposedTeams.value ?: allTeams.value
            val updated = TeamMixerEngine.swapPlayers(
                teams = currentTeams,
                teamAId = teamAId,
                playerAId = playerAId,
                teamBId = teamBId,
                playerBId = playerBId
            )
            if (_mixerProposedTeams.value != null) {
                _mixerProposedTeams.value = updated
            } else {
                repository.saveTeams(updated)
            }
        }
    }

    fun finalizeTeam(teamId: String, finalize: Boolean) {
        viewModelScope.launch {
            val team = repository.getTeam(teamId) ?: return@launch
            val updated = team.copy(isFinalized = finalize)
            repository.saveTeam(updated)

            // If exactly 2 teams exist and both are finalized, automatically populate Rotation with their players & names
            val currentTeams = repository.allTeams.first()
            if (currentTeams.size == 2 && currentTeams.all { it.isFinalized }) {
                syncTeamsToRotation(currentTeams[0], currentTeams[1])
            }
        }
    }

    fun startMatchFromFinalizedMixerTeams() {
        val currentTeams = allTeams.value
        if (currentTeams.size != 2 || !currentTeams.all { it.isFinalized }) return

        val teamA = currentTeams[0]
        val teamB = currentTeams[1]

        fun resolveFieldFormat(team: Team): FieldFormat {
            return FieldFormat.values().firstOrNull {
                it.fieldPlayerCount == team.fieldPlayerCount && it.squadSize == team.squadSize
            } ?: FieldFormat.values().firstOrNull {
                it.fieldPlayerCount == team.fieldPlayerCount
            } ?: FieldFormat.FORMAT_6V6
        }

        val formatA = resolveFieldFormat(teamA)
        val formatB = resolveFieldFormat(teamB)
        val defaultPoints = if (teamA.fieldPlayerCount >= 5) 25 else 21

        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null
        _timeoutExpiredNotice.value = null
        _rotationPhaseOverride.value = null

        viewModelScope.launch {
            syncTeamsToRotation(teamA, teamB)
            val match = Match(
                id = "match_${UUID.randomUUID()}",
                teamAId = teamA.id,
                teamBId = teamB.id,
                teamAName = teamA.name.ifBlank { "Team Rot" },
                teamBName = teamB.name.ifBlank { "Team Blau" },
                matchConfig = MatchConfig(
                    fieldFormatA = formatA,
                    fieldFormatB = formatB,
                    setsMode = SetsMode.BEST_OF_3,
                    pointsPerSet = defaultPoints,
                    winByTwo = true,
                    timeoutDurationSeconds = _timeoutPresetSeconds.value
                ),
                status = MatchStatus.RUNNING,
                servingTeam = TeamSide.TEAM_A,
                rotationLinked = true
            )
            _activeMatchOverride.value = null
            repository.saveMatch(match)
            currentTab.value = NavigationTab.SCORE
        }
    }

    private fun buildRotationConfigForTeam(
        team: Team,
        existing: TeamRotationConfig,
        initialPhase: RotationPhase,
        combinedMap: Map<String, Player>
    ): TeamRotationConfig {
        val resolvedPlayers = team.playerIds.map { pId ->
            combinedMap[pId] ?: Player(
                id = pId,
                name = if (pId.startsWith("dummy_")) "Dummy" else "Spieler",
                skill = 3,
                isDummy = pId.startsWith("dummy_")
            )
        }
        val courtCount = team.fieldPlayerCount.coerceIn(2, 6).let { fc ->
            if (resolvedPlayers.isNotEmpty()) minOf(fc, resolvedPlayers.size).coerceIn(2, 6) else fc
        }
        val roles = TeamRotationConfig.rolesFor(existing.system, courtCount)
        val newNames = mutableMapOf<String, String>()
        val newJerseys = TeamRotationConfig.defaultJerseys().toMutableMap()

        roles.forEachIndexed { idx, roleKey ->
            val player = resolvedPlayers.getOrNull(idx)
            if (player != null) {
                newNames[roleKey] = player.name
            }
            newJerseys[roleKey] = "${idx + 1}"
        }
        newJerseys["LB"] = TeamRotationConfig.DEFAULT_LIBERO_JERSEY

        val bench = resolvedPlayers.drop(courtCount)
            .take(TeamRotationConfig.MAX_TOTAL_PLAYERS - courtCount)
            .mapIndexed { idx, player ->
                RotationPlayer(
                    id = player.id,
                    name = player.name,
                    jerseyNumber = "${courtCount + idx + 1}"
                )
            }

        return existing.copy(
            phase = initialPhase,
            rotationStep = 0,
            playerCountOnCourt = courtCount,
            jerseyNumbers = newJerseys,
            playerNames = newNames,
            benchPlayers = bench,
            substitutionLog = emptyList()
        )
    }

    private suspend fun syncTeamsToRotation(teamA: Team, teamB: Team) {
        val dbPlayers = repository.allPlayers.first().associateBy { it.id }
        val dummyMap = _mixerCreatedDummies.value.associateBy { it.id }
        val combinedMap = dbPlayers + dummyMap

        _rotationTeamNameA.value = teamA.name.ifBlank { "Team Rot" }
        _rotationTeamNameB.value = teamB.name.ifBlank { "Team Blau" }
        _rotationConfigA.value = buildRotationConfigForTeam(teamA, _rotationConfigA.value, RotationPhase.SERVE, combinedMap)
        _rotationConfigB.value = buildRotationConfigForTeam(teamB, _rotationConfigB.value, RotationPhase.RECEIVE, combinedMap)
        _rotationPhaseOverride.value = null
    }

    fun assignMixerTeamToRotationSide(side: TeamSide, team: Team) {
        viewModelScope.launch {
            val dbPlayers = repository.allPlayers.first().associateBy { it.id }
            val dummyMap = _mixerCreatedDummies.value.associateBy { it.id }
            val combinedMap = dbPlayers + dummyMap
            val existingCfg = if (side == TeamSide.TEAM_A) _rotationConfigA.value else _rotationConfigB.value
            val updatedCfg = buildRotationConfigForTeam(team, existingCfg, existingCfg.phase, combinedMap)
            val cleanedName = team.name.ifBlank {
                if (side == TeamSide.TEAM_A) "Team Rot" else "Team Blau"
            }

            if (side == TeamSide.TEAM_A) {
                _rotationTeamNameA.value = cleanedName
                _rotationConfigA.value = updatedCfg
            } else {
                _rotationTeamNameB.value = cleanedName
                _rotationConfigB.value = updatedCfg
            }

            val match = currentMatch.value
            if (match != null && match.rotationLinked) {
                val courtCount = updatedCfg.playerCountOnCourt.coerceIn(2, 6)
                val newFormat = fieldFormatForCourtCount(courtCount, courtCount + updatedCfg.benchPlayers.size)
                val updatedMatch = if (side == TeamSide.TEAM_A) {
                    match.copy(
                        teamAId = team.id,
                        teamAName = cleanedName,
                        matchConfig = match.matchConfig.copy(fieldFormatA = newFormat),
                        rotationStepA = 0,
                        servingPlayerIndexA = 0
                    )
                } else {
                    match.copy(
                        teamBId = team.id,
                        teamBName = cleanedName,
                        matchConfig = match.matchConfig.copy(fieldFormatB = newFormat),
                        rotationStepB = 0,
                        servingPlayerIndexB = 0
                    )
                }
                repository.saveMatch(updatedMatch)
                if (_activeMatchOverride.value?.id == updatedMatch.id) {
                    _activeMatchOverride.value = updatedMatch
                }
            }
        }
    }

    fun clearAllTeams() {
        viewModelScope.launch {
            repository.clearTeams()
            _mixerProposedTeams.value = null
            _mixerCreatedDummies.value = emptyList()
            _mixerErrorMessage.value = null
        }
    }

    // =========================================================================
    // SCOREBOARD & TIMEOUT ACTIONS
    // =========================================================================

    fun setTimeoutPresetSeconds(seconds: Int) {
        _timeoutPresetSeconds.value = seconds.coerceIn(10, 120)
    }

    fun createNewMatch(
        teamAName: String,
        teamBName: String,
        fieldFormatA: FieldFormat,
        fieldFormatB: FieldFormat,
        setsMode: SetsMode,
        pointsPerSet: Int,
        winByTwo: Boolean,
        timeLimitMinutes: Int? = null,
        teamAId: String = "team_a",
        teamBId: String = "team_b",
        timeoutDurationSeconds: Int = _timeoutPresetSeconds.value
    ) {
        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null
        _timeoutExpiredNotice.value = null
        _rotationPhaseOverride.value = null

        viewModelScope.launch {
            val match = Match(
                id = "match_${UUID.randomUUID()}",
                teamAId = teamAId,
                teamBId = teamBId,
                teamAName = teamAName.ifBlank { "Team Rot" },
                teamBName = teamBName.ifBlank { "Team Blau" },
                matchConfig = MatchConfig(
                    fieldFormatA = fieldFormatA,
                    fieldFormatB = fieldFormatB,
                    setsMode = setsMode,
                    pointsPerSet = pointsPerSet,
                    winByTwo = winByTwo,
                    timeLimitMinutes = timeLimitMinutes,
                    timeoutDurationSeconds = timeoutDurationSeconds
                ),
                status = MatchStatus.RUNNING,
                servingTeam = TeamSide.TEAM_A,
                rotationLinked = currentMatch.value?.rotationLinked ?: false
            )
            _activeMatchOverride.value = null
            repository.saveMatch(match)
        }
    }

    fun playTournamentMatchInScoreboard(match: Match) {
        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null
        _timeoutExpiredNotice.value = null

        viewModelScope.launch {
            val matchToPlay = if (match.status == MatchStatus.DRAFT) match.copy(status = MatchStatus.RUNNING) else match
            repository.saveMatch(matchToPlay)
            _activeMatchOverride.value = matchToPlay
            currentTab.value = NavigationTab.SCORE
        }
    }

    fun enterDirectTournamentMatchResult(match: Match, sets: List<SetResult>, auditReason: String = "Direkteingabe") {
        viewModelScope.launch {
            val tId = match.tournamentId
            if (tId != null) {
                tournamentMutex.withLock {
                    val t = repository.getTournament(tId)
                    if (t != null) {
                        pushTournamentUndoSnapshot(t)
                    }
                    val updated = match.copy(
                        completedSets = sets,
                        status = MatchStatus.FINISHED,
                        scoreA = sets.lastOrNull()?.scoreA ?: 0,
                        scoreB = sets.lastOrNull()?.scoreB ?: 0
                    )
                    repository.saveMatch(updated)
                    val summary = "${match.teamAName} vs ${match.teamBName}: ${sets.joinToString(",") { "${it.scoreA}:${it.scoreB}" }}"
                    syncTournamentSpecStateLocked(tId, AuditEntry(System.currentTimeMillis(), match.id, summary, auditReason))
                }
            } else {
                val updated = match.copy(
                    completedSets = sets,
                    status = MatchStatus.FINISHED,
                    scoreA = sets.lastOrNull()?.scoreA ?: 0,
                    scoreB = sets.lastOrNull()?.scoreB ?: 0
                )
                repository.saveMatch(updated)
            }
            if (_activeMatchOverride.value?.id == match.id) {
                _activeMatchOverride.value = null
            }
        }
    }

    fun recordTournamentForfeit(match: Match, winnerSide: TeamSide) {
        viewModelScope.launch {
            val tId = match.tournamentId ?: return@launch
            tournamentMutex.withLock {
                val t = repository.getTournament(tId) ?: return@withLock
                pushTournamentUndoSnapshot(t)
                val setsCount = t.specConfig.setsToWin.coerceAtLeast(1)
                val pps = t.specConfig.pointsPerSet.coerceAtLeast(1)
                val forfeitSets = List(setsCount) { idx ->
                    val a = if (winnerSide == TeamSide.TEAM_A) pps else 0
                    val b = if (winnerSide == TeamSide.TEAM_B) pps else 0
                    SetResult(idx + 1, a, b, winnerSide)
                }
                val updatedDb = match.copy(
                    completedSets = forfeitSets,
                    status = MatchStatus.FINISHED,
                    scoreA = forfeitSets.last().scoreA,
                    scoreB = forfeitSets.last().scoreB
                )
                repository.saveMatch(updatedDb)
                val updatedSpecs = t.specMatches.map { sm ->
                    if (sm.id == match.id) {
                        sm.copy(
                            isForfeit = true,
                            isAutoWithdrawalForfeit = false,
                            winnerSide = winnerSide,
                            status = SpecMatchStatus.FORFEIT,
                            sets = forfeitSets.map { SetScore(it.scoreA, it.scoreB) }
                        )
                    } else sm
                }
                repository.saveTournament(t.copy(specMatches = updatedSpecs))
                val wName = if (winnerSide == TeamSide.TEAM_A) match.teamAName else match.teamBName
                syncTournamentSpecStateLocked(
                    tId,
                    AuditEntry(System.currentTimeMillis(), match.id, "W.O./Forfeit: Sieg für $wName (${match.teamAName} vs ${match.teamBName})", "Forfeit")
                )
            }
            if (_activeMatchOverride.value?.id == match.id) {
                _activeMatchOverride.value = null
            }
        }
    }

    fun scorePoint(side: TeamSide) {
        val match = currentMatch.value ?: return
        // Points can always be scored even during timeout; scoring cancels the active timeout countdown
        if (_activeTimeoutSide.value != null) {
            timeoutJob?.cancel()
            _activeTimeoutSide.value = null
            _activeTimeoutRemaining.value = null
        }
        _rotationPhaseOverride.value = null
        val updated = ScoreEngine.scorePoint(match, side)
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
            if (updated.status == MatchStatus.FINISHED && updated.tournamentId != null) {
                tournamentMutex.withLock {
                    syncTournamentSpecStateLocked(
                        updated.tournamentId,
                        AuditEntry(System.currentTimeMillis(), updated.id, "${updated.teamAName} vs ${updated.teamBName} beendet", "Live-Scoreboard")
                    )
                }
            }
        }
    }

    fun undoScore() {
        val match = currentMatch.value ?: return
        _rotationPhaseOverride.value = null
        val updated = ScoreEngine.undo(match)
        // If undo reverted the active timeout, dismiss countdown cleanly
        if (updated.timeoutStartTime == null && _activeTimeoutSide.value != null) {
            timeoutJob?.cancel()
            _activeTimeoutSide.value = null
            _activeTimeoutRemaining.value = null
        }
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
            if (match.status == MatchStatus.FINISHED && updated.status != MatchStatus.FINISHED && updated.tournamentId != null) {
                tournamentMutex.withLock {
                    syncTournamentSpecStateLocked(
                        updated.tournamentId,
                        AuditEntry(System.currentTimeMillis(), updated.id, "${updated.teamAName} vs ${updated.teamBName} wieder geöffnet", "Live-Undo")
                    )
                }
            }
        }
    }

    fun manualChangeServer(side: TeamSide, playerIndex: Int) {
        val match = currentMatch.value ?: return
        val updated = ScoreEngine.manualChangeServer(match, side, playerIndex)
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
        }
    }

    fun recordTimeout(side: TeamSide) {
        val match = currentMatch.value ?: return
        val updated = ScoreEngine.recordTimeout(match, side)
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
        }

        // Start preset timeout countdown immediately; trigger Trillerpfeife upon expiry!
        val duration = _timeoutPresetSeconds.value.coerceAtLeast(1)
        val teamLabel = if (side == TeamSide.TEAM_A) updated.teamAName else updated.teamBName
        timeoutJob?.cancel()
        _timeoutExpiredNotice.value = null
        _activeTimeoutSide.value = side
        _activeTimeoutRemaining.value = duration

        timeoutJob = viewModelScope.launch {
            var remaining = duration
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1
                _activeTimeoutRemaining.value = remaining
            }
            // Countdown reached 00:00 -> play Trillerpfeife & resume match timer cleanly
            _activeTimeoutSide.value = null
            _activeTimeoutRemaining.value = null
            _timeoutExpiredNotice.value = "Auszeit beendet ($teamLabel)"

            val latest = currentMatch.value
            if (latest != null && latest.timeoutStartTime != null) {
                val resumed = ScoreEngine.resumeFromTimeout(latest)
                repository.saveMatch(resumed)
                if (_activeMatchOverride.value?.id == resumed.id) {
                    _activeMatchOverride.value = resumed
                }
            }

            WhistleSoundEngine.playTrillWhistle()
            delay(3500L)
            _timeoutExpiredNotice.value = null
        }
    }

    fun stopTimeoutCountdown() {
        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null
        viewModelScope.launch {
            val match = currentMatch.value ?: return@launch
            if (match.timeoutStartTime != null) {
                val resumed = ScoreEngine.resumeFromTimeout(match)
                repository.saveMatch(resumed)
                if (_activeMatchOverride.value?.id == resumed.id) {
                    _activeMatchOverride.value = resumed
                }
            }
        }
    }

    fun toggleMatchPause() {
        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null
        val match = currentMatch.value ?: return
        val updated = ScoreEngine.togglePause(match)
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
        }
    }

    fun finishCurrentSet() {
        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null

        val match = currentMatch.value ?: return
        val updated = ScoreEngine.finishSet(match)
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
            if (updated.status == MatchStatus.FINISHED && updated.tournamentId != null) {
                tournamentMutex.withLock {
                    syncTournamentSpecStateLocked(
                        updated.tournamentId,
                        AuditEntry(System.currentTimeMillis(), updated.id, "${updated.teamAName} vs ${updated.teamBName} beendet", "Live-Scoreboard")
                    )
                }
            }
        }
    }

    fun forceFinishMatch() {
        timeoutJob?.cancel()
        _activeTimeoutSide.value = null
        _activeTimeoutRemaining.value = null

        val match = currentMatch.value ?: return
        val updated = ScoreEngine.forceFinishMatch(match)
        viewModelScope.launch {
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
            if (updated.status == MatchStatus.FINISHED && updated.tournamentId != null) {
                tournamentMutex.withLock {
                    syncTournamentSpecStateLocked(
                        updated.tournamentId,
                        AuditEntry(System.currentTimeMillis(), updated.id, "${updated.teamAName} vs ${updated.teamBName} beendet", "Live-Scoreboard")
                    )
                }
            }
        }
    }

    fun resetScoreboardResults() {
        viewModelScope.launch {
            repository.resetScoreboardHistory()
        }
    }

    // =========================================================================
    // ROTATION ACTIONS & SCOREBOARD LINKING
    // =========================================================================

    fun openRotationSetupFromScoreboard() {
        viewModelScope.launch {
            val match = currentMatch.value
            if (match != null && !match.rotationLinked) {
                val countA = match.matchConfig.fieldFormatA.fieldPlayerCount.coerceIn(2, 6)
                val countB = match.matchConfig.fieldFormatB.fieldPlayerCount.coerceIn(2, 6)
                val isFreshMatch = match.setStartTime == null &&
                    match.scoreA == 0 &&
                    match.scoreB == 0 &&
                    match.completedSets.isEmpty()
                val syncedA = if (isFreshMatch) (_rotationConfigA.value.rotationStep % countA) else (match.servingPlayerIndexA % countA)
                val syncedB = if (isFreshMatch) (_rotationConfigB.value.rotationStep % countB) else (match.servingPlayerIndexB % countB)
                val updated = match.copy(
                    rotationLinked = true,
                    rotationStepA = syncedA,
                    servingPlayerIndexA = syncedA,
                    rotationStepB = syncedB,
                    servingPlayerIndexB = syncedB
                )
                repository.saveMatch(updated)
                if (_activeMatchOverride.value?.id == updated.id) {
                    _activeMatchOverride.value = updated
                }
            }
            _returnToScoreAfterRotation.value = true
            currentTab.value = NavigationTab.ROTATION
        }
    }

    fun returnFromRotationToScoreboard() {
        _returnToScoreAfterRotation.value = false
        currentTab.value = NavigationTab.SCORE
    }

    fun toggleRotationLinked(linked: Boolean) {
        _rotationPhaseOverride.value = null
        viewModelScope.launch {
            val match = currentMatch.value ?: return@launch
            val updated = match.copy(rotationLinked = linked)
            repository.saveMatch(updated)
            if (_activeMatchOverride.value?.id == updated.id) {
                _activeMatchOverride.value = updated
            }
        }
    }

    fun setRotationSystem(side: TeamSide, system: RotationSystem) {
        if (side == TeamSide.TEAM_A) {
            _rotationConfigA.value = _rotationConfigA.value.copy(system = system)
        } else {
            _rotationConfigB.value = _rotationConfigB.value.copy(system = system)
        }
    }

    /**
     * Coupled phase control:
     * Selecting SERVE on side X automatically puts the opponent in RECEIVE (and vice versa).
     * Selecting BASE or IN_GAME updates both sides to that phase.
     */
    fun setRotationPhase(side: TeamSide, phase: RotationPhase) {
        val (phaseA, phaseB) = when (phase) {
            RotationPhase.BASE -> RotationPhase.BASE to RotationPhase.BASE
            RotationPhase.IN_GAME -> RotationPhase.IN_GAME to RotationPhase.IN_GAME
            RotationPhase.SERVE -> if (side == TeamSide.TEAM_A) {
                RotationPhase.SERVE to RotationPhase.RECEIVE
            } else {
                RotationPhase.RECEIVE to RotationPhase.SERVE
            }
            RotationPhase.RECEIVE -> if (side == TeamSide.TEAM_A) {
                RotationPhase.RECEIVE to RotationPhase.SERVE
            } else {
                RotationPhase.SERVE to RotationPhase.RECEIVE
            }
        }
        _rotationPhaseOverride.value = phaseA to phaseB
        _rotationConfigA.value = _rotationConfigA.value.copy(phase = phaseA)
        _rotationConfigB.value = _rotationConfigB.value.copy(phase = phaseB)
    }

    fun toggleLiberoActive(side: TeamSide) {
        if (side == TeamSide.TEAM_A) {
            _rotationConfigA.value = _rotationConfigA.value.copy(liberoActive = !_rotationConfigA.value.liberoActive)
        } else {
            _rotationConfigB.value = _rotationConfigB.value.copy(liberoActive = !_rotationConfigB.value.liberoActive)
        }
    }

    private fun fieldFormatForCourtCount(courtCount: Int, squadSize: Int): FieldFormat {
        val clamped = courtCount.coerceIn(2, 6)
        if (clamped == 6) {
            return when (squadSize.coerceIn(6, 10)) {
                7 -> FieldFormat.FORMAT_6V6_PLUS_1
                8 -> FieldFormat.FORMAT_6V6_PLUS_2
                9 -> FieldFormat.FORMAT_6V6_PLUS_3
                10 -> FieldFormat.FORMAT_6V6_PLUS_4
                else -> FieldFormat.FORMAT_6V6
            }
        }
        return FieldFormat.values().firstOrNull { it.fieldPlayerCount == clamped } ?: FieldFormat.FORMAT_6V6
    }

    fun setRotationPlayerCount(side: TeamSide, count: Int) {
        val clamped = count.coerceIn(2, 6)
        val cur = if (side == TeamSide.TEAM_A) _rotationConfigA.value else _rotationConfigB.value
        val updatedCfg = cur.copy(
            playerCountOnCourt = clamped,
            rotationStep = cur.rotationStep % clamped,
            benchPlayers = cur.benchPlayers.take(TeamRotationConfig.MAX_TOTAL_PLAYERS - clamped)
        )
        if (side == TeamSide.TEAM_A) {
            _rotationConfigA.value = updatedCfg
        } else {
            _rotationConfigB.value = updatedCfg
        }
        val match = currentMatch.value
        if (match != null && match.rotationLinked) {
            val newFormat = fieldFormatForCourtCount(clamped, clamped + updatedCfg.benchPlayers.size)
            val updatedMatch = if (side == TeamSide.TEAM_A) {
                match.copy(
                    matchConfig = match.matchConfig.copy(fieldFormatA = newFormat),
                    rotationStepA = match.rotationStepA % clamped,
                    servingPlayerIndexA = match.servingPlayerIndexA % clamped
                )
            } else {
                match.copy(
                    matchConfig = match.matchConfig.copy(fieldFormatB = newFormat),
                    rotationStepB = match.rotationStepB % clamped,
                    servingPlayerIndexB = match.servingPlayerIndexB % clamped
                )
            }
            viewModelScope.launch {
                repository.saveMatch(updatedMatch)
                if (_activeMatchOverride.value?.id == updatedMatch.id) {
                    _activeMatchOverride.value = updatedMatch
                }
            }
        }
    }

    fun stepTeamRotation(side: TeamSide, delta: Int) {
        val match = currentMatch.value
        val countA = rotationConfigA.value.playerCountOnCourt.coerceIn(2, 6)
        val countB = rotationConfigB.value.playerCountOnCourt.coerceIn(2, 6)

        if (match != null && match.rotationLinked) {
            val updated = if (side == TeamSide.TEAM_A) {
                val next = ((match.rotationStepA + delta) % countA + countA) % countA
                match.copy(rotationStepA = next, servingPlayerIndexA = next)
            } else {
                val next = ((match.rotationStepB + delta) % countB + countB) % countB
                match.copy(rotationStepB = next, servingPlayerIndexB = next)
            }
            viewModelScope.launch {
                repository.saveMatch(updated)
                if (_activeMatchOverride.value?.id == updated.id) {
                    _activeMatchOverride.value = updated
                }
            }
        } else {
            if (side == TeamSide.TEAM_A) {
                val next = ((_rotationConfigA.value.rotationStep + delta) % countA + countA) % countA
                _rotationConfigA.value = _rotationConfigA.value.copy(rotationStep = next)
            } else {
                val next = ((_rotationConfigB.value.rotationStep + delta) % countB + countB) % countB
                _rotationConfigB.value = _rotationConfigB.value.copy(rotationStep = next)
            }
        }
    }

    fun updateTeamLineup(
        side: TeamSide,
        teamName: String,
        playerCountOnCourt: Int,
        jerseys: Map<String, String>,
        names: Map<String, String>,
        benchPlayers: List<RotationPlayer>
    ) {
        val clampedCount = playerCountOnCourt.coerceIn(2, 6)
        val maxBench = (TeamRotationConfig.MAX_TOTAL_PLAYERS - clampedCount).coerceAtLeast(0)
        val trimmedBench = benchPlayers.take(maxBench)
        val cleanedName = teamName.trim()

        if (side == TeamSide.TEAM_A) {
            if (cleanedName.isNotBlank()) _rotationTeamNameA.value = cleanedName
            val cur = _rotationConfigA.value
            _rotationConfigA.value = cur.copy(
                playerCountOnCourt = clampedCount,
                rotationStep = cur.rotationStep % clampedCount,
                jerseyNumbers = jerseys,
                playerNames = names,
                benchPlayers = trimmedBench
            )
        } else {
            if (cleanedName.isNotBlank()) _rotationTeamNameB.value = cleanedName
            val cur = _rotationConfigB.value
            _rotationConfigB.value = cur.copy(
                playerCountOnCourt = clampedCount,
                rotationStep = cur.rotationStep % clampedCount,
                jerseyNumbers = jerseys,
                playerNames = names,
                benchPlayers = trimmedBench
            )
        }

        val match = currentMatch.value
        if (match != null && match.rotationLinked) {
            val newFormat = fieldFormatForCourtCount(clampedCount, clampedCount + trimmedBench.size)
            val updatedMatch = if (side == TeamSide.TEAM_A) {
                match.copy(
                    teamAName = if (cleanedName.isNotBlank()) cleanedName else match.teamAName,
                    matchConfig = match.matchConfig.copy(fieldFormatA = newFormat),
                    rotationStepA = match.rotationStepA % clampedCount,
                    servingPlayerIndexA = match.servingPlayerIndexA % clampedCount
                )
            } else {
                match.copy(
                    teamBName = if (cleanedName.isNotBlank()) cleanedName else match.teamBName,
                    matchConfig = match.matchConfig.copy(fieldFormatB = newFormat),
                    rotationStepB = match.rotationStepB % clampedCount,
                    servingPlayerIndexB = match.servingPlayerIndexB % clampedCount
                )
            }
            viewModelScope.launch {
                repository.saveMatch(updatedMatch)
                if (_activeMatchOverride.value?.id == updatedMatch.id) {
                    _activeMatchOverride.value = updatedMatch
                }
            }
        }
    }

    fun substitutePlayer(side: TeamSide, courtRoleKey: String, benchPlayerId: String) {
        val cfg = if (side == TeamSide.TEAM_A) _rotationConfigA.value else _rotationConfigB.value
        val benchIdx = cfg.benchPlayers.indexOfFirst { it.id == benchPlayerId }
        if (benchIdx < 0) return

        val incoming = cfg.benchPlayers[benchIdx]
        val defaultJersey = TeamRotationConfig.defaultJerseys()[courtRoleKey] ?: "?"
        val outgoingJersey = cfg.jerseyNumbers[courtRoleKey].orEmpty().ifBlank { defaultJersey }
        val outgoingName = cfg.playerNames[courtRoleKey].orEmpty().ifBlank { courtRoleKey }

        val updatedJerseys = cfg.jerseyNumbers.toMutableMap().apply {
            this[courtRoleKey] = incoming.jerseyNumber.ifBlank { defaultJersey }
        }
        val updatedNames = cfg.playerNames.toMutableMap().apply {
            this[courtRoleKey] = incoming.name
        }
        val updatedBench = cfg.benchPlayers.toMutableList().apply {
            this[benchIdx] = RotationPlayer(
                id = UUID.randomUUID().toString(),
                name = outgoingName,
                jerseyNumber = outgoingJersey
            )
        }
        val logEntry = "#${incoming.jerseyNumber} ${incoming.name} ↔ #$outgoingJersey $outgoingName ($courtRoleKey)"
        val updatedCfg = cfg.copy(
            jerseyNumbers = updatedJerseys,
            playerNames = updatedNames,
            benchPlayers = updatedBench,
            substitutionLog = (listOf(logEntry) + cfg.substitutionLog).take(20)
        )
        if (side == TeamSide.TEAM_A) {
            _rotationConfigA.value = updatedCfg
        } else {
            _rotationConfigB.value = updatedCfg
        }
    }

    fun loadFinalizedTeamsIntoRotation() {
        val currentTeams = allTeams.value
        if (currentTeams.size == 2) {
            viewModelScope.launch {
                syncTeamsToRotation(currentTeams[0], currentTeams[1])
            }
        }
    }

    fun toggleTrackedCourtTarget(side: TeamSide, token: CourtPlayerToken) {
        val candidate = TrackedCourtTarget(
            side = side,
            originalRole = token.originalRole,
            isLiberoTarget = token.isLibero
        )
        _trackedCourtTarget.value = if (_trackedCourtTarget.value == candidate) null else candidate
    }

    fun clearTrackedCourtTarget() {
        _trackedCourtTarget.value = null
    }

    private fun calculateScoreboardStandings(matches: List<Match>): List<ScoreboardTeamStanding> {
        val finished = matches.filter { it.status == MatchStatus.FINISHED }
        if (finished.isEmpty()) return emptyList()

        val map = mutableMapOf<String, ScoreboardTeamStanding>()

        for (m in finished) {
            val a = map.getOrPut(m.teamAName) { ScoreboardTeamStanding(teamName = m.teamAName) }
            val b = map.getOrPut(m.teamBName) { ScoreboardTeamStanding(teamName = m.teamBName) }

            val setsWonA = m.completedSets.count { it.winner == TeamSide.TEAM_A }
            val setsWonB = m.completedSets.count { it.winner == TeamSide.TEAM_B }

            val ptsA = m.completedSets.sumOf { it.scoreA } + (if (m.completedSets.isEmpty()) m.scoreA else 0)
            val ptsB = m.completedSets.sumOf { it.scoreB } + (if (m.completedSets.isEmpty()) m.scoreB else 0)

            val aWonMatch = setsWonA > setsWonB || (setsWonA == setsWonB && ptsA > ptsB)
            val bWonMatch = setsWonB > setsWonA || (setsWonA == setsWonB && ptsB > ptsA)

            map[m.teamAName] = a.copy(
                matchesPlayed = a.matchesPlayed + 1,
                wins = a.wins + if (aWonMatch) 1 else 0,
                losses = a.losses + if (bWonMatch) 1 else 0,
                setsWon = a.setsWon + setsWonA,
                setsLost = a.setsLost + setsWonB,
                pointsFor = a.pointsFor + ptsA,
                pointsAgainst = a.pointsAgainst + ptsB
            )

            map[m.teamBName] = b.copy(
                matchesPlayed = b.matchesPlayed + 1,
                wins = b.wins + if (bWonMatch) 1 else 0,
                losses = b.losses + if (aWonMatch) 1 else 0,
                setsWon = b.setsWon + setsWonB,
                setsLost = b.setsLost + setsWonA,
                pointsFor = b.pointsFor + ptsB,
                pointsAgainst = b.pointsAgainst + ptsA
            )
        }

        val list = map.values.toMutableList()
        list.sortWith { t1, t2 ->
            if (t2.wins != t1.wins) return@sortWith t2.wins.compareTo(t1.wins)
            if (t2.setDiff != t1.setDiff) return@sortWith t2.setDiff.compareTo(t1.setDiff)
            if (t2.pointsDiff != t1.pointsDiff) return@sortWith t2.pointsDiff.compareTo(t1.pointsDiff)
            t2.pointsFor.compareTo(t1.pointsFor)
        }

        return list.mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    // =========================================================================
    // TOURNAMENT ACTIONS (SPEC v1.0)
    // =========================================================================

    fun selectTournament(tournamentId: String?) {
        _selectedTournamentId.value = tournamentId
        _tournamentUndoStack.value = emptyList()
        _tournamentRedoStack.value = emptyList()
        if (tournamentId != null) {
            viewModelScope.launch {
                tournamentMutex.withLock {
                    val t = repository.getTournament(tournamentId) ?: return@withLock
                    val dbMatches = repository.getMatchesByTournament(tournamentId).first()
                    val playableSpecCount = t.specMatches.count { !it.isByeMatch && it.teamAId != null && it.teamBId != null }
                    val needsHeal = (dbMatches.isEmpty() && t.teamIds.size >= 2) ||
                        (playableSpecCount > dbMatches.size)
                    if (needsHeal) {
                        syncTournamentSpecStateLocked(
                            tournamentId = tournamentId,
                            newAuditEntry = AuditEntry(System.currentTimeMillis(), tournamentId, "Spielplan automatisch abgeglichen", "Auto-Repair")
                        )
                    } else {
                        val specTeams = buildSpecTeamsForTournament(t)
                        val sched = SpecSchedulerAndReferees.scheduleMatches(t.specMatches, specTeams, t.courtCount, t.specConfig)
                        val (_, _, refW) = SpecSchedulerAndReferees.assignReferees(sched.scheduledMatches, specTeams, t.specConfig)
                        _tournamentWarningsById.value = _tournamentWarningsById.value + (
                            tournamentId to (SpecScoring.checkScoringConfigWarnings(t.specConfig) + sched.warnings + refW).distinctBy { it.specId + it.message }
                            )
                    }
                }
            }
        }
    }

    fun clearTournamentFeedback() {
        _tournamentFeedbackMessage.value = null
    }

    fun consumeAutoOpenCertificates() {
        _autoOpenCertificatesTournamentId.value = null
    }

    private suspend fun pushTournamentUndoSnapshot(t: Tournament) {
        val ms = repository.getMatchesByTournament(t.id).first()
        _tournamentUndoStack.value = (_tournamentUndoStack.value + TournamentUndoSnapshot(t, ms)).takeLast(25)
        _tournamentRedoStack.value = emptyList()
    }

    fun undoTournamentAction() {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val stack = _tournamentUndoStack.value
                val snap = stack.lastOrNull() ?: return@withLock
                val curT = repository.getTournament(snap.tournament.id)
                if (curT != null) {
                    val curMs = repository.getMatchesByTournament(curT.id).first()
                    _tournamentRedoStack.value = (_tournamentRedoStack.value + TournamentUndoSnapshot(curT, curMs)).takeLast(25)
                }
                _tournamentUndoStack.value = stack.dropLast(1)
                repository.replaceTournamentSnapshot(snap.tournament, snap.matches)
                val overrideMatch = _activeMatchOverride.value
                if (overrideMatch != null && overrideMatch.tournamentId == snap.tournament.id) {
                    _activeMatchOverride.value = snap.matches.find { it.id == overrideMatch.id }
                }
                _tournamentFeedbackMessage.value = "Änderung rückgängig gemacht (${snap.matches.size} Spiele)."
            }
        }
    }

    fun redoTournamentAction() {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val stack = _tournamentRedoStack.value
                val snap = stack.lastOrNull() ?: return@withLock
                val curT = repository.getTournament(snap.tournament.id)
                if (curT != null) {
                    val curMs = repository.getMatchesByTournament(curT.id).first()
                    _tournamentUndoStack.value = (_tournamentUndoStack.value + TournamentUndoSnapshot(curT, curMs)).takeLast(25)
                }
                _tournamentRedoStack.value = stack.dropLast(1)
                repository.replaceTournamentSnapshot(snap.tournament, snap.matches)
                val overrideMatch = _activeMatchOverride.value
                if (overrideMatch != null && overrideMatch.tournamentId == snap.tournament.id) {
                    _activeMatchOverride.value = snap.matches.find { it.id == overrideMatch.id }
                }
                _tournamentFeedbackMessage.value = "Änderung wiederhergestellt (${snap.matches.size} Spiele)."
            }
        }
    }

    fun generateRandomTestTournamentTeams(count: Int = 17, seed: Int = 2026): List<ManualTournamentTeam> {
        return TournamentEngine.generateRandomTestTournamentTeams(count, seed)
    }

    fun parseTeamsFromText(text: String): List<ManualTournamentTeam> {
        return TournamentEngine.parseTeamsFromText(text)
    }

    private fun buildSpecTeamsForTournament(
        tournament: Tournament,
        allDbTeams: List<Team> = allTeams.value
    ): List<SpecTeam> {
        val manualById = tournament.manualTeams.associateBy { it.id }
        val dbById = allDbTeams.associateBy { it.id }
        return tournament.teamIds.mapIndexed { idx, tid ->
            val mt = manualById[tid]
            val dt = dbById[tid]
            if (mt != null) {
                SpecTeam(
                    id = mt.id,
                    name = mt.name,
                    shortName = mt.shortName.ifBlank { mt.name.trim().take(4).uppercase() },
                    clubId = mt.club,
                    skillLevel = mt.skillLevel.coerceIn(1, 100),
                    present = mt.present,
                    withdrawn = mt.withdrawn,
                    registrationOrder = if (mt.registrationOrder > 0) mt.registrationOrder else idx
                )
            } else {
                val name = dt?.name ?: "Team ${idx + 1}"
                SpecTeam(
                    id = tid,
                    name = name,
                    shortName = name.take(4).uppercase(),
                    clubId = "",
                    skillLevel = 50,
                    present = true,
                    withdrawn = false,
                    registrationOrder = idx
                )
            }
        }
    }

    private fun generateInitialSpecMatchesForTournament(
        tournamentId: String,
        mode: TournamentMode,
        allParticipatingIds: List<String>,
        specTeams: List<SpecTeam>,
        groupMap: Map<String, List<String>>,
        specConfig: SpecTournamentConfig
    ): List<SpecMatch> {
        if (allParticipatingIds.size < 2) return emptyList()
        val (seededOrder, _) = SpecSeedingAndGroups.buildSeedOrder(specTeams, specConfig.seed, specConfig.manualSeedOrder)
        val rawSpecMatches = mutableListOf<SpecMatch>()

        when (mode) {
            TournamentMode.ROUND_ROBIN -> {
                rawSpecMatches.addAll(
                    SpecRoundRobinAndSwiss.generateRoundRobin(
                        ids = allParticipatingIds,
                        groupId = "A",
                        doubleRoundRobin = specConfig.doubleRoundRobin,
                        idPrefix = "rr_${tournamentId.takeLast(6)}"
                    )
                )
            }
            TournamentMode.GROUPS_KO,
            TournamentMode.PLACEMENT,
            TournamentMode.KING_OF_THE_COURT,
            TournamentMode.DOUBLE_ELIMINATION -> {
                groupMap.keys.sorted().forEach { gid ->
                    val ids = groupMap[gid].orEmpty()
                    rawSpecMatches.addAll(
                        SpecRoundRobinAndSwiss.generateRoundRobin(
                            ids = ids,
                            groupId = gid,
                            doubleRoundRobin = specConfig.doubleRoundRobin,
                            idPrefix = "grp_${tournamentId.takeLast(4)}_$gid"
                        )
                    )
                }
            }
            TournamentMode.SWISS_SYSTEM -> {
                val swissRes = SpecRoundRobinAndSwiss.generateSwissRound(
                    teams = specTeams,
                    seededOrder = seededOrder,
                    previousMatches = emptyList(),
                    roundNumber = 1,
                    config = specConfig,
                    idPrefix = "swiss_${tournamentId.takeLast(6)}"
                )
                rawSpecMatches.addAll(swissRes.matches)
            }
            TournamentMode.SINGLE_ELIMINATION -> {
                val seMatches = SpecBracket.buildSingleEliminationBracket(
                    seededTeamIds = allParticipatingIds,
                    consolationMode = specConfig.consolationMode,
                    idPrefix = "ko_${tournamentId.takeLast(6)}"
                )
                val (resolvedSe, _) = SpecBracket.resolveBracket(specTeams, emptyMap(), seMatches, specConfig)
                rawSpecMatches.addAll(resolvedSe)
            }
        }
        return rawSpecMatches
    }

    private fun toPlayableRoomMatch(
        sm: SpecMatch,
        tournamentId: String,
        matchConfig: MatchConfig,
        teamNameById: Map<String, String>
    ): Match? {
        if (sm.isByeMatch || sm.teamAId == null || sm.teamBId == null) return null
        val sets = sm.sets.mapIndexed { idx, s ->
            val w = if (s.a > s.b) TeamSide.TEAM_A else if (s.b > s.a) TeamSide.TEAM_B else (sm.winnerSide ?: TeamSide.TEAM_A)
            SetResult(idx + 1, s.a, s.b, w)
        }
        val dbStatus = when (sm.status) {
            SpecMatchStatus.FINAL, SpecMatchStatus.FORFEIT -> MatchStatus.FINISHED
            SpecMatchStatus.RUNNING -> MatchStatus.RUNNING
            SpecMatchStatus.SCHEDULED -> MatchStatus.DRAFT
        }
        return Match(
            id = sm.id,
            teamAId = sm.teamAId,
            teamBId = sm.teamBId,
            teamAName = teamNameById[sm.teamAId] ?: sm.teamAId,
            teamBName = teamNameById[sm.teamBId] ?: sm.teamBId,
            matchConfig = matchConfig,
            status = dbStatus,
            completedSets = sets,
            scoreA = sets.lastOrNull()?.scoreA ?: 0,
            scoreB = sets.lastOrNull()?.scoreB ?: 0,
            tournamentId = tournamentId,
            groupId = sm.groupId,
            tournamentRound = sm.roundLabel,
            courtNumber = sm.courtNumber ?: 1
        )
    }

    private suspend fun syncTournamentSpecState(
        tournamentId: String,
        newAuditEntry: AuditEntry? = null,
        rescheduleAllUnlocked: Boolean = false
    ) {
        tournamentMutex.withLock {
            syncTournamentSpecStateLocked(tournamentId, newAuditEntry, rescheduleAllUnlocked)
        }
    }

    private suspend fun syncTournamentSpecStateLocked(
        tournamentId: String,
        newAuditEntry: AuditEntry? = null,
        rescheduleAllUnlocked: Boolean = false
    ): ScheduleResult? {
        val t = repository.getTournament(tournamentId) ?: return null
        val dbMatches = repository.getMatchesByTournament(tournamentId).first()
        val alreadyCreatedCertificates = t.status == TournamentStatus.COMPLETED &&
            t.specConfig.auditLog.any { it.reason == "Urkunden" }
        val specTeams = buildSpecTeamsForTournament(t)
        val existingSpecById = t.specMatches.associateBy { it.id }
        val dbById = dbMatches.associateBy { it.id }

        // Preserve ALL SpecMatches (including unresolved KO placeholders and Bye records), merging live DB state
        val mergedFromSpec = t.specMatches.map { existingSm ->
            val dbM = dbById[existingSm.id]
            if (dbM != null) {
                val merged = TournamentEngine.toSpecMatch(dbM, existingSm)
                if (existingSm.isAutoWithdrawalForfeit) merged.copy(isAutoWithdrawalForfeit = true) else merged
            } else {
                existingSm
            }
        }
        val extraDbMatches = dbMatches.filter { it.id !in existingSpecById }.map { TournamentEngine.toSpecMatch(it, null) }
        var specMatches = mergedFromSpec + extraDbMatches

        // Self-heal if tournament lost all matches somehow while having >= 2 teams
        if (specMatches.isEmpty() && t.teamIds.size >= 2) {
            specMatches = generateInitialSpecMatchesForTournament(
                tournamentId = t.id,
                mode = t.mode,
                allParticipatingIds = t.teamIds,
                specTeams = specTeams,
                groupMap = t.groupAssignments,
                specConfig = t.specConfig
            )
        }

        // Apply withdrawal policy (W-04) including clean revert if a team was un-withdrawn
        val withdrawnIds = specTeams.filter { it.withdrawn }.map { it.id }.toSet()
        val beforeWithdrawalById = specMatches.associateBy { it.id }
        specMatches = SpecScoring.applyWithdrawalPolicy(specMatches, withdrawnIds, t.specConfig.withdrawalPolicy)

        // Resolve KO placeholders (F-06)
        val (resolvedSpec, bracketWarnings) = SpecBracket.resolveBracket(specTeams, t.groupAssignments, specMatches, t.specConfig)

        // Re-schedule unlocked SCHEDULED matches while keeping RUNNING/FINAL/FORFEIT frozen (G-06 / I-14)
        val toSchedule = if (rescheduleAllUnlocked) {
            resolvedSpec.map { sm ->
                if (sm.status == SpecMatchStatus.SCHEDULED) sm.copy(slotIndex = null, courtNumber = null) else sm
            }
        } else resolvedSpec

        val sched = SpecSchedulerAndReferees.scheduleMatches(toSchedule, specTeams, t.courtCount, t.specConfig)
        val (withRefs, _, refWarnings) = SpecSchedulerAndReferees.assignReferees(sched.scheduledMatches, specTeams, t.specConfig)

        val teamNameById = specTeams.associate { it.id to it.name }
        val upsertMatches = mutableListOf<Match>()
        val activePlayableIds = HashSet<String>()

        withRefs.forEach { sm ->
            if (sm.isByeMatch || sm.teamAId == null || sm.teamBId == null) return@forEach
            activePlayableIds.add(sm.id)

            val existingDb = dbById[sm.id]
            val prevSm = beforeWithdrawalById[sm.id]
            val newCourt = sm.courtNumber ?: existingDb?.courtNumber ?: 1
            val newTeamAId = sm.teamAId
            val newTeamBId = sm.teamBId
            val newNameA = teamNameById[newTeamAId] ?: existingDb?.teamAName ?: "Team"
            val newNameB = teamNameById[newTeamBId] ?: existingDb?.teamBName ?: "Team"

            if (existingDb == null) {
                toPlayableRoomMatch(sm, tournamentId, t.matchConfig, teamNameById)?.let { upsertMatches.add(it) }
            } else {
                val revertedAutoWo = (prevSm?.isAutoWithdrawalForfeit == true) &&
                    !sm.isForfeit &&
                    sm.status == SpecMatchStatus.SCHEDULED

                val newStatus = when {
                    revertedAutoWo -> MatchStatus.DRAFT
                    sm.status == SpecMatchStatus.FINAL || sm.status == SpecMatchStatus.FORFEIT -> MatchStatus.FINISHED
                    sm.status == SpecMatchStatus.RUNNING -> MatchStatus.RUNNING
                    existingDb.status == MatchStatus.FINISHED && !sm.isForfeit -> MatchStatus.FINISHED
                    else -> existingDb.status
                }
                val newSets = when {
                    revertedAutoWo -> emptyList()
                    sm.isForfeit && sm.sets.isNotEmpty() && existingDb.completedSets.isEmpty() ->
                        sm.sets.mapIndexed { idx, s ->
                            SetResult(idx + 1, s.a, s.b, if (s.a > s.b) TeamSide.TEAM_A else TeamSide.TEAM_B)
                        }
                    else -> existingDb.completedSets
                }
                val newScoreA = if (revertedAutoWo) 0 else existingDb.scoreA
                val newScoreB = if (revertedAutoWo) 0 else existingDb.scoreB
                val newConfig = if (newStatus == MatchStatus.DRAFT) t.matchConfig else existingDb.matchConfig

                if (newCourt != existingDb.courtNumber ||
                    newTeamAId != existingDb.teamAId ||
                    newTeamBId != existingDb.teamBId ||
                    newNameA != existingDb.teamAName ||
                    newNameB != existingDb.teamBName ||
                    newStatus != existingDb.status ||
                    newSets != existingDb.completedSets ||
                    newScoreA != existingDb.scoreA ||
                    newScoreB != existingDb.scoreB ||
                    newConfig != existingDb.matchConfig ||
                    sm.roundLabel != existingDb.tournamentRound ||
                    sm.groupId != existingDb.groupId
                ) {
                    upsertMatches.add(
                        existingDb.copy(
                            teamAId = newTeamAId,
                            teamBId = newTeamBId,
                            teamAName = newNameA,
                            teamBName = newNameB,
                            matchConfig = newConfig,
                            courtNumber = newCourt,
                            tournamentRound = sm.roundLabel.ifBlank { existingDb.tournamentRound },
                            groupId = sm.groupId ?: existingDb.groupId,
                            status = newStatus,
                            completedSets = newSets,
                            scoreA = newScoreA,
                            scoreB = newScoreB
                        )
                    )
                }
            }
        }

        val deleteIds = dbMatches.filter { it.id !in activePlayableIds }.map { it.id }
        val upsertById = upsertMatches.associateBy { it.id }
        val deleteSet = deleteIds.toSet()
        val finalDbMatches = dbMatches.filter { it.id !in deleteSet }.map { upsertById[it.id] ?: it } +
            upsertMatches.filter { it.id !in dbById }

        var updatedLog = if (newAuditEntry != null) {
            (t.specConfig.auditLog + newAuditEntry).takeLast(200)
        } else t.specConfig.auditLog

        val preliminaryTournament = t.copy(
            specConfig = t.specConfig.copy(auditLog = updatedLog),
            specMatches = withRefs
        )

        val isNowPodiumReady = TournamentCertificateEngine.isPodiumReady(preliminaryTournament, finalDbMatches)
        var updatedStatus = t.status
        if (!alreadyCreatedCertificates && isNowPodiumReady) {
            val certs = TournamentCertificateEngine.extractTopThreeCertificates(
                tournament = preliminaryTournament,
                matches = finalDbMatches,
                dbTeams = allTeams.value
            )
            if (certs.isNotEmpty()) {
                updatedStatus = TournamentStatus.COMPLETED
                val summary = certs.joinToString(", ") { "${it.rank}. ${it.teamName}" }
                updatedLog = (updatedLog + AuditEntry(
                    timestampMillis = System.currentTimeMillis(),
                    matchId = tournamentId,
                    summary = "Sieger-Urkunden (Plätze 1–3) automatisch erstellt: $summary",
                    reason = "Urkunden"
                )).takeLast(200)
                _tournamentFeedbackMessage.value = "Endrunde beendet! Urkunden für Plätze 1–3 wurden automatisch erstellt."
                _autoOpenCertificatesTournamentId.value = tournamentId
            }
        }

        val updatedTournament = preliminaryTournament.copy(
            status = updatedStatus,
            specConfig = preliminaryTournament.specConfig.copy(auditLog = updatedLog)
        )

        repository.syncTournamentAndMatches(
            tournament = updatedTournament,
            matchesToUpsert = upsertMatches,
            matchIdsToDelete = deleteIds
        )

        val allWarnings = (SpecScoring.checkScoringConfigWarnings(updatedTournament.specConfig) +
            bracketWarnings + sched.warnings + refWarnings)
            .distinctBy { it.specId + it.message }
        _tournamentWarningsById.value = _tournamentWarningsById.value + (tournamentId to allWarnings)

        return sched
    }

    fun createTournament(
        name: String,
        sportType: SportType,
        fieldFormat: FieldFormat,
        matchConfig: MatchConfig,
        mode: TournamentMode,
        groupsCount: Int,
        seedingMode: SeedingMode,
        participatingTeams: List<Team>,
        manualTeams: List<ManualTournamentTeam> = emptyList(),
        location: String = "",
        date: String = "",
        courtCount: Int = 1,
        templateId: String? = null,
        pointCap: Int? = null,
        pointsForWin: Int = 2,
        pointsForDraw: Int = 1,
        pointsForLoss: Int = 0,
        minFemales: Int = 0,
        minMales: Int = 0,
        doubleRoundRobin: Boolean = false,
        specConfigOverride: SpecTournamentConfig? = null
    ) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val tournamentId = "tournament_${UUID.randomUUID()}"

                val effectiveManualTeams: List<ManualTournamentTeam> = if (manualTeams.isNotEmpty()) {
                    manualTeams.mapIndexed { idx, mt ->
                        mt.copy(
                            registrationOrder = if (mt.registrationOrder > 0) mt.registrationOrder else idx,
                            shortName = mt.shortName.ifBlank { mt.name.trim().take(4).uppercase() },
                            skillLevel = if (mt.skillLevel in 1..100) mt.skillLevel else (mt.skill.coerceIn(1, 5) * 20)
                        )
                    }
                } else {
                    participatingTeams.mapIndexed { idx, t ->
                        ManualTournamentTeam(
                            id = t.id,
                            name = t.name,
                            skill = 3,
                            club = "",
                            shortName = t.name.trim().take(4).uppercase(),
                            skillLevel = 50,
                            present = true,
                            withdrawn = false,
                            registrationOrder = idx
                        )
                    }
                }

                val rawParticipatingIds = effectiveManualTeams.map { it.id }
                val effectiveCourts = courtCount.coerceIn(1, 16)
                val baseSpecCfg = (specConfigOverride ?: SpecTournamentConfig()).copy(
                    schemaVersion = 2,
                    scoringSystem = specConfigOverride?.scoringSystem ?: if (pointsForWin >= 3) ScoringSystem.DVV3 else ScoringSystem.CLASSIC2,
                    validationMode = if (matchConfig.timeLimitMinutes != null) {
                        MatchValidationMode.TIMED
                    } else if (matchConfig.setsMode == SetsMode.BEST_OF_2) {
                        MatchValidationMode.FIXED_SETS
                    } else {
                        specConfigOverride?.validationMode ?: MatchValidationMode.BEST_OF
                    },
                    setsToWin = matchConfig.setsMode.setsToWin,
                    fixedSetsCount = if (matchConfig.setsMode == SetsMode.BEST_OF_2) 2 else 1,
                    pointsPerSet = matchConfig.pointsPerSet,
                    pointCap = pointCap,
                    doubleRoundRobin = doubleRoundRobin
                )

                val allParticipatingIds = if (mode == TournamentMode.SINGLE_ELIMINATION && seedingMode == SeedingMode.RANDOM_DRAW) {
                    Mulberry32(baseSpecCfg.seed).shuffled(rawParticipatingIds)
                } else {
                    rawParticipatingIds
                }

                val teamById = effectiveManualTeams.associateBy { it.id }
                val specTeams = allParticipatingIds.mapIndexedNotNull { _, tid ->
                    teamById[tid]?.let { mt ->
                        SpecTeam(
                            id = mt.id,
                            name = mt.name,
                            shortName = mt.shortName,
                            clubId = mt.club,
                            skillLevel = mt.skillLevel,
                            present = mt.present,
                            withdrawn = mt.withdrawn,
                            registrationOrder = mt.registrationOrder
                        )
                    }
                }

                val effectiveGroupsCount = when (mode) {
                    TournamentMode.SWISS_SYSTEM,
                    TournamentMode.ROUND_ROBIN,
                    TournamentMode.SINGLE_ELIMINATION -> 1
                    else -> groupsCount.coerceIn(2, 4)
                }

                val groupMap = when (mode) {
                    TournamentMode.SWISS_SYSTEM -> mapOf("SWISS" to allParticipatingIds)
                    TournamentMode.ROUND_ROBIN -> mapOf("A" to allParticipatingIds)
                    TournamentMode.SINGLE_ELIMINATION -> mapOf("KO" to allParticipatingIds)
                    else -> TournamentEngine.distributeTeamsToGroups(
                        teamIds = allParticipatingIds,
                        manualTeams = effectiveManualTeams,
                        groupsCount = effectiveGroupsCount,
                        seedingMode = seedingMode,
                        seed = baseSpecCfg.seed
                    )
                }

                val rawSpecMatches = generateInitialSpecMatchesForTournament(
                    tournamentId = tournamentId,
                    mode = mode,
                    allParticipatingIds = allParticipatingIds,
                    specTeams = specTeams,
                    groupMap = groupMap,
                    specConfig = baseSpecCfg
                )

                val sched = SpecSchedulerAndReferees.scheduleMatches(rawSpecMatches, specTeams, effectiveCourts, baseSpecCfg)
                val (withRefs, _, refWarnings) = SpecSchedulerAndReferees.assignReferees(sched.scheduledMatches, specTeams, baseSpecCfg)

                val tournament = Tournament(
                    id = tournamentId,
                    name = name.trim().ifBlank { "Volleyball Turnier" },
                    sportType = sportType,
                    fieldFormat = fieldFormat,
                    matchConfig = matchConfig,
                    mode = mode,
                    status = TournamentStatus.IN_PROGRESS,
                    groupsCount = effectiveGroupsCount,
                    seedingMode = seedingMode,
                    teamIds = allParticipatingIds,
                    groupAssignments = groupMap,
                    manualTeams = effectiveManualTeams,
                    location = location,
                    date = date,
                    courtCount = effectiveCourts,
                    templateId = templateId,
                    pointCap = pointCap,
                    pointsForWin = pointsForWin,
                    pointsForDraw = pointsForDraw,
                    pointsForLoss = pointsForLoss,
                    minFemales = minFemales,
                    minMales = minMales,
                    specConfig = baseSpecCfg.copy(
                        auditLog = listOf(
                            AuditEntry(System.currentTimeMillis(), tournamentId, "Turnier erstellt (${allParticipatingIds.size} Teams, $effectiveCourts Felder)", "Start")
                        )
                    ),
                    specMatches = withRefs
                )

                val teamNameById = effectiveManualTeams.associate { it.id to it.name }
                val matches = withRefs.mapNotNull { sm ->
                    toPlayableRoomMatch(sm, tournamentId, matchConfig, teamNameById)
                }

                repository.syncTournamentAndMatches(tournament, matches)
                val allWarnings = (SpecScoring.checkScoringConfigWarnings(tournament.specConfig) + sched.warnings + refWarnings)
                    .distinctBy { it.specId + it.message }
                _tournamentWarningsById.value = _tournamentWarningsById.value + (tournamentId to allWarnings)
                _selectedTournamentId.value = tournamentId
            }
        }
    }

    fun updateTournamentMetadata(
        tournamentId: String,
        name: String,
        location: String,
        date: String,
        courtCount: Int,
        status: TournamentStatus
    ) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                pushTournamentUndoSnapshot(t)
                val newCourts = courtCount.coerceIn(1, 16)
                val updated = t.copy(
                    name = name.trim().ifBlank { t.name },
                    location = location.trim(),
                    date = date.trim(),
                    courtCount = newCourts,
                    status = status
                )
                repository.saveTournament(updated)
                syncTournamentSpecStateLocked(
                    tournamentId = tournamentId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), tournamentId, "Metadaten / $newCourts Felder aktualisiert", "Bearbeitung"),
                    rescheduleAllUnlocked = (newCourts != t.courtCount)
                )
            }
        }
    }

    fun updateTournamentFullSettings(
        tournamentId: String,
        name: String,
        location: String,
        date: String,
        courtCount: Int,
        status: TournamentStatus,
        matchConfig: MatchConfig,
        groupsCount: Int,
        pointCap: Int?,
        specConfig: SpecTournamentConfig
    ) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                pushTournamentUndoSnapshot(t)

                val dbMatches = repository.getMatchesByTournament(tournamentId).first()
                val hasStartedMatches = dbMatches.any { it.status == MatchStatus.FINISHED || it.status == MatchStatus.RUNNING }

                val newCourts = courtCount.coerceIn(1, 16)
                val effectiveGroups = when (t.mode) {
                    TournamentMode.SWISS_SYSTEM,
                    TournamentMode.ROUND_ROBIN,
                    TournamentMode.SINGLE_ELIMINATION -> 1
                    else -> groupsCount.coerceIn(2, 4)
                }

                val syncedSpecCfg = specConfig.copy(
                    schemaVersion = 2,
                    validationMode = when {
                        matchConfig.timeLimitMinutes != null -> MatchValidationMode.TIMED
                        matchConfig.setsMode == SetsMode.BEST_OF_2 -> MatchValidationMode.FIXED_SETS
                        else -> MatchValidationMode.BEST_OF
                    },
                    setsToWin = matchConfig.setsMode.setsToWin,
                    fixedSetsCount = if (matchConfig.setsMode == SetsMode.BEST_OF_2) 2 else 1,
                    pointsPerSet = matchConfig.pointsPerSet,
                    pointCap = pointCap,
                    auditLog = t.specConfig.auditLog,
                    manualSeedOrder = t.specConfig.manualSeedOrder,
                    manualTieResolutions = t.specConfig.manualTieResolutions
                )
                val ptsWin = if (syncedSpecCfg.scoringSystem == ScoringSystem.DVV3) 3 else 2

                val rebuildGroupsNeeded = !hasStartedMatches && (
                    effectiveGroups != t.groupsCount ||
                        syncedSpecCfg.doubleRoundRobin != t.specConfig.doubleRoundRobin
                    )

                if (rebuildGroupsNeeded && t.teamIds.size >= 2) {
                    val newGroupMap = when (t.mode) {
                        TournamentMode.SWISS_SYSTEM -> mapOf("SWISS" to t.teamIds)
                        TournamentMode.ROUND_ROBIN -> mapOf("A" to t.teamIds)
                        TournamentMode.SINGLE_ELIMINATION -> mapOf("KO" to t.teamIds)
                        else -> TournamentEngine.distributeTeamsToGroups(
                            teamIds = t.teamIds,
                            manualTeams = t.manualTeams,
                            groupsCount = effectiveGroups,
                            seedingMode = t.seedingMode,
                            seed = syncedSpecCfg.seed
                        )
                    }
                    val tempT = t.copy(
                        name = name.trim().ifBlank { t.name },
                        location = location.trim(),
                        date = date.trim(),
                        courtCount = newCourts,
                        status = status,
                        matchConfig = matchConfig,
                        groupsCount = effectiveGroups,
                        groupAssignments = newGroupMap,
                        pointCap = pointCap,
                        pointsForWin = ptsWin,
                        specConfig = syncedSpecCfg
                    )
                    val specTeams = buildSpecTeamsForTournament(tempT)
                    val rawSpecs = generateInitialSpecMatchesForTournament(
                        tournamentId = t.id,
                        mode = t.mode,
                        allParticipatingIds = t.teamIds,
                        specTeams = specTeams,
                        groupMap = newGroupMap,
                        specConfig = syncedSpecCfg
                    )
                    val sched = SpecSchedulerAndReferees.scheduleMatches(rawSpecs, specTeams, newCourts, syncedSpecCfg)
                    val (withRefs, _, _) = SpecSchedulerAndReferees.assignReferees(sched.scheduledMatches, specTeams, syncedSpecCfg)
                    val teamNames = specTeams.associate { it.id to it.name }
                    val newDbMatches = withRefs.mapNotNull { sm ->
                        toPlayableRoomMatch(sm, t.id, matchConfig, teamNames)
                    }
                    repository.replaceTournamentSnapshot(tempT.copy(specMatches = withRefs), newDbMatches)
                    syncTournamentSpecStateLocked(
                        tournamentId = tournamentId,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), tournamentId, "Gruppen/Regeln & Spielplan neu generiert ($newCourts Felder)", "Regel-Update"),
                        rescheduleAllUnlocked = false
                    )
                    _tournamentFeedbackMessage.value = "Einstellungen aktualisiert & Gruppen-Spielplan auf $newCourts Feld(ern) neu erstellt."
                } else {
                    val updated = t.copy(
                        name = name.trim().ifBlank { t.name },
                        location = location.trim(),
                        date = date.trim(),
                        courtCount = newCourts,
                        status = status,
                        matchConfig = matchConfig,
                        pointCap = pointCap,
                        pointsForWin = ptsWin,
                        specConfig = syncedSpecCfg
                    )
                    repository.saveTournament(updated)
                    val schedRes = syncTournamentSpecStateLocked(
                        tournamentId = tournamentId,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), tournamentId, "Einstellungen/Regeln aktualisiert ($newCourts Felder)", "Regel-Update"),
                        rescheduleAllUnlocked = true
                    )
                    val openCount = schedRes?.scheduledMatches?.count { !it.isByeMatch && it.status == SpecMatchStatus.SCHEDULED } ?: 0
                    _tournamentFeedbackMessage.value = if (hasStartedMatches && effectiveGroups != t.groupsCount) {
                        "Regeln & $openCount offene Spiele auf $newCourts Feld(ern) optimiert (Gruppenanzahl wegen laufender Spiele beibehalten)."
                    } else {
                        "Einstellungen & $openCount offene Spiele auf $newCourts Feld(ern) optimiert."
                    }
                }
            }
        }
    }

    fun updateTournamentSpecConfig(tournamentId: String, newConfig: SpecTournamentConfig, reschedule: Boolean = true) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                pushTournamentUndoSnapshot(t)
                val ptsWin = if (newConfig.scoringSystem == ScoringSystem.DVV3) 3 else 2
                val updated = t.copy(
                    pointsForWin = ptsWin,
                    pointsForDraw = 1,
                    pointsForLoss = 0,
                    specConfig = newConfig
                )
                repository.saveTournament(updated)
                syncTournamentSpecStateLocked(
                    tournamentId = tournamentId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), tournamentId, "Turnierregeln / Plan-Einstellungen geändert", "Regel-Update"),
                    rescheduleAllUnlocked = reschedule
                )
            }
        }
    }

    fun setTournamentLiveDelay(tournamentId: String, delayMinutes: Int) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                val clamped = delayMinutes.coerceIn(0, 240)
                val updatedCfg = t.specConfig.copy(
                    liveDelayMinutes = clamped,
                    auditLog = (t.specConfig.auditLog + AuditEntry(System.currentTimeMillis(), tournamentId, "Live-Verzug auf +${clamped} Min gesetzt", "Zeitplan")).takeLast(200)
                )
                repository.saveTournament(t.copy(specConfig = updatedCfg))
            }
        }
    }

    fun rescheduleAndReassignReferees(tournamentId: String) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                pushTournamentUndoSnapshot(t)
                val sched = syncTournamentSpecStateLocked(
                    tournamentId = tournamentId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), tournamentId, "Zeitplan & Schiedsrichter neu optimiert", "Optimierung"),
                    rescheduleAllUnlocked = true
                )
                val totalPlayable = sched?.scheduledMatches?.count { !it.isByeMatch && it.teamAId != null && it.teamBId != null } ?: 0
                val openCount = sched?.scheduledMatches?.count { !it.isByeMatch && it.status == SpecMatchStatus.SCHEDULED } ?: 0
                val b2b = sched?.b2bCount ?: 0
                _tournamentFeedbackMessage.value = "Spielplan & SR optimiert: $totalPlayable Spiele ($openCount offen) auf ${t.courtCount} Feld(ern) · B2B: $b2b."
            }
        }
    }

    fun manualChangeMatchReferee(tournamentId: String, matchId: String, newRefTeamIds: List<String>, lock: Boolean = true) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                val specTeams = buildSpecTeamsForTournament(t)
                val target = t.specMatches.find { it.id == matchId } ?: return@withLock
                for (rid in newRefTeamIds) {
                    val err = SpecSchedulerAndReferees.validateManualRefereeChange(target, rid, t.specMatches, specTeams)
                    if (err != null) {
                        _tournamentFeedbackMessage.value = err
                        return@withLock
                    }
                }
                pushTournamentUndoSnapshot(t)
                val updatedSpecs = t.specMatches.map { sm ->
                    if (sm.id == matchId) sm.copy(refereeTeamIds = newRefTeamIds, refereeManualLocked = lock) else sm
                }
                val audit = AuditEntry(System.currentTimeMillis(), matchId, "SR manuell geändert (${target.roundLabel})", "SR-Zuweisung")
                repository.saveTournament(
                    t.copy(
                        specMatches = updatedSpecs,
                        specConfig = t.specConfig.copy(auditLog = (t.specConfig.auditLog + audit).takeLast(200))
                    )
                )
            }
        }
    }

    fun setManualTieResolution(tournamentId: String, groupKey: String, orderedTeamIds: List<String>) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                pushTournamentUndoSnapshot(t)
                val newMap = t.specConfig.manualTieResolutions.toMutableMap()
                newMap[groupKey] = orderedTeamIds
                val updatedCfg = t.specConfig.copy(
                    manualTieResolutions = newMap,
                    auditLog = (t.specConfig.auditLog + AuditEntry(System.currentTimeMillis(), groupKey, "Manueller Los-/Gleichstandsentscheid gespeichert", "Tie-Break")).takeLast(200)
                )
                repository.saveTournament(t.copy(specConfig = updatedCfg))
                syncTournamentSpecStateLocked(tournamentId)
            }
        }
    }

    fun updateTournamentMatch(match: Match) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                repository.saveMatch(match)
                match.tournamentId?.let { syncTournamentSpecStateLocked(it) }
            }
        }
    }

    fun updateTournamentTeam(tournamentId: String, updatedTeam: ManualTournamentTeam) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                val prevTeam = t.manualTeams.find { it.id == updatedTeam.id }
                pushTournamentUndoSnapshot(t)
                val newManualTeams = if (t.manualTeams.any { it.id == updatedTeam.id }) {
                    t.manualTeams.map { if (it.id == updatedTeam.id) updatedTeam else it }
                } else {
                    t.manualTeams + updatedTeam
                }
                val updatedTournament = t.copy(manualTeams = newManualTeams)
                repository.saveTournament(updatedTournament)

                repository.getTeam(updatedTeam.id)?.let { dbTeam ->
                    repository.saveTeam(dbTeam.copy(name = updatedTeam.name))
                }

                val matches = repository.getMatchesByTournament(tournamentId).first()
                val updatedMatches = matches.mapNotNull { m ->
                    var changed = false
                    var newNameA = m.teamAName
                    var newNameB = m.teamBName
                    if (m.teamAId == updatedTeam.id && m.teamAName != updatedTeam.name) {
                        newNameA = updatedTeam.name
                        changed = true
                    }
                    if (m.teamBId == updatedTeam.id && m.teamBName != updatedTeam.name) {
                        newNameB = updatedTeam.name
                        changed = true
                    }
                    if (changed) m.copy(teamAName = newNameA, teamBName = newNameB) else null
                }
                if (updatedMatches.isNotEmpty()) {
                    repository.saveMatches(updatedMatches)
                }
                val reason = when {
                    updatedTeam.withdrawn -> "Rückzug (W.O.)"
                    prevTeam?.withdrawn == true && !updatedTeam.withdrawn -> "Reaktiviert"
                    !updatedTeam.present -> "Abwesend"
                    else -> "Team bearbeitet"
                }
                syncTournamentSpecStateLocked(
                    tournamentId = tournamentId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), updatedTeam.id, "Team '${updatedTeam.name}' aktualisiert ($reason)", reason),
                    rescheduleAllUnlocked = true
                )
                _tournamentFeedbackMessage.value = "Team '${updatedTeam.name}' ($reason) & Spielplan aktualisiert."
            }
        }
    }

    fun addTournamentTeamPostCreation(tournamentId: String, newTeam: ManualTournamentTeam) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                val cleanedName = newTeam.name.trim()
                if (cleanedName.isBlank()) return@withLock
                if (t.manualTeams.any { it.name.equals(cleanedName, ignoreCase = true) }) {
                    _tournamentFeedbackMessage.value = "Ein Team mit dem Namen '$cleanedName' existiert bereits."
                    return@withLock
                }
                pushTournamentUndoSnapshot(t)

                val existingMatches = repository.getMatchesByTournament(tournamentId).first()
                val hasStartedMatches = existingMatches.any { it.status == MatchStatus.FINISHED || it.status == MatchStatus.RUNNING }

                val assignedTeam = newTeam.copy(
                    name = cleanedName.take(40),
                    shortName = newTeam.shortName.ifBlank { cleanedName.take(4).uppercase() },
                    skillLevel = if (newTeam.skillLevel in 1..100) newTeam.skillLevel else (newTeam.skill.coerceIn(1, 5) * 20),
                    registrationOrder = t.manualTeams.size
                )
                val newIds = t.teamIds + assignedTeam.id
                val newManuals = t.manualTeams + assignedTeam

                if (!hasStartedMatches) {
                    // Clean full regeneration before first whistle -> balanced Kreisverfahren & Serpentine
                    val effectiveIds = if (t.mode == TournamentMode.SINGLE_ELIMINATION && t.seedingMode == SeedingMode.RANDOM_DRAW) {
                        Mulberry32(t.specConfig.seed).shuffled(newIds)
                    } else newIds
                    val newGroupMap = when (t.mode) {
                        TournamentMode.SWISS_SYSTEM -> mapOf("SWISS" to effectiveIds)
                        TournamentMode.ROUND_ROBIN -> mapOf("A" to effectiveIds)
                        TournamentMode.SINGLE_ELIMINATION -> mapOf("KO" to effectiveIds)
                        else -> TournamentEngine.distributeTeamsToGroups(
                            teamIds = effectiveIds,
                            manualTeams = newManuals,
                            groupsCount = t.groupsCount,
                            seedingMode = t.seedingMode,
                            seed = t.specConfig.seed
                        )
                    }
                    val tempT = t.copy(
                        teamIds = effectiveIds,
                        manualTeams = newManuals,
                        groupAssignments = newGroupMap
                    )
                    val specTeams = buildSpecTeamsForTournament(tempT)
                    val rawSpecs = generateInitialSpecMatchesForTournament(
                        tournamentId = t.id,
                        mode = t.mode,
                        allParticipatingIds = effectiveIds,
                        specTeams = specTeams,
                        groupMap = newGroupMap,
                        specConfig = t.specConfig
                    )
                    val sched = SpecSchedulerAndReferees.scheduleMatches(rawSpecs, specTeams, t.courtCount, t.specConfig)
                    val (withRefs, _, _) = SpecSchedulerAndReferees.assignReferees(sched.scheduledMatches, specTeams, t.specConfig)
                    val teamNames = specTeams.associate { it.id to it.name }
                    val newDbMatches = withRefs.mapNotNull { sm ->
                        toPlayableRoomMatch(sm, t.id, t.matchConfig, teamNames)
                    }
                    repository.replaceTournamentSnapshot(tempT.copy(specMatches = withRefs), newDbMatches)
                    syncTournamentSpecStateLocked(
                        tournamentId = tournamentId,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), assignedTeam.id, "Team '${assignedTeam.name}' ergänzt & Spielplan neu balanciert", "Nachmeldung"),
                        rescheduleAllUnlocked = false
                    )
                    _tournamentFeedbackMessage.value = "Team '${assignedTeam.name}' hinzugefügt & Spielplan neu balanciert (${newDbMatches.size} Spiele)."
                    return@withLock
                }

                // Tournament already underway: surgical integration
                val targetGroup = when (t.mode) {
                    TournamentMode.ROUND_ROBIN -> "A"
                    TournamentMode.SWISS_SYSTEM -> "SWISS"
                    TournamentMode.SINGLE_ELIMINATION -> "KO"
                    else -> t.groupAssignments.entries.minByOrNull { it.value.size }?.key ?: "A"
                }
                val newGroupMap = t.groupAssignments.toMutableMap()
                val existingInGroup = newGroupMap[targetGroup].orEmpty()
                newGroupMap[targetGroup] = existingInGroup + assignedTeam.id

                val newDbMatches = mutableListOf<Match>()
                val newSpecMatches = t.specMatches.toMutableList()
                val teamNames = newManuals.associate { it.id to it.name }
                var notice = "Team '${assignedTeam.name}' nachgemeldet & Spielplan optimiert."

                when (t.mode) {
                    TournamentMode.SWISS_SYSTEM -> {
                        val openByeIdx = newSpecMatches.indexOfLast { it.isByeMatch && it.teamAId != null && it.teamBId == null }
                        val byeOpp = if (openByeIdx >= 0) newSpecMatches[openByeIdx].teamAId else null
                        val currentRoundNum = newSpecMatches.maxOfOrNull { it.phaseRound } ?: 1
                        val oppFinished = if (openByeIdx >= 0) {
                            existingMatches.any { it.tournamentRound == "Swiss Runde $currentRoundNum" && (it.teamAId == byeOpp || it.teamBId == byeOpp) }
                        } else true
                        if (openByeIdx >= 0 && byeOpp != null && !oppFinished) {
                            val byeSm = newSpecMatches[openByeIdx]
                            val pairedSm = byeSm.copy(
                                isByeMatch = false,
                                sourceB = MatchSource.DirectTeam(assignedTeam.id),
                                teamBId = assignedTeam.id,
                                status = SpecMatchStatus.SCHEDULED,
                                winnerSide = null
                            )
                            newSpecMatches[openByeIdx] = pairedSm
                            toPlayableRoomMatch(pairedSm, t.id, t.matchConfig, teamNames)?.let { newDbMatches.add(it) }
                            notice = "Team '${assignedTeam.name}' gegen bisherigen Freilos-Gegner (${teamNames[byeOpp]}) gepaart."
                        } else {
                            notice = "Team '${assignedTeam.name}' aufgenommen – wird ab der nächsten Swiss-Runde gepaart."
                        }
                    }
                    TournamentMode.SINGLE_ELIMINATION -> {
                        val byeSlotIdx = newSpecMatches.indexOfFirst {
                            it.isByeMatch && it.phaseRound == 1 && (it.teamAId != null || it.teamBId != null)
                        }
                        if (byeSlotIdx >= 0) {
                            val byeSm = newSpecMatches[byeSlotIdx]
                            val filledSm = if (byeSm.teamAId != null && byeSm.teamBId == null) {
                                byeSm.copy(
                                    isByeMatch = false,
                                    sourceB = MatchSource.DirectTeam(assignedTeam.id),
                                    teamBId = assignedTeam.id,
                                    status = SpecMatchStatus.SCHEDULED,
                                    winnerSide = null
                                )
                            } else {
                                byeSm.copy(
                                    isByeMatch = false,
                                    sourceA = MatchSource.DirectTeam(assignedTeam.id),
                                    teamAId = assignedTeam.id,
                                    status = SpecMatchStatus.SCHEDULED,
                                    winnerSide = null
                                )
                            }
                            newSpecMatches[byeSlotIdx] = filledSm
                            toPlayableRoomMatch(filledSm, t.id, t.matchConfig, teamNames)?.let { newDbMatches.add(it) }
                            notice = "Team '${assignedTeam.name}' in freies K.-o.-Freilos (Runde 1) eingesetzt."
                        } else {
                            notice = "Team '${assignedTeam.name}' eingetragen (Runde 1 ohne freies Freilos bereits gestartet)."
                        }
                    }
                    else -> {
                        existingInGroup.forEachIndexed { idx, oppId ->
                            val mid = "add_${UUID.randomUUID().toString().take(8)}"
                            newDbMatches.add(
                                Match(
                                    id = mid,
                                    teamAId = assignedTeam.id,
                                    teamBId = oppId,
                                    teamAName = assignedTeam.name,
                                    teamBName = teamNames[oppId] ?: "Team",
                                    matchConfig = t.matchConfig,
                                    status = MatchStatus.DRAFT,
                                    tournamentId = t.id,
                                    groupId = targetGroup,
                                    tournamentRound = "Nachmeldung ${idx + 1}",
                                    courtNumber = 1
                                )
                            )
                            newSpecMatches.add(
                                SpecMatch(
                                    id = mid,
                                    roundLabel = "Nachmeldung ${idx + 1}",
                                    groupId = targetGroup,
                                    isKo = false,
                                    sourceA = MatchSource.DirectTeam(assignedTeam.id),
                                    sourceB = MatchSource.DirectTeam(oppId),
                                    teamAId = assignedTeam.id,
                                    teamBId = oppId,
                                    phaseRound = existingInGroup.size + 1
                                )
                            )
                        }
                    }
                }

                repository.saveTournament(
                    t.copy(
                        teamIds = newIds,
                        manualTeams = newManuals,
                        groupAssignments = newGroupMap,
                        specMatches = newSpecMatches
                    )
                )
                if (newDbMatches.isNotEmpty()) repository.saveMatches(newDbMatches)
                syncTournamentSpecStateLocked(
                    tournamentId = tournamentId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), assignedTeam.id, "Team '${assignedTeam.name}' nachgemeldet ($targetGroup)", "Nachmeldung"),
                    rescheduleAllUnlocked = true
                )
                _tournamentFeedbackMessage.value = notice
            }
        }
    }

    fun removeTournamentTeam(tournamentId: String, teamId: String) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                if (t.teamIds.size <= 2) {
                    _tournamentFeedbackMessage.value = "Mindestens 2 Teams müssen im Turnier verbleiben."
                    return@withLock
                }
                pushTournamentUndoSnapshot(t)
                val removedName = t.manualTeams.find { it.id == teamId }?.name ?: teamId
                val allMatches = repository.getMatchesByTournament(tournamentId).first()
                val hasStartedMatches = allMatches.any { it.status == MatchStatus.FINISHED || it.status == MatchStatus.RUNNING }

                val newIds = t.teamIds.filter { it != teamId }
                val newManuals = t.manualTeams.filter { it.id != teamId }
                val cleanedManualSeed = t.specConfig.manualSeedOrder.filter { it != teamId }
                val cleanedManualTies = t.specConfig.manualTieResolutions.mapValues { (_, list) -> list.filter { it != teamId } }
                val cleanedCfg = t.specConfig.copy(
                    manualSeedOrder = cleanedManualSeed,
                    manualTieResolutions = cleanedManualTies
                )

                if (!hasStartedMatches) {
                    val newGroupMap = when (t.mode) {
                        TournamentMode.SWISS_SYSTEM -> mapOf("SWISS" to newIds)
                        TournamentMode.ROUND_ROBIN -> mapOf("A" to newIds)
                        TournamentMode.SINGLE_ELIMINATION -> mapOf("KO" to newIds)
                        else -> TournamentEngine.distributeTeamsToGroups(
                            teamIds = newIds,
                            manualTeams = newManuals,
                            groupsCount = t.groupsCount,
                            seedingMode = t.seedingMode,
                            seed = cleanedCfg.seed
                        )
                    }
                    val tempT = t.copy(
                        teamIds = newIds,
                        manualTeams = newManuals,
                        groupAssignments = newGroupMap,
                        specConfig = cleanedCfg
                    )
                    val specTeams = buildSpecTeamsForTournament(tempT)
                    val rawSpecs = generateInitialSpecMatchesForTournament(
                        tournamentId = t.id,
                        mode = t.mode,
                        allParticipatingIds = newIds,
                        specTeams = specTeams,
                        groupMap = newGroupMap,
                        specConfig = cleanedCfg
                    )
                    val sched = SpecSchedulerAndReferees.scheduleMatches(rawSpecs, specTeams, t.courtCount, cleanedCfg)
                    val (withRefs, _, _) = SpecSchedulerAndReferees.assignReferees(sched.scheduledMatches, specTeams, cleanedCfg)
                    val teamNames = specTeams.associate { it.id to it.name }
                    val newDbMatches = withRefs.mapNotNull { sm ->
                        toPlayableRoomMatch(sm, t.id, t.matchConfig, teamNames)
                    }
                    repository.replaceTournamentSnapshot(tempT.copy(specMatches = withRefs), newDbMatches)
                    syncTournamentSpecStateLocked(
                        tournamentId = tournamentId,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), teamId, "Team '$removedName' entfernt & Spielplan neu generiert", "Team-Entfernung"),
                        rescheduleAllUnlocked = false
                    )
                    _tournamentFeedbackMessage.value = "Team '$removedName' entfernt & Spielplan neu balanciert (${newDbMatches.size} Spiele)."
                    return@withLock
                }

                // Started tournament: remove team + its matches and prune broken references
                val removedMatchIds = allMatches.filter { it.teamAId == teamId || it.teamBId == teamId }.map { it.id }.toSet() +
                    t.specMatches.filter { it.teamAId == teamId || it.teamBId == teamId }.map { it.id }.toSet()
                val remainingMatches = allMatches.filter { it.id !in removedMatchIds }
                val newSpecMatches = t.specMatches
                    .filter { it.id !in removedMatchIds }
                    .map { sm ->
                        val cleanedDeps = sm.dependsOnMatchIds.filter { it !in removedMatchIds }
                        val cleanedRefs = sm.refereeTeamIds.filter { it != teamId }
                        sm.copy(dependsOnMatchIds = cleanedDeps, refereeTeamIds = cleanedRefs)
                    }
                val newGroups = t.groupAssignments.mapValues { (_, list) -> list.filter { it != teamId } }

                repository.replaceTournamentSnapshot(
                    t.copy(
                        teamIds = newIds,
                        manualTeams = newManuals,
                        groupAssignments = newGroups,
                        specConfig = cleanedCfg,
                        specMatches = newSpecMatches
                    ),
                    remainingMatches
                )
                syncTournamentSpecStateLocked(
                    tournamentId = tournamentId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), teamId, "Team '$removedName' aus Turnier entfernt", "Team-Entfernung"),
                    rescheduleAllUnlocked = true
                )
                _tournamentFeedbackMessage.value = "Team '$removedName' entfernt & verbleibender Spielplan optimiert."
            }
        }
    }

    fun renameTournamentMixerTeam(tournamentId: String, teamId: String, newName: String) {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            tournamentMutex.withLock {
                val t = repository.getTournament(tournamentId) ?: return@withLock
                val existingMt = t.manualTeams.find { it.id == teamId }
                if (existingMt != null) {
                    pushTournamentUndoSnapshot(t)
                    val updatedTeam = existingMt.copy(name = cleaned.take(40), shortName = cleaned.take(4).uppercase())
                    val newManuals = t.manualTeams.map { if (it.id == teamId) updatedTeam else it }
                    repository.saveTournament(t.copy(manualTeams = newManuals))
                    repository.getTeam(teamId)?.let { dbTeam ->
                        repository.saveTeam(dbTeam.copy(name = updatedTeam.name))
                    }
                    syncTournamentSpecStateLocked(
                        tournamentId = tournamentId,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), teamId, "Team umbenannt zu '${updatedTeam.name}'", "Team bearbeitet"),
                        rescheduleAllUnlocked = false
                    )
                    return@withLock
                }
                val team = repository.getTeam(teamId)
                if (team != null) {
                    repository.saveTeam(team.copy(name = cleaned))
                }
                val matches = repository.getMatchesByTournament(tournamentId).first()
                val updatedMatches = matches.mapNotNull { m ->
                    var changed = false
                    var newNameA = m.teamAName
                    var newNameB = m.teamBName
                    if (m.teamAId == teamId && m.teamAName != cleaned) {
                        newNameA = cleaned
                        changed = true
                    }
                    if (m.teamBId == teamId && m.teamBName != cleaned) {
                        newNameB = cleaned
                        changed = true
                    }
                    if (changed) m.copy(teamAName = newNameA, teamBName = newNameB) else null
                }
                if (updatedMatches.isNotEmpty()) {
                    repository.saveMatches(updatedMatches)
                }
            }
        }
    }

    fun generateKoMatchesForTournament(tournament: Tournament) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val latestT = repository.getTournament(tournament.id) ?: return@withLock
                val allMatches = repository.getMatchesByTournament(latestT.id).first()
                val groupStandings = mutableMapOf<String, List<Standing>>()
                val teamNames = mutableMapOf<String, String>()
                allTeams.value.forEach { teamNames[it.id] = it.name }
                latestT.manualTeams.forEach { teamNames[it.id] = it.name }

                val hasUnresolvedTie = latestT.groupAssignments.any { (groupId, teamIds) ->
                    val specRows = TournamentEngine.calculateSpecStandings(latestT, groupId, teamIds, teamNames, allMatches)
                    groupStandings[groupId] = specRows.map { r ->
                        Standing(latestT.id, groupId, r.teamId, r.teamName, r.matchesPlayed, r.wins, r.draws, r.losses, r.matchPoints, r.setsWon, r.setsLost, r.pointsFor, r.pointsAgainst, r.rank)
                    }
                    specRows.any { it.decisionRequired }
                }
                if (hasUnresolvedTie) {
                    _tournamentFeedbackMessage.value = "In mindestens einer Gruppe ist ein Gleichstand („Entscheidung erf.“) offen – bitte erst unter „Tabelle“ per Losentscheid klären."
                    return@withLock
                }

                pushTournamentUndoSnapshot(latestT)
                val koMatches = TournamentEngine.generateKoMatches(
                    tournament = latestT,
                    groupStandings = groupStandings,
                    allGroupMatches = allMatches.filter { it.groupId != "KO" },
                    seedingMode = latestT.seedingMode,
                    courtCount = latestT.courtCount
                )
                if (koMatches.isNotEmpty()) {
                    val groupsSorted = groupStandings.keys.sorted()
                    val koSpecs = koMatches.mapIndexed { idx, m ->
                        val baseSm = TournamentEngine.toSpecMatch(m, null)
                        if (latestT.mode == TournamentMode.GROUPS_KO &&
                            (latestT.seedingMode == SeedingMode.CROSS_SEEDING || latestT.seedingMode == SeedingMode.TOP_SEEDS_SEPARATED)
                        ) {
                            when {
                                groupsSorted.size == 2 && koMatches.size == 2 && idx == 0 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[0], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[1], 2)
                                )
                                groupsSorted.size == 2 && koMatches.size == 2 && idx == 1 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[1], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[0], 2)
                                )
                                groupsSorted.size == 2 && koMatches.size == 1 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[0], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[1], 1)
                                )
                                groupsSorted.size == 4 && koMatches.size == 4 && idx == 0 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[0], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[1], 2)
                                )
                                groupsSorted.size == 4 && koMatches.size == 4 && idx == 1 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[2], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[3], 2)
                                )
                                groupsSorted.size == 4 && koMatches.size == 4 && idx == 2 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[1], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[0], 2)
                                )
                                groupsSorted.size == 4 && koMatches.size == 4 && idx == 3 -> baseSm.copy(
                                    sourceA = MatchSource.GroupRank(groupsSorted[3], 1),
                                    sourceB = MatchSource.GroupRank(groupsSorted[2], 2)
                                )
                                else -> baseSm
                            }
                        } else baseSm
                    }
                    repository.syncTournamentAndMatches(
                        tournament = latestT.copy(specMatches = latestT.specMatches + koSpecs),
                        matchesToUpsert = koMatches
                    )
                    syncTournamentSpecStateLocked(
                        tournamentId = latestT.id,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), latestT.id, "${koMatches.size} K.-o.-Spiele erzeugt", "K.-o.-Start"),
                        rescheduleAllUnlocked = true
                    )
                }
            }
        }
    }

    fun generatePlacementMatchesForTournament(tournament: Tournament) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val latestT = repository.getTournament(tournament.id) ?: return@withLock
                val allMatches = repository.getMatchesByTournament(latestT.id).first()
                if (allMatches.any { it.groupId == "KO" }) return@withLock
                val teamNames = mutableMapOf<String, String>()
                allTeams.value.forEach { teamNames[it.id] = it.name }
                latestT.manualTeams.forEach { teamNames[it.id] = it.name }

                val hasUnresolvedTie = latestT.groupAssignments.any { (groupId, teamIds) ->
                    TournamentEngine.calculateSpecStandings(latestT, groupId, teamIds, teamNames, allMatches)
                        .any { it.decisionRequired }
                }
                if (hasUnresolvedTie) {
                    _tournamentFeedbackMessage.value = "In mindestens einer Gruppe ist ein Gleichstand („Entscheidung erf.“) offen – bitte erst unter „Tabelle“ klären."
                    return@withLock
                }

                pushTournamentUndoSnapshot(latestT)
                val groupStandings = mutableMapOf<String, List<Standing>>()
                latestT.groupAssignments.forEach { (groupId, teamIds) ->
                    groupStandings[groupId] = TournamentEngine.calculateStandings(
                        tournamentId = latestT.id,
                        groupId = groupId,
                        teamIds = teamIds,
                        teamNames = teamNames,
                        matches = allMatches,
                        pointsForWin = latestT.pointsForWin,
                        pointsForDraw = latestT.pointsForDraw,
                        pointsForLoss = latestT.pointsForLoss,
                        specConfig = latestT.specConfig,
                        manualTeams = latestT.manualTeams,
                        extraSpecMatches = latestT.specMatches
                    )
                }

                val placementMatches = TournamentEngine.generatePlacementMatches(
                    tournament = latestT,
                    groupStandings = groupStandings,
                    allGroupMatches = allMatches.filter { it.groupId != "KO" },
                    courtCount = latestT.courtCount
                )
                if (placementMatches.isNotEmpty()) {
                    val groupsSorted = groupStandings.keys.sorted()
                    val placementSpecs = placementMatches.mapIndexed { idx, m ->
                        val baseSm = TournamentEngine.toSpecMatch(m, null)
                        if (groupsSorted.size == 2) {
                            baseSm.copy(
                                sourceA = MatchSource.GroupRank(groupsSorted[0], idx + 1),
                                sourceB = MatchSource.GroupRank(groupsSorted[1], idx + 1)
                            )
                        } else baseSm
                    }
                    repository.syncTournamentAndMatches(
                        tournament = latestT.copy(specMatches = latestT.specMatches + placementSpecs),
                        matchesToUpsert = placementMatches
                    )
                    syncTournamentSpecStateLocked(
                        tournamentId = latestT.id,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), latestT.id, "${placementMatches.size} Platzierungsspiele erzeugt", "Platzierung"),
                        rescheduleAllUnlocked = true
                    )
                }
            }
        }
    }

    fun advanceTournamentKoRound(tournament: Tournament) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val latestT = repository.getTournament(tournament.id) ?: return@withLock
                val allMatches = repository.getMatchesByTournament(latestT.id).first()
                val koMatches = allMatches.filter { it.groupId == "KO" }
                val teamNames = mutableMapOf<String, String>()
                allTeams.value.forEach { teamNames[it.id] = it.name }
                latestT.manualTeams.forEach { teamNames[it.id] = it.name }

                pushTournamentUndoSnapshot(latestT)
                val nextMatches = TournamentEngine.advanceKoRoundIfReady(
                    tournament = latestT,
                    koMatches = koMatches,
                    teamNames = teamNames,
                    courtCount = latestT.courtCount
                )
                if (nextMatches.isNotEmpty()) {
                    val semiMatches = koMatches.filter { it.tournamentRound?.contains("Halbfinale") == true }
                    val quarterMatches = koMatches.filter { it.tournamentRound?.contains("Viertelfinale") == true }
                    val nextSpecs = nextMatches.mapIndexed { idx, m ->
                        val baseSm = TournamentEngine.toSpecMatch(m, null)
                        when {
                            m.tournamentRound?.startsWith("Finale") == true && semiMatches.size == 2 -> baseSm.copy(
                                sourceA = MatchSource.WinnerOf(semiMatches[0].id),
                                sourceB = MatchSource.WinnerOf(semiMatches[1].id),
                                dependsOnMatchIds = listOf(semiMatches[0].id, semiMatches[1].id)
                            )
                            m.tournamentRound == "Spiel um Platz 3" && semiMatches.size == 2 -> baseSm.copy(
                                sourceA = MatchSource.LoserOf(semiMatches[0].id),
                                sourceB = MatchSource.LoserOf(semiMatches[1].id),
                                dependsOnMatchIds = listOf(semiMatches[0].id, semiMatches[1].id)
                            )
                            m.tournamentRound?.contains("Halbfinale") == true && quarterMatches.size == 4 && idx < 2 -> {
                                val depA = quarterMatches[idx * 2].id
                                val depB = quarterMatches[idx * 2 + 1].id
                                baseSm.copy(
                                    sourceA = MatchSource.WinnerOf(depA),
                                    sourceB = MatchSource.WinnerOf(depB),
                                    dependsOnMatchIds = listOf(depA, depB)
                                )
                            }
                            else -> baseSm
                        }
                    }
                    repository.syncTournamentAndMatches(
                        tournament = latestT.copy(specMatches = latestT.specMatches + nextSpecs),
                        matchesToUpsert = nextMatches
                    )
                    syncTournamentSpecStateLocked(
                        tournamentId = latestT.id,
                        newAuditEntry = AuditEntry(System.currentTimeMillis(), latestT.id, "Nächste K.-o.-Runde erzeugt (${nextMatches.size} Spiele)", "K.-o.-Runde"),
                        rescheduleAllUnlocked = true
                    )
                }
            }
        }
    }

    fun generateNextSwissRound(tournament: Tournament) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                val latestT = repository.getTournament(tournament.id) ?: return@withLock
                val allMatches = repository.getMatchesByTournament(latestT.id).first()
                val specTeams = buildSpecTeamsForTournament(latestT)
                val teamNames = specTeams.associate { it.id to it.name }
                val (seededOrder, _) = SpecSeedingAndGroups.buildSeedOrder(specTeams, latestT.specConfig.seed, latestT.specConfig.manualSeedOrder)

                val completedRounds = allMatches.mapNotNull { it.tournamentRound }
                    .mapNotNull { r ->
                        if (r.startsWith("Swiss Runde ")) r.removePrefix("Swiss Runde ").trim().toIntOrNull() else null
                    }.maxOrNull() ?: 1
                val nextRoundNum = completedRounds + 1

                pushTournamentUndoSnapshot(latestT)
                val swissRes = SpecRoundRobinAndSwiss.generateSwissRound(
                    teams = specTeams,
                    seededOrder = seededOrder,
                    previousMatches = latestT.specMatches,
                    roundNumber = nextRoundNum,
                    config = latestT.specConfig,
                    idPrefix = "swiss_${latestT.id.takeLast(6)}"
                )

                val newDbMatches = swissRes.matches.mapNotNull { sm ->
                    toPlayableRoomMatch(sm, latestT.id, latestT.matchConfig, teamNames)
                }

                repository.syncTournamentAndMatches(
                    tournament = latestT.copy(specMatches = latestT.specMatches + swissRes.matches),
                    matchesToUpsert = newDbMatches
                )
                syncTournamentSpecStateLocked(
                    tournamentId = latestT.id,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), latestT.id, "Swiss Runde $nextRoundNum gepaart" + (if (swissRes.byeTeamId != null) " (Freilos: ${teamNames[swissRes.byeTeamId]})" else ""), "Swiss"),
                    rescheduleAllUnlocked = true
                )
                swissRes.warnings.firstOrNull()?.let { _tournamentFeedbackMessage.value = it.message }
            }
        }
    }

    fun importTournamentFromBackupJson(rawJson: String) {
        val outcome = SpecExportAndPersistence.importBackupJsonStrict(rawJson)
        if (!outcome.success || outcome.config == null) {
            _tournamentFeedbackMessage.value = outcome.errorMessage ?: "Backup konnte nicht geladen werden."
            return
        }
        viewModelScope.launch {
            tournamentMutex.withLock {
                val newId = "tournament_${UUID.randomUUID()}"
                val mode = try { TournamentMode.valueOf(outcome.modeName) } catch (_: Exception) { TournamentMode.GROUPS_KO }
                val manualTeams = outcome.teams.mapIndexed { idx, st ->
                    st.toManualTeam().copy(registrationOrder = idx)
                }
                val teamNames = manualTeams.associate { it.id to it.name }
                val matchCfg = MatchConfig(
                    fieldFormatA = FieldFormat.FORMAT_4V4,
                    fieldFormatB = FieldFormat.FORMAT_4V4,
                    setsMode = when (outcome.config.setsToWin) {
                        1 -> SetsMode.ONE_SET
                        3 -> SetsMode.BEST_OF_5
                        else -> SetsMode.BEST_OF_3
                    },
                    pointsPerSet = outcome.config.pointsPerSet,
                    winByTwo = true
                )
                val prefix = "${newId.takeLast(6)}_"
                val mappedSpecMatches = outcome.specMatches.map { sm ->
                    sm.copy(
                        id = "$prefix${sm.id}",
                        dependsOnMatchIds = sm.dependsOnMatchIds.map { "$prefix$it" },
                        sourceA = when (val s = sm.sourceA) {
                            is MatchSource.WinnerOf -> MatchSource.WinnerOf("$prefix${s.matchId}")
                            is MatchSource.LoserOf -> MatchSource.LoserOf("$prefix${s.matchId}")
                            else -> s
                        },
                        sourceB = when (val s = sm.sourceB) {
                            is MatchSource.WinnerOf -> MatchSource.WinnerOf("$prefix${s.matchId}")
                            is MatchSource.LoserOf -> MatchSource.LoserOf("$prefix${s.matchId}")
                            else -> s
                        }
                    )
                }
                val tournament = Tournament(
                    id = newId,
                    name = outcome.tournamentName,
                    sportType = SportType.ANY,
                    fieldFormat = FieldFormat.FORMAT_4V4,
                    matchConfig = matchCfg,
                    mode = mode,
                    status = TournamentStatus.IN_PROGRESS,
                    groupsCount = outcome.groupAssignments.size.coerceAtLeast(1),
                    teamIds = manualTeams.map { it.id },
                    groupAssignments = outcome.groupAssignments,
                    manualTeams = manualTeams,
                    courtCount = outcome.courtCount,
                    specConfig = outcome.config,
                    specMatches = mappedSpecMatches
                )
                val dbMatches = mappedSpecMatches.mapNotNull { sm ->
                    toPlayableRoomMatch(sm, newId, matchCfg, teamNames)
                }
                repository.syncTournamentAndMatches(tournament, dbMatches)
                syncTournamentSpecStateLocked(
                    tournamentId = newId,
                    newAuditEntry = AuditEntry(System.currentTimeMillis(), newId, "Backup importiert (${manualTeams.size} Teams)", "Import"),
                    rescheduleAllUnlocked = false
                )
                _selectedTournamentId.value = newId
                _tournamentFeedbackMessage.value = "Backup '${tournament.name}' erfolgreich importiert (${manualTeams.size} Teams, ${dbMatches.size} Spiele)."
            }
        }
    }

    fun runTournamentSelfTests(tournament: Tournament? = null) {
        if (_selfTestRunning.value) return
        viewModelScope.launch {
            _selfTestRunning.value = true
            _selfTestProgress.value = 0.05f to "Starte Golden & Property Tests..."
            val report = withContext(Dispatchers.Default) {
                SpecSelfTestRunner.runFullSuite(
                    currentTournament = tournament,
                    propertyRuns = 1000,
                    onProgress = { frac, msg ->
                        _selfTestProgress.value = frac to msg
                    }
                )
            }
            _selfTestReport.value = report
            _selfTestRunning.value = false
        }
    }

    fun deleteTournament(tournamentId: String) {
        viewModelScope.launch {
            tournamentMutex.withLock {
                repository.deleteTournament(tournamentId)
                _tournamentWarningsById.value = _tournamentWarningsById.value - tournamentId
                if (_selectedTournamentId.value == tournamentId) {
                    _selectedTournamentId.value = null
                }
            }
        }
    }

    // =========================================================================
    // TAKTIKBOARD ACTIONS
    // =========================================================================

    fun toggleCourtType() {
        _isBeachCourt.value = !_isBeachCourt.value
    }

    fun addTacticStroke(stroke: TacticStroke) {
        _tacticStrokes.value = _tacticStrokes.value + stroke
        _tacticRedoStack.value = emptyList()
    }

    fun eraseStrokesNear(x: Float, y: Float, radius: Float) {
        val current = _tacticStrokes.value
        val remaining = current.filter { stroke ->
            stroke.points.none { pt ->
                val dx = pt.x - x
                val dy = pt.y - y
                (dx * dx + dy * dy) <= (radius * radius)
            }
        }
        if (remaining.size != current.size) {
            _tacticStrokes.value = remaining
            _tacticRedoStack.value = emptyList()
        }
    }

    fun undoTactic() {
        val current = _tacticStrokes.value
        if (current.isNotEmpty()) {
            val last = current.last()
            _tacticStrokes.value = current.dropLast(1)
            _tacticRedoStack.value = _tacticRedoStack.value + last
        }
    }

    fun redoTactic() {
        val redo = _tacticRedoStack.value
        if (redo.isNotEmpty()) {
            val toRestore = redo.last()
            _tacticRedoStack.value = redo.dropLast(1)
            _tacticStrokes.value = _tacticStrokes.value + toRestore
        }
    }

    fun clearTacticBoard() {
        _tacticStrokes.value = emptyList()
        _tacticRedoStack.value = emptyList()
    }

    // =========================================================================
    // COIN TOSS ACTIONS
    // =========================================================================

    fun flipCoin(teamAName: String?, teamBName: String?, headsTeam: String?) {
        val result = CoinTossEngine.flipCoin(
            teamAName = teamAName,
            teamBName = teamBName,
            headsTeam = headsTeam
        )
        _coinTossResult.value = result
    }

    fun resetCoinToss() {
        _coinTossResult.value = null
    }
}

enum class NavigationTab(val label: String) {
    TEAM("Team"),
    SCORE("Score"),
    ROTATION("Rotation"),
    TURNIER("Turnier"),
    TAKTIK("Taktik"),
    COIN_TOSS("Coin Toss")
}
