package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.AiProviderConfig
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
        // Fresh v2 key so pre-existing hardcoded locations are cleared and only user-typed locations are saved
        private val KEY_USER_SAVED_LOCATIONS = stringPreferencesKey("user_saved_locations_v2")

        // Custom AI Provider (9Router / Direct Gemini) keys
        private val KEY_AI_PROVIDER_TYPE = stringPreferencesKey("ai_provider_type")
        private val KEY_9ROUTER_BASE_URL = stringPreferencesKey("ninerouter_base_url")
        private val KEY_9ROUTER_API_KEY = stringPreferencesKey("ninerouter_api_key")
        private val KEY_9ROUTER_MODEL = stringPreferencesKey("ninerouter_model")
        private val KEY_GEMINI_DIRECT_KEY = stringPreferencesKey("gemini_direct_key")
        private val KEY_GEMINI_DIRECT_MODEL = stringPreferencesKey("gemini_direct_model")

        // NAS Upload Configuration keys
        private val KEY_NAS_SERVER_URL = stringPreferencesKey("nas_server_url")
        private val KEY_NAS_UPLOAD_TOKEN = stringPreferencesKey("nas_upload_token")
        private val KEY_NAS_AUTO_UPLOAD = booleanPreferencesKey("nas_auto_upload_on_save")
        private val KEY_NAS_AUTO_DETECT_LOCATION = booleanPreferencesKey("nas_auto_detect_location_photo1")

        val DEFAULT_LOCATIONS = emptyList<String>()
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

    val aiProviderConfigFlow: Flow<AiProviderConfig> = context.dataStore.data.map { prefs ->
        val defaults = AiProviderConfig()
        AiProviderConfig(
            providerType = prefs[KEY_AI_PROVIDER_TYPE] ?: defaults.providerType,
            nineRouterBaseUrl = prefs[KEY_9ROUTER_BASE_URL] ?: defaults.nineRouterBaseUrl,
            nineRouterApiKey = prefs[KEY_9ROUTER_API_KEY] ?: defaults.nineRouterApiKey,
            nineRouterModel = prefs[KEY_9ROUTER_MODEL] ?: defaults.nineRouterModel,
            geminiDirectApiKey = prefs[KEY_GEMINI_DIRECT_KEY] ?: defaults.geminiDirectApiKey,
            geminiDirectModel = prefs[KEY_GEMINI_DIRECT_MODEL] ?: defaults.geminiDirectModel
        )
    }

    val nasUploadConfigFlow: Flow<NasUploadConfig> = context.dataStore.data.map { prefs ->
        val defaults = NasUploadConfig()
        NasUploadConfig(
            serverUrl = prefs[KEY_NAS_SERVER_URL]?.takeIf { it.isNotBlank() } ?: defaults.serverUrl,
            token = prefs[KEY_NAS_UPLOAD_TOKEN] ?: defaults.token,
            autoUploadOnSave = prefs[KEY_NAS_AUTO_UPLOAD] ?: defaults.autoUploadOnSave,
            autoDetectLocationFromPhoto1 = prefs[KEY_NAS_AUTO_DETECT_LOCATION] ?: defaults.autoDetectLocationFromPhoto1
        )
    }

    val savedLocationsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_USER_SAVED_LOCATIONS]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
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

    suspend fun saveAiProviderConfig(config: AiProviderConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AI_PROVIDER_TYPE] = config.providerType
            prefs[KEY_9ROUTER_BASE_URL] = config.nineRouterBaseUrl.trim()
            prefs[KEY_9ROUTER_API_KEY] = config.nineRouterApiKey.trim()
            prefs[KEY_9ROUTER_MODEL] = config.nineRouterModel.trim()
            prefs[KEY_GEMINI_DIRECT_KEY] = config.geminiDirectApiKey.trim()
            prefs[KEY_GEMINI_DIRECT_MODEL] = config.geminiDirectModel.trim()
        }
    }

    suspend fun saveNasUploadConfig(config: NasUploadConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAS_SERVER_URL] = config.serverUrl.trim().ifBlank { "https://akmal-satrio-n.work.gd/pm" }
            prefs[KEY_NAS_UPLOAD_TOKEN] = config.token.trim()
            prefs[KEY_NAS_AUTO_UPLOAD] = config.autoUploadOnSave
            prefs[KEY_NAS_AUTO_DETECT_LOCATION] = config.autoDetectLocationFromPhoto1
        }
    }

    suspend fun addCustomLocation(location: String) {
        val clean = location.trim()
        if (clean.length < 2) return
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USER_SAVED_LOCATIONS]?.split("||")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) }).distinct()
            prefs[KEY_USER_SAVED_LOCATIONS] = updated.joinToString("||")
        }
    }

    suspend fun removeCustomLocation(location: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USER_SAVED_LOCATIONS]?.split("||")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val updated = existing.filterNot { it.equals(location.trim(), ignoreCase = true) }
            prefs[KEY_USER_SAVED_LOCATIONS] = updated.joinToString("||")
        }
    }

    fun getPmReportsForDate(dateKey: String): Flow<List<PmReportEntity>> =
        pmDao.getPmReportsForDate(dateKey)

    fun getAllPmReports(): Flow<List<PmReportEntity>> =
        pmDao.getAllPmReports()

    suspend fun getPmReportById(id: Int): PmReportEntity? =
        pmDao.getPmReportById(id)

    suspend fun getPendingOrFailedPmReports(): List<PmReportEntity> =
        pmDao.getPendingOrFailedPmReports()

    suspend fun insertPmReport(report: PmReportEntity): Long =
        pmDao.insertPmReport(report)

    suspend fun updatePmReport(report: PmReportEntity) =
        pmDao.updatePmReport(report)

    suspend fun updatePmUploadStatus(
        id: Int,
        clientRef: String?,
        status: String,
        uploadedId: String?,
        error: String?
    ) = pmDao.updatePmUploadStatus(id, clientRef, status, uploadedId, error)

    suspend fun deletePmReport(id: Int) =
        pmDao.deletePmReportById(id)

    fun getCmForDate(dateKey: String): Flow<List<CorrectiveMaintenanceEntity>> =
        pmDao.getCmForDate(dateKey)

    suspend fun getCmListForDateSync(dateKey: String): List<CorrectiveMaintenanceEntity> =
        pmDao.getCmListForDateSync(dateKey)

    suspend fun insertCm(cm: CorrectiveMaintenanceEntity): Long =
        pmDao.insertCm(cm)

    suspend fun updateCm(cm: CorrectiveMaintenanceEntity) =
        pmDao.updateCm(cm)

    suspend fun deleteCm(id: Int) =
        pmDao.deleteCmById(id)
}

