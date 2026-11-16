package com.example.domain.engine

import kotlin.random.Random

enum class CoinSide(val label: String) {
    HEADS("Kopf"),
    TAILS("Zahl")
}

data class CoinTossResult(
    val side: CoinSide,
    val winnerTeamId: String? = null,
    val winnerTeamName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object CoinTossEngine {

    fun flipCoin(
        random: Random = Random.Default,
        teamAName: String? = null,
        teamBName: String? = null,
        headsTeam: String? = null
    ): CoinTossResult {
        val isHeads = random.nextBoolean()
        val resultSide = if (isHeads) CoinSide.HEADS else CoinSide.TAILS

        var winner: String? = null
        if (!teamAName.isNullOrBlank() && !teamBName.isNullOrBlank()) {
            winner = if (headsTeam == teamAName) {
                if (resultSide == CoinSide.HEADS) teamAName else teamBName
            } else {
                if (resultSide == CoinSide.HEADS) (headsTeam ?: teamAName) else teamBName
            }
        }

        return CoinTossResult(
            side = resultSide,
            winnerTeamName = winner
        )
    }
}
