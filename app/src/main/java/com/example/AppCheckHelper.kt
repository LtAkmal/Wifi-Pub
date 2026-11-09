package com.example

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val APPCHECK_CACHE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val HARNESS_TOKEN_FLAG = "IS_AISTUDIO_HARNESS_TOKEN"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                // Running inside AI Studio Emulator Harness with valid injected debug token
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .putBoolean(HARNESS_TOKEN_FLAG, true)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully from Intent.")

                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            } else if (debugPrefs.getBoolean(HARNESS_TOKEN_FLAG, false) &&
                !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
            ) {
                // Re-launched inside AI Studio Emulator where harness token was already saved
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed using persisted harness token.")
            } else {
                // Running as a standalone installed APK on a physical phone (no harness token).
                // Clear any self-generated invalid debug UUID and cached invalid App Check token,
                // then install RecaptchaAppCheckProviderFactory so physical devices authenticate properly.
                debugPrefs.edit().clear().commit()
                context.getSharedPreferences(
                    "$APPCHECK_CACHE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                ).edit().clear().commit()

                installRecaptchaProvider(context)
            }
        } else {
            installRecaptchaProvider(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context) {
    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    if (resId != 0) {
        val siteKey = context.getString(resId).trim()
        if (siteKey.isNotBlank()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else {
            Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
    } else {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
    }
}
