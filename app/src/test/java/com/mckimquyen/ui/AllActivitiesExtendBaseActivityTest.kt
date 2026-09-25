package com.mckimquyen.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Keep Screen On" (and locale/font-scale override) is applied in [BaseActivity], so it only
 * reaches every screen if every Activity extends it. ActVipManagement once extended
 * AppCompatActivity directly and silently missed the flag - this reads the real manifest so a
 * newly added Activity that skips BaseActivity fails here instead of shipping unnoticed.
 */
class AllActivitiesExtendBaseActivityTest {

    private companion object {
        const val MANIFEST_PATH = "src/main/AndroidManifest.xml"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val BASE_PACKAGE = "com.mckimquyen"
    }

    private fun declaredActivityClassNames(): List<String> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(MANIFEST_PATH))
        val nodes = doc.getElementsByTagName("activity")
        return (0 until nodes.length).map { i ->
            val name = nodes.item(i).attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue
            if (name.startsWith(".")) BASE_PACKAGE + name else name
        }
    }

    @Test
    fun `manifest declares activities`() {
        assertTrue(declaredActivityClassNames().size >= 2)
    }

    @Test
    fun `every declared activity extends BaseActivity`() {
        val offenders = declaredActivityClassNames().filterNot { className ->
            val clazz = Class.forName(className, false, javaClass.classLoader)
            BaseActivity::class.java.isAssignableFrom(clazz)
        }
        assertTrue("Activities not extending BaseActivity: $offenders", offenders.isEmpty())
    }

    @Test
    fun `ActVipManagement extends BaseActivity`() {
        assertTrue(
            BaseActivity::class.java.isAssignableFrom(
                com.mckimquyen.feature.vip.ActVipManagement::class.java
            )
        )
    }
}
