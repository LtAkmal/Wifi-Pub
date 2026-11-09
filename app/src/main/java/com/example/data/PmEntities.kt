package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pm_reports")
data class PmReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateKey: String, // e.g., "2026-10-31" for daily grouping
    val displayDateEn: String, // e.g., "31 October 2026" for Daily PM Report
    val location: String, // e.g., "Area R. Tunggu samping mamma roti loby T3 Inter, Terminal 3"
    val equipmentName: String = "Access Point Wifi Publik",
    val uploadMbps: String, // e.g., "44.6"
    val downloadMbps: String, // e.g., "66.7"
    val statusNetwork: Boolean = true,
    val statusDevice: Boolean = true,
    val statusSpeed: Boolean = true,
    val onDutyText: String,
    val collageFilePath: String? = null,
    val collageSizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val uploadClientRef: String? = null,
    val uploadStatus: String = "OFF", // "PENDING", "UPLOADING", "SENT", "FAILED", "OFF"
    val uploadedReportId: String? = null,
    val uploadError: String? = null,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsAccuracyM: Float? = null
)

@Entity(tableName = "corrective_maintenance")
data class CorrectiveMaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateKey: String, // e.g., "2026-10-31"
    val activityTitle: String = "Pembersihan & Pengujian Speed Access Point Wifi Publik",
    val location: String,
    val equipmentName: String = "Access Point Wifi Publik",
    val kendala: String = "Temuan AP Tampak Tidak Bersih",
    val issue: String = "No Issue",
    val actionLines: String = "-Team Melakukan Pembersihan\n-Team Melakukan Pengujian Speedtest",
    val statusLines: String = "- AP Sudah Bersih✅\n- AP Terpantau Normal ✅\n- Speedtest Terpantau Normal✅",
    val timestamp: Long = System.currentTimeMillis()
)

data class TodayReportConfig(
    val shift: String = "Pagi(SH1)",
    val helpdesk: String = "(+62 811-1014-480)",
    val onDutyDailyPm: String = "Liberry (0895-0961-6714) \nAkmal (0851-7444-2035)",
    val onDutyTodayCgk: String = "Liberry   +62 895-0961-6714\nAkmal \t+62 851-7444-2035",
    val aplusanShiftLabel: String = "Shift Malam (SH2)",
    val petugasEos: String = "Salman  +62 851-2479-3013\nRizky +62 858-9493-9981",
    val officers: String = "Maulana    \t: 0878-8443-3563\nMujiono     \t: 0821-2274-1188 \nRangga      \t: 0812-1887-4642"
)

data class NasUploadConfig(
    val serverUrl: String = "https://akmal-satrio-n.work.gd/pm",
    val token: String = "",
    val autoUploadOnSave: Boolean = true,
    val autoDetectLocationFromPhoto1: Boolean = true
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()
}

