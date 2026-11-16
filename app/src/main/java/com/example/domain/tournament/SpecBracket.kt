package com.example.domain.tournament

import com.example.data.model.TeamSide

// ergänzt gemäß SPEC F-02, F-03, F-04, F-05, F-06, W-05

/**
 * Pure domain KO Bracket construction & idempotent placeholder resolution engine.
 * Supports:
 * - Power-of-2 doubling bracketOrder(P) (F-04)
 * - Gapless KO qualification validation (F-02)
 * - R1 same-group rematch avoidance swap pass (F-03)
 * - Structured MatchSource (GroupRank, Wildcard, WinnerOf, LoserOf, Bye) (F-05)
 * - Consolation (NONE, THIRD, FULL placement bracket) (F-05)
 * - Idempotent resolver with decisionRequired guard and Bye auto-advance (F-06, W-05)
 */
object SpecBracket {

    /**
     * F-04: Standard-Setzreihenfolge per Zweierpotenz-Faltung (1-basierte Seeds):
     * P=4 -> [1, 4, 2, 3]
     * P=8 -> [1, 8, 4, 5, 2, 7, 3, 6]
     */
    fun bracketOrder(p: Int): List<Int> {
        require(p >= 1 && (p and (p - 1)) == 0) { "K.-o.-Größe P muss eine Zweierpotenz sein (war $p)." }
        var order = listOf(1)
        while (order.size < p) {
            val size = order.size * 2
            order = order.flatMap { s -> listOf(s, size + 1 - s) }
        }
        return order
    }

    fun nextPowerOfTwo(n: Int): Int {
        var p = 2
        while (p < n) p *= 2
        return p
    }

    /**
     * F-02: Lückenlose K.-o.-Qualifikation validieren.
     * G * a + w muss == P sein (oder bei Single Elimination <= P mit Freilosen).
     */
    fun validateKoSetup(
        groupsCount: Int,
        advancePerGroup: Int,
        wildcards: Int,
        koSize: Int
    ): List<TournamentWarning> {
        val warnings = mutableListOf<TournamentWarning>()
        if (koSize < 2 || (koSize and (koSize - 1)) != 0) {
            warnings.add(TournamentWarning("F-02", "K.-o.-Größe P ($koSize) muss 2, 4, 8 oder 16 sein.", isError = true))
            return warnings
        }
        val qualified = groupsCount * advancePerGroup + wildcards
        if (qualified != koSize) {
            warnings.add(
                TournamentWarning(
                    "F-02",
                    "Gruppen ($groupsCount × $advancePerGroup) + Wildcards ($wildcards) = $qualified ≠ K.-o.-Größe $koSize.",
                    isError = true
                )
            )
        }
        return warnings
    }

    /**
     * F-03 + F-04 + F-05: Erzeugt komplettes strukturiertes K.-o.-Bracket (inkl. Platzierung)
     * für Gruppen+K.-o. mit Platzhalter-MatchSources (GroupRank / Wildcard / WinnerOf / LoserOf).
     * Löst R1-Gruppenrematch-Vermeidung über Kreuz-/Swap-Pass.
     */
    fun buildGroupPlusKoBracket(
        groupsCount: Int,
        advancePerGroup: Int,
        wildcards: Int,
        koSize: Int,
        consolationMode: ConsolationMode,
        idPrefix: String = "ko"
    ): List<SpecMatch> {
        val p = koSize.coerceAtLeast(2)
        val groupIds = List(groupsCount.coerceAtLeast(1)) { ('A' + it).toString() }

        // Build seeded source list (size P): rank 1 across groups, then rank 2 across groups, ..., then wildcards
        val seededSources = mutableListOf<MatchSource>()
        for (rank in 1..advancePerGroup) {
            // Order group indices cleanly for balanced halves
            val gOrder = if (rank % 2 == 1) groupIds else groupIds.reversed()
            gOrder.forEach { g ->
                if (seededSources.size < p) {
                    seededSources.add(MatchSource.GroupRank(g, rank))
                }
            }
        }
        for (w in 1..wildcards) {
            if (seededSources.size < p) {
                seededSources.add(MatchSource.Wildcard(w))
            }
        }
        while (seededSources.size < p) {
            seededSources.add(MatchSource.Bye)
        }

        // Standard seeded bracket slot assignment
        val slotSeeds = bracketOrder(p) // 1-based seeds
        val pairs = MutableList(p / 2) { idx ->
            seededSources[slotSeeds[idx * 2] - 1] to seededSources[slotSeeds[idx * 2 + 1] - 1]
        }

        // F-03: Avoid Round 1 same-group matchups where possible by swapping B-slots across pairs
        // Keep top-half / bottom-half separation where possible, fallback to any valid pair swap
        fun groupOfSource(src: MatchSource): String? = (src as? MatchSource.GroupRank)?.groupId
        for (i in pairs.indices) {
            val gA = groupOfSource(pairs[i].first)
            val gB = groupOfSource(pairs[i].second)
            if (gA != null && gA == gB) {
                var swapped = false
                for (j in pairs.indices) {
                    if (i == j) continue
                    val jA = groupOfSource(pairs[j].first)
                    val jB = groupOfSource(pairs[j].second)
                    if (jB != gA && gB != jA) {
                        val oldI = pairs[i]
                        val oldJ = pairs[j]
                        pairs[i] = oldI.first to oldJ.second
                        pairs[j] = oldJ.first to oldI.second
                        swapped = true
                        break
                    }
                }
                if (!swapped) {
                    for (j in pairs.indices) {
                        if (i == j) continue
                        val jA = groupOfSource(pairs[j].first)
                        val jB = groupOfSource(pairs[j].second)
                        if (jA != gA && gB != jB) {
                            val oldI = pairs[i]
                            val oldJ = pairs[j]
                            pairs[i] = oldI.first to oldJ.first
                            pairs[j] = oldI.second to oldJ.second
                            break
                        }
                    }
                }
            }
        }

        return buildTreeFromR1Pairs(pairs, consolationMode, idPrefix)
    }

    /**
     * Erzeugt direktes K.-o.-Bracket für bekannte gesetzte Team-IDs (Single Elimination mit Freilosen).
     */
    fun buildSingleEliminationBracket(
        seededTeamIds: List<String>,
        consolationMode: ConsolationMode,
        idPrefix: String = "ko"
    ): List<SpecMatch> {
        if (seededTeamIds.size < 2) return emptyList()
        val p = nextPowerOfTwo(seededTeamIds.size)
        val slotSeeds = bracketOrder(p)
        val slots: List<MatchSource> = slotSeeds.map { seed1Based ->
            val tid = seededTeamIds.getOrNull(seed1Based - 1)
            if (tid != null) MatchSource.DirectTeam(tid) else MatchSource.Bye
        }
        val pairs = (0 until p / 2).map { i -> slots[i * 2] to slots[i * 2 + 1] }
        return buildTreeFromR1Pairs(pairs, consolationMode, idPrefix)
    }

    private fun roundNameForSize(currentSize: Int): String = when (currentSize) {
        2 -> "Finale (Platz 1)"
        4 -> "Halbfinale"
        8 -> "Viertelfinale"
        16 -> "Achtelfinale"
        32 -> "Sechzehntelfinale"
        else -> "K.-o. Runde ($currentSize)"
    }

    private fun buildTreeFromR1Pairs(
        r1Pairs: List<Pair<MatchSource, MatchSource>>,
        consolationMode: ConsolationMode,
        idPrefix: String
    ): List<SpecMatch> {
        val allMatches = mutableListOf<SpecMatch>()
        var currentSize = r1Pairs.size * 2
        var phaseRound = 1

        // Build Round 1
        var prevRoundIds = r1Pairs.mapIndexed { idx, (sA, sB) ->
            val mId = "${idPrefix}_r${phaseRound}_$idx"
            val baseName = roundNameForSize(currentSize)
            val label = if (r1Pairs.size == 1) baseName else "$baseName ${idx + 1}"
            val directA = (sA as? MatchSource.DirectTeam)?.teamId
            val directB = (sB as? MatchSource.DirectTeam)?.teamId
            val isAutoBye = (sA is MatchSource.Bye) || (sB is MatchSource.Bye)
            val winnerSide = when {
                sA is MatchSource.DirectTeam && sB is MatchSource.Bye -> TeamSide.TEAM_A
                sA is MatchSource.Bye && sB is MatchSource.DirectTeam -> TeamSide.TEAM_B
                else -> null
            }
            allMatches.add(
                SpecMatch(
                    id = mId,
                    roundLabel = label,
                    groupId = "KO",
                    isKo = true,
                    sourceA = sA,
                    sourceB = sB,
                    teamAId = directA,
                    teamBId = directB,
                    status = if (isAutoBye) SpecMatchStatus.FINAL else SpecMatchStatus.SCHEDULED,
                    winnerSide = winnerSide,
                    isByeMatch = isAutoBye,
                    phaseRound = phaseRound
                )
            )
            mId
        }

        // Subsequent main rounds until Final
        var semiIdsForThird: List<String> = if (currentSize == 4) prevRoundIds else emptyList()
        var currentLosersByRound = mutableListOf(prevRoundIds to currentSize)

        while (prevRoundIds.size >= 2) {
            currentSize /= 2
            phaseRound++
            val nextRoundIds = mutableListOf<String>()
            val baseName = roundNameForSize(currentSize)
            for (idx in 0 until prevRoundIds.size / 2) {
                val depA = prevRoundIds[idx * 2]
                val depB = prevRoundIds[idx * 2 + 1]
                val mId = "${idPrefix}_r${phaseRound}_$idx"
                val label = if (currentSize == 2) baseName else "$baseName ${idx + 1}"
                allMatches.add(
                    SpecMatch(
                        id = mId,
                        roundLabel = label,
                        groupId = "KO",
                        isKo = true,
                        sourceA = MatchSource.WinnerOf(depA),
                        sourceB = MatchSource.WinnerOf(depB),
                        dependsOnMatchIds = listOf(depA, depB),
                        phaseRound = phaseRound
                    )
                )
                nextRoundIds.add(mId)
            }
            if (currentSize == 2) {
                semiIdsForThird = prevRoundIds
            }
            currentLosersByRound.add(nextRoundIds to currentSize)
            prevRoundIds = nextRoundIds
        }

        // Consolation brackets (F-05: THIRD or FULL placement)
        if (consolationMode == ConsolationMode.THIRD && semiIdsForThird.size == 2) {
            val s0 = semiIdsForThird[0]
            val s1 = semiIdsForThird[1]
            allMatches.add(
                SpecMatch(
                    id = "${idPrefix}_third",
                    roundLabel = "Spiel um Platz 3",
                    groupId = "KO",
                    isKo = true,
                    sourceA = MatchSource.LoserOf(s0),
                    sourceB = MatchSource.LoserOf(s1),
                    dependsOnMatchIds = listOf(s0, s1),
                    phaseRound = phaseRound
                )
            )
        } else if (consolationMode == ConsolationMode.FULL) {
            // Recursive placement brackets for every loser layer (e.g., QF losers -> 5-8 -> 5th & 7th; SF losers -> 3rd)
            for ((loserParentIds, roundBracketSize) in currentLosersByRound) {
                if (roundBracketSize < 4 || loserParentIds.size < 2) continue
                val startPlace = roundBracketSize / 2 + 1
                var layerIds = loserParentIds.map { MatchSource.LoserOf(it) to it }
                // Single consolation round or full mini-bracket for 4 losers (places startPlace..startPlace+3)
                if (layerIds.size == 2) {
                    val (sA, dA) = layerIds[0]
                    val (sB, dB) = layerIds[1]
                    allMatches.add(
                        SpecMatch(
                            id = "${idPrefix}_place_$startPlace",
                            roundLabel = "Spiel um Platz $startPlace",
                            groupId = "KO",
                            isKo = true,
                            sourceA = sA,
                            sourceB = sB,
                            dependsOnMatchIds = listOf(dA, dB),
                            phaseRound = phaseRound
                        )
                    )
                } else if (layerIds.size == 4) {
                    val pSemi0 = "${idPrefix}_place_${startPlace}_sf0"
                    val pSemi1 = "${idPrefix}_place_${startPlace}_sf1"
                    allMatches.add(
                        SpecMatch(
                            id = pSemi0,
                            roundLabel = "Platz ${startPlace}–${startPlace + 3} HF 1",
                            groupId = "KO",
                            isKo = true,
                            sourceA = layerIds[0].first,
                            sourceB = layerIds[1].first,
                            dependsOnMatchIds = listOf(layerIds[0].second, layerIds[1].second),
                            phaseRound = phaseRound - 1
                        )
                    )
                    allMatches.add(
                        SpecMatch(
                            id = pSemi1,
                            roundLabel = "Platz ${startPlace}–${startPlace + 3} HF 2",
                            groupId = "KO",
                            isKo = true,
                            sourceA = layerIds[2].first,
                            sourceB = layerIds[3].first,
                            dependsOnMatchIds = listOf(layerIds[2].second, layerIds[3].second),
                            phaseRound = phaseRound - 1
                        )
                    )
                    allMatches.add(
                        SpecMatch(
                            id = "${idPrefix}_place_$startPlace",
                            roundLabel = "Spiel um Platz $startPlace",
                            groupId = "KO",
                            isKo = true,
                            sourceA = MatchSource.WinnerOf(pSemi0),
                            sourceB = MatchSource.WinnerOf(pSemi1),
                            dependsOnMatchIds = listOf(pSemi0, pSemi1),
                            phaseRound = phaseRound
                        )
                    )
                    allMatches.add(
                        SpecMatch(
                            id = "${idPrefix}_place_${startPlace + 2}",
                            roundLabel = "Spiel um Platz ${startPlace + 2}",
                            groupId = "KO",
                            isKo = true,
                            sourceA = MatchSource.LoserOf(pSemi0),
                            sourceB = MatchSource.LoserOf(pSemi1),
                            dependsOnMatchIds = listOf(pSemi0, pSemi1),
                            phaseRound = phaseRound
                        )
                    )
                }
            }
        }

        return allMatches
    }

    /**
     * F-06: Idempotenter Platzhalter-Resolver.
     * - Löst GroupRank(g, r) nur auf, wenn ALLE Gruppenspiele von g FINAL/FORFEIT sind UND kein ungelöster Gleichstand (decisionRequired) vorliegt.
     * - Löst Wildcard(w) nur auf, wenn ALLE Vorrundengruppen vollständig abgeschlossen sind und Wildcard-Vergleich eindeutig ist.
     * - Löst WinnerOf(m) / LoserOf(m) sofort auf, wenn m FINAL/FORFEIT oder isByeMatch mit Sieger ist.
     * - Bei Bye-Match ohne Gegner (DirectTeam vs Bye): setzt sofort winnerSide & teamAId/teamBId und markiert als isByeMatch=true (kein Geisterspiel auf Platz).
     * - Schutz: Laufende/fertige Spiele überschreiben niemals Team-Zuweisungen.
     */
    fun resolveBracket(
        teams: List<SpecTeam>,
        groupAssignments: Map<String, List<String>>,
        allMatches: List<SpecMatch>,
        config: SpecTournamentConfig
    ): Pair<List<SpecMatch>, List<TournamentWarning>> {
        val warnings = mutableListOf<TournamentWarning>()
        val teamById = teams.associateBy { it.id }

        // Group completion and standings map
        val groupStandings = mutableMapOf<String, List<SpecStandingRow>>()
        val groupComplete = mutableMapOf<String, Boolean>()
        val groupHasTieBlock = mutableMapOf<String, Boolean>()

        groupAssignments.forEach { (gId, tids) ->
            val gMatches = allMatches.filter { !it.isKo && it.groupId == gId && !it.isByeMatch }
            val finished = gMatches.isNotEmpty() && gMatches.all {
                it.status == SpecMatchStatus.FINAL || it.status == SpecMatchStatus.FORFEIT
            }
            groupComplete[gId] = finished
            val gTeams = tids.mapNotNull { teamById[it] }
            val rows = SpecScoring.calculateStandings(gTeams, allMatches, config, groupId = gId)
            groupStandings[gId] = rows
            groupHasTieBlock[gId] = rows.any { it.decisionRequired }
            if (finished && rows.any { it.decisionRequired }) {
                warnings.add(
                    TournamentWarning(
                        "F-06",
                        "Gruppe $gId hat ein ungelöstes Gleichstands-Los („Entscheidung erf.“) – K.-o.-Auflösung pausiert.",
                        isError = false
                    )
                )
            }
        }

        // Wildcard pool (best non-automatically-qualified rank across groups)
        val allGroupsReady = groupAssignments.isNotEmpty() &&
            groupAssignments.keys.all { groupComplete[it] == true && groupHasTieBlock[it] == false }
        val wildcardOrdered: List<SpecStandingRow> = if (allGroupsReady && config.wildcards > 0) {
            SpecSeedingAndGroups.compareAcrossGroups(
                groupStandings = groupStandings,
                targetRankInGroup = config.advancePerGroup + 1,
                teams = teams,
                matches = allMatches,
                config = config
            )
        } else {
            emptyList()
        }

        val working = allMatches.associateBy { it.id }.toMutableMap()

        fun resolveSourceTeam(src: MatchSource?): Pair<String?, Boolean> {
            // Returns (teamId, isExplicitBye)
            return when (src) {
                null -> null to false
                is MatchSource.DirectTeam -> src.teamId to false
                is MatchSource.Bye -> null to true
                is MatchSource.GroupRank -> {
                    val ready = groupComplete[src.groupId] == true && groupHasTieBlock[src.groupId] == false
                    if (!ready) null to false
                    else groupStandings[src.groupId]?.getOrNull(src.rank - 1)?.teamId to false
                }
                is MatchSource.Wildcard -> {
                    if (!allGroupsReady) null to false
                    else wildcardOrdered.getOrNull(src.wildcardRank - 1)?.teamId to false
                }
                is MatchSource.WinnerOf -> {
                    val dep = working[src.matchId]
                    if (dep != null && (dep.status == SpecMatchStatus.FINAL || dep.status == SpecMatchStatus.FORFEIT)) {
                        when {
                            dep.winnerSide == TeamSide.TEAM_A -> dep.teamAId to false
                            dep.winnerSide == TeamSide.TEAM_B -> dep.teamBId to false
                            dep.isByeMatch -> (dep.teamAId ?: dep.teamBId) to (dep.teamAId == null && dep.teamBId == null)
                            else -> null to false
                        }
                    } else null to false
                }
                is MatchSource.LoserOf -> {
                    val dep = working[src.matchId]
                    if (dep != null && (dep.status == SpecMatchStatus.FINAL || dep.status == SpecMatchStatus.FORFEIT) && !dep.isByeMatch) {
                        when (dep.winnerSide) {
                            TeamSide.TEAM_A -> dep.teamBId to false
                            TeamSide.TEAM_B -> dep.teamAId to false
                            null -> null to false
                        }
                    } else if (dep != null && dep.isByeMatch) {
                        null to true
                    } else {
                        null to false
                    }
                }
            }
        }

        // Fixed-point loop until no further resolutions happen (max allMatches.size passes)
        for (pass in 0..allMatches.size) {
            var changed = false
            for (m in allMatches) {
                val cur = working[m.id] ?: continue
                if (!cur.isKo) continue
                if (cur.status == SpecMatchStatus.RUNNING || cur.status == SpecMatchStatus.FINAL || cur.status == SpecMatchStatus.FORFEIT) {
                    continue
                }
                val (resA, byeA) = resolveSourceTeam(cur.sourceA)
                val (resB, byeB) = resolveSourceTeam(cur.sourceB)

                val updated = if (byeA || byeB) {
                    val winner = when {
                        resA != null && byeB -> TeamSide.TEAM_A
                        byeA && resB != null -> TeamSide.TEAM_B
                        else -> null
                    }
                    cur.copy(
                        teamAId = resA,
                        teamBId = resB,
                        isByeMatch = true,
                        status = if (winner != null || (byeA && byeB)) SpecMatchStatus.FINAL else cur.status,
                        winnerSide = winner,
                        slotIndex = null,
                        courtNumber = null
                    )
                } else {
                    cur.copy(
                        teamAId = resA ?: cur.teamAId,
                        teamBId = resB ?: cur.teamBId
                    )
                }
                if (updated != cur) {
                    working[m.id] = updated
                    changed = true
                }
            }
            if (!changed) break
        }

        val orderedOut = allMatches.map { working[it.id] ?: it }
        return orderedOut to warnings
    }

    /**
     * Erzeugt Platzierungsspiele (1. vs 1., 2. vs 2., ...) mit MatchSources (F-05).
     */
    fun buildPlacementMatches(
        groupsCount: Int,
        groupSizes: List<Int>,
        idPrefix: String = "place"
    ): List<SpecMatch> {
        val gIds = List(groupsCount.coerceAtLeast(1)) { ('A' + it).toString() }
        val maxDepth = groupSizes.maxOrNull() ?: 0
        val out = mutableListOf<SpecMatch>()
        if (gIds.size == 2) {
            for (r in 1..maxDepth) {
                val place = (r - 1) * 2 + 1
                val label = if (place == 1) "Spiel um Platz 1 (Finale)" else "Spiel um Platz $place"
                out.add(
                    SpecMatch(
                        id = "${idPrefix}_p$place",
                        roundLabel = label,
                        groupId = "KO",
                        isKo = true,
                        sourceA = MatchSource.GroupRank(gIds[0], r),
                        sourceB = MatchSource.GroupRank(gIds[1], r),
                        phaseRound = 1
                    )
                )
            }
        } else {
            var place = 1
            for (r in 1..maxDepth) {
                var i = 0
                while (i + 1 < gIds.size) {
                    val label = if (place == 1) "Spiel um Platz 1 (Finale)" else "Spiel um Platz $place"
                    out.add(
                        SpecMatch(
                            id = "${idPrefix}_p$place",
                            roundLabel = label,
                            groupId = "KO",
                            isKo = true,
                            sourceA = MatchSource.GroupRank(gIds[i], r),
                            sourceB = MatchSource.GroupRank(gIds[i + 1], r),
                            phaseRound = 1
                        )
                    )
                    place += 2
                    i += 2
                }
            }
        }
        return out
    }
}
