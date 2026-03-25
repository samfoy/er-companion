package com.ercompanion.parser

import android.content.Context
import android.content.SharedPreferences
import com.ercompanion.network.RetroArchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddressScanner(
    private val context: Context,
    private val client: RetroArchClient
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("er_companion_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PARTY_ADDRESS = "party_address"
        private const val KEY_PARTY_COUNT_ADDRESS = "party_count_address"
        private const val KEY_ENEMY_PARTY_ADDRESS = "enemy_party_address"
        private const val KEY_ENEMY_PARTY_COUNT_ADDRESS = "enemy_party_count_address"

        private const val EWRAM_BASE = 0x02000000L
        private const val EWRAM_SIZE = 256 * 1024 // 256KB
        private const val POKEMON_SIZE = 104
        private const val MAX_PARTY_SIZE = 6
        private const val PARTY_STRUCT_SIZE = POKEMON_SIZE * MAX_PARTY_SIZE // 624 bytes

        // Known addresses for Emerald Rogue
        // Note: gPlayerPartyCount at 0x0203777C is unreliable (can be 0)
        // Use 0x02037780 as the party start address
        private val KNOWN_ADDRESSES = listOf(
            0x0203777CL, // gPlayerPartyCount (unreliable, but marks start)
            0x02037780L  // gPlayerParty - ALWAYS use this address
        )
    }

    suspend fun findPartyAddress(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        // Emerald Rogue uses fixed addresses: gPlayerParty at 0x02037780
        // gPlayerPartyCount at 0x0203777C is unreliable (can be 0), so we ignore it.
        val partyCountAddr = 0x0203777CL
        val partyAddr = 0x02037780L

        // Verify the address has valid data
        if (verifyPartyAddress(partyCountAddr, partyAddr)) {
            saveAddresses(partyCountAddr, partyAddr)
            return@withContext Pair(partyCountAddr, partyAddr)
        }

        // If verification failed, something is very wrong
        null
    }

    private suspend fun verifyPartyAddress(countAddr: Long, partyAddr: Long): Boolean {
        // Note: In ER mocha, gPlayerPartyCount is unreliable (can be 0).
        // Just verify we can read a valid Pokemon from the party address.

        // Read first Pokemon slot
        val firstMonData = client.readMemory(partyAddr, POKEMON_SIZE) ?: return false
        val firstMon = Gen3PokemonParser.parsePokemon(firstMonData)

        // Valid if we got a Pokemon with reasonable stats
        if (firstMon == null) return false

        return firstMon.species in 1..1526 && firstMon.level in 1..100
    }

    private suspend fun scanEWRAM(): Pair<Long, Long>? {
        // Read EWRAM in chunks (8KB at a time to avoid large UDP packets)
        val chunkSize = 8 * 1024

        for (offset in 0 until EWRAM_SIZE step 4) {
            if (offset % chunkSize == 0) {
                // Read a chunk
                val chunkData = client.readMemory(EWRAM_BASE + offset,
                    minOf(chunkSize, EWRAM_SIZE - offset)) ?: continue

                // Scan within this chunk
                for (i in 0 until chunkData.size - POKEMON_SIZE * MAX_PARTY_SIZE step 4) {
                    val potentialCount = chunkData[i].toInt() and 0xFF

                    if (potentialCount in 1..MAX_PARTY_SIZE) {
                        val partyAddr = EWRAM_BASE + offset + i + 4
                        val countAddr = EWRAM_BASE + offset + i

                        if (verifyPartyStructure(chunkData, i + 4, potentialCount)) {
                            return Pair(countAddr, partyAddr)
                        }
                    }
                }
            }
        }

        return null
    }

    private fun verifyPartyStructure(data: ByteArray, startOffset: Int, count: Int): Boolean {
        // Verify that we have enough data
        if (startOffset + POKEMON_SIZE * count > data.size) return false

        // Check first Pokemon
        val firstMonData = data.sliceArray(startOffset until startOffset + POKEMON_SIZE)
        val firstMon = Gen3PokemonParser.parsePokemon(firstMonData) ?: return false

        // Basic validation (Emerald Rogue has up to 1526 species)
        if (firstMon.species !in 1..1526 || firstMon.level !in 1..100) {
            return false
        }

        // If there's more than one Pokemon, check the second one too
        if (count >= 2 && startOffset + POKEMON_SIZE * 2 <= data.size) {
            val secondMonData = data.sliceArray(
                startOffset + POKEMON_SIZE until startOffset + POKEMON_SIZE * 2
            )
            val secondMon = Gen3PokemonParser.parsePokemon(secondMonData) ?: return false

            if (secondMon.species !in 1..1526 || secondMon.level !in 1..100) {
                return false
            }
        }

        return true
    }

    private fun saveAddresses(countAddr: Long, partyAddr: Long) {
        prefs.edit()
            .putLong(KEY_PARTY_COUNT_ADDRESS, countAddr)
            .putLong(KEY_PARTY_ADDRESS, partyAddr)
            .apply()
    }

    suspend fun findEnemyPartyAddress(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        // Check if we already have a cached address
        val cachedCountAddr = prefs.getLong(KEY_ENEMY_PARTY_COUNT_ADDRESS, 0L)
        val cachedPartyAddr = prefs.getLong(KEY_ENEMY_PARTY_ADDRESS, 0L)

        if (cachedCountAddr != 0L && cachedPartyAddr != 0L) {
            // Verify the cached address is still valid
            if (verifyEnemyPartyAddress(cachedCountAddr, cachedPartyAddr)) {
                return@withContext Pair(cachedCountAddr, cachedPartyAddr)
            }
        }

        // Try to find enemy party based on player party location
        val playerPartyCountAddr = prefs.getLong(KEY_PARTY_COUNT_ADDRESS, 0L)
        if (playerPartyCountAddr != 0L) {
            // Enemy party typically follows player party: player_count + PARTY_STRUCT_SIZE + 4
            val enemyCountAddr = playerPartyCountAddr + PARTY_STRUCT_SIZE + 4
            val enemyPartyAddr = enemyCountAddr + 4

            if (verifyEnemyPartyAddress(enemyCountAddr, enemyPartyAddr)) {
                saveEnemyAddresses(enemyCountAddr, enemyPartyAddr)
                return@withContext Pair(enemyCountAddr, enemyPartyAddr)
            }
        }

        null
    }

    private suspend fun verifyEnemyPartyAddress(countAddr: Long, partyAddr: Long): Boolean {
        val countData = client.readMemory(countAddr, 1) ?: return false
        val partyCount = countData[0].toInt() and 0xFF

        // Enemy party might have 0 Pokemon (no active battle)
        if (partyCount !in 0..MAX_PARTY_SIZE) return false

        // If there's no Pokemon, it's still valid (no battle)
        if (partyCount == 0) return true

        // Read first Pokemon to verify
        val firstMonData = client.readMemory(partyAddr, POKEMON_SIZE) ?: return false
        val firstMon = Gen3PokemonParser.parsePokemon(firstMonData)

        return firstMon != null
    }

    private fun saveEnemyAddresses(countAddr: Long, partyAddr: Long) {
        prefs.edit()
            .putLong(KEY_ENEMY_PARTY_COUNT_ADDRESS, countAddr)
            .putLong(KEY_ENEMY_PARTY_ADDRESS, partyAddr)
            .apply()
    }

    fun clearCache() {
        prefs.edit()
            .remove(KEY_PARTY_COUNT_ADDRESS)
            .remove(KEY_PARTY_ADDRESS)
            .remove(KEY_ENEMY_PARTY_COUNT_ADDRESS)
            .remove(KEY_ENEMY_PARTY_ADDRESS)
            .apply()
    }
}
