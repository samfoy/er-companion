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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val client = RetroArchClient()
    private val scanner = AddressScanner(application, client)
    private val buildsRepository = BuildsRepository(application)

    private val _connectionState = MutableStateFlow(RetroArchClient.ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<RetroArchClient.ConnectionStatus> = _connectionState.asStateFlow()

    private val _partyState = MutableStateFlow<List<PartyMon?>>(emptyList())
    val partyState: StateFlow<List<PartyMon?>> = _partyState.asStateFlow()

    private val _enemyPartyState = MutableStateFlow<List<PartyMon?>>(emptyList())
    val enemyPartyState: StateFlow<List<PartyMon?>> = _enemyPartyState.asStateFlow()

    private val _scanningState = MutableStateFlow(false)
    val scanningState: StateFlow<Boolean> = _scanningState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _buildsLoaded = MutableStateFlow(false)
    val buildsLoaded: StateFlow<Boolean> = _buildsLoaded.asStateFlow()

    private var pollingJob: Job? = null
    private var partyCountAddress: Long? = null
    private var partyDataAddress: Long? = null
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
                    // Check connection status
                    val status = client.getStatus()
                    _connectionState.value = status

                    if (status == RetroArchClient.ConnectionStatus.CONNECTED) {
                        // Find party address if not found yet
                        if (partyCountAddress == null || partyDataAddress == null) {
                            if (!_scanningState.value) {
                                _scanningState.value = true
                                _errorMessage.value = "Scanning for party data..."

                                val addresses = scanner.findPartyAddress()
                                if (addresses != null) {
                                    partyCountAddress = addresses.first
                                    partyDataAddress = addresses.second
                                    _errorMessage.value = null
                                } else {
                                    _errorMessage.value = "Could not locate party data in memory"
                                }

                                _scanningState.value = false
                            }
                        } else {
                            // Read party data
                            readPartyData()

                            // Try to find enemy party if not found yet
                            if (enemyPartyCountAddress == null || enemyPartyDataAddress == null) {
                                val enemyAddresses = scanner.findEnemyPartyAddress()
                                if (enemyAddresses != null) {
                                    enemyPartyCountAddress = enemyAddresses.first
                                    enemyPartyDataAddress = enemyAddresses.second
                                }
                            } else {
                                // Read enemy party data
                                readEnemyPartyData()
                            }
                        }
                    } else {
                        _partyState.value = emptyList()
                        if (status == RetroArchClient.ConnectionStatus.DISCONNECTED) {
                            _errorMessage.value = "RetroArch not connected"
                        } else {
                            _errorMessage.value = "Connection error"
                        }
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error: ${e.message}"
                    e.printStackTrace()
                }

                delay(500) // Poll every 500ms
            }
        }
    }

    private suspend fun readPartyData() {
        val countAddr = partyCountAddress ?: return
        val dataAddr = partyDataAddress ?: return

        // Read party count
        val countData = client.readMemory(countAddr, 1) ?: return
        val partyCount = countData[0].toInt() and 0xFF

        if (partyCount !in 1..6) {
            // Invalid count, rescan
            partyCountAddress = null
            partyDataAddress = null
            return
        }

        // Read all party Pokemon (6 slots, 104 bytes each)
        val partyData = client.readMemory(dataAddr, 104 * 6) ?: return

        // Parse party
        val party = Gen3PokemonParser.parseParty(partyData, partyCount)
        _partyState.value = party
        _errorMessage.value = null
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
        partyCountAddress = null
        partyDataAddress = null
        enemyPartyCountAddress = null
        enemyPartyDataAddress = null
        scanner.clearCache()
        _errorMessage.value = "Rescanning..."
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
