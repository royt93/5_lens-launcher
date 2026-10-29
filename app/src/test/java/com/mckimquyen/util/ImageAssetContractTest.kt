package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * BUILD-005: pins the image-asset decisions so lint's IconLocation / IconMissingDensityFolder
 * warnings cannot silently come back. Launcher mipmaps are excluded on purpose: the legacy PNGs
 * for API 25 still trigger IconLauncherShape and need a redesigned silhouette (owner declined).
 */
class ImageAssetContractTest {
    private val bitmapExtensions = setOf("png", "jpg", "jpeg", "webp", "gif")

    private fun resDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(File(workingDirectory, "src/main/res"), File(workingDirectory, "app/src/main/res"))
            .first { it.isDirectory }
    }

    private fun drawableDirectories(): List<File> =
        resDirectory().listFiles { f -> f.isDirectory && (f.name == "drawable" || f.name.startsWith("drawable-")) }
            .orEmpty().sortedBy { it.name }

    private fun bitmaps(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.extension.lowercase() in bitmapExtensions }.orEmpty().sortedBy { it.name }

    @Test fun noBitmapLivesInTheDensitylessDrawableFolder() {
        val offenders = bitmaps(File(resDirectory(), "drawable")).map { it.name }
        assertEquals("A bitmap in res/drawable scales with the device density (lint IconLocation)", emptyList<String>(), offenders)
    }

    @Test fun drawableBitmapsAreDensityIndependentLosslessWebp() {
        val offenders = drawableDirectories().flatMap { dir ->
            bitmaps(dir).filterNot { dir.name == "drawable-nodpi" && it.extension.lowercase() == "webp" }
                .map { "${dir.name}/${it.name}" }
        }
        assertEquals(
            "Bitmaps must be WebP in drawable-nodpi; per-density folders would reintroduce IconMissingDensityFolder",
            emptyList<String>(), offenders
        )
    }

    @Test fun noDrawableBitmapIsLargerThanTheSizeBudget() {
        val oversized = drawableDirectories().flatMap { bitmaps(it) }
            .filter { it.length() > MAX_DRAWABLE_BITMAP_BYTES }.map { "${it.parentFile.name}/${it.name} ${it.length()}" }
        assertEquals("The old 396 KB / 176 KB PNGs were the largest files in the APK", emptyList<String>(), oversized)
    }

    @Test fun staticShortcutIconsAreVectorDrawables() {
        val shortcuts = File(resDirectory(), "xml/shortcuts.xml")
        val nodes = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(shortcuts).getElementsByTagName("shortcut")
        assertTrue("shortcuts.xml must declare shortcuts", nodes.length > 0)
        for (i in 0 until nodes.length) {
            val icon = (nodes.item(i) as Element).getAttributeNS("http://schemas.android.com/apk/res/android", "icon")
            assertTrue("shortcut icon must be a drawable reference: $icon", icon.startsWith("@drawable/"))
            val file = File(resDirectory(), "drawable/${icon.removePrefix("@drawable/")}.xml")
            assertTrue("$icon must resolve to a vector xml in res/drawable", file.isFile)
            assertTrue("$icon must be a <vector>", file.readText().contains("<vector"))
        }
    }

    private companion object {
        /** Largest drawable bitmap after the WebP move is ~216 KB; leave headroom, fail on a regression to PNG sizes. */
        const val MAX_DRAWABLE_BITMAP_BYTES = 256L * 1024
    }
}
