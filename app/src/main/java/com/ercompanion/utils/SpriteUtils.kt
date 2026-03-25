package com.ercompanion.utils

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

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
            // Tatsugiri forms — repo uses "tatsugiri" folder for base form
            "tatsugiri" -> "tatsugiri"
            "tatsugiri (droopy)" -> "tatsugiri"
            "tatsugiri (stretchy)" -> "tatsugiri"
            "tatsugiri (curly)" -> "tatsugiri"
            else -> name.lowercase().replace(" ", "-").replace(".", "").replace("'", "")
        }
    }

    const val PLACEHOLDER_SPRITE = "https://raw.githubusercontent.com/DepressoMocha/emerogue/moka-dev/graphics/pokemon/bulbasaur/anim_front.png"
}

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
