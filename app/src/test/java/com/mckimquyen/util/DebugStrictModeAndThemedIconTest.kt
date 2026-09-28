package com.mckimquyen.util

import android.os.StrictMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    // FISH-EXPORT finding: StrictMode is real static JVM-wide state that Robolectric does not
    // reset between test classes on its own. This class only reset it in @After, so "release
    // build leaves StrictMode untouched" silently depended on no earlier test in the same JVM
    // fork having left a policy installed - true by luck until app/build.gradle's
    // testOptions.unitTests.includeAndroidResources=true (added for PolaroidExportHelperTest,
    // which needs real Context.getString) started actually running RApplication.onCreate() for
    // some other test's Application context, which installs a real policy that then leaked here.
    // Reset before every test too, not just after, so this class no longer depends on run order.
    @Before
    fun resetPoliciesBeforeEachTest() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
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
