package com.enrpau.dualscreendex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt

/** How "windows" (cards, list rows, panels) are drawn, imitating each generation's menu boxes. */
enum class BoxStyle {
    FLAT,     // modern: soft rounded card, no frame
    GB,       // gen 1-2: square text box with a thick outer line and a thin inner line
    GBA,      // gen 3: rounded panel with a frame and a hard drop shadow
    DS,       // gen 4: rounded framed panel with an inner highlight
    BW,       // gen 5 style: dark slab with a bright accent edge
    SWITCH    // switch era: clean rounded panel with a thin accent outline and soft shadow
}

enum class BgPattern { NONE, LCD, GRID, STRIPES, DOTS }

data class AppTheme(
    val id: String,
    val displayName: String,

    // global colors
    val windowBackground: Int,
    val contentBackground: Int,

    // text colors
    val listTextColor: Int,
    val labelTextColor: Int,

    // search bar
    val searchBoxColor: Int,
    val searchTextColor: Int,
    val searchHintColor: Int,
    val searchCornerRadius: Float,
    val searchMarginHorizontal: Int,
    val searchStrokeColor: Int,
    val searchStrokeWidth: Int,

    // header & content
    val headerTextColor: Int,
    val subTextColor: Int,
    val gridBackgroundColor: Int,
    val cardCornerRadius: Float,

    // game look
    val boxStyle: BoxStyle = BoxStyle.FLAT,
    val frameColor: Int = Color.TRANSPARENT,     // box outline
    val innerFrameColor: Int = Color.TRANSPARENT, // GB inner line / DS highlight / GBA shadow
    val headerColor: Int = Color.TRANSPARENT,    // battle card header panel
    val pattern: BgPattern = BgPattern.NONE,
    val patternColor: Int = Color.TRANSPARENT,
    val retroFont: Boolean = false,
    val typeTintedHeader: Boolean = false,        // modern: header takes the pokemon's type colour
    val isDark: Boolean = false,
    val fabColor: Int = Color.WHITE,
    val fabIconColor: Int = Color.BLACK,
    val screenTextColor: Int = Color.WHITE,       // text drawn straight on the background
    val monochrome: Boolean = false,             // DMG game boy: everything in 4 shades of green
    val gbc15Bit: Boolean = false,               // game boy color: every colour snapped to the 15-bit RGB555 grid
    val pixelFont: Boolean = false               // gen 1-2: real 8x8 pixel text
) {
    val isRetroScreen get() = boxStyle != BoxStyle.FLAT
}

object ThemeManager {

    val allThemes: List<AppTheme> by lazy { buildThemes() }

    /** Sprites keep their own colours (pokemon yellow on a game boy color); the UI still uses the 4 shades. */
    var nativeColorSprites = false
        private set

    var currentTheme: AppTheme = allThemes.first()

    fun loadTheme(context: Context) {
        val prefs = context.getSharedPreferences("DualDexPrefs", Context.MODE_PRIVATE)
        val themeId = prefs.getString("SELECTED_THEME_ID", "dynamic") ?: "dynamic"
        val base = allThemes.find { it.id == themeId } ?: allThemes.first()
        if (base.monochrome) dmg = com.enrpau.dualscreendex.data.GameCatalog.gbShades(context)
        nativeColorSprites = base.monochrome && com.enrpau.dualscreendex.data.GameCatalog.isYellowOnGbc(context)
        currentTheme = withVersion(context, base)
        dithering = com.enrpau.dualscreendex.data.GameCatalog.isDither(context)
        fourColour = com.enrpau.dualscreendex.data.GameCatalog.isFourColour(context)
    }

    /** Repaints the game boy theme in one 4-shade palette: p[0] darkest .. p[3] lightest. */
    private fun recolorGb(t: AppTheme, p: IntArray): AppTheme = t.copy(
        windowBackground = p[3], contentBackground = p[3], patternColor = p[3],
        listTextColor = p[0], labelTextColor = p[1],
        searchBoxColor = p[3], searchTextColor = p[0], searchHintColor = p[1], searchStrokeColor = p[0],
        headerTextColor = p[0], subTextColor = p[1], gridBackgroundColor = p[3],
        frameColor = p[0], innerFrameColor = p[1], headerColor = p[3],
        fabColor = p[0], fabIconColor = p[3], screenTextColor = p[0]
    )

    /** LCD lines laid over the whole screen (handheld games only): see-through, a faint dark line every few pixels. */
    fun lcdOverlay(context: Context, theme: AppTheme = currentTheme): Drawable? {
        if (theme.id !in setOf("red", "oled", "magical", "emerald")) return null
        val d = context.resources.displayMetrics.density
        val size = (3 * d).toInt().coerceAtLeast(3)
        val bmp = createBitmap(1, size)
        Canvas(bmp).drawRect(0f, size - d.coerceAtLeast(1f), 1f, size.toFloat(),
            Paint().apply { color = Color.argb(30, 0, 0, 0) })
        return bmp.toDrawable(context.resources).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            isFilterBitmap = false
        }
    }

    /** The screen background (the LCD effect now sits on top of everything instead). */
    @Suppress("UNUSED_PARAMETER")
    fun screenBackground(context: Context): Drawable = backgroundDrawable(context)

    /** Full screen: hides the status and navigation bars (swipe from an edge to show them briefly). */
    fun hideNavigationBar(window: android.view.Window) {
        // use the whole panel, including any camera-hole area at the top edge
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    /** Puts the LCD lines over the whole window when the effect is on. */
    fun applyLcd(window: android.view.Window) {
        val ctx = window.context
        window.decorView.foreground =
            if (com.enrpau.dualscreendex.data.GameCatalog.isLcd(ctx)) lcdOverlay(ctx) else null
    }

    /** Applies the selected version's colours (Gold vs Silver, Sword vs Shield...) to a game's theme. */
    fun withVersion(context: Context, base: AppTheme): AppTheme {
        val family = com.enrpau.dualscreendex.data.GameCatalog.familyForTheme(base.id) ?: return base
        val version = com.enrpau.dualscreendex.data.GameCatalog.version(context, family)
        val tinted = if (base.monochrome) recolorGb(base, com.enrpau.dualscreendex.data.GameCatalog.gbShades(context)) else base
        return version.tweak(tinted).copy(displayName = version.name)
    }

    private fun c(hex: String) = hex.toColorInt()

    // ids "dynamic", "oled", "red", "magical" are kept from older versions so saved choices still work
    private fun buildThemes() = listOf(
        AppTheme(
            id = "dynamic", displayName = "OLED",
            windowBackground = Color.BLACK, contentBackground = Color.BLACK,
            listTextColor = c("#F5F6F7"), labelTextColor = c("#8A8F98"),
            searchBoxColor = c("#121417"), searchTextColor = c("#F5F6F7"), searchHintColor = c("#6C717A"),
            searchCornerRadius = 28f, searchMarginHorizontal = 16, searchStrokeColor = Color.TRANSPARENT, searchStrokeWidth = 0,
            headerTextColor = Color.WHITE, subTextColor = c("#C7CAD1"),
            gridBackgroundColor = c("#0E0F12"), cardCornerRadius = 24f,
            typeTintedHeader = true, isDark = true,
            fabColor = Color.WHITE, fabIconColor = Color.BLACK
        ),
        // original Game Boy (DMG) 4-shade green screen, gen 1 double-line text boxes
        AppTheme(
            id = "red", displayName = "Red / Blue / Yellow",
            windowBackground = c("#8BAC0F"), contentBackground = c("#8BAC0F"),
            listTextColor = c("#0F380F"), labelTextColor = c("#306230"),
            searchBoxColor = c("#9BBC0F"), searchTextColor = c("#0F380F"), searchHintColor = c("#306230"),
            searchCornerRadius = 0f, searchMarginHorizontal = 16, searchStrokeColor = c("#0F380F"), searchStrokeWidth = 4,
            headerTextColor = c("#0F380F"), subTextColor = c("#306230"),
            gridBackgroundColor = c("#9BBC0F"), cardCornerRadius = 0f,
            boxStyle = BoxStyle.GB, frameColor = c("#0F380F"), innerFrameColor = c("#306230"),
            headerColor = c("#9BBC0F"), pattern = BgPattern.NONE, patternColor = c("#8BAC0F"),
            retroFont = true, pixelFont = true, monochrome = true, fabColor = c("#0F380F"), fabIconColor = c("#9BBC0F"), screenTextColor = c("#0F380F")
        ),
        // Game Boy Color: white menus with Crystal's navy frame over a blue tiled backdrop
        AppTheme(
            id = "oled", displayName = "Gold / Silver / Crystal",
            windowBackground = c("#3058A8"), contentBackground = c("#3058A8"),
            listTextColor = c("#181818"), labelTextColor = c("#3058A8"),
            searchBoxColor = c("#F8F8F8"), searchTextColor = c("#181818"), searchHintColor = c("#3058A8"),
            searchCornerRadius = 0f, searchMarginHorizontal = 16, searchStrokeColor = c("#102048"), searchStrokeWidth = 4,
            headerTextColor = c("#181818"), subTextColor = c("#3058A8"),
            gridBackgroundColor = c("#F8F8F8"), cardCornerRadius = 0f,
            boxStyle = BoxStyle.GB, frameColor = c("#102048"), innerFrameColor = c("#88A8E8"),
            headerColor = c("#F8F8F8"), pattern = BgPattern.DOTS, patternColor = c("#284898"),
            retroFont = true, pixelFont = true, gbc15Bit = true, fabColor = c("#F8F8F8"), fabIconColor = c("#102048"), screenTextColor = c("#F8F8F8")
        ),
        // FireRed / LeafGreen Pokédex: red-orange header, cream striped body, white rounded panels
        AppTheme(
            id = "magical", displayName = "FireRed / LeafGreen",
            windowBackground = c("#F0D890"), contentBackground = c("#F0D890"),
            listTextColor = c("#383838"), labelTextColor = c("#A04018"),
            searchBoxColor = c("#F8F8F0"), searchTextColor = c("#383838"), searchHintColor = c("#988870"),
            searchCornerRadius = 12f, searchMarginHorizontal = 16, searchStrokeColor = c("#C85028"), searchStrokeWidth = 4,
            headerTextColor = Color.WHITE, subTextColor = c("#FFE0C8"),
            gridBackgroundColor = c("#F8F8F0"), cardCornerRadius = 12f,
            boxStyle = BoxStyle.GBA, frameColor = c("#C85028"), innerFrameColor = c("#B8A070"),
            headerColor = c("#E05830"), pattern = BgPattern.STRIPES, patternColor = c("#E8CC80"),
            retroFont = true, fabColor = c("#E05830"), fabIconColor = Color.WHITE, screenTextColor = c("#383838")
        ),
        // Emerald: green menus with white panels
        AppTheme(
            id = "emerald", displayName = "Ruby / Sapphire / Emerald",
            windowBackground = c("#68B888"), contentBackground = c("#68B888"),
            listTextColor = c("#303030"), labelTextColor = c("#2E6B45"),
            searchBoxColor = c("#F8F8F8"), searchTextColor = c("#303030"), searchHintColor = c("#709080"),
            searchCornerRadius = 12f, searchMarginHorizontal = 16, searchStrokeColor = c("#2E6B45"), searchStrokeWidth = 4,
            headerTextColor = Color.WHITE, subTextColor = c("#D8F8E0"),
            gridBackgroundColor = c("#F8F8F8"), cardCornerRadius = 12f,
            boxStyle = BoxStyle.GBA, frameColor = c("#2E6B45"), innerFrameColor = c("#3E8858"),
            headerColor = c("#3A9060"), pattern = BgPattern.STRIPES, patternColor = c("#5EAE7E"),
            retroFont = true, fabColor = c("#2E6B45"), fabIconColor = Color.WHITE, screenTextColor = c("#F8F8F8")
        ),
        // ---- switch era: clean white panels, each version recolours the accent ----
        AppTheme(
            id = "lgpe", displayName = "Let's Go Pikachu / Eevee",
            windowBackground = c("#FFF3C4"), contentBackground = c("#FFF3C4"),
            listTextColor = c("#3A3428"), labelTextColor = c("#9A7418"),
            searchBoxColor = Color.WHITE, searchTextColor = c("#3A3428"), searchHintColor = c("#A89C80"),
            searchCornerRadius = 40f, searchMarginHorizontal = 16, searchStrokeColor = c("#F2C318"), searchStrokeWidth = 3,
            headerTextColor = c("#3A2A08"), subTextColor = c("#6A5420"),
            gridBackgroundColor = Color.WHITE, cardCornerRadius = 20f,
            boxStyle = BoxStyle.SWITCH, frameColor = c("#F2C318"), innerFrameColor = c("#E8D9A0"),
            headerColor = c("#F2C318"), pattern = BgPattern.DOTS, patternColor = c("#FBE9A8"),
            fabColor = c("#F2C318"), fabIconColor = c("#3A2A08"), screenTextColor = c("#3A3428")
        ),
        AppTheme(
            id = "swsh", displayName = "Sword / Shield",
            windowBackground = c("#1B2140"), contentBackground = c("#1B2140"),
            listTextColor = c("#1B2140"), labelTextColor = c("#5A6290"),
            searchBoxColor = c("#F3F5FA"), searchTextColor = c("#1B2140"), searchHintColor = c("#8890B0"),
            searchCornerRadius = 12f, searchMarginHorizontal = 16, searchStrokeColor = c("#0090D8"), searchStrokeWidth = 3,
            headerTextColor = Color.WHITE, subTextColor = c("#D8E4FF"),
            gridBackgroundColor = c("#F3F5FA"), cardCornerRadius = 12f,
            boxStyle = BoxStyle.SWITCH, frameColor = c("#0090D8"), innerFrameColor = c("#0E1228"),
            headerColor = c("#0090D8"), pattern = BgPattern.STRIPES, patternColor = c("#1F2648"),
            isDark = true, fabColor = c("#0090D8"), fabIconColor = Color.WHITE, screenTextColor = Color.WHITE
        ),
        AppTheme(
            id = "bdsp", displayName = "Brilliant Diamond / Shining Pearl",
            windowBackground = c("#E4ECF4"), contentBackground = c("#E4ECF4"),
            listTextColor = c("#28323E"), labelTextColor = c("#5A6A7E"),
            searchBoxColor = Color.WHITE, searchTextColor = c("#28323E"), searchHintColor = c("#8A98A8"),
            searchCornerRadius = 16f, searchMarginHorizontal = 16, searchStrokeColor = c("#3A7EC0"), searchStrokeWidth = 3,
            headerTextColor = Color.WHITE, subTextColor = c("#E0ECF8"),
            gridBackgroundColor = Color.WHITE, cardCornerRadius = 16f,
            boxStyle = BoxStyle.SWITCH, frameColor = c("#3A7EC0"), innerFrameColor = c("#C8D4E2"),
            headerColor = c("#3A7EC0"), pattern = BgPattern.GRID, patternColor = c("#D6E0EC"),
            fabColor = c("#3A7EC0"), fabIconColor = Color.WHITE, screenTextColor = c("#28323E")
        ),
        // Legends Arceus: survey-corps parchment and ink
        AppTheme(
            id = "pla", displayName = "Legends: Arceus",
            windowBackground = c("#E6D9B8"), contentBackground = c("#E6D9B8"),
            listTextColor = c("#3B3020"), labelTextColor = c("#7A5A2A"),
            searchBoxColor = c("#F4ECD6"), searchTextColor = c("#3B3020"), searchHintColor = c("#9A8A68"),
            searchCornerRadius = 4f, searchMarginHorizontal = 16, searchStrokeColor = c("#3B3020"), searchStrokeWidth = 3,
            headerTextColor = c("#F4ECD6"), subTextColor = c("#D8C8A0"),
            gridBackgroundColor = c("#F4ECD6"), cardCornerRadius = 4f,
            boxStyle = BoxStyle.GB, frameColor = c("#3B3020"), innerFrameColor = c("#A08A60"),
            headerColor = c("#3B3020"), pattern = BgPattern.STRIPES, patternColor = c("#E0D2AE"),
            fabColor = c("#3B3020"), fabIconColor = c("#F4ECD6"), screenTextColor = c("#3B3020")
        ),
        AppTheme(
            id = "sv", displayName = "Scarlet / Violet",
            windowBackground = c("#16161A"), contentBackground = c("#16161A"),
            listTextColor = c("#F4F4F6"), labelTextColor = c("#B8B8C4"),
            searchBoxColor = c("#26262C"), searchTextColor = c("#F4F4F6"), searchHintColor = c("#80808C"),
            searchCornerRadius = 40f, searchMarginHorizontal = 16, searchStrokeColor = c("#E8481E"), searchStrokeWidth = 3,
            headerTextColor = Color.WHITE, subTextColor = c("#FFE0D8"),
            gridBackgroundColor = c("#26262C"), cardCornerRadius = 20f,
            boxStyle = BoxStyle.BW, frameColor = c("#E8481E"), innerFrameColor = c("#0E0E10"),
            headerColor = c("#E8481E"), pattern = BgPattern.DOTS, patternColor = c("#1C1C21"),
            isDark = true, fabColor = c("#E8481E"), fabIconColor = Color.WHITE, screenTextColor = c("#F4F4F6")
        ),
        // Legends Z-A: Lumiose night, black and neon lime
        AppTheme(
            id = "za", displayName = "Legends: Z-A",
            windowBackground = c("#0B0C0D"), contentBackground = c("#0B0C0D"),
            listTextColor = c("#F2F4F0"), labelTextColor = c("#B8F000"),
            searchBoxColor = c("#17191B"), searchTextColor = c("#F2F4F0"), searchHintColor = c("#70786A"),
            searchCornerRadius = 2f, searchMarginHorizontal = 16, searchStrokeColor = c("#B8F000"), searchStrokeWidth = 3,
            headerTextColor = c("#0B0C0D"), subTextColor = c("#2A3000"),
            gridBackgroundColor = c("#17191B"), cardCornerRadius = 2f,
            boxStyle = BoxStyle.BW, frameColor = c("#B8F000"), innerFrameColor = c("#000000"),
            headerColor = c("#B8F000"), pattern = BgPattern.GRID, patternColor = c("#131517"),
            isDark = true, fabColor = c("#B8F000"), fabIconColor = c("#0B0C0D"), screenTextColor = c("#F2F4F0")
        )
    )
    // ---------- 4-shade game boy palette ----------

    // the 4 game boy shades in use (darkest first); follows the chosen screen palette
    private var dmg = intArrayOf(c("#0F380F"), c("#306230"), c("#8BAC0F"), c("#9BBC0F"))

    /** Badge background for a type colour (unchanged unless the theme is monochrome). */
    fun badgeBg(color: Int, darker: Boolean = false, theme: AppTheme = currentTheme): Int = when {
        theme.monochrome -> if (darker) dmg[0] else dmg[1]
        theme.gbc15Bit -> gbc(color)
        else -> color
    }

    // ---------- game boy color: one fixed palette, so the screen never shows more than 56 colours ----------

    private val gbcUiColors = listOf(
        // crystal
        "#F8F8F8", "#181818", "#102048", "#3058A8", "#284898", "#88A8E8", "#3058A0", "#405080", "#6878A0",
        // gold / silver versions
        "#B88820", "#A87818", "#583808", "#E8C868", "#805810", "#8890A0", "#788090", "#303848", "#C0C8D8", "#485068"
    )

    /** 19 UI colours + 18 type colours + 18 light type tints = 55, all on the RGB555 grid. */
    val gbcPalette: IntArray by lazy {
        val set = LinkedHashSet<Int>()
        gbcUiColors.forEach { set += c(it) }
        val types = PokemonType.entries.filter { it != PokemonType.UNKNOWN }
        types.forEach { set += rgb555(it.colorHex) }
        types.forEach { set += rgb555(ColorUtils.blendARGB(it.colorHex, c("#F8F8F8"), 0.5f)) }
        set.take(56).toIntArray()
    }

    /** Colours the GBC list rows draw besides their sprites. */
    fun gbcRowColors(): List<Int> {
        val t = currentTheme
        val set = LinkedHashSet<Int>()
        listOf(t.gridBackgroundColor, t.frameColor, t.innerFrameColor, t.listTextColor, t.labelTextColor).forEach { set += gbc(it) }
        PokemonType.entries.filter { it != PokemonType.UNKNOWN }.forEach { set += gbc(it.colorHex) }
        return set.toList()
    }

    /** Snaps any colour to the GBC palette; see-through colours are first blended onto [over]. */
    fun gbc(color: Int, over: Int = currentTheme.gridBackgroundColor): Int {
        val solid = if (Color.alpha(color) >= 255) color
            else ColorUtils.compositeColors(color, over or 0xFF000000.toInt())
        var best = gbcPalette[0]
        var bestD = Int.MAX_VALUE
        for (p in gbcPalette) {
            val dr = Color.red(p) - Color.red(solid)
            val dg = Color.green(p) - Color.green(solid)
            val db = Color.blue(p) - Color.blue(solid)
            val d = 3 * dr * dr + 4 * dg * dg + 2 * db * db
            if (d < bestD) { bestD = d; best = p }
        }
        return best
    }

    /** True for the themes limited to a hardware palette (DMG 4 greens, GBC 56 colours). */
    val isLimitedPalette get() = currentTheme.monochrome || currentTheme.gbc15Bit

    /** Nearest of the 4 DMG greens (for UI colours). */
    private fun dmgShade(color: Int): Int = nearest(color, dmg)

    /** Sprites: split by brightness into 4 bands so outline, shadow, mid-tone and highlight stay separate. */
    private fun dmgSpriteShade(color: Int): Int {
        val l = ColorUtils.calculateLuminance(color)
        return when { l < 0.05 -> dmg[0]; l < 0.14 -> dmg[1]; l < 0.82 -> dmg[2]; else -> dmg[3] }
    }

    private fun nearest(color: Int, palette: IntArray): Int {
        var best = palette[0]
        var bestD = Int.MAX_VALUE
        for (p in palette) {
            val dr = Color.red(p) - Color.red(color)
            val dg = Color.green(p) - Color.green(color)
            val db = Color.blue(p) - Color.blue(color)
            val d = 3 * dr * dr + 4 * dg * dg + 2 * db * db
            if (d < bestD) { bestD = d; best = p }
        }
        return best
    }

    // snaps every pixel a view draws to the hardware palette (android 13+), so edges, icons and shadows can't add colours
    private const val PALETTE_SHADER = """
        uniform shader content;
        uniform float4 pal[56];
        uniform float count;
        half4 main(float2 p) {
            half4 c = content.eval(p);
            if (c.a < 0.5) return half4(0.0);
            float3 rgb = float3(c.rgb) / float(c.a);
            float best = 1.0e9;
            float3 pick = pal[0].rgb;
            for (int i = 0; i < 56; i++) {
                if (float(i) < count) {
                    float3 d = rgb - pal[i].rgb;
                    float dd = 3.0 * d.r * d.r + 4.0 * d.g * d.g + 2.0 * d.b * d.b;
                    if (dd < best) { best = dd; pick = pal[i].rgb; }
                }
            }
            return half4(half3(pick), 1.0);
        }
    """

    /** Locks [view] (and everything inside it) to the theme's hardware palette; clears it on full-colour themes. */
    fun applyPaletteEffect(view: View, palette: IntArray? = null) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val t = currentTheme
        if (!t.monochrome && !t.gbc15Bit) { view.setRenderEffect(null); return }
        val colors = (palette ?: if (t.monochrome) dmg else gbcPalette).take(56)
        val arr = FloatArray(56 * 4)
        colors.forEachIndexed { i, col ->
            arr[i * 4] = Color.red(col) / 255f
            arr[i * 4 + 1] = Color.green(col) / 255f
            arr[i * 4 + 2] = Color.blue(col) / 255f
            arr[i * 4 + 3] = 1f
        }
        val shader = android.graphics.RuntimeShader(PALETTE_SHADER)
        shader.setFloatUniform("pal", arr)
        shader.setFloatUniform("count", colors.size.toFloat())
        view.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"))
    }

    /** Any UI colour: unchanged, except on the limited-palette themes where it snaps to the hardware palette. */
    fun ui(color: Int, over: Int = currentTheme.gridBackgroundColor): Int {
        val t = currentTheme
        if (!t.monochrome && !t.gbc15Bit) return color
        val solid = if (Color.alpha(color) >= 255) color else ColorUtils.compositeColors(color, over or 0xFF000000.toInt())
        return if (t.monochrome) dmgShade(solid) else gbc(solid)
    }

    /** Recolours a sprite into the theme's hardware palette (no-op for full-colour themes). Half-transparent edges become hard. */
    fun quantizeBitmap(src: Bitmap, shared: List<Int>? = null): Bitmap {
        val t = currentTheme
        if (!t.monochrome && !t.gbc15Bit) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        // gbc-style: every sprite boils down to 4 colours of its own (black, white and its two main colours)
        if (fourColour && t.gbc15Bit) return fourColourSprite(px, w, h, shared)
        val memo = HashMap<Long, Int>()
        // optional ordered dithering (the checkerboard blends GBC-style rom hacks use): each pixel is nudged
        // by a 4x4 Bayer pattern before snapping, so in-between colours become a fine mix of two palette colours
        val spread = if (!dithering) 0 else if (t.monochrome) 48 else 28
        for (i in px.indices) {
            val p = px[i]
            if (Color.alpha(p) < 128) { px[i] = 0; continue }
            val bias = if (spread == 0) 0 else (BAYER[(i / w % 4) * 4 + i % w % 4] * spread / 16) - spread / 2
            val key = ((p or 0xFF000000.toInt()).toLong() shl 8) or (bias + 128).toLong()
            px[i] = memo.getOrPut(key) {
                val c = if (bias == 0) p or 0xFF000000.toInt() else Color.rgb(
                    (Color.red(p) + bias).coerceIn(0, 255), (Color.green(p) + bias).coerceIn(0, 255), (Color.blue(p) + bias).coerceIn(0, 255))
                if (t.monochrome) dmgSpriteShade(c) else gbc(c)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** "Dithering" setting, read by [quantizeBitmap]. */
    var dithering = false
    /** "4-colour sprites" setting (Game Boy Color themes), read by [quantizeBitmap]. */
    var fourColour = false

    private fun dist(a: Int, b: Int): Int {
        val dr = Color.red(a) - Color.red(b); val dg = Color.green(a) - Color.green(b); val db = Color.blue(a) - Color.blue(b)
        return 3 * dr * dr + 4 * dg * dg + 2 * db * db
    }

    /**
     * Like a real Game Boy Color sprite: the darkest and lightest colours stay as outline and highlight, and the
     * rest of the sprite is grouped (k-means) into its two main colours. With "Dithering" on, pixels between two
     * of those colours become a checkerboard of both. The 4 colours are then snapped to the GBC palette.
     */
    /**
     * An animation's frames, recoloured together: in 4-colour mode every frame uses the same 4 colours
     * (picked from all frames at once), so the sprite doesn't change colour from frame to frame.
     */
    fun quantizeFrames(frames: List<Bitmap>): List<Bitmap> {
        val shared = if (fourColour && currentTheme.gbc15Bit) fourColourPalette(frames.flatMap { b ->
            val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
            px.filter { Color.alpha(it) >= 128 }.map { it or 0xFF000000.toInt() }
        }) else null
        return frames.map { quantizeBitmap(it, shared) }
    }

    private fun fourColourSprite(px: IntArray, w: Int, h: Int, shared: List<Int>? = null): Bitmap {
        val opaque = px.filter { Color.alpha(it) >= 128 }.map { it or 0xFF000000.toInt() }
        if (opaque.isEmpty()) return Bitmap.createBitmap(px.map { 0 }.toIntArray(), w, h, Bitmap.Config.ARGB_8888)
        val palette = shared ?: fourColourPalette(opaque)
        return mapToPalette(px, w, h, palette)
    }

    /** The 4 colours for a sprite (or a whole animation): outline, two main colours, highlight. */
    private fun fourColourPalette(opaque: List<Int>): List<Int> {
        if (opaque.isEmpty()) return listOf(Color.BLACK, Color.DKGRAY, Color.LTGRAY, Color.WHITE)
        fun lum(c: Int) = Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)
        val dark = opaque.minByOrNull { lum(it) }!!
        val light = opaque.maxByOrNull { lum(it) }!!
        // two mid colours: the sprite's most used colour, then the most used one that differs clearly from it.
        // Real colours (not averages), so a green-and-pink sprite stays green and pink instead of turning brown.
        val counts = HashMap<Int, Int>()
        for (c in opaque) if (dist(c, dark) > 2000 && dist(c, light) > 2000) {
            val g = gbc(c); counts[g] = (counts[g] ?: 0) + 1
        }
        val byUse = counts.entries.sortedByDescending { it.value }.map { it.key }
        val first = byUse.firstOrNull() ?: gbc(opaque[opaque.size / 2])
        val second = byUse.firstOrNull { dist(it, first) > 6000 } ?: byUse.getOrNull(1) ?: first
        val centers = intArrayOf(first, second)
        return intArrayOf(dark, centers[0], centers[1], light).map { gbc(it) }
    }

    private fun mapToPalette(px: IntArray, w: Int, h: Int, palette: List<Int>): Bitmap {
        val out = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            if (Color.alpha(p) < 128) continue
            val c = p or 0xFF000000.toInt()
            // nearest and second-nearest of the 4 colours
            var b1 = 0; var b2 = 1
            var d1 = Int.MAX_VALUE; var d2 = Int.MAX_VALUE
            for (k in palette.indices) {
                val d = dist(c, palette[k])
                if (d < d1) { d2 = d1; b2 = b1; d1 = d; b1 = k } else if (d < d2) { d2 = d; b2 = k }
            }
            out[i] = if (dithering && d1 + d2 > 0) {
                // the closer to halfway between the two colours, the more pixels take the second one
                val share = d1.toFloat() / (d1 + d2)
                if (BAYER[(i / w % 4) * 4 + i % w % 4] / 16f < share * 0.9f) palette[b2] else palette[b1]
            } else palette[b1]
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /**
     * A rounded rectangle with hard (non-smoothed) edges, so limited-palette themes don't gain in-between colours.
     * Other themes get a normal smooth GradientDrawable.
     */
    fun shape(context: Context, fill: Int, radiusDp: Float, strokeDp: Float = 0f, stroke: Int = Color.TRANSPARENT): Drawable {
        val d = context.resources.displayMetrics.density
        if (!isLimitedPalette) return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusDp * d
            if (strokeDp > 0) setStroke((strokeDp * d).toInt(), stroke)
        }
        return HardRoundRect(ui(fill), radiusDp * d, strokeDp * d, ui(stroke))
    }

    private class HardRoundRect(val fill: Int, val radius: Float, val strokeW: Float, val stroke: Int) : Drawable() {
        private val paint = Paint().apply { isAntiAlias = false }
        override fun draw(canvas: Canvas) {
            val r = android.graphics.RectF(bounds)
            val rad = radius.coerceAtMost(minOf(r.width(), r.height()) / 2f)
            if (Color.alpha(fill) > 0) {
                paint.style = Paint.Style.FILL
                paint.color = fill
                canvas.drawRoundRect(r, rad, rad, paint)
            }
            if (strokeW > 0 && Color.alpha(stroke) > 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokeW
                paint.color = stroke
                val half = strokeW / 2f
                r.inset(half, half)
                canvas.drawRoundRect(r, (rad - half).coerceAtLeast(0f), (rad - half).coerceAtLeast(0f), paint)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Text drawn on a badge. */
    fun badgeText(theme: AppTheme = currentTheme): Int = when {
        theme.monochrome -> dmg[3]
        theme.gbc15Bit -> c("#F8F8F8") // GBC white is 31,31,31 -> 248
        else -> Color.WHITE
    }

    /** Snaps a colour to the Game Boy Color's 5-bits-per-channel palette (32 levels: 0, 8 ... 248). */
    fun rgb555(color: Int): Int =
        Color.argb(Color.alpha(color), Color.red(color) and 0xF8, Color.green(color) and 0xF8, Color.blue(color) and 0xF8)

    /** Maps any colour to the nearest shade by brightness (used for stat bars etc.). */
    fun shade(color: Int, theme: AppTheme = currentTheme): Int {
        if (!theme.monochrome) return color
        val l = ColorUtils.calculateLuminance(color)
        return when { l < 0.12 -> dmg[0]; l < 0.3 -> dmg[1]; l < 0.55 -> dmg[2]; else -> dmg[3] }
    }

    /** No tint: limited-palette themes recolour sprites per pixel instead (see [quantizeBitmap]). */
    @Suppress("UNUSED_PARAMETER")
    fun spriteFilter(theme: AppTheme = currentTheme): android.graphics.ColorFilter? = null

    // ---------- drawing helpers ----------

    /** A game-style window. [fill] defaults to the theme's panel colour. */
    fun boxDrawable(
        context: Context,
        fill: Int = currentTheme.gridBackgroundColor,
        theme: AppTheme = currentTheme,
        fillColors: IntArray? = null,      // a gradient fill instead of [fill]
        fillOffsets: FloatArray? = null    // hard colour bands (game boy color)
    ): Drawable {
        val d = context.resources.displayMetrics.density
        fun rect(color: Int, radius: Float, strokeW: Float = 0f, strokeColor: Int = Color.TRANSPARENT) =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius * d
                if (strokeW > 0) setStroke((strokeW * d).toInt(), strokeColor)
            }
        // the main panel of a box: solid, or a diagonal gradient when [fillColors] is given
        fun panel(radius: Float, strokeW: Float = 0f, strokeColor: Int = Color.TRANSPARENT) =
            if (fillColors == null) rect(fill, radius, strokeW, strokeColor)
            else GradientDrawable(GradientDrawable.Orientation.TL_BR, fillColors).apply {
                if (fillOffsets != null) setColors(fillColors, fillOffsets)
                cornerRadius = radius * d
                if (strokeW > 0) setStroke((strokeW * d).toInt(), strokeColor)
            }

        return when (theme.boxStyle) {
            BoxStyle.FLAT -> panel(theme.cardCornerRadius / d * 1f)
            BoxStyle.GB -> LayerDrawable(arrayOf(
                if (fillColors == null && (theme.monochrome || theme.gbc15Bit))
                    HardRoundRect(fill, 9f * d, 3f * d, theme.frameColor)
                else panel(9f, 3f, theme.frameColor),                 // rounded, like the games' text boxes
                if (theme.monochrome || theme.gbc15Bit) HardRoundRect(Color.TRANSPARENT, 5f * d, 1f * d, theme.innerFrameColor)
                else rect(Color.TRANSPARENT, 5f, 1f, theme.innerFrameColor)
            )).apply { setLayerInset(1, (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt()) }
            BoxStyle.GBA -> LayerDrawable(arrayOf(
                rect(theme.innerFrameColor, 8f),               // hard shadow
                panel(8f, 2.5f, theme.frameColor)
            )).apply {
                setLayerInset(0, (3 * d).toInt(), (3 * d).toInt(), 0, 0)
                setLayerInset(1, 0, 0, (3 * d).toInt(), (3 * d).toInt())
            }
            BoxStyle.DS -> LayerDrawable(arrayOf(
                panel(10f, 2f, theme.frameColor),
                rect(Color.TRANSPARENT, 8f, 1.5f, ColorUtils.setAlphaComponent(theme.innerFrameColor, 170))
            )).apply { setLayerInset(1, (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt()) }
            BoxStyle.BW -> LayerDrawable(arrayOf(
                rect(theme.frameColor, 6f),
                panel(5f)
            )).apply { setLayerInset(1, (4 * d).toInt(), 0, 0, (2 * d).toInt()) } // accent edge on the left + bottom
            BoxStyle.SWITCH -> LayerDrawable(arrayOf(
                rect(ColorUtils.setAlphaComponent(theme.innerFrameColor, 140), 14f), // soft drop shadow
                panel(14f, 1.5f, ColorUtils.setAlphaComponent(theme.frameColor, 200))
            )).apply {
                setLayerInset(0, 0, (3 * d).toInt(), 0, 0)
                setLayerInset(1, 0, 0, 0, (3 * d).toInt())
            }
        }
    }

    /** Full-screen background: flat colour plus the generation's tiled pattern. */
    fun backgroundDrawable(context: Context, theme: AppTheme = currentTheme): Drawable {
        val d = context.resources.displayMetrics.density
        if (theme.pattern == BgPattern.NONE) return theme.windowBackground.toDrawable()

        val size = when (theme.pattern) {
            BgPattern.LCD -> (3 * d).toInt().coerceAtLeast(3)
            BgPattern.GRID -> (16 * d).toInt()
            BgPattern.STRIPES -> (12 * d).toInt()
            BgPattern.DOTS -> (8 * d).toInt()
            BgPattern.NONE -> 1
        }
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        canvas.drawColor(theme.windowBackground)
        val p = Paint().apply { color = theme.patternColor }
        when (theme.pattern) {
            BgPattern.LCD -> canvas.drawRect(0f, size - d.coerceAtLeast(1f), size.toFloat(), size.toFloat(), p) // pixel row gaps
            BgPattern.GRID -> {
                canvas.drawRect(0f, 0f, size.toFloat(), d, p)
                canvas.drawRect(0f, 0f, d, size.toFloat(), p)
            }
            BgPattern.STRIPES -> canvas.drawRect(0f, 0f, size.toFloat(), size / 2f, p)
            BgPattern.DOTS -> canvas.drawRect(0f, 0f, size / 2f, size / 2f, p)
            BgPattern.NONE -> {}
        }
        return bmp.toDrawable(context.resources).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            isFilterBitmap = false
        }
    }

    // kept for callers that still want "the screen" background
    fun createScanlineDrawable(context: Context, withBorder: Boolean): Drawable = backgroundDrawable(context)

    private val retroTypeface: Typeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

    private var pixelTypeface: Typeface? = null

    private fun pixel(context: Context): Typeface =
        pixelTypeface ?: (androidx.core.content.res.ResourcesCompat.getFont(context, R.font.press_start_2p) ?: retroTypeface)
            .also { pixelTypeface = it }

    /**
     * Gives every TextView under [root] the theme's font: real 8x8 pixel text for gen 1-2 (shrunk, it's wide),
     * blocky monospace for gen 3, the normal font otherwise.
     */
    fun applyFont(root: View, theme: AppTheme = currentTheme) {
        when (root) {
            is TextView -> {
                val original = root.getTag(R.id.tag_original_typeface) as? Typeface
                    ?: (root.typeface ?: Typeface.DEFAULT).also { root.setTag(R.id.tag_original_typeface, it) }
                val originalSize = root.getTag(R.id.tag_original_textsize) as? Float
                    ?: root.textSize.also { root.setTag(R.id.tag_original_textsize, it) }
                when {
                    theme.pixelFont -> {
                        root.typeface = pixel(root.context)
                        root.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize * 0.66f)
                        root.letterSpacing = 0f
                        root.paint.isAntiAlias = false   // hard pixel edges, no in-between shades
                    }
                    theme.retroFont -> {
                        root.paint.isAntiAlias = true
                        root.typeface = retroTypeface
                        root.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize)
                    }
                    else -> {
                        root.paint.isAntiAlias = true
                        root.typeface = original
                        root.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize)
                    }
                }
            }
            is ViewGroup -> for (i in 0 until root.childCount) applyFont(root.getChildAt(i), theme)
        }
    }
}
