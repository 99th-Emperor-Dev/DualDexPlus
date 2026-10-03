package com.enrpau.dualscreendex

import com.enrpau.dualscreendex.data.RomProfile

object GenerationHelper {

    fun getGenSpecificTypes(pokemon: Pokemon, mechanics: RomProfile.Mechanics): Pair<PokemonType, PokemonType> {
        var t1 = pokemon.type1
        var t2 = pokemon.type2 ?: PokemonType.UNKNOWN

        // Retro (Gen 1)
        if (mechanics == RomProfile.Mechanics.GEN_1) {
            // Specific Gen 1 reversions
            if (pokemon.name.equals("magnemite", true) || pokemon.name.equals("magneton", true)) {
                t2 = PokemonType.UNKNOWN
            }

            // Exclude Steel and Dark for Gen 1
            if (t1 == PokemonType.STEEL || t1 == PokemonType.DARK) {
                // If it has a valid Gen 1 secondary type, promote it
                if (t2 != PokemonType.UNKNOWN && t2 != PokemonType.STEEL && t2 != PokemonType.DARK && t2 != PokemonType.FAIRY) {
                    t1 = t2
                    t2 = PokemonType.UNKNOWN
                } else {
                    t1 = PokemonType.NORMAL
                }
            }
            if (t2 == PokemonType.STEEL || t2 == PokemonType.DARK) {
                t2 = PokemonType.UNKNOWN
            }
        }

        // Classic/Retro (Gen 1-5)
        if (mechanics != RomProfile.Mechanics.GEN_6_PLUS) {
            // Exclude Fairy type
            if (t1 == PokemonType.FAIRY) {
                // Most pure Fairies or Fairy-primary were Normal before Gen 6
                // (Clefairy line, Snubbull line, Togepi line)
                t1 = PokemonType.NORMAL
            }

            if (t2 == PokemonType.FAIRY) {
                // Dual types that added Fairy usually had Fairy as secondary
                // (Marill, Jigglypuff, Mr. Mime, Gardevoir, Whimsicott)
                t2 = PokemonType.UNKNOWN
            }
        }

        return Pair(t1, t2)
    }
}