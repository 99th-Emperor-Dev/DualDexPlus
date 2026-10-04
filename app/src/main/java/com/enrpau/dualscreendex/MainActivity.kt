package com.enrpau.dualscreendex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // ui references
    private lateinit var tvName: TextView
    private lateinit var tvId: TextView
    private lateinit var layoutTypes: LinearLayout
    private lateinit var gridWeak: LinearLayout
    private lateinit var gridResist: LinearLayout
    private lateinit var lblWeak: TextView
    private lateinit var lblResist: TextView
    private lateinit var btnVariantToggle: com.google.android.material.button.MaterialButton

    private lateinit var navLeftBtn: View
    private lateinit var navRightBtn: View
    private lateinit var tvPrevName: TextView
    private lateinit var tvNextName: TextView
    private lateinit var tvArrowLeft: TextView
    private lateinit var tvArrowRight: TextView

    private lateinit var containerBattle: View
    private lateinit var tvScreenTitle: TextView
    private lateinit var cardHeader: androidx.cardview.widget.CardView
    private lateinit var cardData: androidx.cardview.widget.CardView
    private lateinit var containerPokedex: LinearLayout
    private lateinit var btnBack: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var rvList: androidx.recyclerview.widget.RecyclerView
    private lateinit var etSearch: com.google.android.material.textfield.TextInputEditText
    private lateinit var searchContainer: com.google.android.material.textfield.TextInputLayout

    private lateinit var cardBattleTab: androidx.cardview.widget.CardView
    private lateinit var tvBattleTabText: TextView

    private lateinit var containerTeamBuilder: View
    private lateinit var btnOpenTeam: View
    private lateinit var btnTeamBuilderCard: View
    private lateinit var btnLeftFab: View
    private val teamCells = mutableListOf<androidx.cardview.widget.CardView>()

    private lateinit var ivTargetSprite: android.widget.ImageView
    private lateinit var lblCounters: TextView
    private lateinit var listCounters: LinearLayout
    private lateinit var tvTeamGame: TextView
    private lateinit var tvTeamName: TextView
    private lateinit var tvTeamHint: TextView
    private lateinit var btnTeamAdd: android.widget.ImageView
    private lateinit var btnTeamRename: android.widget.ImageView
    private lateinit var btnTeamDelete: android.widget.ImageView
    private lateinit var lblTeamWeak: TextView
    private lateinit var gridTeamWeak: LinearLayout
    private lateinit var lblTeamGaps: TextView
    private lateinit var gridTeamGaps: LinearLayout

    private lateinit var adapter: PokemonAdapter

    private val pokemonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val found = intent.getBooleanExtra("FOUND", false)
            if (!found) {
                viewModel.onScanResult(null, null, null, null)
                return
            }
            viewModel.onScanResult(
                intent.getStringArrayListExtra("NAMES"),
                intent.getIntegerArrayListExtra("IDS"),
                intent.getStringArrayListExtra("TYPE1S"),
                intent.getStringArrayListExtra("TYPE2S")
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.loadTheme(this)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && moveToSecondScreen()) return
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initViews()

        rvList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        adapter = PokemonAdapter(emptyList()) { selectedPokemon ->
            viewModel.onPokemonSelectedFromList(selectedPokemon)
            hideKeyboard()
        }
        rvList.adapter = adapter
        // game boy color: the list may only add as many colours as the screen budget allows
        adapter.onSpriteColorsChanged = { counts -> updateListPalette(counts) }

        setupListeners()
        fitCardToScreen()
        loadSettings()

        // registered once for the activity's lifetime (re-registering in every onResume leaked receivers
        // and delivered each scan several times)
        ContextCompat.registerReceiver(
            this, pokemonReceiver,
            IntentFilter("com.enrpau.dualscreendex.POKEMON_DETECTED"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        viewModel.displayedPokemon.observeForeverSafe { pokemon ->
            updateCardUI(pokemon)
        }

        viewModel.pokedexList.observeForeverSafe { list ->
            adapter.updateList(list)
        }

        viewModel.isBattleMode.observeForeverSafe { isBattle ->
            if (isBattle) {
                containerPokedex.visibility = View.GONE
                containerBattle.visibility = View.VISIBLE
                containerTeamBuilder.visibility = View.GONE
                btnBack.visibility = View.GONE
                btnLeftFab.visibility = View.GONE
                btnOpenTeam.visibility = View.GONE
            } else {
                if (viewModel.isTeamMode.value != true) {
                    containerPokedex.visibility = View.VISIBLE
                    containerBattle.visibility = View.GONE
                    containerTeamBuilder.visibility = View.GONE
                    btnBack.visibility = View.GONE
                    btnLeftFab.visibility = View.GONE
                    btnOpenTeam.visibility = View.GONE
                }
            }
        }

        viewModel.isTeamMode.observeForeverSafe { isTeam ->
            if (isTeam) {
                containerPokedex.visibility = View.GONE
                containerBattle.visibility = View.GONE
                containerTeamBuilder.visibility = View.VISIBLE
                btnBack.visibility = View.GONE
                btnLeftFab.visibility = View.GONE
                btnOpenTeam.visibility = View.GONE
            } else {
                if (viewModel.isBattleMode.value != true) {
                    containerPokedex.visibility = View.VISIBLE
                    containerBattle.visibility = View.GONE
                    containerTeamBuilder.visibility = View.GONE
                    btnBack.visibility = View.GONE
                    btnLeftFab.visibility = View.GONE
                    btnOpenTeam.visibility = View.GONE
                }
            }
        }

        viewModel.teamList.observeForeverSafe { team ->
            team.forEachIndexed { index, pokemon ->
                teamCells.getOrNull(index)?.let { renderTeamCell(it, index, pokemon) }
            }
        }

        viewModel.activeTeam.observeForeverSafe { team ->
            tvTeamName.text = "${team.name} ▾"
            tvTeamGame.text = viewModel.teamScopeName()
        }

        viewModel.counterList.observeForeverSafe { list -> renderCounters(list) }

        viewModel.teamWeaknesses.observeForeverSafe { list ->
            populateSmartGrid(gridTeamWeak, list, countMode = true)
            ThemeManager.applyFont(gridTeamWeak)
            val show = list.isNotEmpty()
            lblTeamWeak.visibility = if (show) View.VISIBLE else View.GONE
            gridTeamWeak.visibility = if (show) View.VISIBLE else View.GONE
        }

        viewModel.teamCoverageGaps.observeForeverSafe { list ->
            populateTypeGrid(gridTeamGaps, list)
            ThemeManager.applyFont(gridTeamGaps)
            val show = list.isNotEmpty()
            lblTeamGaps.visibility = if (show) View.VISIBLE else View.GONE
            gridTeamGaps.visibility = if (show) View.VISIBLE else View.GONE
        }

        viewModel.teamSuggestions.observeForeverSafe { renderSuggestions(it) }

        // while picking a member, the dex list title tells you which slot you're filling
        viewModel.selectionIndex.observeForeverSafe { slot ->
            tvScreenTitle.text = if (slot >= 0) "Pick for slot ${slot + 1}" else getString(R.string.pokedex_title)
            // each pick starts from the full list, and the last search doesn't leak back into the dex
            if (etSearch.text?.isNotEmpty() == true) etSearch.setText("")
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    etSearch.hasFocus() -> hideKeyboard()
                    (viewModel.selectionIndex.value ?: -1) >= 0 -> viewModel.cancelTeamSelection()
                    viewModel.isBattleMode.value == true || viewModel.isTeamMode.value == true -> viewModel.onBackToListClicked()
                    etSearch.text?.isNotEmpty() == true -> etSearch.setText("")
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        viewModel.prevPokemonName.observeForeverSafe { name ->
            tvPrevName.text = name.replaceFirstChar { it.uppercase() }
        }

        viewModel.nextPokemonName.observeForeverSafe { name ->
            tvNextName.text = name.replaceFirstChar { it.uppercase() }
        }

        viewModel.isPrevButtonVisible.observeForeverSafe { visible ->
            navLeftBtn.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            tvPrevName.visibility = navLeftBtn.visibility
        }

        viewModel.isNextButtonVisible.observeForeverSafe { visible ->
            navRightBtn.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            tvNextName.visibility = navRightBtn.visibility
        }

        viewModel.showBattleTab.observeForeverSafe { show ->
            cardBattleTab.visibility = if (show) View.VISIBLE else View.GONE
        }

        viewModel.battleTabText.observeForeverSafe { text ->
            tvBattleTabText.text = text
        }

        viewModel.battleTabColor.observeForeverSafe { color ->
            cardBattleTab.setCardBackgroundColor(color)
            tvBattleTabText.setTextColor(Color.WHITE)
        }

        viewModel.weaknessList.observeForeverSafe { list ->
            populateSmartGrid(gridWeak, list)
            ThemeManager.applyFont(gridWeak)
            lblWeak.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            gridWeak.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            fitWideToScreen(true)
        }

        viewModel.resistanceList.observeForeverSafe { list ->
            populateSmartGrid(gridResist, list)
            ThemeManager.applyFont(gridResist)
            lblResist.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            gridResist.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            fitWideToScreen(true)
        }

        // a banner instead of forcing the user into system settings on every launch
        findViewById<TextView>(R.id.tvScannerBanner).apply {
            background = ThemeManager.shape(this@MainActivity, ThemeManager.ui("#C62828".toColorInt()), 12f)
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        setupCardSwipe()
    }

    /**
     * Pull down from the top of a page to close it: when the page is scrolled to the top, a downward drag
     * moves the page with your finger and closes it past a threshold (or springs back).
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun attachSwipeDownToClose(page: View) {
        val scroll = page as? android.widget.ScrollView
        var startY = 0f
        var startX = 0f
        var dragging = false
        val d = resources.displayMetrics.density
        val existing = page.getTag(R.id.tag_swipe_listener) as? View.OnTouchListener
        val listener = View.OnTouchListener { v, ev ->
            existing?.onTouch(v, ev)
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { startY = ev.rawY; startX = ev.rawX; dragging = false }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - startY
                    val atTop = (scroll?.scrollY ?: 0) == 0
                    if (!dragging && atTop && dy > 12 * d && dy > kotlin.math.abs(ev.rawX - startX) * 1.5f) dragging = true
                    if (dragging) {
                        page.translationY = (dy * 0.6f).coerceAtLeast(0f)
                        page.alpha = 1f - (page.translationY / (page.height * 1.4f)).coerceIn(0f, 0.5f)
                        return@OnTouchListener true
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    if (page.translationY > 110 * d) {
                        page.animate().translationY(page.height.toFloat()).alpha(0f).setDuration(160).withEndAction {
                            viewModel.onBackToListClicked()
                            page.translationY = 0f
                            page.alpha = 1f
                        }.start()
                    } else {
                        page.animate().translationY(0f).alpha(1f).setDuration(160).start()
                    }
                    return@OnTouchListener true
                }
            }
            false
        }
        page.setOnTouchListener(listener)
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    private fun updateScannerBanner() {
        findViewById<TextView>(R.id.tvScannerBanner).visibility =
            if (isAccessibilityServiceEnabled()) View.GONE else View.VISIBLE
    }

    // swipe the battle card left/right to move between pokemon
    private fun setupCardSwipe() {
        val detector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (kotlin.math.abs(dx) < 120 || kotlin.math.abs(dx) < kotlin.math.abs(dy) * 1.5f) return false
                if (dx < 0 && navRightBtn.visibility == View.VISIBLE) viewModel.onNextClicked()
                if (dx > 0 && navLeftBtn.visibility == View.VISIBLE) viewModel.onPrevClicked()
                return true
            }
        })
        val sideSwipe = View.OnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            false // let the ScrollView keep scrolling vertically
        }
        containerBattle.setTag(R.id.tag_swipe_listener, sideSwipe)
        attachSwipeDownToClose(containerBattle)
        attachSwipeDownToClose(containerTeamBuilder)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return prefString?.contains("$packageName/${DualDexAccessibilityService::class.java.name}") == true
    }

    private fun initViews() {
        tvName = findViewById(R.id.tvTargetName)
        tvId = findViewById(R.id.tvTargetId)
        layoutTypes = findViewById(R.id.layoutTargetTypes)
        gridWeak = findViewById(R.id.gridWeak)
        gridResist = findViewById(R.id.gridResist)
        lblWeak = findViewById(R.id.lblWeak)
        lblResist = findViewById(R.id.lblResist)
        btnVariantToggle = findViewById(R.id.btnVariantToggle)
        navLeftBtn = findViewById(R.id.navLeftContainer)
        navRightBtn = findViewById(R.id.navRightContainer)
        tvPrevName = findViewById(R.id.tvPrevName)
        tvNextName = findViewById(R.id.tvNextName)
        tvArrowLeft = findViewById(R.id.tvArrowLeft)
        tvArrowRight = findViewById(R.id.tvArrowRight)

        containerBattle = findViewById(R.id.containerBattle)
        tvScreenTitle = findViewById(R.id.tvScreenTitle)
        cardHeader = findViewById(R.id.cardHeader)
        cardData = findViewById(R.id.cardData)
        containerPokedex = findViewById(R.id.containerPokedex)
        btnBack = findViewById(R.id.btnBackToList)
        rvList = findViewById(R.id.rvPokemonList)
        etSearch = findViewById(R.id.etSearch)
        searchContainer = findViewById(R.id.searchContainer)
        cardBattleTab = findViewById(R.id.cardBattleTab)
        tvBattleTabText = findViewById(R.id.tvBattleTabText)

        containerTeamBuilder = findViewById(R.id.containerTeamBuilder)
        btnOpenTeam = findViewById(R.id.btnOpenTeam)
        btnTeamBuilderCard = findViewById(R.id.btnTeamBuilderCard)
        btnLeftFab = findViewById(R.id.btnLeftFab)

        teamCells.clear()
        teamCells.add(findViewById(R.id.cell0))
        teamCells.add(findViewById(R.id.cell1))
        teamCells.add(findViewById(R.id.cell2))
        teamCells.add(findViewById(R.id.cell3))
        teamCells.add(findViewById(R.id.cell4))
        teamCells.add(findViewById(R.id.cell5))

        ivTargetSprite = findViewById(R.id.ivTargetSprite)
        lblCounters = findViewById(R.id.lblCounters)
        listCounters = findViewById(R.id.listCounters)
        tvTeamGame = findViewById(R.id.tvTeamGame)
        tvTeamName = findViewById(R.id.tvTeamName)
        tvTeamHint = findViewById(R.id.tvTeamHint)
        btnTeamAdd = findViewById(R.id.btnTeamAdd)
        btnTeamRename = findViewById(R.id.btnTeamRename)
        btnTeamDelete = findViewById(R.id.btnTeamDelete)
        lblTeamWeak = findViewById(R.id.lblTeamWeak)
        gridTeamWeak = findViewById(R.id.gridTeamWeak)
        lblTeamGaps = findViewById(R.id.lblTeamGaps)
        gridTeamGaps = findViewById(R.id.gridTeamGaps)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupListeners() {
        cardBattleTab.setOnClickListener { viewModel.onBattleTabClicked() }
        btnVariantToggle.setOnClickListener { viewModel.onVariantToggleClicked() }
        btnBack.setOnClickListener { viewModel.onBackToListClicked() }
        navLeftBtn.setOnClickListener { viewModel.onPrevClicked() }
        navRightBtn.setOnClickListener { viewModel.onNextClicked() }

        btnOpenTeam.setOnClickListener { viewModel.onTeamBuilderClicked() }
        btnTeamBuilderCard.setOnClickListener { viewModel.onTeamBuilderClicked() }

        btnLeftFab.setOnClickListener {
            viewModel.onJumpToOpponentClicked()
        }

        teamCells.forEachIndexed { index, cell ->
            cell.setOnClickListener {
                val pokemon = viewModel.teamList.value?.getOrNull(index)
                if (pokemon != null) viewModel.onPokemonSelectedFromTeam(pokemon)
                else viewModel.startSelectingForTeam(index)
            }
            cell.setOnLongClickListener {
                showSlotMenu(cell, index)
                true
            }
        }

        tvTeamName.setOnClickListener { showTeamSwitcher() }
        findViewById<View>(R.id.btnTeamShare).setOnClickListener { anchor ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            android.widget.PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Copy team as text")
                menu.add(0, 2, 1, "Paste team from clipboard")
                setOnMenuItemClickListener { item ->
                    if (item.itemId == 1) {
                        val text = viewModel.exportTeamText()
                        if (text.isBlank()) toast("Team is empty")
                        else {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Team", text))
                            toast("Team copied")
                        }
                    } else {
                        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
                        val count = viewModel.importTeamText(text)
                        toast(if (count > 0) "Imported $count Pokémon" else "No Pokémon from this game found in clipboard")
                    }
                    true
                }
            }.show()
        }
        btnTeamAdd.setOnClickListener { promptTeamName("New team", "") { viewModel.createTeam(it) } }
        btnTeamRename.setOnClickListener {
            promptTeamName("Rename team", viewModel.activeTeam.value?.name ?: "") { viewModel.renameActiveTeam(it) }
        }
        btnTeamDelete.setOnClickListener {
            val name = viewModel.activeTeam.value?.name ?: return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete \"$name\"?")
                .setMessage("This team will be removed from ${viewModel.teamScopeName()}.")
                .setPositiveButton("Delete") { _, _ -> viewModel.deleteActiveTeam() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // teams open from the search bar (the floating button is gone)
        searchContainer.setStartIconDrawable(android.R.drawable.ic_menu_myplaces)
        searchContainer.startIconContentDescription = "Team Builder"
        searchContainer.setStartIconOnClickListener { viewModel.onTeamBuilderClicked() }

        searchContainer.setEndIconOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        etSearch.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        etSearch.setSingleLine()
        etSearch.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        rvList.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: androidx.recyclerview.widget.RecyclerView, state: Int) {
                if (state == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.onSearchQuery(s.toString().trim())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateCardUI(pokemon: Pokemon) {
        val mechanics = com.enrpau.dualscreendex.data.RomManager.currentProfile.baseMechanics
        val (t1, t2) = GenerationHelper.getGenSpecificTypes(pokemon, mechanics)
        
        val theme = ThemeManager.currentTheme
        val displayBaseColor = t1.colorHex
        val cleanName = pokemon.name.replaceFirstChar { it.uppercase() }
        val displayName = if (pokemon.variantLabel != null) "$cleanName (${pokemon.variantLabel})" else cleanName

        tvName.text = displayName
        // game dex number first ("Hisui #001"), national number after it
        tvId.text = if (pokemon.dexNumber != null && pokemon.dexLabel != null)
            String.format("%s #%03d  ·  #%03d", pokemon.dexLabel, pokemon.dexNumber, pokemon.id)
        else String.format("#%03d", pokemon.id)
        SpriteManager.bindSprite(ivTargetSprite, pokemon)
        ivTargetSprite.visibility = if (ivTargetSprite.drawable != null) View.VISIBLE else View.GONE
        ivTargetSprite.background = null
        ivTargetSprite.setPadding(0, 0, 0, 0)

        val density = resources.displayMetrics.density
        val headerInner = cardHeader.getChildAt(0)
        val dataInner = cardData.getChildAt(0)
        val headerLp = cardHeader.layoutParams as android.view.ViewGroup.MarginLayoutParams
        val dataLp = cardData.layoutParams as android.view.ViewGroup.MarginLayoutParams

        // the whole header takes the pokemon's type colours (gen 1 stays on its green screen)
        val fill = headerTypeFill(t1, t2)
        val headerAvg = fill?.let { ColorUtils.blendARGB(it.first.first(), it.first.last(), 0.5f) } ?: theme.headerColor
        val onHeader = if (fill == null) theme.headerTextColor else readableOn(headerAvg)
        val onHeaderSub = ThemeManager.ui(ColorUtils.setAlphaComponent(onHeader, 200), headerAvg)
        tvName.setTextColor(onHeader)
        tvId.setTextColor(onHeader)
        listOf(tvArrowLeft, tvArrowRight, tvPrevName, tvNextName).forEach { it.setTextColor(onHeaderSub) }
        (btnTeamBuilderCard as? android.widget.ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(onHeaderSub)

        if (theme.isRetroScreen) {
            // game-style windows: two separate framed boxes on the patterned screen
            containerBattle.background = ThemeManager.screenBackground(this)
            listOf(cardHeader, cardData).forEach {
                it.setCardBackgroundColor(Color.TRANSPARENT)
                it.cardElevation = 0f
                it.radius = 0f
            }
            headerInner.background = ThemeManager.boxDrawable(this, theme.headerColor,
                fillColors = fill?.first, fillOffsets = fill?.second)
            dataInner.background = ThemeManager.boxDrawable(this)
            val m = (10 * density).toInt()
            headerLp.setMargins(m, m, m, 0)
            dataLp.setMargins(m, m, m, 0)
        } else {
            containerBattle.background = null
            containerBattle.setBackgroundColor(ColorUtils.blendARGB(theme.windowBackground, displayBaseColor, 0.06f))
            headerInner.background = fill?.let {
                GradientDrawable(GradientDrawable.Orientation.TL_BR, it.first).apply { cornerRadius = theme.cardCornerRadius }
            }
            dataInner.background = null
            cardHeader.setCardBackgroundColor(headerAvg)
            cardHeader.radius = theme.cardCornerRadius
            cardHeader.cardElevation = 8f
            cardData.setCardBackgroundColor(theme.gridBackgroundColor)
            cardData.radius = theme.cardCornerRadius
            cardData.cardElevation = 4f
            headerLp.setMargins(0, 0, 0, 0)
            dataLp.setMargins(0, if (resources.configuration.screenHeightDp < 640) (6 * density).toInt() else (-20 * density).toInt(), 0, 0)
        }
        if (resources.configuration.screenWidthDp > resources.configuration.screenHeightDp * 1.15f) {
            val m = (8 * density).toInt()
            headerLp.setMargins(m, m, m / 2, m)
            dataLp.setMargins(m / 2, m, m, m)
            styleTeamPanel()
        }
        cardHeader.layoutParams = headerLp
        cardData.layoutParams = dataLp
        tvId.background = ThemeManager.shape(this, ThemeManager.ui(ColorUtils.setAlphaComponent(onHeader, 34), headerAvg),
            if (theme.boxStyle == BoxStyle.GB) 4f else 8f)
        if (viewModel.hasVariants()) {
            btnVariantToggle.visibility = View.VISIBLE
            btnVariantToggle.text = "Switch to ${viewModel.getNextVariantName()}"
            btnVariantToggle.setTextColor(theme.labelTextColor)
            btnVariantToggle.setStrokeColor(android.content.res.ColorStateList.valueOf(theme.labelTextColor))
        } else {
            btnVariantToggle.visibility = View.GONE
        }

        lblWeak.setTextColor(theme.labelTextColor)
        lblResist.setTextColor(theme.labelTextColor)
        renderStats(pokemon)

        layoutTypes.removeAllViews()
        addFullWidthTypeBadge(layoutTypes, t1)
        if (t2 != PokemonType.UNKNOWN) {
            addFullWidthTypeBadge(layoutTypes, t2)
        }
        ThemeManager.applyFont(containerBattle)

        // small entrance: the card fades in and the sprite pops up
        cardHeader.alpha = 0.4f
        cardHeader.animate().alpha(1f).setDuration(180).start()
        ivTargetSprite.alpha = 0f
        ivTargetSprite.animate().alpha(1f).setDuration(220).setStartDelay(60).start()
        fitWideToScreen(true)
    }

    /**
     * Fits the card to small handheld bottom screens (AYN Thor, Retroid Pocket Duo, RG DS...):
     * short screens get a compact card so the sprite and "weak against" fit without scrolling,
     * and wide screens put the pokemon and its matchups side by side.
     */
    private fun fitCardToScreen() {
        val cfg = resources.configuration
        val d = resources.displayMetrics.density
        val compact = cfg.screenHeightDp < 640
        val sideBySide = cfg.screenWidthDp > cfg.screenHeightDp * 1.15f
        fun dp(v: Int) = (v * d).toInt()

        // pokemon and its data next to each other on wide screens
        val col = (containerBattle as android.view.ViewGroup).getChildAt(0) as LinearLayout
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, 0, 0, if (sideBySide) dp(16) else dp(96))
        arrangeTeamPanel(col, sideBySide)
        val dataLp = cardData.layoutParams as LinearLayout.LayoutParams
        if (sideBySide) { dataLp.width = 0; dataLp.weight = 1f } else { dataLp.width = LinearLayout.LayoutParams.MATCH_PARENT; dataLp.weight = 0f }
        cardData.layoutParams = dataLp

        // sprite: a share of the screen height instead of a fixed size
        val spriteH = if (sideBySide) (cfg.screenHeightDp * 0.22f).toInt().coerceIn(70, 130)   // leaves room for the team panel
            else if (compact) (cfg.screenHeightDp * 0.27f).toInt().coerceIn(84, 170) else 208
        // wide: the sprite takes whatever height the pokemon card has left (grows into empty space)
        (ivTargetSprite.layoutParams as LinearLayout.LayoutParams).let { lp ->
            lp.height = if (sideBySide) 0 else dp(spriteH)
            lp.weight = if (sideBySide) 1f else 0f
            lp.width = if (sideBySide) LinearLayout.LayoutParams.MATCH_PARENT else dp((spriteH * 1.12f).toInt())
            ivTargetSprite.layoutParams = lp
        }
        var v: View = ivTargetSprite
        while (v.parent !== cardHeader) {
            v = v.parent as View
            val lp = v.layoutParams
            // the box holding the sprite flexes inside its column; the outer column fills the card
            if (lp is LinearLayout.LayoutParams && v.parent !== cardHeader) {
                lp.height = if (sideBySide) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
                lp.weight = if (sideBySide) 1f else 0f
            } else lp.height = if (sideBySide) android.view.ViewGroup.LayoutParams.MATCH_PARENT else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            v.layoutParams = lp
        }
        if (!sideBySide) col.layoutParams = col.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT }

        // long names ("CHARMANDER", "CHARIZARD (MEGA X)") shrink to fit instead of breaking mid-word
        val nameMax = if (compact || sideBySide) 24f else 38f
        tvName.setTag(R.id.tag_original_textsize, nameMax * resources.displayMetrics.scaledDensity)
        // one line in a fixed full-width box, so the text shrinks to fit rather than growing the header
        tvName.maxLines = 1
        tvName.layoutParams = tvName.layoutParams.apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
            height = (nameMax * 1.35f * resources.displayMetrics.scaledDensity).toInt()
        }
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tvName, 12, nameMax.toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        findViewById<View>(R.id.navNameRow).visibility = if (compact || sideBySide) View.GONE else View.VISIBLE

        // tighter header rows
        val arrowRow = (tvArrowLeft.parent as View).parent as View
        arrowRow.setPadding(arrowRow.paddingLeft, dp(if (compact || sideBySide) 4 else 20), arrowRow.paddingRight, dp(if (compact || sideBySide) 0 else 8))
        listOf(tvArrowLeft, tvArrowRight, btnTeamBuilderCard).forEach { v ->
            v.layoutParams = v.layoutParams.apply { val s = dp(if (compact || sideBySide) 34 else 44); width = s; height = s }
        }
        (tvId.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin = dp(if (compact || sideBySide) 0 else 4)
        // "Hoenn #043 · #291" on one line, shrinking a little if the theme's font is wide
        tvId.maxLines = 1
        tvId.layoutParams = tvId.layoutParams.apply { height = dp(28) }
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tvId, 8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        layoutTypes.layoutParams = (layoutTypes.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
            height = dp(if (compact || sideBySide) 32 else 46); topMargin = dp(if (compact || sideBySide) 4 else 16)
        }
        layoutTypes.setPadding(layoutTypes.paddingLeft, 0, layoutTypes.paddingRight, dp(if (compact || sideBySide) 8 else 24))
        cardData.getChildAt(0).let { val tight = compact || sideBySide
            it.setPadding(dp(if (tight) 14 else 24), dp(if (tight) 10 else 24), dp(if (tight) 14 else 24), dp(if (tight) 10 else 24)) }

        val small = compact || sideBySide
        listOf(btnLeftFab, btnBack, btnOpenTeam).forEach { v ->
            (v as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.let { fab ->
                fab.size = if (small) com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI
                    else com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_NORMAL
                (fab.layoutParams as android.view.ViewGroup.MarginLayoutParams).setMargins(dp(if (small) 12 else 24), dp(if (small) 12 else 24), dp(if (small) 12 else 24), dp(if (small) 12 else 24))
                fab.requestLayout()
            }
        }
        // wide screens: weak and resistant are two narrow columns, one badge per line each
        badgeColumns = if (sideBySide) 1 else 3
        lblWeak.text = if (sideBySide) "Weak to" else getString(R.string.weak_against)
        lblResist.text = if (sideBySide) "Resists" else getString(R.string.resistant_against)
        (gridWeak.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin = dp(if (sideBySide) 6 else 24)
        val lblStats = findViewById<TextView>(R.id.lblStats)
        (lblStats.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = dp(if (sideBySide) 8 else 24)
        listOf<TextView>(lblWeak, lblResist, lblStats).forEach {
            (it.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin = dp(if (sideBySide) 4 else 10)
        }
    }

    /** Matchup badges per row: 3 normally, 2 in the narrow side-by-side column. */
    private var badgeColumns = 3

    private var topRow: LinearLayout? = null
    private var matchRow: LinearLayout? = null
    private var teamPanel: LinearLayout? = null

    /**
     * Wide screens (Thor, Retroid Pocket Duo, RG DS):
     *   top row    = pokemon card | data card (weak + resistant side by side, stats below)
     *   bottom row = "your team vs this" across the full width, members as tiles
     * Tall screens: one column again, team section at the end of the data card.
     */
    private fun arrangeTeamPanel(col: LinearLayout, sideBySide: Boolean) {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val dataInner = cardData.getChildAt(0) as LinearLayout
        if (sideBySide) {
            if (topRow == null) {
                // top row: pokemon and data next to each other
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val idx = col.indexOfChild(cardHeader)
                col.removeView(cardHeader)
                col.removeView(cardData)
                // both cards stretch to the same height, and the row takes whatever the team panel leaves
                row.addView(cardHeader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                row.addView(cardData, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                col.addView(row, idx, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

                // weak and resistant as two columns inside the data card
                val match = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val weakCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val resistCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val at = dataInner.indexOfChild(lblWeak)
                listOf<View>(lblWeak, gridWeak).forEach { dataInner.removeView(it); weakCol.addView(it) }
                listOf<View>(lblResist, gridResist).forEach { dataInner.removeView(it); resistCol.addView(it) }
                match.addView(weakCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                match.addView(resistCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
                dataInner.addView(match, at)

                // team across the whole width underneath
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    // retro boxes have thick borders, keep the title clear of them
                    setPadding(dp(16), dp(12), dp(16), dp(10))
                }
                col.addView(panel, idx + 1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(8), dp(4), dp(8), dp(8))
                })
                topRow = row; matchRow = match; teamPanel = panel
            }
            val panel = teamPanel!!
            listOf<View>(lblCounters, listCounters).forEach { v ->
                if (v.parent !== panel) { (v.parent as? android.view.ViewGroup)?.removeView(v); panel.addView(v) }
            }
            (lblCounters.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply { topMargin = 0; bottomMargin = dp(4) }
            (listCounters.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = 0
            listCounters.setPadding(0, 0, 0, 0)
            (lblResist.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = 0
        } else {
            topRow?.let { row ->
                val idx = col.indexOfChild(row)
                row.removeAllViews()
                col.removeView(row)
                col.addView(cardHeader, idx, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                col.addView(cardData, idx + 1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                matchRow?.let { match ->
                    val at = dataInner.indexOfChild(match)
                    listOf<View>(lblWeak, gridWeak, lblResist, gridResist).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                    dataInner.removeView(match)
                    listOf<View>(lblWeak, gridWeak, lblResist, gridResist).forEachIndexed { i, v -> dataInner.addView(v, at + i) }
                }
                teamPanel?.let { col.removeView(it) }
                findViewById<View>(R.id.listStats).let { it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { height = LinearLayout.LayoutParams.WRAP_CONTENT; weight = 0f } }
                dataInner.layoutParams = dataInner.layoutParams.apply { height = LinearLayout.LayoutParams.WRAP_CONTENT }
                ivTargetSprite.setPadding(0, 0, 0, 0)
                listOf<View>(lblCounters, listCounters).forEach { v -> (v.parent as? android.view.ViewGroup)?.removeView(v); dataInner.addView(v) }
                (lblCounters.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = dp(24)
            }
            topRow = null; matchRow = null; teamPanel = null
        }
        styleTeamPanel()
    }

    /**
     * Dual-screen handhelds (Thor, RP Duo...): the game runs on the main screen, so if we were opened there
     * (Android Studio, the top launcher) reopen on the second screen. Devices whose main display is the
     * physical bottom screen pick "App Screen: MAIN" in settings and we move the other way.
     * Returns true when moving.
     */
    private fun moveToSecondScreen(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
        val wantMain = getSharedPreferences("DualDexPrefs", MODE_PRIVATE).getString("APP_SCREEN", "second") == "main"
        val onMain = display?.displayId == android.view.Display.DEFAULT_DISPLAY
        if (wantMain == onMain) return false
        val target = if (wantMain) android.view.Display.DEFAULT_DISPLAY else {
            val dm = getSystemService(android.hardware.display.DisplayManager::class.java) ?: return false
            dm.displays.firstOrNull {
                it.displayId != android.view.Display.DEFAULT_DISPLAY && it.state == android.view.Display.STATE_ON &&
                    (it.flags and android.view.Display.FLAG_PRIVATE) == 0
            }?.displayId ?: return false
        }
        return try {
            val opts = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(target)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK), opts.toBundle())
            finish()
            true
        } catch (e: Exception) { false }
    }

    /** Badge height in the wide layout; shrinks for pokemon with many weaknesses (Celebi has 7). */
    private var slimBadgeDp = 24
    private var statsTwoCol = false

    /**
     * Wide layout: the page is pinned to the screen height (no scrolling). The team strip takes what it
     * needs at the bottom, the two cards share the rest, and the sprite grows or shrinks to fill the
     * pokemon card. If the data card still overflows (Celebi has 7 weaknesses), the badges step down.
     */
    private fun fitWideToScreen(reset: Boolean) {
        val row = topRow ?: return
        if (reset && slimBadgeDp != 24) { slimBadgeDp = 24; refreshMatchups() }
        if (reset) {
            val want = maxOf(viewModel.weaknessList.value?.size ?: 0, viewModel.resistanceList.value?.size ?: 0) > 3
            if (want != statsTwoCol) viewModel.displayedPokemon.value?.let { renderStats(it); ThemeManager.applyFont(cardData) }
        }
        val scroll = containerBattle as android.view.ViewGroup
        scroll.post {
            if (topRow !== row || scroll.height == 0) return@post
            // a ScrollView never limits its child, so the top row gets an exact height: screen minus team strip
            val col = scroll.getChildAt(0) as LinearLayout
            var used = col.paddingTop + col.paddingBottom
            for (i in 0 until col.childCount) {
                val c = col.getChildAt(i)
                if (c === row || c.visibility == View.GONE) continue
                val m = c.layoutParams as android.view.ViewGroup.MarginLayoutParams
                used += c.height + m.topMargin + m.bottomMargin
            }
            val rowH = scroll.height - used
            // both cards the full row height; the data card spreads its content instead of leaving a gap below
            listOf<View>(cardHeader, cardData).forEach { c ->
                if (c.layoutParams.height != LinearLayout.LayoutParams.MATCH_PARENT)
                    c.layoutParams = c.layoutParams.apply { height = LinearLayout.LayoutParams.MATCH_PARENT }
            }
            val dataInner = cardData.getChildAt(0) as LinearLayout
            if (dataInner.layoutParams.height != LinearLayout.LayoutParams.MATCH_PARENT) {
                dataInner.layoutParams = dataInner.layoutParams.apply { height = LinearLayout.LayoutParams.MATCH_PARENT }
                findViewById<View>(R.id.listStats).let { it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { height = 0; weight = 1f } }
            }
            // pixel sprites look huge blown up to the whole card; cap them (gen 1 fills its whole canvas)
            val cap = (124 * resources.displayMetrics.density).toInt()
            val extra = ((ivTargetSprite.height - cap) / 2).coerceAtLeast(0)
            if (ivTargetSprite.paddingTop != extra) ivTargetSprite.setPadding(0, extra, 0, extra)
            if (rowH > 0 && row.layoutParams.height != rowH) {
                row.layoutParams = row.layoutParams.apply { height = rowH }
                fitWideToScreen(false); return@post
            }
            val inner = cardData.getChildAt(0)
            inner.measure(View.MeasureSpec.makeMeasureSpec(cardData.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            if (inner.measuredHeight > cardData.height && slimBadgeDp > 17) {
                slimBadgeDp -= 2
                refreshMatchups()
                fitWideToScreen(false)
            }
        }
    }

    private fun refreshMatchups() {
        viewModel.weaknessList.value?.let { populateSmartGrid(gridWeak, it); ThemeManager.applyFont(gridWeak) }
        viewModel.resistanceList.value?.let { populateSmartGrid(gridResist, it); ThemeManager.applyFont(gridResist) }
    }

    /** The team panel looks like the data card of the current theme; hidden when there's no team. */
    private fun styleTeamPanel() {
        val panel = teamPanel ?: return
        val theme = ThemeManager.currentTheme
        panel.background = if (theme.isRetroScreen) ThemeManager.boxDrawable(this)
            else ThemeManager.shape(this, theme.gridBackgroundColor, theme.cardCornerRadius / resources.displayMetrics.density)
        panel.visibility = listCounters.visibility
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        fitCardToScreen()
        viewModel.displayedPokemon.value?.let { updateCardUI(it) }
    }

    /**
     * Type colours for the card header: a diagonal fade from type 1 to type 2 (one colour for single types).
     * It's a soft tint of the type colours, so the full-strength type badges and the sprite stand out on it.
     * Game Boy and Game Boy Color keep their plain text-box header.
     */
    private fun headerTypeFill(t1: PokemonType, t2: PokemonType): Pair<IntArray, FloatArray?>? {
        val theme = ThemeManager.currentTheme
        if (theme.monochrome || theme.gbc15Bit) return null
        val dark = theme.isDark || theme.boxStyle == BoxStyle.FLAT
        fun tint(c: Int) =
            if (dark) ColorUtils.blendARGB(c, theme.windowBackground, 0.62f)
            else ColorUtils.blendARGB(c, Color.WHITE, 0.6f)
        val c1 = tint(t1.colorHex)
        val c2 = tint(if (t2 != PokemonType.UNKNOWN) t2.colorHex else t1.colorHex)
        return Pair(intArrayOf(c1, c2), null)
    }

    /** Dark or light text, whichever reads better on [bg]. */
    private fun readableOn(bg: Int): Int {
        val theme = ThemeManager.currentTheme
        val light = if (theme.gbc15Bit) "#F8F8F8".toColorInt() else Color.WHITE
        val dark = if (ColorUtils.calculateLuminance(theme.listTextColor) < 0.3) theme.listTextColor else "#1E1E1E".toColorInt()
        return if (ColorUtils.calculateLuminance(bg) > 0.42) dark else light
    }
    private fun loadSettings() {
        viewModel.refreshSettings()
        applyThemeColors()
        if (::adapter.isInitialized) {
            val currentMechanics = com.enrpau.dualscreendex.data.RomManager.currentProfile.baseMechanics
            val currentTheme = ThemeManager.currentTheme
            adapter.updateSettings(currentMechanics, currentTheme)
            val freshList = viewModel.repository.getAllPokemon()
            adapter.updateList(freshList)
        }
    }

    private fun applyThemeColors() {
        val theme = ThemeManager.currentTheme
        val root = findViewById<View>(R.id.main_root)

        root.background = ThemeManager.screenBackground(this)
        window.statusBarColor = theme.windowBackground
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(theme.windowBackground) > 0.5

        containerPokedex.background = ThemeManager.screenBackground(this)
        containerPokedex.setPadding(0, 0, 0, 0)
        containerTeamBuilder.background = ThemeManager.screenBackground(this)

        // text that sits directly on the screen background (not inside a box)
        val onScreen = theme.screenTextColor
        val onScreenSub = ThemeManager.ui(ColorUtils.setAlphaComponent(onScreen, 190), theme.windowBackground)
        if (theme.isRetroScreen) {
            // screen titles get their own little window, like a game menu header
            tvScreenTitle.background = ThemeManager.boxDrawable(this, theme.headerColor)
            val p = (12 * resources.displayMetrics.density).toInt()
            tvScreenTitle.setPadding(p * 2, p, p * 2, p)
            tvScreenTitle.setTextColor(theme.headerTextColor)
            tvTeamName.setTextColor(onScreen)
        } else {
            tvScreenTitle.background = null
            tvScreenTitle.setPadding(0, 0, 0, 0)
            tvScreenTitle.setTextColor(theme.headerTextColor)
            tvTeamName.setTextColor(theme.headerTextColor)
        }
        tvScreenTitle.textSize = if (theme.retroFont) 22f else 30f

        tvTeamGame.setTextColor(onScreenSub)
        tvTeamHint.setTextColor(onScreenSub)
        lblTeamWeak.setTextColor(onScreen)
        lblTeamGaps.setTextColor(onScreen)
        val teamIconTint = android.content.res.ColorStateList.valueOf(onScreen)
        listOf(btnTeamAdd, btnTeamRename, btnTeamDelete, findViewById<android.widget.ImageView>(R.id.btnTeamShare)).forEach { it.imageTintList = teamIconTint }
        (btnOpenTeam as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.let {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.fabColor)
            it.imageTintList = android.content.res.ColorStateList.valueOf(theme.fabIconColor)
        }
        (btnTeamBuilderCard as? android.widget.ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(theme.subTextColor)
        viewModel.teamList.value?.forEachIndexed { i, p -> teamCells.getOrNull(i)?.let { renderTeamCell(it, i, p) } }

        val searchBox = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchContainer)
        val etSearch = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearch)

        searchBox.boxBackgroundColor = theme.searchBoxColor
        etSearch.setTextColor(theme.searchTextColor)
        etSearch.setHintTextColor(theme.searchHintColor)
        searchBox.defaultHintTextColor = android.content.res.ColorStateList.valueOf(theme.searchHintColor)
        searchBox.setBoxStrokeColorStateList(android.content.res.ColorStateList.valueOf(theme.searchStrokeColor))
        searchBox.boxStrokeWidth = theme.searchStrokeWidth
        searchBox.boxStrokeWidthFocused = theme.searchStrokeWidth + 2
        searchBox.setBoxCornerRadii(theme.searchCornerRadius, theme.searchCornerRadius, theme.searchCornerRadius, theme.searchCornerRadius)

        val params = searchBox.layoutParams as android.view.ViewGroup.MarginLayoutParams
        val density = resources.displayMetrics.density
        val marginPx = (theme.searchMarginHorizontal * density).toInt()
        params.setMargins(marginPx, marginPx / 2, marginPx, marginPx / 2)
        searchBox.layoutParams = params

        val iconColor = theme.searchTextColor
        searchBox.setEndIconTintList(android.content.res.ColorStateList.valueOf(iconColor))
        searchBox.setStartIconTintList(android.content.res.ColorStateList.valueOf(iconColor))

        listOf(btnLeftFab, btnBack).forEach {
            (it as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.apply {
                backgroundTintList = android.content.res.ColorStateList.valueOf(theme.fabColor)
                imageTintList = android.content.res.ColorStateList.valueOf(theme.fabIconColor)
            }
        }
        cardBattleTab.radius = if (theme.isRetroScreen) 0f else 28f * density

        // glass buttons: see-through tint, a light rim and a top highlight (solid on the hardware-palette themes)
        listOf(btnLeftFab, btnBack, btnOpenTeam).forEach { v ->
            (v as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.let { fab ->
                if (ThemeManager.isLimitedPalette) {
                    fab.foreground = null
                    return@let
                }
                // apple-style liquid glass: perfectly round, barely tinted, glass highlights on top
                fab.shapeAppearanceModel = fab.shapeAppearanceModel.withCornerSize(
                    com.google.android.material.shape.RelativeCornerSize(0.5f))
                val tint = if (theme.isDark) Color.WHITE else theme.fabColor
                fab.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(tint, if (theme.isDark) 28 else 60))
                fab.imageTintList = android.content.res.ColorStateList.valueOf(if (theme.isDark) Color.WHITE else theme.fabIconColor)
                fab.compatElevation = 3f * density   // soft lift shadow
                fab.foreground = LiquidGlassDrawable(density, theme.isDark)            }
        }
        // shadows add soft in-between colours the game boy couldn't show
        val flat = ThemeManager.isLimitedPalette
        cardBattleTab.cardElevation = if (flat) 0f else 10f * density
        listOf(btnLeftFab, btnBack, btnOpenTeam).forEach {
            (it as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.compatElevation = if (flat) 0f else 6f * density
        }

        if (::adapter.isInitialized) {
            val currentMechanics = com.enrpau.dualscreendex.data.RomManager.currentProfile.baseMechanics
            adapter.updateSettings(currentMechanics, theme)
        }
        ThemeManager.applyFont(root)

        // pixel themes: the hint is drawn by the text field itself (pixel font, hard edges) instead of the floating label
        val hintText = getString(R.string.search_pokemon)
        if (theme.pixelFont) {
            searchBox.isHintEnabled = false
            etSearch.hint = hintText
        } else {
            etSearch.hint = null
            searchBox.isHintEnabled = true
            searchBox.hint = hintText
        }

        // hardware palette: gen 1 locks the whole screen to its 4 greens; gen 2 locks everything except
        // the sprites, which are authentic game boy color art with their own colours
        val gbcTargets = listOf<View>(tvScreenTitle, searchBox, findViewById(R.id.tvScannerBanner), btnOpenTeam, btnLeftFab, btnBack,
            cardBattleTab, btnTeamBuilderCard, tvArrowLeft, tvArrowRight, btnTeamAdd, btnTeamRename, btnTeamDelete,
            findViewById(R.id.btnTeamShare), btnVariantToggle)
        // without elevation, draw order decides: keep the floating buttons above every screen
        listOf<View>(cardBattleTab, btnOpenTeam, btnLeftFab, btnBack).forEach { it.bringToFront() }

        ThemeManager.applyLcd(window)

        lastListPalette = emptyList()
        if (!theme.gbc15Bit && android.os.Build.VERSION.SDK_INT >= 33) rvList.setRenderEffect(null)
        if (theme.monochrome && !ThemeManager.nativeColorSprites) {
            gbcTargets.forEach { if (android.os.Build.VERSION.SDK_INT >= 33) it.setRenderEffect(null) }
            ThemeManager.applyPaletteEffect(root)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 33) root.setRenderEffect(null)
            gbcTargets.forEach { ThemeManager.applyPaletteEffect(it) }
        }
    }

    private var lastListPalette: List<Int> = emptyList()

    /**
     * GBC: the screen may show at most 56 colours. The screen outside the list uses a handful (background, title,
     * search box, banner, buttons); the list gets the rest: its own row colours, then the visible sprites' colours,
     * most-used first. Any sprite colour that doesn't fit snaps to the nearest one that did.
     */
    private fun updateListPalette(spriteCounts: Map<Int, Int>) {
        val theme = ThemeManager.currentTheme
        if (!theme.gbc15Bit) return
        val outsideList = 12
        val budget = 56 - outsideList
        val palette = LinkedHashSet<Int>(ThemeManager.gbcRowColors())
        spriteCounts.entries.sortedByDescending { it.value }.forEach { if (palette.size < budget) palette += it.key }
        val list = palette.toList()
        if (list == lastListPalette) return
        lastListPalette = list
        ThemeManager.applyPaletteEffect(rvList, list.toIntArray())
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.clearFocus()
    }

    data class MatchupData(val type: PokemonType, val multiplier: Double)

    private var suggestionsPanel: LinearLayout? = null

    /**
     * Team builder: "Suggested teammates" under the team analysis. Each row says what the pokemon fixes
     * (resists the shared weaknesses, hits the coverage gaps); "+ Add" puts it in the first empty slot.
     */
    private fun renderSuggestions(list: List<MainViewModel.Suggestion>) {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val theme = ThemeManager.currentTheme
        val panel = suggestionsPanel ?: LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val parent = gridTeamGaps.parent as android.view.ViewGroup
            parent.addView(this, parent.indexOfChild(gridTeamGaps) + 1, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            suggestionsPanel = this
        }
        panel.removeAllViews()
        panel.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        if (list.isEmpty()) return
        panel.background = if (theme.isRetroScreen) ThemeManager.boxDrawable(this)
            else ThemeManager.shape(this, theme.gridBackgroundColor, theme.cardCornerRadius / d)

        panel.addView(TextView(this).apply {
            text = "SUGGESTED TEAMMATES"
            textSize = 12f
            letterSpacing = 0.08f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(theme.labelTextColor)
            setPadding(0, 0, 0, dp(6))
        })
        fun names(types: List<PokemonType>) = types.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        for (s in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val icon = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
            SpriteManager.bindSprite(icon, s.pokemon, icon = true)
            row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(8), 0) }
            textCol.addView(TextView(this).apply {
                text = displayNameOf(s.pokemon)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.listTextColor)
            })
            textCol.addView(TextView(this).apply {
                text = listOfNotNull(
                    s.resists.takeIf { it.isNotEmpty() }?.let { "resists ${names(it)}" },
                    s.hits.takeIf { it.isNotEmpty() }?.let { "hits ${names(it)}" }
                ).joinToString(" · ")
                textSize = 12f
                setTextColor(ColorUtils.setAlphaComponent(theme.listTextColor, 170))
            })
            row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "+ Add"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ThemeManager.ui(Color.WHITE))
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = ThemeManager.shape(this@MainActivity, ThemeManager.ui(theme.labelTextColor), 999f)
                setOnClickListener { viewModel.addSuggestedMember(s.pokemon) }
            })
            // tapping the rest of the row opens its card
            row.setOnClickListener { viewModel.onPokemonSelectedFromTeam(s.pokemon) }
            panel.addView(row)
        }
        ThemeManager.applyFont(panel)
    }
    private fun Int.blendWithWhite(ratio: Float): Int = ColorUtils.blendARGB(this, Color.WHITE, ratio)
    private fun Int.blendWithBg(ratio: Float): Int = ColorUtils.blendARGB(this, ThemeManager.currentTheme.windowBackground, ratio)

    private fun populateSmartGrid(container: LinearLayout, list: List<MatchupData>, countMode: Boolean = false) {
        fillRows(container, list) { createWeaknessBadgeView(it.type, it.multiplier, countMode) }
    }

    private fun populateTypeGrid(container: LinearLayout, list: List<PokemonType>) {
        fillRows(container, list) { type ->
            layoutInflater.inflate(R.layout.badge_weakness, null, false).apply {
                findViewById<TextView>(R.id.tvBadgeType).apply {
                    text = type.displayName
                    fitOneLine(this, 12f)
                    setBackgroundColor(ThemeManager.badgeBg(type.colorHex)); setTextColor(ThemeManager.badgeText())
                }
                findViewById<View>(R.id.tvBadgeMult).visibility = View.GONE
            }
        }
    }

    private fun <T> fillRows(container: LinearLayout, list: List<T>, makeView: (T) -> View) {
        container.removeAllViews()
        val d = resources.displayMetrics.density
        // wide screens: slimmer badges, tighter rows, so the data card stays as short as the pokemon card
        val slim = topRow != null
        for (rowItems in list.chunked(badgeColumns)) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = badgeColumns.toFloat()
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, if (slim) (3 * d).toInt() else 16)
                }
            }
            for (item in rowItems) {
                rowLayout.addView(makeView(item), LinearLayout.LayoutParams(0,
                    if (slim) (slimBadgeDp * d).toInt() else LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(8, 0, 8, 0)
                })
            }
            container.addView(rowLayout)
        }
    }

    private fun formatMult(mult: Double): String = when (mult) {
        0.5 -> "½"
        0.25 -> "¼"
        0.0 -> "0"
        else -> mult.toInt().toString()
    }

    /** Shrinks the text as needed to stay on one line (the wide pixel font would wrap "ELECTRIC"). */
    private fun fitOneLine(tv: TextView, maxSp: Float) {
        tv.maxLines = 1
        val max = if (ThemeManager.currentTheme.pixelFont) maxSp * 0.66f else maxSp
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tv, 5, max.toInt().coerceAtLeast(6), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    private fun createWeaknessBadgeView(type: PokemonType, mult: Double, countMode: Boolean = false): View {
        val view = layoutInflater.inflate(R.layout.badge_weakness, null, false)
        if (ThemeManager.isLimitedPalette) (view as? androidx.cardview.widget.CardView)?.radius = 0f
        view.findViewById<TextView>(R.id.tvBadgeType).apply {
            text = type.displayName
            fitOneLine(this, 12f)
            setBackgroundColor(ThemeManager.badgeBg(type.colorHex)); setTextColor(ThemeManager.badgeText())
        }
        view.findViewById<TextView>(R.id.tvBadgeMult).apply {
            text = if (countMode) mult.toInt().toString() else "× ${formatMult(mult)}"
            setBackgroundColor(ThemeManager.badgeBg(ColorUtils.blendARGB(type.colorHex, Color.BLACK, 0.35f), darker = true)); setTextColor(ThemeManager.badgeText())
        }
        return view
    }

    private fun displayNameOf(p: Pokemon): String {
        val name = p.name.replaceFirstChar { it.uppercase() }
        return if (p.variantLabel != null) "$name (${p.variantLabel})" else name
    }

    private fun renderTeamCell(cell: androidx.cardview.widget.CardView, index: Int, pokemon: Pokemon?) {
        val theme = ThemeManager.currentTheme
        val density = resources.displayMetrics.density
        cell.removeAllViews()
        cell.radius = if (theme.isRetroScreen) 0f else 16f * density

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }

        if (pokemon == null) {
            // opaque blend: a translucent card lets its own shadow show through as a box
            cell.setCardBackgroundColor(ColorUtils.blendARGB(theme.windowBackground, theme.listTextColor, 0.07f))
            content.addView(TextView(this).apply {
                text = "+"
                textSize = 32f
                gravity = android.view.Gravity.CENTER
                setTextColor(ThemeManager.ui(ColorUtils.setAlphaComponent(theme.listTextColor, 120)))
            })
            content.addView(TextView(this).apply {
                text = "Slot ${index + 1}"
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(ThemeManager.ui(ColorUtils.setAlphaComponent(theme.listTextColor, 120)))
            })
        } else {
            val mechanics = com.enrpau.dualscreendex.data.RomManager.currentProfile.baseMechanics
            val (t1, t2) = GenerationHelper.getGenSpecificTypes(pokemon, mechanics)
            cell.setCardBackgroundColor(ColorUtils.blendARGB(t1.colorHex, Color.BLACK, 0.45f))

            val sprite = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
            SpriteManager.bindSprite(sprite, pokemon)
            content.addView(sprite, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

            content.addView(TextView(this).apply {
                text = displayNameOf(pokemon)
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            val types = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            listOf(t1, t2).filter { it != PokemonType.UNKNOWN }.forEach { type ->
                types.addView(TextView(this).apply {
                    text = type.displayName.uppercase()
                    textSize = 9f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ThemeManager.badgeText())
                    setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
                    background = ThemeManager.shape(this@MainActivity, ThemeManager.badgeBg(type.colorHex), 10f)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins((2 * density).toInt(), (3 * density).toInt(), (2 * density).toInt(), 0)
                })
            }
            content.addView(types)
        }
        if (theme.isRetroScreen) {
            // slots become game windows; filled ones get a light wash of the pokemon's type colour
            cell.setCardBackgroundColor(Color.TRANSPARENT)
            cell.cardElevation = 0f
            val fill = pokemon?.let {
                val (t1, _) = GenerationHelper.getGenSpecificTypes(it, com.enrpau.dualscreendex.data.RomManager.currentProfile.baseMechanics)
                if (theme.boxStyle == BoxStyle.GB) theme.gridBackgroundColor
                else ColorUtils.blendARGB(theme.gridBackgroundColor, t1.colorHex, 0.22f)
            } ?: theme.gridBackgroundColor
            content.background = ThemeManager.boxDrawable(this, fill)
            for (i in 0 until content.childCount) {
                (content.getChildAt(i) as? TextView)?.setTextColor(theme.listTextColor)
            }
        } else {
            cell.cardElevation = 2f * density
        }
        ThemeManager.applyFont(content)
        cell.addView(content, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun renderCounters(list: List<MainViewModel.CounterData>) {
        listCounters.removeAllViews()
        val show = list.isNotEmpty()
        lblCounters.visibility = if (show) View.VISIBLE else View.GONE
        listCounters.visibility = if (show) View.VISIBLE else View.GONE
        styleTeamPanel()
        if (!show) return

        val theme = ThemeManager.currentTheme
        val density = resources.displayMetrics.density
        lblCounters.setTextColor(theme.labelTextColor)
        // wide screens: the team runs across the whole width as tiles, three per row
        if (teamPanel != null) {
            renderCounterTiles(list)
            ThemeManager.applyFont(listCounters)
            return
        }
        val tight = false
        val iconDp = if (tight) 30 else 40
        val nameSp = if (tight) 12f else 14f

        for (c in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
                isClickable = true
                setOnClickListener { viewModel.onPokemonSelectedFromTeam(c.pokemon) }
            }
            val icon = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
            SpriteManager.bindSprite(icon, c.pokemon, icon = true)
            row.addView(icon, LinearLayout.LayoutParams((iconDp * density).toInt(), (iconDp * density).toInt()))

            row.addView(TextView(this).apply {
                text = displayNameOf(c.pokemon)
                textSize = nameSp
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.listTextColor)
                setPadding((8 * density).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(counterPill("hits ×${formatMult(c.bestHit)}", c.bestHit >= 2.0, c.bestHit < 1.0, density).apply { if (tight) shrinkPill(this) })
            row.addView(counterPill("takes ×${formatMult(c.worstTaken)}", c.worstTaken < 1.0, c.worstTaken >= 2.0, density).apply { if (tight) shrinkPill(this) })
            listCounters.addView(row)
        }
        ThemeManager.applyFont(listCounters)
    }

    /** Team members as tiles (icon + name on top, hits / takes underneath), best counter first, all on one line. */
    private fun renderCounterTiles(list: List<MainViewModel.CounterData>) {
        fitWideToScreen(true)
        val theme = ThemeManager.currentTheme
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        // the whole team on one line: icon, name, then hits / takes stacked
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = list.size.coerceAtLeast(3).toFloat()
        }
        for (c in list) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(2), dp(2), dp(2))
                isClickable = true
                setOnClickListener { viewModel.onPokemonSelectedFromTeam(c.pokemon) }
            }
            val icon = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
            SpriteManager.bindSprite(icon, c.pokemon, icon = true)
            tile.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            tile.addView(TextView(this).apply {
                text = displayNameOf(c.pokemon)
                textSize = 13f
                maxLines = 1
                gravity = android.view.Gravity.CENTER
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.listTextColor)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            tile.addView(counterPill("hits ×${formatMult(c.bestHit)}", c.bestHit >= 2.0, c.bestHit < 1.0, d).apply {
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) }
            })
            tile.addView(counterPill("takes ×${formatMult(c.worstTaken)}", c.worstTaken < 1.0, c.worstTaken >= 2.0, d).apply {
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) }
            })
            row.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        listCounters.addView(row)
    }

    private fun shrinkPill(tv: TextView) {
        val d = resources.displayMetrics.density
        tv.textSize = 10f
        tv.setPadding((6 * d).toInt(), (3 * d).toInt(), (6 * d).toInt(), (3 * d).toInt())
        (tv.layoutParams as? LinearLayout.LayoutParams)?.marginStart = (4 * d).toInt()
    }

    private fun renderStats(pokemon: Pokemon) {
        val lbl = findViewById<TextView>(R.id.lblStats)
        val list = findViewById<LinearLayout>(R.id.listStats)
        list.removeAllViews()
        val stats = com.enrpau.dualscreendex.data.BaseStats.get(this, pokemon.id, pokemon.variantLabel)
        lbl.visibility = if (stats != null) View.VISIBLE else View.GONE
        list.visibility = lbl.visibility
        if (stats == null) return

        val theme = ThemeManager.currentTheme
        val density = resources.displayMetrics.density
        lbl.setTextColor(theme.labelTextColor)
        lbl.text = "Base stats · ${stats.total}"

        val rows = listOf("HP" to stats.hp, "Atk" to stats.atk, "Def" to stats.def,
            "SpA" to stats.spa, "SpD" to stats.spd, "Spe" to stats.spe)
        // wide screens: two columns (HP/Atk/Def | SpA/SpD/Spe) so the data card stays short
        // ...but only when the matchups are long; with few badges the bars get the full width (one column)
        statsTwoCol = topRow != null && maxOf(viewModel.weaknessList.value?.size ?: 0, viewModel.resistanceList.value?.size ?: 0) > 3
        val statCols = if (statsTwoCol) {
            val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            list.addView(h)
            List(2) { i ->
                LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also {
                    h.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i == 0) marginEnd = (10 * density).toInt()
                    })
                }
            }
        } else null
        var statIndex = 0
        for ((name, value) in rows) {
            // red (low) -> yellow -> green (high), like most dex sites
            val color = when {
                value < 50 -> "#E53935"
                value < 80 -> "#FB8C00"
                value < 100 -> "#FDD835"
                value < 130 -> "#7CB342"
                else -> "#00ACC1"
            }.toColorInt()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = if (statCols != null) 2 else 3
                setPadding(0, (pad * density).toInt(), 0, (pad * density).toInt())
            }
            row.addView(TextView(this).apply {
                text = name
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.listTextColor)
            }, LinearLayout.LayoutParams(((if (statCols != null) 30 else 40) * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(TextView(this).apply {
                text = value.toString()
                textSize = 12f
                gravity = android.view.Gravity.END
                setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                setTextColor(theme.listTextColor)
            }, LinearLayout.LayoutParams(((if (statCols != null) 26 else 36) * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = ((if (statCols != null) 6 else 10) * density).toInt()
            })
            // bar track + fill, scaled against 200 so most bars are readable
            val track = android.widget.FrameLayout(this).apply {
                background = ThemeManager.shape(this@MainActivity, ThemeManager.ui(ColorUtils.setAlphaComponent(theme.listTextColor, 25)), 5f)
            }
            track.addView(View(this).apply {
                background = ThemeManager.shape(this@MainActivity, ThemeManager.badgeBg(color, darker = true), 5f)
            }, android.widget.FrameLayout.LayoutParams(0, 0))
            row.addView(track, LinearLayout.LayoutParams(0, (10 * density).toInt(), 1f))
            track.post {
                // the bar fills up from empty
                val fill = track.getChildAt(0)
                val target = (track.width * (value.coerceAtMost(200) / 200f)).toInt()
                android.animation.ValueAnimator.ofInt(0, target).apply {
                    duration = 450
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener { a ->
                        fill.layoutParams = android.widget.FrameLayout.LayoutParams(a.animatedValue as Int, track.height)
                    }
                }.start()
            }
            // wide, one column: the bars spread over the card's spare height instead of leaving a gap
            if (statCols == null && topRow != null) list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            else (statCols?.get(statIndex / 3) ?: list).addView(row)
            statIndex++
        }
        ThemeManager.applyFont(list)
    }

    private fun counterPill(label: String, good: Boolean, bad: Boolean, density: Float): TextView {
        val color = when {
            good -> "#2E7D32".toColorInt()
            bad -> "#C62828".toColorInt()
            else -> "#616161".toColorInt()
        }
        return TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.badgeText())
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            background = ThemeManager.shape(this@MainActivity, if (bad) ThemeManager.badgeBg(color, darker = true) else ThemeManager.badgeBg(color), 12f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (6 * density).toInt()
            }
        }
    }

    private fun showSlotMenu(anchor: View, index: Int) {
        val filled = viewModel.teamList.value?.getOrNull(index) != null
        android.widget.PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (filled) "Replace" else "Add Pokémon")
            if (filled) {
                menu.add(0, 2, 1, "View details")
                menu.add(0, 3, 2, "Remove")
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> viewModel.startSelectingForTeam(index)
                    2 -> viewModel.teamList.value?.getOrNull(index)?.let { viewModel.onPokemonSelectedFromTeam(it) }
                    3 -> viewModel.removeTeamMember(index)
                }
                true
            }
        }.show()
    }

    private fun showTeamSwitcher() {
        val teams = viewModel.teamsForCurrentGame()
        val activeId = viewModel.activeTeam.value?.id
        val labels = teams.map { t ->
            val count = t.members.count { it != null }
            "${t.name}  ($count/6)"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Teams · ${viewModel.teamScopeName()}")
            .setSingleChoiceItems(labels, teams.indexOfFirst { it.id == activeId }) { dialog, which ->
                viewModel.switchTeam(teams[which])
                dialog.dismiss()
            }
            .setPositiveButton("New team") { _, _ -> promptTeamName("New team", "") { viewModel.createTeam(it) } }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun promptTeamName(title: String, initial: String, onDone: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            setText(initial)
            hint = "Team name"
            setSingleLine()
            selectAll()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val frame = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton("Save") { _, _ -> onDone(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addFullWidthTypeBadge(container: LinearLayout, type: PokemonType) {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = type.displayName.uppercase()
            setTextColor(ThemeManager.badgeText())
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            letterSpacing = 0.06f
            maxLines = 1
            background = ThemeManager.shape(this@MainActivity, ThemeManager.badgeBg(type.colorHex), 14f)
        }
        fitOneLine(tv, 13f)
        container.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
        })
    }

    // helper to force updates even when paused or in background
    private fun <T> androidx.lifecycle.LiveData<T>.observeForeverSafe(observer: (T) -> Unit) {
        val wrapper = androidx.lifecycle.Observer<T> { data ->
            observer(data)
        }
        this.observeForever(wrapper)

        lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                    this@observeForeverSafe.removeObserver(wrapper)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // "App Screen" may have just changed in settings
        if (moveToSecondScreen()) return
        loadSettings()
        // a new game means a new dex list: run the search again so the list matches the text in the box
        etSearch.text?.toString()?.takeIf { it.isNotEmpty() }?.let { etSearch.setText(it); etSearch.setSelection(it.length) }
        updateScannerBanner()
        // sprite style may have changed in settings
        viewModel.displayedPokemon.value?.let { SpriteManager.bindSprite(ivTargetSprite, it) }
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pokemonReceiver) } catch (_: IllegalArgumentException) { }   // never registered when we moved screens
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ThemeManager.hideNavigationBar(window)
    }
}
