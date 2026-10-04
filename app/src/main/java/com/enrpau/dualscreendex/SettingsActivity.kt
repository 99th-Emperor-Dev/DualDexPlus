package com.enrpau.dualscreendex

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enrpau.dualscreendex.data.GameCatalog
import com.enrpau.dualscreendex.data.RomManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var prefs: SharedPreferences

    private lateinit var swMatchDex: SwitchMaterial
    private lateinit var swGameDex: SwitchMaterial
    private lateinit var swUse2D: SwitchMaterial
    private lateinit var swLcd: SwitchMaterial
    private lateinit var btnScanSource: MaterialButton
    private lateinit var btnScanAlign: MaterialButton
    private lateinit var rvProfiles: RecyclerView

    private val density get() = resources.displayMetrics.density

    // the pixel font has no ▶ glyph
    private fun cursor() = if (ThemeManager.currentTheme.pixelFont) "> " else "▶ "

    // true while the screen sets switch states itself, so listeners don't treat it as a user change
    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.loadTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("DualDexPrefs", Context.MODE_PRIVATE)
        root = findViewById(R.id.settings_root)
        toolbar = findViewById(R.id.topAppBar)
        swMatchDex = findViewById(R.id.swMatchDex)
        swGameDex = findViewById(R.id.swGameDex)
        swUse2D = findViewById(R.id.swUse2D)
        swLcd = findViewById(R.id.swLcd)
        btnScanSource = findViewById(R.id.btnScanSource)
        btnScanAlign = findViewById(R.id.btnScanAlign)
        rvProfiles = findViewById(R.id.rvProfiles)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        toolbar.setNavigationOnClickListener { finish() }

        // match dex to game
        swMatchDex.isChecked = GameCatalog.isMatchDex(this)
        swMatchDex.setOnCheckedChangeListener { _, on ->
            if (updatingUi) return@setOnCheckedChangeListener
            GameCatalog.setMatchDex(this, on)
            GameCatalog.syncProfile(this)
            refreshAll()
        }
        findViewById<View>(R.id.boxMatchDex).setOnClickListener { swMatchDex.toggle() }

        // only pokemon in this game
        swGameDex.setOnCheckedChangeListener { button, on ->
            if (updatingUi) return@setOnCheckedChangeListener
            GameCatalog.currentFamily()?.let { GameCatalog.setGameDexOnly(this, it, on) }
            refreshGameOptions()
        }
        findViewById<View>(R.id.rowGameDex).setOnClickListener { if (swGameDex.isEnabled) swGameDex.performClick() }

        // 2d sprites for the switch-era games
        swUse2D.setOnCheckedChangeListener { button, on ->
            if (updatingUi) return@setOnCheckedChangeListener
            GameCatalog.setUse2D(this, on)
        }
        findViewById<View>(R.id.rowUse2D).setOnClickListener { swUse2D.performClick() }

        // still sprites, for handhelds that struggle running a game and animations together
        val swAnimations = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swAnimations)
        swAnimations.setOnCheckedChangeListener { _, on ->
            if (updatingUi) return@setOnCheckedChangeListener
            GameCatalog.setAnimationsOn(this, on)
        }
        findViewById<View>(R.id.rowAnimations).setOnClickListener { swAnimations.performClick() }

        // lcd texture for the handheld games
        swLcd.setOnCheckedChangeListener { _, on ->
            if (updatingUi) return@setOnCheckedChangeListener
            GameCatalog.setLcd(this, on)
            applyLcd()
        }
        findViewById<View>(R.id.rowLcd).setOnClickListener { swLcd.performClick() }

        // dex version dropdown
        val dexTitle = findViewById<TextView>(R.id.lblProfileTitle)
        val dexBody = findViewById<View>(R.id.dexVersionBody)
        dexTitle.setOnClickListener {
            val open = dexBody.visibility != View.VISIBLE
            dexBody.visibility = if (open) View.VISIBLE else View.GONE
            dexTitle.text = if (open) "Dex Version ▴" else "Dex Version ▾"
        }
        findViewById<MaterialButton>(R.id.btnCreateProfile).setOnClickListener {
            startActivity(Intent(this, CreateProfileActivity::class.java))
        }
        rvProfiles.layoutManager = LinearLayoutManager(this)

        setupScannerClicks()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshProfileList()
    }

    private fun refreshAll() {
        refreshScannerUI()
        applyThemeToSettingsScreen()   // also rebuilds the game grid
        refreshGameOptions()
        refreshProfileList()
    }

    // ---------- dex version ----------

    private fun refreshProfileList() {
        val matching = GameCatalog.activeFamily(this) != null
        findViewById<TextView>(R.id.lblProfileDesc).text =
            if (matching) "Set automatically by the selected game. Turn off \"Match dex to game\" to pick one yourself."
            else "Pick the type chart and Pokédex by hand."
        rvProfiles.adapter = ProfileAdapter(RomManager.getAllProfiles(), RomManager.currentProfile.id,
            onSelect = { profile ->
                if (matching) {
                    // picking by hand means the game shouldn't override it
                    GameCatalog.setMatchDex(this, false)
                    updatingUi = true
                    swMatchDex.isChecked = false
                    updatingUi = false
                }
                RomManager.selectProfile(this, profile)
                refreshAll()
            },
            onDelete = { profile ->
                RomManager.deleteCustomProfile(this, profile)
                refreshProfileList()
            }
        )
    }

    // ---------- games ----------

    private fun selectGame(theme: AppTheme) {
        // a built-in game: leave the custom game as it was saved
        com.enrpau.dualscreendex.data.CustomGames.clearActive(this)
        prefs.edit { putString("SELECTED_THEME_ID", theme.id) }
        ThemeManager.loadTheme(this)
        GameCatalog.syncProfile(this)
        refreshAll()
    }

    // every game gets a live mini preview: its background, a game window, its font and colours
    private fun refreshThemeUI() {
        val grid = findViewById<LinearLayout>(R.id.themeGrid)
        grid.removeAllViews()
        val customActive = com.enrpau.dualscreendex.data.CustomGames.activeId(this)
        val selected = if (customActive != null) "" else ThemeManager.currentTheme.id
        val d = density

        ThemeManager.allThemes.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { base ->
                val t = ThemeManager.withVersion(this, base)
                val family = GameCatalog.familyForTheme(base.id)
                val tile = FrameLayout(this).apply {
                    background = ThemeManager.backgroundDrawable(this@SettingsActivity, t)
                    foreground = if (t.id == selected) android.graphics.drawable.GradientDrawable().apply {
                        setStroke((4 * d).toInt(), ThemeManager.currentTheme.screenTextColor)
                    } else null
                    setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
                    isClickable = true
                    setOnClickListener { selectGame(base) }
                }
                val window = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = ThemeManager.boxDrawable(this@SettingsActivity,
                        t.headerColor.takeIf { it != Color.TRANSPARENT } ?: t.gridBackgroundColor, t)
                    setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
                }
                val onWindow = if (t.headerColor != Color.TRANSPARENT) t.headerTextColor else t.listTextColor
                window.addView(TextView(this).apply {
                    text = (if (t.id == selected) cursor() else "") + (family?.name ?: t.displayName)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(onWindow)
                })
                if (family != null && family.versions.size > 1) {
                    window.addView(TextView(this).apply {
                        text = GameCatalog.version(this@SettingsActivity, family).name
                        textSize = 10f
                        gravity = Gravity.CENTER
                        setTextColor(ColorUtils.setAlphaComponent(onWindow, 190))
                    })
                }
                ThemeManager.applyFont(window, t)
                tile.addView(window)
                row.addView(tile, LinearLayout.LayoutParams(0, (92 * d).toInt(), 1f).apply {
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            grid.addView(row)
        }
        addCustomGameTiles(grid, customActive)
    }

    /**
     * "My games": the user's own games after the built-in ones, each drawn in its base game's style.
     * Tap = use it, hold = rename / delete, "+ Add game" saves the current settings as a new one.
     */
    private fun addCustomGameTiles(grid: LinearLayout, activeId: String?) {
        val d = density
        val games = com.enrpau.dualscreendex.data.CustomGames.all(this)
        grid.addView(TextView(this).apply {
            text = "My games"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.currentTheme.screenTextColor)
            setPadding((4 * d).toInt(), (16 * d).toInt(), 0, (4 * d).toInt())
            ThemeManager.applyFont(this)
        })
        // null = the "+ Add game" tile
        val entries: List<com.enrpau.dualscreendex.data.CustomGames.CustomGame?> = games + listOf(null)
        entries.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { game ->
                val baseId = game?.settings?.get("SELECTED_THEME_ID")?.removePrefix("s:")
                val t = ThemeManager.allThemes.find { it.id == baseId } ?: ThemeManager.currentTheme
                val isActive = game != null && game.id == activeId
                val tile = FrameLayout(this).apply {
                    background = ThemeManager.backgroundDrawable(this@SettingsActivity, t)
                    foreground = if (isActive) android.graphics.drawable.GradientDrawable().apply {
                        setStroke((4 * d).toInt(), ThemeManager.currentTheme.screenTextColor)
                    } else null
                    setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
                    isClickable = true
                    if (game == null) {
                        setOnClickListener {
                            promptName("Add game", "") { name ->
                                com.enrpau.dualscreendex.data.CustomGames.add(this@SettingsActivity, name)
                                refreshAll()
                            }
                        }
                    } else {
                        setOnClickListener {
                            com.enrpau.dualscreendex.data.CustomGames.apply(this@SettingsActivity, game)
                            ThemeManager.loadTheme(this@SettingsActivity)
                            refreshAll()
                        }
                        setOnLongClickListener { customGameMenu(game); true }
                    }
                }
                val window = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = ThemeManager.boxDrawable(this@SettingsActivity,
                        t.headerColor.takeIf { it != Color.TRANSPARENT } ?: t.gridBackgroundColor, t)
                    setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
                }
                val onWindow = if (t.headerColor != Color.TRANSPARENT) t.headerTextColor else t.listTextColor
                window.addView(TextView(this).apply {
                    text = if (game == null) "+ Add game" else (if (isActive) cursor() else "") + game.name
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(onWindow)
                })
                window.addView(TextView(this).apply {
                    text = if (game == null) "saves your current settings" else "hold to rename"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(ColorUtils.setAlphaComponent(onWindow, 190))
                })
                ThemeManager.applyFont(window, t)
                tile.addView(window)
                row.addView(tile, LinearLayout.LayoutParams(0, (92 * d).toInt(), 1f).apply {
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            grid.addView(row)
        }
    }

    private fun customGameMenu(game: com.enrpau.dualscreendex.data.CustomGames.CustomGame) {
        android.app.AlertDialog.Builder(this)
            .setTitle(game.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0) {
                    promptName("Rename", game.name) { name ->
                        com.enrpau.dualscreendex.data.CustomGames.rename(this, game.id, name)
                        refreshAll()
                    }
                } else {
                    android.app.AlertDialog.Builder(this)
                        .setMessage("Delete \"${game.name}\"? Its teams stay saved but won't show.")
                        .setPositiveButton("Delete") { _, _ ->
                            com.enrpau.dualscreendex.data.CustomGames.delete(this, game.id)
                            refreshAll()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun promptName(title: String, current: String, onDone: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            setText(current)
            hint = "e.g. Pokémon Crystal Clear"
            setSingleLine()
            setSelection(text.length)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(FrameLayout(this).apply {
                val p = (20 * density).toInt()
                setPadding(p, (8 * density).toInt(), p, 0)
                addView(input)
            })
            .setPositiveButton("Save") { _, _ -> onDone(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        // whatever changed while a custom game is active belongs to that game
        com.enrpau.dualscreendex.data.CustomGames.saveActive(this)
    }

    private fun refreshGameOptions() {
        // display options apply to every game, so they live in their own box that is always shown
        findViewById<LinearLayout>(R.id.displayOptions).let { display ->
            display.background = ThemeManager.boxDrawable(this)
            updatingUi = true
            findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swAnimations).isChecked = GameCatalog.isAnimationsOn(this)
            updatingUi = false
            listOf(R.id.lblAnimations, R.id.lblAnimationsDesc).forEach { findViewById<TextView>(it).setTextColor(ThemeManager.currentTheme.listTextColor) }
            ThemeManager.applyFont(display)
        }
        val box = findViewById<LinearLayout>(R.id.gameOptions)
        val family = GameCatalog.currentFamily()
        if (family == null) {
            box.visibility = View.GONE
            return
        }
        box.visibility = View.VISIBLE
        val theme = ThemeManager.currentTheme
        val d = density
        box.background = ThemeManager.boxDrawable(this)

        // version buttons
        val versionRow = findViewById<LinearLayout>(R.id.versionRow)
        versionRow.removeAllViews()
        findViewById<View>(R.id.lblVersion).visibility = if (family.versions.size > 1) View.VISIBLE else View.GONE
        versionRow.visibility = findViewById<View>(R.id.lblVersion).visibility
        val current = GameCatalog.version(this, family)
        family.versions.chunked(3).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { v ->
                val selected = v.id == current.id
                val vt = if (theme.monochrome) theme else v.tweak(ThemeManager.allThemes.first { it.id == family.themeId })
                row.addView(TextView(this).apply {
                    text = if (selected) "${cursor()}${v.name}" else v.name
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding((6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt())
                    val fill = if (selected) (if (theme.monochrome) theme.innerFrameColor else vt.headerColor.takeIf { it != Color.TRANSPARENT } ?: vt.gridBackgroundColor)
                        else theme.gridBackgroundColor
                    background = ThemeManager.boxDrawable(this@SettingsActivity, fill, vt)
                    setTextColor(when {
                        selected && theme.monochrome -> theme.gridBackgroundColor
                        selected && vt.headerColor != Color.TRANSPARENT -> vt.headerTextColor
                        else -> theme.listTextColor
                    })
                    isClickable = true
                    setOnClickListener {
                        GameCatalog.setVersion(this@SettingsActivity, family, v)
                        ThemeManager.loadTheme(this@SettingsActivity)
                        refreshAll()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                })
            }
            repeat(3 - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) }
            versionRow.addView(row)
        }

        // game dex toggle
        val matching = GameCatalog.isMatchDex(this)
        val count = GameCatalog.speciesIds(this, family).size
        swGameDex.isEnabled = matching
        updatingUi = true
        swGameDex.isChecked = GameCatalog.isGameDexOnly(this, family)
        findViewById<TextView>(R.id.lblGameDex).text = "Only Pokémon in ${current.name} ($count)"
        findViewById<TextView>(R.id.lblGameDexDesc).text =
            if (!matching) "Turn on \"Match dex to game\" to use this."
            else "Off shows the full National Dex (1025)."
        findViewById<View>(R.id.rowGameDex).alpha = if (matching) 1f else 0.5f

        // custom dex: pick any game's sprites (the dex no longer decides the look)
        refreshSpriteSetRow(box, matching)
        refreshDitherRow(box)
        refreshFourColourRow(box)

        // gen 1: screen palette (game boy green, pocket, light, game boy color, super game boy)
        val paletteRow = findViewById<LinearLayout>(R.id.paletteRow)
        paletteRow.removeAllViews()
        val isGb = family.themeId == "red"
        findViewById<View>(R.id.lblPalette).visibility = if (isGb) View.VISIBLE else View.GONE
        paletteRow.visibility = if (isGb) View.VISIBLE else View.GONE
        if (isGb) {
            val chosen = GameCatalog.gbPalette(this)
            GameCatalog.GbPalette.entries.chunked(2).forEach { chunk ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chunk.forEach { p ->
                    val selected = p == chosen
                    row.addView(TextView(this).apply {
                        text = if (selected) cursor() + p.label else p.label
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding((6 * d).toInt(), (11 * d).toInt(), (6 * d).toInt(), (11 * d).toInt())
                        background = ThemeManager.boxDrawable(this@SettingsActivity,
                            if (selected) theme.innerFrameColor else theme.gridBackgroundColor)
                        setTextColor(if (selected) theme.gridBackgroundColor else theme.listTextColor)
                        setOnClickListener {
                            GameCatalog.setGbPalette(this@SettingsActivity, p)
                            ThemeManager.loadTheme(this@SettingsActivity)
                            refreshAll()
                        }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                    })
                }
                if (chunk.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
                paletteRow.addView(row)
            }
        }

        // lcd effect only for the handheld (game boy / color / advance) games
        val hasLcd = ThemeManager.lcdOverlay(this) != null
        findViewById<View>(R.id.rowLcd).visibility = if (hasLcd) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.lblLcdDesc).text = when (family.themeId) {
            "red" -> "LCD lines like the original Game Boy screen."
            "oled" -> "LCD lines like the Game Boy Color screen."
            else -> "LCD lines like the Game Boy Advance screen."
        }

        val show2D = GameCatalog.is3DGame(family) && family.versions.none { it.always2D }
        findViewById<View>(R.id.rowUse2D).visibility = if (show2D) View.VISIBLE else View.GONE

        swUse2D.isChecked = GameCatalog.isUse2D(this)
        swLcd.isChecked = GameCatalog.isLcd(this)
        updatingUi = false

        listOf(R.id.lblVersion, R.id.lblGameDex, R.id.lblGameDexDesc, R.id.lblUse2D, R.id.lblUse2DDesc, R.id.lblPalette, R.id.lblLcd, R.id.lblLcdDesc).forEach {
            findViewById<TextView>(it).setTextColor(theme.listTextColor)
        }
        ThemeManager.applyFont(box)
    }

    /**
     * "Sprites" row, in every game: keep the game's theme and dex but show another game's sprites
     * (or the 3D models), animated where any animation exists.
     */
    private fun refreshSpriteSetRow(box: LinearLayout, matching: Boolean) {
        val d = density
        val theme = ThemeManager.currentTheme
        val row = box.findViewWithTag<LinearLayout>("spriteSetRow") ?: LinearLayout(this).apply {
            tag = "spriteSetRow"
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            // right under "Only Pokémon in ..." (its row may sit inside a wrapper, so find the box's child)
            var anchor: View = this@SettingsActivity.findViewById(R.id.rowGameDex)
            while (anchor.parent !== box && anchor.parent is View) anchor = anchor.parent as View
            box.addView(this, box.indexOfChild(anchor) + 1, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        }
        row.removeAllViews()
        row.visibility = View.VISIBLE
        // shown in every game: keep the game's look but use another game's sprites
        val current = GameCatalog.spriteSets.firstOrNull { it.first == GameCatalog.spriteSet(this) } ?: GameCatalog.spriteSets[0]
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = "Sprites"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(theme.listTextColor)
        })
        labels.addView(TextView(this).apply {
            text = "Use another game's sprites, animated."; textSize = 12f; setTextColor(theme.listTextColor)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "${current.second} ▾"
            isAllCaps = false
            styleButton(this)
            setOnClickListener {
                val sets = GameCatalog.spriteSets
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Sprites")
                    .setSingleChoiceItems(sets.map { it.second }.toTypedArray(), sets.indexOf(current)) { dlg, which ->
                        GameCatalog.setSpriteSet(this@SettingsActivity, sets[which].first)
                        dlg.dismiss()
                        refreshGameOptions()
                    }
                    .show()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (12 * d).toInt()
        })
    }

    /**
     * "Dithering" switch for the Game Boy / Game Boy Color palettes: sprites snapped to the palette get the
     * checkerboard colour blends of GBC-style rom hacks. A filter at runtime, so no extra files.
     */
    private fun refreshDitherRow(box: LinearLayout) {
        val d = density
        val theme = ThemeManager.currentTheme
        val row = box.findViewWithTag<LinearLayout>("ditherRow") ?: LinearLayout(this).apply {
            tag = "ditherRow"
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val anchor = box.findViewWithTag<View>("spriteSetRow")
            box.addView(this, box.indexOfChild(anchor) + 1, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        }
        row.removeAllViews()
        row.visibility = if (ThemeManager.isLimitedPalette) View.VISIBLE else View.GONE
        if (!ThemeManager.isLimitedPalette) return
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = "Dithering"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(theme.listTextColor)
        })
        labels.addView(TextView(this).apply {
            text = "Checkerboard colour blends, like GBC-style rom hacks."; textSize = 12f; setTextColor(theme.listTextColor)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            isChecked = GameCatalog.isDither(this@SettingsActivity)
            setOnCheckedChangeListener { _, on ->
                GameCatalog.setDither(this@SettingsActivity, on)
                ThemeManager.dithering = on
            }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
    }

    /** "4-colour sprites" (Game Boy Color themes): each sprite keeps only 4 colours, like real GBC sprites. */
    private fun refreshFourColourRow(box: LinearLayout) {
        val d = density
        val theme = ThemeManager.currentTheme
        val row = box.findViewWithTag<LinearLayout>("fourColourRow") ?: LinearLayout(this).apply {
            tag = "fourColourRow"
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val anchor = box.findViewWithTag<View>("spriteSetRow")
            box.addView(this, box.indexOfChild(anchor) + 1, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        }
        row.removeAllViews()
        val show = theme.gbc15Bit
        row.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = "4-colour sprites"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(theme.listTextColor)
        })
        labels.addView(TextView(this).apply {
            text = "Each sprite keeps only 4 colours, like real Game Boy Color sprites."; textSize = 12f; setTextColor(theme.listTextColor)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            isChecked = GameCatalog.isFourColour(this@SettingsActivity)
            setOnCheckedChangeListener { _, on ->
                GameCatalog.setFourColour(this@SettingsActivity, on)
                ThemeManager.fourColour = on
            }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
    }

    /** Settings buttons look like the game's windows. */
    private fun styleButton(btn: MaterialButton) {
        btn.setTextColor(ThemeManager.currentTheme.listTextColor)
        btn.backgroundTintList = null
        btn.background = ThemeManager.boxDrawable(this)
        btn.strokeWidth = 0
        btn.isAllCaps = false
        btn.letterSpacing = 0f
        btn.setRippleColor(android.content.res.ColorStateList.valueOf("#20000000".toColorInt()))
    }

    // ---------- scanner ----------
    private fun setupScannerClicks() {
        // which screen the app lives on: the second one (default), or the main one for devices
        // whose "main" display is physically the bottom screen
        findViewById<MaterialButton>(R.id.btnAppScreen).setOnClickListener {
            val next = if (prefs.getString("APP_SCREEN", "second") == "second") "main" else "second"
            prefs.edit { putString("APP_SCREEN", next) }
            refreshScannerUI()
        }
        btnScanSource.setOnClickListener {
            val newMode = if (prefs.getString("SCAN_SOURCE", "top") == "top") "bottom" else "top"
            prefs.edit { putString("SCAN_SOURCE", newMode) }
            refreshScannerUI()
        }
        findViewById<MaterialButton>(R.id.btnScanLanguage).setOnClickListener {
            val next = if (prefs.getString("SCAN_LANGUAGE", "en") == "ja") "en" else "ja"
            prefs.edit { putString("SCAN_LANGUAGE", next) }
            refreshScannerUI()
        }
        btnScanAlign.setOnClickListener {
            val newMode = if (prefs.getString("SCAN_ALIGN", "left") == "left") "right" else "left"
            prefs.edit { putString("SCAN_ALIGN", newMode) }
            refreshScannerUI()
        }
    }

    private fun refreshScannerUI() {
        findViewById<MaterialButton>(R.id.btnAppScreen).text = "App Screen: ${prefs.getString("APP_SCREEN", "second")?.uppercase()}"
        btnScanSource.text = "Scan Screen: ${prefs.getString("SCAN_SOURCE", "top")?.uppercase()}"
        btnScanAlign.text = "Pokemon Aligned: ${prefs.getString("SCAN_ALIGN", "left")?.uppercase()}"
        findViewById<MaterialButton>(R.id.btnScanLanguage).text =
            "Game Language: " + (if (prefs.getString("SCAN_LANGUAGE", "en") == "ja") "Japanese" else "English")
    }

    // ---------- theme ----------

    // a switch accent that stays visible: light accents (GBC white, OLED white) fall back to the frame colour
    private fun switchAccent(theme: AppTheme): Int = when {
        ColorUtils.calculateLuminance(theme.fabColor) < 0.7 -> theme.fabColor
        Color.alpha(theme.frameColor) > 0 -> theme.frameColor
        else -> "#4CAF50".toColorInt()
    }

    private fun applyThemeToSettingsScreen() {
        val theme = ThemeManager.currentTheme

        root.background = ThemeManager.backgroundDrawable(this)
        toolbar.setBackgroundColor(Color.TRANSPARENT)
        window.statusBarColor = theme.windowBackground
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(theme.windowBackground) > 0.5

        val textColor = theme.screenTextColor
        toolbar.setTitleTextColor(textColor)
        toolbar.setNavigationIconTint(textColor)
        listOf(R.id.lblThemeTitle, R.id.lblScannerTitle, R.id.lblProfileTitle).forEach {
            findViewById<TextView>(it).setTextColor(textColor)
        }
        findViewById<TextView>(R.id.lblProfileDesc).setTextColor(ColorUtils.setAlphaComponent(textColor, 190))

        // the match toggle sits in its own game window
        findViewById<View>(R.id.boxMatchDex).background = ThemeManager.boxDrawable(this)
        findViewById<TextView>(R.id.lblMatchTitle).setTextColor(theme.listTextColor)
        findViewById<TextView>(R.id.lblMatchDesc).setTextColor(ColorUtils.setAlphaComponent(theme.listTextColor, 180))
        val thumb = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(switchAccent(theme), Color.GRAY)
        )
        listOf(swMatchDex, swGameDex, swUse2D, swLcd).forEach {
            it.thumbTintList = thumb
            it.trackTintList = thumb.withAlpha(110)
        }

        // buttons become game windows too

        listOf(findViewById<MaterialButton>(R.id.btnAppScreen), btnScanSource, btnScanAlign, findViewById(R.id.btnScanLanguage), findViewById<MaterialButton>(R.id.btnCreateProfile)).forEach { styleButton(it) }

        ThemeManager.applyFont(root)
        applyLcd()
        // previews last, so each keeps its own game's font
        refreshThemeUI()
    }

    private fun applyLcd() {
        ThemeManager.applyLcd(window)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ThemeManager.hideNavigationBar(window)
    }
}