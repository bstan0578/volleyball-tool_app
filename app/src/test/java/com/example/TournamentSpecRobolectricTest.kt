package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TournamentEntity
import com.example.data.model.FieldFormat
import com.example.data.model.ManualTournamentTeam
import com.example.data.model.MatchConfig
import com.example.data.model.MatchStatus
import com.example.data.model.SetsMode
import com.example.data.model.SetResult
import com.example.data.model.SportType
import com.example.data.model.TeamSide
import com.example.data.model.Tournament
import com.example.data.model.TournamentMode
import com.example.data.model.TournamentStatus
import com.example.data.repository.VolleyRepository
import com.example.domain.tournament.*
import com.example.ui.viewmodel.VolleyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TournamentSpecRobolectricTest {

    @Test
    fun testSpecAll12GoldenTestsPass() {
        val results = SpecSelfTestRunner.runAllGoldenTests()
        assertEquals("Expected 12 Golden Tests", 12, results.size)
        val failures = results.filter { !it.passed }
        assertTrue(
            "Golden test failures: ${failures.joinToString { "${it.id}: ${it.details}" }}",
            failures.isEmpty()
        )
    }

    @Test
    fun testSpec1000PropertyTestsPass() {
        val (passed, details) = SpecSelfTestRunner.runPropertyTests(runs = 1000)
        assertTrue("Property tests failed: $details", passed)
    }

    @Test
    fun testBackupRoundTripMigrationAndSecurityRejections() {
        val teams = (1..4).map { i ->
            ManualTournamentTeam(
                id = "tm_$i",
                name = "Team $i",
                shortName = "T$i",
                club = "Club $i",
                skill = 3,
                skillLevel = 60,
                registrationOrder = i
            )
        }
        val t = Tournament(
            id = "t_backup_1",
            name = "Backup Test Cup",
            sportType = SportType.INDOOR,
            fieldFormat = FieldFormat.FORMAT_6V6,
            matchConfig = MatchConfig(),
            mode = TournamentMode.ROUND_ROBIN,
            groupsCount = 1,
            teamIds = teams.map { it.id },
            groupAssignments = mapOf("A" to teams.map { it.id }),
            manualTeams = teams,
            courtCount = 2,
            specConfig = SpecTournamentConfig(schemaVersion = 2, seed = 777),
            specMatches = SpecRoundRobinAndSwiss.generateRoundRobin(teams.map { it.id }, "A")
        )
        val json = SpecExportAndPersistence.exportBackupJson(t)
        val imported = SpecExportAndPersistence.importBackupJsonStrict(json)
        assertTrue("Valid backup should import cleanly: ${imported.errorMessage}", imported.success)
        assertEquals("Backup Test Cup", imported.tournamentName)
        assertEquals(4, imported.teams.size)
        assertEquals(6, imported.specMatches.size)

        // Prototype pollution key rejection
        val badKeyJson = json.replace("\"schemaVersion\"", "\"__proto__\": 1, \"schemaVersion\"")
        val badRes = SpecExportAndPersistence.importBackupJsonStrict(badKeyJson)
        assertFalse(badRes.success)

        // > 1 MB payload rejection
        val huge = "{\"schemaVersion\":2,\"name\":\"${"X".repeat(1_100_000)}\",\"teams\":[]}"
        val hugeRes = SpecExportAndPersistence.importBackupJsonStrict(huge)
        assertFalse(hugeRes.success)

        // V1 -> V2 migration check via jsonToSpecConfig and migrateV1ToV2
        val v1Json = """{"schemaVersion":1,"scoringSystem":"CLASSIC2","pointsPerSet":21}"""
        val migrated = SpecExportAndPersistence.jsonToSpecConfig(JSONObject(v1Json))
        assertEquals(2, migrated.schemaVersion)
        assertEquals(ScoringSystem.CLASSIC2, migrated.scoringSystem)
        assertEquals(21, migrated.pointsPerSet)
        val migratedDirect = SpecExportAndPersistence.migrateV1ToV2(SpecTournamentConfig(schemaVersion = 1, pointsPerSet = 15))
        assertEquals(2, migratedDirect.schemaVersion)
        assertEquals(15, migratedDirect.pointsPerSet)
    }

    @Test
    fun testRoomDatabaseSchemaV7RoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            val cfg = SpecTournamentConfig(schemaVersion = 2, seed = 999, scoringSystem = ScoringSystem.DVV3)
            val t = Tournament(
                id = "db_t_1",
                name = "Hallencup",
                sportType = SportType.INDOOR,
                fieldFormat = FieldFormat.FORMAT_6V6,
                matchConfig = MatchConfig(),
                mode = TournamentMode.GROUPS_KO,
                groupsCount = 2,
                location = "Halle 1",
                date = "Heute",
                courtCount = 3,
                pointCap = 28,
                pointsForWin = 3,
                specConfig = cfg
            )
            db.tournamentDao().insertOrUpdateTournament(TournamentEntity.fromDomain(t))
            val loadedEntity = db.tournamentDao().getTournamentById("db_t_1")
            assertNotNull(loadedEntity)
            val loadedDomain = loadedEntity!!.toDomain()
            assertEquals(999, loadedDomain.specConfig.seed)
            assertEquals(3, loadedDomain.courtCount)
            assertEquals(28, loadedDomain.pointCap)
        } finally {
            db.close()
        }
    }

    private suspend fun awaitUntil(step: String, timeoutMs: Long = 5000L, predicate: suspend () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!predicate()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                fail("Timed out at [$step] waiting for async Room/ViewModel state after ${timeoutMs}ms")
            }
            kotlinx.coroutines.delay(25L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testViewModelTournamentNeverLosesMatchesAndAutoGeneratesPdfCertificates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Dispatchers.setMain(Dispatchers.Unconfined)
        val dbExecutor = java.util.concurrent.Executors.newCachedThreadPool()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .setQueryExecutor(dbExecutor)
            .setTransactionExecutor(dbExecutor)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VolleyRepository(db)
            val vm = VolleyViewModel(repo)

            val manualTeams = (1..8).map { i ->
                ManualTournamentTeam(
                    id = "team_$i",
                    name = "Team $i",
                    shortName = "T$i",
                    club = "Verein $i",
                    skill = (6 - (i % 5)).coerceIn(1, 5),
                    skillLevel = 100 - i * 8,
                    registrationOrder = i - 1
                )
            }

            vm.createTournament(
                name = "Stadtmeisterschaft",
                sportType = SportType.INDOOR,
                fieldFormat = FieldFormat.FORMAT_6V6,
                matchConfig = MatchConfig(setsMode = SetsMode.BEST_OF_3, pointsPerSet = 25),
                mode = TournamentMode.GROUPS_KO,
                groupsCount = 2,
                seedingMode = com.example.data.model.SeedingMode.TOP_SEEDS_SEPARATED,
                participatingTeams = emptyList(),
                manualTeams = manualTeams,
                location = "Sporthalle West",
                date = "16.11.2026",
                courtCount = 2
            )

            awaitUntil("createTournament") { repo.allTournaments.first().isNotEmpty() }
            val created = repo.allTournaments.first().first()
            val tid = created.id
            awaitUntil("initialMatches12") { repo.getMatchesByTournament(tid).first().size == 12 }
            val initialMatches = repo.getMatchesByTournament(tid).first()
            // 2 groups of 4 teams -> 6 matches per group = 12 matches
            assertEquals(12, initialMatches.size)

            // 1. Optimize Schedule & Referees must never wipe matches!
            val prevLogCount = created.specConfig.auditLog.size
            vm.rescheduleAndReassignReferees(tid)
            awaitUntil("rescheduleAndReassignReferees") {
                (repo.getTournament(tid)?.specConfig?.auditLog?.size ?: 0) > prevLogCount
            }
            val afterOptimize = repo.getMatchesByTournament(tid).first()
            assertEquals("Plan/SR optimieren must retain all 12 matches", 12, afterOptimize.size)

            // 2. Full settings update (courts 2 -> 3, points 25 -> 21, setsMode) keeps matches intact
            vm.updateTournamentFullSettings(
                tournamentId = tid,
                name = "Stadtmeisterschaft",
                location = "Sporthalle West",
                date = "16.11.2026",
                courtCount = 3,
                status = TournamentStatus.IN_PROGRESS,
                matchConfig = MatchConfig(setsMode = SetsMode.ONE_SET, pointsPerSet = 21),
                groupsCount = 2,
                pointCap = null,
                specConfig = created.specConfig.copy(setsToWin = 1, pointsPerSet = 21, slotDurationMinutes = 20)
            )
            awaitUntil("updateTournamentFullSettings") { repo.getTournament(tid)?.courtCount == 3 }
            val afterSettings = repo.getMatchesByTournament(tid).first()
            assertEquals("Settings update must keep all 12 matches", 12, afterSettings.size)

            // 3. Complete all 12 group matches directly
            for (m in afterSettings) {
                vm.enterDirectTournamentMatchResult(
                    m,
                    listOf(SetResult(1, 21, 15, TeamSide.TEAM_A))
                )
            }
            awaitUntil("finishAllGroupMatches") {
                repo.getMatchesByTournament(tid).first().all { it.status == MatchStatus.FINISHED }
            }

            // 4. Generate KO semifinals & finish them
            val tAfterGroups = repo.getTournament(tid)!!
            vm.generateKoMatchesForTournament(tAfterGroups)
            awaitUntil("generateKoSemis") {
                repo.getMatchesByTournament(tid).first().count { it.groupId == "KO" } == 2
            }
            val koSemis = repo.getMatchesByTournament(tid).first().filter { it.groupId == "KO" }
            assertEquals(2, koSemis.size)
            for (semi in koSemis) {
                vm.enterDirectTournamentMatchResult(
                    semi,
                    listOf(SetResult(1, 21, 17, TeamSide.TEAM_A))
                )
            }
            awaitUntil("finishKoSemis") {
                repo.getMatchesByTournament(tid).first()
                    .filter { it.groupId == "KO" }
                    .all { it.status == MatchStatus.FINISHED }
            }

            // 5. Advance to Finale & Spiel um Platz 3
            vm.advanceTournamentKoRound(repo.getTournament(tid)!!)
            awaitUntil("advanceKoToFinals") {
                repo.getMatchesByTournament(tid).first().count { it.groupId == "KO" } == 4
            }
            val finals = repo.getMatchesByTournament(tid).first()
                .filter { it.groupId == "KO" && it.status != MatchStatus.FINISHED }
            assertEquals(2, finals.size)

            for (fm in finals) {
                vm.enterDirectTournamentMatchResult(
                    fm,
                    listOf(SetResult(1, 21, 18, TeamSide.TEAM_A))
                )
            }
            awaitUntil("tournamentCompletedWithCertificates") {
                repo.getTournament(tid)?.status == TournamentStatus.COMPLETED
            }

            val completedTournament = repo.getTournament(tid)!!
            val allDbMatches = repo.getMatchesByTournament(tid).first()
            val koDiag = allDbMatches.filter { it.groupId == "KO" }.joinToString(" | ") { "${it.id}:${it.tournamentRound}:${it.status}" }
            val specKoDiag = completedTournament.specMatches.filter { it.isKo || it.groupId == "KO" }
                .joinToString(" | ") { "${it.id}:${it.roundLabel}:${it.status}:bye=${it.isByeMatch}:A=${it.teamAId}:B=${it.teamBId}" }
            val auditDiag = completedTournament.specConfig.auditLog.joinToString(" || ") { "${it.reason}:${it.matchId}:${it.summary}" }
            assertEquals(TournamentStatus.COMPLETED, completedTournament.status)
            assertEquals(tid, vm.autoOpenCertificatesTournamentId.value)
            assertTrue(
                "isPodiumReady failed (mode=${completedTournament.mode}, dbKo=[$koDiag], specKo=[$specKoDiag], audit=[$auditDiag])",
                TournamentCertificateEngine.isPodiumReady(completedTournament, allDbMatches)
            )

            val certs = TournamentCertificateEngine.extractTopThreeCertificates(completedTournament, allDbMatches)
            assertEquals(3, certs.size)
            assertEquals(listOf(1, 2, 3), certs.map { it.rank })

            // 6. Verify native PDF generation for all 3 template styles (Jugend Haikyu, Klassisch, Modern)
            CertificateStyle.values().forEach { style ->
                val pdfBytes = CertificatePdfExporter.generateCertificatesPdfBytes(
                    context = context,
                    entries = certs,
                    style = style
                )
                assertTrue("PDF for ${style.name} should be non-empty", pdfBytes.size > 1024)
                val header = String(pdfBytes.copyOfRange(0, 5), Charsets.US_ASCII)
                assertEquals("PDF for ${style.name} must start with %PDF-", "%PDF-", header)
            }
        } finally {
            Dispatchers.resetMain()
            db.close()
            dbExecutor.shutdownNow()
        }
    }
}
