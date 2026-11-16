package com.example.domain.tournament

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.R
import java.io.ByteArrayOutputStream
import java.io.File

object CertificatePdfExporter {

    const val A4_WIDTH_PT = 595
    const val A4_HEIGHT_PT = 842

    fun drawableResForStyle(style: CertificateStyle): Int = when (style) {
        CertificateStyle.YOUTH_HAIKYU -> R.drawable.img_cert_youth
        CertificateStyle.CLASSIC -> R.drawable.img_cert_classic
        CertificateStyle.MODERN -> R.drawable.img_cert_modern
    }

    private data class StyleLayoutSpec(
        val topFraction: Float,
        val bottomFraction: Float,
        val sidePadFraction: Float,
        val primaryInkColor: Int,
        val accentColor: Int,
        val mutedColor: Int,
        val titleFont: Typeface,
        val serifAccent: Boolean
    )

    private fun layoutSpecFor(style: CertificateStyle): StyleLayoutSpec = when (style) {
        CertificateStyle.YOUTH_HAIKYU -> StyleLayoutSpec(
            topFraction = 0.285f,
            bottomFraction = 0.77f,
            sidePadFraction = 0.14f,
            primaryInkColor = Color.rgb(18, 28, 52),
            accentColor = Color.rgb(230, 81, 0),
            mutedColor = Color.rgb(66, 78, 98),
            titleFont = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            serifAccent = false
        )
        CertificateStyle.CLASSIC -> StyleLayoutSpec(
            topFraction = 0.195f,
            bottomFraction = 0.855f,
            sidePadFraction = 0.135f,
            primaryInkColor = Color.rgb(22, 34, 62),
            accentColor = Color.rgb(168, 124, 34),
            mutedColor = Color.rgb(74, 68, 56),
            titleFont = Typeface.create(Typeface.SERIF, Typeface.BOLD),
            serifAccent = true
        )
        CertificateStyle.MODERN -> StyleLayoutSpec(
            topFraction = 0.245f,
            bottomFraction = 0.825f,
            sidePadFraction = 0.11f,
            primaryInkColor = Color.rgb(16, 30, 56),
            accentColor = Color.rgb(0, 148, 212),
            mutedColor = Color.rgb(74, 88, 110),
            titleFont = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            serifAccent = false
        )
    }

    /**
     * Generates a multi-page (or single-page) A4 PDF for the given certificate entries and style.
     * Uses Android's native PdfDocument on device, with a deterministic pure-Kotlin PDF-1.4 fallback
     * if native Skia PDF libraries are unavailable (such as headless JVM/Robolectric environments).
     */
    fun generateCertificatesPdfBytes(
        context: Context,
        entries: List<CertificateEntry>,
        style: CertificateStyle
    ): ByteArray {
        if (entries.isEmpty()) return ByteArray(0)
        return try {
            generateNativeAndroidPdf(context, entries, style)
        } catch (_: Throwable) {
            generatePureKotlinPdfFallback(entries, style)
        }
    }

    private fun generateNativeAndroidPdf(
        context: Context,
        entries: List<CertificateEntry>,
        style: CertificateStyle
    ): ByteArray {
        val pdfDoc = PdfDocument()
        try {
            val resId = drawableResForStyle(style)
            val bgBitmap = try {
                BitmapFactory.decodeResource(context.resources, resId)
            } catch (_: Throwable) {
                null
            }
            val spec = layoutSpecFor(style)

            entries.forEachIndexed { idx, entry ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, idx + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                if (bgBitmap != null) {
                    val srcRect = Rect(0, 0, bgBitmap.width, bgBitmap.height)
                    val dstRect = Rect(0, 0, A4_WIDTH_PT, A4_HEIGHT_PT)
                    val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bgBitmap, srcRect, dstRect, bmpPaint)
                } else {
                    drawFallbackTemplate(canvas, style, spec)
                }

                drawCertificateContent(canvas, entry, style, spec)
                pdfDoc.finishPage(page)
            }

            bgBitmap?.recycle()

            val out = ByteArrayOutputStream()
            pdfDoc.writeTo(out)
            return out.toByteArray()
        } finally {
            pdfDoc.close()
        }
    }

    private fun pdfEscapeWinAnsi(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '\r', '\n' -> sb.append(' ')
                '–', '—' -> sb.append('-')
                '·' -> sb.append("\\267")
                'Ä' -> sb.append("\\304")
                'Ö' -> sb.append("\\326")
                'Ü' -> sb.append("\\334")
                'ä' -> sb.append("\\344")
                'ö' -> sb.append("\\366")
                'ü' -> sb.append("\\374")
                'ß' -> sb.append("\\337")
                else -> if (ch.code in 32..126) sb.append(ch) else sb.append('?')
            }
        }
        return sb.toString()
    }

    private fun generatePureKotlinPdfFallback(
        entries: List<CertificateEntry>,
        style: CertificateStyle
    ): ByteArray {
        // Object layout:
        // 1: Catalog
        // 2: Pages
        // 3: Font Helvetica (/F1)
        // 4: Font Helvetica-Bold (/F2)
        // 5 + 2*i: Page i
        // 6 + 2*i: Content stream i
        val objects = mutableListOf<ByteArray>()
        val pageIds = entries.indices.map { 5 + it * 2 }

        fun ascii(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)

        objects.add(ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"))
        objects.add(ascii("2 0 obj\n<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${entries.size} >>\nendobj\n"))
        objects.add(ascii("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n"))
        objects.add(ascii("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n"))

        val (bgRgb, accentRgb, inkRgb) = when (style) {
            CertificateStyle.YOUTH_HAIKYU -> Triple("0.97 0.98 1.00", "0.90 0.32 0.00", "0.07 0.11 0.20")
            CertificateStyle.CLASSIC -> Triple("0.99 0.97 0.93", "0.66 0.49 0.13", "0.09 0.13 0.24")
            CertificateStyle.MODERN -> Triple("0.96 0.98 1.00", "0.00 0.58 0.83", "0.06 0.12 0.22")
        }

        entries.forEachIndexed { i, entry ->
            val pageObjId = 5 + i * 2
            val contentObjId = 6 + i * 2
            val medalRgb = when (entry.rank) {
                1 -> "0.83 0.63 0.09"
                2 -> "0.74 0.78 0.82"
                else -> "0.72 0.45 0.20"
            }
            val rankLabel = when (entry.rank) {
                1 -> "1. PLATZ · TURNIERSIEGER"
                2 -> "2. PLATZ · SILBER"
                3 -> "3. PLATZ · BRONZE"
                else -> "${entry.rank}. PLATZ"
            }
            val footer = listOfNotNull(
                entry.location.takeIf { it.isNotBlank() },
                entry.date.takeIf { it.isNotBlank() }
            ).joinToString(" · ")

            val stream = buildString {
                // Background & border frame
                append("$bgRgb rg 0 0 $A4_WIDTH_PT $A4_HEIGHT_PT re f\n")
                append("$accentRgb RG 6 w 24 24 ${A4_WIDTH_PT - 48} ${A4_HEIGHT_PT - 48} re S\n")
                append("$inkRgb RG 1.5 w 34 34 ${A4_WIDTH_PT - 68} ${A4_HEIGHT_PT - 68} re S\n")
                // Medal banner pill
                append("$medalRgb rg 95 590 405 38 re f\n")
                // Texts
                append("BT $inkRgb rg /F2 34 Tf 215 700 Td (${pdfEscapeWinAnsi("URKUNDE")}) Tj ET\n")
                append("BT 1 1 1 rg /F2 15 Tf 170 603 Td (${pdfEscapeWinAnsi(rankLabel)}) Tj ET\n")
                append("BT $inkRgb rg /F2 14 Tf 80 540 Td (${pdfEscapeWinAnsi(entry.tournamentName)}) Tj ET\n")
                append("BT $inkRgb rg /F2 26 Tf 80 470 Td (${pdfEscapeWinAnsi(entry.teamName)}) Tj ET\n")
                if (entry.club.isNotBlank()) {
                    append("BT $accentRgb rg /F2 14 Tf 80 435 Td (${pdfEscapeWinAnsi(entry.club)}) Tj ET\n")
                }
                append("BT $inkRgb rg /F1 12 Tf 80 390 Td (${pdfEscapeWinAnsi("${entry.formatLabel} · ${entry.modeLabel}")}) Tj ET\n")
                if (entry.recordSummary.isNotBlank()) {
                    append("BT $inkRgb rg /F1 12 Tf 80 368 Td (${pdfEscapeWinAnsi(entry.recordSummary)}) Tj ET\n")
                }
                if (footer.isNotBlank()) {
                    append("BT $inkRgb rg /F2 12 Tf 80 120 Td (${pdfEscapeWinAnsi(footer)}) Tj ET\n")
                }
                append("BT $accentRgb rg /F1 10 Tf 80 70 Td (${pdfEscapeWinAnsi("Stil: ${style.label}")}) Tj ET\n")
            }
            val streamBytes = ascii(stream)
            objects.add(
                ascii("$pageObjId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $A4_WIDTH_PT $A4_HEIGHT_PT] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentObjId 0 R >>\nendobj\n")
            )
            objects.add(
                ascii("$contentObjId 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n") +
                    streamBytes +
                    ascii("endstream\nendobj\n")
            )
        }

        val out = ByteArrayOutputStream()
        out.write(ascii("%PDF-1.4\n"))
        val offsets = IntArray(objects.size + 1)
        objects.forEachIndexed { idx, bytes ->
            offsets[idx + 1] = out.size()
            out.write(bytes)
        }
        val xrefPos = out.size()
        out.write(ascii("xref\n0 ${objects.size + 1}\n"))
        out.write(ascii("0000000000 65535 f \n"))
        for (i in 1..objects.size) {
            out.write(ascii(String.format("%010d 00000 n \n", offsets[i])))
        }
        out.write(ascii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n"))
        return out.toByteArray()
    }

    private fun drawFallbackTemplate(canvas: Canvas, style: CertificateStyle, spec: StyleLayoutSpec) {
        val w = A4_WIDTH_PT.toFloat()
        val h = A4_HEIGHT_PT.toFloat()
        canvas.drawColor(Color.WHITE)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 6f
            color = spec.accentColor
        }
        canvas.drawRect(24f, 24f, w - 24f, h - 24f, borderPaint)
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f
            color = spec.primaryInkColor
        }
        canvas.drawRect(34f, 34f, w - 34f, h - 34f, innerPaint)
    }

    private fun drawCertificateContent(
        canvas: Canvas,
        entry: CertificateEntry,
        style: CertificateStyle,
        spec: StyleLayoutSpec
    ) {
        val pageW = A4_WIDTH_PT.toFloat()
        val pageH = A4_HEIGHT_PT.toFloat()
        val padX = pageW * spec.sidePadFraction
        val usableW = (pageW - padX * 2f).toInt().coerceAtLeast(200)
        val centerX = pageW / 2f

        val contentTop = pageH * spec.topFraction
        val contentBottom = pageH * spec.bottomFraction
        val availableH = contentBottom - contentTop

        var y = contentTop + availableH * 0.04f

        // 1. Main Title ("URKUNDE")
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.primaryInkColor
            textSize = if (style == CertificateStyle.YOUTH_HAIKYU) 34f else 36f
            typeface = Typeface.create(spec.titleFont, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        canvas.drawText("URKUNDE", centerX, y + 32f, headerPaint)
        y += 44f

        // 2. Thin accent divider rule
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.accentColor
            strokeWidth = 2.2f
        }
        canvas.drawLine(centerX - 90f, y, centerX + 90f, y, rulePaint)
        y += 22f

        // 3. Medal / Rank Pill
        val (medalTitle, medalBg, medalFg) = when (entry.rank) {
            1 -> Triple("1. PLATZ · TURNIERSIEGER", Color.rgb(212, 160, 23), Color.rgb(20, 20, 20))
            2 -> Triple("2. PLATZ · SILBER", Color.rgb(188, 198, 210), Color.rgb(20, 24, 34))
            3 -> Triple("3. PLATZ · BRONZE", Color.rgb(184, 115, 51), Color.WHITE)
            else -> Triple("${entry.rank}. PLATZ", spec.accentColor, Color.WHITE)
        }

        val pillW = usableW * 0.72f
        val pillH = 34f
        val pillRect = RectF(centerX - pillW / 2f, y, centerX + pillW / 2f, y + pillH)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = medalBg
            this.style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, 17f, 17f, pillPaint)

        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = medalFg
            textSize = 14.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val pillTextY = pillRect.centerY() - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2f
        canvas.drawText(medalTitle, centerX, pillTextY, pillTextPaint)
        y += pillH + 20f

        // 4. Tournament Name
        val tourneyTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.mutedColor
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val tourneyLayout = StaticLayout.Builder.obtain(entry.tournamentName, 0, entry.tournamentName.length, tourneyTp, usableW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(padX, y)
        tourneyLayout.draw(canvas)
        canvas.restore()
        y += tourneyLayout.height + 16f

        // 5. Subtitle "wird verliehen an"
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.mutedColor
            textSize = 12.5f
            typeface = Typeface.create(if (spec.serifAccent) Typeface.SERIF else Typeface.SANS_SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("erhält dieses Team für herausragende Leistung:", centerX, y + 12f, subPaint)
        y += 28f

        // 6. Hero Team Name (auto-wrapped cleanly via StaticLayout)
        val teamFontSize = when {
            entry.teamName.length > 26 -> 24f
            entry.teamName.length > 18 -> 28f
            else -> 33f
        }
        val teamTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.primaryInkColor
            textSize = teamFontSize
            typeface = Typeface.create(spec.titleFont, Typeface.BOLD)
        }
        val teamLayout = StaticLayout.Builder.obtain(entry.teamName, 0, entry.teamName.length, teamTp, usableW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(padX, y)
        teamLayout.draw(canvas)
        canvas.restore()
        y += teamLayout.height + 10f

        // 7. Optional Club line
        if (entry.club.isNotBlank()) {
            val clubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.accentColor
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(entry.club, centerX, y + 14f, clubPaint)
            y += 26f
        } else {
            y += 6f
        }

        // 8. Result & Format Summary Box
        val summaryStr = buildString {
            append(entry.formatLabel)
            append(" · ")
            append(entry.modeLabel)
            if (entry.recordSummary.isNotBlank()) {
                append("\n")
                append(entry.recordSummary)
            }
        }
        val summaryTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.mutedColor
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val summaryLayout = StaticLayout.Builder.obtain(summaryStr, 0, summaryStr.length, summaryTp, usableW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(4f, 1.0f)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(padX, y)
        summaryLayout.draw(canvas)
        canvas.restore()

        // 9. Clean Date & Location Footer safely inside bottom boundary
        val footerParts = listOfNotNull(
            entry.location.takeIf { it.isNotBlank() },
            entry.date.takeIf { it.isNotBlank() }
        )
        if (footerParts.isNotEmpty()) {
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.primaryInkColor
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val footerY = (contentBottom - 10f).coerceAtLeast(y + summaryLayout.height + 24f)
            canvas.drawText(footerParts.joinToString("  ·  "), centerX, footerY, footerPaint)
        }
    }

    fun buildSafePdfFileName(tournamentName: String, rankFilter: Int? = null): String {
        val cleaned = tournamentName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "turnier" }
        val suffix = if (rankFilter != null) "_platz_$rankFilter" else "_plaetze_1_bis_3"
        return "urkunde_${cleaned}$suffix.pdf"
    }

    fun writePdfToCacheFile(
        context: Context,
        entries: List<CertificateEntry>,
        style: CertificateStyle,
        fileName: String
    ): File {
        val dir = File(context.cacheDir, "certificates").apply { mkdirs() }
        val target = File(dir, fileName)
        val bytes = generateCertificatesPdfBytes(context, entries, style)
        target.writeBytes(bytes)
        return target
    }

    fun sharePdfViaFileProvider(
        context: Context,
        pdfFile: File,
        shareTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, shareTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
