package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE isDummy = 0 ORDER BY name ASC")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id = :id AND isDummy = 0")
    suspend fun getPlayerById(id: String): PlayerEntity?

    @Upsert
    suspend fun insertOrUpdatePlayer(player: PlayerEntity)

    @Upsert
    suspend fun insertPlayers(players: List<PlayerEntity>)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayerById(id: String)

    @Query("DELETE FROM players WHERE isDummy = 1")
    suspend fun purgeDummies()
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY name ASC")
    fun getAllTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getTeamById(id: String): TeamEntity?

    @Upsert
    suspend fun insertOrUpdateTeam(team: TeamEntity)

    @Upsert
    suspend fun insertTeams(teams: List<TeamEntity>)

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteTeamById(id: String)

    @Query("DELETE FROM teams")
    suspend fun clearAllTeams()
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY id DESC")
    fun getAllMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId")
    fun getMatchesByTournament(tournamentId: String): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE tournamentId IS NULL ORDER BY id DESC LIMIT 1")
    fun getLatestStandAloneMatch(): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE tournamentId IS NULL ORDER BY id DESC")
    fun getAllStandAloneMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getMatchById(id: String): MatchEntity?

    @Upsert
    suspend fun insertOrUpdateMatch(match: MatchEntity)

    @Upsert
    suspend fun insertMatches(matches: List<MatchEntity>)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatchById(id: String)

    @Query("DELETE FROM matches WHERE tournamentId = :tournamentId")
    suspend fun deleteMatchesByTournament(tournamentId: String)

    @Query("DELETE FROM matches WHERE tournamentId IS NULL AND status = 'FINISHED'")
    suspend fun deleteFinishedStandAloneMatches()
}

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY id DESC")
    fun getAllTournaments(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournamentById(id: String): TournamentEntity?

    @Upsert
    suspend fun insertOrUpdateTournament(tournament: TournamentEntity)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournamentById(id: String)
}
