package com.mckimquyen.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactSearchIntegrationTest {

    @Test
    fun manifestDeclaresContactsPermissionButSearchHandlesDeniedRuntimeState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permission = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        ).requestedPermissions!!.toList()

        assertTrue(permission.contains(Manifest.permission.READ_CONTACTS))
        UtilSettings(context).save(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, false)
        ContactSearchEngine.setPermissionGrantedForTesting(false)
        try {
            assertTrue(ContactSearchEngine.shouldShowPermissionRequest(context, "contact Jane"))
            assertTrue(ContactSearchEngine.search(context, "contact Jane").isEmpty())
        } finally {
            ContactSearchEngine.setPermissionGrantedForTesting(null)
        }
    }

    @Test
    fun contactDialAndMessageIntentsNeverRequestDirectCallPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permission = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        ).requestedPermissions!!.toList()
        val result = ContactSearchResult("Jane Nguyen", "+84 912 345 678")

        assertEquals(android.content.Intent.ACTION_DIAL, result.dialIntent().action)
        assertEquals(android.content.Intent.ACTION_SENDTO, result.messageIntent().action)
        assertTrue(!permission.contains(Manifest.permission.CALL_PHONE))
    }
}
