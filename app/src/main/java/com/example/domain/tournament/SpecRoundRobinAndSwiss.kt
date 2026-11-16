package com.example.domain.tournament

import com.example.data.model.TournamentMode
import kotlin.math.abs

// ergänzt gemäß SPEC F-01, F-07, F-08, W-05

/**
 * Pure domain Round Robin (exact Kreisverfahren) and Swiss System (Branch & Bound + Fallback)
 * and Coach Recommendation + Feasibility engine.
 * Zero Android/Compose/UI dependencies. Strictly deterministic integer math.
 */
object SpecRoundRobinAndSwiss {

    const val BYE_ID = "\u0000BYE\u0000"

    /**
     * F-01: Exaktes Kreisverfahren.
     * Keine Berger-Formel!
     * Rotation pro Runde: arr.add(1, arr.removeLast())
     * Alternierendes Heimrecht über alle Paarungen (Index 0 nach Runde, innere Paare nach Paar-Parität).
     * BYE-Spiele werden sauber übersprungen (kein Geisterspiel).
     */
    fun generateRoundRobin(
        ids: List<String>,
        groupId: String,
        doubleRoundRobin: Boolean = false,
        idPrefix: String = "rr"
    ): List<SpecMatch> {
        if (ids.size < 2) return emptyList()
        val arr = ids.toMutableList()
        if (arr.size % 2 == 1) {
            arr.add(BYE_ID)
        }
        val n = arr.size
        val roundsPerLeg = n - 1
        val legs = if (doubleRoundRobin) 2 else 1
        val matches = mutableListOf<SpecMatch>()

        for (leg in 0 until legs) {
            val legPrefix = if (doubleRoundRobin) (if (leg == 0) "Hinrunde " else "Rückrunde ") else ""
            val working = arr.toMutableList()
            for (r in 0 until roundsPerLeg) {
                for (i in 0 until n / 2) {
                    val a = working[i]
                    val b = working[n - 1 - i]
                    if (a == BYE_ID || b == BYE_ID) continue
                    val flip = (if (i == 0) (r % 2 != 0) else (i % 2 != 0)) xor (leg == 1)
                    val home = if (flip) b else a
                    val away = if (flip) a else b
                    val globalRound = leg * roundsPerLeg + r + 1
                    matches.add(
                        SpecMatch(
                            id = "${idPrefix}_${groupId}_r${globalRound}_$i",
                            roundLabel = "${legPrefix}Runde ${r + 1}",
                            groupId = groupId,
                            isKo = false,
                            sourceA = MatchSource.DirectTeam(home),
                            sourceB = MatchSource.DirectTeam(away),
                            teamAId = home,
                            teamBId = away,
                            status = SpecMatchStatus.SCHEDULED,
                            phaseRound = globalRound
                        )
                    )
                }
                // Rotate clockwise keeping index 0 fixed: arr.add(1, arr.removeLast())
                val last = working.removeAt(working.size - 1)
                working.add(1, last)
            }
        }
        return matches
    }

    /**
     * Berechnet empfohlene Rundenzahl für Schweizer System: ceil(log2(n)).
     */
    fun recommendedSwissRounds(teamCount: Int): Int {
        if (teamCount <= 2) return 1
        var r = 0
        var p = 1
        while (p < teamCount) {
            p *= 2
            r++
        }
        return r
    }

    data class SwissPairingResult(
        val matches: List<SpecMatch>,
        val byeTeamId: String?,
        val warnings: List<TournamentWarning>,
        val usedRematchFallback: Boolean = false
    )

    /**
     * F-07 + W-05: Schweizer System Paarung für Runde roundNumber (>= 1).
     * - Runde 1: Nach Setzliste sortiert, obere vs untere Hälfte (i vs i + floor(n/2));
     *   bei ungerader Teamzahl erhält das niedrigstgesetzte Team das erste Freilos (BYE).
     * - Runde >= 2: Sortiert nach aktueller Tabelle (MP -> Siege -> Buchholz -> Satzquot -> Ballquot -> Seed).
     *   - Ungerade aktive Teams: niedrigstplatziertes Team ohne bisheriges Freilos, bei dem die
     *     Restteams ohne Rematch paarbar bleiben, erhält das BYE (als ByeMatch gewertet: MP-Sieg,
     *     keine Sätze/Bälle/Buchholz).
     *   - Kostenfunktion: 1000 * (MP_A - MP_B)^2 + |rankDelta - halfBracketOffset|.
     *   - Rekursiver Branch-and-Bound mit Budget 200.000 Knoten; Fallback mit Rematch-Warnung nur
     *     wenn keine rematchfreie Paarung existiert.
     */
    fun generateSwissRound(
        teams: List<SpecTeam>,
        seededOrder: List<SpecTeam>,
        previousMatches: List<SpecMatch>,
        roundNumber: Int,
        config: SpecTournamentConfig,
        idPrefix: String = "swiss"
    ): SwissPairingResult {
        val activeTeams = teams.filter { !it.withdrawn }
        if (activeTeams.size < 2) {
            return SwissPairingResult(
                emptyList(), null,
                listOf(TournamentWarning("F-07", "Zu wenige aktive Teams für Swiss-Runde $roundNumber.", isError = true))
            )
        }

        val warnings = mutableListOf<TournamentWarning>()
        val seedRankMap = seededOrder.mapIndexed { idx, t -> t.id to idx }.toMap()

        // Played pairs & previous Bye recipients
        val playedPairs = mutableSetOf<Pair<String, String>>()
        val hadByeIds = mutableSetOf<String>()
        previousMatches.forEach { m ->
            if (m.isByeMatch) {
                val tid = m.teamAId ?: m.teamBId
                if (tid != null) hadByeIds.add(tid)
            } else if (m.teamAId != null && m.teamBId != null) {
                playedPairs.add(m.teamAId to m.teamBId)
                playedPairs.add(m.teamBId to m.teamAId)
            }
        }

        // Round 1: deterministic seeded split-half (i vs i + floor(n/2)); lowest seed gets Bye if odd
        if (roundNumber == 1 && previousMatches.isEmpty()) {
            val ordered = seededOrder.filter { t -> activeTeams.any { it.id == t.id } }
            var byeTeam: SpecTeam? = null
            val pairPool = if (ordered.size % 2 == 1) {
                byeTeam = ordered.last()
                ordered.dropLast(1)
            } else {
                ordered
            }
            val half = pairPool.size / 2
            val outMatches = mutableListOf<SpecMatch>()
            for (i in 0 until half) {
                val a = pairPool[i]
                val b = pairPool[i + half]
                outMatches.add(
                    SpecMatch(
                        id = "${idPrefix}_r${roundNumber}_$i",
                        roundLabel = "Swiss Runde $roundNumber",
                        groupId = "SWISS",
                        isKo = false,
                        sourceA = MatchSource.DirectTeam(a.id),
                        sourceB = MatchSource.DirectTeam(b.id),
                        teamAId = a.id,
                        teamBId = b.id,
                        status = SpecMatchStatus.SCHEDULED,
                        phaseRound = roundNumber
                    )
                )
            }
            if (byeTeam != null) {
                outMatches.add(createSwissByeMatch(byeTeam.id, roundNumber, idPrefix))
            }
            return SwissPairingResult(outMatches, byeTeam?.id, warnings, usedRematchFallback = false)
        }

        // Later rounds: Rank by MP -> Wins -> Buchholz -> SetQuot -> BallQuot -> Seed
        val standings = SpecScoring.calculateStandings(
            teams = activeTeams,
            matches = previousMatches,
            config = config,
            groupId = "SWISS"
        )
        val standingMap = standings.associateBy { it.teamId }
        val rankedActive = activeTeams.sortedWith { a, b ->
            val sa = standingMap[a.id]
            val sb = standingMap[b.id]
            if (sa != null && sb != null) {
                if (sb.matchPoints != sa.matchPoints) return@sortedWith sb.matchPoints.compareTo(sa.matchPoints)
                if (sb.wins != sa.wins) return@sortedWith sb.wins.compareTo(sa.wins)
                if (sb.buchholz != sa.buchholz) return@sortedWith sb.buchholz.compareTo(sa.buchholz)
                val sq = SpecScoring.cmpQuot(sb.setsWon, sb.setsLost, sa.setsWon, sa.setsLost, config.zeroDivisorPolicy)
                if (sq != 0) return@sortedWith sq
                val bq = SpecScoring.cmpQuot(sb.pointsFor, sb.pointsAgainst, sa.pointsFor, sa.pointsAgainst, config.zeroDivisorPolicy)
                if (bq != 0) return@sortedWith bq
            }
            val ra = seedRankMap[a.id] ?: Int.MAX_VALUE
            val rb = seedRankMap[b.id] ?: Int.MAX_VALUE
            if (ra != rb) ra.compareTo(rb) else a.id.compareTo(b.id)
        }

        val mpMap = rankedActive.associate { it.id to (standingMap[it.id]?.matchPoints ?: 0) }
        val rankIdxMap = rankedActive.mapIndexed { idx, t -> t.id to idx }.toMap()

        // Try pairing without rematches first, trying Bye candidates from lowest-ranked without prior Bye upwards
        val byeCandidates: List<SpecTeam?> = if (rankedActive.size % 2 == 1) {
            val noPriorBye = rankedActive.asReversed().filter { it.id !in hadByeIds }
            val withPriorBye = rankedActive.asReversed().filter { it.id in hadByeIds }
            (noPriorBye + withPriorBye)
        } else {
            listOf(null)
        }

        var chosenPairs: List<Pair<String, String>>? = null
        var chosenBye: String? = null
        var usedFallback = false

        for (byeCand in byeCandidates) {
            val pool = if (byeCand == null) rankedActive else rankedActive.filter { it.id != byeCand.id }
            val result = solveMinimumCostSwissMatching(
                poolIds = pool.map { it.id },
                mpMap = mpMap,
                rankIdxMap = rankIdxMap,
                forbiddenPairs = playedPairs,
                nodeBudget = 200_000
            )
            if (result != null) {
                chosenPairs = result
                chosenBye = byeCand?.id
                break
            }
        }

        if (chosenPairs == null) {
            // Fallback: allow rematches if mathematically impossible without rematch (e.g. > n-1 rounds)
            usedFallback = true
            val byeCand = byeCandidates.firstOrNull()
            chosenBye = byeCand?.id
            val pool = if (byeCand == null) rankedActive else rankedActive.filter { it.id != byeCand.id }
            chosenPairs = solveMinimumCostSwissMatching(
                poolIds = pool.map { it.id },
                mpMap = mpMap,
                rankIdxMap = rankIdxMap,
                forbiddenPairs = emptySet(),
                nodeBudget = 200_000
            ) ?: pool.chunked(2).mapNotNull { chunk ->
                if (chunk.size == 2) chunk[0].id to chunk[1].id else null
            }
            warnings.add(
                TournamentWarning(
                    "F-07",
                    "Swiss Runde $roundNumber: Keine rematchfreie Paarung mehr möglich – Rückgriff auf bestmögliche Paarung mit Wiederbegegnung."
                )
            )
        }

        val matches = chosenPairs.mapIndexed { idx, (idA, idB) ->
            SpecMatch(
                id = "${idPrefix}_r${roundNumber}_$idx",
                roundLabel = "Swiss Runde $roundNumber",
                groupId = "SWISS",
                isKo = false,
                sourceA = MatchSource.DirectTeam(idA),
                sourceB = MatchSource.DirectTeam(idB),
                teamAId = idA,
                teamBId = idB,
                status = SpecMatchStatus.SCHEDULED,
                phaseRound = roundNumber
            )
        }.toMutableList()

        if (chosenBye != null) {
            matches.add(createSwissByeMatch(chosenBye, roundNumber, idPrefix))
        }

        return SwissPairingResult(matches, chosenBye, warnings, usedRematchFallback = usedFallback)
    }

    private fun createSwissByeMatch(teamId: String, roundNumber: Int, idPrefix: String): SpecMatch {
        // W-05: Bye record scored immediately for standings (MP win, 0 sets, 0 balls), NEVER scheduled on court!
        return SpecMatch(
            id = "${idPrefix}_r${roundNumber}_bye_$teamId",
            roundLabel = "Swiss Runde $roundNumber (Freilos)",
            groupId = "SWISS",
            isKo = false,
            sourceA = MatchSource.DirectTeam(teamId),
            sourceB = MatchSource.Bye,
            teamAId = teamId,
            teamBId = null,
            status = SpecMatchStatus.FINAL,
            isByeMatch = true,
            phaseRound = roundNumber
        )
    }

    private fun solveMinimumCostSwissMatching(
        poolIds: List<String>,
        mpMap: Map<String, Int>,
        rankIdxMap: Map<String, Int>,
        forbiddenPairs: Set<Pair<String, String>>,
        nodeBudget: Int
    ): List<Pair<String, String>>? {
        val n = poolIds.size
        if (n % 2 != 0) return null
        if (n == 0) return emptyList()

        val halfOffset = n / 2

        fun edgeCost(i: Int, j: Int): Long {
            val idA = poolIds[i]
            val idB = poolIds[j]
            val mpDiff = (mpMap[idA] ?: 0) - (mpMap[idB] ?: 0)
            val rankDelta = abs((rankIdxMap[idB] ?: j) - (rankIdxMap[idA] ?: i))
            // Prioritize matching same/closest MP first; within score group aim near halfOffset
            return 1000L * mpDiff * mpDiff + abs(rankDelta - halfOffset).toLong()
        }

        // Precompute valid sorted candidates per index
        val candidates = Array(n) { i ->
            (0 until n).filter { j ->
                j > i && (poolIds[i] to poolIds[j]) !in forbiddenPairs
            }.sortedBy { j -> edgeCost(i, j) }
        }

        val used = BooleanArray(n)
        var bestCost = Long.MAX_VALUE
        var bestMatching: List<Pair<String, String>>? = null
        val current = ArrayList<Pair<String, String>>(n / 2)
        var nodesVisited = 0

        fun dfs(accCost: Long) {
            if (nodesVisited >= nodeBudget) return
            nodesVisited++
            if (accCost >= bestCost) return

            // Find first unused vertex (or MRV if constrained)
            var firstIdx = -1
            var minOptions = Int.MAX_VALUE
            for (i in 0 until n) {
                if (!used[i]) {
                    val availCount = candidates[i].count { !used[it] } +
                        (0 until i).count { j -> !used[j] && (poolIds[j] to poolIds[i]) !in forbiddenPairs }
                    if (availCount == 0) return // Prune dead end immediately
                    if (availCount < minOptions) {
                        minOptions = availCount
                        firstIdx = i
                        if (minOptions == 1) break
                    }
                }
            }
            if (firstIdx == -1) {
                bestCost = accCost
                bestMatching = current.toList()
                return
            }

            used[firstIdx] = true
            // Collect all legal unused partners ordered by edgeCost
            val partners = (0 until n).filter { j ->
                !used[j] && (poolIds[firstIdx] to poolIds[j]) !in forbiddenPairs
            }.sortedBy { j -> edgeCost(firstIdx, j) }

            for (partner in partners) {
                val cost = edgeCost(firstIdx, partner)
                if (accCost + cost >= bestCost) continue
                used[partner] = true
                current.add(poolIds[firstIdx] to poolIds[partner])
                dfs(accCost + cost)
                current.removeAt(current.size - 1)
                used[partner] = false
                if (nodesVisited >= nodeBudget) break
            }
            used[firstIdx] = false
        }

        dfs(0L)
        return bestMatching
    }

    /**
     * F-08: Verbandstrainer-Empfehlung + Machbarkeitsprüfung (M Spiele, c Plätze).
     */
    data class CoachAdvice(
        val recommendedMode: TournamentMode,
        val recommendedGroups: Int,
        val recommendedKoSize: Int,
        val headline: String,
        val explanation: String,
        val feasibility: FeasibilityCheck
    )

    fun coachRecommendation(
        teamCount: Int,
        courtCount: Int,
        slotDurationMinutes: Int = 35,
        availableWindowMinutes: Int? = null,
        koSizeOverride: Int? = null,
        groupsOverride: Int? = null,
        advancePerGroupOverride: Int? = null,
        wildcardsOverride: Int? = null
    ): CoachAdvice {
        val c = courtCount.coerceAtLeast(1)
        val slotMin = slotDurationMinutes.coerceAtLeast(5)
        val (mode, recG, recKo, title, desc) = when {
            teamCount <= 1 -> listOf(
                TournamentMode.ROUND_ROBIN, 1, 0,
                "Mindestens 2 Teams benötigt",
                "Bitte lege mindestens 2 Teams an."
            ).let { CoachTuple(TournamentMode.ROUND_ROBIN, 1, 0, "Mindestens 2 Teams", "Bitte lege mindestens 2 Teams an.") }
            teamCount <= 6 -> CoachTuple(
                TournamentMode.ROUND_ROBIN, 1, 0,
                "Jeder gegen Jeden ($teamCount Teams)",
                "Bei $teamCount Teams ideal: Vollrunde ohne K.-o. — maximale Spielzeit und sportlich fairster Vergleich."
            )
            teamCount <= 12 -> CoachTuple(
                TournamentMode.GROUPS_KO, 2, 4,
                "2 Gruppen + Halbfinale/Finale ($teamCount Teams)",
                "Bei $teamCount Teams optimal: 2 Vorrundengruppen (nur intern), Top 2 ins Halbfinale (4er-K.-o.)."
            )
            teamCount <= 20 -> CoachTuple(
                TournamentMode.GROUPS_KO, 4, 8,
                "4 Gruppen + Viertelfinale ($teamCount Teams)",
                "Bei $teamCount Teams empfohlen: 4 Gruppen à ${teamCount / 4} Teams, Top 2 ins 8er-K.-o.-Feld."
            )
            else -> CoachTuple(
                TournamentMode.SWISS_SYSTEM, 1, 8,
                "Schweizer System + Top-8 K.-o. ($teamCount Teams)",
                "Bei $teamCount Teams verhindert das Schweizer System leere Spiele und liefert ausgeglichene Duelle in ${recommendedSwissRounds(teamCount)} Runden."
            )
        }

        val estMatches = estimateMatchCount(mode, teamCount, recG, recKo)
        val estSlots = (estMatches + c - 1) / c
        val estMin = estSlots * slotMin

        val koGapWarning = if (mode == TournamentMode.GROUPS_KO && groupsOverride != null && advancePerGroupOverride != null && koSizeOverride != null) {
            val q = groupsOverride * advancePerGroupOverride + (wildcardsOverride ?: 0)
            if (q != koSizeOverride && koSizeOverride > 0) {
                "Qualifizierte Teams ($q) passen nicht lückenlos in K.-o.-Größe $koSizeOverride (Wildcards/Freilose anpassen)."
            } else null
        } else null

        val durationWarning = if (availableWindowMinutes != null && estMin > availableWindowMinutes) {
            "Geschätzte Dauer (${estMin} Min / $estSlots Slots) überschreitet Zeitfenster ($availableWindowMinutes Min)."
        } else null

        return CoachAdvice(
            recommendedMode = mode,
            recommendedGroups = recG,
            recommendedKoSize = recKo,
            headline = title,
            explanation = desc,
            feasibility = FeasibilityCheck(
                estimatedSlots = estSlots,
                estimatedTotalMinutes = estMin,
                koGapsWarning = koGapWarning,
                durationWarning = durationWarning
            )
        )
    }

    private data class CoachTuple(
        val mode: TournamentMode,
        val groups: Int,
        val koSize: Int,
        val title: String,
        val desc: String
    )

    fun estimateMatchCount(
        mode: TournamentMode,
        teamCount: Int,
        groupsCount: Int,
        koSize: Int
    ): Int {
        if (teamCount < 2) return 0
        return when (mode) {
            TournamentMode.ROUND_ROBIN -> teamCount * (teamCount - 1) / 2
            TournamentMode.SWISS_SYSTEM -> recommendedSwissRounds(teamCount) * (teamCount / 2)
            TournamentMode.SINGLE_ELIMINATION -> teamCount - 1 + 1 // + Platz 3
            TournamentMode.GROUPS_KO -> {
                val g = groupsCount.coerceAtLeast(1)
                val base = teamCount / g
                val rem = teamCount % g
                val groupMatches = rem * ((base + 1) * base / 2) + (g - rem) * (base * (base - 1) / 2)
                val koMatches = if (koSize >= 2) koSize else 0 // incl. Platz 3
                groupMatches + koMatches
            }
            TournamentMode.PLACEMENT -> {
                val g = groupsCount.coerceAtLeast(1)
                val base = teamCount / g
                val rem = teamCount % g
                val groupMatches = rem * ((base + 1) * base / 2) + (g - rem) * (base * (base - 1) / 2)
                groupMatches + (teamCount / 2)
            }
            TournamentMode.DOUBLE_ELIMINATION -> (teamCount - 1) * 2
            TournamentMode.KING_OF_THE_COURT -> teamCount
        }
    }
}
