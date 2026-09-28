package dev.gelo.glyphprogress

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * Nothing OS pairs two faces, and they are not interchangeable.
 *
 * NType 82 is Nothing's serif. Settings headers and the one large figure use it.
 * The file ships on the phone; it is not bundled here.
 *
 * Geist is the Nothing OS 5 interface face: rows, labels, values, and actions.
 * The OFL files live in res/font.
 */
internal object NothingType {
    fun geist(): FontFamily = FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
    )

    fun serif(): FontFamily {
        findSerifFile()?.let { file ->
            try {
                return FontFamily(Typeface.createFromFile(file))
            } catch (_: Exception) {
                // Fall through to a named family, then the system serif.
            }
        }
        val named = listOf("NType82", "NType82 Headline", "NType 82")
            .firstNotNullOfOrNull { family -> systemFamily(family) }
        return if (named != null) FontFamily(named) else FontFamily.Serif
    }

    private fun findSerifFile(): File? {
        val matches = FONT_DIRS
            .map { File(it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { file -> file.isFile && file.name.contains("NType82", ignoreCase = true) }
        return matches.firstOrNull { it.name.contains("Headline", ignoreCase = true) }
            ?: matches.firstOrNull { it.name.contains("Regular", ignoreCase = true) }
            ?: matches.firstOrNull()
    }

    private fun systemFamily(name: String): Typeface? {
        val created = Typeface.create(name, Typeface.NORMAL)
        val fallback = Typeface.defaultFromStyle(Typeface.NORMAL)
        return if (created == fallback) null else created
    }

    private val FONT_DIRS = listOf(
        "/system/fonts",
        "/product/fonts",
        "/system/product/fonts",
        "/system_ext/fonts",
        "/vendor/fonts",
        "/oem/fonts",
    )
}
