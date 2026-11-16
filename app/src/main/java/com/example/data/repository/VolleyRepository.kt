package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VolleyRepository(private val database: AppDatabase) {
    private val playerDao = database.playerDao()
    private val teamDao = database.teamDao()
    private val matchDao = database.matchDao()
    private val tournamentDao = database.tournamentDao()

    // Players
    val allPlayers: Flow<List<Player>> = playerDao.getAllPlayers().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getPlayer(id: String): Player? = playerDao.getPlayerById(id)?.toDomain()

    suspend fun savePlayer(player: Player) {
        playerDao.insertOrUpdatePlayer(PlayerEntity.fromDomain(player))
    }

    suspend fun savePlayers(players: List<Player>) {
        playerDao.insertPlayers(players.map { PlayerEntity.fromDomain(it) })
    }

    suspend fun deletePlayer(id: String) {
        playerDao.deletePlayerById(id)
    }

    suspend fun purgeDummies() {
        playerDao.purgeDummies()
    }

    // Teams
    val allTeams: Flow<List<Team>> = teamDao.getAllTeams().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getTeam(id: String): Team? = teamDao.getTeamById(id)?.toDomain()

    suspend fun saveTeam(team: Team) {
        teamDao.insertOrUpdateTeam(TeamEntity.fromDomain(team))
    }

    suspend fun saveTeams(teams: List<Team>) {
        teamDao.insertTeams(teams.map { TeamEntity.fromDomain(it) })
    }

    suspend fun deleteTeam(id: String) {
        teamDao.deleteTeamById(id)
    }

    suspend fun clearTeams() {
        teamDao.clearAllTeams()
    }

    // Matches
    val allMatches: Flow<List<Match>> = matchDao.getAllMatches().map { list ->
        list.map { it.toDomain() }
    }

    val latestStandaloneMatch: Flow<Match?> = matchDao.getLatestStandAloneMatch().map { it?.toDomain() }

    val allStandAloneMatches: Flow<List<Match>> = matchDao.getAllStandAloneMatches().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun resetScoreboardHistory() {
        matchDao.deleteFinishedStandAloneMatches()
    }

    fun getMatchesByTournament(tournamentId: String): Flow<List<Match>> =
        matchDao.getMatchesByTournament(tournamentId).map { list -> list.map { it.toDomain() } }

    suspend fun getMatch(id: String): Match? = matchDao.getMatchById(id)?.toDomain()

    suspend fun saveMatch(match: Match) {
        matchDao.insertOrUpdateMatch(MatchEntity.fromDomain(match))
    }

    suspend fun saveMatches(matches: List<Match>) {
        matchDao.insertMatches(matches.map { MatchEntity.fromDomain(it) })
    }

    suspend fun deleteMatch(id: String) {
        matchDao.deleteMatchById(id)
    }

    suspend fun deleteMatchesByTournament(tournamentId: String) {
        matchDao.deleteMatchesByTournament(tournamentId)
    }

    // Tournaments
    val allTournaments: Flow<List<Tournament>> = tournamentDao.getAllTournaments().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getTournament(id: String): Tournament? = tournamentDao.getTournamentById(id)?.toDomain()

    suspend fun saveTournament(tournament: Tournament) {
        tournamentDao.insertOrUpdateTournament(TournamentEntity.fromDomain(tournament))
    }

    suspend fun syncTournamentAndMatches(
        tournament: Tournament,
        matchesToUpsert: List<Match>,
        matchIdsToDelete: List<String> = emptyList()
    ) {
        database.withTransaction {
            tournamentDao.insertOrUpdateTournament(TournamentEntity.fromDomain(tournament))
            matchIdsToDelete.forEach { id ->
                matchDao.deleteMatchById(id)
            }
            if (matchesToUpsert.isNotEmpty()) {
                matchDao.insertMatches(matchesToUpsert.map { MatchEntity.fromDomain(it) })
            }
        }
    }

    suspend fun replaceTournamentSnapshot(tournament: Tournament, matches: List<Match>) {
        database.withTransaction {
            matchDao.deleteMatchesByTournament(tournament.id)
            tournamentDao.insertOrUpdateTournament(TournamentEntity.fromDomain(tournament))
            if (matches.isNotEmpty()) {
                matchDao.insertMatches(matches.map { MatchEntity.fromDomain(it) })
            }
        }
    }

    suspend fun deleteTournament(id: String) {
        matchDao.deleteMatchesByTournament(id)
        tournamentDao.deleteTournamentById(id)
    }
}
