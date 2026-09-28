package com.mckimquyen.util

import android.os.StrictMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DebugStrictModeAndThemedIconTest {

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val MONOCHROME_REF = "@drawable/ic_launcher_monochrome"
        val ADAPTIVE_ICONS = listOf(
            "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
        )
    }

    @After
    fun resetPolicies() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
    }

    @Test
    fun `release build leaves StrictMode untouched`() {
        assertFalse(DebugStrictMode.installIfDebug(false))
        assertEquals(StrictMode.ThreadPolicy.LAX.toString(), StrictMode.getThreadPolicy().toString())
        assertEquals(StrictMode.VmPolicy.LAX.toString(), StrictMode.getVmPolicy().toString())
    }

    @Test
    fun `debug build installs thread and vm policies`() {
        assertTrue(DebugStrictMode.installIfDebug(true))
        assertNotEquals(StrictMode.ThreadPolicy.LAX.toString(), StrictMode.getThreadPolicy().toString())
        assertNotEquals(StrictMode.VmPolicy.LAX.toString(), StrictMode.getVmPolicy().toString())
    }

    @Test
    fun `both adaptive launcher icons declare the monochrome layer`() {
        ADAPTIVE_ICONS.forEach { path ->
            val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(File(path))
            val mono = doc.getElementsByTagName("monochrome")
            assertEquals("$path must have exactly one <monochrome>", 1, mono.length)
            assertEquals(MONOCHROME_REF, mono.item(0).attributes.getNamedItemNS(ANDROID_NS, "drawable").nodeValue)
        }
    }
}
