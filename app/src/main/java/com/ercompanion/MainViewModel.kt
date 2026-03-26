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

    // Cache: PID -> Pokemon (persists across corrupted slots to handle switched-out Pokemon)
    private val pokemonCache = mutableMapOf<UInt, PartyMon>()

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

        // Read 12 slots to see if Pupitar might be in a different position
        // (normally 6 for player, but let's check extended region for debugging)
        val partyData = client.readMemory(partyDataAddr, 104 * 12) ?: run {
            _errorMessage.value = "UDP: Failed to read memory at 0x${partyDataAddr.toString(16)}"
            return false
        }

        // DEBUG: Check all 12 slots for Pupitar's PID (0xa9691b3b)
        for (i in 0 until 12) {
            val offset = i * 104
            if (offset + 104 <= partyData.size) {
                val slotData = partyData.sliceArray(offset until offset + 104)
                val personality = ((slotData[0].toInt() and 0xFF) or
                                  ((slotData[1].toInt() and 0xFF) shl 8) or
                                  ((slotData[2].toInt() and 0xFF) shl 16) or
                                  ((slotData[3].toInt() and 0xFF) shl 24)).toUInt()
                if (personality == 0xa9691b3bu) {
                    val mon = Gen3PokemonParser.parsePokemon(slotData)
                    android.util.Log.w("MainViewModel", "Found Pupitar PID at slot $i: ${if (mon != null) "VALID" else "CORRUPTED"}")
                }
            }
        }

        // Parse player party (6 slots) - debug which slots are valid
        val playerSlots = mutableListOf<PartyMon>()
        val slotDebug = mutableListOf<String>()

        for (i in 0 until 6) {
            val offset = i * 104
            if (offset + 104 <= partyData.size) {
                val slotData = partyData.sliceArray(offset until offset + 104)
                val mon = Gen3PokemonParser.parsePokemon(slotData)
                if (mon != null) {
                    playerSlots.add(mon)
                    slotDebug.add("$i:${com.ercompanion.data.PokemonData.getSpeciesName(mon.species)}")
                    android.util.Log.d("MainViewModel", "Slot $i: VALID - ${com.ercompanion.data.PokemonData.getSpeciesName(mon.species)}")
                } else {
                    // Check why this slot is invalid - show more details for debugging
                    val personality = ((slotData[0].toInt() and 0xFF) or
                                      ((slotData[1].toInt() and 0xFF) shl 8) or
                                      ((slotData[2].toInt() and 0xFF) shl 16) or
                                      ((slotData[3].toInt() and 0xFF) shl 24)).toUInt()
                    val level = slotData[0x54].toInt() and 0xFF
                    val hp = ((slotData[0x56].toInt() and 0xFF) or ((slotData[0x57].toInt() and 0xFF) shl 8))
                    val maxHp = ((slotData[0x58].toInt() and 0xFF) or ((slotData[0x59].toInt() and 0xFF) shl 8))
                    slotDebug.add("$i:INVALID(p=0x${personality.toString(16)},lv=$level,hp=$hp/$maxHp)")
                    android.util.Log.d("MainViewModel", "Slot $i: INVALID - PID=0x${personality.toString(16)}, lv=$level, hp=$hp/$maxHp")
                }
            }
        }

        _connectionState.value = RetroArchClient.ConnectionStatus.CONNECTED

        if (playerSlots.isEmpty()) {
            _errorMessage.value = "UDP: Party empty - ${slotDebug.joinToString()}"
        } else {
            _errorMessage.value = "UDP: ${slotDebug.joinToString(", ")}"
        }

        // Read gBattlersCount to check if we're in battle
        val battlersCountAddr = 0x0201C39CL
        val battlersCountData = client.readMemory(battlersCountAddr, 2)
        val battlersCount = if (battlersCountData != null) {
            ((battlersCountData[1].toInt() and 0xFF) shl 8) or (battlersCountData[0].toInt() and 0xFF)
        } else 0

        // Read gBattlerPartyIndexes to know which slot each battler is from
        // Address: 0x0201C39E (gBattlerPartyIndexes[0] = player active slot, u16)
        val battleMonsAddr = 0x0201C358L
        val battlerIndexAddr = 0x0201C39EL

        val indexData = client.readMemory(battlerIndexAddr, 8) // Read 4 u16 values (all battlers)
        val partyIndexes = if (indexData != null && indexData.size >= 8) {
            (0 until 4).map { i ->
                ((indexData[i*2+1].toInt() and 0xFF) shl 8) or (indexData[i*2].toInt() and 0xFF)
            }
        } else listOf(-1, -1, -1, -1)

        // During battle: read gBattleMons[] for all player battlers
        // In single battle: [0]=player, [1]=enemy
        // In double battle: [0]=player1, [1]=enemy1, [2]=player2, [3]=enemy2
        val playerBattleData = client.readMemory(battleMonsAddr, 0x60)

        android.util.Log.d("MainViewModel", "Battle info: battlersCount=$battlersCount, partyIndexes=$partyIndexes")

        var activeBattleMon: PartyMon? = null
        if (playerBattleData != null) {
            val species = ((playerBattleData[1].toInt() and 0xFF) shl 8) or (playerBattleData[0].toInt() and 0xFF)
            if (species > 0 && species <= 1526) {
                val level = playerBattleData[0x2C].toInt() and 0xFF
                val hp = ((playerBattleData[0x2B].toInt() and 0xFF) shl 8) or (playerBattleData[0x2A].toInt() and 0xFF)
                val maxHp = ((playerBattleData[0x2F].toInt() and 0xFF) shl 8) or (playerBattleData[0x2E].toInt() and 0xFF)
                val attack = ((playerBattleData[0x03].toInt() and 0xFF) shl 8) or (playerBattleData[0x02].toInt() and 0xFF)
                val defense = ((playerBattleData[0x05].toInt() and 0xFF) shl 8) or (playerBattleData[0x04].toInt() and 0xFF)
                val speed = ((playerBattleData[0x07].toInt() and 0xFF) shl 8) or (playerBattleData[0x06].toInt() and 0xFF)
                val spAttack = ((playerBattleData[0x09].toInt() and 0xFF) shl 8) or (playerBattleData[0x08].toInt() and 0xFF)
                val spDefense = ((playerBattleData[0x0B].toInt() and 0xFF) shl 8) or (playerBattleData[0x0A].toInt() and 0xFF)

                val moves = (0 until 4).mapNotNull { i ->
                    val moveOffset = 0x0C + i * 2
                    val moveId = ((playerBattleData[moveOffset + 1].toInt() and 0xFF) shl 8) or
                                 (playerBattleData[moveOffset].toInt() and 0xFF)
                    if (moveId > 0) moveId else null
                }

                activeBattleMon = PartyMon(
                    species = species,
                    level = level,
                    hp = hp,
                    maxHp = maxHp,
                    nickname = "",
                    moves = moves,
                    attack = attack,
                    defense = defense,
                    speed = speed,
                    spAttack = spAttack,
                    spDefense = spDefense,
                    experience = 0,
                    friendship = 0
                )
            }
        }

        // Build final party using cache
        val finalParty = mutableListOf<PartyMon>()

        android.util.Log.d("MainViewModel", "Active battle mon: ${activeBattleMon?.let { com.ercompanion.data.PokemonData.getSpeciesName(it.species) } ?: "null"}")

        // Parse all 6 slots
        for (i in 0 until 6) {
            val offset = i * 104
            val slotData = partyData.sliceArray(offset until offset + 104)
            val parsedMon = Gen3PokemonParser.parsePokemon(slotData)

            if (parsedMon != null) {
                // Valid slot - cache it and add to party
                pokemonCache[parsedMon.personality] = parsedMon

                // Overlay battle stats if species matches
                if (activeBattleMon != null && parsedMon.species == activeBattleMon.species) {
                    pokemonCache[activeBattleMon.personality] = activeBattleMon
                    finalParty.add(activeBattleMon)
                    android.util.Log.d("MainViewModel", "Slot $i: Using battle stats for ${com.ercompanion.data.PokemonData.getSpeciesName(activeBattleMon.species)}")
                } else {
                    finalParty.add(parsedMon)
                }
            } else {
                // Corrupted slot - try to recover from cache using PID
                val personality = ((slotData[0].toInt() and 0xFF) or
                                  ((slotData[1].toInt() and 0xFF) shl 8) or
                                  ((slotData[2].toInt() and 0xFF) shl 16) or
                                  ((slotData[3].toInt() and 0xFF) shl 24)).toUInt()

                val cachedMon = pokemonCache[personality]
                if (cachedMon != null) {
                    // Found in cache! Update HP from corrupted slot's unencrypted data
                    val hp = ((slotData[0x56].toInt() and 0xFF) or ((slotData[0x57].toInt() and 0xFF) shl 8))
                    val maxHp = ((slotData[0x58].toInt() and 0xFF) or ((slotData[0x59].toInt() and 0xFF) shl 8))
                    val recoveredMon = cachedMon.copy(hp = hp, maxHp = maxHp)
                    finalParty.add(recoveredMon)
                    android.util.Log.d("MainViewModel", "Slot $i: RECOVERED ${com.ercompanion.data.PokemonData.getSpeciesName(cachedMon.species)} from cache (PID=0x${personality.toString(16)})")
                } else {
                    android.util.Log.w("MainViewModel", "Slot $i: CORRUPTED and not in cache (PID=0x${personality.toString(16)})")
                }
            }
        }

        android.util.Log.d("MainViewModel", "Final party size: ${finalParty.size}, Pokemon: ${finalParty.map { com.ercompanion.data.PokemonData.getSpeciesName(it.species) }}")
        android.util.Log.d("MainViewModel", "Cache size: ${pokemonCache.size} Pokemon")
        _partyState.value = finalParty.take(6)

        // Read full enemy team from gEnemyParty (624 bytes after player party)
        val enemyPartyAddr = partyDataAddr + 624
        val enemyPartyData = client.readMemory(enemyPartyAddr, 104 * 6)

        if (enemyPartyData != null) {
            // Parse enemy party (6 slots)
            val enemySlots = (0 until 6).mapNotNull { i ->
                val offset = i * 104
                if (offset + 104 <= enemyPartyData.size) {
                    Gen3PokemonParser.parsePokemon(enemyPartyData.sliceArray(offset until offset + 104))
                } else null
            }

            // Read active enemy from gBattleMons[1] for current battle stats
            val enemyBattleMonAddr = battleMonsAddr + 0x60
            val enemyBattleData = client.readMemory(enemyBattleMonAddr, 0x60)

            var activeBattleEnemy: PartyMon? = null
            if (enemyBattleData != null) {
                val species = ((enemyBattleData[1].toInt() and 0xFF) shl 8) or (enemyBattleData[0].toInt() and 0xFF)
                if (species > 0 && species <= 1526) {
                    val level = enemyBattleData[0x2C].toInt() and 0xFF
                    val hp = ((enemyBattleData[0x2B].toInt() and 0xFF) shl 8) or (enemyBattleData[0x2A].toInt() and 0xFF)
                    val maxHp = ((enemyBattleData[0x2F].toInt() and 0xFF) shl 8) or (enemyBattleData[0x2E].toInt() and 0xFF)
                    val attack = ((enemyBattleData[0x03].toInt() and 0xFF) shl 8) or (enemyBattleData[0x02].toInt() and 0xFF)
                    val defense = ((enemyBattleData[0x05].toInt() and 0xFF) shl 8) or (enemyBattleData[0x04].toInt() and 0xFF)
                    val speed = ((enemyBattleData[0x07].toInt() and 0xFF) shl 8) or (enemyBattleData[0x06].toInt() and 0xFF)
                    val spAttack = ((enemyBattleData[0x09].toInt() and 0xFF) shl 8) or (enemyBattleData[0x08].toInt() and 0xFF)
                    val spDefense = ((enemyBattleData[0x0B].toInt() and 0xFF) shl 8) or (enemyBattleData[0x0A].toInt() and 0xFF)

                    val moves = (0 until 4).mapNotNull { i ->
                        val moveOffset = 0x0C + i * 2
                        val moveId = ((enemyBattleData[moveOffset + 1].toInt() and 0xFF) shl 8) or
                                     (enemyBattleData[moveOffset].toInt() and 0xFF)
                        if (moveId > 0) moveId else null
                    }

                    activeBattleEnemy = PartyMon(
                        species = species,
                        level = level,
                        hp = hp,
                        maxHp = maxHp,
                        nickname = "",
                        moves = moves,
                        attack = attack,
                        defense = defense,
                        speed = speed,
                        spAttack = spAttack,
                        spDefense = spDefense,
                        experience = 0,
                        friendship = 0
                    )
                }
            }

            // Only show enemy team if we have a valid active enemy (indicates we're in battle)
            val finalEnemyTeam = if (activeBattleEnemy != null) {
                // Find which slot has the active enemy (by species match)
                val activeIndex = enemySlots.indexOfFirst { it.species == activeBattleEnemy.species }
                if (activeIndex >= 0) {
                    // Replace that specific slot with battle stats
                    enemySlots.toMutableList().apply { set(activeIndex, activeBattleEnemy) }
                } else {
                    // Active enemy not in party slots - add it first (currently in battle)
                    listOf(activeBattleEnemy) + enemySlots
                }
            } else {
                // No active enemy = not in battle, clear enemy party
                emptyList()
            }

            _enemyPartyState.value = finalEnemyTeam
        } else {
            _enemyPartyState.value = emptyList()
        }

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

                // Player party: slots matching player OT ID, max 6
                val party = allSlots.filterNotNull()
                    .filter { playerOtId < 0 || it.otId == playerOtId }
                    .take(6)
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
        statusLines.add("=== DATA SOURCE: ${_dataSource.value} ===")
        statusLines.add("UDP fail count: $udpFailCount (falls back after 3)")
        statusLines.add("Connection: ${_connectionState.value}")
        _errorMessage.value?.let { statusLines.add("Error: $it") }
        statusLines.add("--- Save State Info ---")
        statusLines.add(saveStateReader.getStatus())
        statusLines.addAll(saveStateReader.listAllStateFiles())
        _debugLog.value = statusLines
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
