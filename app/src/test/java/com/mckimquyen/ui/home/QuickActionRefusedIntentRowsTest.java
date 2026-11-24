package com.mckimquyen.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The contact call/message rows and the Do Not Disturb access row used to catch only ActivityNotFoundException, like the timer row that crashed a Pixel.
 * Here a real Activity refuses every intent the way a guarded system app does (SecurityException), and the rows must neither throw nor close the search.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class QuickActionRefusedIntentRowsTest {

    /** Refuses every startActivity with a SecurityException, or accepts it, and counts how many arrived. */
    public static class GuardedActivity extends Activity {
        static boolean refuse;
        static int started;

        @Override
        public void startActivity(Intent intent) {
            started++;
            if (refuse) throw new SecurityException("Permission Denial: requires a permission the app does not hold");
        }
    }

    private static class RecordingHost implements HomeQuickActionHost {
        final Activity activity;
        int hideSearchCalls;

        RecordingHost(Activity activity) {
            this.activity = activity;
        }

        @Override public Activity activity() { return activity; }
        @Override public CharSequence searchText() { return ""; }
        @Override public void hideSearch() { hideSearchCalls++; }
        @Override public void refreshWebSearchFallback() { }
    }

    private RecordingHost host(boolean refuse) {
        GuardedActivity.refuse = refuse;
        GuardedActivity.started = 0;
        return new RecordingHost(Robolectric.buildActivity(GuardedActivity.class).setup().get());
    }

    private Intent dial() {
        return new Intent(Intent.ACTION_DIAL, Uri.parse("tel:123"));
    }

    @Test
    public void anAcceptedContactIntentClosesTheSearch() {
        RecordingHost host = host(false);

        new HomeQuickActionDelegate(host).startContactIntent(dial());

        assertEquals("positive control: the intent reached startActivity", 1, GuardedActivity.started);
        assertEquals(1, host.hideSearchCalls);
    }

    @Test
    public void aRefusedContactIntentDoesNotThrowAndKeepsTheSearchOpen() {
        RecordingHost host = host(true);

        new HomeQuickActionDelegate(host).startContactIntent(dial());

        assertEquals("positive control: the intent was attempted", 1, GuardedActivity.started);
        assertEquals("a refused intent must not close the search as if it had worked", 0, host.hideSearchCalls);
    }

    @Test
    public void aRefusedDndAccessScreenDoesNotThrow() {
        RecordingHost host = host(true);

        new HomeQuickActionDelegate(host).openDndAccessSettings();

        assertEquals("positive control: the Settings intent was attempted", 1, GuardedActivity.started);
    }

    @Test
    public void anAcceptedDndAccessScreenIsStartedOnce() {
        RecordingHost host = host(false);

        new HomeQuickActionDelegate(host).openDndAccessSettings();

        assertEquals(1, GuardedActivity.started);
        assertTrue(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS.length() > 0);
    }
}
