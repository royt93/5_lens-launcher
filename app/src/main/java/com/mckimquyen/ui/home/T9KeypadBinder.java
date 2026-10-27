package com.mckimquyen.ui.home;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;

import com.mckimquyen.R;

import java.util.ArrayList;
import java.util.List;

/** ARCH-002: original T9 button listeners, with matching teardown. */
public final class T9KeypadBinder implements AutoCloseable {
    private final List<View> buttons = new ArrayList<>();

    public T9KeypadBinder(Activity activity, EditText appSearch) {
        int[] digitButtons = new int[]{
                R.id.btnT9_0, R.id.btnT9_1, R.id.btnT9_2, R.id.btnT9_3, R.id.btnT9_4,
                R.id.btnT9_5, R.id.btnT9_6, R.id.btnT9_7, R.id.btnT9_8, R.id.btnT9_9
        };
        char[] digits = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        for (int i = 0; i < digitButtons.length; i++) {
            char d = digits[i];
            View btn = activity.findViewById(digitButtons[i]);
            if (btn != null) {
                buttons.add(btn);
                btn.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    if (appSearch != null) appSearch.append(String.valueOf(d));
                });
            }
        }
        View clear = activity.findViewById(R.id.btnT9_Clear);
        if (clear != null) {
            buttons.add(clear);
            clear.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (appSearch != null) appSearch.setText("");
            });
        }
        View backspace = activity.findViewById(R.id.btnT9_Backspace);
        if (backspace != null) {
            buttons.add(backspace);
            backspace.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (appSearch != null) {
                    CharSequence current = appSearch.getText();
                    if (current != null && current.length() > 0) {
                        appSearch.setText(current.subSequence(0, current.length() - 1));
                        appSearch.setSelection(appSearch.getText().length());
                    }
                }
            });
            backspace.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (appSearch != null) appSearch.setText("");
                return true;
            });
        }
    }

    @Override
    public void close() {
        for (View button : buttons) {
            button.setOnClickListener(null);
            button.setOnLongClickListener(null);
        }
        buttons.clear();
    }
}
