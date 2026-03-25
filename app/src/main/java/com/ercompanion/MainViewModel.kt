package com.ercompanion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _connectionState = MutableStateFlow(RetroArchClient.ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<RetroArchClient.ConnectionStatus> = _connectionState.asStateFlow()

    private val _partyState = MutableStateFlow<List<PartyMon?>>(emptyList())
    val partyState: StateFlow<List<PartyMon?>> = _partyState.asStateFlow()

    private val _scanningState = MutableStateFlow(false)
    val scanningState: StateFlow<Boolean> = _scanningState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null
    private var partyCountAddress: Long? = null
    private var partyDataAddress: Long? = null

    init {
        startPolling()
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

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun rescan() {
        partyCountAddress = null
        partyDataAddress = null
        scanner.clearCache()
        _errorMessage.value = "Rescanning..."
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
