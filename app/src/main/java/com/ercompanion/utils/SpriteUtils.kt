package com.ercompanion.utils

object SpriteUtils {
    private const val SPRITE_BASE_URL = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon"

    fun getSpriteUrl(speciesName: String): String {
        val slug = speciesNameToSlug(speciesName)
        return "$SPRITE_BASE_URL/$slug/icon.png"
    }

    private fun speciesNameToSlug(name: String): String {
        return when (name.lowercase()) {
            // Special cases
            "nidoran♀" -> "nidoran-f"
            "nidoran♂" -> "nidoran-m"
            "mr. mime" -> "mr-mime"
            "farfetch'd" -> "farfetchd"
            "mime jr." -> "mime-jr"
            "porygon2" -> "porygon2"
            "ho-oh" -> "ho-oh"
            else -> {
                // General case: lowercase and replace spaces with hyphens
                name.lowercase()
                    .replace(" ", "-")
                    .replace(".", "")
                    .replace("'", "")
            }
        }
    }

    // Fallback placeholder for when sprite fails to load
    const val PLACEHOLDER_SPRITE = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon/bulbasaur/icon.png"
}
