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

    private val _connectionState = MutableStateFlow(RetroArchClient.ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<RetroArchClient.ConnectionStatus> = _connectionState.asStateFlow()

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

    private val _buildsLoaded = MutableStateFlow(false)
    val buildsLoaded: StateFlow<Boolean> = _buildsLoaded.asStateFlow()

    private var pollingJob: Job? = null
    private var enemyPartyCountAddress: Long? = null
    private var enemyPartyDataAddress: Long? = null

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
                            // gBattlersCount == 2 is necessary but not sufficient — ER doesn't
                            // always reset it after battle. Secondary check: at least one enemy
                            // mon must have HP > 0 (all fainted = battle is over).
                            val inBattle = saveStateReader.readInBattle()
                            val enemySlots = allSlots.filterNotNull()
                                .filter { playerOtId < 0 || it.otId != playerOtId }
                            val activeEnemy = enemySlots.any { it.hp > 0 }
                            if (inBattle && activeEnemy) {
                                _enemyPartyState.value = enemySlots

                                val activeSlot = saveStateReader.readActivePlayerSlot()
                                _activePlayerSlot.value = if (activeSlot >= 0) activeSlot
                                    else inferActiveSlot(party)
                            } else {
                                _enemyPartyState.value = emptyList()
                                _activePlayerSlot.value = -1
                            }
                        } else {
                            _connectionState.value = RetroArchClient.ConnectionStatus.ERROR
                            _errorMessage.value = "Parse failed: ${saveStateReader.lastStatus}"
                        }
                    } else {
                        // No new data, check if file exists
                        val status = saveStateReader.getStatus()
                        if (status.startsWith("No state file")) {
                            _connectionState.value = RetroArchClient.ConnectionStatus.DISCONNECTED
                            _errorMessage.value = "No save state file found — see debug panel for details"
                        } else {
                            // File exists but no new data, keep current state
                            _connectionState.value = RetroArchClient.ConnectionStatus.CONNECTED
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.value = RetroArchClient.ConnectionStatus.ERROR
                    _errorMessage.value = "Error: ${e.message}"
                    e.printStackTrace()
                }

                refreshDebugLog()
                delay(1000) // Poll every 1000ms
            }
        }
    }

    private suspend fun readEnemyPartyData() {
        val countAddr = enemyPartyCountAddress ?: return
        val dataAddr = enemyPartyDataAddress ?: return

        // Read enemy party count
        val countData = client.readMemory(countAddr, 1) ?: return
        val enemyCount = countData[0].toInt() and 0xFF

        if (enemyCount !in 0..6) {
            // Invalid count, rescan
            enemyPartyCountAddress = null
            enemyPartyDataAddress = null
            return
        }

        // If no enemy party, clear state
        if (enemyCount == 0) {
            _enemyPartyState.value = emptyList()
            return
        }

        // Read all enemy party Pokemon (6 slots, 104 bytes each)
        val enemyData = client.readMemory(dataAddr, 104 * 6) ?: return

        // Parse enemy party
        val enemyParty = Gen3PokemonParser.parseParty(enemyData, enemyCount)
        _enemyPartyState.value = enemyParty
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun rescan() {
        enemyPartyCountAddress = null
        enemyPartyDataAddress = null
        saveStateReader.clearCache()
        scanner.clearCache()
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
        val result = com.ercompanion.calc.DamageCalculator.calc(
            attackerLevel = attacker.level,
            attackStat = atkStat,
            defenseStat = defStat,
            movePower = moveData.power,
            moveType = moveData.type,
            attackerTypes = attackerTypes,
            defenderTypes = defenderTypes,
            targetMaxHP = defender.maxHp
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
