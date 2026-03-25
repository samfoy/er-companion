package com.ercompanion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ercompanion.data.BuildsRepository
import com.ercompanion.data.PokemonBuild
import com.ercompanion.network.RetroArchClient
import com.ercompanion.parser.AddressScanner
import com.ercompanion.parser.Gen3PokemonParser
import com.ercompanion.parser.PartyMon
import com.ercompanion.savefile.SaveStateReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val saveStateReader = SaveStateReader(application)
    private val client = RetroArchClient()  // Keep as fallback
    private val scanner = AddressScanner(application, client)
    private val buildsRepository = BuildsRepository(application)

    enum class DataSource {
        UDP, SAVE_STATE, DISCONNECTED
    }

    private val _connectionState = MutableStateFlow(RetroArchClient.ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<RetroArchClient.ConnectionStatus> = _connectionState.asStateFlow()

    private val _dataSource = MutableStateFlow(DataSource.DISCONNECTED)
    val dataSource: StateFlow<DataSource> = _dataSource.asStateFlow()

    private val _partyState = MutableStateFlow<List<PartyMon?>>(emptyList())
    val partyState: StateFlow<List<PartyMon?>> = _partyState.asStateFlow()

    private val _enemyPartyState = MutableStateFlow<List<PartyMon?>>(emptyList())
    val enemyPartyState: StateFlow<List<PartyMon?>> = _enemyPartyState.asStateFlow()

    // Index of the active player party slot (0-5), -1 if unknown / not in battle
    private val _activePlayerSlot = MutableStateFlow(-1)
    val activePlayerSlot: StateFlow<Int> = _activePlayerSlot.asStateFlow()

    private val _scanningState = MutableStateFlow(false)
    val scanningState: StateFlow<Boolean> = _scanningState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    var debugManualPath: String = ""
    var preferUdp: Boolean = true  // User preference: try UDP first

    private val _buildsLoaded = MutableStateFlow(false)
    val buildsLoaded: StateFlow<Boolean> = _buildsLoaded.asStateFlow()

    private var pollingJob: Job? = null
    private var udpFailCount = 0
    private var lastUdpCheckTime = 0L

    init {
        startPolling()
        loadBuildsData()
    }

    private fun loadBuildsData() {
        viewModelScope.launch {
            try {
                buildsRepository.loadBuilds()
                _buildsLoaded.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getBuildForSpecies(speciesName: String): PokemonBuild? {
        return buildsRepository.getBuildForSpecies(speciesName)
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val now = System.currentTimeMillis()

                    // Try UDP first if preferred and not recently failed
                    if (preferUdp && (now - lastUdpCheckTime > 5000 || udpFailCount < 3)) {
                        val udpSuccess = tryUdpPolling()
                        if (udpSuccess) {
                            _dataSource.value = DataSource.UDP
                            udpFailCount = 0
                            lastUdpCheckTime = now
                        } else {
                            udpFailCount++
                            lastUdpCheckTime = now
                            // Fall through to save state
                        }
                    }

                    // Use save states if UDP failed or not preferred
                    if (_dataSource.value != DataSource.UDP) {
                        val saveStateSuccess = trySaveStatePolling()
                        if (saveStateSuccess) {
                            _dataSource.value = DataSource.SAVE_STATE
                        } else {
                            _dataSource.value = DataSource.DISCONNECTED
                        }
                    }

                } catch (e: Exception) {
                    _connectionState.value = RetroArchClient.ConnectionStatus.ERROR
                    _errorMessage.value = "Error: ${e.message}"
                    e.printStackTrace()
                }

                refreshDebugLog()
                delay(500) // Poll every 500ms for UDP, save state checks its own timing
            }
        }
    }

    private suspend fun tryUdpPolling(): Boolean {
        // First check if UDP server is responsive
        val status = client.getStatus()
        if (status != RetroArchClient.ConnectionStatus.CONNECTED) {
            return false
        }

        // Scan for party addresses if not cached
        _scanningState.value = true
        val addresses = scanner.findPartyAddress()
        _scanningState.value = false

        if (addresses == null) {
            _errorMessage.value = "UDP: Scanning for party addresses..."
            return false
        }

        // Read party data via UDP
        val (partyCountAddr, partyDataAddr) = addresses

        // Note: In ER mocha, gPlayerPartyCount is unreliable. Read all 12 slots.
        val partyData = client.readMemory(partyDataAddr, 104 * 12) ?: return false

        // Parse all slots - party is contiguous from slot 0 until first empty slot
        val allSlots = Gen3PokemonParser.parseAllSlots(partyData)

        // Take only contiguous Pokemon from slot 0 (stop at first null/empty)
        val party = allSlots.takeWhile { it != null }

        _partyState.value = party
        _connectionState.value = RetroArchClient.ConnectionStatus.CONNECTED
        _errorMessage.value = null

        // Enemy party: Pokemon after the first empty slot with different OT ID
        val partyEndIndex = party.size  // First empty slot is at this index
        val playerOtId = party.firstOrNull()?.otId ?: -1L
        val enemyParty = if (playerOtId >= 0 && partyEndIndex < allSlots.size) {
            allSlots.drop(partyEndIndex + 1)
                .takeWhile { it != null }
                .filterNotNull()
                .filter { it.otId != playerOtId }
        } else {
            emptyList()
        }
        _enemyPartyState.value = enemyParty

        return true
    }

    private suspend fun trySaveStatePolling(): Boolean {
        // Check if save state file has new data
        if (saveStateReader.hasNewData()) {
            _connectionState.value = RetroArchClient.ConnectionStatus.CONNECTED

            // Read party data from save state
            val partyData = saveStateReader.readPartyData()
            if (partyData != null) {
                val (_, partyBytes) = partyData

                // Parse all 12 slots; find player's OT ID from first valid mon
                val allSlots = Gen3PokemonParser.parseAllSlots(partyBytes)
                val playerOtId = allSlots.firstOrNull { it != null && it.level > 0 }?.otId ?: -1L

                // Player party: slots matching player OT ID
                val party = allSlots.filterNotNull()
                    .filter { playerOtId < 0 || it.otId == playerOtId }
                _partyState.value = party
                _errorMessage.value = null

                // Enemy party: only show if actively in battle
                val inBattle = saveStateReader.readInBattle()
                val enemySlots = allSlots.filterNotNull()
                    .filter { playerOtId < 0 || it.otId != playerOtId }
                val activeEnemy = enemySlots.any { it.hp > 0 }
                if (inBattle && activeEnemy) {
                    // Read gBattleMons for live modified stats
                    val battleMons = saveStateReader.readBattleMons()
                    val playerBattleMon = battleMons.getOrNull(0)
                    val enemyBattleMon  = battleMons.getOrNull(1)

                    // Overlay battle stats onto party/enemy lists for damage calcs
                    val activeSlot = saveStateReader.readActivePlayerSlot()
                    _activePlayerSlot.value = if (activeSlot >= 0) activeSlot
                        else inferActiveSlot(party)

                    // Patch active player mon with live stats
                    val patchedParty = party.mapIndexed { idx, mon ->
                        if (idx == _activePlayerSlot.value && playerBattleMon != null
                            && playerBattleMon.species == mon.species) {
                            mon.copy(
                                attack = playerBattleMon.attack,
                                defense = playerBattleMon.defense,
                                speed = playerBattleMon.speed,
                                spAttack = playerBattleMon.spAttack,
                                spDefense = playerBattleMon.spDefense,
                                hp = playerBattleMon.hp,
                                maxHp = playerBattleMon.maxHp
                            )
                        } else mon
                    }
                    _partyState.value = patchedParty

                    // Patch active enemy with live stats
                    val patchedEnemy = enemySlots.mapIndexed { idx, mon ->
                        if (idx == 0 && enemyBattleMon != null
                            && enemyBattleMon.species == mon.species) {
                            mon.copy(
                                attack = enemyBattleMon.attack,
                                defense = enemyBattleMon.defense,
                                speed = enemyBattleMon.speed,
                                spAttack = enemyBattleMon.spAttack,
                                spDefense = enemyBattleMon.spDefense,
                                hp = enemyBattleMon.hp,
                                maxHp = enemyBattleMon.maxHp
                            )
                        } else mon
                    }
                    _enemyPartyState.value = patchedEnemy
                } else {
                    _enemyPartyState.value = emptyList()
                    _activePlayerSlot.value = -1
                }
                return true
            } else {
                _connectionState.value = RetroArchClient.ConnectionStatus.ERROR
                _errorMessage.value = "Parse failed: ${saveStateReader.lastStatus}"
                return false
            }
        } else {
            // No new data, check if file exists
            val status = saveStateReader.getStatus()
            if (status.startsWith("No state file")) {
                _connectionState.value = RetroArchClient.ConnectionStatus.DISCONNECTED
                _errorMessage.value = "No save state file found — see debug panel for details"
                return false
            } else {
                // File exists but no new data, keep current state
                _connectionState.value = RetroArchClient.ConnectionStatus.CONNECTED
                return true
            }
        }
    }


    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun rescan() {
        saveStateReader.clearCache()
        scanner.clearCache()
        udpFailCount = 0
        lastUdpCheckTime = 0L
        _errorMessage.value = "Rescanning..."
    }

    fun applySaveStatePath(path: String) {
        debugManualPath = path
        saveStateReader.setManualPath(path)
        rescan()
    }

    fun getSaveStateStatus(): String {
        return saveStateReader.getStatus()
    }

    fun getSaveStateSearchPaths(): List<String> {
        return saveStateReader.getSearchedPaths()
    }

    fun listAllStateFiles(): List<String> {
        return saveStateReader.listAllStateFiles()
    }

    /** Heuristic: the active mon is likely the first non-fainted mon with damage taken,
     *  or simply the first non-fainted mon if none have taken damage. */
    private fun inferActiveSlot(party: List<PartyMon>): Int {
        val damaged = party.indexOfFirst { it != null && it.hp > 0 && it.hp < it.maxHp }
        if (damaged >= 0) return damaged
        return party.indexOfFirst { it != null && it.hp > 0 }.coerceAtLeast(0)
    }

    fun calcDamage(attacker: com.ercompanion.parser.PartyMon, defender: com.ercompanion.parser.PartyMon, moveData: com.ercompanion.data.MoveData): Int {
        val atkStat = if (moveData.category == 0) attacker.attack else attacker.spAttack
        val defStat = if (moveData.category == 0) defender.defense else defender.spDefense
        val attackerTypes = com.ercompanion.data.PokemonData.getSpeciesTypes(attacker.species)
        val defenderTypes = com.ercompanion.data.PokemonData.getSpeciesTypes(defender.species)

        // Calculate type effectiveness for Expert Belt check
        val effectiveness = com.ercompanion.calc.DamageCalculator.getTypeEffectiveness(moveData.type, defenderTypes)

        val result = com.ercompanion.calc.DamageCalculator.calc(
            attackerLevel = attacker.level,
            attackStat = atkStat,
            defenseStat = defStat,
            movePower = moveData.power,
            moveType = moveData.type,
            attackerTypes = attackerTypes,
            defenderTypes = defenderTypes,
            targetMaxHP = defender.maxHp,
            attackerItem = attacker.heldItem,
            defenderItem = defender.heldItem,
            attackerHp = attacker.hp,
            attackerMaxHp = attacker.maxHp,
            isSuperEffective = effectiveness > 1.0f,
            attackerAbility = attacker.ability,
            defenderAbility = defender.ability
        )
        return result.minDamage
    }

    fun refreshDebugLog() {
        val statusLines = mutableListOf<String>()
        statusLines.add(saveStateReader.getStatus())
        statusLines.addAll(saveStateReader.listAllStateFiles())
        _debugLog.value = statusLines
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
