package com.ercompanion.utils

object SpriteUtils {
    private const val SPRITE_BASE_URL = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon"

    fun getSpriteUrl(speciesName: String): String {
        val slug = speciesNameToSlug(speciesName)
        return "$SPRITE_BASE_URL/$slug/anim_front.png"
    }

    private fun speciesNameToSlug(name: String): String {
        return when (name.lowercase()) {
            "nidoran♀" -> "nidoran-f"
            "nidoran♂" -> "nidoran-m"
            "mr. mime" -> "mr-mime"
            "farfetch'd" -> "farfetchd"
            "mime jr." -> "mime-jr"
            "ho-oh" -> "ho-oh"
            else -> name.lowercase().replace(" ", "-").replace(".", "").replace("'", "")
        }
    }

    const val PLACEHOLDER_SPRITE = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon/bulbasaur/anim_front.png"
}

