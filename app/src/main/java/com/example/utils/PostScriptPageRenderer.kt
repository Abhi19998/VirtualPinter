package com.example.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.util.Stack
import kotlin.math.max

/**
 * High-quality PostScript (.ps) visual page renderer and parser for Android.
 * Renders PostScript vector graphics, text, paths, and bounding boxes onto Bitmap pages,
 * allowing users to visually preview and read PostScript documents directly inside the app.
 */
object PostScriptPageRenderer {

    data class RenderedPsDocument(
        val pages: List<Bitmap>,
        val metadata: Map<String, String>,
        val extractedText: List<String>,
        val totalLines: Int,
        val totalBytes: Long
    )

    private data class GraphicsState(
        var currentX: Float = 0f,
        var currentY: Float = 0f,
        var strokeColor: Int = Color.BLACK,
        var fillColor: Int = Color.BLACK,
        var strokeWidth: Float = 1f,
        var fontSize: Float = 12f,
        var typeface: Typeface = Typeface.DEFAULT,
        var fontName: String = "Helvetica"
    )

    /**
     * Parses and renders a PostScript file into visual Bitmap pages.
     */
    fun renderPostScriptFile(file: File, targetWidth: Int = 1240, targetHeight: Int = 1754): RenderedPsDocument {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("PostScript file does not exist or is empty")
        }

        val allLines = file.readLines()
        val metadata = mutableMapOf<String, String>()
        val extractedText = mutableListOf<String>()

        // 1. Parse DSC Headers
        var declaredPages = 1
        var llx = 0f
        var lly = 0f
        var urx = 612f // Default 8.5 x 11 inches at 72 dpi
        var ury = 792f

        for (line in allLines.take(100)) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("%%Title:", ignoreCase = true) ->
                    metadata["Title"] = trimmed.substringAfter(":").trim()
                trimmed.startsWith("%%Creator:", ignoreCase = true) ->
                    metadata["Creator"] = trimmed.substringAfter(":").trim()
                trimmed.startsWith("%%CreationDate:", ignoreCase = true) ->
                    metadata["CreationDate"] = trimmed.substringAfter(":").trim()
                trimmed.startsWith("%%For:", ignoreCase = true) ->
                    metadata["User / Author"] = trimmed.substringAfter(":").trim()
                trimmed.startsWith("%%Orientation:", ignoreCase = true) ->
                    metadata["Orientation"] = trimmed.substringAfter(":").trim()
                trimmed.startsWith("%%LanguageLevel:", ignoreCase = true) ->
                    metadata["Language Level"] = trimmed.substringAfter(":").trim()
                trimmed.startsWith("%%Pages:", ignoreCase = true) -> {
                    val pStr = trimmed.substringAfter(":").trim()
                    metadata["Pages"] = pStr
                    pStr.toIntOrNull()?.let { if (it > 0) declaredPages = it }
                }
                trimmed.startsWith("%%BoundingBox:", ignoreCase = true) -> {
                    metadata["BoundingBox"] = trimmed.substringAfter(":").trim()
                    val parts = trimmed.substringAfter(":").trim().split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        llx = parts[0].toFloatOrNull() ?: 0f
                        lly = parts[1].toFloatOrNull() ?: 0f
                        urx = parts[2].toFloatOrNull() ?: 612f
                        ury = parts[3].toFloatOrNull() ?: 792f
                    }
                }
            }
        }

        val pageWidthPt = max(urx - llx, 100f)
        val pageHeightPt = max(ury - lly, 100f)

        // 2. Segment lines into pages based on %%Page: or showpage
        val pageLineChunks = mutableListOf<MutableList<String>>()
        var currentPageChunk = mutableListOf<String>()

        for (line in allLines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("%%Page:", ignoreCase = true) && currentPageChunk.isNotEmpty()) {
                pageLineChunks.add(currentPageChunk)
                currentPageChunk = mutableListOf()
            }
            currentPageChunk.add(line)
            if (trimmed == "showpage" || trimmed.endsWith(" showpage")) {
                pageLineChunks.add(currentPageChunk)
                currentPageChunk = mutableListOf()
            }
        }
        if (currentPageChunk.isNotEmpty()) {
            pageLineChunks.add(currentPageChunk)
        }

        // If no page breaks were found, treat entire file as single page
        if (pageLineChunks.isEmpty()) {
            pageLineChunks.add(allLines.toMutableList())
        }

        val renderedBitmaps = mutableListOf<Bitmap>()

        // 3. Render each page
        for ((pageIndex, pageLines) in pageLineChunks.withIndex()) {
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Fill page background white
            canvas.drawColor(Color.WHITE)

            // Draw subtle paper border & header indicator
            val borderPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), borderPaint)

            // Coordinate Transformation: PostScript (0,0) is bottom-left, Canvas (0,0) is top-left
            val scaleX = targetWidth / pageWidthPt
            val scaleY = targetHeight / pageHeightPt

            // We do manual coordinate mapping for precision:
            // xCanvas = (xPs - llx) * scaleX
            // yCanvas = (pageHeightPt - (yPs - lly)) * scaleY

            fun mapX(xPs: Float): Float = (xPs - llx) * scaleX
            fun mapY(yPs: Float): Float = (pageHeightPt - (yPs - lly)) * scaleY
            fun mapW(wPs: Float): Float = wPs * scaleX
            fun mapH(hPs: Float): Float = hPs * scaleY

            var state = GraphicsState()
            val stateStack = Stack<GraphicsState>()
            val currentPath = Path()
            var pathStarted = false

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = state.fillColor
                textSize = state.fontSize * scaleY
                typeface = state.typeface
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = state.strokeColor
                style = Paint.Style.STROKE
                strokeWidth = max(state.strokeWidth * scaleX, 1f)
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = state.fillColor
                style = Paint.Style.FILL
            }

            // Extract strings & text commands
            val stringRegex = Regex("\\((.*?)\\)")
            var hasDrawnAnything = false

            for (line in pageLines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("%") && !trimmed.startsWith("%%")) continue

                // Check for string extraction
                val matches = stringRegex.findAll(line)
                for (match in matches) {
                    val rawStr = match.groupValues[1]
                    if (rawStr.isNotBlank()) {
                        extractedText.add(rawStr)
                    }
                }

                // Parse tokens
                val tokens = tokenizePsLine(line)
                var i = 0
                while (i < tokens.size) {
                    val token = tokens[i]
                    when (token) {
                        "gsave" -> {
                            stateStack.push(state.copy())
                            i++
                        }
                        "grestore" -> {
                            if (stateStack.isNotEmpty()) {
                                state = stateStack.pop()
                                textPaint.color = state.fillColor
                                textPaint.textSize = state.fontSize * scaleY
                                textPaint.typeface = state.typeface
                                strokePaint.color = state.strokeColor
                                strokePaint.strokeWidth = max(state.strokeWidth * scaleX, 1f)
                                fillPaint.color = state.fillColor
                            }
                            i++
                        }
                        "setgray" -> {
                            if (i >= 1) {
                                val g = tokens[i - 1].toFloatOrNull() ?: 0f
                                val grayVal = (g * 255).toInt().coerceIn(0, 255)
                                val col = Color.rgb(grayVal, grayVal, grayVal)
                                state.fillColor = col
                                state.strokeColor = col
                                textPaint.color = col
                                strokePaint.color = col
                                fillPaint.color = col
                            }
                            i++
                        }
                        "setrgbcolor" -> {
                            if (i >= 3) {
                                val r = (tokens[i - 3].toFloatOrNull() ?: 0f) * 255
                                val g = (tokens[i - 2].toFloatOrNull() ?: 0f) * 255
                                val b = (tokens[i - 1].toFloatOrNull() ?: 0f) * 255
                                val col = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
                                state.fillColor = col
                                state.strokeColor = col
                                textPaint.color = col
                                strokePaint.color = col
                                fillPaint.color = col
                            }
                            i++
                        }
                        "setlinewidth" -> {
                            if (i >= 1) {
                                val lw = tokens[i - 1].toFloatOrNull() ?: 1f
                                state.strokeWidth = lw
                                strokePaint.strokeWidth = max(lw * scaleX, 1f)
                            }
                            i++
                        }
                        "scalefont" -> {
                            if (i >= 1) {
                                val size = tokens[i - 1].toFloatOrNull() ?: 12f
                                state.fontSize = size
                                textPaint.textSize = max(size * scaleY, 10f)
                            }
                            i++
                        }
                        "findfont" -> {
                            if (i >= 1) {
                                val fName = tokens[i - 1].removePrefix("/")
                                state.fontName = fName
                                state.typeface = when {
                                    fName.contains("Bold", ignoreCase = true) -> Typeface.DEFAULT_BOLD
                                    fName.contains("Courier", ignoreCase = true) -> Typeface.MONOSPACE
                                    fName.contains("Times", ignoreCase = true) -> Typeface.SERIF
                                    else -> Typeface.DEFAULT
                                }
                                textPaint.typeface = state.typeface
                            }
                            i++
                        }
                        "moveto" -> {
                            if (i >= 2) {
                                val x = tokens[i - 2].toFloatOrNull() ?: 0f
                                val y = tokens[i - 1].toFloatOrNull() ?: 0f
                                state.currentX = x
                                state.currentY = y
                                currentPath.moveTo(mapX(x), mapY(y))
                                pathStarted = true
                            }
                            i++
                        }
                        "lineto" -> {
                            if (i >= 2) {
                                val x = tokens[i - 2].toFloatOrNull() ?: 0f
                                val y = tokens[i - 1].toFloatOrNull() ?: 0f
                                state.currentX = x
                                state.currentY = y
                                if (!pathStarted) {
                                    currentPath.moveTo(mapX(x), mapY(y))
                                    pathStarted = true
                                } else {
                                    currentPath.lineTo(mapX(x), mapY(y))
                                }
                                hasDrawnAnything = true
                            }
                            i++
                        }
                        "rmoveto" -> {
                            if (i >= 2) {
                                val dx = tokens[i - 2].toFloatOrNull() ?: 0f
                                val dy = tokens[i - 1].toFloatOrNull() ?: 0f
                                state.currentX += dx
                                state.currentY += dy
                                currentPath.rMoveTo(dx * scaleX, -dy * scaleY)
                            }
                            i++
                        }
                        "rlineto" -> {
                            if (i >= 2) {
                                val dx = tokens[i - 2].toFloatOrNull() ?: 0f
                                val dy = tokens[i - 1].toFloatOrNull() ?: 0f
                                state.currentX += dx
                                state.currentY += dy
                                currentPath.rLineTo(dx * scaleX, -dy * scaleY)
                                hasDrawnAnything = true
                            }
                            i++
                        }
                        "newpath" -> {
                            currentPath.reset()
                            pathStarted = false
                            i++
                        }
                        "closepath" -> {
                            currentPath.close()
                            i++
                        }
                        "stroke" -> {
                            canvas.drawPath(currentPath, strokePaint)
                            currentPath.reset()
                            pathStarted = false
                            hasDrawnAnything = true
                            i++
                        }
                        "fill" -> {
                            canvas.drawPath(currentPath, fillPaint)
                            currentPath.reset()
                            pathStarted = false
                            hasDrawnAnything = true
                            i++
                        }
                        "rectfill" -> {
                            if (i >= 4) {
                                val rx = tokens[i - 4].toFloatOrNull() ?: 0f
                                val ry = tokens[i - 3].toFloatOrNull() ?: 0f
                                val rw = tokens[i - 2].toFloatOrNull() ?: 0f
                                val rh = tokens[i - 1].toFloatOrNull() ?: 0f
                                val cLeft = mapX(rx)
                                val cTop = mapY(ry + rh)
                                val cRight = cLeft + mapW(rw)
                                val cBottom = cTop + mapH(rh)
                                canvas.drawRect(RectF(cLeft, cTop, cRight, cBottom), fillPaint)
                                hasDrawnAnything = true
                            }
                            i++
                        }
                        "rectstroke" -> {
                            if (i >= 4) {
                                val rx = tokens[i - 4].toFloatOrNull() ?: 0f
                                val ry = tokens[i - 3].toFloatOrNull() ?: 0f
                                val rw = tokens[i - 2].toFloatOrNull() ?: 0f
                                val rh = tokens[i - 1].toFloatOrNull() ?: 0f
                                val cLeft = mapX(rx)
                                val cTop = mapY(ry + rh)
                                val cRight = cLeft + mapW(rw)
                                val cBottom = cTop + mapH(rh)
                                canvas.drawRect(RectF(cLeft, cTop, cRight, cBottom), strokePaint)
                                hasDrawnAnything = true
                            }
                            i++
                        }
                        "show" -> {
                            if (i >= 1) {
                                val rawText = unescapePsString(tokens[i - 1])
                                canvas.drawText(rawText, mapX(state.currentX), mapY(state.currentY), textPaint)
                                val textWidthPt = textPaint.measureText(rawText) / scaleX
                                state.currentX += textWidthPt
                                hasDrawnAnything = true
                            }
                            i++
                        }
                        else -> {
                            i++
                        }
                    }
                }
            }

            // If the PostScript used binary drivers or high-level macros without direct primitives,
            // render a polished formatted layout with extracted strings and metadata so it's readable
            if (!hasDrawnAnything && extractedText.isNotEmpty()) {
                renderExtractedTextFallback(canvas, targetWidth, targetHeight, extractedText, metadata, pageIndex + 1)
            } else if (!hasDrawnAnything) {
                renderDefaultDocumentLayout(canvas, targetWidth, targetHeight, metadata, pageIndex + 1)
            }

            renderedBitmaps.add(bitmap)
        }

        if (renderedBitmaps.isEmpty()) {
            val emptyBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val c = Canvas(emptyBitmap)
            c.drawColor(Color.WHITE)
            renderDefaultDocumentLayout(c, targetWidth, targetHeight, metadata, 1)
            renderedBitmaps.add(emptyBitmap)
        }

        return RenderedPsDocument(
            pages = renderedBitmaps,
            metadata = metadata,
            extractedText = extractedText.distinct(),
            totalLines = allLines.size,
            totalBytes = file.length()
        )
    }

    private fun renderExtractedTextFallback(
        canvas: Canvas,
        width: Int,
        height: Int,
        texts: List<String>,
        metadata: Map<String, String>,
        pageNumber: Int
    ) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 20f
            typeface = Typeface.DEFAULT
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 22f
            typeface = Typeface.DEFAULT
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 2f
        }

        var y = 80f
        val margin = 70f
        val maxW = width - (margin * 2)

        // Document Title / Header
        val title = metadata["Title"] ?: "PostScript Print Document"
        canvas.drawText(title, margin, y, titlePaint)
        y += 35f

        val creator = metadata["Creator"] ?: metadata["User / Author"] ?: "Virtual Printer Document"
        canvas.drawText("Generated by $creator  •  Page $pageNumber", margin, y, subPaint)
        y += 25f
        canvas.drawLine(margin, y, width - margin, y, dividerPaint)
        y += 45f

        // Render extracted text lines
        for (text in texts.take(60)) {
            val cleanText = text.trim()
            if (cleanText.isEmpty()) continue

            // Auto word wrap
            val words = cleanText.split(" ")
            var lineBuf = ""
            for (word in words) {
                val testLine = if (lineBuf.isEmpty()) word else "$lineBuf $word"
                if (bodyPaint.measureText(testLine) > maxW) {
                    canvas.drawText(lineBuf, margin, y, bodyPaint)
                    y += 32f
                    lineBuf = word
                    if (y > height - 80f) break
                } else {
                    lineBuf = testLine
                }
            }
            if (lineBuf.isNotEmpty()) {
                canvas.drawText(lineBuf, margin, y, bodyPaint)
                y += 32f
            }
            if (y > height - 80f) break
        }
    }

    private fun renderDefaultDocumentLayout(
        canvas: Canvas,
        width: Int,
        height: Int,
        metadata: Map<String, String>,
        pageNumber: Int
    ) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 22f
            typeface = Typeface.DEFAULT
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bannerPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        var y = 90f
        val margin = 70f

        canvas.drawText(metadata["Title"] ?: "PostScript Document", margin, y, titlePaint)
        y += 40f
        canvas.drawText("Page $pageNumber", margin, y, bodyPaint)
        y += 40f

        // Metadata box
        val boxRect = RectF(margin, y, width - margin, y + 260f)
        canvas.drawRoundRect(boxRect, 16f, 16f, bannerPaint)
        canvas.drawRoundRect(boxRect, 16f, 16f, borderPaint)

        var metaY = y + 45f
        for ((k, v) in metadata.entries.take(5)) {
            canvas.drawText("$k:", margin + 30f, metaY, labelPaint)
            canvas.drawText(v, margin + 220f, metaY, bodyPaint)
            metaY += 40f
        }
    }

    private fun tokenizePsLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inString = false
        var depth = 0

        for (ch in line) {
            when {
                ch == '(' -> {
                    if (!inString) inString = true
                    depth++
                    sb.append(ch)
                }
                ch == ')' -> {
                    depth--
                    sb.append(ch)
                    if (depth <= 0) {
                        inString = false
                        tokens.add(sb.toString())
                        sb.clear()
                        depth = 0
                    }
                }
                inString -> {
                    sb.append(ch)
                }
                ch.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> {
                    sb.append(ch)
                }
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun unescapePsString(raw: String): String {
        var s = raw
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
    }
}
