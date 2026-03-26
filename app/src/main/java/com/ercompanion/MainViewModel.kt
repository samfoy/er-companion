package com.ercompanion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ercompanion.calc.CurseState
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

    // List of active player party slots (0-2 slots for singles/doubles), empty if not in battle
    private val _activePlayerSlots = MutableStateFlow<List<Int>>(emptyList())
    val activePlayerSlots: StateFlow<List<Int>> = _activePlayerSlots.asStateFlow()

    // List of active enemy party slots (0-2 slots for singles/doubles), empty if not in battle
    private val _activeEnemySlots = MutableStateFlow<List<Int>>(emptyList())
    val activeEnemySlots: StateFlow<List<Int>> = _activeEnemySlots.asStateFlow()

    // Legacy single-slot accessor for backwards compatibility
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

    private val _curseState = MutableStateFlow(CurseState())
    val curseState: StateFlow<CurseState> = _curseState.asStateFlow()

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

        // Read only 6 slots for player party (not 12 - that would read into enemy party)
        val partyData = client.readMemory(partyDataAddr, 104 * 6) ?: run {
            _errorMessage.value = "UDP: Failed to read memory at 0x${partyDataAddr.toString(16)}"
            return false
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
                } else {
                    // Check why this slot is invalid - show details for debugging
                    val personality = ((slotData[0].toInt() and 0xFF) or
                                      ((slotData[1].toInt() and 0xFF) shl 8) or
                                      ((slotData[2].toInt() and 0xFF) shl 16) or
                                      ((slotData[3].toInt() and 0xFF) shl 24)).toUInt()
                    val level = slotData[0x54].toInt() and 0xFF
                    val hp = ((slotData[0x56].toInt() and 0xFF) or ((slotData[0x57].toInt() and 0xFF) shl 8))
                    val maxHp = ((slotData[0x58].toInt() and 0xFF) or ((slotData[0x59].toInt() and 0xFF) shl 8))
                    slotDebug.add("$i:INVALID(p=0x${personality.toString(16)},lv=$level,hp=$hp/$maxHp)")
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

        // Update active player slots from gBattlerPartyIndexes
        val activeSlots = mutableListOf<Int>()
        val activeEnemyIndexes = mutableListOf<Int>()
        if (battlersCount == 2 || battlersCount == 4) {
            val slot0 = partyIndexes[0]
            if (slot0 in 0..5) activeSlots.add(slot0)

            val slot1 = partyIndexes[1]
            if (slot1 in 0..5) activeEnemyIndexes.add(slot1)

            // In doubles, also check battler[2] and battler[3]
            if (battlersCount == 4) {
                val slot2 = partyIndexes.getOrNull(2) ?: -1
                if (slot2 in 0..5 && slot2 != 0xFE && slot2 != 0xFF) {
                    activeSlots.add(slot2)
                }
                val slot3 = partyIndexes.getOrNull(3) ?: -1
                if (slot3 in 0..5 && slot3 != 0xFE && slot3 != 0xFF) {
                    activeEnemyIndexes.add(slot3)
                }
            }
        }
        _activePlayerSlots.value = activeSlots
        _activeEnemySlots.value = activeEnemyIndexes
        _activePlayerSlot.value = activeSlots.firstOrNull() ?: -1

        // During battle: read gBattleMons[] for all player battlers
        // In single battle: [0]=player, [1]=enemy
        // In double battle: [0]=player1, [1]=enemy1, [2]=player2, [3]=enemy2
        val allBattleMons = mutableListOf<PartyMon?>()

        // Read all 4 battler slots
        for (i in 0 until 4) {
            val battleData = client.readMemory(battleMonsAddr + i * 0x60, 0x60)
            if (battleData != null) {
                val species = ((battleData[1].toInt() and 0xFF) shl 8) or (battleData[0].toInt() and 0xFF)
                if (species > 0 && species <= 1526) {
                    val level = battleData[0x2C].toInt() and 0xFF
                    val hp = ((battleData[0x2B].toInt() and 0xFF) shl 8) or (battleData[0x2A].toInt() and 0xFF)
                    val maxHp = ((battleData[0x2F].toInt() and 0xFF) shl 8) or (battleData[0x2E].toInt() and 0xFF)
                    val attack = ((battleData[0x03].toInt() and 0xFF) shl 8) or (battleData[0x02].toInt() and 0xFF)
                    val defense = ((battleData[0x05].toInt() and 0xFF) shl 8) or (battleData[0x04].toInt() and 0xFF)
                    val speed = ((battleData[0x07].toInt() and 0xFF) shl 8) or (battleData[0x06].toInt() and 0xFF)
                    val spAttack = ((battleData[0x09].toInt() and 0xFF) shl 8) or (battleData[0x08].toInt() and 0xFF)
                    val spDefense = ((battleData[0x0B].toInt() and 0xFF) shl 8) or (battleData[0x0A].toInt() and 0xFF)

                    val moves = (0 until 4).mapNotNull { idx ->
                        val moveOffset = 0x0C + idx * 2
                        val moveId = ((battleData[moveOffset + 1].toInt() and 0xFF) shl 8) or
                                     (battleData[moveOffset].toInt() and 0xFF)
                        if (moveId > 0) moveId else null
                    }

                    allBattleMons.add(PartyMon(
                        species = species, level = level, hp = hp, maxHp = maxHp,
                        nickname = "", moves = moves,
                        attack = attack, defense = defense, speed = speed,
                        spAttack = spAttack, spDefense = spDefense,
                        experience = 0, friendship = 0
                    ))
                } else {
                    allBattleMons.add(null)
                }
            } else {
                allBattleMons.add(null)
            }
        }

        // Extract player battle mons (indexes 0 and 2)
        val activeBattleMon = allBattleMons.getOrNull(0)
        val activeBattleMon2 = allBattleMons.getOrNull(2)

        // Build final party using cache
        val finalParty = mutableListOf<PartyMon>()

        // Parse all 6 slots
        for (i in 0 until 6) {
            val offset = i * 104
            val slotData = partyData.sliceArray(offset until offset + 104)
            val parsedMon = Gen3PokemonParser.parsePokemon(slotData)

            if (parsedMon != null) {
                // Valid slot - cache it and add to party
                pokemonCache[parsedMon.personality] = parsedMon

                // Overlay battle stats if this is an active slot
                // IMPORTANT: Battle stats from gBattleMons are already post-stat-stage
                val matchingBattleMon = when {
                    activeBattleMon != null && activeSlots.contains(i)
                        && parsedMon.species == activeBattleMon.species
                        && parsedMon.level == activeBattleMon.level -> activeBattleMon
                    activeBattleMon2 != null && activeSlots.contains(i)
                        && parsedMon.species == activeBattleMon2.species
                        && parsedMon.level == activeBattleMon2.level -> activeBattleMon2
                    else -> null
                }

                if (matchingBattleMon != null) {
                    // Use battle stats (already includes stat stage modifiers)
                    val battleMon = parsedMon.copy(
                        attack = matchingBattleMon.attack,      // POST stat stages
                        defense = matchingBattleMon.defense,    // POST stat stages
                        speed = matchingBattleMon.speed,        // POST stat stages
                        spAttack = matchingBattleMon.spAttack,  // POST stat stages
                        spDefense = matchingBattleMon.spDefense,// POST stat stages
                        hp = matchingBattleMon.hp,
                        maxHp = matchingBattleMon.maxHp
                    )
                    pokemonCache[battleMon.personality] = battleMon
                    finalParty.add(battleMon)
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
                }
            }
        }

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

            // Extract active enemies from battle mons (indexes 1 and 3)
            val activeBattleEnemy = allBattleMons.getOrNull(1)
            val activeBattleEnemy2 = allBattleMons.getOrNull(3)

            // Only show enemy team if we have a valid active enemy (indicates we're in battle)
            val finalEnemyTeam = if (activeBattleEnemy != null || activeBattleEnemy2 != null) {
                // Get enemy active slots from gBattlerPartyIndexes
                val enemySlot1 = partyIndexes.getOrNull(1) ?: -1
                val enemySlot2 = if (battlersCount == 4) partyIndexes.getOrNull(3) ?: -1 else -1

                val patchedSlots = enemySlots.toMutableList()

                // Patch enemy slot 1
                if (activeBattleEnemy != null && enemySlot1 in patchedSlots.indices) {
                    val slotMon = patchedSlots[enemySlot1]
                    if (slotMon.species == activeBattleEnemy.species && slotMon.level == activeBattleEnemy.level) {
                        patchedSlots[enemySlot1] = slotMon.copy(
                            attack = activeBattleEnemy.attack,
                            defense = activeBattleEnemy.defense,
                            speed = activeBattleEnemy.speed,
                            spAttack = activeBattleEnemy.spAttack,
                            spDefense = activeBattleEnemy.spDefense,
                            hp = activeBattleEnemy.hp,
                            maxHp = activeBattleEnemy.maxHp
                        )
                    }
                }

                // Patch enemy slot 2 (doubles)
                if (activeBattleEnemy2 != null && enemySlot2 in patchedSlots.indices) {
                    val slotMon = patchedSlots[enemySlot2]
                    if (slotMon.species == activeBattleEnemy2.species && slotMon.level == activeBattleEnemy2.level) {
                        patchedSlots[enemySlot2] = slotMon.copy(
                            attack = activeBattleEnemy2.attack,
                            defense = activeBattleEnemy2.defense,
                            speed = activeBattleEnemy2.speed,
                            spAttack = activeBattleEnemy2.spAttack,
                            spDefense = activeBattleEnemy2.spDefense,
                            hp = activeBattleEnemy2.hp,
                            maxHp = activeBattleEnemy2.maxHp
                        )
                    }
                }

                patchedSlots
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

                // Parse party with contiguous slot detection and OT ID filtering
                // This automatically detects player's OT ID and stops at first enemy/null slot
                val party = Gen3PokemonParser.parseParty(partyBytes, maxSlots = 12).take(6)
                _partyState.value = party
                _errorMessage.value = null

                // Enemy party: parse all 12 slots and filter out player Pokemon
                val allSlots = Gen3PokemonParser.parseAllSlots(partyBytes)
                val playerOtId = party.firstOrNull()?.otId ?: -1L

                // Enemy party: only show if actively in battle
                val inBattle = saveStateReader.readInBattle()
                val enemySlots = allSlots.filterNotNull()
                    .filter { playerOtId >= 0 && it.otId != playerOtId }
                val activeEnemy = enemySlots.any { it.hp > 0 }
                if (inBattle && activeEnemy) {
                    // Read gBattleMons for live modified stats
                    val battleMons = saveStateReader.readBattleMons()
                    val playerBattleMon = battleMons.getOrNull(0)
                    val enemyBattleMon  = battleMons.getOrNull(1)

                    // Read active slots (supports both singles and doubles)
                    val activeSlots = saveStateReader.readActivePlayerSlots()
                    _activePlayerSlots.value = activeSlots
                    _activePlayerSlot.value = activeSlots.firstOrNull() ?: inferActiveSlot(party)

                    // Patch active player mons with live gBattleMons stats (post-stat-stage)
                    // IMPORTANT: gBattleMons stats are ALREADY modified by stat stages (baseline=6)
                    // Do NOT apply stat stage modifiers manually - they're baked into these values
                    val playerBattleMon2 = battleMons.getOrNull(2)  // Second player mon in doubles
                    val patchedParty = party.mapIndexed { idx, mon ->
                        when {
                            // First active slot
                            playerBattleMon != null && idx in activeSlots
                                && playerBattleMon.species == mon.species
                                && playerBattleMon.level == mon.level -> {
                                mon.copy(
                                    attack = playerBattleMon.attack,
                                    defense = playerBattleMon.defense,
                                    speed = playerBattleMon.speed,
                                    spAttack = playerBattleMon.spAttack,
                                    spDefense = playerBattleMon.spDefense,
                                    hp = playerBattleMon.hp,
                                    maxHp = playerBattleMon.maxHp
                                )
                            }
                            // Second active slot (doubles)
                            playerBattleMon2 != null && idx in activeSlots
                                && playerBattleMon2.species == mon.species
                                && playerBattleMon2.level == mon.level -> {
                                mon.copy(
                                    attack = playerBattleMon2.attack,
                                    defense = playerBattleMon2.defense,
                                    speed = playerBattleMon2.speed,
                                    spAttack = playerBattleMon2.spAttack,
                                    spDefense = playerBattleMon2.spDefense,
                                    hp = playerBattleMon2.hp,
                                    maxHp = playerBattleMon2.maxHp
                                )
                            }
                            else -> mon
                        }
                    }
                    _partyState.value = patchedParty

                    // Patch active enemies with live gBattleMons stats (post-stat-stage)
                    val activeEnemySlots = saveStateReader.readActiveEnemySlots()
                    _activeEnemySlots.value = activeEnemySlots
                    val enemyBattleMon2 = battleMons.getOrNull(3)  // Second enemy in doubles
                    val patchedEnemy = enemySlots.mapIndexed { idx, mon ->
                        when {
                            // First active enemy
                            enemyBattleMon != null && idx in activeEnemySlots
                                && enemyBattleMon.species == mon.species
                                && enemyBattleMon.level == mon.level -> {
                                mon.copy(
                                    attack = enemyBattleMon.attack,
                                    defense = enemyBattleMon.defense,
                                    speed = enemyBattleMon.speed,
                                    spAttack = enemyBattleMon.spAttack,
                                    spDefense = enemyBattleMon.spDefense,
                                    hp = enemyBattleMon.hp,
                                    maxHp = enemyBattleMon.maxHp
                                )
                            }
                            // Second active enemy (doubles)
                            enemyBattleMon2 != null && idx in activeEnemySlots
                                && enemyBattleMon2.species == mon.species
                                && enemyBattleMon2.level == mon.level -> {
                                mon.copy(
                                    attack = enemyBattleMon2.attack,
                                    defense = enemyBattleMon2.defense,
                                    speed = enemyBattleMon2.speed,
                                    spAttack = enemyBattleMon2.spAttack,
                                    spDefense = enemyBattleMon2.spDefense,
                                    hp = enemyBattleMon2.hp,
                                    maxHp = enemyBattleMon2.maxHp
                                )
                            }
                            else -> mon
                        }
                    }
                    _enemyPartyState.value = patchedEnemy
                } else {
                    _enemyPartyState.value = emptyList()
                    _activePlayerSlots.value = emptyList()
                    _activeEnemySlots.value = emptyList()
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

    fun calcDamage(
        attacker: com.ercompanion.parser.PartyMon,
        defender: com.ercompanion.parser.PartyMon,
        moveData: com.ercompanion.data.MoveData,
        isEnemyAttacking: Boolean = false
    ): Int {
        // NOTE: For active Pokemon in battle, attacker/defender stats are already patched
        // with gBattleMons values (post-stat-stage). For non-active Pokemon, these are
        // base stats from party structure. This is correct behavior - we can't know stat
        // stages for Pokemon not currently in battle.
        val atkStat = if (moveData.category == 0) attacker.attack else attacker.spAttack
        val defStat = if (moveData.category == 0) defender.defense else defender.spDefense
        val attackerTypes = com.ercompanion.data.PokemonData.getSpeciesTypes(attacker.species)
        val defenderTypes = com.ercompanion.data.PokemonData.getSpeciesTypes(defender.species)

        // Calculate type effectiveness for Expert Belt check
        val effectiveness = com.ercompanion.calc.DamageCalculator.getTypeEffectiveness(moveData.type, defenderTypes)

        val result = com.ercompanion.calc.DamageCalculator.calc(
            attackerLevel = attacker.level,
            attackStat = atkStat,      // Already post-stat-stage for active battlers
            defenseStat = defStat,     // Already post-stat-stage for active battlers
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
            defenderAbility = defender.ability,
            isEnemyAttacking = isEnemyAttacking,
            curses = _curseState.value
        )
        // Return -1 if stats are invalid (will be handled in UI)
        return if (result.isValid) result.minDamage else -1
    }

    fun updateCurses(curses: CurseState) {
        if (curses.isValid()) {
            _curseState.value = curses
        }
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
