package com.example

import com.example.data.model.*
import com.example.domain.engine.CoinSide
import com.example.domain.engine.CoinTossEngine
import com.example.domain.engine.ScoreEngine
import com.example.domain.engine.MixerConfig
import com.example.domain.engine.TeamMixerEngine
import com.example.domain.engine.TournamentEngine
import com.example.domain.tournament.TournamentCertificateEngine
import org.junit.Assert.*
import org.junit.Test

class VolleyEnginesUnitTest {

    // =========================================================================
    // 1. TEAMMIXER & DUMMY LOGIC TESTS
    // =========================================================================

    @Test
    fun testDummyLogicCalculation() {
        // 13 players for 6v6 + 1 (squadSize 7, 2 teams = 14 total needed -> 1 dummy needed)
        val needed = TeamMixerEngine.dummyCountNeeded(13, 14)
        assertEquals(1, needed)
        assertTrue(TeamMixerEngine.needsDummy(13, 7))

        // 12 players for 6v6 (squadSize 6, 2 teams = 12 total -> 0 dummy needed)
        val neededExact = TeamMixerEngine.dummyCountNeeded(12, 12)
        assertEquals(0, neededExact)
        assertFalse(TeamMixerEngine.needsDummy(12, 6))

        // Create dummy verification
        val dummy = TeamMixerEngine.createDummyPlayer(skill = 4)
        assertTrue(dummy.isDummy)
        assertEquals("Dummy", dummy.name)
        assertEquals(4, dummy.skill)
    }

    @Test
    fun testTeammixerBalancingAndSnakeDraft() {
        // Pool of 8 players with diverse skills, 4 height classes, and genders for 2v2 (4 teams)
        val players = listOf(
            Player("1", "A", 5, HeightClass.LARGE, gender = Gender.MALE),
            Player("2", "B", 5, HeightClass.MEDIUM, gender = Gender.FEMALE),
            Player("3", "C", 4, HeightClass.LARGE, gender = Gender.MALE),
            Player("4", "D", 4, HeightClass.SMALL, gender = Gender.FEMALE),
            Player("5", "E", 3, HeightClass.VERY_SMALL, gender = Gender.DIVERSE),
            Player("6", "F", 3, HeightClass.SMALL, gender = Gender.FEMALE),
            Player("7", "G", 2, HeightClass.LARGE, gender = Gender.MALE),
            Player("8", "H", 2, HeightClass.MEDIUM, gender = Gender.MALE)
        )

        val teams = TeamMixerEngine.balanceTeams(
            availablePlayers = players,
            format = FieldFormat.FORMAT_2V2,
            sportType = SportType.BEACH,
            teamCount = 4
        )

        assertEquals(4, teams.size)
        teams.forEach { team ->
            assertEquals(2, team.squadSize)
            assertEquals(2, team.fieldPlayerCount)
            assertEquals(2, team.playerIds.size)
            assertFalse(team.isFinalized)
        }

        // Test manual player swap between Team 1 and Team 2
        val team1Player = teams[0].playerIds[0]
        val team2Player = teams[1].playerIds[0]

        val swappedTeams = TeamMixerEngine.swapPlayers(
            teams = teams,
            teamAId = teams[0].id,
            playerAId = team1Player,
            teamBId = teams[1].id,
            playerBId = team2Player
        )

        assertTrue(swappedTeams[0].playerIds.contains(team2Player))
        assertTrue(swappedTeams[1].playerIds.contains(team1Player))
    }

    @Test
    fun testRepeatedMixDoesNotCrashAndProducesNoDuplicatePlayerIds() {
        val playerList = listOf(
            Player("p1", "Anna", 5, HeightClass.LARGE, gender = Gender.FEMALE),
            Player("p2", "Ben", 4, HeightClass.MEDIUM, gender = Gender.MALE),
            Player("p3", "Clara", 3, HeightClass.SMALL, gender = Gender.FEMALE),
            Player("p4", "David", 4, HeightClass.VERY_SMALL, gender = Gender.MALE),
            Player("p5", "Emma", 2, HeightClass.MEDIUM, gender = Gender.FEMALE),
            Player("p6", "Felix", 5, HeightClass.LARGE, gender = Gender.MALE),
            Player("p7", "Greta", 3, HeightClass.SMALL, gender = Gender.FEMALE),
            Player("p8", "Hans", 1, HeightClass.VERY_SMALL, gender = Gender.MALE)
        )

        val config = MixerConfig(
            format = FieldFormat.FORMAT_2V2,
            sportType = SportType.BEACH,
            teamCount = 4,
            autoBalance = true
        )

        // Run mixer 10 times consecutively (Regression test for 2nd mix crash)
        for (i in 1..10) {
            val result = TeamMixerEngine.mix(playerList, config)
            assertEquals(4, result.teams.size)

            val allAssignedIds = result.teams.flatMap { it.playerIds }
            val distinctIds = allAssignedIds.distinct()
            assertEquals("Duplicate player IDs detected on mix #$i", distinctIds.size, allAssignedIds.size)
            assertEquals(8, allAssignedIds.size)

            // Verify original player list was not mutated
            assertEquals(8, playerList.size)
        }
    }

    @Test
    fun testMixerAutoBalanceDisabled() {
        val playerList = (1..6).map { idx ->
            Player("p$idx", "Player $idx", idx % 5 + 1, HeightClass.MEDIUM, gender = Gender.MALE)
        }

        val config = MixerConfig(
            format = FieldFormat.FORMAT_2V2,
            sportType = SportType.BEACH,
            teamCount = 3,
            autoBalance = false
        )

        val result = TeamMixerEngine.mix(playerList, config)
        assertEquals(3, result.teams.size)
        result.teams.forEach { team ->
            assertEquals(2, team.playerIds.size)
        }
    }

    // =========================================================================
    // 2. AUFSCHLAGROTATION TESTS
    // =========================================================================

    @Test
    fun testSideOutAndServeRotationRules() {
        val config = MatchConfig(
            fieldFormatA = FieldFormat.FORMAT_6V6_PLUS_2,
            fieldFormatB = FieldFormat.FORMAT_6V6,
            setsMode = SetsMode.BEST_OF_3,
            pointsPerSet = 25,
            winByTwo = true
        )

        val match = Match(
            id = "test_match",
            teamAId = "A",
            teamBId = "B",
            teamAName = "Team Alpha",
            teamBName = "Team Beta",
            matchConfig = config,
            status = MatchStatus.RUNNING,
            servingTeam = TeamSide.TEAM_A,
            servingPlayerIndexA = 0,
            servingPlayerIndexB = 0
        )

        // Case 1: Team A is serving and scores a point. Server stays at 0.
        val match1 = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(1, match1.scoreA)
        assertEquals(0, match1.scoreB)
        assertEquals(TeamSide.TEAM_A, match1.servingTeam)
        assertEquals(0, match1.servingPlayerIndexA)

        // Case 2: Team B wins rally (Side-out). Server rotates to 1.
        val match2 = ScoreEngine.scorePoint(match1, TeamSide.TEAM_B)
        assertEquals(1, match2.scoreA)
        assertEquals(1, match2.scoreB)
        assertEquals(TeamSide.TEAM_B, match2.servingTeam)
        assertEquals(1, match2.servingPlayerIndexB)

        // Case 3: Team B continues serving and scores -> stays at 1.
        val match3 = ScoreEngine.scorePoint(match2, TeamSide.TEAM_B)
        assertEquals(2, match3.scoreB)
        assertEquals(TeamSide.TEAM_B, match3.servingTeam)
        assertEquals(1, match3.servingPlayerIndexB)

        // Case 4: Side-out back to Team A -> Team A rotates from 0 to 1.
        val match4 = ScoreEngine.scorePoint(match3, TeamSide.TEAM_A)
        assertEquals(2, match4.scoreA)
        assertEquals(TeamSide.TEAM_A, match4.servingTeam)
        assertEquals(1, match4.servingPlayerIndexA)
    }

    @Test
    fun testRotationFullCycleModuloFieldPlayerCount() {
        val config = MatchConfig(
            fieldFormatA = FieldFormat.FORMAT_2V2,
            fieldFormatB = FieldFormat.FORMAT_2V2,
            pointsPerSet = 21
        )
        var match = Match(
            id = "test_2v2",
            teamAId = "A",
            teamBId = "B",
            teamAName = "A",
            teamBName = "B",
            matchConfig = config,
            servingTeam = TeamSide.TEAM_A,
            servingPlayerIndexA = 0
        )

        // Side out to B -> B server becomes 1
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_B)
        assertEquals(1, match.servingPlayerIndexB)

        // Side out to A -> A server rotates from 0 to 1
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(1, match.servingPlayerIndexA)

        // Side out to B -> B server rotates from 1 to 0 (modulo 2)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_B)
        assertEquals(0, match.servingPlayerIndexB)

        // Side out to A -> A server rotates from 1 to 0 (modulo 2)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(0, match.servingPlayerIndexA)
    }

    // =========================================================================
    // 3. SATZLOGIK & WIN BY TWO TESTS
    // =========================================================================

    @Test
    fun testSetWinningAndWinByTwoRule() {
        assertFalse(ScoreEngine.isSetWon(20, 19, 21, true))
        assertFalse(ScoreEngine.isSetWon(21, 20, 21, true))
        assertTrue(ScoreEngine.isSetWon(21, 19, 21, true))
        assertTrue(ScoreEngine.isSetWon(24, 22, 21, true))

        // winByTwo = false
        assertTrue(ScoreEngine.isSetWon(21, 20, 21, false))
    }

    @Test
    fun testBestOf3MatchProgression() {
        val config = MatchConfig(
            setsMode = SetsMode.BEST_OF_3,
            pointsPerSet = 21,
            winByTwo = true
        )
        var match = Match(
            id = "m",
            teamAId = "A",
            teamBId = "B",
            teamAName = "Team A",
            teamBName = "Team B",
            matchConfig = config,
            status = MatchStatus.RUNNING,
            scoreA = 20,
            scoreB = 18,
            currentSet = 1,
            servingTeam = TeamSide.TEAM_A
        )

        // Team A scores 21st point -> Set 1 won!
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(1, match.completedSets.size)
        assertEquals(1, match.completedSets[0].setNumber)
        assertEquals(21, match.completedSets[0].scoreA)
        assertEquals(18, match.completedSets[0].scoreB)
        assertEquals(TeamSide.TEAM_A, match.completedSets[0].winner)

        // Should advance to Set 2 with score reset to 0:0 and status RUNNING
        assertEquals(2, match.currentSet)
        assertEquals(0, match.scoreA)
        assertEquals(0, match.scoreB)
        assertEquals(MatchStatus.RUNNING, match.status)

        // Fast forward Set 2: Team A wins 21:15 -> Match should FINISH (2 sets won)
        match = match.copy(scoreA = 20, scoreB = 15)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(2, match.completedSets.size)
        assertEquals(MatchStatus.FINISHED, match.status)
        assertEquals(TeamSide.TEAM_A, ScoreEngine.getMatchWinner(match.completedSets, SetsMode.BEST_OF_3))
    }

    // =========================================================================
    // 4. UNDO / REDO TESTS
    // =========================================================================

    @Test
    fun testUndoRestoresPreviousStateExactly() {
        val config = MatchConfig(pointsPerSet = 21)
        var match = Match(
            id = "m",
            teamAId = "A",
            teamBId = "B",
            teamAName = "A",
            teamBName = "B",
            matchConfig = config,
            scoreA = 5,
            scoreB = 5,
            servingTeam = TeamSide.TEAM_A,
            servingPlayerIndexA = 2
        )

        // Point for B (side-out)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_B)
        assertEquals(6, match.scoreB)
        assertEquals(TeamSide.TEAM_B, match.servingTeam)
        assertEquals(1, match.undoHistory.size)

        // Undo
        match = ScoreEngine.undo(match)
        assertEquals(5, match.scoreA)
        assertEquals(5, match.scoreB)
        assertEquals(TeamSide.TEAM_A, match.servingTeam)
        assertEquals(2, match.servingPlayerIndexA)
        assertEquals(0, match.undoHistory.size)
    }

    // =========================================================================
    // 5. TURNIER-PAARUNGEN & MANUAL TEAMS TESTS
    // =========================================================================

    @Test
    fun testRoundRobinGenerationWithOddTeamsDoesNotSaveByeAsMatch() {
        val teams = listOf(
            Team("1", "T1", SportType.BEACH, 2, 2, emptyList()),
            Team("2", "T2", SportType.BEACH, 2, 2, emptyList()),
            Team("3", "T3", SportType.BEACH, 2, 2, emptyList())
        )

        val matches = TournamentEngine.generateRoundRobinMatches(
            tournamentId = "tourney1",
            groupId = "A",
            teams = teams,
            config = MatchConfig()
        )

        assertEquals(3, matches.size)
        matches.forEach { m ->
            assertNotEquals("BYE", m.teamAId)
            assertNotEquals("BYE", m.teamBId)
        }
    }

    @Test
    fun testManualTournamentTeamsSupport() {
        val manualTeams = listOf(
            ManualTournamentTeam("m1", "Sandsturm", skill = 4, maleCount = 2, femaleCount = 2, totalAge = 110, note = "Mixed Team"),
            ManualTournamentTeam("m2", "Blockbuster", skill = 3, maleCount = 4, femaleCount = 0, totalAge = 120, note = ""),
            ManualTournamentTeam("m3", "Netzroller", skill = 5, maleCount = 1, femaleCount = 3, totalAge = 105, note = "Favorit"),
            ManualTournamentTeam("m4", "BeachBabes", skill = 2, maleCount = 0, femaleCount = 4, totalAge = 98, note = "")
        )

        val groupMap = mapOf(
            "A" to listOf("m1", "m2"),
            "B" to listOf("m3", "m4")
        )

        val tournament = Tournament(
            id = "t_manual",
            name = "Beach-Pokal",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_4V4,
            matchConfig = MatchConfig(),
            mode = TournamentMode.GROUPS_KO,
            groupsCount = 2,
            seedingMode = SeedingMode.CROSS_SEEDING,
            teamIds = manualTeams.map { it.id },
            groupAssignments = groupMap,
            manualTeams = manualTeams
        )

        assertEquals(4, tournament.teamIds.size)
        assertEquals(4, tournament.manualTeams.size)
        assertEquals(2, tournament.groupAssignments.size)
        assertEquals(2, tournament.groupAssignments["A"]?.size)
        assertEquals(2, tournament.groupAssignments["B"]?.size)
    }

    @Test
    fun testKoMatchesBlockedUntilAllGroupMatchesFinished() {
        val tournament = Tournament(
            id = "T1",
            name = "Test Cup",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_2V2,
            matchConfig = MatchConfig(),
            mode = TournamentMode.GROUPS_KO,
            groupAssignments = mapOf("A" to listOf("T1", "T2"), "B" to listOf("T3", "T4"))
        )

        val unfinishedMatches = listOf(
            Match(
                id = "m1",
                teamAId = "T1",
                teamBId = "T2",
                teamAName = "T1",
                teamBName = "T2",
                matchConfig = MatchConfig(),
                status = MatchStatus.RUNNING
            )
        )

        assertFalse(TournamentEngine.areGroupMatchesCompleted(unfinishedMatches))

        try {
            TournamentEngine.generateKoMatches(
                tournament = tournament,
                groupStandings = emptyMap<String, List<Standing>>(),
                allGroupMatches = unfinishedMatches,
                seedingMode = SeedingMode.CROSS_SEEDING
            )
            fail("Should fail when group matches are not finished")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("FINISHED") == true)
        }
    }

    // =========================================================================
    // 6. RANGLISTEN & TIEBREAKER TESTS
    // =========================================================================

    @Test
    fun testStandingsRankingCriteria() {
        val teamIds = listOf("T1", "T2", "T3")
        val teamNames = mapOf("T1" to "Team 1", "T2" to "Team 2", "T3" to "Team 3")

        val matches = listOf(
            Match(
                id = "m1",
                teamAId = "T1",
                teamBId = "T2",
                teamAName = "Team 1",
                teamBName = "Team 2",
                matchConfig = MatchConfig(),
                status = MatchStatus.FINISHED,
                completedSets = listOf(
                    SetResult(1, 21, 10, TeamSide.TEAM_A),
                    SetResult(2, 21, 10, TeamSide.TEAM_A)
                ),
                tournamentId = "t"
            ),
            Match(
                id = "m2",
                teamAId = "T2",
                teamBId = "T3",
                teamAName = "Team 2",
                teamBName = "Team 3",
                matchConfig = MatchConfig(),
                status = MatchStatus.FINISHED,
                completedSets = listOf(
                    SetResult(1, 21, 15, TeamSide.TEAM_A),
                    SetResult(2, 21, 15, TeamSide.TEAM_A)
                ),
                tournamentId = "t"
            ),
            Match(
                id = "m3",
                teamAId = "T1",
                teamBId = "T3",
                teamAName = "Team 1",
                teamBName = "Team 3",
                matchConfig = MatchConfig(),
                status = MatchStatus.FINISHED,
                completedSets = listOf(
                    SetResult(1, 21, 5, TeamSide.TEAM_A),
                    SetResult(2, 21, 5, TeamSide.TEAM_A)
                ),
                tournamentId = "t"
            )
        )

        val standings = TournamentEngine.calculateStandings(
            tournamentId = "t",
            groupId = "",
            teamIds = teamIds,
            teamNames = teamNames,
            matches = matches
        )

        assertEquals(3, standings.size)

        // Rank 1: T1 (2 wins, 4:0 sets)
        assertEquals("T1", standings[0].teamId)
        assertEquals(1, standings[0].rank)
        assertEquals(2, standings[0].wins)
        assertEquals(4, standings[0].setsWon)
        assertEquals(0, standings[0].setsLost)

        // Rank 2: T2 (1 win, 2:2 sets)
        assertEquals("T2", standings[1].teamId)
        assertEquals(2, standings[1].rank)
        assertEquals(1, standings[1].wins)

        // Rank 3: T3 (0 wins, 0:4 sets)
        assertEquals("T3", standings[2].teamId)
        assertEquals(3, standings[2].rank)
        assertEquals(0, standings[2].wins)
    }

    // =========================================================================
    // 7. COIN TOSS TESTS
    // =========================================================================

    @Test
    fun testCoinTossEngine() {
        val res = CoinTossEngine.flipCoin(
            teamAName = "Alpha",
            teamBName = "Beta",
            headsTeam = "Alpha"
        )
        assertNotNull(res.side)
        assertNotNull(res.winnerTeamName)
        assertTrue(res.winnerTeamName == "Alpha" || res.winnerTeamName == "Beta")
    }

    @Test
    fun testScoreEngineTimerTracking() {
        val match = Match(
            id = "m_timer",
            teamAId = "tA",
            teamBId = "tB",
            teamAName = "A",
            teamBName = "B",
            matchConfig = MatchConfig(pointsPerSet = 21, winByTwo = true)
        )
        assertFalse(match.isTimerRunning)
        assertNull(match.setStartTime)

        // Point 1: Auto-starts timer
        val afterPoint1 = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertTrue(afterPoint1.isTimerRunning)
        assertNotNull(afterPoint1.setStartTime)

        // Timeout Team A: pauses timer
        val afterTimeout = ScoreEngine.recordTimeout(afterPoint1, TeamSide.TEAM_A)
        assertFalse(afterTimeout.isTimerRunning)
        assertNotNull(afterTimeout.timeoutStartTime)

        // Point 2: resumes timer
        val afterPoint2 = ScoreEngine.scorePoint(afterTimeout, TeamSide.TEAM_B)
        assertTrue(afterPoint2.isTimerRunning)
        assertNull(afterPoint2.timeoutStartTime)

        // Finish set: calculates net time and finishes set
        val finishedSetMatch = ScoreEngine.finishSet(afterPoint2)
        assertFalse(finishedSetMatch.isTimerRunning)
        assertEquals(1, finishedSetMatch.completedSets.size)
        val timeInfo = finishedSetMatch.completedSets[0].timeInfo
        assertNotNull(timeInfo)
        assertTrue(timeInfo!!.totalSeconds >= 0)
    }

    @Test
    fun testTeamMixerMixedQuotaConstraints() {
        val players = listOf(
            Player("p1", "F1", 4, HeightClass.MEDIUM, Gender.FEMALE),
            Player("p2", "M1", 4, HeightClass.LARGE, Gender.MALE),
            Player("p3", "M2", 3, HeightClass.LARGE, Gender.MALE),
            Player("p4", "M3", 3, HeightClass.LARGE, Gender.MALE)
        )

        // Request 2 teams of 2v2 with min 1 female per team -> only 1 female available for 2 teams!
        val config = MixerConfig(
            format = FieldFormat.FORMAT_2V2,
            sportType = SportType.BEACH,
            teamCount = 2,
            minFemalesPerTeam = 1,
            minMalesPerTeam = 1
        )
        val result = TeamMixerEngine.mix(players, config)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Spielerinnen"))

        // Now provide 2 females and 2 males -> must succeed and satisfy quotas
        val balancedPlayers = listOf(
            Player("p1", "F1", 4, HeightClass.MEDIUM, Gender.FEMALE),
            Player("p5", "F2", 3, HeightClass.SMALL, Gender.FEMALE),
            Player("p2", "M1", 4, HeightClass.LARGE, Gender.MALE),
            Player("p3", "M2", 3, HeightClass.LARGE, Gender.MALE)
        )
        val successConfig = MixerConfig(
            format = FieldFormat.FORMAT_2V2,
            sportType = SportType.BEACH,
            teamCount = 2,
            minFemalesPerTeam = 1,
            minMalesPerTeam = 1
        )
        val successResult = TeamMixerEngine.mix(balancedPlayers, successConfig)
        assertNull(successResult.errorMessage)
        assertEquals(2, successResult.teams.size)
        // Each team must have at least 1 female
        val team1Females = successResult.teams[0].playerIds.count { id -> id == "p1" || id == "p5" }
        val team2Females = successResult.teams[1].playerIds.count { id -> id == "p1" || id == "p5" }
        assertEquals(1, team1Females)
        assertEquals(1, team2Females)
    }

    @Test
    fun testScoreEngineWinByTwoScoresAbove21() {
        var match = Match(
            id = "m_extended",
            teamAId = "tA",
            teamBId = "tB",
            teamAName = "A",
            teamBName = "B",
            scoreA = 20,
            scoreB = 20,
            matchConfig = MatchConfig(setsMode = SetsMode.ONE_SET, pointsPerSet = 21, winByTwo = true)
        )

        // Point to A: 21:20 (not finished, diff is 1)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(21, match.scoreA)
        assertEquals(20, match.scoreB)
        assertEquals(MatchStatus.RUNNING, match.status)
        assertEquals(0, match.completedSets.size)

        // Point to B: 21:21
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_B)
        assertEquals(21, match.scoreA)
        assertEquals(21, match.scoreB)
        assertEquals(MatchStatus.RUNNING, match.status)

        // Point to A: 22:21
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(22, match.scoreA)
        assertEquals(21, match.scoreB)
        assertEquals(MatchStatus.RUNNING, match.status)

        // Point to A: 23:21 (finished! 2 points lead)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(MatchStatus.FINISHED, match.status)
        assertEquals(1, match.completedSets.size)
        assertEquals(23, match.completedSets[0].scoreA)
        assertEquals(21, match.completedSets[0].scoreB)
        assertEquals(TeamSide.TEAM_A, match.completedSets[0].winner)
    }

    @Test
    fun testTournamentKoMatchesRequirementGuard() {
        val matches = listOf(
            Match(
                id = "m1",
                teamAId = "T1",
                teamBId = "T2",
                teamAName = "A",
                teamBName = "B",
                matchConfig = MatchConfig(),
                groupId = "A",
                status = MatchStatus.FINISHED
            ),
            Match(
                id = "m2",
                teamAId = "T3",
                teamBId = "T4",
                teamAName = "C",
                teamBName = "D",
                matchConfig = MatchConfig(),
                groupId = "A",
                status = MatchStatus.RUNNING // unfinished!
            )
        )

        assertFalse(TournamentEngine.areGroupMatchesCompleted(matches))

        // Calling generateKoMatches must throw IllegalArgumentException when group matches are unfinished
        val tournament = Tournament(
            id = "t_guard",
            name = "Cup",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_2V2,
            matchConfig = MatchConfig(),
            mode = TournamentMode.GROUPS_KO,
            teamIds = listOf("T1", "T2", "T3", "T4")
        )

        try {
            TournamentEngine.generateKoMatches(
                tournament = tournament,
                groupStandings = emptyMap(),
                allGroupMatches = matches,
                seedingMode = SeedingMode.CROSS_SEEDING
            )
            fail("Expected IllegalArgumentException when group matches are unfinished")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("FINISHED"))
        }
    }

    // =========================================================================
    // 8. PFLICHTENHEFT: TEMPLATES, 17 TEAMS, CLIPBOARD IMPORT & SWISS SYSTEM
    // =========================================================================

    @Test
    fun test17RandomTestTournamentTeamsGeneration() {
        val teams = TournamentEngine.generateRandomTestTournamentTeams(17)
        assertEquals(17, teams.size)

        val ids = teams.map { it.id }.toSet()
        assertEquals("All 17 teams must have unique IDs", 17, ids.size)

        teams.forEach { team ->
            assertTrue("Name must not be empty", team.name.isNotBlank())
            assertTrue("Skill must be 1..5", team.skill in 1..5)
            assertTrue("Male count must be >= 0", team.maleCount >= 0)
            assertTrue("Female count must be >= 0", team.femaleCount >= 0)
            assertTrue("Total age must be > 0", team.totalAge > 0)
            assertTrue("Club must not be blank", team.club.isNotBlank())
        }
    }

    @Test
    fun testClipboardTextParsingTsvAndCsvAndSemicolon() {
        // Case 1: Tab-separated (Excel / Sheets export) with header
        val tsvData = """
            Mannschaft	Skill	Herren	Damen	Alter	Verein	Notiz
            Beach Hawks	4	2	2	110	VBC Nord	Favorit
            Blockbusters	3	3	1	95	TSV Süd	Defensiv
            Netz-Ninjas	5	2	2	104	SV West	
        """.trimIndent()

        val tsvTeams = TournamentEngine.parseTeamsFromText(tsvData)
        assertEquals(3, tsvTeams.size)
        assertEquals("Beach Hawks", tsvTeams[0].name)
        assertEquals(4, tsvTeams[0].skill)
        assertEquals(2, tsvTeams[0].maleCount)
        assertEquals(2, tsvTeams[0].femaleCount)
        assertEquals(110, tsvTeams[0].totalAge)
        assertEquals("VBC Nord", tsvTeams[0].club)
        assertEquals("Favorit", tsvTeams[0].note)

        assertEquals("Blockbusters", tsvTeams[1].name)
        assertEquals(3, tsvTeams[1].skill)
        assertEquals(3, tsvTeams[1].maleCount)
        assertEquals("TSV Süd", tsvTeams[1].club)

        // Case 2: Semicolon-separated (German Excel export) without header
        val semiData = """
            Spike Kings;5;2;2;112;DJK Ost;Top
            Volley Tigers;2;1;3;88;TV Beach;
        """.trimIndent()

        val semiTeams = TournamentEngine.parseTeamsFromText(semiData)
        assertEquals(2, semiTeams.size)
        assertEquals("Spike Kings", semiTeams[0].name)
        assertEquals(5, semiTeams[0].skill)
        assertEquals("DJK Ost", semiTeams[0].club)
        assertEquals("Volley Tigers", semiTeams[1].name)
        assertEquals(2, semiTeams[1].skill)

        // Case 3: Comma-separated (CSV)
        val csvData = """
            Team Name,Skill,Male,Female,Age,Club
            Ace Hunters, 4, 2, 0, 56, Club Alpha
            Dig Masters, 3, 0, 2, 52, Club Beta
        """.trimIndent()

        val csvTeams = TournamentEngine.parseTeamsFromText(csvData)
        assertEquals(2, csvTeams.size)
        assertEquals("Ace Hunters", csvTeams[0].name)
        assertEquals(4, csvTeams[0].skill)
        assertEquals("Club Alpha", csvTeams[0].club)

        // Case 4: Plain lines (just team names)
        val plainData = """
            Team 1
            Team 2
            Team 3
        """.trimIndent()

        val plainTeams = TournamentEngine.parseTeamsFromText(plainData)
        assertEquals(3, plainTeams.size)
        assertEquals("Team 1", plainTeams[0].name)
        assertEquals("Team 2", plainTeams[1].name)
        assertEquals("Team 3", plainTeams[2].name)
    }

    @Test
    fun testAll11TournamentTemplatesCoverPflichtenheft() {
        val templates = TournamentTemplate.ALL
        assertTrue(templates.size >= 11)

        val ids = templates.map { it.id }.toSet()
        assertTrue(ids.contains("beach_2v2_classic"))
        assertTrue(ids.contains("beach_2v2_quick"))
        assertTrue(ids.contains("beach_4v4_fun"))
        assertTrue(ids.contains("mixed_4v4"))
        assertTrue(ids.contains("mixed_6v6"))
        assertTrue(ids.contains("indoor_6v6_cup"))
        assertTrue(ids.contains("indoor_6v6_league"))
        assertTrue(ids.contains("youth_u_team"))
        assertTrue(ids.contains("company_event"))
        assertTrue(ids.contains("swiss_tournament"))
        assertTrue(ids.contains("king_of_court"))

        // Check specific template settings from Pflichtenheft
        val mixed4v4 = templates.first { it.id == "mixed_4v4" }
        assertEquals(FieldFormat.FORMAT_4V4, mixed4v4.fieldFormat)
        assertEquals(2, mixed4v4.minFemales)
        assertEquals(1, mixed4v4.minMales)

        val swiss = templates.first { it.id == "swiss_tournament" }
        assertEquals(TournamentMode.SWISS_SYSTEM, swiss.mode)

        val liga = templates.first { it.id == "indoor_6v6_league" }
        assertEquals(SetsMode.BEST_OF_3, liga.setsMode)
        assertEquals(25, liga.pointsPerSet)
    }

    @Test
    fun testCourtAssignmentInRoundRobinMatches() {
        val teams = (1..6).map { idx ->
            Team("t$idx", "Team $idx", SportType.INDOOR, 6, 6, emptyList())
        }

        val matches = TournamentEngine.generateRoundRobinMatches(
            tournamentId = "t_courts",
            groupId = "A",
            teams = teams,
            config = MatchConfig(),
            courtCount = 3
        )

        assertEquals(15, matches.size) // 6 teams = 6*5/2 = 15 matches
        matches.forEach { m ->
            assertNotNull(m.courtNumber)
            assertTrue("Court number must be between 1 and 3", m.courtNumber!! in 1..3)
        }

        // Verify court distribution: each court has matches
        val courtsUsed = matches.mapNotNull { it.courtNumber }.toSet()
        assertEquals(setOf(1, 2, 3), courtsUsed)
    }

    @Test
    fun testClubSeparationSeeding() {
        // 8 teams with 2 clubs: "Club Nord" (4 teams) and "Club Süd" (4 teams)
        val teams = listOf(
            ManualTournamentTeam("m1", "Nord 1", 5, 2, 2, 100, "Club Nord"),
            ManualTournamentTeam("m2", "Nord 2", 4, 2, 2, 100, "Club Nord"),
            ManualTournamentTeam("m3", "Nord 3", 3, 2, 2, 100, "Club Nord"),
            ManualTournamentTeam("m4", "Nord 4", 2, 2, 2, 100, "Club Nord"),
            ManualTournamentTeam("m5", "Süd 1", 5, 2, 2, 100, "Club Süd"),
            ManualTournamentTeam("m6", "Süd 2", 4, 2, 2, 100, "Club Süd"),
            ManualTournamentTeam("m7", "Süd 3", 3, 2, 2, 100, "Club Süd"),
            ManualTournamentTeam("m8", "Süd 4", 2, 2, 2, 100, "Club Süd")
        )

        val groups = TournamentEngine.distributeTeamsToGroups(
            teamIds = teams.map { it.id },
            groupsCount = 2,
            seedingMode = SeedingMode.CLUB_SEPARATION,
            manualTeams = teams
        )

        assertEquals(2, groups.size)
        assertEquals(4, groups["A"]?.size)
        assertEquals(4, groups["B"]?.size)

        // Count Nord teams in Group A and Group B
        val nordTeamsA = groups["A"]!!.count { id -> teams.first { it.id == id }.club == "Club Nord" }
        val nordTeamsB = groups["B"]!!.count { id -> teams.first { it.id == id }.club == "Club Nord" }

        // Both groups should receive exactly 2 Nord teams and 2 Süd teams!
        assertEquals(2, nordTeamsA)
        assertEquals(2, nordTeamsB)
    }

    @Test
    fun testSwissSystemRoundPairingsAndRematchAvoidance() {
        val teams = (1..6).map { idx ->
            ManualTournamentTeam("t$idx", "Team $idx", skill = idx, club = "Club $idx")
        }

        val tournament = Tournament(
            id = "t_swiss",
            name = "Swiss Masters",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_2V2,
            matchConfig = MatchConfig(),
            mode = TournamentMode.SWISS_SYSTEM,
            courtCount = 2,
            teamIds = teams.map { it.id },
            manualTeams = teams
        )

        val teamNames = teams.associate { it.id to it.name }
        val initialStandings = TournamentEngine.calculateStandings(
            tournamentId = tournament.id,
            groupId = "SWISS",
            teamIds = tournament.teamIds,
            teamNames = teamNames,
            matches = emptyList()
        )

        // Round 1 pairings
        val round1Matches = TournamentEngine.generateSwissRoundMatches(
            tournament = tournament,
            roundNumber = 1,
            standings = initialStandings,
            previousMatches = emptyList(),
            courtCount = tournament.courtCount
        )

        assertEquals(3, round1Matches.size) // 6 teams = 3 matches
        round1Matches.forEach { m ->
            assertEquals("Swiss Runde 1", m.tournamentRound)
            assertNotNull(m.courtNumber)
            assertTrue(m.courtNumber!! in 1..2)
        }

        // Simulate Round 1 finished: Team 1 beats Team 6, Team 2 beats Team 5, Team 3 beats Team 4
        val finishedRound1 = round1Matches.mapIndexed { idx, m ->
            m.copy(
                status = MatchStatus.FINISHED,
                scoreA = 21,
                scoreB = 15,
                completedSets = listOf(SetResult(1, 21, 15, TeamSide.TEAM_A))
            )
        }

        val standingsAfterR1 = TournamentEngine.calculateStandings(
            tournamentId = tournament.id,
            groupId = "SWISS",
            teamIds = tournament.teamIds,
            teamNames = teamNames,
            matches = finishedRound1
        )

        // Round 2 pairings
        val round2Matches = TournamentEngine.generateSwissRoundMatches(
            tournament = tournament,
            roundNumber = 2,
            standings = standingsAfterR1,
            previousMatches = finishedRound1,
            courtCount = tournament.courtCount
        )

        assertEquals(3, round2Matches.size)
        round2Matches.forEach { m ->
            assertEquals("Swiss Runde 2", m.tournamentRound)
            // Verify NO rematch against Round 1 opponent
            val r1MatchForA = finishedRound1.find { it.teamAId == m.teamAId || it.teamBId == m.teamAId }
            assertNotNull(r1MatchForA)
            val r1OpponentOfA = if (r1MatchForA!!.teamAId == m.teamAId) r1MatchForA.teamBId else r1MatchForA.teamAId
            assertNotEquals("Round 2 opponent must not be the same as Round 1 opponent", r1OpponentOfA, m.teamBId)
        }
    }

    @Test
    fun testTournamentExportFormat() {
        val tournament = Tournament(
            id = "t_export",
            name = "City Beach Cup 2026",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_2V2,
            matchConfig = MatchConfig(),
            mode = TournamentMode.ROUND_ROBIN,
            location = "Stadtstrand Arena",
            date = "15.07.2026",
            courtCount = 2,
            teamIds = listOf("T1", "T2")
        )

        val matches = listOf(
            Match(
                id = "m1",
                tournamentId = "t_export",
                teamAId = "T1",
                teamBId = "T2",
                teamAName = "Sandsturm",
                teamBName = "Netz-Dominanz",
                matchConfig = MatchConfig(),
                courtNumber = 1,
                status = MatchStatus.FINISHED,
                completedSets = listOf(SetResult(1, 21, 18, TeamSide.TEAM_A), SetResult(2, 21, 19, TeamSide.TEAM_A))
            )
        )

        val standings = TournamentEngine.calculateStandings(
            tournamentId = "t_export",
            groupId = "A",
            teamIds = listOf("T1", "T2"),
            teamNames = mapOf("T1" to "Sandsturm", "T2" to "Netz-Dominanz"),
            matches = matches
        )

        val exportText = TournamentEngine.exportTournamentScheduleAndStandings(
            tournament = tournament,
            matches = matches,
            standingsByGroup = mapOf("A" to standings)
        )

        assertTrue(exportText.contains("City Beach Cup 2026"))
        assertTrue(exportText.contains("Stadtstrand Arena"))
        assertTrue(exportText.contains("15.07.2026"))
        assertTrue(exportText.contains("Feld 1"))
        assertTrue(exportText.contains("Sandsturm"))
        assertTrue(exportText.contains("Netz-Dominanz"))
        assertTrue(exportText.contains("21:18, 21:19"))
        assertTrue(exportText.contains("TABELLEN & PLATZIERUNGEN"))
    }

    // =========================================================================
    // 9. MINIMAL DUMMIES, OVERALL STANDINGS, TIMELINE & ROTATION TESTS
    // =========================================================================

    @Test
    fun testTeamMixerUsesMinimalDummiesAndDistributesAllRealPlayersFirst() {
        // 5 real players split into 2 teams of 4v4 (capacity = 8, so only 3 dummies should be added)
        val players = (1..5).map { i ->
            Player("p$i", "Real $i", skill = (i % 5) + 1, heightClass = HeightClass.MEDIUM, gender = Gender.MALE)
        }
        val config = MixerConfig(
            format = FieldFormat.FORMAT_4V4,
            sportType = SportType.BEACH,
            teamCount = 2,
            autoBalance = true
        )

        val result = TeamMixerEngine.mix(players, config)
        assertNull(result.errorMessage)
        assertEquals(2, result.teams.size)
        assertEquals("Exactly 3 dummies needed for 5 players in 2x4 slots", 3, result.createdDummies.size)

        val allAssignedIds = result.teams.flatMap { it.playerIds }
        assertEquals(8, allAssignedIds.size)
        players.forEach { real ->
            assertTrue("Every real player must be assigned before dummies", allAssignedIds.contains(real.id))
        }
    }

    @Test
    fun testCalculateOverallStandingsAcrossGroups() {
        val teams = listOf(
            ManualTournamentTeam("a1", "Alpha 1", skill = 5),
            ManualTournamentTeam("a2", "Alpha 2", skill = 3),
            ManualTournamentTeam("b1", "Beta 1", skill = 4),
            ManualTournamentTeam("b2", "Beta 2", skill = 2)
        )
        val tournament = Tournament(
            id = "t_all",
            name = "Gesamt Cup",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_2V2,
            matchConfig = MatchConfig(),
            mode = TournamentMode.GROUPS_KO,
            groupsCount = 2,
            teamIds = teams.map { it.id },
            groupAssignments = mapOf("A" to listOf("a1", "a2"), "B" to listOf("b1", "b2")),
            manualTeams = teams
        )
        val teamNames = teams.associate { it.id to it.name }
        val matches = listOf(
            Match(
                id = "mA",
                tournamentId = "t_all",
                groupId = "A",
                teamAId = "a1",
                teamBId = "a2",
                teamAName = "Alpha 1",
                teamBName = "Alpha 2",
                matchConfig = MatchConfig(),
                status = MatchStatus.FINISHED,
                completedSets = listOf(SetResult(1, 21, 10, TeamSide.TEAM_A))
            ),
            Match(
                id = "mB",
                tournamentId = "t_all",
                groupId = "B",
                teamAId = "b1",
                teamBId = "b2",
                teamAName = "Beta 1",
                teamBName = "Beta 2",
                matchConfig = MatchConfig(),
                status = MatchStatus.FINISHED,
                completedSets = listOf(SetResult(1, 21, 18, TeamSide.TEAM_A))
            )
        )

        val overall = TournamentEngine.calculateOverallStandings(tournament, teamNames, matches)
        assertEquals(4, overall.size)
        // Alpha 1 won 21:10 (+11) vs Beta 1 won 21:18 (+3) -> Alpha 1 ranks #1 overall
        assertEquals("a1", overall[0].teamId)
        assertEquals(1, overall[0].rank)
        assertEquals("b1", overall[1].teamId)
        assertEquals(2, overall[1].rank)
    }

    @Test
    fun testScoreEngineEventsTimelineUndoAndLinkedRotation() {
        var match = Match(
            id = "m_rot",
            teamAId = "A",
            teamBId = "B",
            teamAName = "Rot",
            teamBName = "Blau",
            matchConfig = MatchConfig(pointsPerSet = 25),
            servingTeam = TeamSide.TEAM_A,
            rotationLinked = true,
            rotationStepA = 0,
            rotationStepB = 0
        )

        // 1. Timeout for Team A recorded in timeline
        match = ScoreEngine.recordTimeout(match, TeamSide.TEAM_A)
        assertEquals(1, match.timeoutsA)
        assertEquals(1, match.events.size)
        assertEquals(MatchEventType.TIMEOUT, match.events.last().type)
        assertEquals(TeamSide.TEAM_A, match.events.last().teamSide)

        // 2. Point for Team B during timeout -> resumes timer, records POINT event, and advances rotationStepB (side-out!)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_B)
        assertNull(match.timeoutStartTime)
        assertEquals(2, match.events.size)
        assertEquals(MatchEventType.POINT, match.events.last().type)
        assertEquals(1, match.rotationStepB)
        assertEquals(0, match.rotationStepA)

        // 3. Undo reverts the point, the POINT event, and rotationStepB back to 0!
        match = ScoreEngine.undo(match)
        assertEquals(0, match.scoreB)
        assertEquals(0, match.rotationStepB)
        assertEquals(1, match.events.size)
        assertEquals(MatchEventType.TIMEOUT, match.events.last().type)
    }

    @Test
    fun testRotationEngineOverlapLegalityAcrossAllSystemsPhasesAndSteps() {
        val overlapPhases = listOf(RotationPhase.BASE, RotationPhase.SERVE, RotationPhase.RECEIVE)
        RotationSystem.values().forEach { sys ->
            overlapPhases.forEach { phase ->
                for (step in 0..5) {
                    val cfg = TeamRotationConfig(
                        system = sys,
                        phase = phase,
                        rotationStep = step,
                        liberoActive = true
                    )
                    val tokens = RotationEngine.calculatePositions(cfg)
                    assertEquals(6, tokens.size)
                    val byZone = tokens.associateBy { it.zoneNumber }

                    val p4 = byZone[4]!!
                    val p3 = byZone[3]!!
                    val p2 = byZone[2]!!
                    val p5 = byZone[5]!!
                    val p6 = byZone[6]!!
                    val p1 = byZone[1]!!

                    // Front row left-to-right order: P4 < P3 < P2
                    assertTrue("$sys $phase step $step: x(P4) < x(P3)", p4.xHalfNorm < p3.xHalfNorm)
                    assertTrue("$sys $phase step $step: x(P3) < x(P2)", p3.xHalfNorm < p2.xHalfNorm)

                    // Back row left-to-right order: P5 < P6 < P1
                    assertTrue("$sys $phase step $step: x(P5) < x(P6)", p5.xHalfNorm < p6.xHalfNorm)
                    assertTrue("$sys $phase step $step: x(P6) < x(P1)", p6.xHalfNorm < p1.xHalfNorm)

                    // Front row strictly closer to net than corresponding back row
                    assertTrue("$sys $phase step $step: y(P4) < y(P5)", p4.yNetNorm < p5.yNetNorm)
                    assertTrue("$sys $phase step $step: y(P3) < y(P6)", p3.yNetNorm < p6.yNetNorm)
                    assertTrue("$sys $phase step $step: y(P2) < y(P1)", p2.yNetNorm < p1.yNetNorm)
                }
            }
        }
    }

    @Test
    fun testTrackedCourtTargetFollowsPlayerAcrossRotations() {
        val target = TrackedCourtTarget(
            side = TeamSide.TEAM_A,
            originalRole = "OH1",
            isLiberoTarget = false
        )
        val expectedZonesByStep = listOf(2, 1, 6, 5, 4, 3)

        for (step in 0..5) {
            val tokens = RotationEngine.calculatePositions(
                TeamRotationConfig(
                    system = RotationSystem.SYSTEM_5_1,
                    phase = RotationPhase.RECEIVE,
                    rotationStep = step,
                    liberoActive = true
                )
            )
            val matchedA = tokens.filter { target.matches(TeamSide.TEAM_A, it) }
            assertEquals("Exactly 1 token should match OH1 on step $step", 1, matchedA.size)
            assertEquals("OH1 should be in P${expectedZonesByStep[step]} on step $step", expectedZonesByStep[step], matchedA.first().zoneNumber)

            // Must never match opposite team
            assertTrue(tokens.none { target.matches(TeamSide.TEAM_B, it) })
        }

        // Verify overlap neighbors helper for front/back zones
        assertEquals(setOf(4, 2, 6), RotationEngine.overlapNeighborZones(3).toSet())
        assertEquals(setOf(3, 1), RotationEngine.overlapNeighborZones(2).toSet())
    }

    @Test
    fun testMixerStrictlyUsesOnlyCheckedPlayers() {
        val pool = listOf(
            Player("p1", "Checked 1", 5, HeightClass.LARGE, Gender.MALE, isSelectedForMixer = true),
            Player("p2", "Checked 2", 4, HeightClass.MEDIUM, Gender.FEMALE, isSelectedForMixer = true),
            Player("p3", "Checked 3", 3, HeightClass.MEDIUM, Gender.MALE, isSelectedForMixer = true),
            Player("p4", "Checked 4", 2, HeightClass.SMALL, Gender.FEMALE, isSelectedForMixer = true),
            Player("p5", "Unchecked 1", 5, HeightClass.LARGE, Gender.MALE, isSelectedForMixer = false),
            Player("p6", "Unchecked 2", 1, HeightClass.SMALL, Gender.FEMALE, isSelectedForMixer = false)
        )

        val checkedOnly = pool.filter { it.isSelectedForMixer && !it.isDummy }
        val result = TeamMixerEngine.mix(
            inputPlayers = checkedOnly,
            config = MixerConfig(
                format = FieldFormat.FORMAT_2V2,
                sportType = SportType.BEACH,
                teamCount = 2,
                autoBalance = true
            )
        )

        assertNull(result.errorMessage)
        assertEquals(2, result.teams.size)
        val assignedIds = result.teams.flatMap { it.playerIds }.toSet()
        assertEquals(setOf("p1", "p2", "p3", "p4"), assignedIds)
        assertFalse(assignedIds.contains("p5"))
        assertFalse(assignedIds.contains("p6"))
        assertTrue(result.createdDummies.isEmpty())
    }

    @Test
    fun testRotationDefaultJerseysAndLibero77() {
        val defaults = TeamRotationConfig.defaultJerseys()
        assertEquals("77", defaults["LB"])
        assertEquals("77", TeamRotationConfig.DEFAULT_LIBERO_JERSEY)

        val roles51 = TeamRotationConfig.rolesFor(RotationSystem.SYSTEM_5_1, 6)
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), roles51.map { defaults[it] })

        val roles42 = TeamRotationConfig.rolesFor(RotationSystem.SYSTEM_4_2, 6)
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), roles42.map { defaults[it] })
    }

    @Test
    fun testRotationSmallSidedCounts2To5() {
        for (count in 2..5) {
            RotationPhase.values().forEach { phase ->
                for (step in 0 until count) {
                    val tokens = RotationEngine.calculatePositions(
                        TeamRotationConfig(
                            system = RotationSystem.SYSTEM_5_1,
                            phase = phase,
                            rotationStep = step,
                            playerCountOnCourt = count
                        )
                    )
                    assertEquals("Expected $count tokens on court for $phase step $step", count, tokens.size)
                    assertEquals((1..count).toSet(), tokens.map { it.zoneNumber }.toSet())
                }
            }
            // Verify step wraps cleanly modulo count
            val step0 = RotationEngine.calculatePositions(
                TeamRotationConfig(playerCountOnCourt = count, rotationStep = 0)
            )
            val stepWrapped = RotationEngine.calculatePositions(
                TeamRotationConfig(playerCountOnCourt = count, rotationStep = count)
            )
            assertEquals(step0.map { it.originalRole }, stepWrapped.map { it.originalRole })
        }
    }

    @Test
    fun testRotationInGameSwitchesOh2FromP1ToP6AndOccupiesDistinctZones() {
        RotationSystem.values().forEach { sys ->
            for (step in 0..5) {
                val tokens = RotationEngine.calculatePositions(
                    TeamRotationConfig(
                        system = sys,
                        phase = RotationPhase.IN_GAME,
                        rotationStep = step,
                        liberoActive = true
                    )
                )
                assertEquals(6, tokens.size)
                assertEquals(
                    "All 6 players must switch to unique target zones 1..6 in IN_GAME",
                    setOf(1, 2, 3, 4, 5, 6),
                    tokens.mapNotNull { it.inGameTargetZone }.toSet()
                )
            }
        }

        // Specifically verify: in 5-1 step 4, OH2 serves on P1 and switches to P6 in IN_GAME
        val step4Serve = RotationEngine.calculatePositions(
            TeamRotationConfig(system = RotationSystem.SYSTEM_5_1, phase = RotationPhase.SERVE, rotationStep = 4)
        )
        val oh2Serve = step4Serve.first { it.originalRole == "OH2" }
        assertEquals(1, oh2Serve.zoneNumber)
        assertTrue(oh2Serve.isServer)

        val step4InGame = RotationEngine.calculatePositions(
            TeamRotationConfig(system = RotationSystem.SYSTEM_5_1, phase = RotationPhase.IN_GAME, rotationStep = 4)
        )
        val oh2InGame = step4InGame.first { it.originalRole == "OH2" }
        assertEquals(1, oh2InGame.zoneNumber)
        assertEquals(6, oh2InGame.inGameTargetZone)
    }

    @Test
    fun testSquadSizeUpTo10AndLinkedSmallSidedRotationModulo() {
        assertEquals(9, FieldFormat.FORMAT_6V6_PLUS_3.squadSize)
        assertEquals(6, FieldFormat.FORMAT_6V6_PLUS_3.fieldPlayerCount)
        assertEquals(10, FieldFormat.FORMAT_6V6_PLUS_4.squadSize)
        assertEquals(6, FieldFormat.FORMAT_6V6_PLUS_4.fieldPlayerCount)

        var match = Match(
            id = "m_4v4_linked",
            teamAId = "A",
            teamBId = "B",
            teamAName = "Rot",
            teamBName = "Blau",
            matchConfig = MatchConfig(
                fieldFormatA = FieldFormat.FORMAT_4V4,
                fieldFormatB = FieldFormat.FORMAT_4V4,
                pointsPerSet = 21
            ),
            servingTeam = TeamSide.TEAM_A,
            rotationLinked = true,
            rotationStepA = 3,
            rotationStepB = 0
        )

        // Side-out to B, then back to A -> rotationStepA wraps from 3 -> 0 (modulo 4)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_B)
        assertEquals(1, match.rotationStepB)
        match = ScoreEngine.scorePoint(match, TeamSide.TEAM_A)
        assertEquals(0, match.rotationStepA)
    }

    @Test
    fun testTeamMixerPrioritizesSkillOverGenderAndHeight() {
        // Two 2v2 teams with mixed quota (1 female, 1 male per team).
        // Skill gap is extreme (5 vs 1), while height/gender combinations would tempt a lower skill weight to split differently.
        val players = listOf(
            Player("f1", "Lara", skill = 5, heightClass = HeightClass.LARGE, gender = Gender.FEMALE),
            Player("f2", "Mia", skill = 1, heightClass = HeightClass.LARGE, gender = Gender.FEMALE),
            Player("m1", "Jan", skill = 5, heightClass = HeightClass.VERY_SMALL, gender = Gender.MALE),
            Player("m2", "Tom", skill = 1, heightClass = HeightClass.VERY_SMALL, gender = Gender.MALE)
        )
        val result = TeamMixerEngine.mix(
            inputPlayers = players,
            config = MixerConfig(
                format = FieldFormat.FORMAT_2V2,
                sportType = SportType.BEACH,
                teamCount = 2,
                autoBalance = true,
                minFemalesPerTeam = 1,
                minMalesPerTeam = 1
            )
        )
        assertNull(result.errorMessage)
        assertEquals(2, result.teams.size)

        val playerMap = players.associateBy { it.id }
        val r0 = TeamMixerEngine.calculateTeamRating(result.teams[0], playerMap)
        val r1 = TeamMixerEngine.calculateTeamRating(result.teams[1], playerMap)

        // Mixed constraint respected
        assertEquals(1, r0.femaleCount)
        assertEquals(1, r0.maleCount)
        assertEquals(1, r1.femaleCount)
        assertEquals(1, r1.maleCount)

        // Both teams MUST average 3.0 skill (one skill-5 + one skill-1), never 5.0 vs 1.0!
        assertEquals(3.0, r0.averageSkill, 0.01)
        assertEquals(3.0, r1.averageSkill, 0.01)
    }

    private fun finishMatchWithWinner(match: Match, winner: TeamSide): Match {
        val winSet = if (winner == TeamSide.TEAM_A) {
            SetResult(setNumber = 1, scoreA = 21, scoreB = 15, winner = TeamSide.TEAM_A)
        } else {
            SetResult(setNumber = 1, scoreA = 15, scoreB = 21, winner = TeamSide.TEAM_B)
        }
        return match.copy(
            status = MatchStatus.FINISHED,
            scoreA = winSet.scoreA,
            scoreB = winSet.scoreB,
            completedSets = listOf(winSet)
        )
    }

    @Test
    fun testSingleEliminationFullBracketAndFreilosAdvancement() {
        val teamIds = (1..6).map { "T$it" }
        val teamNames = teamIds.associateWith { "Team ${it.drop(1)}" }
        val tournament = Tournament(
            id = "t_ko_6",
            name = "Pokal 6 Teams",
            sportType = SportType.BEACH,
            fieldFormat = FieldFormat.FORMAT_2V2,
            matchConfig = MatchConfig(setsMode = SetsMode.ONE_SET, pointsPerSet = 21),
            mode = TournamentMode.SINGLE_ELIMINATION,
            teamIds = teamIds,
            seedingMode = SeedingMode.TOP_SEEDS_SEPARATED
        )

        val seededStandings = teamIds.mapIndexed { idx, tid ->
            Standing(tournament.id, "KO", tid, teamNames[tid]!!, rank = idx + 1)
        }

        // Round 1 (8er-Raster mit 2 Freilosen für Seed 1 & Seed 2 -> 2 Viertelfinal-Spiele: #4 vs #5 und #3 vs #6)
        val round1 = TournamentEngine.generateNextSingleEliminationMatches(tournament, seededStandings, emptyList(), courtCount = 2)
        assertEquals(2, round1.size)
        assertTrue(round1.all { it.tournamentRound?.startsWith("Viertelfinale") == true })

        val finishedR1 = round1.map { finishMatchWithWinner(it, TeamSide.TEAM_A) }

        // Round 2 -> Halbfinale (2 matches: Seed 1 & Seed 2 join the QF winners)
        val round2 = TournamentEngine.advanceKoRoundIfReady(tournament, finishedR1, teamNames, courtCount = 2)
        assertEquals(2, round2.size)
        assertTrue(round2.all { it.tournamentRound?.startsWith("Halbfinale") == true })

        val allAfterR2 = finishedR1 + round2.map { finishMatchWithWinner(it, TeamSide.TEAM_A) }

        // Round 3 -> Finale (Platz 1) + Spiel um Platz 3
        val round3 = TournamentEngine.advanceKoRoundIfReady(tournament, allAfterR2, teamNames, courtCount = 2)
        assertEquals(2, round3.size)
        assertTrue(round3.any { it.tournamentRound == "Finale (Platz 1)" })
        assertTrue(round3.any { it.tournamentRound == "Spiel um Platz 3" })

        val allFinished = allAfterR2 + round3.map { finishMatchWithWinner(it, TeamSide.TEAM_A) }
        val afterFinal = TournamentEngine.advanceKoRoundIfReady(tournament, allFinished, teamNames, courtCount = 2)
        assertTrue(afterFinal.isEmpty())
    }

    @Test
    fun testGroupsKo4GroupsQuarterfinalsAndPlacementMatches() {
        // 4 groups with 2 teams each = 8 teams
        val groups = mapOf(
            "A" to listOf("A1", "A2"),
            "B" to listOf("B1", "B2"),
            "C" to listOf("C1", "C2"),
            "D" to listOf("D1", "D2")
        )
        val teamNames = groups.values.flatten().associateWith { it }
        val tournament = Tournament(
            id = "t_4grp",
            name = "Verbands-Cup",
            sportType = SportType.INDOOR,
            fieldFormat = FieldFormat.FORMAT_6V6,
            matchConfig = MatchConfig(setsMode = SetsMode.ONE_SET, pointsPerSet = 25),
            mode = TournamentMode.GROUPS_KO,
            groupsCount = 4,
            seedingMode = SeedingMode.CROSS_SEEDING,
            teamIds = groups.values.flatten(),
            groupAssignments = groups
        )

        val groupStandings = groups.mapValues { (grp, tids) ->
            tids.mapIndexed { idx, tid ->
                Standing(tournament.id, grp, tid, tid, rank = idx + 1)
            }
        }
        val dummyCompletedGroupMatch = listOf(
            finishMatchWithWinner(
                Match("gm1", "A1", "A2", "A1", "A2", tournament.matchConfig, tournamentId = tournament.id, groupId = "A"),
                TeamSide.TEAM_A
            )
        )

        // QF should have 4 matches
        val qf = TournamentEngine.generateKoMatches(
            tournament = tournament,
            groupStandings = groupStandings,
            allGroupMatches = dummyCompletedGroupMatch,
            seedingMode = SeedingMode.CROSS_SEEDING,
            courtCount = 2
        )
        assertEquals(4, qf.size)
        assertTrue(qf.all { it.tournamentRound?.contains("Viertelfinale") == true })

        val finishedQf = qf.map { finishMatchWithWinner(it, TeamSide.TEAM_A) }
        val semis = TournamentEngine.advanceKoRoundIfReady(tournament, finishedQf, teamNames, courtCount = 2)
        assertEquals(2, semis.size)
        assertTrue(semis.all { it.tournamentRound?.contains("Halbfinale") == true })

        val finishedAllToSemis = finishedQf + semis.map { finishMatchWithWinner(it, TeamSide.TEAM_A) }
        val finals = TournamentEngine.advanceKoRoundIfReady(tournament, finishedAllToSemis, teamNames, courtCount = 2)
        assertEquals(2, finals.size)
        assertTrue(finals.any { it.tournamentRound == "Finale (Platz 1)" })
        assertTrue(finals.any { it.tournamentRound == "Spiel um Platz 3" })

        // Placement mode with 2 groups -> Platz 1, Platz 3
        val twoGroupStandings = mapOf(
            "A" to listOf(Standing("t_p", "A", "A1", "A1", rank = 1), Standing("t_p", "A", "A2", "A2", rank = 2)),
            "B" to listOf(Standing("t_p", "B", "B1", "B1", rank = 1), Standing("t_p", "B", "B2", "B2", rank = 2))
        )
        val placement = TournamentEngine.generatePlacementMatches(
            tournament = tournament.copy(id = "t_p", mode = TournamentMode.PLACEMENT, groupsCount = 2),
            groupStandings = twoGroupStandings,
            allGroupMatches = dummyCompletedGroupMatch,
            courtCount = 2
        )
        assertEquals(2, placement.size)
        assertEquals("Spiel um Platz 1 (Finale)", placement[0].tournamentRound)
        assertEquals("Spiel um Platz 3", placement[1].tournamentRound)

        // Coach mode recommendations
        assertEquals(TournamentMode.ROUND_ROBIN, TournamentEngine.coachRecommendedMode(5).first)
        assertEquals(TournamentMode.GROUPS_KO, TournamentEngine.coachRecommendedMode(10).first)
        assertEquals(TournamentMode.SWISS_SYSTEM, TournamentEngine.coachRecommendedMode(24).first)
    }

    @Test
    fun testGroupsPlayStrictlyWithinOwnGroupVsRoundRobinDoubleLeg() {
        val teams = (1..6).map { idx ->
            Team(
                id = "t$idx",
                name = "Team $idx",
                sportType = SportType.INDOOR,
                fieldPlayerCount = 6,
                squadSize = 6,
                playerIds = emptyList()
            )
        }
        val groupMap = TournamentEngine.distributeTeamsToGroups(
            teamIds = teams.map { it.id },
            manualTeams = emptyList(),
            groupsCount = 2,
            seedingMode = SeedingMode.TOP_SEEDS_SEPARATED
        )
        val teamById = teams.associateBy { it.id }
        val groupMatches = groupMap.flatMap { (grp, tids) ->
            TournamentEngine.generateRoundRobinMatches(
                tournamentId = "t_grp",
                groupId = grp,
                teams = tids.mapNotNull { teamById[it] },
                config = MatchConfig(),
                courtCount = 2
            )
        }
        // Strictly within each group: 2 groups of 3 -> 3 matches per group = 6 matches total, zero cross-group pairs
        assertEquals(6, groupMatches.size)
        val groupAIds = groupMap["A"]!!.toSet()
        val groupBIds = groupMap["B"]!!.toSet()
        assertTrue(groupMatches.all { m ->
            (m.teamAId in groupAIds && m.teamBId in groupAIds) ||
                (m.teamAId in groupBIds && m.teamBId in groupBIds)
        })

        // Single vs Double Round Robin (Hin- & Rückrunde) for unified 4-team league
        val leagueTeams = teams.take(4)
        val singleLeg = TournamentEngine.generateRoundRobinMatches(
            tournamentId = "t_rr",
            groupId = "A",
            teams = leagueTeams,
            config = MatchConfig(),
            doubleRoundRobin = false
        )
        val doubleLeg = TournamentEngine.generateRoundRobinMatches(
            tournamentId = "t_rr2",
            groupId = "A",
            teams = leagueTeams,
            config = MatchConfig(),
            doubleRoundRobin = true
        )
        assertEquals(6, singleLeg.size)
        assertEquals(12, doubleLeg.size)
        assertTrue(doubleLeg.any { it.tournamentRound?.startsWith("Hinrunde") == true })
        assertTrue(doubleLeg.any { it.tournamentRound?.startsWith("Rückrunde") == true })
    }

    @Test
    fun testRotationSystemToggleCycleAnd62Positions() {
        // Verify cyclic toggle order: 5-1 -> 4-2 -> 6-2 -> 5-1
        assertEquals(RotationSystem.SYSTEM_4_2, RotationSystem.SYSTEM_5_1.next())
        assertEquals(RotationSystem.SYSTEM_6_2, RotationSystem.SYSTEM_4_2.next())
        assertEquals(RotationSystem.SYSTEM_5_1, RotationSystem.SYSTEM_6_2.next())
        assertEquals("5-1", RotationSystem.SYSTEM_5_1.shortLabel)
        assertEquals("4-2", RotationSystem.SYSTEM_4_2.shortLabel)
        assertEquals("6-2", RotationSystem.SYSTEM_6_2.shortLabel)

        // In 6-2 RECEIVE step 0, SE1 is back-right (P1) and SE2 is front-left (P4)
        val step0Receive = RotationEngine.calculatePositions(
            TeamRotationConfig(system = RotationSystem.SYSTEM_6_2, phase = RotationPhase.RECEIVE, rotationStep = 0)
        )
        assertEquals(1, step0Receive.first { it.originalRole == "SE1" }.zoneNumber)
        assertEquals(4, step0Receive.first { it.originalRole == "SE2" }.zoneNumber)

        // In 6-2 RECEIVE step 3, SE2 is back-right (P1) and SE1 is front-left (P4)
        val step3Receive = RotationEngine.calculatePositions(
            TeamRotationConfig(system = RotationSystem.SYSTEM_6_2, phase = RotationPhase.RECEIVE, rotationStep = 3)
        )
        assertEquals(1, step3Receive.first { it.originalRole == "SE2" }.zoneNumber)
        assertEquals(4, step3Receive.first { it.originalRole == "SE1" }.zoneNumber)

        // In 6-2 IN_GAME step 3, front-row SE1 switches to P2 and back-row SE2 goes to P1
        val step3InGame = RotationEngine.calculatePositions(
            TeamRotationConfig(system = RotationSystem.SYSTEM_6_2, phase = RotationPhase.IN_GAME, rotationStep = 3)
        )
        assertEquals(2, step3InGame.first { it.originalRole == "SE1" }.inGameTargetZone)
        assertEquals(1, step3InGame.first { it.originalRole == "SE2" }.inGameTargetZone)
    }

    @Test
    fun testSpec1000PropertyTestsAndCrossMultiplicationPureJvm() {
        val (propOk, propMsg) = com.example.domain.tournament.SpecSelfTestRunner.runPropertyTests(runs = 1000)
        assertTrue("Property test failure: $propMsg", propOk)

        // Exact cross-multiplication checks (W-06): 3:2 vs 2:1 -> 3*1 < 2*2 (3 < 4 => 2:1 is higher quotient 2.0 > 1.5)
        assertTrue(
            com.example.domain.tournament.SpecScoring.cmpQuot(3, 2, 2, 1, com.example.domain.tournament.ZeroDivisorPolicy.NUMERATOR) < 0
        )
        // Zero divisor NUMERATOR: 4:0 > 2:0, and 4:0 > 100:1
        assertTrue(
            com.example.domain.tournament.SpecScoring.cmpQuot(4, 0, 2, 0, com.example.domain.tournament.ZeroDivisorPolicy.NUMERATOR) > 0
        )
        assertTrue(
            com.example.domain.tournament.SpecScoring.cmpQuot(2, 0, 100, 1, com.example.domain.tournament.ZeroDivisorPolicy.NUMERATOR) > 0
        )

        // Feasibility check scales with courtCount
        val adv1 = com.example.domain.tournament.SpecRoundRobinAndSwiss.coachRecommendation(12, courtCount = 1, slotDurationMinutes = 30)
        val adv4 = com.example.domain.tournament.SpecRoundRobinAndSwiss.coachRecommendation(12, courtCount = 4, slotDurationMinutes = 30)
        assertTrue(adv4.feasibility.estimatedSlots < adv1.feasibility.estimatedSlots)
        assertTrue(adv4.feasibility.estimatedTotalMinutes < adv1.feasibility.estimatedTotalMinutes)
    }

    @Test
    fun testScoreEngineTogglePauseFreezesNetClockAndResumes() {
        val base = Match(
            id = "pause_m1",
            teamAId = "A",
            teamBId = "B",
            teamAName = "Rot",
            teamBName = "Blau",
            matchConfig = MatchConfig(pointsPerSet = 25),
            status = MatchStatus.RUNNING,
            setStartTime = 10_000L,
            isTimerRunning = true
        )

        // Pause at t = 70_000L (60s net elapsed)
        val paused = ScoreEngine.togglePause(base, now = 70_000L)
        assertEquals(MatchStatus.PAUSED, paused.status)
        assertFalse(paused.isTimerRunning)
        assertEquals(70_000L, paused.timeoutStartTime)
        assertEquals(60L, ScoreEngine.computeElapsedNetSeconds(paused, now = 70_000L))
        // Still frozen after 120 more seconds pass
        assertEquals(60L, ScoreEngine.computeElapsedNetSeconds(paused, now = 190_000L))

        // Resume at t = 190_000L -> accumulates 120_000ms pause, resumes RUNNING, net clock still 60s
        val resumed = ScoreEngine.togglePause(paused, now = 190_000L)
        assertEquals(MatchStatus.RUNNING, resumed.status)
        assertTrue(resumed.isTimerRunning)
        assertNull(resumed.timeoutStartTime)
        assertEquals(120_000L, resumed.totalTimeoutMillis)
        assertEquals(60L, ScoreEngine.computeElapsedNetSeconds(resumed, now = 190_000L))

        // Undo restores paused state cleanly
        val undoneToPause = ScoreEngine.undo(resumed)
        assertEquals(MatchStatus.PAUSED, undoneToPause.status)
        assertEquals(70_000L, undoneToPause.timeoutStartTime)
    }

    @Test
    fun testRotationEngineResolveServerInfoWithNamesAndJerseys() {
        val cfg6v6 = TeamRotationConfig(
            system = RotationSystem.SYSTEM_5_1,
            playerCountOnCourt = 6,
            playerNames = mapOf("SE" to "Lukas", "MB1" to "Max", "OP" to "Tobias"),
            jerseyNumbers = mapOf("SE" to "1", "MB1" to "9", "OP" to "11")
        )
        // Roles in 5-1 6v6: ["SE", "OH1", "MB1", "OP", "OH2", "MB2"]
        val server0 = RotationEngine.resolveServerInfo(cfg6v6, serverIndex = 0)
        assertEquals("SE", server0.roleKey)
        assertEquals("1", server0.jerseyNumber)
        assertEquals("Lukas", server0.playerName)

        val server2 = RotationEngine.resolveServerInfo(cfg6v6, serverIndex = 2)
        assertEquals("MB1", server2.roleKey)
        assertEquals("9", server2.jerseyNumber)
        assertEquals("Max", server2.playerName)

        val cfg62 = cfg6v6.copy(
            system = RotationSystem.SYSTEM_6_2,
            playerNames = mapOf("SE1" to "Mia", "SE2" to "Lena")
        )
        val srv62 = RotationEngine.resolveServerInfo(cfg62, serverIndex = 3)
        assertEquals("SE2", srv62.roleKey)
        assertEquals("Lena", srv62.playerName)

        // Small-sided 2v2 wrap-around index (rolesFor count=2 is ["P1", "P2"])
        val cfg2v2 = TeamRotationConfig(
            playerCountOnCourt = 2,
            playerNames = mapOf("P1" to "Jan", "P2" to "Ben"),
            jerseyNumbers = mapOf("P1" to "4", "P2" to "8")
        )
        val srv2v2 = RotationEngine.resolveServerInfo(cfg2v2, serverIndex = 3)
        assertEquals("P2", srv2v2.roleKey)
        assertEquals("Ben", srv2v2.playerName)
        assertEquals("8", srv2v2.jerseyNumber)
    }

    @Test
    fun testTournamentCertificateEngineAutoPodiumTop3() {
        val teams = listOf(
            ManualTournamentTeam("t1", "Adler Berlin", club = "SC Adler", skill = 5, skillLevel = 95, registrationOrder = 0),
            ManualTournamentTeam("t2", "Elche München", club = "TSV Süd", skill = 4, skillLevel = 80, registrationOrder = 1),
            ManualTournamentTeam("t3", "Haie Hamburg", club = "HSV", skill = 3, skillLevel = 65, registrationOrder = 2),
            ManualTournamentTeam("t4", "Löwen Köln", club = "KTV", skill = 2, skillLevel = 50, registrationOrder = 3)
        )
        val tournament = Tournament(
            id = "t_cert_1",
            name = "Herbst-Open",
            sportType = SportType.INDOOR,
            fieldFormat = FieldFormat.FORMAT_6V6,
            matchConfig = MatchConfig(setsMode = SetsMode.BEST_OF_3, pointsPerSet = 25),
            mode = TournamentMode.GROUPS_KO,
            location = "Arena Nord",
            date = "16.11.2026",
            teamIds = teams.map { it.id },
            manualTeams = teams
        )
        val unfinishedFinal = Match(
            id = "ko_f",
            teamAId = "t1",
            teamBId = "t2",
            teamAName = "Adler Berlin",
            teamBName = "Elche München",
            matchConfig = tournament.matchConfig,
            status = MatchStatus.RUNNING,
            tournamentId = tournament.id,
            groupId = "KO",
            tournamentRound = "Finale (Platz 1)"
        )
        val finishedThird = Match(
            id = "ko_3",
            teamAId = "t3",
            teamBId = "t4",
            teamAName = "Haie Hamburg",
            teamBName = "Löwen Köln",
            matchConfig = tournament.matchConfig,
            status = MatchStatus.FINISHED,
            completedSets = listOf(
                SetResult(1, 25, 20, TeamSide.TEAM_A),
                SetResult(2, 25, 18, TeamSide.TEAM_A)
            ),
            tournamentId = tournament.id,
            groupId = "KO",
            tournamentRound = "Spiel um Platz 3"
        )

        val semi1 = Match(
            id = "ko_sf1",
            teamAId = "t1",
            teamBId = "t4",
            teamAName = "Adler Berlin",
            teamBName = "Löwen Köln",
            matchConfig = tournament.matchConfig,
            status = MatchStatus.FINISHED,
            completedSets = listOf(SetResult(1, 25, 18, TeamSide.TEAM_A)),
            tournamentId = tournament.id,
            groupId = "KO",
            tournamentRound = "Halbfinale 1 (A1 vs B2)"
        )
        val semi2Draft = semi1.copy(
            id = "ko_sf2",
            teamAId = "t2",
            teamBId = "t3",
            status = MatchStatus.DRAFT,
            completedSets = emptyList(),
            tournamentRound = "Halbfinale 2 (B1 vs A2)"
        )
        val qfFinished = semi1.copy(
            id = "ko_qf1",
            tournamentRound = "Viertelfinale 1"
        )
        assertFalse(TournamentCertificateEngine.isPodiumReady(tournament, listOf(semi1, semi2Draft)))
        assertFalse(TournamentCertificateEngine.isPodiumReady(tournament, listOf(qfFinished)))
        assertFalse(TournamentCertificateEngine.isPodiumReady(tournament, listOf(unfinishedFinal, finishedThird)))
        assertTrue(TournamentCertificateEngine.extractTopThreeCertificates(tournament, listOf(unfinishedFinal, finishedThird)).isEmpty())

        val finishedFinal = unfinishedFinal.copy(
            status = MatchStatus.FINISHED,
            completedSets = listOf(
                SetResult(1, 25, 22, TeamSide.TEAM_A),
                SetResult(2, 25, 19, TeamSide.TEAM_A)
            )
        )
        // Even if Finale is finished, Spiel um Platz 3 in DRAFT must still keep podium incomplete
        assertFalse(TournamentCertificateEngine.isPodiumReady(tournament, listOf(finishedFinal, finishedThird.copy(status = MatchStatus.DRAFT))))
        assertTrue(TournamentCertificateEngine.isPodiumReady(tournament, listOf(finishedFinal, finishedThird)))
        val certs = TournamentCertificateEngine.extractTopThreeCertificates(tournament, listOf(finishedFinal, finishedThird))
        assertEquals(3, certs.size)
        assertEquals(1, certs[0].rank)
        assertEquals("Adler Berlin", certs[0].teamName)
        assertEquals("SC Adler", certs[0].club)
        assertEquals("Arena Nord", certs[0].location)
        assertEquals("16.11.2026", certs[0].date)

        assertEquals(2, certs[1].rank)
        assertEquals("Elche München", certs[1].teamName)
        assertEquals("TSV Süd", certs[1].club)

        assertEquals(3, certs[2].rank)
        assertEquals("Haie Hamburg", certs[2].teamName)
        assertEquals("HSV", certs[2].club)
    }
}
