package com.example.domain.tournament

import com.example.data.model.TeamSide
import kotlin.math.abs

// ergänzt gemäß SPEC P-01..P-06, R-01..R-06, A6

/**
 * Pure domain Slot-based Multi-Court Scheduler, Fair Referee Assignment Engine,
 * and Derived Team Statistics calculator.
 * Zero UI/Android imports. Strictly deterministic integer math.
 */
object SpecSchedulerAndReferees {

    fun formatSlotTime(slotIndex: Int?, config: SpecTournamentConfig, isScheduledFuture: Boolean = true): String {
        if (slotIndex == null || slotIndex < 0) return "--:--"
        val delay = if (isScheduledFuture) config.liveDelayMinutes else 0
        val totalMin = (config.startTimeMinutes + slotIndex * config.slotDurationMinutes + delay).coerceAtLeast(0)
        val hh = (totalMin / 60) % 24
        val mm = totalMin % 60
        return String.format("%02d:%02d", hh, mm)
    }

    /**
     * P-01..P-06: Multi-Court Slot Scheduler.
     * Hard rules:
     * - H1: Kein Team zweimal im selben Slot.
     * - H2: Pro Slot max c Plätze (1..c), keine Doppelbelegung desselben Feldes im Slot.
     * - H3: Abhängigkeiten eingehalten: slot(m) >= max(slot(dep)) + 1 und KO nach Gruppenphase.
     * - H4: Bye-Spiele und Forfeit/annullierte ohne Spielfeld erhalten keinen Slot/Feld.
     * - H5: Mindestpause je nach RestStrictness (STRICT=hart, BALANCED=optimiert, FAST=min. Slots).
     * - G-06/I-14: Bereits gestartete/beendete Spiele (RUNNING/FINAL/FORFEIT) mit bestehendem Slot behalten diesen exakt.
     */
    fun scheduleMatches(
        matches: List<SpecMatch>,
        teams: List<SpecTeam>,
        courtCount: Int,
        config: SpecTournamentConfig
    ): ScheduleResult {
        val c = courtCount.coerceIn(1, 32)
        if (matches.isEmpty()) {
            return ScheduleResult(emptyList(), emptyList(), minRestAchieved = 0, b2bCount = 0, makespanSlots = 0)
        }

        val withdrawnIds = teams.filter { it.withdrawn }.map { it.id }.toSet()
        val warnings = mutableListOf<TournamentWarning>()

        // Separate non-schedulable (Bye or double-forfeit/annulled without active matchup)
        // Running/Final matches with existing slot+court remain frozen!
        val frozenIds = mutableSetOf<String>()
        val nonCourtIds = mutableSetOf<String>()
        val schedulable = mutableListOf<SpecMatch>()

        matches.forEach { m ->
            val aOut = m.teamAId in withdrawnIds
            val bOut = m.teamBId in withdrawnIds
            val isAnnulledOrBye = m.isByeMatch ||
                (m.status == SpecMatchStatus.FORFEIT && m.sets.isEmpty() && m.winnerSide == null) ||
                ((aOut || bOut) && config.withdrawalPolicy == WithdrawalPolicy.ANNUL_ALL)
            if (isAnnulledOrBye) {
                nonCourtIds.add(m.id)
            } else if ((m.status == SpecMatchStatus.RUNNING || m.status == SpecMatchStatus.FINAL || m.status == SpecMatchStatus.FORFEIT) &&
                m.slotIndex != null && m.courtNumber != null
            ) {
                frozenIds.add(m.id)
            } else {
                schedulable.add(m)
            }
        }

        if (schedulable.isEmpty()) {
            val cleaned = matches.map { m ->
                if (m.id in nonCourtIds) m.copy(slotIndex = null, courtNumber = null) else m
            }
            val (minRest, b2b, makespan) = evaluateScheduleMetrics(cleaned)
            return ScheduleResult(cleaned, warnings, minRest, b2b, makespan)
        }

        val byId = matches.associateBy { it.id }
        val nonKoMaxFrozenSlot = matches
            .filter { !it.isKo && it.id in frozenIds }
            .mapNotNull { it.slotIndex }
            .maxOrNull() ?: -1

        // Fast integer indexing for teams & matches
        val allTeamIds = (teams.map { it.id } + matches.flatMap { listOfNotNull(it.teamAId, it.teamBId) }).distinct()
        val teamToIdx = allTeamIds.withIndex().associate { (i, id) -> id to i }
        val teamCount = allTeamIds.size

        // Dependency graph indices within schedulable + frozen
        val depIdsByMatch = matches.associate { m ->
            val deps = m.dependsOnMatchIds.toMutableSet()
            listOf(m.sourceA, m.sourceB).forEach { src ->
                when (src) {
                    is MatchSource.WinnerOf -> deps.add(src.matchId)
                    is MatchSource.LoserOf -> deps.add(src.matchId)
                    else -> Unit
                }
            }
            m.id to deps.filter { it in byId }
        }

        val minRestRequired = when (config.restStrictness) {
            RestStrictness.STRICT -> config.minRestSlots.coerceAtLeast(1)
            RestStrictness.BALANCED -> config.minRestSlots.coerceAtLeast(1)
            RestStrictness.FAST -> 0
        }

        // Group phase completion barrier: KO matches cannot be scheduled before all non-KO matches
        val hasGroupMatches = matches.any { !it.isKo && it.id !in nonCourtIds }

        // Frozen occupancy
        val frozenSlotById = matches.filter { it.id in frozenIds }.associate { it.id to it.slotIndex!! }
        val frozenCourtById = matches.filter { it.id in frozenIds }.associate { it.id to it.courtNumber!! }

        // Topological ordering of schedulable matches (round-first + dependencies)
        val topoBase = topologicalSortMatches(schedulable, depIdsByMatch)

        val attemptCount = when {
            schedulable.size <= 4 -> 1
            schedulable.size <= 48 -> 200
            schedulable.size <= 120 -> 32
            else -> 8
        }
        var bestSlotAssign: Map<String, Int>? = null
        var bestCourtAssign: Map<String, Int>? = null
        var bestScore = Long.MAX_VALUE
        var bestStrictOk = false

        for (attempt in 0 until attemptCount) {
            val rng = Mulberry32(config.seed + attempt * 7919)
            val ordered = if (attempt == 0) {
                topoBase
            } else {
                // Perturb order within same phaseRound / non-KO vs KO while preserving dependency topological feasibility
                topologicalSortRandomized(schedulable, depIdsByMatch, rng)
            }

            // Greedy slot + court assignment
            val slotOf = frozenSlotById.toMutableMap()
            val courtOf = frozenCourtById.toMutableMap()
            val slotCourtUsed = HashMap<Int, BooleanArray>()
            val slotUsedCount = HashMap<Int, Int>()
            val teamSlots = Array(teamCount) { ArrayList<Int>(8) }
            val teamSlotSets = Array(teamCount) { HashSet<Int>(16) }
            var firstNonFullSlot = 0

            matches.filter { it.id in frozenIds }.forEach { fm ->
                val s = fm.slotIndex!!
                val crt = (fm.courtNumber!! - 1).coerceIn(0, c - 1)
                val arr = slotCourtUsed.getOrPut(s) { BooleanArray(c) }
                if (!arr[crt]) {
                    arr[crt] = true
                    slotUsedCount[s] = (slotUsedCount[s] ?: 0) + 1
                }
                fm.teamAId?.let { teamToIdx[it] }?.let {
                    teamSlots[it].add(s)
                    teamSlotSets[it].add(s)
                }
                fm.teamBId?.let { teamToIdx[it] }?.let {
                    teamSlots[it].add(s)
                    teamSlotSets[it].add(s)
                }
            }
            while ((slotUsedCount[firstNonFullSlot] ?: 0) >= c) {
                firstNonFullSlot++
            }

            var maxNonKoSlot = nonKoMaxFrozenSlot

            // First schedule non-KO, then KO (so KO always respects maxNonKoSlot)
            val phaseOrdered = ordered.sortedBy { if (it.isKo) 1 else 0 }
            var strictBroken = false

            for (m in phaseOrdered) {
                var minAllowedSlot = 0
                depIdsByMatch[m.id].orEmpty().forEach { depId ->
                    val depSlot = slotOf[depId]
                    if (depSlot != null) {
                        minAllowedSlot = maxOf(minAllowedSlot, depSlot + 1)
                    }
                }
                if (m.isKo && hasGroupMatches && maxNonKoSlot >= 0) {
                    minAllowedSlot = maxOf(minAllowedSlot, maxNonKoSlot + 1)
                }

                val idxA = m.teamAId?.let { teamToIdx[it] }
                val idxB = m.teamBId?.let { teamToIdx[it] }

                // Find best feasible slot starting from max(minAllowedSlot, firstNonFullSlot)
                var chosenSlot = -1
                var chosenCourt = 0
                var bestLocalPenalty = Long.MAX_VALUE

                val startSlot = maxOf(minAllowedSlot, firstNonFullSlot)
                val searchCap = startSlot + maxOf(12, minOf(64, schedulable.size + 4))
                for (s in startSlot..searchCap) {
                    if ((slotUsedCount[s] ?: 0) >= c) continue
                    val courtsArr = slotCourtUsed.getOrPut(s) { BooleanArray(c) }
                    val freeCourt = courtsArr.indexOfFirst { !it }
                    if (freeCourt < 0) continue

                    // H1: check team conflict in same slot s (O(1))
                    val clashA = idxA != null && teamSlotSets[idxA].contains(s)
                    val clashB = idxB != null && teamSlotSets[idxB].contains(s)
                    if (clashA || clashB) continue

                    if (config.restStrictness == RestStrictness.FAST) {
                        // In FAST mode, s*100 dominates b2b*10, so the earliest valid slot is strictly optimal
                        chosenSlot = s
                        chosenCourt = freeCourt + 1
                        break
                    }

                    // Check rest violation against already placed slots
                    var b2bDelta = 0
                    var restViolationDelta = 0
                    fun evalTeamRest(tIdx: Int?) {
                        if (tIdx == null) return
                        for (prevS in teamSlots[tIdx]) {
                            val gap = abs(s - prevS) - 1
                            if (gap == 0) b2bDelta++
                            if (gap < minRestRequired) restViolationDelta += (minRestRequired - gap)
                        }
                    }
                    evalTeamRest(idxA)
                    evalTeamRest(idxB)

                    if (config.restStrictness == RestStrictness.STRICT && restViolationDelta > 0) {
                        // In STRICT mode, keep searching later slots before allowing violation
                        continue
                    }

                    val penalty = when (config.restStrictness) {
                        RestStrictness.FAST -> s.toLong() * 100L + b2bDelta.toLong() * 10L
                        RestStrictness.BALANCED -> restViolationDelta.toLong() * 10_000L + b2bDelta.toLong() * 5_000L + s.toLong() * 10L
                        RestStrictness.STRICT -> s.toLong() * 10L
                    }

                    if (penalty < bestLocalPenalty) {
                        bestLocalPenalty = penalty
                        chosenSlot = s
                        chosenCourt = freeCourt + 1
                        if (restViolationDelta == 0 && b2bDelta == 0) {
                            // Earliest clean slot with zero penalty found!
                            break
                        }
                    }
                }

                // If STRICT had no zero-rest slot within searchCap, relax minimally
                if (chosenSlot < 0) {
                    strictBroken = true
                    var s = startSlot
                    while (true) {
                        if ((slotUsedCount[s] ?: 0) < c) {
                            val courtsArr = slotCourtUsed.getOrPut(s) { BooleanArray(c) }
                            val freeCourt = courtsArr.indexOfFirst { !it }
                            val clashA = idxA != null && teamSlotSets[idxA].contains(s)
                            val clashB = idxB != null && teamSlotSets[idxB].contains(s)
                            if (freeCourt >= 0 && !clashA && !clashB) {
                                chosenSlot = s
                                chosenCourt = freeCourt + 1
                                break
                            }
                        }
                        s++
                    }
                }

                slotOf[m.id] = chosenSlot
                courtOf[m.id] = chosenCourt
                val chosenArr = slotCourtUsed.getOrPut(chosenSlot) { BooleanArray(c) }
                if (!chosenArr[chosenCourt - 1]) {
                    chosenArr[chosenCourt - 1] = true
                    slotUsedCount[chosenSlot] = (slotUsedCount[chosenSlot] ?: 0) + 1
                }
                while ((slotUsedCount[firstNonFullSlot] ?: 0) >= c) {
                    firstNonFullSlot++
                }
                idxA?.let {
                    teamSlots[it].add(chosenSlot)
                    teamSlotSets[it].add(chosenSlot)
                }
                idxB?.let {
                    teamSlots[it].add(chosenSlot)
                    teamSlotSets[it].add(chosenSlot)
                }
                if (!m.isKo && chosenSlot > maxNonKoSlot) {
                    maxNonKoSlot = chosenSlot
                }
            }

            // Compute objective score across entire schedule
            var totalB2B = 0L
            var totalRestPenalty = 0L
            var maxSlot = 0
            for (t in 0 until teamCount) {
                val list = teamSlots[t]
                if (list.size <= 1) continue
                list.sort()
                for (k in 0 until list.size - 1) {
                    val gap = list[k + 1] - list[k] - 1
                    if (gap == 0) totalB2B++
                    if (gap < minRestRequired) totalRestPenalty += (minRestRequired - gap)
                }
            }
            slotOf.values.forEach { if (it > maxSlot) maxSlot = it }

            val obj = when (config.restStrictness) {
                RestStrictness.FAST -> maxSlot.toLong() * 1000L + totalB2B * 50L
                else -> totalRestPenalty * 100_000L + totalB2B * 5_000L + maxSlot.toLong() * 10L
            }

            val strictOk = !strictBroken && totalRestPenalty == 0L
            if (bestSlotAssign == null ||
                (strictOk && !bestStrictOk) ||
                (strictOk == bestStrictOk && obj < bestScore)
            ) {
                bestScore = obj
                bestSlotAssign = slotOf
                bestCourtAssign = courtOf
                bestStrictOk = strictOk
                if (strictOk && totalB2B == 0L && attempt >= 5) {
                    // Already optimal zero-B2B schedule; still do tiny check if FAST
                    if (config.restStrictness != RestStrictness.FAST) break
                }
            }
        }

        val finalSlots = bestSlotAssign.orEmpty()
        val finalCourts = bestCourtAssign.orEmpty()
        val resultMatches = matches.map { m ->
            if (m.id in nonCourtIds) {
                m.copy(slotIndex = null, courtNumber = null)
            } else if (m.id in frozenIds) {
                m
            } else {
                m.copy(
                    slotIndex = finalSlots[m.id],
                    courtNumber = finalCourts[m.id]
                )
            }
        }.sortedWith(compareBy<SpecMatch> { it.slotIndex ?: Int.MAX_VALUE }.thenBy { it.courtNumber ?: Int.MAX_VALUE })

        val (minRestAchieved, b2bCount, makespan) = evaluateScheduleMetrics(resultMatches)

        if (config.restStrictness == RestStrictness.STRICT && minRestAchieved < minRestRequired && schedulable.size > 1) {
            warnings.add(
                TournamentWarning(
                    "P-03",
                    "Strikte Mindestpause (${minRestRequired} Slots) nicht für alle Begegnungen einhaltbar – bestmögliche Verteilung gewählt."
                )
            )
        } else if (b2bCount > 0 && config.restStrictness != RestStrictness.FAST) {
            warnings.add(
                TournamentWarning(
                    "P-03",
                    "$b2bCount Back-to-Back-Spiele ohne Pause bei $c Plätzen unvermeidbar oder minimiert."
                )
            )
        }

        return ScheduleResult(
            scheduledMatches = resultMatches,
            warnings = warnings,
            minRestAchieved = minRestAchieved,
            b2bCount = b2bCount,
            makespanSlots = makespan
        )
    }

    private fun topologicalSortMatches(
        matches: List<SpecMatch>,
        depsById: Map<String, List<String>>
    ): List<SpecMatch> {
        val byId = matches.associateBy { it.id }
        val inDegree = HashMap<String, Int>(matches.size)
        val children = HashMap<String, MutableList<String>>()
        matches.forEach { inDegree[it.id] = 0 }
        matches.forEach { m ->
            depsById[m.id].orEmpty().forEach { d ->
                if (d in byId) {
                    inDegree[m.id] = (inDegree[m.id] ?: 0) + 1
                    children.getOrPut(d) { mutableListOf() }.add(m.id)
                }
            }
        }
        val ready = matches.filter { (inDegree[it.id] ?: 0) == 0 }
            .sortedWith(compareBy<SpecMatch> { it.isKo }.thenBy { it.phaseRound }.thenBy { it.id })
            .toMutableList()
        val result = ArrayList<SpecMatch>(matches.size)
        while (ready.isNotEmpty()) {
            val cur = ready.removeAt(0)
            result.add(cur)
            children[cur.id].orEmpty().forEach { cid ->
                val nd = (inDegree[cid] ?: 0) - 1
                inDegree[cid] = nd
                if (nd == 0) {
                    byId[cid]?.let { ready.add(it) }
                    ready.sortWith(compareBy<SpecMatch> { it.isKo }.thenBy { it.phaseRound }.thenBy { it.id })
                }
            }
        }
        if (result.size < matches.size) {
            val seen = result.map { it.id }.toSet()
            result.addAll(matches.filter { it.id !in seen })
        }
        return result
    }

    private fun topologicalSortRandomized(
        matches: List<SpecMatch>,
        depsById: Map<String, List<String>>,
        rng: Mulberry32
    ): List<SpecMatch> {
        val byId = matches.associateBy { it.id }
        val inDegree = HashMap<String, Int>(matches.size)
        val children = HashMap<String, MutableList<String>>()
        matches.forEach { inDegree[it.id] = 0 }
        matches.forEach { m ->
            depsById[m.id].orEmpty().forEach { d ->
                if (d in byId) {
                    inDegree[m.id] = (inDegree[m.id] ?: 0) + 1
                    children.getOrPut(d) { mutableListOf() }.add(m.id)
                }
            }
        }
        val ready = matches.filter { (inDegree[it.id] ?: 0) == 0 }.toMutableList()
        val result = ArrayList<SpecMatch>(matches.size)
        while (ready.isNotEmpty()) {
            // Bias slightly towards lower phaseRound, with random tie-break
            val minPhase = ready.minOf { (if (it.isKo) 100 else 0) + it.phaseRound }
            val tier = ready.filter { ((if (it.isKo) 100 else 0) + it.phaseRound) <= minPhase + 1 }
            val pick = tier[rng.nextInt(tier.size)]
            ready.remove(pick)
            result.add(pick)
            children[pick.id].orEmpty().forEach { cid ->
                val nd = (inDegree[cid] ?: 0) - 1
                inDegree[cid] = nd
                if (nd == 0) byId[cid]?.let { ready.add(it) }
            }
        }
        if (result.size < matches.size) {
            val seen = result.map { it.id }.toSet()
            result.addAll(matches.filter { it.id !in seen })
        }
        return result
    }

    private fun evaluateScheduleMetrics(matches: List<SpecMatch>): Triple<Int, Int, Int> {
        val slotsByTeam = HashMap<String, MutableList<Int>>()
        var maxSlot = -1
        matches.forEach { m ->
            val s = m.slotIndex ?: return@forEach
            if (m.isByeMatch) return@forEach
            if (s > maxSlot) maxSlot = s
            m.teamAId?.let { slotsByTeam.getOrPut(it) { mutableListOf() }.add(s) }
            m.teamBId?.let { slotsByTeam.getOrPut(it) { mutableListOf() }.add(s) }
        }
        var minRest = Int.MAX_VALUE
        var b2b = 0
        slotsByTeam.values.forEach { list ->
            if (list.size >= 2) {
                list.sort()
                for (i in 0 until list.size - 1) {
                    val gap = list[i + 1] - list[i] - 1
                    if (gap < minRest) minRest = gap
                    if (gap == 0) b2b++
                }
            }
        }
        return Triple(if (minRest == Int.MAX_VALUE) 0 else minRest, b2b, maxSlot + 1)
    }

    /**
     * R-01..R-06: Optimale, faire Schiedsrichter-Zuweisung pro Slot.
     * - R-01: Modus OFF / EXTERNAL -> keine Team-SR (löscht nur ungesperrte automatische SR).
     * - R-02: Harte Regeln: nie Teilnehmer, nie Doppelbelegung im Slot (weder spielend noch zweites SR), nur present && !withdrawn.
     * - R-03: Lexikographische Kosten: (playsNextSlot, refCount, sameClubOrGroup, playedPrevSlot / loserBonus, seed).
     * - R-04: K.-o.-Spiele ohne vollständig aufgelöste Teilnehmer bleiben ohne SR bis zur Auflösung.
     * - R-05: refereeManualLocked == true wird niemals überschrieben.
     * - R-06: Fairness-Bericht (max - min).
     */
    fun assignReferees(
        matches: List<SpecMatch>,
        teams: List<SpecTeam>,
        config: SpecTournamentConfig
    ): Triple<List<SpecMatch>, RefereeFairnessReport, List<TournamentWarning>> {
        val warnings = mutableListOf<TournamentWarning>()
        val teamById = teams.associateBy { it.id }

        if (config.refereeMode == RefereeMode.OFF || config.refereeMode == RefereeMode.EXTERNAL) {
            val cleared = matches.map { m ->
                if (m.refereeManualLocked) m else m.copy(refereeTeamIds = emptyList())
            }
            val report = computeRefereeFairnessReport(cleared, teams, warnings)
            return Triple(cleared, report, warnings)
        }

        val activeCandidates = teams.filter { it.present && !it.withdrawn }
        val rng = Mulberry32(config.seed + 997)
        val rngRank = activeCandidates.associate { it.id to rng.nextInt(100_000) }

        val matchesBySlot = matches.filter { !it.isByeMatch && it.slotIndex != null }
            .groupBy { it.slotIndex!! }
            .toSortedMap()

        val playingInSlot = HashMap<Int, Set<String>>()
        val refCount = teams.associate { it.id to 0 }.toMutableMap()
        val lastRefSlot = mutableMapOf<String, Int>()
        val updatedById = matches.associateBy { it.id }.toMutableMap()

        matchesBySlot.forEach { (slot, slotMatches) ->
            val playing = slotMatches.flatMap { listOfNotNull(it.teamAId, it.teamBId) }.toSet()
            playingInSlot[slot] = playing
        }

        // Pre-count locked referees so fairness accounts for manual overrides
        matches.forEach { m ->
            if (m.refereeManualLocked) {
                m.refereeTeamIds.forEach { tid ->
                    refCount[tid] = (refCount[tid] ?: 0) + 1
                }
            }
        }

        val reqPerMatch = config.refTeamsPerMatch.coerceIn(1, 2)

        for ((slot, slotMatches) in matchesBySlot) {
            val playingNow = playingInSlot[slot].orEmpty()
            val playingNext = playingInSlot[slot + 1].orEmpty()
            val playingPrev = playingInSlot[slot - 1].orEmpty()
            val prevLosersOnCourt: Map<Int, String> = if (slot > 0) {
                matchesBySlot[slot - 1].orEmpty().mapNotNull { pm ->
                    if (pm.courtNumber != null && (pm.status == SpecMatchStatus.FINAL || pm.status == SpecMatchStatus.FORFEIT)) {
                        val loser = when (pm.winnerSide) {
                            TeamSide.TEAM_A -> pm.teamBId
                            TeamSide.TEAM_B -> pm.teamAId
                            null -> null
                        }
                        if (loser != null) pm.courtNumber to loser else null
                    } else null
                }.toMap()
            } else emptyMap()

            val lockedRefUsedInSlot = slotMatches
                .filter { it.refereeManualLocked }
                .flatMap { it.refereeTeamIds }
                .toMutableSet()

            // Clear non-locked matches that aren't ready (R-04) or reset before re-assigning
            val toAssign = mutableListOf<SpecMatch>()
            for (m in slotMatches) {
                if (m.refereeManualLocked) continue
                // R-04: Unresolved KO teams (or FINAL already locked historically? Only update SCHEDULED or empty FINAL if unlocked)
                if (m.teamAId == null || m.teamBId == null) {
                    updatedById[m.id] = m.copy(refereeTeamIds = emptyList())
                    continue
                }
                if (m.status == SpecMatchStatus.RUNNING || m.status == SpecMatchStatus.FINAL || m.status == SpecMatchStatus.FORFEIT) {
                    // Keep existing refs on already-started/completed matches
                    m.refereeTeamIds.forEach { tid ->
                        lockedRefUsedInSlot.add(tid)
                        refCount[tid] = (refCount[tid] ?: 0) + 1
                    }
                    continue
                }
                toAssign.add(m)
            }

            if (toAssign.isEmpty()) continue

            val usedRefsInSlot = lockedRefUsedInSlot.toMutableSet()

            // Assign optimally per slot; first pass primary ref (0), second pass optional assistant (1) if reqPerMatch == 2
            val assignedPerMatch = toAssign.associate { it.id to mutableListOf<String>() }.toMutableMap()

            for (pass in 0 until reqPerMatch) {
                for (m in toAssign) {
                    val clubA = teamById[m.teamAId]?.clubId?.trim()?.lowercase().orEmpty()
                    val clubB = teamById[m.teamBId]?.clubId?.trim()?.lowercase().orEmpty()

                    val legal = activeCandidates.filter { cand ->
                        // R-02: hard rules
                        cand.id != m.teamAId &&
                            cand.id != m.teamBId &&
                            cand.id !in playingNow &&
                            cand.id !in usedRefsInSlot
                    }

                    if (legal.isEmpty()) {
                        if (pass == 0) {
                            warnings.add(
                                TournamentWarning(
                                    "R-02",
                                    "Slot ${slot + 1} (${m.roundLabel}): Kein freies Team für Schiedsrichtereinsatz verfügbar."
                                )
                            )
                        }
                        continue
                    }

                    val best = legal.minWithOrNull { c1, c2 ->
                        fun costTuple(t: SpecTeam): Long {
                            val playsNext = if (t.id in playingNext) 1L else 0L
                            val rCount = (refCount[t.id] ?: 0).toLong()
                            val candClub = t.clubId.trim().lowercase()
                            val sameClub = if (candClub.isNotBlank() && (candClub == clubA || candClub == clubB)) 1L else 0L
                            val isLoserPreferred = if (config.refereeMode == RefereeMode.LOSER_REFS &&
                                m.courtNumber != null && prevLosersOnCourt[m.courtNumber] == t.id
                            ) 0L else 1L
                            val playedPrev = if (t.id in playingPrev) 1L else 0L
                            val refBackToBack = if (lastRefSlot[t.id] == slot - 1) 1L else 0L

                            return when (config.refereeMode) {
                                RefereeMode.LOSER_REFS ->
                                    isLoserPreferred * 100_000_000L +
                                        playsNext * 10_000_000L +
                                        rCount * 100_000L +
                                        sameClub * 10_000L +
                                        refBackToBack * 1_000L +
                                        (rngRank[t.id] ?: 0)
                                else -> // AUTO_FAIR
                                    playsNext * 100_000_000L +
                                        rCount * 1_000_000L +
                                        sameClub * 10_000L +
                                        playedPrev * 1_000L +
                                        refBackToBack * 500L +
                                        (rngRank[t.id] ?: 0)
                            }
                        }
                        val c = costTuple(c1).compareTo(costTuple(c2))
                        if (c != 0) c else c1.id.compareTo(c2.id)
                    }

                    if (best != null) {
                        assignedPerMatch[m.id]!!.add(best.id)
                        usedRefsInSlot.add(best.id)
                        refCount[best.id] = (refCount[best.id] ?: 0) + 1
                        lastRefSlot[best.id] = slot
                    }
                }
            }

            toAssign.forEach { m ->
                updatedById[m.id] = m.copy(refereeTeamIds = assignedPerMatch[m.id].orEmpty())
            }
        }

        val finalMatches = matches.map { updatedById[it.id] ?: it }
        val report = computeRefereeFairnessReport(finalMatches, teams, warnings)
        return Triple(finalMatches, report, warnings)
    }

    /**
     * R-02 + R-05: Prüft einen manuellen SR-Wechsel auf Regelkonformität (nie Teilnehmer / Doppelbelegung).
     */
    fun validateManualRefereeChange(
        targetMatch: SpecMatch,
        newRefTeamId: String,
        allMatches: List<SpecMatch>,
        teams: List<SpecTeam>
    ): String? {
        val team = teams.find { it.id == newRefTeamId } ?: return "Team nicht gefunden."
        if (!team.present || team.withdrawn) return "${team.name} ist nicht anwesend oder zurückgezogen."
        if (newRefTeamId == targetMatch.teamAId || newRefTeamId == targetMatch.teamBId) {
            return "${team.name} spielt in diesem Match selbst (R-02)."
        }
        val slot = targetMatch.slotIndex
        if (slot != null) {
            val conflictMatch = allMatches.firstOrNull { m ->
                m.id != targetMatch.id && m.slotIndex == slot && !m.isByeMatch &&
                    (m.teamAId == newRefTeamId || m.teamBId == newRefTeamId || newRefTeamId in m.refereeTeamIds)
            }
            if (conflictMatch != null) {
                return "${team.name} ist in Slot ${slot + 1} bereits als Spieler oder SR eingeteilt (${conflictMatch.roundLabel})."
            }
        }
        return null
    }

    fun computeRefereeFairnessReport(
        matches: List<SpecMatch>,
        teams: List<SpecTeam>,
        warnings: List<TournamentWarning>
    ): RefereeFairnessReport {
        val counts = teams.filter { it.present && !it.withdrawn }.associate { it.id to 0 }.toMutableMap()
        matches.filter { !it.isByeMatch }.forEach { m ->
            m.refereeTeamIds.forEach { rid ->
                if (rid in counts) counts[rid] = (counts[rid] ?: 0) + 1
            }
        }
        val maxC = counts.values.maxOrNull() ?: 0
        val minC = counts.values.minOrNull() ?: 0
        return RefereeFairnessReport(
            assignmentsByTeam = counts,
            maxCount = maxC,
            minCount = minC,
            spread = maxC - minC,
            warningCount = warnings.size
        )
    }

    /**
     * A6: Abgeleitete Teamstatistiken (Spiele, Punkte, Ø Punkte/Satz ganzzahlig formatiert,
     * längste Siegesserie, Min/Avg/Max Pause, Back-to-Back, SR-Einsätze, Buchholz).
     */
    fun computeDerivedTeamStats(
        teams: List<SpecTeam>,
        matches: List<SpecMatch>,
        config: SpecTournamentConfig
    ): List<DerivedTeamStats> {
        val overallRows = SpecScoring.calculateStandings(teams, matches, config, groupId = "")
        val rowById = overallRows.associateBy { it.teamId }

        val orderedMatches = matches.filter { !it.isByeMatch }
            .sortedWith(compareBy<SpecMatch> { it.slotIndex ?: Int.MAX_VALUE }.thenBy { it.phaseRound }.thenBy { it.id })

        return teams.map { team ->
            val row = rowById[team.id]
            val teamMatches = orderedMatches.filter { it.teamAId == team.id || it.teamBId == team.id }
            val finished = teamMatches.filter { it.status == SpecMatchStatus.FINAL || it.status == SpecMatchStatus.FORFEIT }

            val totalSetsPlayed = (row?.setsWon ?: 0) + (row?.setsLost ?: 0)
            val totalBallsScored = row?.pointsFor ?: 0
            // Integer X.Y formatting without Float/Double in domain state
            val avgBallsX10 = if (totalSetsPlayed > 0) (totalBallsScored * 10 + totalSetsPlayed / 2) / totalSetsPlayed else 0
            val avgStr = "${avgBallsX10 / 10}.${avgBallsX10 % 10}"

            var streak = 0
            var maxStreak = 0
            finished.forEach { m ->
                val won = (m.teamAId == team.id && m.winnerSide == TeamSide.TEAM_A) ||
                    (m.teamBId == team.id && m.winnerSide == TeamSide.TEAM_B)
                if (won) {
                    streak++
                    if (streak > maxStreak) maxStreak = streak
                } else {
                    streak = 0
                }
            }

            val slots = teamMatches.mapNotNull { it.slotIndex }.sorted()
            val gaps = if (slots.size >= 2) (0 until slots.size - 1).map { (slots[it + 1] - slots[it] - 1).coerceAtLeast(0) } else emptyList()
            val minRest = gaps.minOrNull() ?: 0
            val maxRest = gaps.maxOrNull() ?: 0
            val avgRestX10 = if (gaps.isNotEmpty()) (gaps.sum() * 10 + gaps.size / 2) / gaps.size else 0
            val b2b = gaps.count { it == 0 }

            val refDuties = matches.count { !it.isByeMatch && team.id in it.refereeTeamIds }

            DerivedTeamStats(
                teamId = team.id,
                teamName = team.name,
                played = row?.matchesPlayed ?: 0,
                wins = row?.wins ?: 0,
                losses = row?.losses ?: 0,
                matchPoints = row?.matchPoints ?: 0,
                setsWon = row?.setsWon ?: 0,
                setsLost = row?.setsLost ?: 0,
                pointsFor = row?.pointsFor ?: 0,
                pointsAgainst = row?.pointsAgainst ?: 0,
                avgPointsPerSet = avgStr,
                longestWinStreak = maxStreak,
                minRestSlots = minRest,
                avgRestSlotsX10 = avgRestX10,
                maxRestSlots = maxRest,
                b2bCount = b2b,
                refDuties = refDuties,
                buchholz = row?.buchholz ?: 0
            )
        }
    }
}
