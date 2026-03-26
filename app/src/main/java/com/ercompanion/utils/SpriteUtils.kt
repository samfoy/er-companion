package com.ercompanion.utils

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Sprite URL generation for Pokémon from the emerogue romhack.
 *
 * Form Handling:
 * - Base forms: graphics/pokemon/{species}/anim_front.png
 * - Regional forms: graphics/pokemon/{species}/{form}/front.png
 *   (e.g., vulpix/alolan/front.png, meowth/galarian/front.png)
 * - Unown forms: graphics/pokemon/unown/{letter}/front.png
 *   (determined by personality value using GET_UNOWN_LETTER calculation)
 *
 * Species ID Ranges (from emerogue):
 * - Base: 1-905
 * - Mega: 906-955
 * - Alolan: 956-973
 * - Galarian: 974-992
 * - Hisuian: 993-1008
 */
object SpriteUtils {
    private const val SPRITE_BASE_URL = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon"

    /**
     * Get sprite URL for a Pokémon species, handling regional forms and special cases.
     * @param speciesName Species name (e.g., "Vulpix Alolan", "Unown")
     * @param personality Optional personality value for form determination (e.g., Unown letter)
     */
    fun getSpriteUrl(speciesName: String, personality: UInt = 0u): String {
        val formInfo = parseFormInfo(speciesName)
        val baseSlug = speciesNameToSlug(formInfo.baseName)

        // Handle Unown letter forms via personality
        if (baseSlug == "unown" && personality != 0u) {
            val letter = getUnownLetter(personality)
            return "$SPRITE_BASE_URL/$baseSlug/$letter/front.png"
        }

        // Handle regional forms (Alolan, Galarian, Hisuian) and other alternate forms
        if (formInfo.formSuffix != null) {
            val formPath = formInfo.formSuffix.lowercase()
            return "$SPRITE_BASE_URL/$baseSlug/$formPath/front.png"
        }

        // Base form uses anim_front.png
        return "$SPRITE_BASE_URL/$baseSlug/anim_front.png"
    }

    /**
     * Parse species name into base name and form suffix.
     * Examples: "Vulpix Alolan" -> ("Vulpix", "alolan")
     *           "Meowth Galarian" -> ("Meowth", "galarian")
     *           "Darmanitan Galarian Zen" -> ("Darmanitan", "galarian_zen")
     */
    private fun parseFormInfo(name: String): FormInfo {
        val parts = name.split(" ")

        // Check for regional form suffixes
        val regionalForms = listOf("alolan", "galarian", "hisuian", "paldean")
        val regionalIndex = parts.indexOfFirst { it.lowercase() in regionalForms }

        if (regionalIndex != -1) {
            val baseName = parts.take(regionalIndex).joinToString(" ")
            val formSuffix = parts.drop(regionalIndex).joinToString("_")
            return FormInfo(baseName, formSuffix)
        }

        // Check for other special forms
        when {
            "mega" in name.lowercase() -> {
                val baseName = name.replace(Regex("(?i)\\s*mega.*"), "").trim()
                return FormInfo(baseName, "mega")
            }
            "primal" in name.lowercase() -> {
                val baseName = name.replace(Regex("(?i)\\s*primal.*"), "").trim()
                return FormInfo(baseName, "primal")
            }
            "gigantamax" in name.lowercase() -> {
                val baseName = name.replace(Regex("(?i)\\s*gigantamax.*"), "").trim()
                return FormInfo(baseName, "gigantamax")
            }
        }

        // No form detected, return full name as base
        return FormInfo(name, null)
    }

    private fun speciesNameToSlug(name: String): String {
        return when (name.lowercase()) {
            "nidoran♀" -> "nidoran-f"
            "nidoran♂" -> "nidoran-m"
            "mr. mime" -> "mr-mime"
            "farfetch'd" -> "farfetchd"
            "farfetchd" -> "farfetchd"
            "mime jr." -> "mime-jr"
            "ho-oh" -> "ho-oh"
            // Tatsugiri forms — repo uses "tatsugiri" folder for base form
            "tatsugiri" -> "tatsugiri"
            "tatsugiri (droopy)" -> "tatsugiri"
            "tatsugiri (stretchy)" -> "tatsugiri"
            "tatsugiri (curly)" -> "tatsugiri"
            else -> name.lowercase().replace(" ", "-").replace(".", "").replace("'", "")
        }
    }

    /**
     * Calculate Unown letter form from personality value.
     * Based on emerogue's GET_UNOWN_LETTER macro.
     */
    private fun getUnownLetter(personality: UInt): String {
        // Extract bits: 24-25, 16-17, 8-9, 0-1
        val value = (
            ((personality and 0x03000000u) shr 18) or
            ((personality and 0x00030000u) shr 12) or
            ((personality and 0x00000300u) shr 6) or
            ((personality and 0x00000003u) shr 0)
        ) % 28u

        return when (value.toInt()) {
            0 -> "a"
            1 -> "b"
            2 -> "c"
            3 -> "d"
            4 -> "e"
            5 -> "f"
            6 -> "g"
            7 -> "h"
            8 -> "i"
            9 -> "j"
            10 -> "k"
            11 -> "l"
            12 -> "m"
            13 -> "n"
            14 -> "o"
            15 -> "p"
            16 -> "q"
            17 -> "r"
            18 -> "s"
            19 -> "t"
            20 -> "u"
            21 -> "v"
            22 -> "w"
            23 -> "x"
            24 -> "y"
            25 -> "z"
            26 -> "exclamation_mark"
            27 -> "question_mark"
            else -> "a" // Fallback
        }
    }

    const val PLACEHOLDER_SPRITE = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon/bulbasaur/anim_front.png"
}

private data class FormInfo(
    val baseName: String,
    val formSuffix: String?
)

/**
 * Coil Transformation that crops the top half of an image.
 * ER's anim_front.png is 64x128: top 64px = front sprite, bottom 64px = back sprite.
 */
class TopHalfCropTransformation : Transformation {
    override val cacheKey: String = "top_half_crop"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val halfHeight = input.height / 2
        return Bitmap.createBitmap(input, 0, 0, input.width, halfHeight)
    }
}
