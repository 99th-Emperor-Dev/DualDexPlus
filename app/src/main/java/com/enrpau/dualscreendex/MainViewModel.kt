package com.enrpau.dualscreendex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.enrpau.dualscreendex.data.RomManager
import com.enrpau.dualscreendex.data.RomProfile
import com.enrpau.dualscreendex.data.Team
import com.enrpau.dualscreendex.data.GameCatalog
import com.enrpau.dualscreendex.data.TeamManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PokemonRepository(application)

    private val resetHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val resetRunnable = Runnable {
        if (isLiveUpdateActive) {
            _isBattleMode.value = false
            _showBattleTab.value = false
        }
        battleList = emptyList()
        battleTabText.value = ""
    }

    private val _displayedPokemon = MutableLiveData<Pokemon>()
    val displayedPokemon: LiveData<Pokemon> = _displayedPokemon

    private val _pokedexList = MutableLiveData<List<Pokemon>>()
    val pokedexList: LiveData<List<Pokemon>> = _pokedexList

    private val _isBattleMode = MutableLiveData(false)
    val isBattleMode: LiveData<Boolean> = _isBattleMode

    private val _isTeamMode = MutableLiveData(false)
    val isTeamMode: LiveData<Boolean> = _isTeamMode

    private val _teamList = MutableLiveData<List<Pokemon?>>()
    val teamList: LiveData<List<Pokemon?>> = _teamList

    private val _activeTeam = MutableLiveData<Team>()
    val activeTeam: LiveData<Team> = _activeTeam

    // opponent matchups against each of your team members
    data class CounterData(val pokemon: Pokemon, val bestHit: Double, val bestHitType: PokemonType, val worstTaken: Double)
    val counterList = MutableLiveData<List<CounterData>>()

    // attacking type -> number of team members weak to it (only shared weaknesses)
    val teamWeaknesses = MutableLiveData<List<MainActivity.MatchupData>>()
    // declared up here: the team (and its suggestions) is loaded while the view model is still being built
    val teamSuggestions = MutableLiveData<List<Suggestion>>(emptyList())
    // types that none of the team's STAB types hit super-effectively
    val teamCoverageGaps = MutableLiveData<List<PokemonType>>()

    private fun loadTeam() {
        val team = TeamManager.activeTeam(getApplication(), teamScope())
        _activeTeam.value = team
        _teamList.value = team.members
        calculateTeamAnalysis(team.members)
        _displayedPokemon.value?.let { calculateCounters(it) }
    }

    // teams belong to the selected game when "Match dex to game" is on, otherwise to the Dex Version
    private fun teamScope(): String =
        GameCatalog.activeFamily(getApplication())?.takeIf { it.themeId != "dynamic" }?.let { "game_" + it.themeId }
            ?: RomManager.currentProfile.id

    /** Shown above the team: "Crystal", "Sword"... or the Dex Version name. */
    fun teamScopeName(): String = GameCatalog.activeName(getApplication()) ?: RomManager.currentProfile.name

    fun teamsForCurrentGame(): List<Team> = TeamManager.teamsFor(getApplication(), teamScope())

    fun switchTeam(team: Team) {
        TeamManager.setActive(getApplication(), team)
        loadTeam()
    }

    fun createTeam(name: String?) {
        val profileId = teamScope()
        val finalName = name?.takeIf { it.isNotBlank() } ?: TeamManager.nextDefaultName(getApplication(), profileId)
        TeamManager.createTeam(getApplication(), profileId, finalName)
        loadTeam()
    }

    fun renameActiveTeam(name: String) {
        val team = _activeTeam.value ?: return
        if (name.isBlank()) return
        TeamManager.renameTeam(getApplication(), team.id, name.trim())
        loadTeam()
    }

    fun deleteActiveTeam() {
        val team = _activeTeam.value ?: return
        TeamManager.deleteTeam(getApplication(), team.id)
        loadTeam()
    }

    /** Team as Showdown-style text (species only), e.g. for pasting into Showdown or a note. */
    fun exportTeamText(): String =
        _teamList.value.orEmpty().filterNotNull().joinToString("\n\n") { p ->
            val species = p.name.split(' ', '-').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
            when (p.variantLabel?.lowercase()) {
                null -> species
                "alolan" -> "$species-Alola"
                "galarian" -> "$species-Galar"
                "hisuian" -> "$species-Hisui"
                "paldean" -> "$species-Paldea"
                else -> species
            }
        }

    /** Fills the active team from Showdown text. Returns how many pokemon were recognised. */
    fun importTeamText(text: String): Int {
        val team = _activeTeam.value ?: return 0
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
        val all = repository.getAllPokemon()
        val regionSuffix = mapOf("alola" to "Alolan", "galar" to "Galarian", "hisui" to "Hisuian", "paldea" to "Paldean")

        val found = text.split(Regex("\\r?\\n\\s*\\r?\\n")).mapNotNull { block ->
            // "Nickname (Species) (M) @ Item" -> Species
            var line = block.lineSequence().firstOrNull { it.isNotBlank() }?.substringBefore("@")?.trim() ?: return@mapNotNull null
            line = line.replace(Regex("\\((M|F)\\)\\s*$"), "").trim()
            Regex("\\(([^)]+)\\)\\s*$").find(line)?.let { line = it.groupValues[1] }

            val parts = line.split('-')
            val region = regionSuffix[parts.last().lowercase()]
            val baseName = if (region != null) parts.dropLast(1).joinToString("-") else line
            val candidates = all.filter { p ->
                val n = norm(p.name)
                n == norm(baseName) || n.startsWith(norm(baseName)) && p.name.contains('-')
            }
            candidates.firstOrNull { it.variantLabel == region } ?: candidates.firstOrNull()
        }.take(TeamManager.TEAM_SIZE)

        if (found.isEmpty()) return 0
        found.forEachIndexed { i, p -> TeamManager.setMember(getApplication(), team.id, i, p) }
        for (i in found.size until TeamManager.TEAM_SIZE) TeamManager.setMember(getApplication(), team.id, i, null)
        loadTeam()
        return found.size
    }

    private fun setTeamMember(slot: Int, pokemon: Pokemon?) {
        val team = _activeTeam.value ?: return
        TeamManager.setMember(getApplication(), team.id, slot, pokemon)
        loadTeam()
    }

    private val _selectionIndex = MutableLiveData<Int>(-1)
    val selectionIndex: LiveData<Int> = _selectionIndex

    private val _showBattleTab = MutableLiveData(false)
    val showBattleTab: LiveData<Boolean> = _showBattleTab
    val battleTabText = MutableLiveData<String>()
    val battleTabColor = MutableLiveData<Int>()

    private var isLiveUpdateActive = false

    val isPrevButtonVisible = MutableLiveData<Boolean>()
    val isNextButtonVisible = MutableLiveData<Boolean>()

    private var fullList = repository.getAllPokemon()
    private var currentFilteredList = fullList
    private var battleList: List<Pokemon> = emptyList()
    private var userDismissedBattle = false

    val weaknessList = MutableLiveData<List<MainActivity.MatchupData>>()
    val resistanceList = MutableLiveData<List<MainActivity.MatchupData>>()

    private var selectedIndex = 0
    private var currentVariantList: List<Pokemon> = emptyList()
    private var currentVariantIndex = 0
    val prevPokemonName = MutableLiveData<String>()
    val nextPokemonName = MutableLiveData<String>()

    private var currentMechanics: RomProfile.Mechanics = RomManager.currentProfile.baseMechanics

    init {
        _pokedexList.value = fullList
        loadTeam()
        if (fullList.isNotEmpty()) selectPokemon(fullList[0])
    }

    fun refreshSettings() {
        GameCatalog.syncProfile(getApplication())
        repository.reloadDatabase()

        val newList = repository.getAllPokemon()
        _pokedexList.value = newList

        currentVariantList = emptyList()
        currentFilteredList = newList

        val profile = RomManager.currentProfile
        currentMechanics = profile.baseMechanics
        // the game may have changed in settings, so swap to that game's teams
        loadTeam()

        val current = displayedPokemon.value
        if (current != null) {
            val newVersion = newList.find { it.name.equals(current.name, true) && it.variantLabel == current.variantLabel }
                ?: newList.find { it.id == current.id }

            if (newVersion != null) {
                _displayedPokemon.value = newVersion!!
                calculateMatchups(newVersion)
            }
        }
    }

    fun onSearchQuery(query: String) {
        currentFilteredList = repository.filterPokemon(query)
        _pokedexList.value = currentFilteredList
    }

    fun onPokemonSelectedFromList(pokemon: Pokemon) {
        val sIdx = _selectionIndex.value ?: -1
        if (sIdx != -1) {
            setTeamMember(sIdx, pokemon)
            _selectionIndex.value = -1
            _isTeamMode.value = true
            return
        }

        isLiveUpdateActive = false
        selectedIndex = currentFilteredList.indexOfFirst { it.name == pokemon.name }
        if (selectedIndex == -1) selectedIndex = 0
        selectPokemon(pokemon)
        _isBattleMode.value = true
    }

    fun onScanResult(names: ArrayList<String>?, ids: ArrayList<Int>?, t1s: ArrayList<String>?, t2s: ArrayList<String>?) {
        resetHandler.removeCallbacks(resetRunnable)

        if (names.isNullOrEmpty()) {
            // a missed scan never closes the card you're reading; only the list forgets the opponent
            if (_isBattleMode.value != true) {
                battleList = emptyList()
                _showBattleTab.value = false
            }
            // single misses happen mid-battle; only several in a row (~6s) mean the battle is over
            if (++emptyScans >= 4) autoOpenBlocked = false
            return
        }
        emptyScans = 0

        val scanned = ArrayList<Pokemon>()
        val allPokemon = repository.getAllPokemon()

        for (i in names.indices) {
            val pId = ids?.getOrNull(i) ?: 0
            val t1 = PokemonType.fromString(t1s?.getOrNull(i) ?: "unknown")

            val match = allPokemon.firstOrNull {
                it.id == pId && it.type1 == t1
            } ?: allPokemon.firstOrNull { it.id == pId }

            if (match != null) {
                scanned.add(match)
            } else {
                scanned.add(Pokemon(names[i], pId, t1, PokemonType.UNKNOWN, null))
            }
        }

        battleList = scanned

        val label = battleList.joinToString(" & ") { it.name.replaceFirstChar { c -> c.uppercase() } }
        battleTabText.value = label
        if (battleList.isNotEmpty()) {
            battleTabColor.value = battleList[0].type1.colorHex
        }

        val hasOpponent = battleList.isNotEmpty()

        if (_isBattleMode.value == true) {
            // on a card nothing changes by itself (it used to flicker between scans); instead a button
            // with the detected pokemon's name appears, unless that pokemon is already on the card
            val shown = _displayedPokemon.value
            _showBattleTab.value = hasOpponent && battleList.none { it.id == shown?.id }
        } else if (_isTeamMode.value == true) {
            // Team builder - just show the tab
            _showBattleTab.value = hasOpponent
        } else {
            // Main Dex list
            if (hasOpponent && autoOpenBlocked) {
                // you closed this one: stay on the list, the name button is enough until the opponent changes
                _showBattleTab.value = true
            } else if (hasOpponent) {
                // AUTO JUMP ONLY FROM MAIN DEX
                isLiveUpdateActive = true
                _isBattleMode.value = true
                selectedIndex = 0
                selectPokemon(battleList[0])
                _showBattleTab.value = false
            } else {
                _showBattleTab.value = false
            }
        }
    }

    fun onBattleTabClicked() {
        _showBattleTab.value = false
        if (battleList.isNotEmpty()) {
            isLiveUpdateActive = true
            _isBattleMode.value = true
            selectedIndex = 0
            selectPokemon(battleList[0])
        }
    }

    fun onBackToListClicked() {
        _isBattleMode.value = false
        _isTeamMode.value = false
        isLiveUpdateActive = false
        _selectionIndex.value = -1
        _showBattleTab.value = battleList.isNotEmpty()
        userDismissedBattle = true
        if (battleList.isNotEmpty()) autoOpenBlocked = true
    }

    /** You closed a scanned card mid-battle: stay on the list (name button only) until the battle ends. */
    private var autoOpenBlocked = false
    private var emptyScans = 0

    fun onBackToBattleClicked() {
        onJumpToOpponentClicked()
    }

    fun onJumpToOpponentClicked() {
        if (battleList.isNotEmpty()) {
            isLiveUpdateActive = true
            _isBattleMode.value = true
            _isTeamMode.value = false
            selectedIndex = 0
            selectPokemon(battleList[0])
            _showBattleTab.value = false
        } else {
            // If nothing scanned, just return to a generic battle mode if needed
            _isTeamMode.value = false
            _isBattleMode.value = true
        }
        _selectionIndex.value = -1
    }

    fun onTeamBuilderClicked() {
        _isTeamMode.value = true
        _isBattleMode.value = false
        _selectionIndex.value = -1
    }

    // the slot keeps its current member until a replacement is actually picked
    fun startSelectingForTeam(index: Int) {
        _selectionIndex.value = index
        _isTeamMode.value = false
        _isBattleMode.value = false
    }

    fun cancelTeamSelection() {
        _selectionIndex.value = -1
        _isTeamMode.value = true
    }

    fun removeTeamMember(index: Int) {
        if (index !in 0 until TeamManager.TEAM_SIZE) return
        setTeamMember(index, null)
    }

    fun onPokemonSelectedFromTeam(pokemon: Pokemon) {
        isLiveUpdateActive = false
        selectPokemon(pokemon)
        _isBattleMode.value = true
        _isTeamMode.value = false
    }

    fun onNextClicked() = cycleSelection(1)
    fun onPrevClicked() = cycleSelection(-1)

    fun onVariantToggleClicked() {
        if (currentVariantList.size <= 1) return
        currentVariantIndex = (currentVariantIndex + 1) % currentVariantList.size

        val target = currentVariantList[currentVariantIndex]
        _displayedPokemon.value = target
        calculateMatchups(target)
    }

    private fun selectPokemon(pokemon: Pokemon) {
        if (currentVariantList.isEmpty() || !currentVariantList[0].name.equals(pokemon.name, true)) {
            currentVariantList = repository.getVariantsFor(pokemon.name)
        }

        if (currentVariantList.isNotEmpty()) {
            val matchIndex = currentVariantList.indexOfFirst { it.variantLabel == pokemon.variantLabel }
            if (matchIndex != -1) {
                currentVariantIndex = matchIndex
            } else {
                currentVariantIndex = if (currentVariantList.isNotEmpty()) 0 else 0
            }
        }

        val target = if (currentVariantList.isNotEmpty() && currentVariantIndex < currentVariantList.size) {
            currentVariantList[currentVariantIndex]
        } else {
            pokemon
        }
        _displayedPokemon.value = target
        calculateMatchups(target)

        // previous / next always walk the whole dex (in the game's order), even after a search or a scan
        val dex = repository.getAllPokemon()
        val currentIndex = dex.indexOfFirst { it.id == target.id && it.variantLabel == target.variantLabel }
            .takeIf { it != -1 } ?: dex.indexOfFirst { it.id == target.id }
        if (currentIndex != -1 && dex.size > 1) {
            fun getDisplayName(p: Pokemon): String {
                val name = p.name.replaceFirstChar { it.uppercase() }
                return if (p.variantLabel != null) "$name (${p.variantLabel})" else name
            }
            selectedIndex = currentIndex
            isPrevButtonVisible.value = true
            isNextButtonVisible.value = true
            prevPokemonName.value = getDisplayName(dex[(currentIndex - 1 + dex.size) % dex.size])
            nextPokemonName.value = getDisplayName(dex[(currentIndex + 1) % dex.size])
        } else {
            isPrevButtonVisible.value = false
            isNextButtonVisible.value = false
        }
    }

    private fun calculateMatchups(pokemon: Pokemon) {
        val weak = ArrayList<MainActivity.MatchupData>()
        val resist = ArrayList<MainActivity.MatchupData>()

        for (attacker in PokemonType.entries) {
            if (attacker == PokemonType.UNKNOWN) continue

            // Exclude types that don't exist in the current mechanics
            if (currentMechanics == RomProfile.Mechanics.GEN_1) {
                if (attacker == PokemonType.STEEL || attacker == PokemonType.DARK || attacker == PokemonType.FAIRY) continue
            } else if (currentMechanics == RomProfile.Mechanics.GEN_2_TO_5) {
                if (attacker == PokemonType.FAIRY) continue
            }

            val (t1, t2) = GenerationHelper.getGenSpecificTypes(pokemon, currentMechanics)

            val mult = TypeMatchup.getMultiplier(attacker, t1, getApplication()) *
                    (if (t2 != PokemonType.UNKNOWN) TypeMatchup.getMultiplier(attacker, t2, getApplication()) else 1.0)

            if (mult > 1.0) weak.add(MainActivity.MatchupData(attacker, mult))
            if (mult < 1.0) resist.add(MainActivity.MatchupData(attacker, mult))
        }

        weak.sortByDescending { it.multiplier }
        resist.sortBy { it.multiplier }

        weaknessList.value = weak
        resistanceList.value = resist
        calculateCounters(pokemon)
    }

    private fun typesOf(p: Pokemon): List<PokemonType> {
        val (t1, t2) = GenerationHelper.getGenSpecificTypes(p, currentMechanics)
        return listOf(t1, t2).filter { it != PokemonType.UNKNOWN }
    }

    private fun effectiveness(attacker: PokemonType, defender: Pokemon): Double =
        typesOf(defender).fold(1.0) { acc, t -> acc * TypeMatchup.getMultiplier(attacker, t, getApplication()) }

    private fun isTypeInGame(type: PokemonType): Boolean = when (currentMechanics) {
        RomProfile.Mechanics.GEN_1 -> type != PokemonType.STEEL && type != PokemonType.DARK && type != PokemonType.FAIRY
        RomProfile.Mechanics.GEN_2_TO_5 -> type != PokemonType.FAIRY
        else -> true
    } && type != PokemonType.UNKNOWN

    // how each team member fares against the pokemon on the card (STAB types only)
    private fun calculateCounters(opponent: Pokemon) {
        val members = _teamList.value?.filterNotNull().orEmpty()
        counterList.value = members.map { member ->
            val hits = typesOf(member).map { it to effectiveness(it, opponent) }
            val best = hits.maxByOrNull { it.second } ?: (PokemonType.UNKNOWN to 1.0)
            val worstTaken = typesOf(opponent).maxOfOrNull { effectiveness(it, member) } ?: 1.0
            CounterData(member, best.second, best.first, worstTaken)
        }.sortedWith(compareByDescending<CounterData> { it.bestHit }.thenBy { it.worstTaken })
    }

    private fun calculateTeamAnalysis(team: List<Pokemon?>) {
        val members = team.filterNotNull()
        if (members.isEmpty()) {
            teamWeaknesses.value = emptyList()
            teamCoverageGaps.value = emptyList()
            return
        }
        val allTypes = PokemonType.entries.filter { isTypeInGame(it) }

        teamWeaknesses.value = allTypes.mapNotNull { attacker ->
            val weak = members.count { effectiveness(attacker, it) > 1.0 }
            val resist = members.count { effectiveness(attacker, it) < 1.0 }
            if (weak >= 2 && weak > resist) MainActivity.MatchupData(attacker, weak.toDouble()) else null
        }.sortedByDescending { it.multiplier }

        val stab = members.flatMap { typesOf(it) }.toSet()
        teamCoverageGaps.value = allTypes.filter { defender ->
            stab.none { atk -> TypeMatchup.getMultiplier(atk, defender, getApplication()) > 1.0 }
        }
        calculateSuggestions(members)
    }

    /** A teammate idea: what it covers for the current team. */
    data class Suggestion(val pokemon: Pokemon, val resists: List<PokemonType>, val hits: List<PokemonType>)

    /**
     * Pokemon from this game's dex that patch the team's holes: they resist the types several members
     * are weak to, and their own types hit what the team can't hit super-effectively.
     */
    private fun calculateSuggestions(members: List<Pokemon>) {
        val weaknesses = teamWeaknesses.value.orEmpty().map { it.type }
        val gaps = teamCoverageGaps.value.orEmpty()
        if (members.size >= 6 || (weaknesses.isEmpty() && gaps.isEmpty())) {
            teamSuggestions.value = emptyList(); return
        }
        val taken = members.map { it.id }.toSet()
        teamSuggestions.value = repository.getAllPokemon().asSequence()
            .filter { it.id !in taken && it.variantLabel?.let { v -> v.startsWith("Mega") || v.startsWith("Primal") } != true }
            .map { c ->
                val resists = weaknesses.filter { effectiveness(it, c) < 1.0 }
                val weakToo = weaknesses.count { effectiveness(it, c) > 1.0 }
                val hits = gaps.filter { g -> typesOf(c).any { TypeMatchup.getMultiplier(it, g, getApplication()) > 1.0 } }
                val score = resists.size * 2 + hits.size - weakToo * 2
                Triple(Suggestion(c, resists, hits), score,
                    com.enrpau.dualscreendex.data.BaseStats.get(getApplication(), c.id, c.variantLabel)?.total ?: 0)
            }
            .filter { it.second > 0 }
            // best fit first, stronger pokemon break ties; one entry per species
            .sortedWith(compareByDescending<Triple<Suggestion, Int, Int>> { it.second }.thenByDescending { it.third })
            .distinctBy { it.first.pokemon.id }
            .take(6).map { it.first }.toList()
    }

    /** "+ Add" on a suggestion: first empty slot. */
    fun addSuggestedMember(pokemon: Pokemon) {
        val slot = _teamList.value?.indexOfFirst { it == null } ?: -1
        if (slot >= 0) setTeamMember(slot, pokemon)
    }

    private fun cycleSelection(direction: Int) {
        // whole dex, wrapping around (selectPokemon keeps selectedIndex in step with the card)
        val dex = repository.getAllPokemon()
        if (dex.isEmpty()) return
        val next = (selectedIndex + direction + dex.size) % dex.size
        isLiveUpdateActive = false
        selectPokemon(dex[next])
    }

    fun hasVariants(): Boolean = currentVariantList.size > 1
    fun getNextVariantName(): String {
        if (currentVariantList.isEmpty()) return ""
        val nextIdx = (currentVariantIndex + 1) % currentVariantList.size
        return currentVariantList[nextIdx].variantLabel ?: "Normal"
    }
}