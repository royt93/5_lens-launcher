package com.mckimquyen.search

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ContactSearchEngineTest {

    private fun context() = RuntimeEnvironment.getApplication()

    private fun resetPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit().clear().commit()
    }

    @Test
    fun `only explicit contact prefixes are contact queries`() {
        assertEquals("Jane", ContactSearchEngine.extractedQuery("contact Jane"))
        assertEquals("Jane", ContactSearchEngine.extractedQuery("call Jane"))
        assertEquals("Jane", ContactSearchEngine.extractedQuery("message Jane"))
        assertEquals("Jane", ContactSearchEngine.extractedQuery("lien he Jane"))
        assertEquals("", ContactSearchEngine.extractedQuery("Jane"))
        assertFalse(ContactSearchEngine.isExplicitContactQuery("call J"))
        assertTrue(ContactSearchEngine.isExplicitContactQuery("call Jane"))
    }

    @Test
    fun `permission request is offered once for explicit contact search only`() {
        resetPrefs()
        Shadows.shadowOf(context()).denyPermissions(Manifest.permission.READ_CONTACTS)

        assertFalse(ContactSearchEngine.shouldShowPermissionRequest(context(), "camera"))
        assertTrue(ContactSearchEngine.shouldShowPermissionRequest(context(), "contact Jane"))

        UtilSettings(context()).save(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, true)
        assertFalse(ContactSearchEngine.shouldShowPermissionRequest(context(), "contact Jane"))
    }

    @Test
    fun `permission request is not shown when contacts permission is granted`() {
        resetPrefs()
        Shadows.shadowOf(context()).grantPermissions(Manifest.permission.READ_CONTACTS)

        assertTrue(ContactSearchEngine.hasContactsPermission(context()))
        assertFalse(ContactSearchEngine.shouldShowPermissionRequest(context(), "call Jane"))
    }

    @Test
    fun `rank rows matches names phone digits and stays deterministic`() {
        val rows = listOf(
            ContactRow("Jane Nguyen", "+84 912 345 678"),
            ContactRow("Janet Tran", "+84 900 111 222"),
            ContactRow("Bob Phone", "+1 555 912 0000")
        )

        assertEquals(
            listOf("Jane Nguyen", "Janet Tran"),
            ContactSearchEngine.rankRows(rows, "Jane").map(ContactRow::displayName)
        )
        assertEquals(
            listOf("Bob Phone", "Jane Nguyen"),
            ContactSearchEngine.rankRows(rows, "912").map(ContactRow::displayName)
        )
    }

    @Test
    fun `rank rows rejects blank short and non-positive limits`() {
        val rows = listOf(ContactRow("Jane Nguyen", "+84 912 345 678"))

        assertTrue(ContactSearchEngine.rankRows(rows, "").isEmpty())
        assertTrue(ContactSearchEngine.rankRows(rows, "J").isEmpty())
        assertTrue(ContactSearchEngine.rankRows(rows, "Jane", limit = 0).isEmpty())
    }

    @Test
    fun `contact intents use standard non-sensitive dial and message actions`() {
        val result = ContactSearchResult("Jane Nguyen", "+84 912 345 678")

        assertEquals(Intent.ACTION_DIAL, result.dialIntent().action)
        assertEquals("tel", result.dialIntent().data?.scheme)
        assertEquals(Intent.ACTION_SENDTO, result.messageIntent().action)
        assertEquals("smsto", result.messageIntent().data?.scheme)
    }
}
