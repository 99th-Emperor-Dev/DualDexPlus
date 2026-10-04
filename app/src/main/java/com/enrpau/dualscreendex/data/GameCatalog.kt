package com.enrpau.dualscreendex.data

import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.enrpau.dualscreendex.AppTheme
import com.enrpau.dualscreendex.BgPattern

/** One playable version, e.g. "Crystal". It decides the sprites and can recolour its game's theme. */
data class GameVersion(
    val id: String,
    val name: String,
    val spriteFolder: String,
    val animFolder: String? = null,               // idle animations, for the games that had them
    val always2D: Boolean = false,                // shows animated 2d sprites regardless of the 2d setting
    val tweak: (AppTheme) -> AppTheme = { it }
)

/** A game (one theme) with its versions, its own national dex and which alternate forms exist in it. */
data class GameFamily(
    val themeId: String,
    val name: String,
    val mechanics: RomProfile.Mechanics,
    val dexAsset: String,
    val formGroups: Set<String>,
    val versions: List<GameVersion>,
    val defaultVersion: Int = 0
)

object GameCatalog {
    private const val PREFS = "DualDexPrefs"
    private const val KEY_MATCH = "MATCH_DEX_TO_GAME"
    private const val KEY_VERSION = "GAME_VERSION_"
    private const val KEY_GAME_DEX_ONLY = "GAME_DEX_ONLY_"
    private const val KEY_USE_2D = "USE_2D_SPRITES"
    private const val KEY_GB_PALETTE = "GB_PALETTE"
    private const val KEY_LCD = "LCD_EFFECT"

    /** The 4-shade screen palettes emulators offer for original Game Boy games (darkest to lightest). */
    enum class GbPalette(val id: String, val label: String, val shades: List<String>?) {
        // screen colours measured from each model (as listed for emulators / Wikipedia's console palettes)
        DMG("dmg", "Game Boy", listOf("#102000", "#486828", "#80B050", "#B8F878")),
        POCKET("pocket", "Pocket (B&W)", listOf("#080808", "#585858", "#A8A8A8", "#F8F8F8")),
        LIGHT("light", "Game Boy Light", listOf("#080808", "#085858", "#08A8A8", "#08F8F8")),
        GBC("gbc", "Game Boy Color", null),   // the colours a GBC applies to this game (red / blue / yellow)
        // the game's own super game boy palette for the pokedex (PAL_MEWMON); pokemon get their own SGB colours
        SGB("sgb", "Super Game Boy", listOf("#181010", "#807098", "#F0B088", "#F8E8F8"))
    }

    // what a Game Boy Color shows for each original pokemon game: the boot ROM palettes
    // (red: FF8484/943A3A, blue: 63A5FF/0000FF, yellow: FFFF00/FF0000) passed through the GBC LCD
    // colour response (R = 26r+4g+2b, G = 24g+8b, B = 6r+4g+22b), which is what the screen really looked like
    private val gbcGamePalettes = mapOf(
        "red" to listOf("#000000", "#7F3848", "#E18096", "#F0F0F0"),
        "blue" to listOf("#000000", "#0F3EAA", "#71B6D0", "#F0F0F0"),
        // yellow is a native GBC game: black text on white, pikachu yellow and a dark gold
        "yellow" to listOf("#000000", "#A07000", "#F8D030", "#F8F8F8")
    )

    fun gbPalette(context: Context): GbPalette =
        GbPalette.entries.find { it.id == prefs(context).getString(KEY_GB_PALETTE, "dmg") } ?: GbPalette.DMG
    fun setGbPalette(context: Context, p: GbPalette) = prefs(context).edit { putString(KEY_GB_PALETTE, p.id) }

    /** The 4 shades (darkest first) for gen 1, following the chosen palette and version. */
    fun gbShades(context: Context): IntArray {
        val family = familyForTheme("red")!!
        val p = gbPalette(context)
        val hex = p.shades ?: gbcGamePalettes[version(context, family).id] ?: GbPalette.DMG.shades!!
        val shades = IntArray(4) { c(hex[it]) }
        // game boy color: the light tint (pokemon bodies) a little paler, closer to how the screen looked
        if (p == GbPalette.GBC) shades[2] = androidx.core.graphics.ColorUtils.blendARGB(shades[2], shades[3], 0.35f)
        return shades
    }

    /** Pokemon Yellow on a Game Boy Color: every pokemon in its own colours (the GBC-coloured yellow sprites). */
    fun isYellowOnGbc(context: Context): Boolean {
        val family = familyForTheme("red") ?: return false
        val p = gbPalette(context)
        // super game boy coloured every pokemon individually in red / blue / yellow too
        return p == GbPalette.SGB || (p == GbPalette.GBC && version(context, family).id == "yellow")
    }

    /** Screen texture (pixel grid / scanlines) for the handheld games. */
    fun isLcd(context: Context) = prefs(context).getBoolean(KEY_LCD, false)
    fun setLcd(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_LCD, on) }

    private fun c(hex: String) = hex.toColorInt()

    // version accents: header, frame (on modern panels) and buttons
    private fun accent(color: String, onColor: String = "#FFFFFF", bg: String? = null, pattern: String? = null): (AppTheme) -> AppTheme = { t ->
        val withBg = if (bg == null) t else t.copy(windowBackground = c(bg), contentBackground = c(bg), patternColor = c(pattern ?: bg))
        withBg.copy(headerColor = c(color), frameColor = c(color), fabColor = c(color), fabIconColor = c(onColor),
            headerTextColor = c(onColor))
    }


    val families: List<GameFamily> = listOf(
        // OLED: "the latest games" - national dex order (bulbasaur first, newest last), every form, animated 2d sprites
        GameFamily("dynamic", "Latest games · National Dex", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/national.csv",
            setOf("Alolan", "Galarian", "Hisuian", "Paldean", "Mega"), listOf(
                GameVersion("national", "National Dex", "gen6", always2D = true)
            )),
        GameFamily("red", "Red / Blue / Yellow", RomProfile.Mechanics.GEN_1, "dex/games/gen1.csv", emptySet(), listOf(
            GameVersion("red", "Red", "rb", animFolder = "crystal_anim"),
            GameVersion("blue", "Blue", "rb", animFolder = "crystal_anim"),
            GameVersion("yellow", "Yellow", "yellow", animFolder = "crystal_anim")
        )),
        GameFamily("oled", "Gold / Silver / Crystal", RomProfile.Mechanics.GEN_2_TO_5, "dex/games/gen2.csv", emptySet(), listOf(
            // every colour is on the GBC's RGB555 grid (multiples of 8)
            GameVersion("gold", "Gold", "gold", animFolder = "crystal_anim") { t ->
                t.copy(windowBackground = c("#B88820"), contentBackground = c("#B88820"), patternColor = c("#A87818"),
                    frameColor = c("#583808"), innerFrameColor = c("#E8C868"), fabIconColor = c("#583808"),
                    labelTextColor = c("#805810"), searchStrokeColor = c("#583808"))
            },
            GameVersion("silver", "Silver", "silver", animFolder = "crystal_anim") { t ->
                t.copy(windowBackground = c("#8890A0"), contentBackground = c("#8890A0"), patternColor = c("#788090"),
                    frameColor = c("#303848"), innerFrameColor = c("#C0C8D8"), fabIconColor = c("#303848"),
                    labelTextColor = c("#485068"), searchStrokeColor = c("#303848"))
            },
            GameVersion("crystal", "Crystal", "crystal", animFolder = "crystal_anim")
        ), defaultVersion = 2),
        GameFamily("emerald", "Ruby / Sapphire / Emerald", RomProfile.Mechanics.GEN_2_TO_5, "dex/games/gen3.csv", emptySet(), listOf(
            GameVersion("ruby", "Ruby", "rs", animFolder = "emerald_anim") { t ->
                t.copy(windowBackground = c("#D07070"), contentBackground = c("#D07070"), patternColor = c("#C06060"),
                    headerColor = c("#B83838"), frameColor = c("#782020"), innerFrameColor = c("#A04848"),
                    fabColor = c("#782020"), labelTextColor = c("#A03030"), searchStrokeColor = c("#782020"))
            },
            GameVersion("sapphire", "Sapphire", "rs", animFolder = "emerald_anim") { t ->
                t.copy(windowBackground = c("#6888D0"), contentBackground = c("#6888D0"), patternColor = c("#5878C0"),
                    headerColor = c("#3858B8"), frameColor = c("#203878"), innerFrameColor = c("#4060A0"),
                    fabColor = c("#203878"), labelTextColor = c("#3050A0"), searchStrokeColor = c("#203878"))
            },
            GameVersion("emerald", "Emerald", "emerald", animFolder = "emerald_anim")
        ), defaultVersion = 2),
        GameFamily("magical", "FireRed / LeafGreen", RomProfile.Mechanics.GEN_2_TO_5, "dex/games/frlg.csv", emptySet(), listOf(
            GameVersion("firered", "FireRed", "frlg", animFolder = "emerald_anim"),
            GameVersion("leafgreen", "LeafGreen", "frlg", animFolder = "emerald_anim") { t ->
                t.copy(headerColor = c("#48A048"), frameColor = c("#307830"), fabColor = c("#48A048"),
                    labelTextColor = c("#307830"), searchStrokeColor = c("#307830"),
                    windowBackground = c("#C0DCA0"), contentBackground = c("#C0DCA0"), patternColor = c("#B0D090"),
                    innerFrameColor = c("#98B880"))
            }
        )),
        GameFamily("lgpe", "Let's Go Pikachu / Eevee", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/lgpe.csv", setOf("Alolan", "Mega", "Partner"), listOf(
            GameVersion("pikachu", "Let's Go Pikachu", "gen6", tweak = accent("#F2C318", "#3A2A08")),
            GameVersion("eevee", "Let's Go Eevee", "gen6", tweak = accent("#B07A48", bg = "#F2E4CC", pattern = "#E8D6B8"))
        )),
        GameFamily("swsh", "Sword / Shield", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/swsh.csv", setOf("Alolan", "Galarian"), listOf(
            GameVersion("sword", "Sword", "gen6", tweak = accent("#0090D8", bg = "#13294A", pattern = "#183258")),
            GameVersion("shield", "Shield", "gen6", tweak = accent("#D0106E", bg = "#3A1232", pattern = "#451840"))
        )),
        GameFamily("bdsp", "Brilliant Diamond / Shining Pearl", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/bdsp.csv", emptySet(), listOf(
            GameVersion("bd", "Brilliant Diamond", "gen6", tweak = accent("#3A7EC0", bg = "#DDE9F7", pattern = "#CEDDF0")),
            GameVersion("sp", "Shining Pearl", "gen6", tweak = accent("#D06C94", bg = "#F7E1EB", pattern = "#EFD2DF"))
        )),
        GameFamily("pla", "Legends: Arceus", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/pla.csv", setOf("Hisuian"), listOf(
            GameVersion("pla", "Legends: Arceus", "gen6")
        )),
        GameFamily("sv", "Scarlet / Violet", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/sv.csv", setOf("Alolan", "Galarian", "Hisuian", "Paldean"), listOf(
            GameVersion("scarlet", "Scarlet", "gen6", tweak = accent("#E8481E", bg = "#241513", pattern = "#2C1A17")),
            GameVersion("violet", "Violet", "gen6", tweak = accent("#8A3CB4", bg = "#1B1426", pattern = "#231A31"))
        )),
        GameFamily("za", "Legends: Z-A", RomProfile.Mechanics.GEN_6_PLUS, "dex/games/za.csv", setOf("Mega"), listOf(
            GameVersion("za", "Legends: Z-A", "gen6")
        ))
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun familyForTheme(themeId: String): GameFamily? = families.find { it.themeId == themeId }

    fun version(context: Context, family: GameFamily): GameVersion {
        val id = prefs(context).getString(KEY_VERSION + family.themeId, null)
        return family.versions.find { it.id == id } ?: family.versions[family.defaultVersion]
    }

    fun setVersion(context: Context, family: GameFamily, version: GameVersion) =
        prefs(context).edit { putString(KEY_VERSION + family.themeId, version.id) }

    /** "Match dex to game": the selected game decides type chart, dex and sprites. */
    fun isMatchDex(context: Context) = prefs(context).getBoolean(KEY_MATCH, true)
    fun setMatchDex(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_MATCH, on) }

    /** Only list the pokemon that exist in this game (its own national dex) instead of all 1025. */
    fun isGameDexOnly(context: Context, family: GameFamily) = prefs(context).getBoolean(KEY_GAME_DEX_ONLY + family.themeId, true)
    fun setGameDexOnly(context: Context, family: GameFamily, on: Boolean) =
        prefs(context).edit { putBoolean(KEY_GAME_DEX_ONLY + family.themeId, on) }

    /** Switch-era games show 3D models by default; this swaps them for 2D pixel sprites. */
    fun isUse2D(context: Context) = prefs(context).getBoolean(KEY_USE_2D, false)
    fun setUse2D(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_USE_2D, on) }

    /**
     * Sprite set to use when "Match dex to game" is off, so a custom dex can still look like any game.
     * "auto" keeps the old behaviour (sprites follow the dex's generation).
     */
    val spriteSets = listOf(
        "auto" to "Automatic (the game's own)",
        "rb" to "Red / Blue", "yellow" to "Yellow",
        "gold" to "Gold", "silver" to "Silver", "crystal" to "Crystal",
        "rs" to "Ruby / Sapphire", "emerald" to "Emerald", "frlg" to "FireRed / LeafGreen",
        "dp" to "Diamond / Pearl", "pt" to "Platinum", "hgss" to "HeartGold / SoulSilver",
        "bw" to "Black / White (2D)", "gen6" to "3D models"
    )
    fun spriteSet(context: Context) = prefs(context).getString("SPRITE_SET", "auto") ?: "auto"
    fun setSpriteSet(context: Context, set: String) = prefs(context).edit { putString("SPRITE_SET", set) }
    /** The custom set in effect right now, or null when sprites follow the game / dex. */
    fun customSpriteSet(context: Context): String? = spriteSet(context).takeIf { it != "auto" }

    /** Checkerboard dithering when sprites are snapped to the Game Boy / Game Boy Color palettes. */
    fun isDither(context: Context) = prefs(context).getBoolean("DITHER", false)
    fun setDither(context: Context, on: Boolean) = prefs(context).edit { putBoolean("DITHER", on) }
    /** Game Boy Color themes: every sprite cut to 4 colours of its own, like real GBC sprites. */
    fun isFourColour(context: Context) = prefs(context).getBoolean("FOUR_COLOUR", false)
    fun setFourColour(context: Context, on: Boolean) = prefs(context).edit { putBoolean("FOUR_COLOUR", on) }

    /** Sprite animations (idle animations, animated models, idle bob). Off = still sprites, lighter on the GPU. */
    fun isAnimationsOn(context: Context) = prefs(context).getBoolean("SPRITE_ANIMATIONS", true)
    fun setAnimationsOn(context: Context, on: Boolean) = prefs(context).edit { putBoolean("SPRITE_ANIMATIONS", on) }

    fun is3DGame(family: GameFamily) = family.mechanics == RomProfile.Mechanics.GEN_6_PLUS

    /** The game behind the current theme (null for OLED). */
    fun currentFamily(): GameFamily? = familyForTheme(com.enrpau.dualscreendex.ThemeManager.currentTheme.id)

    fun currentVersion(context: Context): GameVersion? = currentFamily()?.let { version(context, it) }

    /** The game that is driving the dex right now, or null when the manual Dex Version is used. */
    fun activeFamily(context: Context): GameFamily? = if (isMatchDex(context)) currentFamily() else null

    /** "Crystal", "Sword"... or null. */
    fun activeName(context: Context): String? = activeFamily(context)?.let { version(context, it).name }

    /** Points the Dex Version at the built-in profile with the right type chart for the selected game. */
    fun syncProfile(context: Context) {
        val family = activeFamily(context) ?: return
        val profileId = when (family.mechanics) {
            RomProfile.Mechanics.GEN_1 -> "vanilla_retro"
            RomProfile.Mechanics.GEN_2_TO_5 -> "vanilla_classic"
            RomProfile.Mechanics.GEN_6_PLUS -> "vanilla_modern"
        }
        if (RomManager.currentProfile.id != profileId) {
            RomManager.getAllProfiles().find { it.id == profileId }?.let { RomManager.selectProfile(context, it) }
        }
    }

    /** One row of a game's dex: national id, which in-game dex it's in ("Hisui", "Isle of Armor"...) and its number there. */
    data class DexEntry(val id: Int, val dex: String?, val number: Int?)

    private val orderCache = HashMap<String, List<DexEntry>>()

    /** Every pokemon in the game, in the game's own order: its regional dex(es) first, then the rest by national number. */
    fun dexOrder(context: Context, family: GameFamily): List<DexEntry> = orderCache.getOrPut(family.dexAsset) {
        try {
            context.assets.open(family.dexAsset).bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val p = line.split(',')
                    val id = p.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                    DexEntry(id, p.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }, p.getOrNull(2)?.trim()?.toIntOrNull())
                }.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun speciesIds(context: Context, family: GameFamily): Set<Int> = dexOrder(context, family).map { it.id }.toSet()

    /** "Mega X" -> "Mega", "Paldean Combat Breed" -> "Paldean". */
    fun formGroup(label: String): String {
        val first = label.substringBefore(' ')
        return if (first == "Primal") "Mega" else first
    }
}
