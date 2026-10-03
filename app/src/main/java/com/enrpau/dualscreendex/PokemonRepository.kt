package com.enrpau.dualscreendex

import android.content.Context
import com.enrpau.dualscreendex.data.CsvParsers
import com.enrpau.dualscreendex.data.GameCatalog
import com.enrpau.dualscreendex.data.RomManager

class PokemonRepository(private val context: Context) {

    private var allPokemon: List<Pokemon> = emptyList()

    init {
        RomManager.initialize(context)
        reloadDatabase()
    }

    fun reloadDatabase() {
        val profile = RomManager.currentProfile

        val fullDex = CsvParsers.parsePokedex(context, profile)
        val game = GameCatalog.activeFamily(context)

        val baseList: List<Pokemon>
        val regionalList: List<Pokemon>
        if (game != null) {
            // the selected game decides: its own national dex (or all 1025), and only the forms that exist in it
            val inGame = if (GameCatalog.isGameDexOnly(context, game)) GameCatalog.speciesIds(context, game) else null
            baseList = if (inGame != null) fullDex.filter { it.id in inGame } else fullDex
            val ids = baseList.map { it.id }.toSet()
            regionalList = CsvParsers.parseFormsAsset(context, "dex/forms.csv").filter { f ->
                f.id in ids && GameCatalog.formGroup(f.variantLabel.orEmpty()) in game.formGroups
            }
        } else {
            val maxId = profile.maxDexId ?: Int.MAX_VALUE
            baseList = fullDex.filter { it.id <= maxId }
            regionalList = if (profile.hasRegionals()) CsvParsers.parseRegionalForms(context, profile) else emptyList()
        }

        val nameMap = baseList.associate { it.id to it.name }
        val kanaMap = baseList.associate { it.id to it.japaneseKana }

        val processedRegionals = regionalList.map { p ->
            val rawName = nameMap[p.id] ?: "Unknown"
            val baseName = rawName.replaceFirstChar { it.uppercase() }
            // adapter takes care of region suffix
            val fullName = baseName

            Pokemon(fullName, p.id, p.type1, p.type2, p.variantLabel, japaneseKana = kanaMap[p.id])
        }

        // stable sort keeps each base species ahead of its forms
        val combined = (baseList + processedRegionals).sortedBy { it.id }

        // a game's own dex: its order and numbering (Hisui #001 Rowlet...), forms right after their species
        val order = game?.takeIf { GameCatalog.isGameDexOnly(context, it) }?.let { GameCatalog.dexOrder(context, it) }
        allPokemon = if (order.isNullOrEmpty()) combined else {
            val byId = combined.groupBy { it.id }
            order.flatMap { entry ->
                byId[entry.id].orEmpty().map { it.copy(dexNumber = entry.number, dexLabel = entry.dex) }
            }
        }
    }

    fun getAllPokemon(): List<Pokemon> = allPokemon

    fun getVariantsFor(name: String): List<Pokemon> {
        val target = allPokemon.find { it.name.equals(name, true) } ?: return emptyList()

        return allPokemon.filter { it.id == target.id }
    }

    fun filterPokemon(query: String): List<Pokemon> {
        if (query.isBlank()) return allPokemon
        return allPokemon.filter {
            it.name.contains(query, ignoreCase = true) ||
                    (it.japaneseKana?.contains(query) == true) ||
                    (it.variantLabel?.contains(query, ignoreCase = true) == true)
        }
    }
}