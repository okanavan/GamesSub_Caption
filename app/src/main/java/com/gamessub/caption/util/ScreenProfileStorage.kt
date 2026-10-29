package com.gamessub.caption.util

import android.content.Context
import android.content.SharedPreferences
import com.gamessub.caption.model.NormalizedPoint
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.RoiZoneType
import com.gamessub.caption.model.SavedScreenProfile
import com.gamessub.caption.model.ScreenQuad
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists:
 * 1. The last active 4-corner `ScreenQuad`, sub-zone rectangles (`SUBTITLE`, `QUEST`, `DIALOGUE`),
 *    `cameraZoomRatio`, and `exposureCompensation` automatically on every edit so nothing is lost
 *    when closing and reopening the app.
 * 2. Named Tripod / Monitor Presets (`SavedScreenProfile`) so if the user places the tripod in a
 *    fixed spot (e.g., "Salon TV - Tripod", "Monitör Yakın", "Witcher 3 Profil"), they can load
 *    all 4 corners, sub-frames, and camera zoom with a single tap!
 */
class ScreenProfileStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLastActiveState(
        screenQuad: ScreenQuad,
        roiMap: Map<RoiZoneType, NormalizedRoiRect>,
        cameraZoomRatio: Float,
        exposureCompensation: Int
    ) {
        val obj = JSONObject().apply {
            put("quad", quadToJson(screenQuad))
            put("subtitle", rectToJson(roiMap[RoiZoneType.SUBTITLE] ?: NormalizedRoiRect.defaultFor(RoiZoneType.SUBTITLE)))
            put("quest", rectToJson(roiMap[RoiZoneType.QUEST] ?: NormalizedRoiRect.defaultFor(RoiZoneType.QUEST)))
            put("dialogue", rectToJson(roiMap[RoiZoneType.DIALOGUE] ?: NormalizedRoiRect.defaultFor(RoiZoneType.DIALOGUE)))
            put("zoom", cameraZoomRatio.toDouble())
            put("exposure", exposureCompensation)
        }
        prefs.edit().putString(KEY_LAST_ACTIVE, obj.toString()).apply()
    }

    fun loadLastActiveState(): SavedScreenProfile? {
        val raw = prefs.getString(KEY_LAST_ACTIVE, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            SavedScreenProfile(
                id = "last_active",
                name = "Son Aktif Ayar",
                screenQuad = jsonToQuad(obj.getJSONObject("quad")),
                subtitleRect = jsonToRect(obj.getJSONObject("subtitle")),
                questRect = jsonToRect(obj.getJSONObject("quest")),
                dialogueRect = jsonToRect(obj.getJSONObject("dialogue")),
                cameraZoomRatio = obj.optDouble("zoom", 1.0).toFloat().coerceIn(1.0f, 4.0f),
                exposureCompensation = obj.optInt("exposure", -2)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun loadSavedProfiles(): List<SavedScreenProfile> {
        val raw = prefs.getString(KEY_SAVED_PROFILES, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultBuiltInProfiles()
            saveProfilesList(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedScreenProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedScreenProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        screenQuad = jsonToQuad(obj.getJSONObject("quad")),
                        subtitleRect = jsonToRect(obj.getJSONObject("subtitle")),
                        questRect = jsonToRect(obj.getJSONObject("quest")),
                        dialogueRect = jsonToRect(obj.getJSONObject("dialogue")),
                        cameraZoomRatio = obj.optDouble("zoom", 1.0).toFloat().coerceIn(1.0f, 4.0f),
                        exposureCompensation = obj.optInt("exposure", -2)
                    )
                )
            }
            list
        } catch (_: Exception) {
            defaultBuiltInProfiles()
        }
    }

    fun saveOrUpdateProfile(profile: SavedScreenProfile): List<SavedScreenProfile> {
        val current = loadSavedProfiles().toMutableList()
        val existingIdx = current.indexOfFirst { it.id == profile.id || it.name.equals(profile.name, ignoreCase = true) }
        if (existingIdx >= 0) {
            current[existingIdx] = profile
        } else {
            current.add(0, profile)
        }
        saveProfilesList(current)
        return current
    }

    fun deleteProfile(profileId: String): List<SavedScreenProfile> {
        val current = loadSavedProfiles().filterNot { it.id == profileId }
        saveProfilesList(current)
        return current
    }

    private fun saveProfilesList(profiles: List<SavedScreenProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("quad", quadToJson(p.screenQuad))
                put("subtitle", rectToJson(p.subtitleRect))
                put("quest", rectToJson(p.questRect))
                put("dialogue", rectToJson(p.dialogueRect))
                put("zoom", p.cameraZoomRatio.toDouble())
                put("exposure", p.exposureCompensation)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_PROFILES, arr.toString()).apply()
    }

    private fun defaultBuiltInProfiles(): List<SavedScreenProfile> = listOf(
        SavedScreenProfile(
            id = "preset_tripod_monitor",
            name = "Masaüstü Monitör (Sabit Tripod 1.0x)",
            screenQuad = ScreenQuad(
                topLeft = NormalizedPoint(0.06f, 0.08f),
                topRight = NormalizedPoint(0.94f, 0.08f),
                bottomRight = NormalizedPoint(0.95f, 0.92f),
                bottomLeft = NormalizedPoint(0.05f, 0.92f)
            ),
            subtitleRect = NormalizedRoiRect.defaultFor(RoiZoneType.SUBTITLE),
            questRect = NormalizedRoiRect.defaultFor(RoiZoneType.QUEST),
            dialogueRect = NormalizedRoiRect.defaultFor(RoiZoneType.DIALOGUE),
            cameraZoomRatio = 1.0f,
            exposureCompensation = -2
        ),
        SavedScreenProfile(
            id = "preset_tripod_tv_zoom",
            name = "Salon TV Uzak Mesafe (Tripod 1.8x Zoom)",
            screenQuad = ScreenQuad(
                topLeft = NormalizedPoint(0.10f, 0.12f),
                topRight = NormalizedPoint(0.90f, 0.12f),
                bottomRight = NormalizedPoint(0.92f, 0.88f),
                bottomLeft = NormalizedPoint(0.08f, 0.88f)
            ),
            subtitleRect = NormalizedRoiRect(0.12f, 0.74f, 0.88f, 0.93f),
            questRect = NormalizedRoiRect(0.60f, 0.10f, 0.95f, 0.44f),
            dialogueRect = NormalizedRoiRect(0.20f, 0.48f, 0.80f, 0.75f),
            cameraZoomRatio = 1.8f,
            exposureCompensation = -2
        )
    )

    private fun quadToJson(q: ScreenQuad): JSONObject = JSONObject().apply {
        put("tlX", q.topLeft.x.toDouble())
        put("tlY", q.topLeft.y.toDouble())
        put("trX", q.topRight.x.toDouble())
        put("trY", q.topRight.y.toDouble())
        put("brX", q.bottomRight.x.toDouble())
        put("brY", q.bottomRight.y.toDouble())
        put("blX", q.bottomLeft.x.toDouble())
        put("blY", q.bottomLeft.y.toDouble())
    }

    private fun jsonToQuad(obj: JSONObject): ScreenQuad = ScreenQuad(
        topLeft = NormalizedPoint(obj.getDouble("tlX").toFloat(), obj.getDouble("tlY").toFloat()),
        topRight = NormalizedPoint(obj.getDouble("trX").toFloat(), obj.getDouble("trY").toFloat()),
        bottomRight = NormalizedPoint(obj.getDouble("brX").toFloat(), obj.getDouble("brY").toFloat()),
        bottomLeft = NormalizedPoint(obj.getDouble("blX").toFloat(), obj.getDouble("blY").toFloat())
    ).clamp()

    private fun rectToJson(r: NormalizedRoiRect): JSONObject = JSONObject().apply {
        put("l", r.left.toDouble())
        put("t", r.top.toDouble())
        put("r", r.right.toDouble())
        put("b", r.bottom.toDouble())
    }

    private fun jsonToRect(obj: JSONObject): NormalizedRoiRect = NormalizedRoiRect(
        left = obj.getDouble("l").toFloat(),
        top = obj.getDouble("t").toFloat(),
        right = obj.getDouble("r").toFloat(),
        bottom = obj.getDouble("b").toFloat()
    ).clamp()

    companion object {
        private const val PREFS_NAME = "gamessub_screen_profiles_prefs"
        private const val KEY_LAST_ACTIVE = "last_active_screen_config_json"
        private const val KEY_SAVED_PROFILES = "saved_tripod_profiles_json_array"
    }
}
