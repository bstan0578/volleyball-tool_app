package com.example.domain.tournament

import com.example.data.model.ManualTournamentTeam
import com.example.data.model.Match
import com.example.data.model.SetsMode
import com.example.data.model.TeamSide
import com.example.data.model.TournamentMode

// ergänzt gemäß SPEC G-03, W-02..W-08, F-02..F-08, P-01..P-06, R-01..R-06, E-01..E-05

enum class ScoringSystem(val label: String) {
    DVV3("DVV 3-Punkte (3:0 / 2:1)"),
    CLASSIC2("Klassisch 2-Punkte (2:0)"),
    FIXED_SETS("Feste Sätze (2:1:0)")
}

enum class MatchValidationMode(val label: String) {
    BEST_OF("Best-of (Gewinnsätze)"),
    FIXED_SETS("Feste Sätze (Remis mögl.)"),
    TIMED("Zeitspiel (1 Satz, ohne 2-Pkt)")
}

enum class WithdrawalPolicy(val label: String) {
    FORFEIT_REST("Restspiele als W.O. (2:0 / 25:0)"),
    ANNUL_ALL("Alle Spiele des Teams streichen")
}

enum class ZeroDivisorPolicy(val label: String) {
    NUMERATOR("Mehr Zähler gewinnt (x:0)"),
    EQUAL("Alle x:0 gleichwertig")
}

enum class TieBreakCriterion(val label: String) {
    MATCH_POINTS("MP"),
    WINS("Siege"),
    SET_QUOTIENT("Satzquot"),
    BALL_QUOTIENT("Ballquot"),
    HEAD_TO_HEAD("H2H"),
    LOT("Los")
}

enum class TieBreakPreset(val label: String, val criteria: List<TieBreakCriterion>) {
    DEFAULT_SPEC(
        "Standard (MP→Siege→Satz→Ball→H2H→Los)",
        listOf(
            TieBreakCriterion.MATCH_POINTS,
            TieBreakCriterion.WINS,
            TieBreakCriterion.SET_QUOTIENT,
            TieBreakCriterion.BALL_QUOTIENT,
            TieBreakCriterion.HEAD_TO_HEAD,
            TieBreakCriterion.LOT
        )
    ),
    DVV(
        "DVV (MP→Satz→Ball→H2H→Los)",
        listOf(
            TieBreakCriterion.MATCH_POINTS,
            TieBreakCriterion.SET_QUOTIENT,
            TieBreakCriterion.BALL_QUOTIENT,
            TieBreakCriterion.HEAD_TO_HEAD,
            TieBreakCriterion.LOT
        )
    ),
    FIVB(
        "FIVB (Siege→MP→Satz→Ball→H2H→Los)",
        listOf(
            TieBreakCriterion.WINS,
            TieBreakCriterion.MATCH_POINTS,
            TieBreakCriterion.SET_QUOTIENT,
            TieBreakCriterion.BALL_QUOTIENT,
            TieBreakCriterion.HEAD_TO_HEAD,
            TieBreakCriterion.LOT
        )
    )
}

enum class GroupComparisonPolicy(val label: String) {
    DROP_LAST("Gegen Gruppenletzten streichen"),
    PER_GAME("Punkte pro Spiel (Kreuzmult.)")
}

enum class ConsolationMode(val label: String) {
    NONE("Kein Spiel um Platz 3"),
    THIRD("Spiel um Platz 3"),
    FULL("Volles Platzierungs-Bracket")
}

enum class RestStrictness(val label: String) {
    STRICT("Strikt (Harte Pause)"),
    BALANCED("Ausgewogen (Standard)"),
    FAST("Schnell (Min. Gesamtdauer)")
}

enum class RefereeMode(val label: String) {
    AUTO_FAIR("Auto-Fair (Teams pfeifen)"),
    LOSER_REFS("Verlierer pfeift (weich)"),
    EXTERNAL("Externe Schiedsrichter"),
    OFF("Aus")
}

sealed class MatchSource {
    data class DirectTeam(val teamId: String) : MatchSource()
    data class GroupRank(val groupId: String, val rank: Int) : MatchSource()
    data class Wildcard(val wildcardRank: Int) : MatchSource()
    data class WinnerOf(val matchId: String) : MatchSource()
    data class LoserOf(val matchId: String) : MatchSource()
    object Bye : MatchSource()
}

data class SpecTeam(
    val id: String,
    val name: String,
    val shortName: String = name.trim().take(4).uppercase(),
    val clubId: String = "",
    val skillLevel: Int = 50, // T-01: 1..100, higher = stronger
    val present: Boolean = true,
    val withdrawn: Boolean = false,
    val registrationOrder: Int = 0
) {
    fun toManualTeam(): ManualTournamentTeam = ManualTournamentTeam(
        id = id,
        name = name.take(40),
        skill = ((skillLevel - 1) / 20 + 1).coerceIn(1, 5),
        club = clubId,
        shortName = shortName.take(4),
        skillLevel = skillLevel.coerceIn(1, 100),
        present = present,
        withdrawn = withdrawn,
        registrationOrder = registrationOrder
    )
}

data class SetScore(
    val a: Int,
    val b: Int
)

enum class SpecMatchStatus {
    SCHEDULED,
    RUNNING,
    FINAL,
    FORFEIT
}

data class SpecMatch(
    val id: String,
    val roundLabel: String,
    val groupId: String? = null, // null / "KO" / "SWISS" / "A".."D"
    val isKo: Boolean = false,
    val sourceA: MatchSource? = null,
    val sourceB: MatchSource? = null,
    val teamAId: String? = null,
    val teamBId: String? = null,
    val sets: List<SetScore> = emptyList(),
    val status: SpecMatchStatus = SpecMatchStatus.SCHEDULED,
    val winnerSide: TeamSide? = null,
    val isForfeit: Boolean = false,
    val isByeMatch: Boolean = false, // W-05: Swiss bye record only, never scheduled on court
    val slotIndex: Int? = null,
    val courtNumber: Int? = null,
    val refereeTeamIds: List<String> = emptyList(),
    val refereeManualLocked: Boolean = false,
    val dependsOnMatchIds: List<String> = emptyList(),
    val phaseRound: Int = 1,
    val isAutoWithdrawalForfeit: Boolean = false
)

data class SpecStandingRow(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val matchPoints: Int,
    val setsWon: Int,
    val setsLost: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val buchholz: Int = 0,
    val decidedBy: String = TieBreakCriterion.MATCH_POINTS.label,
    val isProvisional: Boolean = false,
    val decisionRequired: Boolean = false
)

data class AuditEntry(
    val timestampMillis: Long,
    val matchId: String,
    val summary: String,
    val reason: String
)

data class TournamentWarning(
    val specId: String,
    val message: String,
    val isError: Boolean = false
)

data class SpecTournamentConfig(
    val schemaVersion: Int = 2,
    val seed: Int = 424242,
    val scoringSystem: ScoringSystem = ScoringSystem.DVV3,
    val validationMode: MatchValidationMode = MatchValidationMode.BEST_OF,
    val setsToWin: Int = 2,
    val fixedSetsCount: Int = 2,
    val pointsPerSet: Int = 25,
    val decidingSetPoints: Int = 15,
    val pointCap: Int? = null,
    val withdrawalPolicy: WithdrawalPolicy = WithdrawalPolicy.FORFEIT_REST,
    val includeForfeitBallsInQuotient: Boolean = false,
    val zeroDivisorPolicy: ZeroDivisorPolicy = ZeroDivisorPolicy.NUMERATOR,
    val tieBreakPreset: TieBreakPreset = TieBreakPreset.DEFAULT_SPEC,
    val groupComparisonPolicy: GroupComparisonPolicy = GroupComparisonPolicy.DROP_LAST,
    val advancePerGroup: Int = 2,
    val wildcards: Int = 0,
    val koSize: Int = 4,
    val consolationMode: ConsolationMode = ConsolationMode.THIRD,
    val doubleRoundRobin: Boolean = false,
    val restStrictness: RestStrictness = RestStrictness.BALANCED,
    val minRestSlots: Int = 1,
    val refereeMode: RefereeMode = RefereeMode.AUTO_FAIR,
    val refTeamsPerMatch: Int = 2,
    val startTimeMinutes: Int = 9 * 60, // 09:00
    val slotDurationMinutes: Int = 35,
    val liveDelayMinutes: Int = 0,
    val manualSeedOrder: List<String> = emptyList(),
    val manualTieResolutions: Map<String, List<String>> = emptyMap(), // key = groupId|"OVERALL", val = ordered teamIds
    val auditLog: List<AuditEntry> = emptyList()
)

data class FeasibilityCheck(
    val estimatedSlots: Int,
    val estimatedTotalMinutes: Int,
    val koGapsWarning: String? = null,
    val durationWarning: String? = null
)

data class ScheduleResult(
    val scheduledMatches: List<SpecMatch>,
    val warnings: List<TournamentWarning>,
    val minRestAchieved: Int,
    val b2bCount: Int,
    val makespanSlots: Int
)

data class RefereeFairnessReport(
    val assignmentsByTeam: Map<String, Int>,
    val maxCount: Int,
    val minCount: Int,
    val spread: Int,
    val warningCount: Int
)

data class DerivedTeamStats(
    val teamId: String,
    val teamName: String,
    val played: Int,
    val wins: Int,
    val losses: Int,
    val matchPoints: Int,
    val setsWon: Int,
    val setsLost: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val avgPointsPerSet: String, // integer formatted X.Y without float state
    val longestWinStreak: Int,
    val minRestSlots: Int,
    val avgRestSlotsX10: Int,
    val maxRestSlots: Int,
    val b2bCount: Int,
    val refDuties: Int,
    val buchholz: Int
)

data class SelfTestCheckResult(
    val id: String,
    val category: String, // "GOLDEN", "PROPERTY", "INVARIANT"
    val title: String,
    val passed: Boolean,
    val details: String
)

data class SelfTestReport(
    val timestampMillis: Long,
    val goldenResults: List<SelfTestCheckResult>,
    val propertyRunsCompleted: Int,
    val propertyPassed: Boolean,
    val propertyFailureDetails: String?,
    val stateInvariantResults: List<SelfTestCheckResult>
) {
    val allPassed: Boolean
        get() = goldenResults.all { it.passed } && propertyPassed && stateInvariantResults.all { it.passed }
}
