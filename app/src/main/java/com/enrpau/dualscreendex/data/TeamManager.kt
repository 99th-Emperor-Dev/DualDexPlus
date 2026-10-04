package com.enrpau.dualscreendex.data

import android.content.Context
import androidx.core.content.edit
import com.enrpau.dualscreendex.Pokemon
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Team(
    val id: String,
    val name: String,
    val profileId: String,
    val members: List<Pokemon?>
)

// stores any number of 6-slot teams, grouped by game profile
object TeamManager {
    private const val PREFS_NAME = "TeamData"
    private const val KEY_TEAMS = "TEAMS_V2"
    private const val KEY_LEGACY_TEAM = "SAVED_TEAM"
    private const val KEY_ACTIVE_PREFIX = "ACTIVE_TEAM_"
    const val TEAM_SIZE = 6

    private var teams: MutableList<Team>? = null

    private fun all(context: Context): MutableList<Team> {
        teams?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = try {
            prefs.getString(KEY_TEAMS, null)?.let {
                val type = object : TypeToken<List<Team>>() {}.type
                Gson().fromJson<List<Team>>(it, type)
            }
        } catch (e: Exception) {
            null
        }
        val list = loaded?.map { it.copy(members = normalize(it.members)) }?.toMutableList() ?: mutableListOf()

        // migrate the old single global team into the current profile
        if (loaded == null) {
            val legacy = try {
                prefs.getString(KEY_LEGACY_TEAM, null)?.let {
                    val type = object : TypeToken<List<Pokemon?>>() {}.type
                    Gson().fromJson<List<Pokemon?>>(it, type)
                }
            } catch (e: Exception) {
                null
            }
            if (legacy != null && legacy.any { it != null }) {
                list += Team(newId(), "My Team", RomManager.currentProfile.id, normalize(legacy))
            }
        }
        teams = list
        return list
    }

    fun teamsFor(context: Context, profileId: String): List<Team> =
        all(context).filter { it.profileId == profileId }

    fun activeTeam(context: Context, profileId: String): Team {
        val forProfile = teamsFor(context, profileId)
        val activeId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PREFIX + profileId, null)
        return forProfile.find { it.id == activeId }
            ?: forProfile.firstOrNull()
            ?: createTeam(context, profileId, "Team 1")
    }

    fun setActive(context: Context, team: Team) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ACTIVE_PREFIX + team.profileId, team.id)
        }
    }

    fun createTeam(context: Context, profileId: String, name: String): Team {
        val team = Team(newId(), name, profileId, List(TEAM_SIZE) { null })
        all(context).add(team)
        setActive(context, team)
        save(context)
        return team
    }

    fun renameTeam(context: Context, teamId: String, name: String) = update(context, teamId) { it.copy(name = name) }

    fun setMember(context: Context, teamId: String, slot: Int, pokemon: Pokemon?) = update(context, teamId) {
        if (slot !in 0 until TEAM_SIZE) it
        else it.copy(members = it.members.toMutableList().also { m -> m[slot] = pokemon })
    }

    fun deleteTeam(context: Context, teamId: String) {
        all(context).removeAll { it.id == teamId }
        save(context)
    }

    /** A new custom game starts with copies of the teams you had in the game it was made from. */
    fun copyTeams(context: Context, fromScope: String, toScope: String) {
        val copies = teamsFor(context, fromScope).map { it.copy(id = newId(), profileId = toScope) }
        if (copies.isEmpty()) return
        all(context).addAll(copies)
        save(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeIdx = teamsFor(context, fromScope).indexOfFirst { it.id == prefs.getString(KEY_ACTIVE_PREFIX + fromScope, null) }
        setActive(context, copies[activeIdx.coerceAtLeast(0)])
    }

    fun nextDefaultName(context: Context, profileId: String): String {
        val names = teamsFor(context, profileId).map { it.name }.toSet()
        var n = names.size + 1
        while ("Team $n" in names) n++
        return "Team $n"
    }

    private fun update(context: Context, teamId: String, change: (Team) -> Team) {
        val list = all(context)
        val idx = list.indexOfFirst { it.id == teamId }
        if (idx == -1) return
        list[idx] = change(list[idx])
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TEAMS, Gson().toJson(all(context)))
        }
    }

    private fun normalize(members: List<Pokemon?>?): List<Pokemon?> {
        val m = (members ?: emptyList()).take(TEAM_SIZE).toMutableList()
        while (m.size < TEAM_SIZE) m.add(null)
        return m
    }

    private fun newId() = "team_${System.currentTimeMillis()}_${(0..999).random()}"
}
