package com.example.domain.engine

import com.example.data.model.*
import java.util.UUID
import kotlin.math.roundToInt

data class TeamRating(
    val teamId: String,
    val averageSkill: Double,
    val verySmallCount: Int,
    val smallCount: Int,
    val mediumCount: Int,
    val largeCount: Int,
    val maleCount: Int,
    val femaleCount: Int,
    val diverseCount: Int,
    val totalPlayers: Int
)

data class MixerConfig(
    val format: FieldFormat,
    val sportType: SportType = SportType.ANY,
    val teamCount: Int,
    val autoBalance: Boolean = true,
    val minFemalesPerTeam: Int = 0,
    val minMalesPerTeam: Int = 0
)

data class MixerResult(
    val teams: List<Team>,
    val createdDummies: List<Player> = emptyList(),
    val errorMessage: String? = null
)

object TeamMixerEngine {

    const val DUMMY_NAME = "Dummy"

    fun createDummyPlayer(
        id: String = "dummy_${UUID.randomUUID()}",
        skill: Int = 3,
        heightClass: HeightClass = HeightClass.MEDIUM,
        gender: Gender = Gender.DIVERSE
    ): Player {
        return Player(
            id = id,
            name = DUMMY_NAME,
            skill = skill.coerceIn(1, 5),
            heightClass = heightClass,
            gender = gender,
            isDummy = true
        )
    }

    fun needsDummy(playerCount: Int, squadSize: Int): Boolean {
        if (squadSize <= 0) return false
        return playerCount % squadSize != 0
    }

    fun dummyCountNeeded(playerCount: Int, totalSlotsNeeded: Int): Int {
        if (totalSlotsNeeded <= playerCount) return 0
        return totalSlotsNeeded - playerCount
    }

    fun calculateTeamRating(team: Team, playerMap: Map<String, Player>): TeamRating {
        val players = team.playerIds.mapNotNull { playerMap[it] }
        val avgSkill = if (players.isNotEmpty()) players.sumOf { it.skill }.toDouble() / players.size else 0.0
        return TeamRating(
            teamId = team.id,
            averageSkill = avgSkill,
            verySmallCount = players.count { it.heightClass == HeightClass.VERY_SMALL },
            smallCount = players.count { it.heightClass == HeightClass.SMALL },
            mediumCount = players.count { it.heightClass == HeightClass.MEDIUM },
            largeCount = players.count { it.heightClass == HeightClass.LARGE },
            maleCount = players.count { it.gender == Gender.MALE },
            femaleCount = players.count { it.gender == Gender.FEMALE },
            diverseCount = players.count { it.gender == Gender.DIVERSE },
            totalPlayers = players.size
        )
    }

    /**
     * Pure function: List<Player> + MixerConfig -> MixerResult
     * Minimal-dummies policy:
     * 1. Distributes ALL genuine players across the requested teams first (respecting mixed rules & balancing).
     * 2. Fills only remaining open team slots with temporary Dummy players AFTER all real players are placed.
     */
    fun mix(
        inputPlayers: List<Player>,
        config: MixerConfig
    ): MixerResult {
        require(config.teamCount > 0) { "teamCount must be positive" }

        val squadSize = config.format.squadSize
        val fieldCount = config.format.fieldPlayerCount
        val totalSlots = config.teamCount * squadSize

        // 1. Filter out old dummies so we only start with genuine players
        val realPlayers = inputPlayers.filter { !it.isDummy }.map { it.copy() }

        // 2. Validate mixed constraints against genuine players
        val neededFemales = config.minFemalesPerTeam * config.teamCount
        val neededMales = config.minMalesPerTeam * config.teamCount
        val availableFemales = realPlayers.count { it.gender == Gender.FEMALE }
        val availableMales = realPlayers.count { it.gender == Gender.MALE }

        if (neededFemales > 0 && availableFemales < neededFemales) {
            return MixerResult(
                teams = emptyList(),
                createdDummies = emptyList(),
                errorMessage = "Mixed-Voraussetzung nicht erfüllt: Benötigt werden $neededFemales Spielerinnen (mind. ${config.minFemalesPerTeam} pro Team), aber es sind nur $availableFemales vorhanden."
            )
        }

        if (neededMales > 0 && availableMales < neededMales) {
            return MixerResult(
                teams = emptyList(),
                createdDummies = emptyList(),
                errorMessage = "Mixed-Voraussetzung nicht erfüllt: Benötigt werden $neededMales männliche Spieler (mind. ${config.minMalesPerTeam} pro Team), aber es sind nur $availableMales vorhanden."
            )
        }

        val teamBuckets = List(config.teamCount) { mutableListOf<Player>() }
        val realToAssign = realPlayers.take(totalSlots).toMutableList()

        // Step A: Distribute ALL genuine players onto teams first!
        if (!config.autoBalance) {
            // Round-robin distribution so all teams get real players first before any dummy is needed
            realToAssign.forEachIndexed { index, player ->
                val teamIdx = index % config.teamCount
                teamBuckets[teamIdx].add(player.copy())
            }
        } else {
            val remainingPool = realToAssign.toMutableList()

            // Satisfy female quota across teams using snake-draft sorted by skill desc so quotas stay skill-balanced
            if (config.minFemalesPerTeam > 0) {
                val femalePlayers = remainingPool.filter { it.gender == Gender.FEMALE }
                    .sortedWith(
                        compareByDescending<Player> { it.skill }
                            .thenByDescending { it.heightClass.ordinal }
                    )
                var fIdx = 0
                for (round in 0 until config.minFemalesPerTeam) {
                    val order = if (round % 2 == 0) teamBuckets.indices else teamBuckets.indices.reversed()
                    for (tIdx in order) {
                        val team = teamBuckets[tIdx]
                        if (fIdx < femalePlayers.size && team.size < squadSize) {
                            val f = femalePlayers[fIdx++]
                            team.add(f.copy())
                            remainingPool.remove(f)
                        }
                    }
                }
            }

            // Satisfy male quota across teams using snake-draft sorted by skill desc
            if (config.minMalesPerTeam > 0) {
                val malePlayers = remainingPool.filter { it.gender == Gender.MALE }
                    .sortedWith(
                        compareByDescending<Player> { it.skill }
                            .thenByDescending { it.heightClass.ordinal }
                    )
                var mIdx = 0
                for (round in 0 until config.minMalesPerTeam) {
                    val order = if (round % 2 == 0) teamBuckets.indices else teamBuckets.indices.reversed()
                    for (tIdx in order) {
                        val team = teamBuckets[tIdx]
                        if (mIdx < malePlayers.size && team.size < squadSize) {
                            val m = malePlayers[mIdx++]
                            team.add(m.copy())
                            remainingPool.remove(m)
                        }
                    }
                }
            }

            // Sort remaining real players primarily by skill desc, then height & gender
            val sortedRemaining = remainingPool.sortedWith(
                compareByDescending<Player> { it.skill }
                    .thenByDescending { it.heightClass.ordinal }
                    .thenBy { it.gender.ordinal }
            )

            // Phase 1: Initial distribution keeping sizes equal, prioritizing lowest average/total skill first, then gender & height
            for (player in sortedRemaining) {
                val candidateTeams = teamBuckets.indices.filter { teamBuckets[it].size < squadSize }
                if (candidateTeams.isEmpty()) break

                val bestTeamIdx = candidateTeams.minWithOrNull(
                    compareBy<Int> { idx -> teamBuckets[idx].size }
                        .thenBy { idx ->
                            val b = teamBuckets[idx]
                            (b.sumOf { it.skill } + player.skill).toDouble() / (b.size + 1)
                        }
                        .thenBy { idx -> teamBuckets[idx].sumOf { it.skill } }
                        .thenBy { idx -> teamBuckets[idx].count { it.gender == player.gender } }
                        .thenBy { idx -> teamBuckets[idx].count { it.heightClass == player.heightClass } }
                ) ?: candidateTeams.first()

                teamBuckets[bestTeamIdx].add(player.copy())
            }

            // Phase 2: Iterative swap optimization (Weighting: Skill >>> Gender & Height)
            optimizeTeamBucketsSkillFirst(
                teamBuckets = teamBuckets,
                minFemalesPerTeam = config.minFemalesPerTeam,
                minMalesPerTeam = config.minMalesPerTeam
            )
        }

        // Step B: Fill ONLY remaining empty slots with minimum necessary dummies
        val dummiesCreated = mutableListOf<Player>()
        val avgSkill = if (realToAssign.isNotEmpty()) {
            (realToAssign.sumOf { it.skill }.toDouble() / realToAssign.size).roundToInt().coerceIn(1, 5)
        } else 3

        teamBuckets.forEach { bucket ->
            while (bucket.size < squadSize) {
                val dummy = createDummyPlayer(
                    id = "dummy_${UUID.randomUUID()}",
                    skill = avgSkill,
                    heightClass = HeightClass.MEDIUM,
                    gender = Gender.DIVERSE
                )
                dummiesCreated.add(dummy)
                bucket.add(dummy)
            }
        }

        val teams = teamBuckets.mapIndexed { index, members ->
            Team(
                id = "team_${UUID.randomUUID()}",
                name = "Team ${index + 1}",
                sportType = config.sportType,
                fieldPlayerCount = fieldCount,
                squadSize = squadSize,
                playerIds = members.map { it.id },
                isFinalized = false
            )
        }

        return MixerResult(
            teams = teams,
            createdDummies = dummiesCreated,
            errorMessage = null
        )
    }

    fun balanceTeams(
        availablePlayers: List<Player>,
        format: FieldFormat,
        sportType: SportType,
        teamCount: Int
    ): List<Team> {
        val config = MixerConfig(format = format, sportType = sportType, teamCount = teamCount, autoBalance = true)
        return mix(availablePlayers, config).teams
    }

    fun swapPlayers(
        teams: List<Team>,
        teamAId: String,
        playerAId: String,
        teamBId: String,
        playerBId: String
    ): List<Team> {
        val teamA = teams.find { it.id == teamAId } ?: return teams
        val teamB = teams.find { it.id == teamBId } ?: return teams

        if (!teamA.playerIds.contains(playerAId) || !teamB.playerIds.contains(playerBId)) {
            return teams
        }

        val newPlayersA = teamA.playerIds.toMutableList()
        val newPlayersB = teamB.playerIds.toMutableList()

        val idxA = newPlayersA.indexOf(playerAId)
        val idxB = newPlayersB.indexOf(playerBId)

        newPlayersA[idxA] = playerBId
        newPlayersB[idxB] = playerAId

        return teams.map { team ->
            when (team.id) {
                teamAId -> team.copy(playerIds = newPlayersA, isFinalized = false)
                teamBId -> team.copy(playerIds = newPlayersB, isFinalized = false)
                else -> team
            }
        }
    }

    /**
     * Pairwise swap hill-climber maximizing Skill parity first (weight 1000.0),
     * then Gender balance (weight 10.0) and Height balance (weight 10.0),
     * while preserving mixed gender quotas.
     */
    private fun optimizeTeamBucketsSkillFirst(
        teamBuckets: List<MutableList<Player>>,
        minFemalesPerTeam: Int,
        minMalesPerTeam: Int
    ) {
        if (teamBuckets.size < 2) return
        val nonEmptyTeams = teamBuckets.count { it.isNotEmpty() }
        if (nonEmptyTeams < 2) return

        var currentCost = evaluateBalanceCost(teamBuckets)
        var improved = true
        var passes = 0

        while (improved && passes < 250) {
            improved = false
            passes++

            loop@ for (t1 in 0 until teamBuckets.size - 1) {
                for (t2 in t1 + 1 until teamBuckets.size) {
                    val b1 = teamBuckets[t1]
                    val b2 = teamBuckets[t2]
                    for (i1 in b1.indices) {
                        for (i2 in b2.indices) {
                            val p1 = b1[i1]
                            val p2 = b2[i2]
                            if (p1.skill == p2.skill && p1.gender == p2.gender && p1.heightClass == p2.heightClass) {
                                continue
                            }

                            // Check mixed quota validity if swapped
                            if (p1.gender != p2.gender) {
                                val f1After = b1.count { it.gender == Gender.FEMALE } -
                                    (if (p1.gender == Gender.FEMALE) 1 else 0) +
                                    (if (p2.gender == Gender.FEMALE) 1 else 0)
                                val f2After = b2.count { it.gender == Gender.FEMALE } -
                                    (if (p2.gender == Gender.FEMALE) 1 else 0) +
                                    (if (p1.gender == Gender.FEMALE) 1 else 0)
                                if (f1After < minFemalesPerTeam || f2After < minFemalesPerTeam) continue

                                val m1After = b1.count { it.gender == Gender.MALE } -
                                    (if (p1.gender == Gender.MALE) 1 else 0) +
                                    (if (p2.gender == Gender.MALE) 1 else 0)
                                val m2After = b2.count { it.gender == Gender.MALE } -
                                    (if (p2.gender == Gender.MALE) 1 else 0) +
                                    (if (p1.gender == Gender.MALE) 1 else 0)
                                if (m1After < minMalesPerTeam || m2After < minMalesPerTeam) continue
                            }

                            b1[i1] = p2
                            b2[i2] = p1
                            val nextCost = evaluateBalanceCost(teamBuckets)
                            if (nextCost + 1e-6 < currentCost) {
                                currentCost = nextCost
                                improved = true
                                break@loop
                            } else {
                                b1[i1] = p1
                                b2[i2] = p2
                            }
                        }
                    }
                }
            }
        }
    }

    private fun evaluateBalanceCost(teamBuckets: List<List<Player>>): Double {
        val activeBuckets = teamBuckets.filter { it.isNotEmpty() }
        if (activeBuckets.size < 2) return 0.0

        val n = activeBuckets.size.toDouble()
        val allPlayers = activeBuckets.flatten()
        val globalAvgSkill = allPlayers.sumOf { it.skill }.toDouble() / allPlayers.size
        val globalAvgTeamSkillSum = activeBuckets.sumOf { b -> b.sumOf { it.skill } }.toDouble() / n

        var skillCost = 0.0
        for (b in activeBuckets) {
            val avg = b.sumOf { it.skill }.toDouble() / b.size
            val diffAvg = avg - globalAvgSkill
            val diffSum = b.sumOf { it.skill }.toDouble() - globalAvgTeamSkillSum
            skillCost += (diffAvg * diffAvg) + 0.25 * (diffSum * diffSum)
        }

        // Gender distribution variance across teams
        var genderCost = 0.0
        for (g in Gender.values()) {
            val counts = activeBuckets.map { b -> b.count { it.gender == g }.toDouble() }
            val mean = counts.sum() / n
            genderCost += counts.sumOf { c -> (c - mean) * (c - mean) }
        }

        // Height distribution variance across teams (per class + average height ordinal)
        var heightCost = 0.0
        for (h in HeightClass.values()) {
            val counts = activeBuckets.map { b -> b.count { it.heightClass == h }.toDouble() }
            val mean = counts.sum() / n
            heightCost += counts.sumOf { c -> (c - mean) * (c - mean) }
        }
        val globalAvgHeight = allPlayers.sumOf { it.heightClass.ordinal }.toDouble() / allPlayers.size
        for (b in activeBuckets) {
            val avgH = b.sumOf { it.heightClass.ordinal }.toDouble() / b.size
            val diffH = avgH - globalAvgHeight
            heightCost += diffH * diffH
        }

        // High weight on skill balance first; gender and height balanced secondarily
        return (1000.0 * skillCost) + (10.0 * genderCost) + (10.0 * heightCost)
    }
}
