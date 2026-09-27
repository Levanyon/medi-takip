package com.levanyon.meditakip.data

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.levanyon.meditakip.domain.*
import java.io.OutputStream

/** A4, paginated at text-line boundaries, including arbitrarily long names and notes. */
object ReportPdf {
    const val FOOTER = "Bu rapor kullanıcının girdiği kayıtlardan oluşturulmuştur."
    fun write(report: DoctorReport, output: OutputStream) {
        val document = PdfDocument()
        var unfinished: PdfDocument.Page? = null
        try {
            var pageNumber = 0
            fun page(): PdfDocument.Page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create()).also {
                unfinished = it
                val paint = TextPaint().apply { textSize = 9f; color = Color.DKGRAY; isAntiAlias = true }
                it.canvas.drawText(FOOTER, 40f, 812f, paint)
                it.canvas.drawText("MediTakip · $pageNumber", 40f, 28f, paint)
            }
            var current = page()
            var y = 48f
            fun paragraph(text: String, title: Boolean = false) {
                val paint = TextPaint().apply {
                    textSize = if (title) 16f else 11f; color = Color.BLACK; isAntiAlias = true
                    typeface = if (title) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
                }
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 515)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setLineSpacing(3f, 1f).build()
                for (line in 0 until layout.lineCount) {
                    val top = layout.getLineTop(line)
                    val bottom = layout.getLineBottom(line)
                    val height = bottom - top
                    if (y + height > 786f) { document.finishPage(current); unfinished = null; current = page(); y = 48f }
                    current.canvas.save()
                    current.canvas.clipRect(40f, y, 555f, y + height)
                    current.canvas.translate(40f, y - top)
                    layout.draw(current.canvas)
                    current.canvas.restore()
                    y += height
                }
                y += if (title) 12f else 8f
            }
            paragraph("Doktor raporu", true)
            paragraph(report.person.name, true)
            paragraph("${TurkishFormat.date(report.from)} – ${TurkishFormat.date(report.through)}")
            paragraph("Kullanımdaki ilaçlar", true)
            if (report.medications.isEmpty()) paragraph("Kullanımda olan ilaç yok.")
            val weekdays = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
            report.medications.forEach { s ->
                paragraph("${s.name} · ${s.dose}")
                paragraph("Saatler: ${s.times}\nGünler: ${weekdays.filterIndexed { i, _ -> s.days and (1 shl i) != 0 }.joinToString(", ")}\nBaşlangıç: ${TurkishFormat.date(java.time.LocalDate.ofEpochDay(s.start))}\nBitiş: ${s.end?.let { TurkishFormat.date(java.time.LocalDate.ofEpochDay(it)) } ?: "Belirtilmedi"}")
            }
            paragraph("Son 30 günün kayıtları", true)
            if (report.records.isEmpty()) paragraph("Bu tarih aralığında kayıt yok.")
            report.records.forEach { o ->
                val state = if (o.skipped) "Atlandı" else "İçildi · ${TurkishFormat.completed(o.takenAt!!, o.date)}"
                paragraph("${TurkishFormat.date(o.date)} · Planlanan ${o.time}\n${o.schedule.name} · ${o.schedule.dose}\n$state${o.note?.takeIf { it.isNotBlank() }?.let { "\nNot: $it" } ?: ""}")
            }
            document.finishPage(current)
            unfinished = null
            document.writeTo(output)
        } finally {
            unfinished?.let { document.finishPage(it) }
            document.close()
        }
    }
}
