package com.mckimquyen.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.AlarmClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Release smoke 2026-11-24 (Pixel): tapping the "timer 7" quick action crashed the app with a SecurityException, because Google Clock guards
 * SET_TIMER with com.android.alarm.permission.SET_ALARM, which the app does not declare, and only ActivityNotFoundException was caught.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class QuickActionIntentLaunchTest {

    @Test
    public void aHandledIntentIsStartedOnce() {
        Activity activity = mock(Activity.class);
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);

        assertTrue(HomeQuickActionDelegate.startQuickActionIntent(activity, intent));

        verify(activity, times(1)).startActivity(intent);
    }

    @Test
    public void anIntentNobodyHandlesReturnsFalseInsteadOfThrowing() {
        Activity activity = mock(Activity.class);
        doThrow(new ActivityNotFoundException()).when(activity).startActivity(any(Intent.class));

        assertFalse(HomeQuickActionDelegate.startQuickActionIntent(activity, new Intent(Intent.ACTION_VIEW)));
    }

    @Test
    public void aRefusedSettimerFallsBackToTheTimersScreen() {
        Activity activity = mock(Activity.class);
        Intent setTimer = new Intent(AlarmClock.ACTION_SET_TIMER);
        doThrow(new SecurityException("requires SET_ALARM")).when(activity).startActivity(setTimer);

        assertTrue(HomeQuickActionDelegate.startQuickActionIntent(activity, setTimer));

        ArgumentCaptor<Intent> started = ArgumentCaptor.forClass(Intent.class);
        verify(activity, times(2)).startActivity(started.capture());
        assertEquals(AlarmClock.ACTION_SHOW_TIMERS, started.getAllValues().get(1).getAction());
    }

    @Test
    public void aRefusedIntentThatIsNotATimerReturnsFalseWithoutAFallback() {
        Activity activity = mock(Activity.class);
        Intent other = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        doThrow(new SecurityException("refused")).when(activity).startActivity(other);

        assertFalse(HomeQuickActionDelegate.startQuickActionIntent(activity, other));

        verify(activity, times(1)).startActivity(any(Intent.class));
    }

    @Test
    public void whenEvenTheFallbackIsRefusedTheResultIsFalseAndNothingThrows() {
        Activity activity = mock(Activity.class);
        doThrow(new SecurityException("refused")).when(activity).startActivity(any(Intent.class));

        assertFalse(HomeQuickActionDelegate.startQuickActionIntent(activity, new Intent(AlarmClock.ACTION_SET_TIMER)));
    }
}
