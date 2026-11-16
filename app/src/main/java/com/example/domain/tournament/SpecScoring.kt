package com.example.domain.tournament

import com.example.data.model.TeamSide
import kotlin.math.abs

/**
 * Pure domain scoring, validation, forfeit & standings engine (SPEC W-01..W-07).
 * Strictly integer-based — zero floats/doubles, zero NaN/Infinity, zero UI dependencies.
 */
object SpecScoring {

    /**
     * W-01: Satzvalidierung.
     * Satz (w, l) mit Gewinnerpunkten w und Verliererpunkten l gültig, wenn ganzzahlig >= 0 und:
     * (w == target && l <= target - 2) ODER (w > target && l == w - 2) ODER (cap != null && w == cap && l == cap - 1).
     * Mit cap: w <= cap.
     * In TIMED mode: unentschieden erlaubt, kein 2-Punkte-Vorsprung nötig.
     */
    fun isSetValid(
        scoreA: Int,
        scoreB: Int,
        target: Int,
        cap: Int? = null,
        isTimed: Boolean = false
    ): Boolean {
        if (scoreA < 0 || scoreB < 0 || target <= 0) return false
        if (isTimed) {
            return (scoreA > 0 || scoreB > 0) && (cap == null || (scoreA <= cap && scoreB <= cap))
        }
        if (scoreA == scoreB) return false
        val w = maxOf(scoreA, scoreB)
        val l = minOf(scoreA, scoreB)
        if (cap != null) {
            if (cap < target) return false
            if (w > cap) return false
            if (w == cap && l == cap - 1) return true
        }
        return (w == target && l <= target - 2) || (w > target && l == w - 2)
    }

    /**
     * W-02: Spielvalidierung für BEST_OF, FIXED_SETS und TIMED.
     */
    data class MatchValidationResult(
        val isValid: Boolean,
        val isComplete: Boolean,
        val setsWonA: Int,
        val setsWonB: Int,
        val errorMessage: String? = null
    )

    fun validateMatchSets(
        sets: List<SetScore>,
        config: SpecTournamentConfig
    ): MatchValidationResult {
        if (sets.isEmpty()) {
            return MatchValidationResult(false, false, 0, 0, "Keine Sätze eingetragen.")
        }

        if (config.validationMode == MatchValidationMode.TIMED) {
            if (sets.size != 1) {
                return MatchValidationResult(false, false, 0, 0, "Zeitspiel erlaubt genau 1 Satz.")
            }
            val s = sets[0]
            if (!isSetValid(s.a, s.b, config.pointsPerSet, config.pointCap, isTimed = true)) {
                return MatchValidationResult(false, false, 0, 0, "Ungültiges Zeitspiel-Ergebnis.")
            }
            val wA = if (s.a > s.b) 1 else 0
            val wB = if (s.b > s.a) 1 else 0
            return MatchValidationResult(true, true, wA, wB)
        }

        if (config.validationMode == MatchValidationMode.FIXED_SETS) {
            val expected = config.fixedSetsCount.coerceAtLeast(1)
            if (sets.size != expected) {
                return MatchValidationResult(false, false, 0, 0, "Feste Sätze: genau $expected Sätze erforderlich.")
            }
            var wA = 0
            var wB = 0
            sets.forEachIndexed { idx, s ->
                if (!isSetValid(s.a, s.b, config.pointsPerSet, config.pointCap, isTimed = false)) {
                    return MatchValidationResult(false, false, wA, wB, "Satz ${idx + 1} ist ungültig.")
                }
                if (s.a > s.b) wA++ else wB++
            }
            return MatchValidationResult(true, true, wA, wB)
        }

        // BEST_OF (setsToWin 1 | 2 | 3)
        val setsToWin = config.setsToWin.coerceIn(1, 3)
        val maxSets = setsToWin * 2 - 1
        var wA = 0
        var wB = 0
        sets.forEachIndexed { idx, s ->
            if (wA >= setsToWin || wB >= setsToWin) {
                return MatchValidationResult(
                    false, false, wA, wB,
                    "Ab $setsToWin Gewinnsätzen sind keine weiteren Sätze zulässig."
                )
            }
            val isDeciding = (idx == maxSets - 1) && setsToWin > 1
            val target = if (isDeciding) config.decidingSetPoints else config.pointsPerSet
            if (!isSetValid(s.a, s.b, target, config.pointCap, isTimed = false)) {
                return MatchValidationResult(false, false, wA, wB, "Satz ${idx + 1} (Ziel $target) ist ungültig.")
            }
            if (s.a > s.b) wA++ else wB++
        }
        val complete = (wA >= setsToWin || wB >= setsToWin)
        return MatchValidationResult(
            isValid = complete,
            isComplete = complete,
            setsWonA = wA,
            setsWonB = wB,
            errorMessage = if (complete) null else "Spiel noch nicht bis $setsToWin Gewinnsätze beendet."
        )
    }

    /**
     * W-03: Matchpunkte (DVV3, CLASSIC2, FIXED_SETS).
     * DVV3: 3:0 ohne Entscheidungssatz (auch 3:1 bei Bo5), 2:1 mit Entscheidungssatz (2:1 bei Bo3, 3:2 bei Bo5).
     */
    fun computeMatchPoints(
        setsWonA: Int,
        setsWonB: Int,
        config: SpecTournamentConfig
    ): Pair<Int, Int> {
        return when (config.scoringSystem) {
            ScoringSystem.DVV3 -> {
                val aWon = setsWonA > setsWonB
                val isDraw = setsWonA == setsWonB
                if (isDraw) return 1 to 1
                val loserSets = minOf(setsWonA, setsWonB)
                val wentDecider = config.setsToWin > 1 && loserSets == config.setsToWin - 1
                val winPts = if (wentDecider) 2 else 3
                val losePts = if (wentDecider) 1 else 0
                if (aWon) winPts to losePts else losePts to winPts
            }
            ScoringSystem.CLASSIC2 -> {
                when {
                    setsWonA > setsWonB -> 2 to 0
                    setsWonB > setsWonA -> 0 to 2
                    else -> 1 to 1
                }
            }
            ScoringSystem.FIXED_SETS -> {
                when {
                    setsWonA > setsWonB -> 2 to 0
                    setsWonB > setsWonA -> 0 to 2
                    else -> 1 to 1
                }
            }
        }
    }

    /**
     * W-03 / F-08 Config warning helper: DVV3 mit setsToWin=1 nicht sinnvoll → CLASSIC2 empfehlen.
     */
    fun checkScoringConfigWarnings(config: SpecTournamentConfig): List<TournamentWarning> {
        val warnings = mutableListOf<TournamentWarning>()
        if (config.scoringSystem == ScoringSystem.DVV3 &&
            config.validationMode == MatchValidationMode.BEST_OF &&
            config.setsToWin == 1
        ) {
            warnings.add(
                TournamentWarning(
                    specId = "W-03",
                    message = "DVV 3-Punkte bei 1 Gewinnsatz hat keinen Entscheidungssatz – Classic 2-Punkte empfohlen."
                )
            )
        }
        return warnings
    }

    /**
     * W-06: Ganzzahl-Kreuzmultiplikation für Quotienten (aNum/aDen vs bNum/bDen).
     * Rückgabe: 1 wenn a > b, -1 wenn a < b, 0 wenn gleich.
     * - 0/0 = 0
     * - x/0 (x > 0) = unendlich:
     *   - gegen regulär -> x/0 gewinnt (1)
     *   - gegen y/0 -> NUMERATOR vergleicht x vs y; EQUAL gibt 0 zurück
     */
    fun cmpQuot(
        aNum: Int,
        aDen: Int,
        bNum: Int,
        bDen: Int,
        zeroDivisorPolicy: ZeroDivisorPolicy = ZeroDivisorPolicy.NUMERATOR
    ): Int {
        val aZero = aDen == 0
        val bZero = bDen == 0
        if (aZero && bZero) {
            if (aNum == 0 && bNum == 0) return 0
            if (aNum == 0) return -1
            if (bNum == 0) return 1
            return when (zeroDivisorPolicy) {
                ZeroDivisorPolicy.NUMERATOR -> aNum.compareTo(bNum)
                ZeroDivisorPolicy.EQUAL -> 0
            }
        }
        if (aZero) {
            // aDen == 0: if aNum == 0 -> 0/0 = 0; else +infinity
            return if (aNum == 0) {
                if (bNum == 0) 0 else -1
            } else {
                1
            }
        }
        if (bZero) {
            return if (bNum == 0) {
                if (aNum == 0) 0 else 1
            } else {
                -1
            }
        }
        val left = aNum.toLong() * bDen.toLong()
        val right = bNum.toLong() * aDen.toLong()
        return left.compareTo(right)
    }

    /**
     * W-04: Rückzug eines Teams anwenden (FORFEIT_REST oder ANNUL_ALL).
     */
    fun applyWithdrawalPolicy(
        matches: List<SpecMatch>,
        withdrawnTeamIds: Set<String>,
        policy: WithdrawalPolicy
    ): List<SpecMatch> {
        return matches.map { m ->
            val aOut = m.teamAId != null && m.teamAId in withdrawnTeamIds
            val bOut = m.teamBId != null && m.teamBId in withdrawnTeamIds
            if (!aOut && !bOut) {
                if (m.isAutoWithdrawalForfeit) {
                    return@map m.copy(
                        status = SpecMatchStatus.SCHEDULED,
                        isForfeit = false,
                        isAutoWithdrawalForfeit = false,
                        sets = emptyList(),
                        winnerSide = null
                    )
                }
                return@map m
            }

            when (policy) {
                WithdrawalPolicy.ANNUL_ALL -> {
                    m.copy(
                        status = SpecMatchStatus.FORFEIT,
                        isForfeit = true,
                        isAutoWithdrawalForfeit = (m.status != SpecMatchStatus.FINAL),
                        sets = emptyList(),
                        winnerSide = null
                    )
                }
                WithdrawalPolicy.FORFEIT_REST -> {
                    if (m.status == SpecMatchStatus.FINAL && !m.isAutoWithdrawalForfeit) {
                        m // Bereits regulär gespielte Spiele bleiben erhalten (W-04)
                    } else if (aOut && bOut) {
                        m.copy(
                            status = SpecMatchStatus.FORFEIT,
                            isForfeit = true,
                            isAutoWithdrawalForfeit = true,
                            sets = emptyList(),
                            winnerSide = null
                        )
                    } else if (aOut) {
                        m.copy(
                            status = SpecMatchStatus.FORFEIT,
                            isForfeit = true,
                            isAutoWithdrawalForfeit = true,
                            sets = listOf(SetScore(0, 25), SetScore(0, 25)),
                            winnerSide = TeamSide.TEAM_B
                        )
                    } else {
                        m.copy(
                            status = SpecMatchStatus.FORFEIT,
                            isForfeit = true,
                            isAutoWithdrawalForfeit = true,
                            sets = listOf(SetScore(25, 0), SetScore(25, 0)),
                            winnerSide = TeamSide.TEAM_A
                        )
                    }
                }
            }
        }
    }

    private data class RawAccum(
        var played: Int = 0,
        var wins: Int = 0,
        var draws: Int = 0,
        var losses: Int = 0,
        var mp: Int = 0,
        var setsWon: Int = 0,
        var setsLost: Int = 0,
        var ptsFor: Int = 0,
        var ptsAgainst: Int = 0,
        var buchholz: Int = 0
    )

    /**
     * W-05, W-06, W-07: Vollständige deterministische Tabellenberechnung
     * inkl. Quotienten per Kreuzmultiplikation, rekursiver Direktvergleich-Minitabelle,
     * Buchholz-Berechnung, Entscheidungsmarkierung und Speicherung des entscheidenden Kriteriums.
     */
    fun calculateStandings(
        teams: List<SpecTeam>,
        matches: List<SpecMatch>,
        config: SpecTournamentConfig,
        groupId: String = "",
        criteria: List<TieBreakCriterion> = config.tieBreakPreset.criteria,
        manualTieBreakKey: String = groupId.ifBlank { "OVERALL" }
    ): List<SpecStandingRow> {
        if (teams.isEmpty()) return emptyList()

        val withdrawnIds = teams.filter { it.withdrawn }.map { it.id }.toSet()
        val effectiveMatches = applyWithdrawalPolicy(matches, withdrawnIds, config.withdrawalPolicy)
            .filter { groupId.isEmpty() || it.groupId == groupId }

        val countedMatches = effectiveMatches.filter {
            it.status == SpecMatchStatus.FINAL || it.status == SpecMatchStatus.FORFEIT
        }

        // Accumulate stats per team (only FINAL/FORFEIT count per W-07; ANNUL_ALL skips withdrawn)
        val accMap = teams.associate { it.id to RawAccum() }.toMutableMap()
        val playedOpponents = teams.associate { it.id to mutableListOf<String>() }

        for (m in countedMatches) {
            if (m.isByeMatch) {
                // W-05: Im Swiss: Bye = Sieg für MP, NICHT für Sätze/Bälle/Buchholz
                val tid = m.teamAId ?: m.teamBId ?: continue
                val acc = accMap[tid] ?: continue
                val byeMp = when (config.scoringSystem) {
                    ScoringSystem.DVV3 -> 3
                    ScoringSystem.CLASSIC2, ScoringSystem.FIXED_SETS -> 2
                }
                acc.played += 1
                acc.wins += 1
                acc.mp += byeMp
                continue
            }

            val idA = m.teamAId ?: continue
            val idB = m.teamBId ?: continue
            if (config.withdrawalPolicy == WithdrawalPolicy.ANNUL_ALL &&
                (idA in withdrawnIds || idB in withdrawnIds)
            ) {
                continue
            }
            if (m.winnerSide == null && m.isForfeit) continue // Beidseitig forfait oder annulliert

            val accA = accMap[idA]
            val accB = accMap[idB]
            if (accA == null || accB == null) continue

            val setsA = m.sets.count { it.a > it.b }
            val setsB = m.sets.count { it.b > it.a }
            val isDraw = setsA == setsB && m.winnerSide == null
            val aWon = m.winnerSide == TeamSide.TEAM_A || (!isDraw && setsA > setsB)
            val bWon = m.winnerSide == TeamSide.TEAM_B || (!isDraw && setsB > setsA)

            val (mpA, mpB) = if (m.isForfeit) {
                val wPts = if (config.scoringSystem == ScoringSystem.DVV3) 3 else 2
                if (aWon) wPts to 0 else if (bWon) 0 to wPts else 0 to 0
            } else {
                computeMatchPoints(setsA, setsB, config)
            }

            val ballsA = if (m.isForfeit && !config.includeForfeitBallsInQuotient) 0 else m.sets.sumOf { it.a }
            val ballsB = if (m.isForfeit && !config.includeForfeitBallsInQuotient) 0 else m.sets.sumOf { it.b }

            accA.played += 1
            accB.played += 1
            if (aWon) {
                accA.wins += 1
                accB.losses += 1
            } else if (bWon) {
                accB.wins += 1
                accA.losses += 1
            } else {
                accA.draws += 1
                accB.draws += 1
            }
            accA.mp += mpA
            accB.mp += mpB
            accA.setsWon += setsA
            accA.setsLost += setsB
            accB.setsWon += setsB
            accB.setsLost += setsA
            accA.ptsFor += ballsA
            accA.ptsAgainst += ballsB
            accB.ptsFor += ballsB
            accB.ptsAgainst += ballsA

            playedOpponents[idA]?.add(idB)
            playedOpponents[idB]?.add(idA)
        }

        // Compute Buchholz = sum of opponents' match points (W-05: excludes Bye)
        teams.forEach { t ->
            val opps = playedOpponents[t.id].orEmpty()
            accMap[t.id]?.buchholz = opps.sumOf { oppId -> accMap[oppId]?.mp ?: 0 }
        }

        val maxPlayed = accMap.values.maxOfOrNull { it.played } ?: 0
        val minPlayed = accMap.values.minOfOrNull { it.played } ?: 0
        val provisional = maxPlayed != minPlayed // W-07

        val baseRows = teams.map { t ->
            val a = accMap[t.id]!!
            SpecStandingRow(
                rank = 1,
                teamId = t.id,
                teamName = t.name,
                matchesPlayed = a.played,
                wins = a.wins,
                draws = a.draws,
                losses = a.losses,
                matchPoints = a.mp,
                setsWon = a.setsWon,
                setsLost = a.setsLost,
                pointsFor = a.ptsFor,
                pointsAgainst = a.ptsAgainst,
                buchholz = a.buchholz,
                decidedBy = criteria.firstOrNull()?.label ?: "MP",
                isProvisional = provisional,
                decisionRequired = false
            )
        }

        // Sort deterministically & input-order independent (I-07) using recursive subgroup resolution (W-06)
        val teamById = teams.associateBy { it.id }
        val sortedRows = sortTiered(
            rows = baseRows,
            criteria = criteria,
            countedMatches = countedMatches,
            teamById = teamById,
            config = config,
            manualOrder = config.manualTieResolutions[manualTieBreakKey].orEmpty(),
            allowH2H = true
        )

        return sortedRows.mapIndexed { idx, r -> r.copy(rank = idx + 1) }
    }

    private fun compareSingleCriterion(
        a: SpecStandingRow,
        b: SpecStandingRow,
        criterion: TieBreakCriterion,
        config: SpecTournamentConfig
    ): Int {
        // Returns -1 if a ranks BETTER (ahead of) b, +1 if b ranks BETTER than a, 0 if tied!
        return when (criterion) {
            TieBreakCriterion.MATCH_POINTS -> b.matchPoints.compareTo(a.matchPoints)
            TieBreakCriterion.WINS -> b.wins.compareTo(a.wins)
            TieBreakCriterion.SET_QUOTIENT -> cmpQuot(
                b.setsWon, b.setsLost, a.setsWon, a.setsLost, config.zeroDivisorPolicy
            )
            TieBreakCriterion.BALL_QUOTIENT -> cmpQuot(
                b.pointsFor, b.pointsAgainst, a.pointsFor, a.pointsAgainst, config.zeroDivisorPolicy
            )
            TieBreakCriterion.HEAD_TO_HEAD, TieBreakCriterion.LOT -> 0
        }
    }

    private fun sortTiered(
        rows: List<SpecStandingRow>,
        criteria: List<TieBreakCriterion>,
        countedMatches: List<SpecMatch>,
        teamById: Map<String, SpecTeam>,
        config: SpecTournamentConfig,
        manualOrder: List<String>,
        allowH2H: Boolean
    ): List<SpecStandingRow> {
        if (rows.size <= 1) return rows

        // Walk criteria sequentially until we split the group into smaller buckets
        val preH2HCriteria = criteria.takeWhile {
            it != TieBreakCriterion.HEAD_TO_HEAD && it != TieBreakCriterion.LOT
        }

        fun splitByCriteriaList(
            subset: List<SpecStandingRow>,
            critList: List<TieBreakCriterion>
        ): Pair<List<List<SpecStandingRow>>, Map<String, String>> {
            var buckets = listOf(subset)
            val reasonByTeam = mutableMapOf<String, String>()

            for (crit in critList) {
                val nextBuckets = mutableListOf<List<SpecStandingRow>>()
                for (bucket in buckets) {
                    if (bucket.size <= 1) {
                        nextBuckets.add(bucket)
                        continue
                    }
                    // Sort bucket deterministically on this single criterion
                    val sorted = bucket.sortedWith { x, y ->
                        val c = compareSingleCriterion(x, y, crit, config)
                        if (c != 0) c else x.teamId.compareTo(y.teamId)
                    }
                    // Partition into contiguous tied sub-buckets
                    val subBuckets = mutableListOf<MutableList<SpecStandingRow>>()
                    for (item in sorted) {
                        if (subBuckets.isEmpty() ||
                            compareSingleCriterion(subBuckets.last().first(), item, crit, config) != 0
                        ) {
                            subBuckets.add(mutableListOf(item))
                        } else {
                            subBuckets.last().add(item)
                        }
                    }
                    if (subBuckets.size > 1) {
                        // This criterion separated at least some teams in this bucket
                        subBuckets.forEach { sb ->
                            sb.forEach { row -> reasonByTeam.putIfAbsent(row.teamId, crit.label) }
                        }
                    }
                    nextBuckets.addAll(subBuckets)
                }
                buckets = nextBuckets
            }
            return buckets to reasonByTeam
        }

        val (buckets, reasons) = splitByCriteriaList(rows, preH2HCriteria)
        val result = mutableListOf<SpecStandingRow>()

        for (bucket in buckets) {
            if (bucket.size == 1) {
                val r = bucket.first()
                result.add(r.copy(decidedBy = reasons[r.teamId] ?: r.decidedBy))
                continue
            }

            // Try recursive H2H mini-table if allowed & smaller or same group with H2H in criteria
            val hasH2H = allowH2H && criteria.contains(TieBreakCriterion.HEAD_TO_HEAD)
            if (hasH2H) {
                val tiedIds = bucket.map { it.teamId }.toSet()
                val h2hMatches = countedMatches.filter { m ->
                    !m.isByeMatch && m.teamAId in tiedIds && m.teamBId in tiedIds
                }
                if (h2hMatches.isNotEmpty()) {
                    val miniTeams = bucket.mapNotNull { teamById[it.teamId] }
                    val miniRows = calculateStandings(
                        teams = miniTeams,
                        matches = h2hMatches,
                        config = config.copy(manualTieResolutions = emptyMap()),
                        groupId = "",
                        criteria = preH2HCriteria,
                        manualTieBreakKey = ""
                    )
                    // Check if mini-table broke at least part of the tie
                    val groupedByMiniSignature = miniRows.groupBy { mr ->
                        "${mr.matchPoints}_${mr.wins}_${mr.setsWon}_${mr.setsLost}_${mr.pointsFor}_${mr.pointsAgainst}"
                    }
                    if (groupedByMiniSignature.size > 1) {
                        // Re-order according to mini-table buckets; if a sub-bucket still ties and is smaller than bucket, recurse!
                        val bucketMap = bucket.associateBy { it.teamId }
                        val orderedMini = groupedByMiniSignature.values
                        for (miniBucket in orderedMini) {
                            val subOrig = miniBucket.mapNotNull { bucketMap[it.teamId] }
                            if (subOrig.size == 1) {
                                result.add(subOrig.first().copy(decidedBy = "H2H"))
                            } else if (subOrig.size < bucket.size) {
                                // W-06: Bleibt Teilmenge gleichauf -> rekursiv nur mit dieser Teilmenge
                                val recResolved = sortTiered(
                                    rows = subOrig.map { it.copy(decidedBy = "H2H") },
                                    criteria = criteria,
                                    countedMatches = countedMatches,
                                    teamById = teamById,
                                    config = config,
                                    manualOrder = manualOrder,
                                    allowH2H = true
                                )
                                result.addAll(recResolved)
                            } else {
                                // Complete tie remained; mark decisionRequired unless manual lot resolved
                                result.addAll(resolveRemainingTie(subOrig, manualOrder))
                            }
                        }
                        continue
                    }
                }
            }

            // Unresolved tie after all criteria + H2H
            result.addAll(resolveRemainingTie(bucket, manualOrder))
        }

        return result
    }

    private fun resolveRemainingTie(
        tied: List<SpecStandingRow>,
        manualOrder: List<String>
    ): List<SpecStandingRow> {
        val indexInManual = tied.associate { r ->
            val idx = manualOrder.indexOf(r.teamId)
            r.teamId to (if (idx >= 0) idx else Int.MAX_VALUE)
        }
        val allManuallyOrdered = tied.all { (indexInManual[it.teamId] ?: Int.MAX_VALUE) < Int.MAX_VALUE }
        val sorted = tied.sortedWith { a, b ->
            val ma = indexInManual[a.teamId] ?: Int.MAX_VALUE
            val mb = indexInManual[b.teamId] ?: Int.MAX_VALUE
            if (ma != mb) ma.compareTo(mb) else a.teamId.compareTo(b.teamId) // Deterministic I-07 fallback
        }
        return sorted.map { row ->
            row.copy(
                decidedBy = if (allManuallyOrdered) "Los (Manuell)" else "Entscheidung erf.",
                decisionRequired = !allManuallyOrdered
            )
        }
    }
}
