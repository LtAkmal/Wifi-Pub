package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pm_report_prefs")

class PmRepository(
    private val context: Context,
    private val pmDao: PmDao
) {
    companion object {
        private val KEY_SHIFT = stringPreferencesKey("shift")
        private val KEY_HELPDESK = stringPreferencesKey("helpdesk")
        private val KEY_ON_DUTY_DAILY_PM = stringPreferencesKey("on_duty_daily_pm")
        private val KEY_ON_DUTY_TODAY_CGK = stringPreferencesKey("on_duty_today_cgk")
        private val KEY_APLUSAN_SHIFT = stringPreferencesKey("aplusan_shift")
        private val KEY_PETUGAS_EOS = stringPreferencesKey("petugas_eos")
        private val KEY_OFFICERS = stringPreferencesKey("officers")
        private val KEY_CUSTOM_LOCATIONS = stringPreferencesKey("custom_locations")

        val DEFAULT_LOCATIONS = listOf(
            "Area R. Tunggu samping mamma roti loby T3 Inter, Terminal 3",
            "Plaza Kedatangan Gate 4 Tengah, Terminal 3",
            "Plaza Kedatangan Gate 4 Sebelah Kiri, Terminal 3",
            "Plaza Kedatangan Gate 3 Sebelah Kanan, Terminal 3",
            "Plaza Kedatangan Gate 3 Sebelah Kiri, Terminal 3",
            "R. Tunggu East Lobby T3 Dom, Terminal 3",
            "Plaza Kedatangan Gate 2 Tengah, Terminal 3",
            "Plaza Kedatangan Gate 5 Sebelah Kanan, Terminal 3",
            "R. Tunggu Parkir T3 Dom, Terminal 3",
            "Plaza Kedatangan Gate 1 Sebelah kanan, Terminal 3"
        )
    }

    val reportConfigFlow: Flow<TodayReportConfig> = context.dataStore.data.map { prefs ->
        val defaults = TodayReportConfig()
        TodayReportConfig(
            shift = prefs[KEY_SHIFT] ?: defaults.shift,
            helpdesk = prefs[KEY_HELPDESK] ?: defaults.helpdesk,
            onDutyDailyPm = prefs[KEY_ON_DUTY_DAILY_PM] ?: defaults.onDutyDailyPm,
            onDutyTodayCgk = prefs[KEY_ON_DUTY_TODAY_CGK] ?: defaults.onDutyTodayCgk,
            aplusanShiftLabel = prefs[KEY_APLUSAN_SHIFT] ?: defaults.aplusanShiftLabel,
            petugasEos = prefs[KEY_PETUGAS_EOS] ?: defaults.petugasEos,
            officers = prefs[KEY_OFFICERS] ?: defaults.officers
        )
    }

    val savedLocationsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_LOCATIONS]
        if (raw.isNullOrBlank()) {
            DEFAULT_LOCATIONS
        } else {
            val customList = raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }
            (customList + DEFAULT_LOCATIONS).distinct()
        }
    }

    suspend fun saveReportConfig(config: TodayReportConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHIFT] = config.shift
            prefs[KEY_HELPDESK] = config.helpdesk
            prefs[KEY_ON_DUTY_DAILY_PM] = config.onDutyDailyPm
            prefs[KEY_ON_DUTY_TODAY_CGK] = config.onDutyTodayCgk
            prefs[KEY_APLUSAN_SHIFT] = config.aplusanShiftLabel
            prefs[KEY_PETUGAS_EOS] = config.petugasEos
            prefs[KEY_OFFICERS] = config.officers
        }
    }

    suspend fun addCustomLocation(location: String) {
        val clean = location.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_CUSTOM_LOCATIONS]?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(clean) + existing).distinct()
            prefs[KEY_CUSTOM_LOCATIONS] = updated.joinToString("||")
        }
    }

    fun getPmReportsForDate(dateKey: String): Flow<List<PmReportEntity>> =
        pmDao.getPmReportsForDate(dateKey)

    fun getAllPmReports(): Flow<List<PmReportEntity>> =
        pmDao.getAllPmReports()

    suspend fun insertPmReport(report: PmReportEntity): Long =
        pmDao.insertPmReport(report)

    suspend fun deletePmReport(id: Int) =
        pmDao.deletePmReportById(id)

    fun getCmForDate(dateKey: String): Flow<List<CorrectiveMaintenanceEntity>> =
        pmDao.getCmForDate(dateKey)

    suspend fun insertCm(cm: CorrectiveMaintenanceEntity): Long =
        pmDao.insertCm(cm)

    suspend fun deleteCm(id: Int) =
        pmDao.deleteCmById(id)
}
