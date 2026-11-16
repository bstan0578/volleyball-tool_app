package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.*

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val skill: Int,
    val heightClass: String,
    val gender: String = "DIVERSE",
    val isDummy: Boolean,
    val tagsJson: String = "[]",
    val isSelectedForMixer: Boolean = false
) {
    fun toDomain(): Player = Player(
        id = id,
        name = name,
        skill = skill,
        heightClass = try { HeightClass.valueOf(heightClass) } catch (e: Exception) { HeightClass.MEDIUM },
        gender = try { Gender.valueOf(gender) } catch (e: Exception) { Gender.DIVERSE },
        isDummy = isDummy,
        tags = JsonHelper.jsonToPlayerIds(tagsJson),
        isSelectedForMixer = isSelectedForMixer
    )

    companion object {
        fun fromDomain(player: Player): PlayerEntity = PlayerEntity(
            id = player.id,
            name = player.name,
            skill = player.skill,
            heightClass = player.heightClass.name,
            gender = player.gender.name,
            isDummy = player.isDummy,
            tagsJson = JsonHelper.playerIdsToJson(player.tags),
            isSelectedForMixer = player.isSelectedForMixer
        )
    }
}

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sportType: String,
    val fieldPlayerCount: Int,
    val squadSize: Int,
    val playerIdsJson: String,
    val isFinalized: Boolean
) {
    fun toDomain(): Team = Team(
        id = id,
        name = name,
        sportType = try { SportType.valueOf(sportType) } catch (e: Exception) { SportType.ANY },
        fieldPlayerCount = fieldPlayerCount,
        squadSize = squadSize,
        playerIds = JsonHelper.jsonToPlayerIds(playerIdsJson),
        isFinalized = isFinalized
    )

    companion object {
        fun fromDomain(team: Team): TeamEntity = TeamEntity(
            id = team.id,
            name = team.name,
            sportType = team.sportType.name,
            fieldPlayerCount = team.fieldPlayerCount,
            squadSize = team.squadSize,
            playerIdsJson = JsonHelper.playerIdsToJson(team.playerIds),
            isFinalized = team.isFinalized
        )
    }
}

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tournamentId")]
)
data class MatchEntity(
    @PrimaryKey val id: String,
    val teamAId: String,
    val teamBId: String,
    val teamAName: String,
    val teamBName: String,
    val matchConfigJson: String,
    val status: String,
    val currentSet: Int,
    val scoreA: Int,
    val scoreB: Int,
    val servingTeam: String,
    val servingPlayerIndexA: Int,
    val servingPlayerIndexB: Int,
    val timeoutsA: Int,
    val timeoutsB: Int,
    val completedSetsJson: String,
    val undoHistoryJson: String,
    val tournamentId: String?,
    val groupId: String?,
    val tournamentRound: String?,
    val setStartTime: Long? = null,
    val setEndTime: Long? = null,
    val timeoutStartTime: Long? = null,
    val totalTimeoutMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val courtNumber: Int? = null,
    val eventsJson: String = "[]",
    val rotationLinked: Boolean = false,
    val rotationStepA: Int = 0,
    val rotationStepB: Int = 0
) {
    fun toDomain(): Match = Match(
        id = id,
        teamAId = teamAId,
        teamBId = teamBId,
        teamAName = teamAName,
        teamBName = teamBName,
        matchConfig = JsonHelper.jsonToMatchConfig(matchConfigJson),
        status = try { MatchStatus.valueOf(status) } catch (e: Exception) { MatchStatus.RUNNING },
        currentSet = currentSet,
        scoreA = scoreA,
        scoreB = scoreB,
        servingTeam = try { TeamSide.valueOf(servingTeam) } catch (e: Exception) { TeamSide.TEAM_A },
        servingPlayerIndexA = servingPlayerIndexA,
        servingPlayerIndexB = servingPlayerIndexB,
        timeoutsA = timeoutsA,
        timeoutsB = timeoutsB,
        completedSets = JsonHelper.jsonToCompletedSets(completedSetsJson),
        undoHistory = JsonHelper.jsonToUndoHistory(undoHistoryJson),
        tournamentId = tournamentId,
        groupId = groupId,
        tournamentRound = tournamentRound,
        setStartTime = setStartTime,
        setEndTime = setEndTime,
        timeoutStartTime = timeoutStartTime,
        totalTimeoutMillis = totalTimeoutMillis,
        isTimerRunning = isTimerRunning,
        courtNumber = courtNumber,
        events = JsonHelper.jsonToMatchEvents(eventsJson),
        rotationLinked = rotationLinked,
        rotationStepA = rotationStepA,
        rotationStepB = rotationStepB
    )

    companion object {
        fun fromDomain(match: Match): MatchEntity = MatchEntity(
            id = match.id,
            teamAId = match.teamAId,
            teamBId = match.teamBId,
            teamAName = match.teamAName,
            teamBName = match.teamBName,
            matchConfigJson = JsonHelper.matchConfigToJson(match.matchConfig),
            status = match.status.name,
            currentSet = match.currentSet,
            scoreA = match.scoreA,
            scoreB = match.scoreB,
            servingTeam = match.servingTeam.name,
            servingPlayerIndexA = match.servingPlayerIndexA,
            servingPlayerIndexB = match.servingPlayerIndexB,
            timeoutsA = match.timeoutsA,
            timeoutsB = match.timeoutsB,
            completedSetsJson = JsonHelper.completedSetsToJson(match.completedSets),
            undoHistoryJson = JsonHelper.undoHistoryToJson(match.undoHistory),
            tournamentId = match.tournamentId,
            groupId = match.groupId,
            tournamentRound = match.tournamentRound,
            setStartTime = match.setStartTime,
            setEndTime = match.setEndTime,
            timeoutStartTime = match.timeoutStartTime,
            totalTimeoutMillis = match.totalTimeoutMillis,
            isTimerRunning = match.isTimerRunning,
            courtNumber = match.courtNumber,
            eventsJson = JsonHelper.matchEventsToJson(match.events),
            rotationLinked = match.rotationLinked,
            rotationStepA = match.rotationStepA,
            rotationStepB = match.rotationStepB
        )
    }
}

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sportType: String,
    val fieldFormat: String,
    val matchConfigJson: String,
    val mode: String,
    val status: String,
    val groupsCount: Int,
    val seedingMode: String,
    val teamIdsJson: String,
    val groupAssignmentsJson: String,
    val manualTeamsJson: String = "[]",
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
    val specDataJson: String = "{}"
) {
    fun toDomain(): Tournament {
        val (cfg, sMatches) = com.example.domain.tournament.SpecExportAndPersistence.jsonToSpecData(specDataJson)
        return Tournament(
            id = id,
            name = name,
            sportType = try { SportType.valueOf(sportType) } catch (e: Exception) { SportType.ANY },
            fieldFormat = try { FieldFormat.valueOf(fieldFormat) } catch (e: Exception) { FieldFormat.FORMAT_2V2 },
            matchConfig = JsonHelper.jsonToMatchConfig(matchConfigJson),
            mode = try { TournamentMode.valueOf(mode) } catch (e: Exception) { TournamentMode.GROUPS_KO },
            status = try { TournamentStatus.valueOf(status) } catch (e: Exception) { TournamentStatus.DRAFT },
            groupsCount = groupsCount,
            seedingMode = try { SeedingMode.valueOf(seedingMode) } catch (e: Exception) { SeedingMode.CROSS_SEEDING },
            teamIds = JsonHelper.jsonToPlayerIds(teamIdsJson),
            groupAssignments = JsonHelper.jsonToGroupAssignments(groupAssignmentsJson),
            manualTeams = JsonHelper.jsonToManualTeams(manualTeamsJson),
            location = location,
            date = date,
            courtCount = courtCount,
            templateId = templateId,
            pointCap = pointCap,
            pointsForWin = pointsForWin,
            pointsForDraw = pointsForDraw,
            pointsForLoss = pointsForLoss,
            minFemales = minFemales,
            minMales = minMales,
            specConfig = cfg,
            specMatches = sMatches
        )
    }

    companion object {
        fun fromDomain(t: Tournament): TournamentEntity = TournamentEntity(
            id = t.id,
            name = t.name,
            sportType = t.sportType.name,
            fieldFormat = t.fieldFormat.name,
            matchConfigJson = JsonHelper.matchConfigToJson(t.matchConfig),
            mode = t.mode.name,
            status = t.status.name,
            groupsCount = t.groupsCount,
            seedingMode = t.seedingMode.name,
            teamIdsJson = JsonHelper.playerIdsToJson(t.teamIds),
            groupAssignmentsJson = JsonHelper.groupAssignmentsToJson(t.groupAssignments),
            manualTeamsJson = JsonHelper.manualTeamsToJson(t.manualTeams),
            location = t.location,
            date = t.date,
            courtCount = t.courtCount,
            templateId = t.templateId,
            pointCap = t.pointCap,
            pointsForWin = t.pointsForWin,
            pointsForDraw = t.pointsForDraw,
            pointsForLoss = t.pointsForLoss,
            minFemales = t.minFemales,
            minMales = t.minMales,
            specDataJson = com.example.domain.tournament.SpecExportAndPersistence.specDataToJson(t.specConfig, t.specMatches)
        )
    }
}
