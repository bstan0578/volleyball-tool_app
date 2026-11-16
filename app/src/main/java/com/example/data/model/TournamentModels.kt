package com.example.data.model

import com.example.domain.tournament.SpecMatch
import com.example.domain.tournament.SpecTournamentConfig
import java.util.UUID

enum class TournamentMode(val label: String, val coachHint: String) {
    GROUPS_KO(
        "Gruppenphase + K.-o.",
        "Spiele ausschließlich innerhalb jeder Gruppe, danach Überkreuz-K.-o. (Viertel-/Halbfinale/Finale)."
    ),
    ROUND_ROBIN(
        "Jeder gegen Jeden (Liga)",
        "Alle Teams spielen in einer einzigen gemeinsamen Liga gegeneinander — ohne Gruppen und ohne K.-o."
    ),
    SINGLE_ELIMINATION(
        "Direktes K.-o. (Pokal)",
        "Gesetzter K.-o.-Turnierbaum ab Runde 1 (mit automatischen Freilosen bei ungerader Teamzahl)."
    ),
    SWISS_SYSTEM(
        "Schweizer System",
        "Jede Runde treten Punktgleiche gegeneinander an, ohne dass Begegnungen wiederholt werden."
    ),
    PLACEMENT(
        "Gruppen + Platzierung",
        "Spiele nur innerhalb jeder Gruppe, danach direkte Platzierungsspiele (Platz 1, Platz 3, Platz 5...)."
    ),
    DOUBLE_ELIMINATION(
        "Doppel-K.-o.",
        "K.-o.-Turnierbaum mit Sieger- und Verliererrunde."
    ),
    KING_OF_THE_COURT(
        "King of the Court",
        "Schnelles Rotationsformat auf Zeit."
    )
}

enum class SeedingMode(val label: String) {
    CROSS_SEEDING("Cross-Seeding (A1 vs B2)"),
    RANDOM_DRAW("Zufallslosung"),
    TOP_SEEDS_SEPARATED("Top-Seeds getrennt (1 & 2)"),
    CLUB_SEPARATION("Vereins-/Herkunftstrennung")
}

enum class TournamentStatus(val label: String) {
    DRAFT("Planung (Draft)"),
    IN_PROGRESS("Aktiv"),
    COMPLETED("Abgeschlossen")
}

data class Standing(
    val tournamentId: String,
    val groupId: String,
    val teamId: String,
    val teamName: String,
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val points: Int = 0, // Tournament table points (e.g. 2 for win, 1 for draw)
    val setsWon: Int = 0,
    val setsLost: Int = 0,
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0,
    val rank: Int = 1
) {
    val setDiff: Int get() = setsWon - setsLost
    val pointsDiff: Int get() = pointsFor - pointsAgainst
}

data class ManualTournamentTeam(
    val id: String = "mteam_${UUID.randomUUID()}",
    val name: String,
    val skill: Int = 3,
    val maleCount: Int = 0,
    val femaleCount: Int = 0,
    val totalAge: Int = 0,
    val club: String = "",
    val note: String = "",
    val shortName: String = name.trim().take(4).uppercase(),
    val skillLevel: Int = (skill.coerceIn(1, 5) * 20).coerceIn(1, 100),
    val present: Boolean = true,
    val withdrawn: Boolean = false,
    val registrationOrder: Int = 0
)

data class TournamentTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val sportType: SportType,
    val fieldFormat: FieldFormat,
    val mode: TournamentMode,
    val setsMode: SetsMode,
    val pointsPerSet: Int,
    val winByTwo: Boolean = true,
    val decidingSetPoints: Int = 15,
    val pointCap: Int? = null,
    val timeLimitMinutes: Int? = null,
    val minFemales: Int = 0,
    val minMales: Int = 0,
    val pointsForWin: Int = 2,
    val pointsForDraw: Int = 1,
    val pointsForLoss: Int = 0,
    val defaultCourts: Int = 2
) {
    companion object {
        val ALL: List<TournamentTemplate> = listOf(
            TournamentTemplate(
                id = "beach_2v2_classic",
                name = "Beach 2x2 Classic",
                subtitle = "Gruppen + K.-o., Best of 3 bis 21 (Entsch. 15)",
                sportType = SportType.BEACH,
                fieldFormat = FieldFormat.FORMAT_2V2,
                mode = TournamentMode.GROUPS_KO,
                setsMode = SetsMode.BEST_OF_3,
                pointsPerSet = 21,
                winByTwo = true,
                decidingSetPoints = 15,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "beach_2v2_quick",
                name = "Beach 2x2 Schnellturnier",
                subtitle = "Jeder gegen Jeden, 1 Satz bis 21",
                sportType = SportType.BEACH,
                fieldFormat = FieldFormat.FORMAT_2V2,
                mode = TournamentMode.ROUND_ROBIN,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 21,
                winByTwo = true,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "beach_4v4_fun",
                name = "Beach 4x4 Fun",
                subtitle = "Gruppen + K.-o., Golden Set bis 21",
                sportType = SportType.BEACH,
                fieldFormat = FieldFormat.FORMAT_4V4,
                mode = TournamentMode.GROUPS_KO,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 21,
                winByTwo = true,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "mixed_4v4",
                name = "Mixed 4x4",
                subtitle = "Gruppen + K.-o., 1 Satz bis 21, min. 2 Frauen",
                sportType = SportType.ANY,
                fieldFormat = FieldFormat.FORMAT_4V4,
                mode = TournamentMode.GROUPS_KO,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 21,
                minFemales = 2,
                minMales = 1,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "mixed_6v6",
                name = "Mixed 6x6",
                subtitle = "Gruppen + K.-o., 1 Satz bis 25, min. 2 Frauen",
                sportType = SportType.INDOOR,
                fieldFormat = FieldFormat.FORMAT_6V6,
                mode = TournamentMode.GROUPS_KO,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 25,
                minFemales = 2,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "indoor_6v6_cup",
                name = "Hallen 6x6 Turnier",
                subtitle = "Gruppen + K.-o., Best of 3 bis 25 (Entsch. 15)",
                sportType = SportType.INDOOR,
                fieldFormat = FieldFormat.FORMAT_6V6,
                mode = TournamentMode.GROUPS_KO,
                setsMode = SetsMode.BEST_OF_3,
                pointsPerSet = 25,
                winByTwo = true,
                decidingSetPoints = 15,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "indoor_6v6_league",
                name = "Hallen 6x6 Liga",
                subtitle = "Liga/Round Robin, 2 Gewinnsätze bis 25",
                sportType = SportType.INDOOR,
                fieldFormat = FieldFormat.FORMAT_6V6,
                mode = TournamentMode.ROUND_ROBIN,
                setsMode = SetsMode.BEST_OF_3,
                pointsPerSet = 25,
                winByTwo = true,
                defaultCourts = 1
            ),
            TournamentTemplate(
                id = "youth_u_team",
                name = "Jugendturnier U-Team",
                subtitle = "4x4 Gruppen + Platzierungsspiele bis 15",
                sportType = SportType.INDOOR,
                fieldFormat = FieldFormat.FORMAT_4V4,
                mode = TournamentMode.PLACEMENT,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 15,
                winByTwo = true,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "company_event",
                name = "Firmen- / Teamevent",
                subtitle = "4x4 Zeitspiel 12 Min, Jeder gegen Jeden",
                sportType = SportType.ANY,
                fieldFormat = FieldFormat.FORMAT_4V4,
                mode = TournamentMode.ROUND_ROBIN,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 21,
                timeLimitMinutes = 12,
                defaultCourts = 3
            ),
            TournamentTemplate(
                id = "swiss_tournament",
                name = "Swiss Turnier",
                subtitle = "Schweizer System über feste Runden, 1 Satz bis 21",
                sportType = SportType.ANY,
                fieldFormat = FieldFormat.FORMAT_4V4,
                mode = TournamentMode.SWISS_SYSTEM,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 21,
                defaultCourts = 2
            ),
            TournamentTemplate(
                id = "king_of_court",
                name = "King of the Court",
                subtitle = "Beach 2x2, Zeitfenster 15 Min, Rotationsmodus",
                sportType = SportType.BEACH,
                fieldFormat = FieldFormat.FORMAT_2V2,
                mode = TournamentMode.KING_OF_THE_COURT,
                setsMode = SetsMode.ONE_SET,
                pointsPerSet = 21,
                timeLimitMinutes = 15,
                defaultCourts = 1
            ),
            TournamentTemplate(
                id = "ko_cup",
                name = "Pokal K.-o. (Direkt)",
                subtitle = "Direktes K.-o.-System mit Seeding & Freilosen, Best of 3",
                sportType = SportType.ANY,
                fieldFormat = FieldFormat.FORMAT_6V6,
                mode = TournamentMode.SINGLE_ELIMINATION,
                setsMode = SetsMode.BEST_OF_3,
                pointsPerSet = 25,
                winByTwo = true,
                decidingSetPoints = 15,
                defaultCourts = 2
            )
        )
    }
}

data class Tournament(
    val id: String,
    val name: String,
    val sportType: SportType,
    val fieldFormat: FieldFormat,
    val matchConfig: MatchConfig,
    val mode: TournamentMode,
    val status: TournamentStatus = TournamentStatus.DRAFT,
    val groupsCount: Int = 2,
    val seedingMode: SeedingMode = SeedingMode.CROSS_SEEDING,
    val teamIds: List<String> = emptyList(),
    val groupAssignments: Map<String, List<String>> = emptyMap(), // GroupId -> List<TeamId>
    val manualTeams: List<ManualTournamentTeam> = emptyList(),
    val location: String = "",
    val date: String = "",
    val courtCount: Int = 1,
    val templateId: String? = null,
    val pointCap: Int? = null,
    val pointsForWin: Int = 2,
    val pointsForDraw: Int = 1,
    val pointsForLoss: Int = 0,
    val minFemales: Int = 0,
    val minMales: Int = 0,
    val specConfig: SpecTournamentConfig = SpecTournamentConfig(),
    val specMatches: List<SpecMatch> = emptyList()
)
