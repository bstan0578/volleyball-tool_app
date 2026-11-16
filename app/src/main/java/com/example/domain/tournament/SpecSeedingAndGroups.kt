package com.example.domain.tournament

// ergänzt gemäß SPEC T-01..T-05, W-08

/**
 * Pure domain team validation, deterministic seeding, exact serpentine distribution,
 * club separation swap pass, and cross-group comparison (SPEC T-01..T-05, W-08).
 */
object SpecSeedingAndGroups {

    /**
     * T-01: Validiert Teams auf eindeutigen, nicht-leeren Namen (case-insensitiv, 1-40 Zeichen)
     * sowie gültiges Skill-Level (1..100).
     */
    fun validateTeams(teams: List<SpecTeam>): List<TournamentWarning> {
        val warnings = mutableListOf<TournamentWarning>()
        val seenNames = mutableSetOf<String>()
        teams.forEach { t ->
            val trimmed = t.name.trim()
            if (trimmed.isEmpty() || trimmed.length > 40) {
                warnings.add(TournamentWarning("T-01", "Teamname '${t.name}' muss 1–40 Zeichen lang sein.", isError = true))
            }
            val key = trimmed.lowercase()
            if (!seenNames.add(key)) {
                warnings.add(TournamentWarning("T-01", "Teamname '${t.name}' ist doppelt vorhanden.", isError = true))
            }
            if (t.skillLevel !in 1..100) {
                warnings.add(TournamentWarning("T-01", "Skill von '${t.name}' muss zwischen 1 und 100 liegen.", isError = true))
            }
        }
        return warnings
    }

    /**
     * T-02: Deterministische Setzliste = (-skillLevel, Anmeldereihenfolge, seeded RNG).
     * Wenn manualSeedOrder eine gültige Permutation aller Team-IDs ist, gilt diese Reihenfolge.
     */
    fun buildSeedOrder(
        teams: List<SpecTeam>,
        seed: Int,
        manualSeedOrder: List<String> = emptyList()
    ): Pair<List<SpecTeam>, List<TournamentWarning>> {
        val warnings = mutableListOf<TournamentWarning>()
        val activeIds = teams.map { it.id }.toSet()
        if (manualSeedOrder.isNotEmpty()) {
            if (manualSeedOrder.size == teams.size && manualSeedOrder.toSet() == activeIds) {
                val byId = teams.associateBy { it.id }
                return manualSeedOrder.mapNotNull { byId[it] } to warnings
            } else {
                warnings.add(
                    TournamentWarning(
                        "T-02",
                        "Manuelle Setzliste ist keine gültige Permutation aller Teams – automatische Setzliste verwendet.",
                        isError = false
                    )
                )
            }
        }

        // Tie-breaker deterministic via Mulberry32 on seed
        val rng = Mulberry32(seed)
        val rngTie = teams.associate { it.id to rng.nextInt(1_000_000) }
        val sorted = teams.sortedWith { a, b ->
            val sk = b.skillLevel.compareTo(a.skillLevel) // -skillLevel (höher = besser = früherer Seed)
            if (sk != 0) return@sortedWith sk
            val reg = a.registrationOrder.compareTo(b.registrationOrder)
            if (reg != 0) return@sortedWith reg
            val rA = rngTie[a.id] ?: 0
            val rB = rngTie[b.id] ?: 0
            if (rA != rB) rA.compareTo(rB) else a.id.compareTo(b.id)
        }
        return sorted to warnings
    }

    /**
     * T-03: Exakte Serpentine-Formel:
     * groupOf(R0, G) = { row = R0 / G; k = R0 % G; return if (row % 2 == 0) k else G - 1 - k }
     * 0-basierter Seed-Rang R0 -> 0-basierter Gruppenindex 0..G-1.
     */
    fun groupOf(rankZeroBased: Int, groupsCount: Int): Int {
        require(groupsCount > 0) { "groupsCount must be positive" }
        val row = rankZeroBased / groupsCount
        val k = rankZeroBased % groupsCount
        return if (row % 2 == 0) k else (groupsCount - 1 - k)
    }

    /**
     * T-03 + T-04 + T-05: Gruppenverteilung per Serpentine + optionaler Vereinstrennungs-Tauschpass
     * innerhalb derselben Stufe (row) ohne T-03-Regeln (Größendifferenz <= 1, Seed 1 & 2 getrennt) zu verletzen.
     */
    fun distributeToGroups(
        seededTeams: List<SpecTeam>,
        groupsCount: Int,
        separateClubs: Boolean = true
    ): Pair<Map<String, List<SpecTeam>>, List<TournamentWarning>> {
        val g = groupsCount.coerceAtLeast(1)
        val groupNames = List(g) { ('A' + it).toString() }
        val buckets = groupNames.associateWith { mutableListOf<SpecTeam>() }.toMutableMap()
        val warnings = mutableListOf<TournamentWarning>()

        if (seededTeams.isEmpty()) return buckets to warnings

        // 1. Exact T-03 Serpentine assignment (preserves tier row)
        val teamByRow = seededTeams.mapIndexed { r0, team ->
            val row = r0 / g
            val gIdx = groupOf(r0, g)
            val gName = groupNames[gIdx]
            buckets[gName]!!.add(team)
            Triple(row, gName, team)
        }

        // 2. T-04: Club separation swap pass WITHIN same tier (row) to keep serpentine balance intact
        // Never swaps Row 0 if G >= 2 so Seed 1 & Seed 2 remain guaranteed in different top groups,
        // wait: swapping within row 0 only swaps within row 0 anyway (all in row 0 are in distinct groups)!
        // Actually swapping two teams in the SAME row keeps every team in its seed tier and preserves group sizes <= 1 diff!
        if (separateClubs && g >= 2) {
            fun groupClubConflictCount(gName: String, clubId: String): Int {
                if (clubId.isBlank()) return 0
                return buckets[gName]!!.count { it.clubId.equals(clubId, ignoreCase = true) }
            }

            val maxRow = (seededTeams.size - 1) / g
            for (row in 1..maxRow) {
                val rowStart = row * g
                val rowEnd = minOf(seededTeams.size, rowStart + g)
                val rowGroups = (rowStart until rowEnd).map { r0 -> groupNames[groupOf(r0, g)] }
                for (i in rowGroups.indices) {
                    val gA = rowGroups[i]
                    val tA = buckets[gA]?.getOrNull(row) ?: continue
                    if (tA.clubId.isBlank()) continue
                    if (groupClubConflictCount(gA, tA.clubId) <= 1) continue

                    // Look for swap partner in same tier (row) in another group that reduces total club conflicts
                    var bestPartnerIdx: Int? = null
                    for (j in rowGroups.indices) {
                        if (i == j) continue
                        val gB = rowGroups[j]
                        val tB = buckets[gB]?.getOrNull(row) ?: continue
                        val currentConflicts = groupClubConflictCount(gA, tA.clubId) + groupClubConflictCount(gB, tB.clubId)
                        // Simulate conflict after swapping tA and tB
                        val newConfA = buckets[gA]!!.count { it.id != tA.id && tB.clubId.isNotBlank() && it.clubId.equals(tB.clubId, ignoreCase = true) } +
                            if (tB.clubId.isNotBlank()) 1 else 0
                        val newConfB = buckets[gB]!!.count { it.id != tB.id && it.clubId.equals(tA.clubId, ignoreCase = true) } + 1
                        if (newConfA + newConfB < currentConflicts) {
                            bestPartnerIdx = j
                            break
                        }
                    }
                    if (bestPartnerIdx != null) {
                        val gB = rowGroups[bestPartnerIdx]
                        val tB = buckets[gB]!![row]
                        buckets[gA]!![row] = tB
                        buckets[gB]!![row] = tA
                    }
                }
            }

            // Warn if any club conflict remains unresolvable
            val unresolvedClubs = buckets.values.flatMap { grp ->
                grp.filter { it.clubId.isNotBlank() }
                    .groupBy { it.clubId.trim().lowercase() }
                    .filter { it.value.size > 1 }
                    .keys
            }.distinct()
            if (unresolvedClubs.isNotEmpty()) {
                warnings.add(
                    TournamentWarning(
                        "T-04",
                        "Vereinstrennung nicht für alle Vereine vollständig lösbar (${unresolvedClubs.joinToString(", ")})."
                    )
                )
            }
        }

        // 3. T-05: Group size checks (>= 2 required; size 3 triggers warning)
        buckets.forEach { (gName, list) ->
            if (list.size < 2) {
                warnings.add(TournamentWarning("T-05", "Gruppe $gName hat weniger als 2 Teams (${list.size}).", isError = true))
            } else if (list.size == 3) {
                warnings.add(TournamentWarning("T-05", "Gruppe $gName ist eine 3er-Gruppe (bei ungerader Anzahl häufig längere Wartezeiten)."))
            }
        }

        return buckets to warnings
    }

    /**
     * W-08: Gruppenvergleich (z. B. beste Gruppendritte oder Wildcard-Vergleich) bei ggf. ungleicher Gruppengröße.
     * DROP_LAST (Default): Ergebnisse gegen den jeweiligen Gruppenletzten werden gestrichen, wenn Gruppen ungleich groß sind.
     * PER_GAME: Normiert Punkte pro Spiel mittels Ganzzahl-Kreuzmultiplikation.
     */
    fun compareAcrossGroups(
        groupStandings: Map<String, List<SpecStandingRow>>,
        targetRankInGroup: Int,
        teams: List<SpecTeam>,
        matches: List<SpecMatch>,
        config: SpecTournamentConfig
    ): List<SpecStandingRow> {
        val candidates = groupStandings.keys.sorted().mapNotNull { gName ->
            val list = groupStandings[gName].orEmpty()
            list.getOrNull(targetRankInGroup - 1)?.let { gName to it }
        }
        if (candidates.isEmpty()) return emptyList()

        val groupSizes = groupStandings.values.map { it.size }
        val minSize = groupSizes.minOrNull() ?: 0
        val maxSize = groupSizes.maxOrNull() ?: 0
        val unequalSizes = minSize != maxSize
        val teamById = teams.associateBy { it.id }

        val normalizedRows = if (unequalSizes && config.groupComparisonPolicy == GroupComparisonPolicy.DROP_LAST) {
            // Recompute each candidate's standing after dropping matches against the last-placed team(s) of larger groups
            candidates.map { (gName, origRow) ->
                val gStandings = groupStandings[gName].orEmpty()
                if (gStandings.size > minSize) {
                    val dropCount = gStandings.size - minSize
                    val droppedIds = gStandings.takeLast(dropCount).map { it.teamId }.toSet()
                    val filteredTeams = gStandings.filter { it.teamId !in droppedIds }.mapNotNull { teamById[it.teamId] }
                    val filteredMatches = matches.filter { m ->
                        m.groupId == gName && m.teamAId !in droppedIds && m.teamBId !in droppedIds
                    }
                    SpecScoring.calculateStandings(
                        teams = filteredTeams,
                        matches = filteredMatches,
                        config = config,
                        groupId = gName
                    ).firstOrNull { it.teamId == origRow.teamId } ?: origRow
                } else {
                    origRow
                }
            }
        } else {
            candidates.map { it.second }
        }

        val usePerGame = unequalSizes && config.groupComparisonPolicy == GroupComparisonPolicy.PER_GAME
        val sorted = normalizedRows.sortedWith { a, b ->
            // 1. MP (or MP / matchesPlayed via cross-multiplication if PER_GAME)
            val cMp = if (usePerGame) {
                SpecScoring.cmpQuot(b.matchPoints, b.matchesPlayed, a.matchPoints, a.matchesPlayed, config.zeroDivisorPolicy)
            } else {
                b.matchPoints.compareTo(a.matchPoints)
            }
            if (cMp != 0) return@sortedWith cMp

            // 2. Wins (or Wins / matchesPlayed)
            val cW = if (usePerGame) {
                SpecScoring.cmpQuot(b.wins, b.matchesPlayed, a.wins, a.matchesPlayed, config.zeroDivisorPolicy)
            } else {
                b.wins.compareTo(a.wins)
            }
            if (cW != 0) return@sortedWith cW

            // 3. Satzquot
            val cSq = SpecScoring.cmpQuot(b.setsWon, b.setsLost, a.setsWon, a.setsLost, config.zeroDivisorPolicy)
            if (cSq != 0) return@sortedWith cSq

            // 4. Ballquot
            val cBq = SpecScoring.cmpQuot(b.pointsFor, b.pointsAgainst, a.pointsFor, a.pointsAgainst, config.zeroDivisorPolicy)
            if (cBq != 0) return@sortedWith cBq

            // 5. Seed (höheres Skill-Level zuerst), dann Los/ID
            val skA = teamById[a.teamId]?.skillLevel ?: 50
            val skB = teamById[b.teamId]?.skillLevel ?: 50
            if (skB != skA) return@sortedWith skB.compareTo(skA)

            a.teamId.compareTo(b.teamId)
        }

        return sorted.mapIndexed { idx, row -> row.copy(rank = idx + 1) }
    }
}
