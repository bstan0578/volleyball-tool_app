package com.example.domain.engine

import com.example.data.model.*
import com.example.domain.tournament.*
import java.util.UUID

object TournamentEngine {

    /**
     * Converts a Match (Room/UI) + optional SpecMatch metadata to SpecMatch for pure SPEC calculations.
     */
    fun toSpecMatch(m: Match, specMeta: SpecMatch? = null): SpecMatch {
        val sets = if (m.completedSets.isNotEmpty()) {
            m.completedSets.map { SetScore(it.scoreA, it.scoreB) }
        } else if (m.status == MatchStatus.FINISHED && (m.scoreA > 0 || m.scoreB > 0)) {
            listOf(SetScore(m.scoreA, m.scoreB))
        } else {
            emptyList()
        }
        val status = when {
            specMeta?.isForfeit == true -> SpecMatchStatus.FORFEIT
            m.status == MatchStatus.FINISHED -> SpecMatchStatus.FINAL
            m.status == MatchStatus.RUNNING && (m.scoreA > 0 || m.scoreB > 0 || m.completedSets.isNotEmpty() || m.setStartTime != null) -> SpecMatchStatus.RUNNING
            else -> SpecMatchStatus.SCHEDULED
        }
        val winnerSide = if (status == SpecMatchStatus.FINAL || status == SpecMatchStatus.FORFEIT) {
            if (specMeta?.winnerSide != null) {
                specMeta.winnerSide
            } else {
                val wA = sets.count { it.a > it.b }
                val wB = sets.count { it.b > it.a }
                when {
                    wA > wB -> TeamSide.TEAM_A
                    wB > wA -> TeamSide.TEAM_B
                    else -> null
                }
            }
        } else null

        return SpecMatch(
            id = m.id,
            roundLabel = m.tournamentRound ?: specMeta?.roundLabel ?: "",
            groupId = m.groupId,
            isKo = m.groupId == "KO" || specMeta?.isKo == true,
            sourceA = specMeta?.sourceA ?: MatchSource.DirectTeam(m.teamAId),
            sourceB = specMeta?.sourceB ?: MatchSource.DirectTeam(m.teamBId),
            teamAId = m.teamAId.ifBlank { null },
            teamBId = m.teamBId.ifBlank { null },
            sets = sets,
            status = status,
            winnerSide = winnerSide,
            isForfeit = specMeta?.isForfeit ?: false,
            isByeMatch = specMeta?.isByeMatch ?: false,
            slotIndex = specMeta?.slotIndex,
            courtNumber = m.courtNumber ?: specMeta?.courtNumber,
            refereeTeamIds = specMeta?.refereeTeamIds ?: emptyList(),
            refereeManualLocked = specMeta?.refereeManualLocked ?: false,
            dependsOnMatchIds = specMeta?.dependsOnMatchIds ?: emptyList(),
            phaseRound = specMeta?.phaseRound ?: 1,
            isAutoWithdrawalForfeit = specMeta?.isAutoWithdrawalForfeit ?: false
        )
    }

    /**
     * Computes standings using SPEC v1.0 deterministic integer-only cross-multiplication engine
     * (MP -> Wins -> SetQuot -> BallQuot -> recursive H2H mini-table -> manual/deterministic tie-break).
     */
    fun calculateStandings(
        tournamentId: String,
        groupId: String,
        teamIds: List<String>,
        teamNames: Map<String, String>,
        matches: List<Match>,
        pointsForWin: Int = 2,
        pointsForDraw: Int = 1,
        pointsForLoss: Int = 0,
        specConfig: SpecTournamentConfig? = null,
        manualTeams: List<ManualTournamentTeam> = emptyList(),
        extraSpecMatches: List<SpecMatch> = emptyList()
    ): List<Standing> {
        val cfg = specConfig ?: SpecTournamentConfig(
            scoringSystem = if (pointsForWin >= 3) ScoringSystem.DVV3 else ScoringSystem.CLASSIC2
        )
        val manualById = manualTeams.associateBy { it.id }
        val specTeams = teamIds.mapIndexed { idx, tid ->
            val mt = manualById[tid]
            SpecTeam(
                id = tid,
                name = teamNames[tid] ?: mt?.name ?: "Team",
                shortName = mt?.shortName ?: (teamNames[tid] ?: "TM").take(4).uppercase(),
                clubId = mt?.club ?: "",
                skillLevel = mt?.skillLevel ?: 50,
                present = mt?.present ?: true,
                withdrawn = mt?.withdrawn ?: false,
                registrationOrder = mt?.registrationOrder ?: idx
            )
        }
        val groupMatches = matches.filter {
            it.tournamentId == tournamentId && (groupId.isEmpty() || it.groupId == groupId)
        }
        val specById = extraSpecMatches.associateBy { it.id }
        val convertedMatches = groupMatches.map { toSpecMatch(it, specById[it.id]) }
        val byeRecords = extraSpecMatches.filter {
            it.isByeMatch && (groupId.isEmpty() || it.groupId == groupId) &&
                convertedMatches.none { cm -> cm.id == it.id }
        }

        val rows = SpecScoring.calculateStandings(
            teams = specTeams,
            matches = convertedMatches + byeRecords,
            config = cfg,
            groupId = groupId
        )
        return rows.map { r ->
            Standing(
                tournamentId = tournamentId,
                groupId = groupId,
                teamId = r.teamId,
                teamName = r.teamName,
                matchesPlayed = r.matchesPlayed,
                wins = r.wins,
                draws = r.draws,
                losses = r.losses,
                points = r.matchPoints,
                setsWon = r.setsWon,
                setsLost = r.setsLost,
                pointsFor = r.pointsFor,
                pointsAgainst = r.pointsAgainst,
                rank = r.rank
            )
        }
    }

    /**
     * Computes SPEC standing rows directly (preserving decidedBy, decisionRequired, buchholz, isProvisional).
     */
    fun calculateSpecStandings(
        tournament: Tournament,
        groupId: String,
        teamIds: List<String>,
        teamNames: Map<String, String>,
        matches: List<Match>
    ): List<SpecStandingRow> {
        val manualById = tournament.manualTeams.associateBy { it.id }
        val specTeams = teamIds.mapIndexed { idx, tid ->
            val mt = manualById[tid]
            SpecTeam(
                id = tid,
                name = teamNames[tid] ?: mt?.name ?: "Team",
                shortName = mt?.shortName ?: (teamNames[tid] ?: "TM").take(4).uppercase(),
                clubId = mt?.club ?: "",
                skillLevel = mt?.skillLevel ?: 50,
                present = mt?.present ?: true,
                withdrawn = mt?.withdrawn ?: false,
                registrationOrder = mt?.registrationOrder ?: idx
            )
        }
        val specById = tournament.specMatches.associateBy { it.id }
        val converted = matches
            .filter { it.tournamentId == tournament.id && (groupId.isEmpty() || it.groupId == groupId) }
            .map { toSpecMatch(it, specById[it.id]) }
        val byes = tournament.specMatches.filter {
            it.isByeMatch && (groupId.isEmpty() || it.groupId == groupId) && converted.none { cm -> cm.id == it.id }
        }
        return SpecScoring.calculateStandings(
            teams = specTeams,
            matches = converted + byes,
            config = tournament.specConfig,
            groupId = groupId
        )
    }

    /**
     * Computes a unified overall ranking across ALL teams and ALL finished matches in a tournament.
     */
    fun calculateOverallStandings(
        tournament: Tournament,
        teamNames: Map<String, String>,
        matches: List<Match>
    ): List<Standing> {
        return calculateStandings(
            tournamentId = tournament.id,
            groupId = "",
            teamIds = tournament.teamIds,
            teamNames = teamNames,
            matches = matches,
            pointsForWin = tournament.pointsForWin,
            pointsForDraw = tournament.pointsForDraw,
            pointsForLoss = tournament.pointsForLoss,
            specConfig = tournament.specConfig,
            manualTeams = tournament.manualTeams,
            extraSpecMatches = tournament.specMatches
        )
    }

    /**
     * Distributes teams into groups using SPEC T-02/T-03/T-04 (deterministic seeding, exact Serpentine,
     * and same-row club separation swap pass).
     */
    fun distributeTeamsToGroups(
        teamIds: List<String>,
        manualTeams: List<ManualTournamentTeam>,
        groupsCount: Int,
        seedingMode: SeedingMode,
        seed: Int = 424242
    ): Map<String, List<String>> {
        val g = groupsCount.coerceAtLeast(1)
        val groupNames = List(g) { ('A' + it).toString() }
        if (teamIds.isEmpty()) return groupNames.associateWith { emptyList() }

        val manualById = manualTeams.associateBy { it.id }
        val specTeams = teamIds.mapIndexed { idx, tid ->
            val mt = manualById[tid]
            SpecTeam(
                id = tid,
                name = mt?.name ?: "Team ${idx + 1}",
                shortName = mt?.shortName ?: "TM",
                clubId = mt?.club ?: "",
                skillLevel = mt?.skillLevel ?: 50,
                present = mt?.present ?: true,
                withdrawn = mt?.withdrawn ?: false,
                registrationOrder = mt?.registrationOrder ?: idx
            )
        }

        val orderedTeams = when (seedingMode) {
            SeedingMode.RANDOM_DRAW -> Mulberry32(seed).shuffled(specTeams)
            else -> SpecSeedingAndGroups.buildSeedOrder(specTeams, seed).first
        }
        val separateClubs = seedingMode == SeedingMode.CLUB_SEPARATION ||
            seedingMode == SeedingMode.CROSS_SEEDING ||
            seedingMode == SeedingMode.TOP_SEEDS_SEPARATED

        val (distributed, _) = SpecSeedingAndGroups.distributeToGroups(
            seededTeams = orderedTeams,
            groupsCount = g,
            separateClubs = separateClubs
        )
        return distributed.mapValues { (_, list) -> list.map { it.id } }
    }

    /**
     * Generates Round Robin matches using SPEC F-01 exact Kreisverfahren + multi-court scheduling.
     */
    fun generateRoundRobinMatches(
        tournamentId: String,
        groupId: String,
        teams: List<Team>,
        config: MatchConfig,
        courtCount: Int = 1,
        doubleRoundRobin: Boolean = false
    ): List<Match> {
        if (teams.size < 2) return emptyList()
        val teamById = teams.associateBy { it.id }
        val specMatches = SpecRoundRobinAndSwiss.generateRoundRobin(
            ids = teams.map { it.id },
            groupId = groupId,
            doubleRoundRobin = doubleRoundRobin,
            idPrefix = "rr_${UUID.randomUUID().toString().take(8)}"
        )
        val specTeams = teams.mapIndexed { idx, t ->
            SpecTeam(id = t.id, name = t.name, registrationOrder = idx)
        }
        val sched = SpecSchedulerAndReferees.scheduleMatches(
            matches = specMatches,
            teams = specTeams,
            courtCount = courtCount,
            config = SpecTournamentConfig()
        )
        return sched.scheduledMatches.mapNotNull { sm ->
            val a = sm.teamAId?.let { teamById[it] } ?: return@mapNotNull null
            val b = sm.teamBId?.let { teamById[it] } ?: return@mapNotNull null
            Match(
                id = sm.id,
                teamAId = a.id,
                teamBId = b.id,
                teamAName = a.name,
                teamBName = b.name,
                matchConfig = config,
                status = MatchStatus.DRAFT,
                tournamentId = tournamentId,
                groupId = groupId,
                tournamentRound = sm.roundLabel,
                courtNumber = sm.courtNumber ?: 1
            )
        }
    }

    /**
     * Generates a new round in a Swiss System tournament using SPEC F-07 Branch & Bound + Bye rule.
     */
    fun generateSwissRoundMatches(
        tournament: Tournament,
        roundNumber: Int,
        standings: List<Standing>,
        previousMatches: List<Match>,
        courtCount: Int = 1
    ): List<Match> {
        val teamNames = mutableMapOf<String, String>()
        standings.forEach { teamNames[it.teamId] = it.teamName }
        tournament.manualTeams.forEach { teamNames[it.id] = it.name }
        val manualById = tournament.manualTeams.associateBy { it.id }

        val specTeams = standings.mapIndexed { idx, s ->
            val mt = manualById[s.teamId]
            SpecTeam(
                id = s.teamId,
                name = teamNames[s.teamId] ?: s.teamName,
                clubId = mt?.club ?: "",
                skillLevel = mt?.skillLevel ?: (100 - idx * 5).coerceIn(1, 100),
                present = mt?.present ?: true,
                withdrawn = mt?.withdrawn ?: false,
                registrationOrder = mt?.registrationOrder ?: idx
            )
        }
        val (seededOrder, _) = SpecSeedingAndGroups.buildSeedOrder(specTeams, tournament.specConfig.seed)
        val specPrev = previousMatches.map { toSpecMatch(it) } +
            tournament.specMatches.filter { it.isByeMatch }
        val swissRes = SpecRoundRobinAndSwiss.generateSwissRound(
            teams = specTeams,
            seededOrder = seededOrder,
            previousMatches = specPrev,
            roundNumber = roundNumber,
            config = tournament.specConfig,
            idPrefix = "swiss_${UUID.randomUUID().toString().take(8)}"
        )
        val courtMatches = swissRes.matches.filter { !it.isByeMatch }
        val sched = SpecSchedulerAndReferees.scheduleMatches(
            matches = courtMatches,
            teams = specTeams,
            courtCount = courtCount,
            config = tournament.specConfig
        )
        return sched.scheduledMatches.mapNotNull { sm ->
            val idA = sm.teamAId ?: return@mapNotNull null
            val idB = sm.teamBId ?: return@mapNotNull null
            Match(
                id = sm.id,
                teamAId = idA,
                teamBId = idB,
                teamAName = teamNames[idA] ?: idA,
                teamBName = teamNames[idB] ?: idB,
                matchConfig = tournament.matchConfig,
                status = MatchStatus.DRAFT,
                tournamentId = tournament.id,
                groupId = "SWISS",
                tournamentRound = "Swiss Runde $roundNumber",
                courtNumber = sm.courtNumber ?: 1
            )
        }
    }

    /**
     * Checks if all group matches in a tournament are FINISHED.
     */
    fun areGroupMatchesCompleted(groupMatches: List<Match>): Boolean {
        if (groupMatches.isEmpty()) return false
        return groupMatches.all { it.status == MatchStatus.FINISHED }
    }

    /**
     * Returns a federation-coach recommendation for tournament mode based on team count.
     */
    fun coachRecommendedMode(teamCount: Int): Pair<TournamentMode, String> {
        return when {
            teamCount <= 1 -> TournamentMode.ROUND_ROBIN to "Mindestens 2 Teams anlegen."
            teamCount <= 6 -> TournamentMode.ROUND_ROBIN to "Bei $teamCount Teams ideal: Jeder gegen Jeden (kurze Wartezeiten, fairer Vergleich)."
            teamCount <= 12 -> TournamentMode.GROUPS_KO to "Bei $teamCount Teams empfohlen: 2 Gruppen (Vorrunde nur in der Gruppe) + Überkreuz-K.-o."
            teamCount <= 20 -> TournamentMode.GROUPS_KO to "Bei $teamCount Teams empfohlen: 4 Gruppen + Viertel-/Halbfinale + Finale."
            else -> TournamentMode.SWISS_SYSTEM to "Bei $teamCount Teams empfohlen: Schweizer System oder Direktes K.-o. mit Freilosen."
        }
    }

    /**
     * Standard seeded bracket slot order for a power-of-2 bracket (0-based seed indices).
     * Guarantees seed #1 and #2 are on opposite halves and can only meet in the Final.
     */
    fun buildBracketSeedOrder(bracketSize: Int): List<Int> {
        return SpecBracket.bracketOrder(bracketSize.coerceAtLeast(1)).map { it - 1 }
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 2
        while (p < n) p *= 2
        return p
    }

    private fun koRoundTitle(bracketSize: Int, matchIndex: Int, totalInRound: Int): String {
        val base = when (bracketSize) {
            2 -> "Finale (Platz 1)"
            4 -> "Halbfinale"
            8 -> "Viertelfinale"
            16 -> "Achtelfinale"
            32 -> "Sechzehntelfinale"
            else -> "K.-o. Runde ($bracketSize)"
        }
        return if (bracketSize <= 2 || totalInRound <= 1) base else "$base ${matchIndex + 1}"
    }

    private fun matchWinnerAndLoser(match: Match): Pair<Pair<String, String>, Pair<String, String>> {
        val setsA = match.completedSets.count { it.winner == TeamSide.TEAM_A }
        val setsB = match.completedSets.count { it.winner == TeamSide.TEAM_B }
        val aWon = if (setsA != setsB) setsA > setsB else match.scoreA >= match.scoreB
        return if (aWon) {
            Pair(match.teamAId to match.teamAName, match.teamBId to match.teamBName)
        } else {
            Pair(match.teamBId to match.teamBName, match.teamAId to match.teamAName)
        }
    }

    /**
     * Walks a seeded Single Elimination bracket from initial seeds + existing KO matches
     * and generates the next playable round (skipping automatic Freilos/BYE advances cleanly).
     */
    fun generateNextSingleEliminationMatches(
        tournament: Tournament,
        seededTeams: List<Standing>,
        existingKoMatches: List<Match>,
        courtCount: Int = 1
    ): List<Match> {
        if (seededTeams.size < 2) return emptyList()
        if (existingKoMatches.any { it.tournamentRound?.startsWith("Finale") == true }) {
            return emptyList()
        }

        val bracketSize = nextPowerOfTwo(seededTeams.size)
        val slotOrder = buildBracketSeedOrder(bracketSize)
        var nodes: List<Standing?> = slotOrder.map { seedIdx -> seededTeams.getOrNull(seedIdx) }
        var currentBracketSize = bracketSize

        while (nodes.size >= 2) {
            val matchesToCreate = mutableListOf<Match>()
            val nextNodes = mutableListOf<Standing?>()
            val roundLosers = mutableListOf<Standing>()
            var hasPendingMatch = false
            val pairCount = nodes.size / 2

            for (i in 0 until pairCount) {
                val left = nodes[i * 2]
                val right = nodes[i * 2 + 1]
                when {
                    left == null && right == null -> nextNodes.add(null)
                    left != null && right == null -> nextNodes.add(left) // Freilos (BYE)
                    left == null && right != null -> nextNodes.add(right) // Freilos (BYE)
                    left != null && right != null -> {
                        val existing = existingKoMatches.firstOrNull { m ->
                            (m.teamAId == left.teamId && m.teamBId == right.teamId) ||
                                (m.teamAId == right.teamId && m.teamBId == left.teamId)
                        }
                        if (existing == null) {
                            val court = if (courtCount > 1) (matchesToCreate.size % courtCount) + 1 else 1
                            matchesToCreate.add(
                                createKoMatch(
                                    tournament = tournament,
                                    teamA = left,
                                    teamB = right,
                                    roundLabel = koRoundTitle(currentBracketSize, matchesToCreate.size, pairCount),
                                    court = court
                                )
                            )
                        } else if (existing.status != MatchStatus.FINISHED) {
                            hasPendingMatch = true
                        } else {
                            val (winnerPair, loserPair) = matchWinnerAndLoser(existing)
                            nextNodes.add(
                                Standing(
                                    tournamentId = tournament.id,
                                    groupId = "KO",
                                    teamId = winnerPair.first,
                                    teamName = winnerPair.second
                                )
                            )
                            roundLosers.add(
                                Standing(
                                    tournamentId = tournament.id,
                                    groupId = "KO",
                                    teamId = loserPair.first,
                                    teamName = loserPair.second
                                )
                            )
                        }
                    }
                }
            }

            if (matchesToCreate.isNotEmpty()) {
                // If we just entered Finale (currentBracketSize == 2), check if previous round (Halbfinale) produced 2 losers for 3rd-place match
                if (currentBracketSize == 2 && existingKoMatches.none { it.tournamentRound == "Spiel um Platz 3" }) {
                    val semiMatches = existingKoMatches.filter { it.tournamentRound?.contains("Halbfinale") == true }
                    if (semiMatches.size == 2 && semiMatches.all { it.status == MatchStatus.FINISHED }) {
                        val l1 = matchWinnerAndLoser(semiMatches[0]).second
                        val l2 = matchWinnerAndLoser(semiMatches[1]).second
                        matchesToCreate.add(
                            Match(
                                id = "third_${UUID.randomUUID()}",
                                teamAId = l1.first,
                                teamBId = l2.first,
                                teamAName = l1.second,
                                teamBName = l2.second,
                                matchConfig = tournament.matchConfig,
                                status = MatchStatus.DRAFT,
                                tournamentId = tournament.id,
                                groupId = "KO",
                                tournamentRound = "Spiel um Platz 3",
                                courtNumber = if (courtCount > 1) 2 else 1
                            )
                        )
                    }
                }
                return matchesToCreate
            }
            if (hasPendingMatch) {
                return emptyList()
            }
            nodes = nextNodes
            currentBracketSize /= 2
        }

        return emptyList()
    }

    /**
     * Generates KO bracket matches.
     * Prerequisite for GROUPS_KO: All group matches must be FINISHED!
     */
    fun generateKoMatches(
        tournament: Tournament,
        groupStandings: Map<String, List<Standing>>,
        allGroupMatches: List<Match>,
        seedingMode: SeedingMode,
        courtCount: Int = 1
    ): List<Match> {
        if (tournament.mode == TournamentMode.GROUPS_KO) {
            require(areGroupMatchesCompleted(allGroupMatches)) {
                "K.-o.-Paarungen dürfen erst erzeugt werden, wenn alle Gruppenspiele FINISHED sind."
            }
        }

        if (tournament.mode == TournamentMode.SINGLE_ELIMINATION) {
            val rawSeeds = groupStandings.values.flatten()
            val ordered = if (seedingMode == SeedingMode.RANDOM_DRAW) rawSeeds.shuffled() else rawSeeds.sortedBy { it.rank }
            return generateNextSingleEliminationMatches(
                tournament = tournament,
                seededTeams = ordered,
                existingKoMatches = emptyList(),
                courtCount = courtCount
            )
        }

        val groups = groupStandings.keys.sorted()

        if (groups.size == 2) {
            val groupA = groupStandings[groups[0]] ?: emptyList()
            val groupB = groupStandings[groups[1]] ?: emptyList()

            val a1 = groupA.getOrNull(0)
            val a2 = groupA.getOrNull(1)
            val b1 = groupB.getOrNull(0)
            val b2 = groupB.getOrNull(1)

            if (a1 != null && a2 != null && b1 != null && b2 != null) {
                return if (seedingMode == SeedingMode.CROSS_SEEDING || seedingMode == SeedingMode.TOP_SEEDS_SEPARATED) {
                    listOf(
                        createKoMatch(tournament, a1, b2, "Halbfinale 1 (A1 vs B2)", court = 1),
                        createKoMatch(tournament, b1, a2, "Halbfinale 2 (B1 vs A2)", court = if (courtCount > 1) 2 else 1)
                    )
                } else {
                    val four = listOf(a1, a2, b1, b2).shuffled()
                    listOf(
                        createKoMatch(tournament, four[0], four[1], "Halbfinale 1 (Los)", court = 1),
                        createKoMatch(tournament, four[2], four[3], "Halbfinale 2 (Los)", court = if (courtCount > 1) 2 else 1)
                    )
                }
            } else if (a1 != null && b1 != null) {
                return listOf(createKoMatch(tournament, a1, b1, "Finale (Platz 1)", court = 1))
            }
        }

        if (groups.size == 4) {
            val a1 = groupStandings[groups[0]]?.getOrNull(0)
            val a2 = groupStandings[groups[0]]?.getOrNull(1)
            val b1 = groupStandings[groups[1]]?.getOrNull(0)
            val b2 = groupStandings[groups[1]]?.getOrNull(1)
            val c1 = groupStandings[groups[2]]?.getOrNull(0)
            val c2 = groupStandings[groups[2]]?.getOrNull(1)
            val d1 = groupStandings[groups[3]]?.getOrNull(0)
            val d2 = groupStandings[groups[3]]?.getOrNull(1)

            if (a1 != null && a2 != null && b1 != null && b2 != null &&
                c1 != null && c2 != null && d1 != null && d2 != null
            ) {
                val pairs = if (seedingMode == SeedingMode.RANDOM_DRAW) {
                    val eight = listOf(a1, a2, b1, b2, c1, c2, d1, d2).shuffled()
                    listOf(
                        Triple(eight[0], eight[1], "Viertelfinale 1 (Los)"),
                        Triple(eight[2], eight[3], "Viertelfinale 2 (Los)"),
                        Triple(eight[4], eight[5], "Viertelfinale 3 (Los)"),
                        Triple(eight[6], eight[7], "Viertelfinale 4 (Los)")
                    )
                } else {
                    // Federation standard: VF1 Winner vs VF2 Winner, VF3 Winner vs VF4 Winner -> Group A/B teams separated until Final
                    listOf(
                        Triple(a1, b2, "Viertelfinale 1 (A1 vs B2)"),
                        Triple(c1, d2, "Viertelfinale 2 (C1 vs D2)"),
                        Triple(b1, a2, "Viertelfinale 3 (B1 vs A2)"),
                        Triple(d1, c2, "Viertelfinale 4 (D1 vs C2)")
                    )
                }
                return pairs.mapIndexed { idx, (t1, t2, label) ->
                    val court = if (courtCount > 1) (idx % courtCount) + 1 else 1
                    createKoMatch(tournament, t1, t2, label, court)
                }
            }
        }

        // General 3 groups or other group counts: take Top 2 per group (+ best 3rds if 8-team QF fits)
        val qualifiedTeams = mutableListOf<Standing>()
        val thirdPlaces = mutableListOf<Standing>()
        for (grp in groups) {
            val standings = groupStandings[grp] ?: emptyList()
            if (standings.isNotEmpty()) qualifiedTeams.add(standings[0])
            if (standings.size > 1) qualifiedTeams.add(standings[1])
            if (standings.size > 2) thirdPlaces.add(standings[2])
        }

        if (groups.size == 3 && qualifiedTeams.size == 6 && thirdPlaces.size >= 2) {
            val comparator = compareByDescending<Standing> { it.points }
                .thenByDescending { it.wins }
                .thenByDescending { it.setDiff }
                .thenByDescending { it.pointsDiff }
            val bestThirds = thirdPlaces.sortedWith(comparator).take(2)
            val eight = (qualifiedTeams.filter { it.rank == 1 }.sortedWith(comparator) +
                qualifiedTeams.filter { it.rank == 2 }.sortedWith(comparator) +
                bestThirds)
            val matches = mutableListOf<Match>()
            val pairings = if (seedingMode == SeedingMode.RANDOM_DRAW) {
                val s = eight.shuffled()
                listOf(s[0] to s[1], s[2] to s[3], s[4] to s[5], s[6] to s[7])
            } else {
                listOf(eight[0] to eight[7], eight[3] to eight[4], eight[1] to eight[6], eight[2] to eight[5])
            }
            pairings.forEachIndexed { idx, (tA, tB) ->
                val court = if (courtCount > 1) (idx % courtCount) + 1 else 1
                matches.add(createKoMatch(tournament, tA, tB, "Viertelfinale ${idx + 1}", court))
            }
            return matches
        }

        val matches = mutableListOf<Match>()
        if (seedingMode == SeedingMode.CROSS_SEEDING || seedingMode == SeedingMode.TOP_SEEDS_SEPARATED) {
            val firsts = qualifiedTeams.filter { it.rank == 1 }
            val seconds = qualifiedTeams.filter { it.rank == 2 }
            val count = minOf(firsts.size, seconds.size)
            val roundBase = if (count == 2) "Halbfinale" else "K.-o. Runde"
            for (i in 0 until count) {
                val teamA = firsts[i]
                val teamB = seconds[(count - 1 - i) % seconds.size]
                val court = if (courtCount > 1) (i % courtCount) + 1 else 1
                matches.add(createKoMatch(tournament, teamA, teamB, "$roundBase ${i + 1} (${teamA.teamName} vs ${teamB.teamName})", court))
            }
        } else {
            val shuffled = qualifiedTeams.shuffled()
            val roundBase = if (shuffled.size / 2 == 2) "Halbfinale" else "K.-o. Runde"
            for (i in 0 until (shuffled.size / 2)) {
                val court = if (courtCount > 1) (i % courtCount) + 1 else 1
                matches.add(
                    createKoMatch(
                        tournament,
                        shuffled[i * 2],
                        shuffled[i * 2 + 1],
                        "$roundBase ${i + 1} (Los)",
                        court
                    )
                )
            }
        }

        return matches
    }

    /**
     * Generates placement matches after a group stage in PLACEMENT mode:
     * 1st vs 1st (Platz 1), 2nd vs 2nd (Platz 3), 3rd vs 3rd (Platz 5), etc.
     */
    fun generatePlacementMatches(
        tournament: Tournament,
        groupStandings: Map<String, List<Standing>>,
        allGroupMatches: List<Match>,
        courtCount: Int = 1
    ): List<Match> {
        require(areGroupMatchesCompleted(allGroupMatches)) {
            "Platzierungsspiele dürfen erst erzeugt werden, wenn alle Gruppenspiele FINISHED sind."
        }
        val groups = groupStandings.keys.sorted()
        if (groups.isEmpty()) return emptyList()

        val result = mutableListOf<Match>()
        var matchIndex = 0

        if (groups.size == 2) {
            val gA = groupStandings[groups[0]] ?: emptyList()
            val gB = groupStandings[groups[1]] ?: emptyList()
            val pairs = minOf(gA.size, gB.size)
            for (i in 0 until pairs) {
                val place = i * 2 + 1
                val label = if (place == 1) "Spiel um Platz 1 (Finale)" else "Spiel um Platz $place"
                val court = if (courtCount > 1) (matchIndex % courtCount) + 1 else 1
                result.add(createKoMatch(tournament, gA[i], gB[i], label, court))
                matchIndex++
            }
        } else {
            val maxDepth = groupStandings.values.maxOfOrNull { it.size } ?: 0
            val cmp = compareByDescending<Standing> { it.points }
                .thenByDescending { it.wins }
                .thenByDescending { it.setDiff }
                .thenByDescending { it.pointsDiff }
            var currentPlace = 1
            for (rankIdx in 0 until maxDepth) {
                val tierTeams = groups.mapNotNull { g -> groupStandings[g]?.getOrNull(rankIdx) }.sortedWith(cmp)
                var i = 0
                while (i + 1 < tierTeams.size) {
                    val label = if (currentPlace == 1) "Spiel um Platz 1 (Finale)" else "Spiel um Platz $currentPlace"
                    val court = if (courtCount > 1) (matchIndex % courtCount) + 1 else 1
                    result.add(createKoMatch(tournament, tierTeams[i], tierTeams[i + 1], label, court))
                    currentPlace += 2
                    matchIndex++
                    i += 2
                }
            }
        }
        return result
    }

    private fun createKoMatch(
        tournament: Tournament,
        teamA: Standing,
        teamB: Standing,
        roundLabel: String,
        court: Int = 1
    ): Match {
        return Match(
            id = "ko_${UUID.randomUUID()}",
            teamAId = teamA.teamId,
            teamBId = teamB.teamId,
            teamAName = teamA.teamName,
            teamBName = teamB.teamName,
            matchConfig = tournament.matchConfig,
            status = MatchStatus.DRAFT,
            tournamentId = tournament.id,
            groupId = "KO",
            tournamentRound = roundLabel,
            courtNumber = court
        )
    }

    /**
     * Advances KO stage cleanly:
     * - SINGLE_ELIMINATION: walks bracket tree with Freilose until Finale + Platz 3.
     * - GROUPS_KO: Viertelfinale (4) -> Halbfinale (2) -> Finale (Platz 1) + Spiel um Platz 3.
     */
    fun advanceKoRoundIfReady(
        tournament: Tournament,
        koMatches: List<Match>,
        teamNames: Map<String, String>,
        courtCount: Int = 1
    ): List<Match> {
        if (tournament.mode == TournamentMode.SINGLE_ELIMINATION) {
            val seeded = tournament.teamIds.mapIndexed { idx, tid ->
                Standing(
                    tournamentId = tournament.id,
                    groupId = "KO",
                    teamId = tid,
                    teamName = teamNames[tid] ?: "Team ${idx + 1}",
                    rank = idx + 1
                )
            }
            return generateNextSingleEliminationMatches(
                tournament = tournament,
                seededTeams = seeded,
                existingKoMatches = koMatches,
                courtCount = courtCount
            )
        }

        if (koMatches.any { it.tournamentRound?.startsWith("Finale") == true }) {
            return emptyList()
        }

        val semiMatches = koMatches.filter { it.tournamentRound?.contains("Halbfinale") == true }
        if (semiMatches.isNotEmpty()) {
            return generateFinalsIfReady(tournament, koMatches, courtCount)
        }

        val quarterMatches = koMatches.filter { it.tournamentRound?.contains("Viertelfinale") == true }
        if (quarterMatches.size == 4 && quarterMatches.all { it.status == MatchStatus.FINISHED }) {
            val w = quarterMatches.map { matchWinnerAndLoser(it).first }
            val semi1 = Match(
                id = "semi_${UUID.randomUUID()}",
                teamAId = w[0].first,
                teamBId = w[1].first,
                teamAName = w[0].second,
                teamBName = w[1].second,
                matchConfig = tournament.matchConfig,
                status = MatchStatus.DRAFT,
                tournamentId = tournament.id,
                groupId = "KO",
                tournamentRound = "Halbfinale 1",
                courtNumber = 1
            )
            val semi2 = Match(
                id = "semi_${UUID.randomUUID()}",
                teamAId = w[2].first,
                teamBId = w[3].first,
                teamAName = w[2].second,
                teamBName = w[3].second,
                matchConfig = tournament.matchConfig,
                status = MatchStatus.DRAFT,
                tournamentId = tournament.id,
                groupId = "KO",
                tournamentRound = "Halbfinale 2",
                courtNumber = if (courtCount > 1) 2 else 1
            )
            return listOf(semi1, semi2)
        }

        return emptyList()
    }

    /**
     * Checks if semifinals are completed and generates Final & 3rd place matches.
     */
    fun generateFinalsIfReady(
        tournament: Tournament,
        koMatches: List<Match>,
        courtCount: Int = 1
    ): List<Match> {
        if (koMatches.any { it.tournamentRound?.startsWith("Finale") == true }) return emptyList()
        val semiMatches = koMatches.filter { it.tournamentRound?.contains("Halbfinale") == true }
        if (semiMatches.size == 2 && semiMatches.all { it.status == MatchStatus.FINISHED }) {
            val (winner1, loser1) = matchWinnerAndLoser(semiMatches[0])
            val (winner2, loser2) = matchWinnerAndLoser(semiMatches[1])

            val finals = mutableListOf<Match>()
            finals.add(
                Match(
                    id = "final_${UUID.randomUUID()}",
                    teamAId = winner1.first,
                    teamBId = winner2.first,
                    teamAName = winner1.second,
                    teamBName = winner2.second,
                    matchConfig = tournament.matchConfig,
                    status = MatchStatus.DRAFT,
                    tournamentId = tournament.id,
                    groupId = "KO",
                    tournamentRound = "Finale (Platz 1)",
                    courtNumber = 1
                )
            )
            finals.add(
                Match(
                    id = "third_${UUID.randomUUID()}",
                    teamAId = loser1.first,
                    teamBId = loser2.first,
                    teamAName = loser1.second,
                    teamBName = loser2.second,
                    matchConfig = tournament.matchConfig,
                    status = MatchStatus.DRAFT,
                    tournamentId = tournament.id,
                    groupId = "KO",
                    tournamentRound = "Spiel um Platz 3",
                    courtNumber = if (courtCount > 1) 2 else 1
                )
            )
            return finals
        }
        return emptyList()
    }

    /**
     * Exports tournament schedule and standings as clean formatted text.
     */
    fun exportTournamentScheduleAndStandings(
        tournament: Tournament,
        standingsByGroup: Map<String, List<Standing>>,
        matches: List<Match>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("TURNIER: ${tournament.name}")
        sb.appendLine("Sportart: ${tournament.sportType.label} | Format: ${tournament.fieldFormat.label}")
        sb.appendLine("Modus: ${tournament.mode.label} | Felder: ${tournament.courtCount}")
        if (tournament.location.isNotBlank()) sb.appendLine("Ort: ${tournament.location}")
        if (tournament.date.isNotBlank()) sb.appendLine("Datum: ${tournament.date}")
        sb.appendLine("==================================================")
        sb.appendLine()

        // Standings
        if (standingsByGroup.isNotEmpty()) {
            sb.appendLine("--- TABELLEN & PLATZIERUNGEN ---")
            standingsByGroup.forEach { (grp, list) ->
                sb.appendLine("Gruppe $grp:")
                sb.appendLine(String.format("%-4s %-20s %-4s %-4s %-8s %-6s", "Rang", "Team", "Sp", "Pkt", "Sätze", "Diff"))
                list.forEach { s ->
                    sb.appendLine(
                        String.format(
                            "%-4s %-20s %-4d %-4d %-8s %-6d",
                            "#${s.rank}",
                            s.teamName.take(20),
                            s.matchesPlayed,
                            s.points,
                            "${s.setsWon}:${s.setsLost}",
                            s.pointsDiff
                        )
                    )
                }
                sb.appendLine()
            }
        }

        // Matches
        sb.appendLine("--- SPIELPLAN & ERGEBNISSE ---")
        matches.forEachIndexed { idx, m ->
            val courtStr = if (m.courtNumber != null) "Feld ${m.courtNumber}" else "-"
            val roundStr = m.tournamentRound ?: (if (m.groupId != null) "Gruppe ${m.groupId}" else "Spiel ${idx + 1}")
            val scoreStr = if (m.completedSets.isNotEmpty()) {
                m.completedSets.joinToString(", ") { "${it.scoreA}:${it.scoreB}" }
            } else if (m.status == MatchStatus.FINISHED) {
                "${m.scoreA}:${m.scoreB}"
            } else {
                "ausstehend"
            }
            sb.appendLine("[$courtStr] $roundStr: ${m.teamAName} vs ${m.teamBName} -> $scoreStr (${m.status.label})")
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }

    /**
     * Generates count (default 17) deterministic seeded test tournament teams WITHOUT touching the player pool.
     */
    fun generateRandomTestTournamentTeams(count: Int = 17, seed: Int = 2026): List<ManualTournamentTeam> {
        val teamNames = listOf(
            "Beach Hawks", "Blockbusters", "Netz-Ninjas", "Spike Kings", "Volley Tigers",
            "Thunder Spikes", "Ace Hunters", "Sandstorm 2", "Dig Masters", "Sun & Sand",
            "Sky Jumpers", "Match Point", "Beach Ballers", "Court Crushers", "Sideout",
            "Gold Coast", "Highflyers", "Beach Pirates", "Block Monkeys", "Smash Stars"
        )
        val clubs = listOf("VBC Nord", "Beach Club Süd", "TSV Mitte", "SV West", "DJK Ost", "TV Beach")
        val rng = Mulberry32(seed)
        val list = mutableListOf<ManualTournamentTeam>()

        for (i in 0 until count) {
            val name = if (i < teamNames.size) teamNames[i] else "Team ${i + 1}"
            val skill = rng.nextInt(5) + 1
            val males = rng.nextInt(3) + 1
            val females = rng.nextInt(3) + 1
            val totalAge = (males + females) * (20 + rng.nextInt(15))
            val club = clubs[rng.nextInt(clubs.size)]
            list.add(
                ManualTournamentTeam(
                    id = "mteam_${seed}_$i",
                    name = name,
                    skill = skill,
                    maleCount = males,
                    femaleCount = females,
                    totalAge = totalAge,
                    club = club,
                    note = "Test-Team",
                    shortName = name.take(4).uppercase(),
                    skillLevel = (skill * 20).coerceIn(1, 100),
                    present = true,
                    withdrawn = false,
                    registrationOrder = i
                )
            )
        }
        return list
    }

    /**
     * Parses a text table from clipboard (TSV, CSV, semicolon, lines).
     * Does NOT touch the player pool.
     */
    fun parseTeamsFromText(text: String): List<ManualTournamentTeam> {
        return try {
            val sanitized = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n')
            if (sanitized.isBlank()) return emptyList()
            val lines = sanitized.lines().map { it.trim() }.filter { it.isNotBlank() }.take(300)
            if (lines.isEmpty()) return emptyList()

            // Determine separator
            val firstLine = lines.first()
            val delimiter = when {
                firstLine.contains('\t') -> "\t"
                firstLine.contains(';') -> ";"
                firstLine.contains('|') -> "|"
                firstLine.contains(',') -> ","
                else -> "\t"
            }

            // Check if first line is a header
            val lowerFirst = firstLine.lowercase().trim()
            val hasDelimiter = firstLine.contains('\t') || firstLine.contains(';') || firstLine.contains('|') || firstLine.contains(',')
            val hasHeader = (hasDelimiter && (lowerFirst.contains("name") || lowerFirst.contains("team") ||
                    lowerFirst.contains("mannschaft") || lowerFirst.contains("skill") ||
                    lowerFirst.contains("verein") || lowerFirst.contains("club"))) ||
                    lowerFirst in setOf("team", "teams", "name", "names", "mannschaft", "mannschaften")
            val dataLines = if (hasHeader) lines.drop(1) else lines

            val result = mutableListOf<ManualTournamentTeam>()
            dataLines.forEach { line ->
                try {
                    val cols = line.split(delimiter).map { it.trim().trim('"', '\'') }
                    if (cols.isNotEmpty() && cols[0].isNotBlank()) {
                        val teamName = cols[0].take(80)
                        val skill = cols.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 5) ?: 3
                        val maleCount = cols.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 99) ?: 1
                        val femaleCount = cols.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 99) ?: 1
                        val totalAge = cols.getOrNull(4)?.toIntOrNull()?.coerceIn(0, 999) ?: 50
                        val club = cols.getOrNull(5)?.take(60) ?: ""
                        val note = cols.getOrNull(6)?.take(120) ?: ""
                        result.add(
                            ManualTournamentTeam(
                                id = "mteam_${UUID.randomUUID()}",
                                name = teamName,
                                skill = skill,
                                maleCount = maleCount,
                                femaleCount = femaleCount,
                                totalAge = totalAge,
                                club = club,
                                note = note
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Ignore single malformed line
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
