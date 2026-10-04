package com.enrpau.dualscreendex.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Games the user adds in Settings (a ROM hack, a favourite combo...): a name plus a snapshot of every
 * look-and-dex setting - base game and version, dex profile, sprites, palette, dithering, LCD and so on.
 * Picking one restores the snapshot; while one is active, setting changes are saved back into it.
 * Each custom game also has its own teams.
 */
object CustomGames {
    data class CustomGame(val id: String, var name: String, var settings: Map<String, String>)

    private const val PREFS = "DualDexPrefs"
    private const val KEY_LIST = "CUSTOM_GAMES"
    private const val KEY_ACTIVE = "ACTIVE_CUSTOM_GAME"
    private const val KEY_PROFILE = "PROFILE"   // the Dex Version lives in RomManager's own prefs

    // the settings a custom game remembers; per-family keys (GAME_VERSION_red...) are matched by prefix
    private val keys = setOf(
        "SELECTED_THEME_ID", "MATCH_DEX_TO_GAME", "SPRITE_SET", "USE_2D_SPRITES",
        "GB_PALETTE", "LCD_EFFECT", "DITHER", "FOUR_COLOUR"
    )
    private val prefixes = listOf("GAME_VERSION_", "GAME_DEX_ONLY_")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<CustomGame> = try {
        prefs(context).getString(KEY_LIST, null)?.let {
            Gson().fromJson<List<CustomGame>>(it, object : TypeToken<List<CustomGame>>() {}.type)
        }.orEmpty()
    } catch (e: Exception) { emptyList() }

    private fun store(context: Context, list: List<CustomGame>) =
        prefs(context).edit { putString(KEY_LIST, Gson().toJson(list)) }

    fun activeId(context: Context): String? = prefs(context).getString(KEY_ACTIVE, null)
    fun active(context: Context): CustomGame? = activeId(context)?.let { id -> all(context).find { it.id == id } }

    /** Back to the built-in games (the current settings stay as they are). */
    fun clearActive(context: Context) = prefs(context).edit { remove(KEY_ACTIVE) }

    /** The current settings, as strings tagged with their type ("b:true", "s:emerald"). */
    private fun snapshot(context: Context): Map<String, String> {
        val out = HashMap<String, String>()
        for ((k, v) in prefs(context).all) {
            if (k !in keys && prefixes.none { k.startsWith(it) }) continue
            when (v) {
                is Boolean -> out[k] = "b:$v"
                is String -> out[k] = "s:$v"
            }
        }
        out[KEY_PROFILE] = "s:" + RomManager.currentProfile.id
        return out
    }

    /**
     * Where teams are kept right now: the active custom game, else the selected game when "Match dex to game"
     * is on, else the Dex Version.
     */
    fun teamScope(context: Context): String =
        activeId(context)?.let { "custom_$it" }
            ?: GameCatalog.activeFamily(context)?.takeIf { it.themeId != "dynamic" }?.let { "game_" + it.themeId }
            ?: RomManager.currentProfile.id

    /** Adds a game from the current settings (and a copy of the current teams) and makes it the active one. */
    fun add(context: Context, name: String): CustomGame {
        val game = CustomGame("cg_${System.currentTimeMillis()}", name.trim().ifEmpty { "My game" }, snapshot(context))
        val fromScope = teamScope(context)
        store(context, all(context) + game)
        prefs(context).edit { putString(KEY_ACTIVE, game.id) }
        TeamManager.copyTeams(context, fromScope, "custom_${game.id}")
        return game
    }

    fun rename(context: Context, id: String, name: String) =
        store(context, all(context).map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })

    fun delete(context: Context, id: String) {
        store(context, all(context).filterNot { it.id == id })
        if (activeId(context) == id) clearActive(context)
    }

    /** Remembers the current settings in the active custom game (called whenever settings change). */
    fun saveActive(context: Context) {
        val id = activeId(context) ?: return
        store(context, all(context).map { if (it.id == id) it.copy(settings = snapshot(context)) else it })
    }

    /** Restores a custom game's settings and makes it active. */
    fun apply(context: Context, game: CustomGame) {
        prefs(context).edit {
            for ((k, v) in game.settings) {
                if (k == KEY_PROFILE) continue
                when {
                    v.startsWith("b:") -> putBoolean(k, v.removePrefix("b:").toBoolean())
                    v.startsWith("s:") -> putString(k, v.removePrefix("s:"))
                }
            }
            putString(KEY_ACTIVE, game.id)
        }
        game.settings[KEY_PROFILE]?.removePrefix("s:")?.let { pid ->
            RomManager.getAllProfiles().find { it.id == pid }?.let { RomManager.selectProfile(context, it) }
        }
    }
}
