package com.gamessub.caption.billing

import android.content.Context
import android.content.SharedPreferences
import com.gamessub.caption.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Manages the Monetization & Access Tier rules defined in Section 6 of PROJE_MIMARISI.md:
 *
 * 1. `vipAdmin` Flavor (`BuildConfig.IS_VIP_ADMIN_BUILD == true`):
 *    - 100% Unlimited Lifetime PRO, zero ads, no 7-day limit.
 * 2. `playstore` Flavor:
 *    - First 7 days from first launch: Full PRO Trial (Multi-ROI, BYOK AI keys, Multi-Character TTS, All HUD themes, No Ads).
 *    - After 7 days (if not subscribed & no Master Key): Free Tier:
 *      - Single ROI zone only (Subtitle Zone 1)
 *      - Offline ML Kit translation + Single Narrator TTS
 *      - Bottom 18% fixed AdMob Adaptive Banner strip (top 82% reserved strictly for subtitle HUD so ads never overlap text).
 * 3. Secret 7-Tap Master Key in Settings:
 *    - Tapping the version number 7 times opens the Master Key dialog.
 *    - Entering the valid Master Code unlocks lifetime PRO on any device.
 */
class ProAccessManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_FIRST_INSTALL_TIME)) {
            prefs.edit().putLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis()).apply()
        }
    }

    val isVipAdminBuild: Boolean
        get() = BuildConfig.IS_VIP_ADMIN_BUILD

    var isMasterKeyUnlocked: Boolean
        get() = prefs.getBoolean(KEY_MASTER_UNLOCKED, false)
        private set(value) {
            prefs.edit().putBoolean(KEY_MASTER_UNLOCKED, value).apply()
        }

    var isMonthlySubscriber: Boolean
        get() = prefs.getBoolean(KEY_SUBSCRIBER_ACTIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SUBSCRIBER_ACTIVE, value).apply()
        }

    var deepLApiKey: String
        get() = prefs.getString(KEY_DEEPL_API, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DEEPL_API, value.trim()).apply()
        }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_GEMINI_API, value.trim()).apply()
        }

    /**
     * Remaining days in the 7-day free PRO trial (0..7).
     */
    fun getRemainingTrialDays(nowMillis: Long = System.currentTimeMillis()): Int {
        val firstInstall = prefs.getLong(KEY_FIRST_INSTALL_TIME, nowMillis)
        val elapsedMillis = (nowMillis - firstInstall).coerceAtLeast(0L)
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(elapsedMillis).toInt()
        return (TRIAL_DURATION_DAYS - elapsedDays).coerceIn(0, TRIAL_DURATION_DAYS)
    }

    fun isTrialActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return getRemainingTrialDays(nowMillis) > 0
    }

    /**
     * Returns true if the user currently has full PRO privileges (via VIP APK, Master Key,
     * active 7-day trial, or Google Play monthly subscription).
     */
    fun hasProAccess(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return isVipAdminBuild ||
            isMasterKeyUnlocked ||
            isMonthlySubscriber ||
            isTrialActive(nowMillis)
    }

    /**
     * Bottom 18% non-intrusive banner ad is shown ONLY when PRO access is not active.
     */
    fun shouldShowBottomBannerAd(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return !hasProAccess(nowMillis)
    }

    /**
     * Verifies and activates the 7-tap Secret Master Password.
     */
    fun verifyAndUnlockMasterKey(inputCode: String): Boolean {
        val normalized = inputCode.trim()
        if (normalized.equals(DEFAULT_MASTER_KEY, ignoreCase = true)) {
            isMasterKeyUnlocked = true
            return true
        }
        return false
    }

    fun getStatusBadgeText(): String {
        return when {
            isVipAdminBuild -> "VIP ADMIN SÜRÜMÜ (Ömür Boyu Sınırsız PRO)"
            isMasterKeyUnlocked -> "MASTER KEY AKTİF (Ömür Boyu Sınırsız PRO)"
            isMonthlySubscriber -> "PRO ABONE (Reklamsız Tam Sürüm)"
            isTrialActive() -> "ÜCRETSİZ PRO DENEME (${getRemainingTrialDays()} Gün Kaldı)"
            else -> "ÜCRETSİZ SÜRÜM (Tek Alan • Offline • Alt %18 Banner)"
        }
    }

    companion object {
        private const val PREFS_NAME = "gamessub_pro_prefs"
        private const val KEY_FIRST_INSTALL_TIME = "first_install_time_ms"
        private const val KEY_MASTER_UNLOCKED = "master_key_unlocked"
        private const val KEY_SUBSCRIBER_ACTIVE = "monthly_subscriber_active"
        private const val KEY_DEEPL_API = "byok_deepl_api_key"
        private const val KEY_GEMINI_API = "byok_gemini_api_key"

        const val TRIAL_DURATION_DAYS = 7
        // Secret master password unlocked by tapping version number 7 times in Settings
        const val DEFAULT_MASTER_KEY = "OKAN-VIP-777"
    }
}

