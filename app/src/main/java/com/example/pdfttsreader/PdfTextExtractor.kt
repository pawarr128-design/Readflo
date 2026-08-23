package com.example.pdfttsreader

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import java.awt.Rectangle
import java.io.InputStream

/**
 * Extracts text from a PDF, letting the caller crop out a top margin (header)
 * and bottom margin (footer) as a fraction of page height (0.0 - 0.4 each).
 *
 * Returns one combined string plus a list of per-sentence character offsets
 * used for play/pause/seek and percent-read tracking.
 */
object PdfTextExtractor {

    data class ExtractResult(
        val fullText: String,
        val sentences: List<String>,
        // cumulative character offset where each sentence starts, for progress %
        val sentenceStartOffsets: List<Int>
    )

    fun extract(
        context: Context,
        uri: Uri,
        topMarginFraction: Float = 0.08f,
        bottomMarginFraction: Float = 0.08f
    ): ExtractResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open PDF")

        val document = PDDocument.load(inputStream)
        val sb = StringBuilder()

        try {
            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                // Region excluding the top header band and bottom footer band.
                // PDFBox area coordinates start from the TOP-LEFT of the page.
                val topCut = pageHeight * topMarginFraction
                val bottomCut = pageHeight * bottomMarginFraction
                val bodyHeight = (pageHeight - topCut - bottomCut).coerceAtLeast(1f)

                val region = Rectangle(
                    0,
                    topCut.toInt(),
                    pageWidth.toInt(),
                    bodyHeight.toInt()
                )

                val stripper = PDFTextStripperByArea()
                stripper.addRegion("body", region)
                stripper.extractRegions(page)
                val regionText = stripper.getTextForRegion("body")

                sb.append(regionText)
                sb.append("\n\n")
            }
        } finally {
            document.close()
            inputStream.close()
        }

        val fullText = sb.toString()
        val sentences = splitIntoSentences(fullText)
        val offsets = computeOffsets(fullText, sentences)

        return ExtractResult(fullText, sentences, offsets)
    }

    /** Simple, dependency-free sentence splitter good enough for TTS chunking. */
    private fun splitIntoSentences(text: String): List<String> {
        val regex = Regex("(?<=[.!?])\\s+")
        return text.split(regex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun computeOffsets(fullText: String, sentences: List<String>): List<Int> {
        val offsets = mutableListOf<Int>()
        var searchFrom = 0
        for (s in sentences) {
            val idx = fullText.indexOf(s, searchFrom).let { if (it == -1) searchFrom else it }
            offsets.add(idx)
            searchFrom = idx + s.length
        }
        return offsets
    }
}
