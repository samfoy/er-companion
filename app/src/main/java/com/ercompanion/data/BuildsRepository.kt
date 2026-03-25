package com.ercompanion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PokemonBuild(
    val tier: String?,
    val notes: String?,
    val recommendedMoves: List<String>,
    val recommendedItem: String?
)

class BuildsRepository(private val context: Context) {
    private var buildsCache: Map<String, PokemonBuild>? = null

    suspend fun loadBuilds(): Map<String, PokemonBuild> = withContext(Dispatchers.IO) {
        if (buildsCache != null) {
            return@withContext buildsCache!!
        }

        try {
            val jsonString = context.assets.open("builds.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val builds = mutableMapOf<String, PokemonBuild>()

            // Skip _note field
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("_")) continue

                val buildObj = jsonObject.getJSONObject(key)
                val movesArray = buildObj.optJSONArray("recommendedMoves")
                val movesList = mutableListOf<String>()
                if (movesArray != null) {
                    for (i in 0 until movesArray.length()) {
                        movesList.add(movesArray.getString(i))
                    }
                }

                builds[key.lowercase()] = PokemonBuild(
                    tier = buildObj.optString("tier", null),
                    notes = buildObj.optString("notes", null),
                    recommendedMoves = movesList,
                    recommendedItem = buildObj.optString("recommendedItem", null)
                )
            }

            buildsCache = builds
            builds
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    fun getBuildForSpecies(speciesName: String): PokemonBuild? {
        val normalizedName = speciesName.lowercase()
            .replace(" ", "")
            .replace(".", "")
            .replace("'", "")
            .replace("♀", "f")
            .replace("♂", "m")
        return buildsCache?.get(normalizedName)
    }
}
