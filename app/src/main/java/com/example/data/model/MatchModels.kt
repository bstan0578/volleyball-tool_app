package com.example.data.model

import java.util.UUID

enum class SetsMode(val label: String, val maxSets: Int, val setsToWin: Int) {
    ONE_SET("1 Satz", 1, 1),
    BEST_OF_2("Best-of-2 (2 Sätze)", 2, 2),
    BEST_OF_3("Best-of-3 (2 Gewinnsätze)", 3, 2),
    BEST_OF_5("Best-of-5 (3 Gewinnsätze)", 5, 3)
}

enum class TeamSide(val label: String) {
    TEAM_A("Team Rot"),
    TEAM_B("Team Blau")
}

enum class MatchStatus(val label: String) {
    DRAFT("Geplant"),
    RUNNING("Läuft"),
    PAUSED("Pausiert"),
    FINISHED("Beendet")
}

enum class MatchEventType {
    POINT,
    TIMEOUT
}

data class MatchEvent(
    val id: String = UUID.randomUUID().toString(),
    val setNumber: Int,
    val teamSide: TeamSide,
    val type: MatchEventType,
    val scoreA: Int,
    val scoreB: Int,
    val timestampMillis: Long = System.currentTimeMillis(),
    val elapsedNetSeconds: Long = 0L
)

data class SetTimeInfo(
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L,
    val netSeconds: Long = 0L,
    val timeoutSeconds: Long = 0L,
    val totalSeconds: Long = 0L
) {
    val formattedNetTime: String
        get() {
            val mins = netSeconds / 60
            val secs = netSeconds % 60
            return String.format("%02d:%02d", mins, secs)
        }
}

data class SetResult(
    val setNumber: Int,
    val scoreA: Int,
    val scoreB: Int,
    val winner: TeamSide,
    val timeInfo: SetTimeInfo? = null
)

data class MatchConfig(
    val fieldFormatA: FieldFormat = FieldFormat.FORMAT_2V2,
    val fieldFormatB: FieldFormat = FieldFormat.FORMAT_2V2,
    val setsMode: SetsMode = SetsMode.BEST_OF_3,
    val pointsPerSet: Int = 21,
    val winByTwo: Boolean = true,
    val timeLimitMinutes: Int? = null,
    val timeoutDurationSeconds: Int = 30
)

data class MatchStateSnapshot(
    val currentSet: Int,
    val scoreA: Int,
    val scoreB: Int,
    val servingTeam: TeamSide,
    val servingPlayerIndexA: Int,
    val servingPlayerIndexB: Int,
    val timeoutsA: Int,
    val timeoutsB: Int,
    val completedSets: List<SetResult>,
    val status: MatchStatus,
    val setStartTime: Long? = null,
    val timeoutStartTime: Long? = null,
    val totalTimeoutMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val events: List<MatchEvent> = emptyList(),
    val rotationStepA: Int = 0,
    val rotationStepB: Int = 0
)

data class Match(
    val id: String,
    val teamAId: String,
    val teamBId: String,
    val teamAName: String,
    val teamBName: String,
    val matchConfig: MatchConfig,
    val status: MatchStatus = MatchStatus.DRAFT,
    val currentSet: Int = 1,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val servingTeam: TeamSide = TeamSide.TEAM_A,
    val servingPlayerIndexA: Int = 0,
    val servingPlayerIndexB: Int = 0,
    val timeoutsA: Int = 0,
    val timeoutsB: Int = 0,
    val completedSets: List<SetResult> = emptyList(),
    val undoHistory: List<MatchStateSnapshot> = emptyList(),
    val tournamentId: String? = null,
    val groupId: String? = null,
    val tournamentRound: String? = null,
    val setStartTime: Long? = null,
    val setEndTime: Long? = null,
    val timeoutStartTime: Long? = null,
    val totalTimeoutMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val courtNumber: Int? = null,
    val events: List<MatchEvent> = emptyList(),
    val rotationLinked: Boolean = false,
    val rotationStepA: Int = 0,
    val rotationStepB: Int = 0
) {
    fun toSnapshot(): MatchStateSnapshot = MatchStateSnapshot(
        currentSet = currentSet,
        scoreA = scoreA,
        scoreB = scoreB,
        servingTeam = servingTeam,
        servingPlayerIndexA = servingPlayerIndexA,
        servingPlayerIndexB = servingPlayerIndexB,
        timeoutsA = timeoutsA,
        timeoutsB = timeoutsB,
        completedSets = completedSets,
        status = status,
        setStartTime = setStartTime,
        timeoutStartTime = timeoutStartTime,
        totalTimeoutMillis = totalTimeoutMillis,
        isTimerRunning = isTimerRunning,
        events = events,
        rotationStepA = rotationStepA,
        rotationStepB = rotationStepB
    )
}
