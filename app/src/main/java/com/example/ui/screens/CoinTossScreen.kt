package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.CoinSide
import com.example.ui.theme.*
import com.example.ui.viewmodel.VolleyViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CoinTossScreen(
    viewModel: VolleyViewModel,
    modifier: Modifier = Modifier
) {
    val result by viewModel.coinTossResult.collectAsState()
    val teams by viewModel.allTeams.collectAsState()

    var teamAName by remember { mutableStateOf("") }
    var teamBName by remember { mutableStateOf("") }
    var headsAssignedToTeamA by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    var flipJob by remember { mutableStateOf<Job?>(null) }
    var isFlipping by remember { mutableStateOf(false) }

    // Animatable rotation (0 to 1800 degrees = 5 full 360-degree rotations, always ending upright!)
    val rotationAnim = remember { Animatable(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolleyBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Clean compact top header row without thick TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Coin Toss (Münzwurf)",
                color = VolleyYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Team Assignment (Optional)
        Card(
            colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VolleyGrayBorder, RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Optionale Team-Zuordnung:",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = teamAName,
                        onValueChange = { teamAName = it },
                        label = { Text("Team A", color = VolleyWhite) },
                        placeholder = { Text("z.B. Team Gelb", color = VolleyTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("input_cointoss_team_a")
                    )

                    OutlinedTextField(
                        value = teamBName,
                        onValueChange = { teamBName = it },
                        label = { Text("Team B", color = VolleyWhite) },
                        placeholder = { Text("z.B. Team Schwarz", color = VolleyTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VolleyWhite,
                            unfocusedTextColor = VolleyWhite,
                            focusedBorderColor = VolleyYellow,
                            unfocusedBorderColor = VolleyGrayBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("input_cointoss_team_b")
                    )
                }

                if (teamAName.isNotBlank() && teamBName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Kopf: ${if (headsAssignedToTeamA) teamAName else teamBName}",
                            color = VolleyWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = { headsAssignedToTeamA = !headsAssignedToTeamA },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VolleyDarkSurfaceVariant,
                                contentColor = VolleyYellow
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Tauschen", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ANIMATED COIN WITH GUARANTEED NON-MIRRORED TEXT
        val currentSide = result?.side ?: CoinSide.HEADS
        val isShowingHeads = currentSide == CoinSide.HEADS

        // To completely eliminate mirrored text:
        // We spin the visual disc container in 3D during flipping.
        // Once the spin finishes, rotation is locked to 0f, ensuring text is ALWAYS upright and legible.
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    rotationY = if (isFlipping) rotationAnim.value else 0f
                    cameraDistance = 12f * density
                }
                .background(
                    if (isShowingHeads) VolleyYellow else Color(0xFFE0E0E0),
                    CircleShape
                )
                .border(
                    6.dp,
                    if (isShowingHeads) Color(0xFFF57F17) else Color(0xFF9E9E9E),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isShowingHeads) "KOPF" else "ZAHL",
                    color = Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isShowingHeads) "AUFSCHLAG" else "SEITE",
                    color = if (isShowingHeads) Color(0xFF5D4037) else Color(0xFF424242),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // RESULT CARD
        if (result != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VolleyDarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, VolleyYellow, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Ergebnis: ${result!!.side.label.uppercase()}",
                        color = VolleyYellow,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (!result!!.winnerTeamName.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Gewinner des Toss: ${result!!.winnerTeamName}",
                            color = VolleyWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // PRIMARY ACTION BUTTON: FLIP COIN (Race-condition safe!)
        Button(
            onClick = {
                // Cancel any previous in-flight flip job to eliminate race conditions
                flipJob?.cancel()
                flipJob = coroutineScope.launch {
                    isFlipping = true
                    rotationAnim.snapTo(0f)

                    // Execute model flip
                    val headsChoice = if (headsAssignedToTeamA) teamAName else teamBName
                    viewModel.flipCoin(
                        teamAName = teamAName.ifBlank { null },
                        teamBName = teamBName.ifBlank { null },
                        headsTeam = if (teamAName.isNotBlank()) headsChoice else null
                    )

                    // Animate 5 complete 360-degree flips (1800 degrees)
                    rotationAnim.animateTo(
                        targetValue = 1800f,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                    )

                    isFlipping = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_flip_coin"),
            colors = ButtonDefaults.buttonColors(
                containerColor = VolleyYellow,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                "Münze werfen",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }

        if (result != null) {
            OutlinedButton(
                onClick = {
                    flipJob?.cancel()
                    isFlipping = false
                    viewModel.resetCoinToss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_reset_cointoss"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = VolleyRed)
                Spacer(Modifier.width(6.dp))
                Text("Ergebnis zurücksetzen", fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}
