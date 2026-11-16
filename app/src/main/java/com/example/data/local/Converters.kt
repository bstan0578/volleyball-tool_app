package com.example.data.local

import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object JsonHelper {
    fun playerIdsToJson(ids: List<String>): String {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        return array.toString()
    }

    fun jsonToPlayerIds(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<String>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.optString(i, "")
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun completedSetsToJson(sets: List<SetResult>): String {
        val array = JSONArray()
        sets.forEach { set ->
            val obj = JSONObject()
            obj.put("setNumber", set.setNumber)
            obj.put("scoreA", set.scoreA)
            obj.put("scoreB", set.scoreB)
            obj.put("winner", set.winner.name)
            if (set.timeInfo != null) {
                val tObj = JSONObject()
                tObj.put("startTimestamp", set.timeInfo.startTimestamp)
                tObj.put("endTimestamp", set.timeInfo.endTimestamp)
                tObj.put("netSeconds", set.timeInfo.netSeconds)
                tObj.put("timeoutSeconds", set.timeInfo.timeoutSeconds)
                tObj.put("totalSeconds", set.timeInfo.totalSeconds)
                obj.put("timeInfo", tObj)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToCompletedSets(json: String?): List<SetResult> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<SetResult>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timeInfo = if (obj.has("timeInfo")) {
                    val tObj = obj.getJSONObject("timeInfo")
                    SetTimeInfo(
                        startTimestamp = tObj.optLong("startTimestamp", 0L),
                        endTimestamp = tObj.optLong("endTimestamp", 0L),
                        netSeconds = tObj.optLong("netSeconds", 0L),
                        timeoutSeconds = tObj.optLong("timeoutSeconds", 0L),
                        totalSeconds = tObj.optLong("totalSeconds", 0L)
                    )
                } else null
                list.add(
                    SetResult(
                        setNumber = obj.optInt("setNumber", i + 1),
                        scoreA = obj.optInt("scoreA", 0),
                        scoreB = obj.optInt("scoreB", 0),
                        winner = try { TeamSide.valueOf(obj.optString("winner", TeamSide.TEAM_A.name)) } catch (_: Exception) { TeamSide.TEAM_A },
                        timeInfo = timeInfo
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun matchEventsToJson(events: List<MatchEvent>): String {
        val array = JSONArray()
        events.forEach { ev ->
            val obj = JSONObject()
            obj.put("id", ev.id)
            obj.put("setNumber", ev.setNumber)
            obj.put("teamSide", ev.teamSide.name)
            obj.put("type", ev.type.name)
            obj.put("scoreA", ev.scoreA)
            obj.put("scoreB", ev.scoreB)
            obj.put("timestampMillis", ev.timestampMillis)
            obj.put("elapsedNetSeconds", ev.elapsedNetSeconds)
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToMatchEvents(json: String?): List<MatchEvent> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<MatchEvent>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MatchEvent(
                        id = obj.optString("id", "ev_$i"),
                        setNumber = obj.optInt("setNumber", 1),
                        teamSide = try { TeamSide.valueOf(obj.optString("teamSide", TeamSide.TEAM_A.name)) } catch (_: Exception) { TeamSide.TEAM_A },
                        type = try { MatchEventType.valueOf(obj.optString("type", MatchEventType.POINT.name)) } catch (_: Exception) { MatchEventType.POINT },
                        scoreA = obj.optInt("scoreA", 0),
                        scoreB = obj.optInt("scoreB", 0),
                        timestampMillis = obj.optLong("timestampMillis", 0L),
                        elapsedNetSeconds = obj.optLong("elapsedNetSeconds", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun matchConfigToJson(config: MatchConfig): String {
        val obj = JSONObject()
        obj.put("fieldFormatA", config.fieldFormatA.name)
        obj.put("fieldFormatB", config.fieldFormatB.name)
        obj.put("setsMode", config.setsMode.name)
        obj.put("pointsPerSet", config.pointsPerSet)
        obj.put("winByTwo", config.winByTwo)
        if (config.timeLimitMinutes != null) {
            obj.put("timeLimitMinutes", config.timeLimitMinutes)
        }
        obj.put("timeoutDurationSeconds", config.timeoutDurationSeconds)
        return obj.toString()
    }

    fun jsonToMatchConfig(json: String?): MatchConfig {
        if (json.isNullOrBlank()) return MatchConfig()
        return try {
            val obj = JSONObject(json)
            val timeLimit = if (obj.has("timeLimitMinutes") && !obj.isNull("timeLimitMinutes")) obj.optInt("timeLimitMinutes") else null
            MatchConfig(
                fieldFormatA = try { FieldFormat.valueOf(obj.optString("fieldFormatA", FieldFormat.FORMAT_2V2.name)) } catch (_: Exception) { FieldFormat.FORMAT_2V2 },
                fieldFormatB = try { FieldFormat.valueOf(obj.optString("fieldFormatB", FieldFormat.FORMAT_2V2.name)) } catch (_: Exception) { FieldFormat.FORMAT_2V2 },
                setsMode = try { SetsMode.valueOf(obj.optString("setsMode", SetsMode.BEST_OF_3.name)) } catch (_: Exception) { SetsMode.BEST_OF_3 },
                pointsPerSet = obj.optInt("pointsPerSet", 21),
                winByTwo = obj.optBoolean("winByTwo", true),
                timeLimitMinutes = timeLimit,
                timeoutDurationSeconds = obj.optInt("timeoutDurationSeconds", 30)
            )
        } catch (_: Exception) {
            MatchConfig()
        }
    }

    fun undoHistoryToJson(history: List<MatchStateSnapshot>): String {
        val array = JSONArray()
        history.forEach { snap ->
            val obj = JSONObject()
            obj.put("currentSet", snap.currentSet)
            obj.put("scoreA", snap.scoreA)
            obj.put("scoreB", snap.scoreB)
            obj.put("servingTeam", snap.servingTeam.name)
            obj.put("servingPlayerIndexA", snap.servingPlayerIndexA)
            obj.put("servingPlayerIndexB", snap.servingPlayerIndexB)
            obj.put("timeoutsA", snap.timeoutsA)
            obj.put("timeoutsB", snap.timeoutsB)
            obj.put("status", snap.status.name)
            obj.put("completedSets", completedSetsToJson(snap.completedSets))
            if (snap.setStartTime != null) obj.put("setStartTime", snap.setStartTime)
            if (snap.timeoutStartTime != null) obj.put("timeoutStartTime", snap.timeoutStartTime)
            obj.put("totalTimeoutMillis", snap.totalTimeoutMillis)
            obj.put("isTimerRunning", snap.isTimerRunning)
            obj.put("events", matchEventsToJson(snap.events))
            obj.put("rotationStepA", snap.rotationStepA)
            obj.put("rotationStepB", snap.rotationStepB)
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToUndoHistory(json: String?): List<MatchStateSnapshot> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<MatchStateSnapshot>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val setStart = if (obj.has("setStartTime") && !obj.isNull("setStartTime")) obj.optLong("setStartTime") else null
                val timeoutStart = if (obj.has("timeoutStartTime") && !obj.isNull("timeoutStartTime")) obj.optLong("timeoutStartTime") else null
                list.add(
                    MatchStateSnapshot(
                        currentSet = obj.optInt("currentSet", 1),
                        scoreA = obj.optInt("scoreA", 0),
                        scoreB = obj.optInt("scoreB", 0),
                        servingTeam = try { TeamSide.valueOf(obj.optString("servingTeam", TeamSide.TEAM_A.name)) } catch (_: Exception) { TeamSide.TEAM_A },
                        servingPlayerIndexA = obj.optInt("servingPlayerIndexA", 0),
                        servingPlayerIndexB = obj.optInt("servingPlayerIndexB", 0),
                        timeoutsA = obj.optInt("timeoutsA", 0),
                        timeoutsB = obj.optInt("timeoutsB", 0),
                        completedSets = jsonToCompletedSets(obj.optString("completedSets")),
                        status = try { MatchStatus.valueOf(obj.optString("status", MatchStatus.RUNNING.name)) } catch (_: Exception) { MatchStatus.RUNNING },
                        setStartTime = setStart,
                        timeoutStartTime = timeoutStart,
                        totalTimeoutMillis = obj.optLong("totalTimeoutMillis", 0L),
                        isTimerRunning = obj.optBoolean("isTimerRunning", false),
                        events = jsonToMatchEvents(obj.optString("events")),
                        rotationStepA = obj.optInt("rotationStepA", 0),
                        rotationStepB = obj.optInt("rotationStepB", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun groupAssignmentsToJson(map: Map<String, List<String>>): String {
        val obj = JSONObject()
        map.forEach { (groupId, teamList) ->
            val arr = JSONArray()
            teamList.forEach { arr.put(it) }
            obj.put(groupId, arr)
        }
        return obj.toString()
    }

    fun jsonToGroupAssignments(json: String?): Map<String, List<String>> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val map = mutableMapOf<String, List<String>>()
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.optString(i, ""))
                }
                map[key] = list.filter { it.isNotBlank() }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun manualTeamsToJson(teams: List<ManualTournamentTeam>): String {
        val array = JSONArray()
        teams.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("skill", t.skill)
            obj.put("maleCount", t.maleCount)
            obj.put("femaleCount", t.femaleCount)
            obj.put("totalAge", t.totalAge)
            obj.put("club", t.club)
            obj.put("note", t.note)
            obj.put("shortName", t.shortName)
            obj.put("skillLevel", t.skillLevel)
            obj.put("present", t.present)
            obj.put("withdrawn", t.withdrawn)
            obj.put("registrationOrder", t.registrationOrder)
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToManualTeams(json: String?): List<ManualTournamentTeam> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<ManualTournamentTeam>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "Team ${i + 1}")
                val skill = obj.optInt("skill", 3).coerceIn(1, 5)
                list.add(
                    ManualTournamentTeam(
                        id = obj.optString("id", "mteam_$i"),
                        name = name,
                        skill = skill,
                        maleCount = obj.optInt("maleCount", 0),
                        femaleCount = obj.optInt("femaleCount", 0),
                        totalAge = obj.optInt("totalAge", 0),
                        club = obj.optString("club", ""),
                        note = obj.optString("note", ""),
                        shortName = obj.optString("shortName", name.trim().take(4).uppercase()),
                        skillLevel = obj.optInt("skillLevel", skill * 20).coerceIn(1, 100),
                        present = obj.optBoolean("present", true),
                        withdrawn = obj.optBoolean("withdrawn", false),
                        registrationOrder = obj.optInt("registrationOrder", i)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
