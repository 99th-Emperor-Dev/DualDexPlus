package com.enrpau.dualscreendex

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Pokemon(
    val name: String,
    val id: Int,
    val type1: PokemonType,
    val type2: PokemonType?,
    val variantLabel: String? = null,
    // the selected game's own dex number (e.g. Hisui #001), null when showing national numbers
    val dexNumber: Int? = null,
    val dexLabel: String? = null,
    // japanese (katakana) name, used to recognise japanese games on screen
    val japaneseKana: String? = null
) : Parcelable
