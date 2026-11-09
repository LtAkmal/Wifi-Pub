package com.example

import com.example.data.CorrectiveMaintenanceEntity
import com.example.data.PmReportEntity
import com.example.data.TodayReportConfig
import com.example.util.ReportFormatter
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun dailyPmReportFormat_matchesUserTemplate() {
        val text = ReportFormatter.formatDailyPmReport(
            location = "Area R. Tunggu samping mamma roti loby T3 Inter, Terminal 3",
            equipmentName = "Access Point Wifi Publik",
            uploadMbps = "44.6",
            downloadMbps = "66.7",
            displayDateEn = "17 October 2026",
            onDutyText = "Liberry (0895-0961-6714) \nAkmal (0851-7444-2035)"
        )
        assertTrue(text.contains("REPORT DAILY ROUTINE\nPREVENTIVE MAINTENANCE"))
        assertTrue(text.contains("Kecepatan : Up 44.6Mbps / Down 66.7Mbps"))
        assertTrue(text.contains("Lokasi : Area R. Tunggu samping mamma roti loby T3 Inter, Terminal 3"))
        assertTrue(text.contains("Tanggal : 17 October 2026"))
    }

    @Test
    fun todayReportFormat_matchesIctOperationDailyReportCgk() {
        val pmList = listOf(
            PmReportEntity(
                id = 1,
                dateKey = "2026-10-17",
                displayDateEn = "17 October 2026",
                location = "Plaza Kedatangan Gate 4 Tengah, Terminal 3",
                uploadMbps = "57.1",
                downloadMbps = "65.2",
                onDutyText = ""
            ),
            PmReportEntity(
                id = 2,
                dateKey = "2026-10-17",
                displayDateEn = "17 October 2026",
                location = "Plaza Kedatangan Gate 4 Sebelah Kiri, Terminal 3",
                uploadMbps = "41.3",
                downloadMbps = "40.7",
                onDutyText = ""
            )
        )
        val cmList = listOf(
            CorrectiveMaintenanceEntity(
                id = 1,
                dateKey = "2026-10-17",
                location = "Plaza Kedatangan Gate 1 Sebelah kanan, Terminal 3"
            )
        )
        val report = ReportFormatter.formatTodayReport(
            hariTanggalId = "Sabtu, 17 Oktober 2026",
            config = TodayReportConfig(),
            pmList = pmList,
            cmList = cmList
        )
        assertTrue(report.contains("*ICT OPERATION DAILY REPORT (CGK)."))
        assertTrue(report.contains(". . Speedtest Wifi :*"))
        assertTrue(report.contains("- [Pengujian & Pengecekan Speed Bandwitch Wifi Publik area Plaza Kedatangan Gate 4 Tengah, Terminal 3 - Up 57.1Mbps / Down 65.2Mbps]"))
        assertTrue(report.contains("*Corrective Maintenance: -"))
        assertTrue(report.contains("Lokasi : Plaza Kedatangan Gate 1 Sebelah kanan, Terminal 3"))
    }
}
