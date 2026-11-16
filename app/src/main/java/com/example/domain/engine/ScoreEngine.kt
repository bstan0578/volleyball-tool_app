package com.example.domain.engine

import com.example.data.model.*

object ScoreEngine {

    fun isSetWon(
        scoreA: Int,
        scoreB: Int,
        targetPoints: Int,
        winByTwo: Boolean
    ): Boolean {
        val maxScore = maxOf(scoreA, scoreB)
        val minScore = minOf(scoreA, scoreB)
        if (maxScore < targetPoints) return false
        return if (winByTwo) {
            (maxScore - minScore) >= 2
        } else {
            true
        }
    }

    fun getSetWinner(
        scoreA: Int,
        scoreB: Int,
        targetPoints: Int,
        winByTwo: Boolean
    ): TeamSide? {
        if (!isSetWon(scoreA, scoreB, targetPoints, winByTwo)) return null
        return if (scoreA > scoreB) TeamSide.TEAM_A else TeamSide.TEAM_B
    }

    fun isMatchCompleted(
        completedSets: List<SetResult>,
        mode: SetsMode
    ): Boolean {
        val setsWonA = completedSets.count { it.winner == TeamSide.TEAM_A }
        val setsWonB = completedSets.count { it.winner == TeamSide.TEAM_B }
        val totalCompleted = completedSets.size

        return when (mode) {
            SetsMode.ONE_SET -> totalCompleted >= 1
            SetsMode.BEST_OF_2 -> totalCompleted >= 2 || setsWonA >= 2 || setsWonB >= 2
            SetsMode.BEST_OF_3 -> setsWonA >= 2 || setsWonB >= 2 || totalCompleted >= 3
            SetsMode.BEST_OF_5 -> setsWonA >= 3 || setsWonB >= 3 || totalCompleted >= 5
        }
    }

    fun getMatchWinner(
        completedSets: List<SetResult>,
        mode: SetsMode
    ): TeamSide? {
        if (!isMatchCompleted(completedSets, mode)) return null
        val setsWonA = completedSets.count { it.winner == TeamSide.TEAM_A }
        val setsWonB = completedSets.count { it.winner == TeamSide.TEAM_B }
        return when {
            setsWonA > setsWonB -> TeamSide.TEAM_A
            setsWonB > setsWonA -> TeamSide.TEAM_B
            else -> null
        }
    }

    fun computeElapsedNetSeconds(match: Match, now: Long = System.currentTimeMillis()): Long {
        val start = match.setStartTime ?: return 0L
        val currentOrEnd = if (match.status == MatchStatus.FINISHED) (match.setEndTime ?: now) else now
        val totalElapsed = (currentOrEnd - start).coerceAtLeast(0L)
        val activeTimeout = if (match.timeoutStartTime != null) (currentOrEnd - match.timeoutStartTime).coerceAtLeast(0L) else 0L
        val timeoutElapsed = match.totalTimeoutMillis + activeTimeout
        return ((totalElapsed - timeoutElapsed).coerceAtLeast(0L)) / 1000L
    }

    /**
     * Scores a point for Team A or Team B.
     * Supports Side-Out & Serve Rotation (including 6-zone linked court rotation),
     * live timeline event recording, and resuming timer if a timeout was active.
     */
    fun scorePoint(match: Match, scoringTeam: TeamSide): Match {
        if (match.status == MatchStatus.FINISHED) return match

        val snapshot = match.toSnapshot()
        val newUndoHistory = match.undoHistory + snapshot

        val fieldCountA = match.matchConfig.fieldFormatA.fieldPlayerCount
        val fieldCountB = match.matchConfig.fieldFormatB.fieldPlayerCount

        var newScoreA = match.scoreA
        var newScoreB = match.scoreB
        var newServingTeam = match.servingTeam
        var newServerA = match.servingPlayerIndexA
        var newServerB = match.servingPlayerIndexB
        var newRotStepA = match.rotationStepA
        var newRotStepB = match.rotationStepB

        if (scoringTeam == TeamSide.TEAM_A) {
            newScoreA += 1
            if (match.servingTeam != TeamSide.TEAM_A) {
                newServingTeam = TeamSide.TEAM_A
                newServerA = (match.servingPlayerIndexA + 1) % fieldCountA
                if (match.rotationLinked) {
                    newRotStepA = (match.rotationStepA + 1) % fieldCountA
                }
            }
        } else {
            newScoreB += 1
            if (match.servingTeam != TeamSide.TEAM_B) {
                newServingTeam = TeamSide.TEAM_B
                newServerB = (match.servingPlayerIndexB + 1) % fieldCountB
                if (match.rotationLinked) {
                    newRotStepB = (match.rotationStepB + 1) % fieldCountB
                }
            }
        }

        val now = System.currentTimeMillis()
        var newSetStartTime = match.setStartTime
        var newTimeoutStartTime = match.timeoutStartTime
        var newTotalTimeoutMillis = match.totalTimeoutMillis
        var newIsTimerRunning = match.isTimerRunning

        if (newSetStartTime == null) {
            newSetStartTime = now
            newIsTimerRunning = true
        }

        if (newTimeoutStartTime != null) {
            newTotalTimeoutMillis += (now - newTimeoutStartTime)
            newTimeoutStartTime = null
            newIsTimerRunning = true
        }

        val netSecs = ((now - newSetStartTime - newTotalTimeoutMillis).coerceAtLeast(0L)) / 1000L
        val pointEvent = MatchEvent(
            setNumber = match.currentSet,
            teamSide = scoringTeam,
            type = MatchEventType.POINT,
            scoreA = newScoreA,
            scoreB = newScoreB,
            timestampMillis = now,
            elapsedNetSeconds = netSecs
        )

        val updatedMatch = match.copy(
            scoreA = newScoreA,
            scoreB = newScoreB,
            servingTeam = newServingTeam,
            servingPlayerIndexA = newServerA,
            servingPlayerIndexB = newServerB,
            status = MatchStatus.RUNNING,
            undoHistory = newUndoHistory,
            setStartTime = newSetStartTime,
            timeoutStartTime = newTimeoutStartTime,
            totalTimeoutMillis = newTotalTimeoutMillis,
            isTimerRunning = newIsTimerRunning,
            events = match.events + pointEvent,
            rotationStepA = newRotStepA,
            rotationStepB = newRotStepB
        )

        val setWinner = getSetWinner(
            scoreA = newScoreA,
            scoreB = newScoreB,
            targetPoints = match.matchConfig.pointsPerSet,
            winByTwo = match.matchConfig.winByTwo
        )

        return if (setWinner != null) {
            finishSet(updatedMatch, setWinner)
        } else {
            updatedMatch
        }
    }

    fun manualChangeServer(match: Match, side: TeamSide, playerIndex: Int): Match {
        val snapshot = match.toSnapshot()
        val fieldCount = if (side == TeamSide.TEAM_A) {
            match.matchConfig.fieldFormatA.fieldPlayerCount
        } else {
            match.matchConfig.fieldFormatB.fieldPlayerCount
        }
        val safeIndex = playerIndex.coerceIn(0, (fieldCount - 1).coerceAtLeast(0))

        return if (side == TeamSide.TEAM_A) {
            match.copy(
                servingPlayerIndexA = safeIndex,
                rotationStepA = if (match.rotationLinked) safeIndex else match.rotationStepA,
                undoHistory = match.undoHistory + snapshot
            )
        } else {
            match.copy(
                servingPlayerIndexB = safeIndex,
                rotationStepB = if (match.rotationLinked) safeIndex else match.rotationStepB,
                undoHistory = match.undoHistory + snapshot
            )
        }
    }

    /**
     * Records a timeout for a team, logs it in timeline events, and pauses set timer.
     * Points and Undo remain fully usable while a timeout is active!
     */
    fun recordTimeout(match: Match, side: TeamSide): Match {
        val snapshot = match.toSnapshot()
        val now = System.currentTimeMillis()
        val newTimeoutStartTime = match.timeoutStartTime ?: now
        val netSecs = computeElapsedNetSeconds(match, now)

        val timeoutEvent = MatchEvent(
            setNumber = match.currentSet,
            teamSide = side,
            type = MatchEventType.TIMEOUT,
            scoreA = match.scoreA,
            scoreB = match.scoreB,
            timestampMillis = now,
            elapsedNetSeconds = netSecs
        )

        return if (side == TeamSide.TEAM_A) {
            match.copy(
                status = MatchStatus.RUNNING,
                timeoutsA = match.timeoutsA + 1,
                timeoutStartTime = newTimeoutStartTime,
                isTimerRunning = false,
                undoHistory = match.undoHistory + snapshot,
                events = match.events + timeoutEvent
            )
        } else {
            match.copy(
                status = MatchStatus.RUNNING,
                timeoutsB = match.timeoutsB + 1,
                timeoutStartTime = newTimeoutStartTime,
                isTimerRunning = false,
                undoHistory = match.undoHistory + snapshot,
                events = match.events + timeoutEvent
            )
        }
    }

    /**
     * Resumes set timer when timeout finishes naturally or is dismissed manually.
     */
    fun resumeFromTimeout(match: Match): Match {
        val activeStart = match.timeoutStartTime ?: return match
        val now = System.currentTimeMillis()
        return match.copy(
            status = if (match.status == MatchStatus.FINISHED) MatchStatus.FINISHED else MatchStatus.RUNNING,
            totalTimeoutMillis = match.totalTimeoutMillis + (now - activeStart).coerceAtLeast(0L),
            timeoutStartTime = null,
            isTimerRunning = match.setStartTime != null && match.status != MatchStatus.FINISHED
        )
    }

    /**
     * Toggles manual match interruption (Pause / Resume) while freezing net set timer accurately.
     */
    fun togglePause(match: Match, now: Long = System.currentTimeMillis()): Match {
        if (match.status == MatchStatus.FINISHED) return match
        val snapshot = match.toSnapshot()
        return if (match.status != MatchStatus.PAUSED) {
            match.copy(
                status = MatchStatus.PAUSED,
                isTimerRunning = false,
                timeoutStartTime = if (match.setStartTime != null) (match.timeoutStartTime ?: now) else null,
                undoHistory = match.undoHistory + snapshot
            )
        } else {
            val addedPause = if (match.timeoutStartTime != null) {
                (now - match.timeoutStartTime).coerceAtLeast(0L)
            } else 0L
            val hasActivity = match.setStartTime != null || match.scoreA > 0 || match.scoreB > 0
            match.copy(
                status = if (hasActivity) MatchStatus.RUNNING else MatchStatus.DRAFT,
                totalTimeoutMillis = match.totalTimeoutMillis + addedPause,
                timeoutStartTime = null,
                isTimerRunning = match.setStartTime != null,
                undoHistory = match.undoHistory + snapshot
            )
        }
    }

    fun finishSet(match: Match, winnerOverride: TeamSide? = null): Match {
        val winner = winnerOverride ?: (if (match.scoreA > match.scoreB) TeamSide.TEAM_A else TeamSide.TEAM_B)
        val now = System.currentTimeMillis()
        val start = match.setStartTime ?: now
        val timeoutMillis = match.totalTimeoutMillis + (if (match.timeoutStartTime != null) (now - match.timeoutStartTime) else 0L)
        val totalSecs = ((now - start).coerceAtLeast(0L)) / 1000
        val timeoutSecs = timeoutMillis / 1000
        val netSecs = (totalSecs - timeoutSecs).coerceAtLeast(0L)

        val timeInfo = SetTimeInfo(
            startTimestamp = start,
            endTimestamp = now,
            netSeconds = netSecs,
            timeoutSeconds = timeoutSecs,
            totalSeconds = totalSecs
        )

        val setResult = SetResult(
            setNumber = match.currentSet,
            scoreA = match.scoreA,
            scoreB = match.scoreB,
            winner = winner,
            timeInfo = timeInfo
        )
        val newCompletedSets = match.completedSets + setResult
        val isFinished = isMatchCompleted(newCompletedSets, match.matchConfig.setsMode)

        return if (isFinished) {
            match.copy(
                completedSets = newCompletedSets,
                status = MatchStatus.FINISHED,
                setStartTime = null,
                setEndTime = now,
                timeoutStartTime = null,
                totalTimeoutMillis = 0L,
                isTimerRunning = false
            )
        } else {
            val nextSet = match.currentSet + 1
            val initialServerNextSet = if (match.currentSet % 2 == 1) TeamSide.TEAM_B else TeamSide.TEAM_A
            match.copy(
                currentSet = nextSet,
                scoreA = 0,
                scoreB = 0,
                timeoutsA = 0,
                timeoutsB = 0,
                servingTeam = initialServerNextSet,
                completedSets = newCompletedSets,
                status = MatchStatus.RUNNING,
                setStartTime = null,
                setEndTime = null,
                timeoutStartTime = null,
                totalTimeoutMillis = 0L,
                isTimerRunning = false
            )
        }
    }

    fun forceFinishMatch(match: Match): Match {
        val snapshot = match.toSnapshot()
        val now = System.currentTimeMillis()
        return match.copy(
            status = MatchStatus.FINISHED,
            setEndTime = now,
            isTimerRunning = false,
            undoHistory = match.undoHistory + snapshot
        )
    }

    fun undo(match: Match): Match {
        if (match.undoHistory.isEmpty()) return match

        val lastIndex = match.undoHistory.size - 1
        val lastSnapshot = match.undoHistory[lastIndex]
        val remainingHistory = match.undoHistory.subList(0, lastIndex)

        return match.copy(
            currentSet = lastSnapshot.currentSet,
            scoreA = lastSnapshot.scoreA,
            scoreB = lastSnapshot.scoreB,
            servingTeam = lastSnapshot.servingTeam,
            servingPlayerIndexA = lastSnapshot.servingPlayerIndexA,
            servingPlayerIndexB = lastSnapshot.servingPlayerIndexB,
            timeoutsA = lastSnapshot.timeoutsA,
            timeoutsB = lastSnapshot.timeoutsB,
            completedSets = lastSnapshot.completedSets,
            status = lastSnapshot.status,
            setStartTime = lastSnapshot.setStartTime,
            timeoutStartTime = lastSnapshot.timeoutStartTime,
            totalTimeoutMillis = lastSnapshot.totalTimeoutMillis,
            isTimerRunning = lastSnapshot.isTimerRunning,
            events = lastSnapshot.events,
            rotationStepA = lastSnapshot.rotationStepA,
            rotationStepB = lastSnapshot.rotationStepB,
            undoHistory = remainingHistory
        )
    }
}
