package com.example.domain.tournament

import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.Team
import com.example.data.model.TeamSide
import com.example.data.model.Tournament
import com.example.data.model.TournamentMode
import com.example.domain.engine.TournamentEngine

enum class CertificateStyle(
    val id: String,
    val label: String,
    val subtitle: String
) {
    YOUTH_HAIKYU(
        id = "youth_haikyu",
        label = "Jugend (Haikyu)",
        subtitle = "Dynamischer Anime-Volleyball-Look"
    ),
    CLASSIC(
        id = "classic",
        label = "Klassisch",
        subtitle = "Edler Gold-Lorbeer & Verbandsstil"
    ),
    MODERN(
        id = "modern",
        label = "Modern",
        subtitle = "Klarer Turnier-Look in Marine & Cyan"
    )
}

data class CertificateEntry(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val club: String,
    val tournamentId: String,
    val tournamentName: String,
    val date: String,
    val location: String,
    val formatLabel: String,
    val modeLabel: String,
    val recordSummary: String
)

object TournamentCertificateEngine {

    private val platz1Regex = Regex("""\bPlatz\s*1\b""", RegexOption.IGNORE_CASE)
    private val platz3Regex = Regex("""\bPlatz\s*3\b""", RegexOption.IGNORE_CASE)

    private fun isFinalMatch(m: Match): Boolean {
        val r = m.tournamentRound.orEmpty().trim()
        if (r.isEmpty()) return false
        if (r.contains("Halbfinale", ignoreCase = true) ||
            r.contains("Viertelfinale", ignoreCase = true) ||
            r.contains("Achtelfinale", ignoreCase = true) ||
            r.contains("Sechzehntelfinale", ignoreCase = true)
        ) {
            return false
        }
        return r.startsWith("Finale", ignoreCase = true) ||
            r.contains("(Finale)", ignoreCase = true) ||
            platz1Regex.containsMatchIn(r)
    }

    private fun isThirdPlaceMatch(m: Match): Boolean {
        val r = m.tournamentRound.orEmpty().trim()
        if (r.isEmpty()) return false
        return platz3Regex.containsMatchIn(r)
    }

    private fun isSemiFinalMatch(m: Match): Boolean {
        val r = m.tournamentRound.orEmpty()
        return r.contains("Halbfinale", ignoreCase = true)
    }

    private fun winnerAndLoserIds(m: Match): Pair<String, String> {
        val setsA = m.completedSets.count { it.winner == TeamSide.TEAM_A }
        val setsB = m.completedSets.count { it.winner == TeamSide.TEAM_B }
        val aWon = when {
            setsA != setsB -> setsA > setsB
            m.scoreA != m.scoreB -> m.scoreA > m.scoreB
            else -> true
        }
        return if (aWon) (m.teamAId to m.teamBId) else (m.teamBId to m.teamAId)
    }

    private fun formatMatchScoreSummary(m: Match, prefix: String): String {
        val scoreStr = if (m.completedSets.isNotEmpty()) {
            m.completedSets.joinToString(", ") { "${it.scoreA}:${it.scoreB}" }
        } else {
            "${m.scoreA}:${m.scoreB}"
        }
        return "$prefix ($scoreStr)"
    }

    /**
     * Checks if the tournament's decisive KO / Placement podium (Places 1-3) is finished
     * so certificates can be generated automatically.
     */
    fun isPodiumReady(tournament: Tournament, matches: List<Match>): Boolean {
        if (matches.isEmpty()) return false
        return when (tournament.mode) {
            TournamentMode.GROUPS_KO,
            TournamentMode.SINGLE_ELIMINATION,
            TournamentMode.DOUBLE_ELIMINATION,
            TournamentMode.PLACEMENT -> {
                val koMatches = matches.filter { it.groupId == "KO" || isFinalMatch(it) || isThirdPlaceMatch(it) }
                if (koMatches.any { isSemiFinalMatch(it) && it.status != MatchStatus.FINISHED }) return false
                val finalMatch = koMatches.firstOrNull { isFinalMatch(it) } ?: return false
                if (finalMatch.status != MatchStatus.FINISHED) return false
                val thirdMatch = koMatches.firstOrNull { isThirdPlaceMatch(it) }
                thirdMatch == null || thirdMatch.status == MatchStatus.FINISHED
            }
            TournamentMode.ROUND_ROBIN,
            TournamentMode.SWISS_SYSTEM,
            TournamentMode.KING_OF_THE_COURT -> {
                matches.all { it.status == MatchStatus.FINISHED }
            }
        }
    }

    /**
     * Automatically builds Place 1, 2, 3 CertificateEntry objects with resolved team names,
     * club affiliation, match/set summary, tournament date, and venue.
     */
    fun extractTopThreeCertificates(
        tournament: Tournament,
        matches: List<Match>,
        dbTeams: List<Team> = emptyList()
    ): List<CertificateEntry> {
        if (!isPodiumReady(tournament, matches)) return emptyList()

        val teamNames = mutableMapOf<String, String>()
        dbTeams.forEach { teamNames[it.id] = it.name }
        tournament.manualTeams.forEach { teamNames[it.id] = it.name }
        matches.forEach { m ->
            if (m.teamAId.isNotBlank() && m.teamAName.isNotBlank()) teamNames.putIfAbsent(m.teamAId, m.teamAName)
            if (m.teamBId.isNotBlank() && m.teamBName.isNotBlank()) teamNames.putIfAbsent(m.teamBId, m.teamBName)
        }
        val clubById = tournament.manualTeams.associate { it.id to it.club.trim() }

        val overallStandings = TournamentEngine.calculateSpecStandings(
            tournament = tournament,
            groupId = "",
            teamIds = tournament.teamIds,
            teamNames = teamNames,
            matches = matches
        )

        val entries = mutableListOf<CertificateEntry>()

        fun makeEntry(rank: Int, tid: String, summary: String): CertificateEntry {
            return CertificateEntry(
                rank = rank,
                teamId = tid,
                teamName = teamNames[tid] ?: tid,
                club = clubById[tid].orEmpty(),
                tournamentId = tournament.id,
                tournamentName = tournament.name,
                date = tournament.date.trim(),
                location = tournament.location.trim(),
                formatLabel = "${tournament.sportType.label} · ${tournament.fieldFormat.label}",
                modeLabel = tournament.mode.label,
                recordSummary = summary
            )
        }

        val koMatches = matches.filter { it.groupId == "KO" || isFinalMatch(it) || isThirdPlaceMatch(it) }
        val finalMatch = koMatches.firstOrNull { isFinalMatch(it) && it.status == MatchStatus.FINISHED }

        if (finalMatch != null) {
            val (firstId, secondId) = winnerAndLoserIds(finalMatch)
            entries.add(makeEntry(1, firstId, formatMatchScoreSummary(finalMatch, "Turniersieger · Finale")))
            entries.add(makeEntry(2, secondId, formatMatchScoreSummary(finalMatch, "Finalist · 2. Platz")))

            val thirdMatch = koMatches.firstOrNull { isThirdPlaceMatch(it) && it.status == MatchStatus.FINISHED }
            if (thirdMatch != null) {
                val (thirdId, _) = winnerAndLoserIds(thirdMatch)
                entries.add(makeEntry(3, thirdId, formatMatchScoreSummary(thirdMatch, "Sieger Spiel um Platz 3")))
            } else {
                val usedIds = setOf(firstId, secondId)
                val semiLosers = koMatches
                    .filter { isSemiFinalMatch(it) && it.status == MatchStatus.FINISHED }
                    .map { winnerAndLoserIds(it).second }
                    .filter { it !in usedIds }
                    .toSet()

                val thirdRow = overallStandings.firstOrNull { it.teamId in semiLosers }
                    ?: overallStandings.firstOrNull { it.teamId !in usedIds }

                if (thirdRow != null) {
                    val rec = "Halbfinale / Gesamtrang #3 (${thirdRow.wins} Siege, ${thirdRow.setsWon}:${thirdRow.setsLost} Sätze)"
                    entries.add(makeEntry(3, thirdRow.teamId, rec))
                }
            }
        } else {
            overallStandings.take(3).forEachIndexed { idx, row ->
                val summary = "${row.matchPoints} Pkt · ${row.wins} Siege · ${row.setsWon}:${row.setsLost} Sätze"
                entries.add(makeEntry(idx + 1, row.teamId, summary))
            }
        }

        return entries.sortedBy { it.rank }
    }
}
