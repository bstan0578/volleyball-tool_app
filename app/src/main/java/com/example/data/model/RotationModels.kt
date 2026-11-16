package com.example.data.model

import java.util.UUID

enum class RotationSystem(val label: String, val shortLabel: String) {
    SYSTEM_5_1("5-1 System", "5-1"),
    SYSTEM_4_2("4-2 System", "4-2"),
    SYSTEM_6_2("6-2 System", "6-2");

    fun next(): RotationSystem {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

enum class RotationPhase(val label: String) {
    BASE("Grund"),
    SERVE("Aufschlag"),
    RECEIVE("Annahme"),
    IN_GAME("Im Spiel")
}

data class RotationPlayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val jerseyNumber: String
)

/**
 * Standard roles on a volleyball court (2..6 field players + up to 4 substitutes = max 10 total).
 * For 6v6 clockwise P1..P6:
 * P1 (Back Right), P2 (Front Right), P3 (Front Middle), P4 (Front Left), P5 (Back Left), P6 (Back Middle).
 * Default jerseys equal starting court position 1..6; Libero is always 77.
 */
data class TeamRotationConfig(
    val system: RotationSystem = RotationSystem.SYSTEM_5_1,
    val phase: RotationPhase = RotationPhase.RECEIVE,
    val rotationStep: Int = 0,
    val playerCountOnCourt: Int = 6, // 2..6
    val liberoActive: Boolean = true,
    val jerseyNumbers: Map<String, String> = defaultJerseys(),
    val playerNames: Map<String, String> = emptyMap(),
    val benchPlayers: List<RotationPlayer> = emptyList(),
    val substitutionLog: List<String> = emptyList()
) {
    companion object {
        val ROLES_5_1 = listOf("SE", "OH1", "MB1", "OPH", "OH2", "MB2")
        val ROLES_4_2 = listOf("SE1", "OH1", "MB1", "SE2", "OH2", "MB2")
        val ROLES_6_2 = listOf("SE1", "OH1", "MB1", "SE2", "OH2", "MB2")
        const val MAX_TOTAL_PLAYERS = 10
        const val DEFAULT_LIBERO_JERSEY = "77"

        fun rolesFor(system: RotationSystem, playerCountOnCourt: Int): List<String> {
            val count = playerCountOnCourt.coerceIn(2, 6)
            return when (count) {
                2 -> listOf("P1", "P2")
                3 -> listOf("P1", "P2", "P3")
                4 -> listOf("SE", "OH1", "OPH", "OH2")
                5 -> listOf("SE", "OH1", "MB1", "OPH", "OH2")
                else -> when (system) {
                    RotationSystem.SYSTEM_5_1 -> ROLES_5_1
                    RotationSystem.SYSTEM_4_2 -> ROLES_4_2
                    RotationSystem.SYSTEM_6_2 -> ROLES_6_2
                }
            }
        }

        fun defaultJerseys(): Map<String, String> {
            return mapOf(
                "SE" to "1",
                "SE1" to "1",
                "OH1" to "2",
                "MB1" to "3",
                "OPH" to "4",
                "SE2" to "4",
                "OH2" to "5",
                "MB2" to "6",
                "P1" to "1",
                "P2" to "2",
                "P3" to "3",
                "P4" to "4",
                "P5" to "5",
                "LB" to DEFAULT_LIBERO_JERSEY
            )
        }
    }
}

data class CourtPlayerToken(
    val roleLabel: String,
    val originalRole: String,
    val jerseyNumber: String,
    val playerName: String = "",
    val zoneNumber: Int, // Rotational zone 1..N
    val xHalfNorm: Float, // 0f (left sideline facing net) .. 1f (right sideline facing net)
    val yNetNorm: Float,  // 0f (at the net) .. 1f (at the baseline); > 1f = behind baseline serving
    val isServer: Boolean = false,
    val isLibero: Boolean = false,
    val inGameTargetZone: Int? = null
)

data class TrackedCourtTarget(
    val side: TeamSide,
    val originalRole: String,
    val isLiberoTarget: Boolean = false
) {
    fun matches(tokenSide: TeamSide, token: CourtPlayerToken): Boolean {
        if (tokenSide != side) return false
        return if (isLiberoTarget) {
            token.isLibero
        } else {
            !token.isLibero && token.originalRole == originalRole
        }
    }
}

data class ServerDisplayInfo(
    val roleKey: String,
    val jerseyNumber: String,
    val playerName: String
)

object RotationEngine {

    fun resolveServerInfo(
        config: TeamRotationConfig,
        serverIndex: Int,
        playerCountOverride: Int? = null
    ): ServerDisplayInfo {
        val count = (playerCountOverride ?: config.playerCountOnCourt).coerceIn(2, 6)
        val roster = TeamRotationConfig.rolesFor(config.system, count)
        val safeIdx = ((serverIndex % count) + count) % count
        val roleKey = roster[safeIdx]
        val jersey = config.jerseyNumbers[roleKey].orEmpty().ifBlank {
            TeamRotationConfig.defaultJerseys()[roleKey] ?: "${safeIdx + 1}"
        }
        val playerName = config.playerNames[roleKey].orEmpty().trim()
        return ServerDisplayInfo(
            roleKey = roleKey,
            jerseyNumber = jersey,
            playerName = playerName
        )
    }

    val OVERLAP_PAIRS = listOf(
        4 to 3, 3 to 2, // Front row horizontal neighbors
        5 to 6, 6 to 1, // Back row horizontal neighbors
        4 to 5, 3 to 6, 2 to 1 // Front-back vertical neighbors
    )

    fun zoneLabel(zone: Int): String = when (zone) {
        1 -> "P1 (Hinten Rechts)"
        2 -> "P2 (Vorne Rechts)"
        3 -> "P3 (Vorne Mitte)"
        4 -> "P4 (Vorne Links)"
        5 -> "P5 (Hinten Links)"
        6 -> "P6 (Hinten Mitte)"
        else -> "P$zone"
    }

    fun overlapNeighborZones(zone: Int): List<Int> {
        return OVERLAP_PAIRS.mapNotNull { (a, b) ->
            when {
                a == zone -> b
                b == zone -> a
                else -> null
            }
        }
    }

    /**
     * Computes exact legal volleyball player coordinates on a team's half-court.
     * Supports 2..6 players on court as well as BASE, SERVE, RECEIVE, and IN_GAME (switched rally defense) phases.
     * For 6v6 BASE/SERVE/RECEIVE, strictly enforces FIVB positional overlap constraints.
     */
    fun calculatePositions(
        config: TeamRotationConfig
    ): List<CourtPlayerToken> {
        val count = config.playerCountOnCourt.coerceIn(2, 6)
        if (count < 6) {
            return calculateSmallSidedPositions(config, count)
        }

        val baseRoster = TeamRotationConfig.rolesFor(config.system, 6)
        val step = ((config.rotationStep % 6) + 6) % 6

        // Clockwise rotation: Zone P(z) holds baseRoster[(z - 1 + step) % 6]
        val zoneRoles = (1..6).associateWith { zone ->
            baseRoster[(zone - 1 + step) % 6]
        }

        val coords: MutableMap<Int, Pair<Float, Float>> = mutableMapOf(
            4 to (0.18f to 0.22f),
            3 to (0.50f to 0.22f),
            2 to (0.82f to 0.22f),
            5 to (0.18f to 0.68f),
            6 to (0.50f to 0.68f),
            1 to (0.82f to 0.68f)
        )
        val inGameZones = mutableMapOf<Int, Int>()

        when (config.phase) {
            RotationPhase.BASE -> {
                // Clean 3x2 rotational order already set
            }
            RotationPhase.SERVE -> {
                coords[1] = 0.80f to 1.07f
                val r4 = zoneRoles[4]!!
                val r2 = zoneRoles[2]!!
                coords[4] = when {
                    r4.startsWith("MB") -> 0.36f to 0.16f
                    r4 == "SE" || r4 == "SE1" || r4 == "SE2" || r4 == "OPH" -> 0.28f to 0.16f
                    else -> 0.20f to 0.16f
                }
                coords[3] = 0.50f to 0.16f
                coords[2] = when {
                    r2.startsWith("OH") -> 0.78f to 0.16f
                    else -> 0.72f to 0.16f
                }
                coords[5] = 0.22f to 0.65f
                coords[6] = 0.50f to 0.74f
            }
            RotationPhase.RECEIVE -> {
                when (config.system) {
                    RotationSystem.SYSTEM_5_1 -> apply51ReceiveFormation(step, coords)
                    RotationSystem.SYSTEM_4_2 -> apply42ReceiveFormation(step, coords)
                    RotationSystem.SYSTEM_6_2 -> apply62ReceiveFormation(step, coords)
                }
            }
            RotationPhase.IN_GAME -> {
                // Specialized rally/defense positions after serve/switch:
                // Front row: OH -> P4 (Left), MB -> P3 (Middle), OPH/SE -> P2 (Right)
                // Back row:  MB/LB -> P5 (Left), OH (e.g. OH2 after serving on P1!) -> P6 (Middle), SE/OPH -> P1 (Right)
                for (zone in 1..6) {
                    val role = zoneRoles[zone]!!
                    val isFront = zone == 2 || zone == 3 || zone == 4
                    val targetZone = if (isFront) {
                        when {
                            role.startsWith("OH") -> 4
                            role.startsWith("MB") -> 3
                            else -> 2 // OPH, SE, SE1, SE2
                        }
                    } else {
                        when {
                            role.startsWith("MB") -> 5
                            role.startsWith("OH") -> 6
                            else -> 1 // SE, SE1, SE2, OPH
                        }
                    }
                    inGameZones[zone] = targetZone
                    coords[zone] = when (targetZone) {
                        4 -> 0.18f to 0.22f
                        3 -> 0.50f to 0.18f
                        2 -> 0.82f to 0.22f
                        5 -> 0.20f to 0.66f
                        6 -> 0.50f to 0.74f
                        else -> 0.80f to 0.66f // 1
                    }
                }
            }
        }

        return (1..6).map { zone ->
            val origRole = zoneRoles[zone]!!
            val isBackRow = zone == 1 || zone == 5 || zone == 6
            val isMiddle = origRole == "MB1" || origRole == "MB2"
            val useLibero = config.liberoActive && isMiddle && isBackRow && !(zone == 1 && config.phase == RotationPhase.SERVE)
            val displayRole = if (useLibero) "LB" else origRole
            val key = if (useLibero) "LB" else origRole
            val jersey = config.jerseyNumbers[key].orEmpty().ifBlank {
                TeamRotationConfig.defaultJerseys()[key] ?: "$zone"
            }
            val playerName = config.playerNames[key].orEmpty()
            val (x, y) = coords[zone]!!

            CourtPlayerToken(
                roleLabel = displayRole,
                originalRole = origRole,
                jerseyNumber = jersey,
                playerName = playerName,
                zoneNumber = zone,
                xHalfNorm = x,
                yNetNorm = y,
                isServer = zone == 1 && config.phase == RotationPhase.SERVE,
                isLibero = useLibero,
                inGameTargetZone = inGameZones[zone]
            )
        }
    }

    private fun calculateSmallSidedPositions(
        config: TeamRotationConfig,
        count: Int
    ): List<CourtPlayerToken> {
        val roster = TeamRotationConfig.rolesFor(config.system, count)
        val step = ((config.rotationStep % count) + count) % count

        // Slot 0 in rotation order is the active Server (P1)
        val slotRoles = (0 until count).map { idx ->
            roster[(idx + step) % count]
        }

        // Standard zones per format mapped for Base, Serve, Receive, InGame
        val coordsList: List<Pair<Float, Float>> = when (count) {
            2 -> when (config.phase) {
                RotationPhase.BASE -> listOf(0.72f to 0.54f, 0.28f to 0.54f)
                RotationPhase.SERVE -> listOf(0.76f to 1.07f, 0.48f to 0.24f)
                RotationPhase.RECEIVE -> listOf(0.72f to 0.62f, 0.28f to 0.62f)
                RotationPhase.IN_GAME -> listOf(0.72f to 0.45f, 0.28f to 0.45f)
            }
            3 -> when (config.phase) {
                RotationPhase.BASE -> listOf(0.78f to 0.65f, 0.22f to 0.65f, 0.50f to 0.24f)
                RotationPhase.SERVE -> listOf(0.78f to 1.07f, 0.30f to 0.64f, 0.50f to 0.20f)
                RotationPhase.RECEIVE -> listOf(0.74f to 0.64f, 0.26f to 0.64f, 0.52f to 0.24f)
                RotationPhase.IN_GAME -> listOf(0.72f to 0.66f, 0.28f to 0.66f, 0.50f to 0.22f)
            }
            4 -> when (config.phase) {
                RotationPhase.BASE -> listOf(0.78f to 0.68f, 0.76f to 0.24f, 0.24f to 0.24f, 0.22f to 0.68f)
                RotationPhase.SERVE -> listOf(0.78f to 1.07f, 0.68f to 0.18f, 0.32f to 0.18f, 0.34f to 0.66f)
                RotationPhase.RECEIVE -> listOf(0.76f to 0.64f, 0.66f to 0.22f, 0.24f to 0.54f, 0.50f to 0.72f)
                RotationPhase.IN_GAME -> {
                    // Specialize in-game: slot roles switch to Left, Mid-Net, Right, Mid-Back
                    val posBySlot = (0 until 4).map { i ->
                        when (slotRoles[i]) {
                            "SE" -> 0.66f to 0.22f
                            "OH1" -> 0.20f to 0.24f
                            "OPH" -> 0.80f to 0.24f
                            else -> 0.50f to 0.70f // OH2 defends deep middle
                        }
                    }
                    posBySlot
                }
            }
            else -> when (config.phase) { // 5 players
                RotationPhase.BASE -> listOf(0.80f to 0.68f, 0.80f to 0.22f, 0.50f to 0.22f, 0.20f to 0.22f, 0.20f to 0.68f)
                RotationPhase.SERVE -> listOf(0.80f to 1.07f, 0.76f to 0.18f, 0.50f to 0.18f, 0.24f to 0.18f, 0.36f to 0.68f)
                RotationPhase.RECEIVE -> listOf(0.80f to 0.66f, 0.74f to 0.22f, 0.48f to 0.22f, 0.22f to 0.56f, 0.48f to 0.72f)
                RotationPhase.IN_GAME -> (0 until 5).map { i ->
                    when (slotRoles[i]) {
                        "SE" -> 0.78f to 0.64f
                        "OH1" -> 0.20f to 0.22f
                        "MB1" -> 0.50f to 0.18f
                        "OPH" -> 0.80f to 0.22f
                        else -> 0.48f to 0.72f
                    }
                }
            }
        }

        return (0 until count).map { idx ->
            val role = slotRoles[idx]
            val zoneNumber = idx + 1
            val defaultJ = TeamRotationConfig.defaultJerseys()[role] ?: "${roster.indexOf(role) + 1}"
            val jersey = config.jerseyNumbers[role].orEmpty().ifBlank { defaultJ }
            val name = config.playerNames[role].orEmpty()
            val (x, y) = coordsList[idx]

            CourtPlayerToken(
                roleLabel = role,
                originalRole = role,
                jerseyNumber = jersey,
                playerName = name,
                zoneNumber = zoneNumber,
                xHalfNorm = x,
                yNetNorm = y,
                isServer = idx == 0 && config.phase == RotationPhase.SERVE,
                isLibero = false
            )
        }
    }

    private fun apply51ReceiveFormation(step: Int, coords: MutableMap<Int, Pair<Float, Float>>) {
        when (step) {
            0 -> {
                coords[4] = 0.14f to 0.25f
                coords[3] = 0.50f to 0.22f
                coords[2] = 0.78f to 0.54f
                coords[5] = 0.22f to 0.68f
                coords[6] = 0.50f to 0.72f
                coords[1] = 0.86f to 0.62f
            }
            1 -> {
                coords[4] = 0.20f to 0.56f
                coords[3] = 0.54f to 0.18f
                coords[2] = 0.82f to 0.24f
                coords[5] = 0.42f to 0.74f
                coords[6] = 0.56f to 0.30f
                coords[1] = 0.80f to 0.68f
            }
            2 -> {
                coords[4] = 0.18f to 0.20f
                coords[3] = 0.30f to 0.58f
                coords[2] = 0.84f to 0.24f
                coords[5] = 0.22f to 0.68f
                coords[6] = 0.52f to 0.72f
                coords[1] = 0.80f to 0.68f
            }
            3 -> {
                coords[4] = 0.36f to 0.16f
                coords[3] = 0.56f to 0.22f
                coords[2] = 0.80f to 0.58f
                coords[5] = 0.22f to 0.68f
                coords[6] = 0.50f to 0.72f
                coords[1] = 0.84f to 0.76f
            }
            4 -> {
                coords[4] = 0.20f to 0.56f
                coords[3] = 0.64f to 0.16f
                coords[2] = 0.84f to 0.24f
                coords[5] = 0.44f to 0.72f
                coords[6] = 0.64f to 0.76f
                coords[1] = 0.82f to 0.68f
            }
            else -> {
                coords[4] = 0.16f to 0.22f
                coords[3] = 0.28f to 0.58f
                coords[2] = 0.78f to 0.16f
                coords[5] = 0.18f to 0.76f
                coords[6] = 0.52f to 0.70f
                coords[1] = 0.82f to 0.68f
            }
        }
    }

    private fun apply42ReceiveFormation(step: Int, coords: MutableMap<Int, Pair<Float, Float>>) {
        when (step % 3) {
            0 -> {
                coords[4] = 0.36f to 0.16f
                coords[3] = 0.56f to 0.22f
                coords[2] = 0.80f to 0.58f
                coords[5] = 0.22f to 0.68f
                coords[6] = 0.50f to 0.72f
                coords[1] = 0.84f to 0.76f
            }
            1 -> {
                coords[4] = 0.20f to 0.56f
                coords[3] = 0.62f to 0.16f
                coords[2] = 0.84f to 0.24f
                coords[5] = 0.44f to 0.72f
                coords[6] = 0.64f to 0.76f
                coords[1] = 0.82f to 0.68f
            }
            else -> {
                coords[4] = 0.16f to 0.22f
                coords[3] = 0.28f to 0.58f
                coords[2] = 0.78f to 0.16f
                coords[5] = 0.18f to 0.76f
                coords[6] = 0.52f to 0.70f
                coords[1] = 0.82f to 0.68f
            }
        }
    }

    /**
     * 6-2 system: two setters opposite each other (SE1 & SE2).
     * Whichever setter is in the back row (P1, P6, P5) penetrates to set while the front-row setter hits.
     */
    private fun apply62ReceiveFormation(step: Int, coords: MutableMap<Int, Pair<Float, Float>>) {
        apply51ReceiveFormation(step % 3, coords)
    }
}
