package com.example.domain.tournament

import com.example.data.model.Tournament
import com.example.data.model.TournamentMode
import org.json.JSONArray
import org.json.JSONObject

// ergänzt gemäß SPEC G-07, E-01..E-05, A8

/**
 * Pure domain Export (ASCII Box, RFC 4180 CSV with formula-injection protection,
 * Generic Ticker JSON/XML) and strict Backup/Migration serialization.
 * No invented SAMS format. Rejects prototype-pollution keys & oversized payloads.
 */
object SpecExportAndPersistence {

    const val MAX_BACKUP_BYTES = 1_048_576 // 1 MB (A8)
    private val FORBIDDEN_JSON_KEYS = setOf("__proto__", "constructor", "prototype")

    fun sanitizeFilename(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9äöüÄÖÜß_-]+"), "_")
            .trim('_')
        return cleaned.ifBlank { "turnier_export" }.take(64)
    }

    /**
     * E-01 / E-02 / E-04: CSV (RFC 4180, Semikolon, UTF-8 BOM, CRLF) inkl. Formel-Injektionsschutz.
     * Nur Strings mit führendem = + - @ \t \r erhalten führendes '.
     * Numerische Spalten bleiben unberührt!
     */
    fun exportCsvRfc4180(
        tournamentName: String,
        standingsByGroup: Map<String, List<SpecStandingRow>>,
        matches: List<SpecMatch>,
        teamNames: Map<String, String>,
        config: SpecTournamentConfig
    ): String {
        fun escapeCsvText(value: String): String {
            val guarded = if (value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
                "'$value"
            } else {
                value
            }
            val needsQuotes = guarded.contains(';') || guarded.contains('"') || guarded.contains('\n') || guarded.contains('\r')
            return if (needsQuotes) "\"${guarded.replace("\"", "\"\"")}\"" else guarded
        }

        val sb = StringBuilder("\uFEFF") // UTF-8 BOM
        fun appendRow(vararg cells: Any) {
            sb.append(
                cells.joinToString(";") { c ->
                    when (c) {
                        is Number -> c.toString() // Numerische Spalten bleiben exakt ohne Quote/Prefix!
                        else -> escapeCsvText(c.toString())
                    }
                }
            )
            sb.append("\r\n")
        }

        appendRow("Turnier", tournamentName)
        appendRow()
        appendRow("Gruppe", "Rang", "Team", "Sp", "S", "U", "N", "MP", "Saetze+", "Saetze-", "Baelle+", "Baelle-", "Buchholz", "Entscheidung")
        standingsByGroup.keys.sorted().forEach { grp ->
            standingsByGroup[grp].orEmpty().forEach { row ->
                appendRow(
                    grp.ifBlank { "Gesamt" },
                    row.rank,
                    row.teamName,
                    row.matchesPlayed,
                    row.wins,
                    row.draws,
                    row.losses,
                    row.matchPoints,
                    row.setsWon,
                    row.setsLost,
                    row.pointsFor,
                    row.pointsAgainst,
                    row.buchholz,
                    if (row.decisionRequired) "Entscheidung erf." else row.decidedBy
                )
            }
        }

        appendRow()
        appendRow("Slot", "Uhrzeit", "Feld", "Phase/Runde", "Team A", "Team B", "Ergebnis", "Schiedsrichter", "Status")
        matches.forEach { m ->
            val timeStr = SpecSchedulerAndReferees.formatSlotTime(m.slotIndex, config, m.status == SpecMatchStatus.SCHEDULED)
            val courtStr = m.courtNumber?.toString() ?: "-"
            val nameA = m.teamAId?.let { teamNames[it] ?: it } ?: (m.sourceA?.let { describeSource(it) } ?: "Offen")
            val nameB = m.teamBId?.let { teamNames[it] ?: it } ?: (m.sourceB?.let { describeSource(it) } ?: "Freilos")
            val scoreStr = m.sets.joinToString(" ") { "${it.a}:${it.b}" }.ifBlank { "-" }
            val refStr = m.refereeTeamIds.joinToString(", ") { teamNames[it] ?: it }.ifBlank { "-" }
            appendRow(
                if (m.slotIndex != null) m.slotIndex + 1 else "-",
                timeStr,
                courtStr,
                m.roundLabel,
                nameA,
                nameB,
                scoreStr,
                refStr,
                m.status.name
            )
        }
        return sb.toString()
    }

    fun describeSource(src: MatchSource): String = when (src) {
        is MatchSource.DirectTeam -> src.teamId
        is MatchSource.GroupRank -> "Platz ${src.rank} Gr.${src.groupId}"
        is MatchSource.Wildcard -> "Wildcard #${src.wildcardRank}"
        is MatchSource.WinnerOf -> "Sieger ${src.matchId}"
        is MatchSource.LoserOf -> "Verlierer ${src.matchId}"
        MatchSource.Bye -> "Freilos"
    }

    /**
     * E-01: Saubere ASCII-Box-Tabelle.
     */
    fun exportAsciiTable(
        tournamentName: String,
        standingsByGroup: Map<String, List<SpecStandingRow>>,
        matches: List<SpecMatch>,
        teamNames: Map<String, String>,
        config: SpecTournamentConfig
    ): String {
        val sb = StringBuilder()
        val header = "TURNIER: $tournamentName"
        val border = "+${"-".repeat(maxOf(58, header.length + 2))}+"
        sb.appendLine(border)
        sb.appendLine("| ${header.padEnd(border.length - 4)} |")
        sb.appendLine(border)

        standingsByGroup.keys.sorted().forEach { grp ->
            sb.appendLine()
            sb.appendLine("== ${if (grp.isBlank()) "Gesamttabelle" else "Gruppe $grp"} ==")
            sb.appendLine("+------+----------------------+----+----+-----+-------+---------+------------------+")
            sb.appendLine("| Rang | Team                 | Sp | MP | S:N | Sätze | Bälle   | Kriterium        |")
            sb.appendLine("+------+----------------------+----+----+-----+-------+---------+------------------+")
            standingsByGroup[grp].orEmpty().forEach { r ->
                val dec = if (r.decisionRequired) "Entsch. erf." else r.decidedBy
                sb.appendLine(
                    String.format(
                        "| #%-3d | %-20s | %2d | %2d | %1d:%1d | %2d:%-2d | %3d:%-3d | %-16s |",
                        r.rank,
                        r.teamName.take(20),
                        r.matchesPlayed,
                        r.matchPoints,
                        r.wins,
                        r.losses,
                        r.setsWon,
                        r.setsLost,
                        r.pointsFor,
                        r.pointsAgainst,
                        dec.take(16)
                    )
                )
            }
            sb.appendLine("+------+----------------------+----+----+-----+-------+---------+------------------+")
        }

        sb.appendLine()
        sb.appendLine("== SPIELPLAN & SCHIEDSRICHTER ==")
        matches.forEach { m ->
            val slotStr = if (m.slotIndex != null) "S${m.slotIndex + 1} ${SpecSchedulerAndReferees.formatSlotTime(m.slotIndex, config)}" else "ohne Slot"
            val courtStr = if (m.courtNumber != null) "F${m.courtNumber}" else "-"
            val nameA = m.teamAId?.let { teamNames[it] ?: it } ?: (m.sourceA?.let { describeSource(it) } ?: "?")
            val nameB = m.teamBId?.let { teamNames[it] ?: it } ?: (m.sourceB?.let { describeSource(it) } ?: "Freilos")
            val scoreStr = m.sets.joinToString(", ") { "${it.a}:${it.b}" }.ifBlank { m.status.name }
            val refStr = if (m.refereeTeamIds.isEmpty()) "" else " [SR: ${m.refereeTeamIds.joinToString(",") { teamNames[it] ?: it }}]"
            sb.appendLine("[$slotStr | $courtStr] ${m.roundLabel}: $nameA vs $nameB -> $scoreStr$refStr")
        }
        return sb.toString()
    }

    /**
     * E-05: Generischer Ticker-JSON Export (kein erfundenes SAMS-Format).
     */
    fun exportGenericTickerJson(
        tournamentId: String,
        tournamentName: String,
        standingsByGroup: Map<String, List<SpecStandingRow>>,
        matches: List<SpecMatch>,
        teamNames: Map<String, String>,
        config: SpecTournamentConfig
    ): String {
        val root = JSONObject()
        root.put("format", "generic-volleyball-ticker-v1")
        root.put("schemaVersion", config.schemaVersion)
        root.put("tournamentId", tournamentId)
        root.put("tournamentName", tournamentName)

        val matchesArr = JSONArray()
        matches.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("round", m.roundLabel)
            obj.put("groupId", m.groupId ?: "")
            obj.put("isKo", m.isKo)
            obj.put("slot", m.slotIndex ?: JSONObject.NULL)
            obj.put("time", SpecSchedulerAndReferees.formatSlotTime(m.slotIndex, config))
            obj.put("court", m.courtNumber ?: JSONObject.NULL)
            obj.put("teamA", m.teamAId?.let { teamNames[it] ?: it } ?: "")
            obj.put("teamB", m.teamBId?.let { teamNames[it] ?: it } ?: "")
            obj.put("status", m.status.name)
            val setsArr = JSONArray()
            m.sets.forEach { s ->
                setsArr.put(JSONObject().put("a", s.a).put("b", s.b))
            }
            obj.put("sets", setsArr)
            matchesArr.put(obj)
        }
        root.put("matches", matchesArr)

        val tablesObj = JSONObject()
        standingsByGroup.forEach { (grp, rows) ->
            val arr = JSONArray()
            rows.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("rank", r.rank)
                        .put("teamId", r.teamId)
                        .put("teamName", r.teamName)
                        .put("played", r.matchesPlayed)
                        .put("mp", r.matchPoints)
                        .put("sets", "${r.setsWon}:${r.setsLost}")
                        .put("balls", "${r.pointsFor}:${r.pointsAgainst}")
                        .put("decidedBy", if (r.decisionRequired) "Entscheidung erf." else r.decidedBy)
                )
            }
            tablesObj.put(grp.ifBlank { "OVERALL" }, arr)
        }
        root.put("standings", tablesObj)
        return root.toString(2)
    }

    /**
     * E-05: Generischer Ticker-XML Export mit striktem XML-Escaping.
     */
    fun exportGenericTickerXml(
        tournamentId: String,
        tournamentName: String,
        standingsByGroup: Map<String, List<SpecStandingRow>>,
        matches: List<SpecMatch>,
        teamNames: Map<String, String>,
        config: SpecTournamentConfig
    ): String {
        fun esc(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<ticker format="generic-volleyball-ticker-v1" id="${esc(tournamentId)}" name="${esc(tournamentName)}">""")
        sb.appendLine("  <matches>")
        matches.forEach { m ->
            val nameA = m.teamAId?.let { teamNames[it] ?: it } ?: ""
            val nameB = m.teamBId?.let { teamNames[it] ?: it } ?: ""
            val time = SpecSchedulerAndReferees.formatSlotTime(m.slotIndex, config)
            sb.appendLine(
                """    <match id="${esc(m.id)}" round="${esc(m.roundLabel)}" group="${esc(m.groupId ?: "")}" slot="${m.slotIndex ?: -1}" time="${esc(time)}" court="${m.courtNumber ?: 0}" status="${esc(m.status.name)}">"""
            )
            sb.appendLine("""      <teamA>${esc(nameA)}</teamA>""")
            sb.appendLine("""      <teamB>${esc(nameB)}</teamB>""")
            sb.appendLine("""      <sets>${esc(m.sets.joinToString(",") { "${it.a}:${it.b}" })}</sets>""")
            sb.appendLine("    </match>")
        }
        sb.appendLine("  </matches>")
        sb.appendLine("  <standings>")
        standingsByGroup.forEach { (grp, rows) ->
            sb.appendLine("""    <table group="${esc(grp.ifBlank { "OVERALL" })}">""")
            rows.forEach { r ->
                sb.appendLine(
                    """      <row rank="${r.rank}" team="${esc(r.teamName)}" mp="${r.matchPoints}" sets="${r.setsWon}:${r.setsLost}" balls="${r.pointsFor}:${r.pointsAgainst}" />"""
                )
            }
            sb.appendLine("    </table>")
        }
        sb.appendLine("  </standings>")
        sb.appendLine("</ticker>")
        return sb.toString()
    }

    // =========================================================================
    // SPEC SERIALIZATION, MIGRATION v1 -> v2, STRICT BACKUP IMPORT (G-07, E-03, A8)
    // =========================================================================

    fun specConfigToJson(config: SpecTournamentConfig): JSONObject {
        val obj = JSONObject()
        obj.put("schemaVersion", config.schemaVersion)
        obj.put("seed", config.seed)
        obj.put("scoringSystem", config.scoringSystem.name)
        obj.put("validationMode", config.validationMode.name)
        obj.put("setsToWin", config.setsToWin)
        obj.put("fixedSetsCount", config.fixedSetsCount)
        obj.put("pointsPerSet", config.pointsPerSet)
        obj.put("decidingSetPoints", config.decidingSetPoints)
        obj.put("pointCap", config.pointCap ?: JSONObject.NULL)
        obj.put("withdrawalPolicy", config.withdrawalPolicy.name)
        obj.put("includeForfeitBallsInQuotient", config.includeForfeitBallsInQuotient)
        obj.put("zeroDivisorPolicy", config.zeroDivisorPolicy.name)
        obj.put("tieBreakPreset", config.tieBreakPreset.name)
        obj.put("groupComparisonPolicy", config.groupComparisonPolicy.name)
        obj.put("advancePerGroup", config.advancePerGroup)
        obj.put("wildcards", config.wildcards)
        obj.put("koSize", config.koSize)
        obj.put("consolationMode", config.consolationMode.name)
        obj.put("doubleRoundRobin", config.doubleRoundRobin)
        obj.put("restStrictness", config.restStrictness.name)
        obj.put("minRestSlots", config.minRestSlots)
        obj.put("refereeMode", config.refereeMode.name)
        obj.put("refTeamsPerMatch", config.refTeamsPerMatch)
        obj.put("startTimeMinutes", config.startTimeMinutes)
        obj.put("slotDurationMinutes", config.slotDurationMinutes)
        obj.put("liveDelayMinutes", config.liveDelayMinutes)
        obj.put("manualSeedOrder", JSONArray(config.manualSeedOrder))

        val tiesObj = JSONObject()
        config.manualTieResolutions.forEach { (k, v) -> tiesObj.put(k, JSONArray(v)) }
        obj.put("manualTieResolutions", tiesObj)

        val logArr = JSONArray()
        config.auditLog.takeLast(200).forEach { entry ->
            logArr.put(
                JSONObject()
                    .put("ts", entry.timestampMillis)
                    .put("matchId", entry.matchId)
                    .put("summary", entry.summary)
                    .put("reason", entry.reason)
            )
        }
        obj.put("auditLog", logArr)
        return obj
    }

    fun jsonToSpecConfig(obj: JSONObject?): SpecTournamentConfig {
        if (obj == null) return SpecTournamentConfig()
        val version = obj.optInt("schemaVersion", 1)
        val rawTieObj = obj.optJSONObject("manualTieResolutions")
        val manualTies = mutableMapOf<String, List<String>>()
        if (rawTieObj != null) {
            val keys = rawTieObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = rawTieObj.optJSONArray(k) ?: continue
                manualTies[k] = List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
            }
        }
        val seedOrderArr = obj.optJSONArray("manualSeedOrder")
        val manualSeed = if (seedOrderArr != null) {
            List(seedOrderArr.length()) { seedOrderArr.optString(it) }.filter { it.isNotBlank() }
        } else emptyList()

        val logArr = obj.optJSONArray("auditLog")
        val audit = if (logArr != null) {
            (0 until logArr.length()).mapNotNull { idx ->
                val e = logArr.optJSONObject(idx) ?: return@mapNotNull null
                AuditEntry(
                    timestampMillis = e.optLong("ts", 0L),
                    matchId = e.optString("matchId", ""),
                    summary = e.optString("summary", ""),
                    reason = e.optString("reason", "")
                )
            }
        } else emptyList()

        val cap = if (obj.has("pointCap") && !obj.isNull("pointCap")) obj.optInt("pointCap") else null

        val parsed = SpecTournamentConfig(
            schemaVersion = maxOf(version, 2),
            seed = obj.optInt("seed", 424242),
            scoringSystem = enumOrDefault(obj.optString("scoringSystem"), ScoringSystem.DVV3),
            validationMode = enumOrDefault(obj.optString("validationMode"), MatchValidationMode.BEST_OF),
            setsToWin = obj.optInt("setsToWin", 2).coerceIn(1, 3),
            fixedSetsCount = obj.optInt("fixedSetsCount", 2).coerceIn(1, 5),
            pointsPerSet = obj.optInt("pointsPerSet", 25).coerceAtLeast(1),
            decidingSetPoints = obj.optInt("decidingSetPoints", 15).coerceAtLeast(1),
            pointCap = cap,
            withdrawalPolicy = enumOrDefault(obj.optString("withdrawalPolicy"), WithdrawalPolicy.FORFEIT_REST),
            includeForfeitBallsInQuotient = obj.optBoolean("includeForfeitBallsInQuotient", false),
            zeroDivisorPolicy = enumOrDefault(obj.optString("zeroDivisorPolicy"), ZeroDivisorPolicy.NUMERATOR),
            tieBreakPreset = enumOrDefault(obj.optString("tieBreakPreset"), TieBreakPreset.DEFAULT_SPEC),
            groupComparisonPolicy = enumOrDefault(obj.optString("groupComparisonPolicy"), GroupComparisonPolicy.DROP_LAST),
            advancePerGroup = obj.optInt("advancePerGroup", 2).coerceAtLeast(1),
            wildcards = obj.optInt("wildcards", 0).coerceAtLeast(0),
            koSize = obj.optInt("koSize", 4).coerceAtLeast(2),
            consolationMode = enumOrDefault(obj.optString("consolationMode"), ConsolationMode.THIRD),
            doubleRoundRobin = obj.optBoolean("doubleRoundRobin", false),
            restStrictness = enumOrDefault(obj.optString("restStrictness"), RestStrictness.BALANCED),
            minRestSlots = obj.optInt("minRestSlots", 1).coerceIn(0, 5),
            refereeMode = enumOrDefault(obj.optString("refereeMode"), RefereeMode.AUTO_FAIR),
            refTeamsPerMatch = obj.optInt("refTeamsPerMatch", 2).coerceIn(1, 2),
            startTimeMinutes = obj.optInt("startTimeMinutes", 9 * 60),
            slotDurationMinutes = obj.optInt("slotDurationMinutes", 35).coerceIn(5, 180),
            liveDelayMinutes = obj.optInt("liveDelayMinutes", 0).coerceAtLeast(0),
            manualSeedOrder = manualSeed,
            manualTieResolutions = manualTies,
            auditLog = audit
        )
        return if (version < 2) migrateV1ToV2(parsed) else parsed
    }

    /**
     * G-07: Deterministische Schema-Migration v1 -> v2 (fügt fehlende SPEC-Defaults sauber hinzu).
     */
    fun migrateV1ToV2(v1Config: SpecTournamentConfig): SpecTournamentConfig {
        return v1Config.copy(schemaVersion = 2)
    }

    fun specMatchToJson(m: SpecMatch): JSONObject {
        val obj = JSONObject()
        obj.put("id", m.id)
        obj.put("roundLabel", m.roundLabel)
        obj.put("groupId", m.groupId ?: JSONObject.NULL)
        obj.put("isKo", m.isKo)
        obj.put("sourceA", matchSourceToJson(m.sourceA) ?: JSONObject.NULL)
        obj.put("sourceB", matchSourceToJson(m.sourceB) ?: JSONObject.NULL)
        obj.put("teamAId", m.teamAId ?: JSONObject.NULL)
        obj.put("teamBId", m.teamBId ?: JSONObject.NULL)
        val setsArr = JSONArray()
        m.sets.forEach { setsArr.put(JSONObject().put("a", it.a).put("b", it.b)) }
        obj.put("sets", setsArr)
        obj.put("status", m.status.name)
        obj.put("winnerSide", m.winnerSide?.name ?: JSONObject.NULL)
        obj.put("isForfeit", m.isForfeit)
        obj.put("isByeMatch", m.isByeMatch)
        obj.put("slotIndex", m.slotIndex ?: JSONObject.NULL)
        obj.put("courtNumber", m.courtNumber ?: JSONObject.NULL)
        obj.put("refereeTeamIds", JSONArray(m.refereeTeamIds))
        obj.put("refereeManualLocked", m.refereeManualLocked)
        obj.put("dependsOnMatchIds", JSONArray(m.dependsOnMatchIds))
        obj.put("phaseRound", m.phaseRound)
        obj.put("autoWo", m.isAutoWithdrawalForfeit)
        return obj
    }

    fun jsonToSpecMatch(obj: JSONObject): SpecMatch {
        val setsArr = obj.optJSONArray("sets")
        val sets = if (setsArr != null) {
            (0 until setsArr.length()).mapNotNull { i ->
                val so = setsArr.optJSONObject(i) ?: return@mapNotNull null
                SetScore(so.optInt("a", 0), so.optInt("b", 0))
            }
        } else emptyList()
        val refArr = obj.optJSONArray("refereeTeamIds")
        val refs = if (refArr != null) List(refArr.length()) { refArr.optString(it) }.filter { it.isNotBlank() } else emptyList()
        val depArr = obj.optJSONArray("dependsOnMatchIds")
        val deps = if (depArr != null) List(depArr.length()) { depArr.optString(it) }.filter { it.isNotBlank() } else emptyList()

        return SpecMatch(
            id = obj.optString("id", "m"),
            roundLabel = obj.optString("roundLabel", ""),
            groupId = if (obj.isNull("groupId")) null else obj.optString("groupId"),
            isKo = obj.optBoolean("isKo", false),
            sourceA = jsonToMatchSource(obj.optJSONObject("sourceA")),
            sourceB = jsonToMatchSource(obj.optJSONObject("sourceB")),
            teamAId = if (obj.isNull("teamAId")) null else obj.optString("teamAId"),
            teamBId = if (obj.isNull("teamBId")) null else obj.optString("teamBId"),
            sets = sets,
            status = enumOrDefault(obj.optString("status"), SpecMatchStatus.SCHEDULED),
            winnerSide = if (obj.isNull("winnerSide")) null else enumOrDefault(obj.optString("winnerSide"), com.example.data.model.TeamSide.TEAM_A),
            isForfeit = obj.optBoolean("isForfeit", false),
            isByeMatch = obj.optBoolean("isByeMatch", false),
            slotIndex = if (obj.isNull("slotIndex")) null else obj.optInt("slotIndex"),
            courtNumber = if (obj.isNull("courtNumber")) null else obj.optInt("courtNumber"),
            refereeTeamIds = refs,
            refereeManualLocked = obj.optBoolean("refereeManualLocked", false),
            dependsOnMatchIds = deps,
            phaseRound = obj.optInt("phaseRound", 1),
            isAutoWithdrawalForfeit = obj.optBoolean("autoWo", false)
        )
    }

    private fun matchSourceToJson(src: MatchSource?): JSONObject? {
        if (src == null) return null
        return when (src) {
            is MatchSource.DirectTeam -> JSONObject().put("type", "DirectTeam").put("teamId", src.teamId)
            is MatchSource.GroupRank -> JSONObject().put("type", "GroupRank").put("groupId", src.groupId).put("rank", src.rank)
            is MatchSource.Wildcard -> JSONObject().put("type", "Wildcard").put("wildcardRank", src.wildcardRank)
            is MatchSource.WinnerOf -> JSONObject().put("type", "WinnerOf").put("matchId", src.matchId)
            is MatchSource.LoserOf -> JSONObject().put("type", "LoserOf").put("matchId", src.matchId)
            MatchSource.Bye -> JSONObject().put("type", "Bye")
        }
    }

    private fun jsonToMatchSource(obj: JSONObject?): MatchSource? {
        if (obj == null) return null
        return when (obj.optString("type")) {
            "DirectTeam" -> MatchSource.DirectTeam(obj.optString("teamId"))
            "GroupRank" -> MatchSource.GroupRank(obj.optString("groupId"), obj.optInt("rank", 1))
            "Wildcard" -> MatchSource.Wildcard(obj.optInt("wildcardRank", 1))
            "WinnerOf" -> MatchSource.WinnerOf(obj.optString("matchId"))
            "LoserOf" -> MatchSource.LoserOf(obj.optString("matchId"))
            "Bye" -> MatchSource.Bye
            else -> null
        }
    }

    fun specDataToJson(config: SpecTournamentConfig, specMatches: List<SpecMatch>): String {
        val obj = JSONObject()
        obj.put("schemaVersion", config.schemaVersion)
        obj.put("config", specConfigToJson(config))
        val mArr = JSONArray()
        specMatches.forEach { mArr.put(specMatchToJson(it)) }
        obj.put("specMatches", mArr)
        return obj.toString()
    }

    fun jsonToSpecData(json: String?): Pair<SpecTournamentConfig, List<SpecMatch>> {
        if (json.isNullOrBlank() || json == "{}") return SpecTournamentConfig() to emptyList()
        return try {
            val root = JSONObject(json)
            val cfg = jsonToSpecConfig(root.optJSONObject("config"))
            val mArr = root.optJSONArray("specMatches")
            val matches = if (mArr != null) {
                (0 until mArr.length()).mapNotNull { i ->
                    mArr.optJSONObject(i)?.let { jsonToSpecMatch(it) }
                }
            } else emptyList()
            cfg to matches
        } catch (_: Exception) {
            SpecTournamentConfig() to emptyList()
        }
    }

    /**
     * E-03 / A8: Vollständiges JSON-Backup eines Turniers erstellen.
     */
    fun exportBackupJson(tournament: Tournament): String {
        val root = JSONObject()
        root.put("backupFormat", "volley-tournament-backup-v2")
        root.put("schemaVersion", tournament.specConfig.schemaVersion)
        root.put("id", tournament.id)
        root.put("name", tournament.name)
        root.put("mode", tournament.mode.name)
        root.put("courtCount", tournament.courtCount)
        root.put("groupsCount", tournament.groupsCount)
        root.put("location", tournament.location)
        root.put("date", tournament.date)
        root.put("config", specConfigToJson(tournament.specConfig))

        val teamsArr = JSONArray()
        tournament.manualTeams.forEach { t ->
            teamsArr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("shortName", t.shortName)
                    .put("club", t.club)
                    .put("skillLevel", t.skillLevel)
                    .put("present", t.present)
                    .put("withdrawn", t.withdrawn)
                    .put("registrationOrder", t.registrationOrder)
            )
        }
        root.put("teams", teamsArr)

        val grpObj = JSONObject()
        tournament.groupAssignments.forEach { (g, ids) -> grpObj.put(g, JSONArray(ids)) }
        root.put("groupAssignments", grpObj)

        val mArr = JSONArray()
        tournament.specMatches.forEach { mArr.put(specMatchToJson(it)) }
        root.put("specMatches", mArr)
        return root.toString(2)
    }

    data class BackupImportOutcome(
        val success: Boolean,
        val config: SpecTournamentConfig? = null,
        val teams: List<SpecTeam> = emptyList(),
        val specMatches: List<SpecMatch> = emptyList(),
        val groupAssignments: Map<String, List<String>> = emptyMap(),
        val tournamentName: String = "",
        val modeName: String = "",
        val courtCount: Int = 1,
        val errorMessage: String? = null
    )

    /**
     * A8 + E-03: Strenge Backup-Import-Validierung:
     * - Max 1 MB Größe
     * - Lehnt gefährliche Keys (__proto__, constructor, prototype) ab
     * - Prüft Schema & Pflichtfelder, niemals stille Teil-Übernahme bei Defekt!
     */
    fun importBackupJsonStrict(rawJson: String): BackupImportOutcome {
        if (rawJson.isBlank()) return BackupImportOutcome(false, errorMessage = "Leeres Backup.")
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            return BackupImportOutcome(false, errorMessage = "Backup überschreitet die Maximalgröße von 1 MB.")
        }
        return try {
            val root = JSONObject(rawJson)
            if (containsForbiddenKey(root)) {
                return BackupImportOutcome(false, errorMessage = "Ungültiger JSON-Schlüssel erkannt (__proto__/constructor/prototype).")
            }
            val version = root.optInt("schemaVersion", -1)
            if (version < 1 || version > 99) {
                return BackupImportOutcome(false, errorMessage = "Unbekannte oder fehlende schemaVersion.")
            }
            val name = root.optString("name", "").trim()
            if (name.isEmpty()) {
                return BackupImportOutcome(false, errorMessage = "Turniername fehlt im Backup.")
            }
            val teamsArr = root.optJSONArray("teams")
                ?: return BackupImportOutcome(false, errorMessage = "Teamliste fehlt im Backup.")
            val teams = mutableListOf<SpecTeam>()
            val seenIds = mutableSetOf<String>()
            for (i in 0 until teamsArr.length()) {
                val to = teamsArr.optJSONObject(i) ?: return BackupImportOutcome(false, errorMessage = "Ungültiger Team-Eintrag #$i.")
                val tid = to.optString("id", "").trim()
                val tname = to.optString("name", "").trim()
                if (tid.isEmpty() || tname.isEmpty() || !seenIds.add(tid)) {
                    return BackupImportOutcome(false, errorMessage = "Ungültige oder doppelte Team-ID/Name bei #$i.")
                }
                teams.add(
                    SpecTeam(
                        id = tid,
                        name = tname,
                        shortName = to.optString("shortName", tname.take(4).uppercase()),
                        clubId = to.optString("club", ""),
                        skillLevel = to.optInt("skillLevel", 50).coerceIn(1, 100),
                        present = to.optBoolean("present", true),
                        withdrawn = to.optBoolean("withdrawn", false),
                        registrationOrder = to.optInt("registrationOrder", i)
                    )
                )
            }
            val teamErrors = SpecSeedingAndGroups.validateTeams(teams).filter { it.isError }
            if (teamErrors.isNotEmpty()) {
                return BackupImportOutcome(false, errorMessage = teamErrors.first().message)
            }

            val cfg = jsonToSpecConfig(root.optJSONObject("config")).copy(schemaVersion = maxOf(version, 2))
            val mArr = root.optJSONArray("specMatches")
            val matches = if (mArr != null) {
                (0 until mArr.length()).map { idx ->
                    val mo = mArr.optJSONObject(idx) ?: return BackupImportOutcome(false, errorMessage = "Ungültiger Spiel-Eintrag #$idx.")
                    jsonToSpecMatch(mo)
                }
            } else emptyList()

            val grpObj = root.optJSONObject("groupAssignments")
            val groups = mutableMapOf<String, List<String>>()
            if (grpObj != null) {
                val keys = grpObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = grpObj.optJSONArray(k) ?: continue
                    groups[k] = List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
                }
            }

            BackupImportOutcome(
                success = true,
                config = cfg,
                teams = teams,
                specMatches = matches,
                groupAssignments = groups,
                tournamentName = name,
                modeName = root.optString("mode", TournamentMode.GROUPS_KO.name),
                courtCount = root.optInt("courtCount", 1).coerceIn(1, 16)
            )
        } catch (e: Exception) {
            BackupImportOutcome(false, errorMessage = "Ungültiges JSON-Format: ${e.message ?: "Parse-Fehler"}")
        }
    }

    private fun containsForbiddenKey(node: Any?): Boolean {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in FORBIDDEN_JSON_KEYS) return true
                    if (containsForbiddenKey(node.opt(k))) return true
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (containsForbiddenKey(node.opt(i))) return true
                }
            }
        }
        return false
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T {
        if (name.isNullOrBlank()) return default
        return try {
            enumValueOf<T>(name)
        } catch (_: Exception) {
            default
        }
    }
}
