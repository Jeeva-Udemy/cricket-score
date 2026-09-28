package com.example.cricketscorer.ui.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.cricketscorer.stats.MatchDashboard
import java.util.Locale

/**
 * Draws a [MatchDashboard] as ONE tall PNG-ready image (1080px wide — WhatsApp's native
 * width) so the whole match can be shared as a single picture, instead of stitching
 * several phone screenshots together.
 *
 * Drawn directly with android.graphics rather than screenshotting Compose, so the image is
 * identical on every phone (no cut-off rows, no dependency on screen size / dark mode /
 * font scale) and long scorecards are never clipped by the screen height.
 *
 * Every draw helper takes a nullable Canvas: a first pass with `null` only measures the
 * total height, the second pass draws into a bitmap of exactly that height.
 */
class DashboardImageRenderer(private val d: MatchDashboard) {

    private companion object {
        const val W = 1080
        const val PAD = 40f
        const val CARD_PAD = 28f

        val BG = Color.parseColor("#EEF3EF")
        val HEADER = Color.parseColor("#1B5E20")
        val HEADER_2 = Color.parseColor("#2E7D32")
        val CARD = Color.WHITE
        val TEXT = Color.parseColor("#1C1B1F")
        val MUTED = Color.parseColor("#5F6368")
        val DIVIDER = Color.parseColor("#E3E7E4")
        val ACCENT = Color.parseColor("#FFB300")
        val ACCENT_BG = Color.parseColor("#FFF4D6")
        val OUT = Color.parseColor("#C62828")
        val LIGHT_ON_HEADER = Color.parseColor("#C8E6C9")
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            textAlign = align
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    fun render(): Bitmap {
        val height = layout(null).toInt()
        val bitmap = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)
        layout(canvas)
        return bitmap
    }

    /** Draws (or, with a null canvas, just measures) the whole image; returns total height. */
    private fun layout(c: Canvas?): Float {
        var y = header(c)
        y = resultBanner(c, y + 24f)
        y = scoreSummary(c, y + 24f)
        d.playerOfTheMatch?.let { y = playerOfTheMatch(c, y + 24f) }
        if (d.topBatters.isNotEmpty() || d.topBowlers.isNotEmpty()) y = highlights(c, y + 24f)
        d.innings.forEach { y = inningsCard(c, it, y + 24f) }
        y = footer(c, y + 30f)
        return y
    }

    // ---------------------------------------------------------------- sections

    private fun header(c: Canvas?): Float {
        val top = 0f
        var y = top + 64f
        val small = paint(26f, LIGHT_ON_HEADER, bold = true)
        val title = paint(56f, Color.WHITE, bold = true)
        val sub = paint(30f, Color.WHITE)
        val toss = paint(27f, LIGHT_ON_HEADER)
        val titleLines = wrap(d.title, title, W - 2 * PAD, maxLines = 2)
        val height = 64f + 72f * titleLines.size + 50f + 44f + 40f
        c?.let {
            fill.color = HEADER
            it.drawRect(0f, top, W.toFloat(), top + height, fill)
            fill.color = HEADER_2
            it.drawCircle(W - 90f, top + 40f, 170f, fill)
        }
        c?.drawText("WICKT  •  MATCH SUMMARY", PAD, y, small)
        titleLines.forEach { line ->
            y += 72f
            c?.drawText(line, PAD, y, title)
        }
        y += 50f
        c?.drawText(ellipsize(d.subtitle, sub, W - 2 * PAD), PAD, y, sub)
        y += 44f
        c?.drawText(ellipsize(d.tossLine, toss, W - 2 * PAD), PAD, y, toss)
        return top + height
    }

    private fun resultBanner(c: Canvas?, top: Float): Float {
        val p = paint(36f, TEXT, bold = true)
        val lines = wrap(d.result, p, W - 2 * PAD - 2 * CARD_PAD, maxLines = 3)
        val height = CARD_PAD * 2 + 46f * lines.size
        c?.let {
            fill.color = if (d.isCompleted) ACCENT_BG else CARD
            it.drawRoundRect(RectF(PAD, top, W - PAD, top + height), 24f, 24f, fill)
            fill.color = ACCENT
            it.drawRoundRect(RectF(PAD, top, PAD + 12f, top + height), 6f, 6f, fill)
        }
        var y = top + CARD_PAD + 36f
        lines.forEach { line ->
            c?.drawText(line, PAD + CARD_PAD + 8f, y, p)
            y += 46f
        }
        return top + height
    }

    private fun scoreSummary(c: Canvas?, top: Float): Float {
        if (d.innings.isEmpty()) return top
        val rowH = 108f
        val height = CARD_PAD * 2 + rowH * d.innings.size - 20f
        card(c, top, height)
        val team = paint(34f, TEXT, bold = true)
        val label = paint(24f, MUTED)
        val score = paint(52f, TEXT, bold = true, align = Paint.Align.RIGHT)
        val overs = paint(24f, MUTED, align = Paint.Align.RIGHT)
        var y = top + CARD_PAD
        d.innings.forEachIndexed { i, inn ->
            if (i > 0) divider(c, y - 14f)
            val left = PAD + CARD_PAD
            val right = W - PAD - CARD_PAD
            c?.drawText(ellipsize(inn.battingTeam, team, 560f), left, y + 42f, team)
            val tag = when {
                inn.isSuperOver -> "Super Over"
                inn.isLive -> "Batting now"
                else -> if (inn.label.startsWith("1st")) "1st innings" else "2nd innings"
            } + (inn.target?.let { "  •  Target $it" } ?: "")
            c?.drawText(tag, left, y + 78f, label)
            c?.drawText(inn.score, right, y + 50f, score)
            c?.drawText("(${inn.overs}/${inn.oversLimit} ov)  RR ${fmt(inn.runRate)}", right, y + 82f, overs)
            y += rowH
        }
        return top + height
    }

    private fun playerOfTheMatch(c: Canvas?, top: Float): Float {
        val award = d.playerOfTheMatch ?: return top
        val height = 150f
        c?.let {
            fill.color = HEADER
            it.drawRoundRect(RectF(PAD, top, W - PAD, top + height), 24f, 24f, fill)
            fill.color = ACCENT
            it.drawCircle(PAD + 70f, top + height / 2, 42f, fill)
        }
        c?.drawText("★", PAD + 70f, top + height / 2 + 16f, paint(46f, HEADER, bold = true, align = Paint.Align.CENTER))
        val left = PAD + 136f
        c?.drawText("PLAYER OF THE MATCH", left, top + 48f, paint(24f, ACCENT, bold = true))
        c?.drawText(ellipsize(award.playerName, paint(42f, Color.WHITE, bold = true), W - PAD - left - 20f), left, top + 96f, paint(42f, Color.WHITE, bold = true))
        c?.drawText(awardFigures(award), left, top + 132f, paint(26f, LIGHT_ON_HEADER))
        return top + height
    }

    private fun highlights(c: Canvas?, top: Float): Float {
        val rows = maxOf(d.topBatters.size, d.topBowlers.size)
        val height = CARD_PAD * 2 + 48f + rows * 80f
        card(c, top, height)
        val colW = (W - 2 * PAD - 2 * CARD_PAD - 30f) / 2
        val leftX = PAD + CARD_PAD
        val rightX = leftX + colW + 30f
        performerColumn(c, "TOP BATTERS", d.topBatters, leftX, top + CARD_PAD, colW)
        performerColumn(c, "TOP BOWLERS", d.topBowlers, rightX, top + CARD_PAD, colW)
        c?.let {
            fill.color = DIVIDER
            it.drawRect(leftX + colW + 14f, top + CARD_PAD, leftX + colW + 16f, top + height - CARD_PAD, fill)
        }
        return top + height
    }

    private fun performerColumn(c: Canvas?, title: String, list: List<MatchDashboard.Performer>, x: Float, top: Float, w: Float) {
        c?.drawText(title, x, top + 28f, paint(24f, HEADER_2, bold = true))
        var y = top + 48f
        val name = paint(29f, TEXT, bold = true)
        val figure = paint(32f, TEXT, bold = true, align = Paint.Align.RIGHT)
        val detail = paint(22f, MUTED)
        list.forEach { p ->
            val figW = figure.measureText(p.figure)
            c?.drawText(ellipsize(p.name, name, w - figW - 16f), x, y + 36f, name)
            c?.drawText(p.figure, x + w, y + 38f, figure)
            c?.drawText(ellipsize("${p.team} • ${p.detail}", detail, w), x, y + 68f, detail)
            y += 80f
        }
    }

    private fun inningsCard(c: Canvas?, inn: MatchDashboard.InningsSummary, top: Float): Float {
        val left = PAD + CARD_PAD
        val right = W - PAD - CARD_PAD
        val headerH = 84f
        val batRowH = 70f
        val bowlRowH = 54f
        val tableHeadH = 48f
        val height = headerH + 20f +
            tableHeadH + batRowH * inn.batting.size.coerceAtLeast(1) +
            96f + // extras + total
            (if (inn.bowling.isNotEmpty()) 24f + tableHeadH + bowlRowH * inn.bowling.size else 0f) +
            CARD_PAD
        card(c, top, height)

        // Header strip
        c?.let {
            fill.color = HEADER_2
            it.drawRoundRect(RectF(PAD, top, W - PAD, top + headerH), 24f, 24f, fill)
            it.drawRect(PAD, top + headerH - 24f, W - PAD, top + headerH, fill)
        }
        val scoreText = "${inn.score}  (${inn.overs} ov)"
        val scoreP = paint(32f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        val labelP = paint(30f, Color.WHITE, bold = true)
        c?.drawText(ellipsize(inn.label, labelP, right - left - scoreP.measureText(scoreText) - 24f), left, top + 54f, labelP)
        c?.drawText(scoreText, right, top + 54f, scoreP)

        var y = top + headerH + 20f

        // Batting table
        val cols = floatArrayOf(right - 330f, right - 250f, right - 170f, right - 100f, right) // R B 4s 6s SR (right edges)
        val head = paint(24f, MUTED, bold = true)
        val headR = paint(24f, MUTED, bold = true, align = Paint.Align.RIGHT)
        c?.drawText("BATTER", left, y + 30f, head)
        listOf("R", "B", "4s", "6s", "SR").forEachIndexed { i, h -> c?.drawText(h, cols[i], y + 30f, headR) }
        y += tableHeadH
        divider(c, y - 6f)

        val nameP = paint(29f, TEXT, bold = true)
        val numP = paint(29f, TEXT, align = Paint.Align.RIGHT)
        val runsP = paint(29f, TEXT, bold = true, align = Paint.Align.RIGHT)
        if (inn.batting.isEmpty()) {
            c?.drawText("No batting yet", left, y + 40f, paint(26f, MUTED))
            y += batRowH
        }
        inn.batting.forEach { b ->
            val nameMax = cols[0] - 60f - left
            c?.drawText(ellipsize(b.name + if (!b.isOut) "*" else "", nameP, nameMax), left, y + 32f, nameP)
            c?.drawText(ellipsize(b.dismissal, paint(22f, if (b.isOut) OUT else MUTED), nameMax), left, y + 60f,
                paint(22f, if (b.isOut) OUT else MUTED))
            c?.drawText("${b.runs}", cols[0], y + 36f, runsP)
            c?.drawText("${b.balls}", cols[1], y + 36f, numP)
            c?.drawText("${b.fours}", cols[2], y + 36f, numP)
            c?.drawText("${b.sixes}", cols[3], y + 36f, numP)
            c?.drawText(fmt(b.strikeRate), cols[4], y + 36f, numP)
            y += batRowH
        }
        divider(c, y + 2f)
        val muted = paint(25f, MUTED)
        val bold = paint(28f, TEXT, bold = true)
        val boldR = paint(28f, TEXT, bold = true, align = Paint.Align.RIGHT)
        c?.drawText("Extras ${inn.extras}  (${inn.extrasDetail})", left, y + 38f, muted)
        c?.drawText("TOTAL", left, y + 80f, bold)
        c?.drawText("${inn.score}  (${inn.overs} ov, RR ${fmt(inn.runRate)})", right, y + 80f, boldR)
        y += 96f

        // Bowling table
        if (inn.bowling.isNotEmpty()) {
            y += 24f
            val bcols = floatArrayOf(right - 400f, right - 320f, right - 240f, right - 160f, right) // O M R W Econ
            c?.drawText("BOWLER", left, y + 30f, head)
            listOf("O", "M", "R", "W", "ECON").forEachIndexed { i, h -> c?.drawText(h, bcols[i], y + 30f, headR) }
            y += tableHeadH
            divider(c, y - 6f)
            val bname = paint(28f, TEXT, bold = true)
            inn.bowling.forEach { bw ->
                c?.drawText(ellipsize(bw.name, bname, bcols[0] - 80f - left), left, y + 36f, bname)
                c?.drawText(bw.overs, bcols[0], y + 36f, numP)
                c?.drawText("${bw.maidens}", bcols[1], y + 36f, numP)
                c?.drawText("${bw.runs}", bcols[2], y + 36f, numP)
                c?.drawText("${bw.wickets}", bcols[3], y + 36f, runsP)
                c?.drawText(fmt(bw.economy), bcols[4], y + 36f, numP)
                y += bowlRowH
            }
        }
        return top + height
    }

    private fun footer(c: Canvas?, top: Float): Float {
        c?.drawText(
            "Scored with Wickt: The Cricket Scorer",
            W / 2f, top + 30f, paint(24f, MUTED, align = Paint.Align.CENTER)
        )
        return top + 70f
    }

    // ---------------------------------------------------------------- helpers

    private fun card(c: Canvas?, top: Float, height: Float) {
        c ?: return
        fill.color = Color.parseColor("#14000000")
        c.drawRoundRect(RectF(PAD, top + 4f, W - PAD, top + height + 4f), 24f, 24f, fill)
        fill.color = CARD
        c.drawRoundRect(RectF(PAD, top, W - PAD, top + height), 24f, 24f, fill)
    }

    private fun divider(c: Canvas?, y: Float) {
        c ?: return
        fill.color = DIVIDER
        c.drawRect(PAD + CARD_PAD, y, W - PAD - CARD_PAD, y + 2f, fill)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun awardFigures(a: com.example.cricketscorer.stats.PlayerStatsCalculator.PlayerAward): String {
        val bat = if (a.ballsFaced > 0) "${a.runs} (${a.ballsFaced})" else null
        val bowl = if (a.ballsBowled > 0) "${a.wickets}/${a.runsConceded}" else null
        return listOfNotNull(bat, bowl).joinToString("  &  ").ifBlank { "${a.runs} runs" }
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && p.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun wrap(text: String, p: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        if (lines.isEmpty()) lines += ""
        if (lines.size > maxLines) {
            val kept = lines.take(maxLines).toMutableList()
            kept[maxLines - 1] = ellipsize(lines.drop(maxLines - 1).joinToString(" "), p, maxWidth)
            return kept
        }
        return lines.map { ellipsize(it, p, maxWidth) }
    }
}
