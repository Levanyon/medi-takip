package com.levanyon.meditakip.data

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.levanyon.meditakip.domain.*
import java.io.File
import java.time.LocalDate

/** Real Android PDF integration checks; Robolectric 4.14 has no PdfDocument native bridge.
 * Run only against the separate debug app on a safe test device, never the family release data. */
class ReportPdfDeviceTest : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            longTurkishTextProducesReadableMultiPagePdf()
            emptyReportIsStillAValidPdf()
            result.putString("stream", "2 PDF device checks passed. Inspect cache/report samples visually.")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("stream", "PDF device check failed: ${e.javaClass.simpleName}: ${e.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun longTurkishTextProducesReadableMultiPagePdf() {
        val today = LocalDate.of(2026, 9, 25)
        val person = PersonEntity("p", "Örnek Kişi — Şule Çığdem")
        val s = MedicationScheduleEntity("s", "m", "", "Uzun Türkçe ilaç adı çığ şişe ölçüsü ".repeat(12), "Kullanıcı tarafından girilen doz", "", "08:00", 127, today.toEpochDay(), null, true, today.toEpochDay())
        val records = (0L..29).map { Occurrence(s, "p", today.minusDays(it), 480, personName = person.name, status = LogStatus.SKIPPED, note = "Kullanıcının notu. ".repeat(30)) }
        val report = DoctorReport(person, today.minusDays(29), today, listOf(s), records)
        val file = File(targetContext.cacheDir, "doctor-report-sample.pdf")
        file.outputStream().use { ReportPdf.write(report, it) }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            check(renderer.pageCount > 2)
            renderer.openPage(renderer.pageCount - 1).use { check(it.width == 595); check(it.height == 842) }
        } finally { renderer.close() }
    }
    private fun emptyReportIsStillAValidPdf() {
        val today = LocalDate.of(2026, 9, 25)
        val file = File(targetContext.cacheDir, "doctor-report-empty.pdf")
        file.outputStream().use { ReportPdf.write(DoctorReport(PersonEntity("p", "Örnek"), today.minusDays(29), today, emptyList(), emptyList()), it) }
        val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        try { check(renderer.pageCount == 1) } finally { renderer.close() }
    }
}
