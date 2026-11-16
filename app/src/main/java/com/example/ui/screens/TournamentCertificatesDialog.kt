package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tournament.CertificateEntry
import com.example.domain.tournament.CertificatePdfExporter
import com.example.domain.tournament.CertificateStyle
import com.example.ui.components.VolleyFormDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentCertificatesDialog(
    tournamentName: String,
    certificates: List<CertificateEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedStyle by remember { mutableStateOf(CertificateStyle.YOUTH_HAIKYU) }
    var selectedRank by remember(certificates) { mutableIntStateOf(certificates.firstOrNull()?.rank ?: 1) }
    var exportStatusMsg by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val activeCert = remember(certificates, selectedRank) {
        certificates.find { it.rank == selectedRank } ?: certificates.firstOrNull()
    }

    // Play-Policy compliant SAF PDF save (zero storage permission required)
    var pendingSaveSingle by remember { mutableStateOf(false) }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null || activeCert == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            try {
                val targetList = if (pendingSaveSingle) listOf(activeCert) else certificates
                val pdfBytes = withContext(Dispatchers.Default) {
                    CertificatePdfExporter.generateCertificatesPdfBytes(
                        context = context,
                        entries = targetList,
                        style = selectedStyle
                    )
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(pdfBytes)
                    }
                }
                exportStatusMsg = if (pendingSaveSingle) {
                    "Urkunde (${activeCert.rank}. Platz) als PDF gespeichert!"
                } else {
                    "Alle ${certificates.size} Sieger-Urkunden als PDF gespeichert!"
                }
            } catch (e: Exception) {
                exportStatusMsg = "PDF-Speichern fehlgeschlagen: ${e.message ?: "Fehler"}"
            } finally {
                isExporting = false
            }
        }
    }

    VolleyFormDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(22.dp))
                Text(
                    "Sieger-Urkunden (Plätze 1–3)",
                    color = VolleyYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        if (certificates.isEmpty() || activeCert == null) {
            Text(
                "Noch keine Urkunden verfügbar. Sobald Finale und Spiel um Platz 3 beendet sind, werden Plätze 1–3 automatisch erstellt.",
                color = VolleyTextSecondary,
                fontSize = 14.sp
            )
            return@VolleyFormDialog
        }

        // 1. Style Template Selector (Jugend Haikyu / Klassisch / Modern)
        Text("Urkunden-Stil wählen:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CertificateStyle.values().forEach { style ->
                val isSelected = selectedStyle == style
                Surface(
                    color = if (isSelected) VolleyYellow else VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) VolleyYellow else VolleyGrayBorder),
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { selectedStyle = style }
                        .testTag("btn_cert_style_${style.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            style.label,
                            color = if (isSelected) Color.Black else VolleyWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        Text(
            selectedStyle.subtitle,
            color = VolleyTextSecondary,
            fontSize = 11.sp
        )

        // 2. Rank Selector (1. Platz Gold, 2. Platz Silber, 3. Platz Bronze)
        Text("Platzierung:", color = VolleyWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            certificates.forEach { cert ->
                val selected = cert.rank == activeCert.rank
                val accent = when (cert.rank) {
                    1 -> Color(0xFFFFD54F)
                    2 -> Color(0xFFB0BEC5)
                    else -> Color(0xFFD78A4E)
                }
                Surface(
                    color = if (selected) accent else VolleyDarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) accent else VolleyGrayBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable { selectedRank = cert.rank }
                        .testTag("btn_cert_rank_${cert.rank}")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Text(
                            "${cert.rank}. Platz",
                            color = if (selected) Color.Black else VolleyWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 3. Live WYSIWYG A4 Preview matching the exact PDF layout
        CertificatePreviewCanvasCard(
            entry = activeCert,
            style = selectedStyle,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(595f / 842f)
                .testTag("preview_certificate_card")
        )

        if (exportStatusMsg != null) {
            Surface(
                color = VolleyGreen.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, VolleyGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    exportStatusMsg!!,
                    color = VolleyGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // 4. PDF Export Actions (Share via FileProvider OR Save via Android Document Picker)
        Button(
            onClick = {
                scope.launch {
                    isExporting = true
                    try {
                        val file = withContext(Dispatchers.IO) {
                            val fname = CertificatePdfExporter.buildSafePdfFileName(tournamentName)
                            CertificatePdfExporter.writePdfToCacheFile(context, certificates, selectedStyle, fname)
                        }
                        CertificatePdfExporter.sharePdfViaFileProvider(
                            context = context,
                            pdfFile = file,
                            shareTitle = "Urkunden $tournamentName (Plätze 1–3)"
                        )
                        exportStatusMsg = "PDF für Plätze 1–3 bereit zum Teilen/Drucken."
                    } catch (e: Exception) {
                        exportStatusMsg = "PDF-Export fehlgeschlagen: ${e.message ?: "Fehler"}"
                    } finally {
                        isExporting = false
                    }
                }
            },
            enabled = !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_export_all_certs_pdf"),
            colors = ButtonDefaults.buttonColors(containerColor = VolleyYellow, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Alle 3 Urkunden als PDF teilen",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    pendingSaveSingle = false
                    createPdfLauncher.launch(CertificatePdfExporter.buildSafePdfFileName(tournamentName))
                },
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_save_all_certs_pdf"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyYellow.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Alle speichern", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = {
                    pendingSaveSingle = true
                    createPdfLauncher.launch(
                        CertificatePdfExporter.buildSafePdfFileName(tournamentName, activeCert.rank)
                    )
                },
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_save_single_cert_pdf"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VolleyWhite),
                border = BorderStroke(1.dp, VolleyGrayBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = VolleyYellow, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("${activeCert.rank}. Platz PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun CertificatePreviewCanvasCard(
    entry: CertificateEntry,
    style: CertificateStyle,
    modifier: Modifier = Modifier
) {
    val bgRes = CertificatePdfExporter.drawableResForStyle(style)

    val (inkColor, accentColor, topPadWeight, bodyWeight, bottomPadWeight, sideFrac, titleFamily) = when (style) {
        CertificateStyle.YOUTH_HAIKYU -> Tuple7(
            Color(0xFF121C34),
            Color(0xFFE65100),
            0.285f,
            0.495f,
            0.22f,
            0.14f,
            FontFamily.SansSerif
        )
        CertificateStyle.CLASSIC -> Tuple7(
            Color(0xFF16223E),
            Color(0xFFA87C22),
            0.195f,
            0.665f,
            0.14f,
            0.135f,
            FontFamily.Serif
        )
        CertificateStyle.MODERN -> Tuple7(
            Color(0xFF101E38),
            Color(0xFF0094D4),
            0.245f,
            0.585f,
            0.17f,
            0.11f,
            FontFamily.SansSerif
        )
    }

    val (medalText, medalBg, medalFg) = when (entry.rank) {
        1 -> Triple("1. PLATZ · TURNIERSIEGER", Color(0xFFD4A017), Color(0xFF141414))
        2 -> Triple("2. PLATZ · SILBER", Color(0xFFBCC6D2), Color(0xFF141822))
        3 -> Triple("3. PLATZ · BRONZE", Color(0xFFB87333), Color.White)
        else -> Triple("${entry.rank}. PLATZ", accentColor, Color.White)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, VolleyYellow.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
    ) {
        Image(
            painter = painterResource(id = bgRes),
            contentDescription = "Urkundenvorlage ${style.label}",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(topPadWeight))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(bodyWeight)
            ) {
                val sidePad = maxWidth * sideFrac
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = sidePad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "URKUNDE",
                            color = inkColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = titleFamily,
                            letterSpacing = 2.sp
                        )
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(2.dp)
                                .background(accentColor)
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            color = medalBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                medalText,
                                color = medalFg,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.tournamentName,
                            color = Color(0xFF445064),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "erhält dieses Team für herausragende Leistung:",
                            color = Color(0xFF556074),
                            fontSize = 8.sp,
                            fontFamily = if (style == CertificateStyle.CLASSIC) FontFamily.Serif else FontFamily.SansSerif,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            entry.teamName,
                            color = inkColor,
                            fontSize = if (entry.teamName.length > 20) 15.sp else 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = titleFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.club.isNotBlank()) {
                            Text(
                                entry.club,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "${entry.formatLabel} · ${entry.modeLabel}\n${entry.recordSummary}",
                            color = Color(0xFF4A586E),
                            fontSize = 8.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val footer = listOfNotNull(
                        entry.location.takeIf { it.isNotBlank() },
                        entry.date.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (footer.isNotBlank()) {
                        Text(
                            footer,
                            color = inkColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(bottomPadWeight))
        }
    }
}

private data class Tuple7<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)
