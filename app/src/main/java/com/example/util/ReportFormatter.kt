package com.example.util

import com.example.data.CorrectiveMaintenanceEntity
import com.example.data.PmReportEntity
import com.example.data.TodayReportConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportFormatter {

    fun todayDateKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun formatDisplayDateEn(dateKey: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(dateKey) ?: Date()
            SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(date)
        } catch (e: Exception) {
            SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())
        }
    }

    fun formatHariTanggalId(dateKey: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(dateKey) ?: Date()
            val localeId = Locale("id", "ID")
            SimpleDateFormat("EEEE, dd MMMM yyyy", localeId).format(date)
        } catch (e: Exception) {
            val localeId = Locale("id", "ID")
            SimpleDateFormat("EEEE, dd MMMM yyyy", localeId).format(Date())
        }
    }

    fun cleanSpeedNumber(raw: String): String {
        val cleaned = raw.trim()
            .replace("Mbps", "", ignoreCase = true)
            .replace("mbps", "", ignoreCase = true)
            .trim()
        return cleaned.ifEmpty { "0" }
    }

    /**
     * Formats the individual Daily Routine Preventive Maintenance report
     * matching the exact template provided by the user.
     */
    fun formatDailyPmReport(
        location: String,
        equipmentName: String,
        uploadMbps: String,
        downloadMbps: String,
        displayDateEn: String,
        statusNetwork: Boolean = true,
        statusDevice: Boolean = true,
        statusSpeed: Boolean = true,
        onDutyText: String
    ): String {
        val upClean = cleanSpeedNumber(uploadMbps)
        val downClean = cleanSpeedNumber(downloadMbps)
        val locText = location.trim().ifEmpty { "-" }
        val equipText = equipmentName.trim().ifEmpty { "Access Point Wifi Publik" }
        val netIcon = if (statusNetwork) "✅" else "⚠️"
        val devIcon = if (statusDevice) "✅" else "⚠️"
        val spdIcon = if (statusSpeed) "✅" else "⚠️"

        return buildString {
            appendLine("REPORT DAILY ROUTINE")
            appendLine("PREVENTIVE MAINTENANCE")
            appendLine()
            appendLine("\uD83D\uDEE0\uFE0F: Pengujian & Pengecekan Speed Bandwitch Wifi Publik. ")
            appendLine("Lokasi : $locText")
            appendLine("Nama Peralatan : $equipText")
            appendLine("Kecepatan : Up ${upClean}Mbps / Down ${downClean}Mbps")
            appendLine()
            appendLine(" \uD83D\uDDD3\uFE0FTanggal : ${displayDateEn.trim()}")
            appendLine()
            appendLine("Status :")
            appendLine("- Jaringan Normal $netIcon")
            appendLine("- Perangkat berfungsi normal $devIcon")
            appendLine("- Speed Normal$spdIcon")
            appendLine("-------------------------------")
            appendLine("\uD83E\uDDD1\uD83D\uDD27 On Duty Wifi Publik:")
            append(onDutyText.trim())
        }
    }

    /**
     * Formats the compiled ICT OPERATION DAILY REPORT (CGK) (Today Report)
     * combining all PM Speedtest items (target 10) and Corrective Maintenance items.
     */
    fun formatTodayReport(
        hariTanggalId: String,
        config: TodayReportConfig,
        pmList: List<PmReportEntity>,
        cmList: List<CorrectiveMaintenanceEntity>
    ): String {
        return buildString {
            appendLine("*ICT OPERATION DAILY REPORT (CGK).")
            appendLine()
            appendLine("Hari Tanggal\t: $hariTanggalId")
            appendLine("Shift\t        : ${config.shift}")
            appendLine()
            appendLine("Helpdesk : ${config.helpdesk}")
            appendLine("━━━━━━━━━━━━━━━━━━")
            appendLine("\uD83D\uDCF6 WIFI PUBLIC ")
            appendLine("On Duty :")
            appendLine()
            appendLine("CGK : ")
            appendLine()
            appendLine(config.onDutyTodayCgk.trim())
            appendLine()
            appendLine("===============================================================")
            appendLine()
            appendLine("Aplusan ke Tim ${config.aplusanShiftLabel}")
            appendLine("Petugas EOS\t: ")
            appendLine()
            appendLine(config.petugasEos.trim())
            appendLine()
            appendLine("===============================================================")
            appendLine()
            appendLine("*Preventive Maintenance :")
            appendLine()

            if (pmList.isEmpty()) {
                appendLine(". . Speedtest Wifi :*")
                appendLine("- [Belum ada data PM Speedtest hari ini]")
                appendLine()
            } else {
                pmList.forEachIndexed { index, pm ->
                    val prefix = if (index == 0) ". . Speedtest Wifi :*" else ". Speedtest Wifi :*"
                    val normalizedLoc = pm.location.trim()
                        .removePrefix("Area ")
                        .removePrefix("area ")
                        .trim()
                    val upClean = cleanSpeedNumber(pm.uploadMbps)
                    val downClean = cleanSpeedNumber(pm.downloadMbps)
                    appendLine(prefix)
                    appendLine("- [Pengujian & Pengecekan Speed Bandwitch Wifi Publik area $normalizedLoc - Up ${upClean}Mbps / Down ${downClean}Mbps]")
                    appendLine()
                }
            }

            appendLine("*Corrective Maintenance: -")
            if (cmList.isNotEmpty()) {
                appendLine()
                cmList.forEachIndexed { index, cm ->
                    appendLine("\uD83D\uDEE0\uFE0F:${cm.activityTitle.trim()}")
                    appendLine("Lokasi : ${cm.location.trim()}")
                    appendLine("Nama Peralatan : ${cm.equipmentName.trim()}")
                    appendLine("Kendala: ${cm.kendala.trim()}")
                    appendLine("Issue: ${cm.issue.trim()}")
                    appendLine("Action:")
                    appendLine(cm.actionLines.trim())
                    appendLine("Status :")
                    appendLine(cm.statusLines.trim())
                    if (index < cmList.lastIndex) {
                        appendLine()
                    }
                }
            }
            appendLine("===============================================================")
            appendLine()
            appendLine("OFFICER")
            append(config.officers.trim())
        }
    }
}
