package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RotationPlayer
import com.example.data.model.Team
import com.example.data.model.TeamRotationConfig
import com.example.ui.components.VolleyFormDialog
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditLineupDialog(
    initialTeamName: String,
    teamColor: Color,
    config: TeamRotationConfig,
    availableMixerTeams: List<Team>,
    hasTwoMixerTeams: Boolean,
    onLoadFromMixer: () -> Unit,
    onAssignMixerTeam: (Team) -> Unit,
    onOpenTeamMixer: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        teamName: String,
        playerCountOnCourt: Int,
        jerseys: Map<String, String>,
        names: Map<String, String>,
        benchPlayers: List<RotationPlayer>
    ) -> Unit
) {
    var teamName by remember(initialTeamName) { mutableStateOf(initialTeamName) }
    var courtCount by remember(config.playerCountOnCourt) { mutableIntStateOf(config.playerCountOnCourt.coerceIn(2, 6)) }

    val jerseyEdits = remember(config) {
        mutableStateMapOf<String, String>().apply {
            putAll(TeamRotationConfig.defaultJerseys())
            putAll(config.jerseyNumbers)
        }
    }
    val nameEdits = remember(config) {
        mutableStateMapOf<String, String>().apply {
            putAll(config.playerNames)
        }
    }
    val benchList = remember(config) {
        mutableStateListOf<RotationPlayer>().apply {
            addAll(config.benchPlayers)
        }
    }

    val activeRoles = remember(config.system, courtCount) {
        TeamRotationConfig.rolesFor(config.system, courtCount)
    }
    val maxBenchAllowed = (TeamRotationConfig.MAX_TOTAL_PLAYERS - courtCount).coerceAtLeast(0)

    fun roleTitle(roleKey: String, posIdx: Int): String = when (roleKey) {
        "SE" -> "Zuspiel (SE · P1)"
        "SE1" -> "Zuspiel 1 (SE1 · P1)"
        "SE2" -> "Zuspiel 2 (SE2 · P4)"
        "OH1" -> "Außen 1 (OH1 · P${posIdx + 1})"
        "OH2" -> "Außen 2 (OH2 · P${posIdx + 1})"
        "MB1" -> "Mitte 1 (MB1 · P${posIdx + 1})"
        "MB2" -> "Mitte 2 (MB2 · P${posIdx + 1})"
        "OPH" -> "Diagonal (OPH · P${posIdx + 1})"
        else -> "Position ${posIdx + 1} ($roleKey)"
    }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Kader & Trikots · $initialTeamName",
                color = teamColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abbrechen")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        teamName,
                        courtCount,
                        jerseyEdits.toMap(),
                        nameEdits.toMap(),
                        benchList.toList()
                    )
                },
                modifier = Modifier.height(48.dp).testTag("btn_save_lineup"),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Übernehmen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
                OutlinedTextField(
                    value = teamName,
                    onValueChange = { teamName = it },
                    label = { Text("Teamname", color = VolleyTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VolleyWhite,
                        unfocusedTextColor = VolleyWhite,
                        focusedBorderColor = teamColor,
                        unfocusedBorderColor = VolleyGrayBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_rotation_team_name")
                )

                Text("Feldspieler-Anzahl (2–6):", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (2..6).forEach { cnt ->
                        val active = courtCount == cnt
                        Button(
                            onClick = {
                                courtCount = cnt
                                val limit = TeamRotationConfig.MAX_TOTAL_PLAYERS - cnt
                                while (benchList.size > limit) {
                                    benchList.removeAt(benchList.lastIndex)
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(1f).height(42.dp).testTag("btn_court_count_$cnt"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) VolleyYellow else VolleyDarkSurfaceVariant,
                                contentColor = if (active) Color.Black else VolleyWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$cnt", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // Quick actions wrap cleanly
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val defaults = TeamRotationConfig.defaultJerseys()
                            activeRoles.forEachIndexed { idx, key ->
                                jerseyEdits[key] = "${idx + 1}"
                            }
                            jerseyEdits["LB"] = defaults["LB"] ?: "77"
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        border = BorderStroke(1.dp, VolleyGrayBorder),
                        modifier = Modifier.height(42.dp).testTag("btn_reset_default_jerseys")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nr. 1–$courtCount / LB 77", color = VolleyYellow, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }

                    if (hasTwoMixerTeams) {
                        OutlinedButton(
                            onClick = {
                                onLoadFromMixer()
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(1.dp, VolleyGreen),
                            modifier = Modifier.height(42.dp).testTag("btn_load_mixer_lineup")
                        ) {
                            Text("Beide Mixer-Teams laden", color = VolleyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Querverbindung zum Team-Mixer
                Surface(
                    color = VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VolleyGrayBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(16.dp))
                                Text(
                                    "Verknüpfung: Team-Mixer",
                                    color = VolleyYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenTeamMixer()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                border = BorderStroke(1.dp, VolleyYellow),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(40.dp).testTag("btn_open_mixer_from_kader")
                            ) {
                                Text("Zum Team-Mixer", color = VolleyYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(14.dp))
                            }
                        }

                        if (availableMixerTeams.isEmpty()) {
                            Text(
                                "Noch keine Mixer-Teams gespeichert. Erstelle Teams im Team-Mixer, um sie direkt auf diese Feldseite zu laden.",
                                color = VolleyTextSecondary,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                "Team aus Mixer für diese Seite übernehmen:",
                                color = VolleyWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableMixerTeams.forEach { mixerTeam ->
                                    OutlinedButton(
                                        onClick = {
                                            onAssignMixerTeam(mixerTeam)
                                            onDismiss()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        border = BorderStroke(1.dp, teamColor.copy(alpha = 0.8f)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(40.dp).testTag("btn_assign_mixer_team_${mixerTeam.id}")
                                    ) {
                                        Text(
                                            "${mixerTeam.name} (${mixerTeam.playerIds.size})",
                                            color = VolleyWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = VolleyGrayBorder)
                Text(
                    "Startaufstellung (${activeRoles.size} auf Feld):",
                    color = VolleyYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                activeRoles.forEachIndexed { idx, roleKey ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameEdits[roleKey] ?: "",
                            onValueChange = { nameEdits[roleKey] = it },
                            label = { Text(roleTitle(roleKey, idx), color = VolleyTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("Spielername", color = VolleyTextSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = VolleyWhite,
                                unfocusedTextColor = VolleyWhite,
                                focusedBorderColor = teamColor,
                                unfocusedBorderColor = VolleyGrayBorder
                            ),
                            modifier = Modifier.weight(1f).testTag("input_name_$roleKey")
                        )
                        OutlinedTextField(
                            value = jerseyEdits[roleKey] ?: "",
                            onValueChange = { input ->
                                jerseyEdits[roleKey] = input.filter { it.isDigit() }.take(3)
                            },
                            label = { Text("Nr.", color = VolleyTextSecondary, fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = VolleyYellow,
                                unfocusedTextColor = VolleyYellow,
                                focusedBorderColor = teamColor,
                                unfocusedBorderColor = VolleyGrayBorder
                            ),
                            modifier = Modifier.width(68.dp).testTag("input_jersey_$roleKey")
                        )
                    }
                }

                if (courtCount == 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameEdits["LB"] ?: "",
                            onValueChange = { nameEdits["LB"] = it },
                            label = { Text("Libero (LB)", color = Color(0xFFFFB300), fontSize = 11.sp) },
                            placeholder = { Text("Name", color = VolleyTextSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = VolleyWhite,
                                unfocusedTextColor = VolleyWhite,
                                focusedBorderColor = Color(0xFFFFB300),
                                unfocusedBorderColor = VolleyGrayBorder
                            ),
                            modifier = Modifier.weight(1f).testTag("input_name_LB")
                        )
                        OutlinedTextField(
                            value = jerseyEdits["LB"] ?: TeamRotationConfig.DEFAULT_LIBERO_JERSEY,
                            onValueChange = { input ->
                                jerseyEdits["LB"] = input.filter { it.isDigit() }.take(3)
                            },
                            label = { Text("Nr.", color = Color(0xFFFFB300), fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFFFB300),
                                unfocusedTextColor = Color(0xFFFFB300),
                                focusedBorderColor = Color(0xFFFFB300),
                                unfocusedBorderColor = VolleyGrayBorder
                            ),
                            modifier = Modifier.width(68.dp).testTag("input_jersey_LB")
                        )
                    }
                }

                HorizontalDivider(color = VolleyGrayBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Auswechselspieler (${benchList.size}/$maxBenchAllowed · Max 10 gesamt):",
                        color = VolleyYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (benchList.size < maxBenchAllowed) {
                                val nextNum = courtCount + benchList.size + 1
                                benchList.add(
                                    RotationPlayer(
                                        id = UUID.randomUUID().toString(),
                                        name = "Bank ${benchList.size + 1}",
                                        jerseyNumber = "$nextNum"
                                    )
                                )
                            }
                        },
                        enabled = benchList.size < maxBenchAllowed,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(40.dp).testTag("btn_add_bench_player"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Bankspieler hinzufügen", modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Bank", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (benchList.isEmpty()) {
                    Text(
                        "Keine Auswechselspieler eingetragen. Tippe „+ Bank“, um bis zu ${TeamRotationConfig.MAX_TOTAL_PLAYERS} Spieler pro Team zu erfassen.",
                        color = VolleyTextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    benchList.forEachIndexed { idx, benchPlayer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = benchPlayer.name,
                                onValueChange = { newName ->
                                    benchList[idx] = benchPlayer.copy(name = newName)
                                },
                                label = { Text("Bank ${idx + 1}", color = VolleyTextSecondary, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = VolleyWhite,
                                    unfocusedTextColor = VolleyWhite,
                                    focusedBorderColor = teamColor,
                                    unfocusedBorderColor = VolleyGrayBorder
                                ),
                                modifier = Modifier.weight(1f).testTag("input_bench_name_$idx")
                            )
                            OutlinedTextField(
                                value = benchPlayer.jerseyNumber,
                                onValueChange = { input ->
                                    benchList[idx] = benchPlayer.copy(jerseyNumber = input.filter { it.isDigit() }.take(3))
                                },
                                label = { Text("Nr.", color = VolleyTextSecondary, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = VolleyYellow,
                                    unfocusedTextColor = VolleyYellow,
                                    focusedBorderColor = teamColor,
                                    unfocusedBorderColor = VolleyGrayBorder
                                ),
                                modifier = Modifier.width(64.dp).testTag("input_bench_jersey_$idx")
                            )
                            IconButton(
                                onClick = { benchList.removeAt(idx) },
                                modifier = Modifier.size(44.dp).testTag("btn_delete_bench_$idx")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Bankspieler entfernen", tint = VolleyRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
    }
}

@Composable
fun SubstitutionDialog(
    teamName: String,
    teamColor: Color,
    config: TeamRotationConfig,
    onSubstitute: (courtRoleKey: String, benchPlayerId: String) -> Unit,
    onOpenLineupSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeRoles = remember(config.system, config.playerCountOnCourt) {
        TeamRotationConfig.rolesFor(config.system, config.playerCountOnCourt)
    }
    var selectedBenchId by remember(config.benchPlayers) {
        mutableStateOf(config.benchPlayers.firstOrNull()?.id)
    }
    var selectedCourtRole by remember(activeRoles) {
        mutableStateOf(activeRoles.firstOrNull())
    }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = VolleyYellow)
                Text(
                    "Wechsel · $teamName",
                    color = teamColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyDarkSurfaceVariant, contentColor = VolleyYellow),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
                if (config.benchPlayers.isEmpty()) {
                    Text(
                        "Aktuell sind keine Auswechselspieler auf der Bank eingetragen.",
                        color = VolleyTextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenLineupSetup()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_open_kader_for_sub"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black)
                    ) {
                        Text("Auswechselspieler im Kader anlegen", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("1. Einwechselspieler (Bank):", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        config.benchPlayers.forEach { bp ->
                            val isSelected = selectedBenchId == bp.id
                            OutlinedButton(
                                onClick = { selectedBenchId = bp.id },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) VolleyGreen.copy(alpha = 0.25f) else Color.Transparent,
                                    contentColor = VolleyWhite
                                ),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) VolleyGreen else VolleyGrayBorder)
                            ) {
                                Text(
                                    "EIN: #${bp.jerseyNumber} ${bp.name}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text("2. Auswechselspieler (Feld):", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeRoles.forEachIndexed { idx, roleKey ->
                            val jersey = config.jerseyNumbers[roleKey].orEmpty().ifBlank { "${idx + 1}" }
                            val name = config.playerNames[roleKey].orEmpty().ifBlank { roleKey }
                            val isSelected = selectedCourtRole == roleKey
                            OutlinedButton(
                                onClick = { selectedCourtRole = roleKey },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) teamColor.copy(alpha = 0.25f) else Color.Transparent,
                                    contentColor = VolleyWhite
                                ),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) teamColor else VolleyGrayBorder)
                            ) {
                                Text(
                                    "AUS: #$jersey $name ($roleKey)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val bId = selectedBenchId
                            val cRole = selectedCourtRole
                            if (bId != null && cRole != null) {
                                onSubstitute(cRole, bId)
                            }
                        },
                        enabled = selectedBenchId != null && selectedCourtRole != null,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_execute_substitution"),
                        colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("Wechsel durchführen", fontWeight = FontWeight.Bold)
                    }
                }

                if (config.substitutionLog.isNotEmpty()) {
                    HorizontalDivider(color = VolleyGrayBorder)
                    Text("Wechsel-Protokoll:", color = VolleyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VolleyDarkSurfaceVariant, RoundedCornerShape(6.dp))
                            .border(1.dp, VolleyGrayBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        config.substitutionLog.forEach { entry ->
                            Text("• $entry", color = VolleyWhite, fontSize = 12.sp)
                        }
                    }
                }
    }
}
