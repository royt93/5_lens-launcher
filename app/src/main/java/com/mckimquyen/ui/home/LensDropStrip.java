package com.mckimquyen.ui.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mckimquyen.R;
import com.mckimquyen.model.LensWorkspace;
import com.mckimquyen.views.LensDragPayload;
import com.mckimquyen.views.LensDragPolicy;
import com.mckimquyen.views.LensView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** FISH-038b: temporary top strip of lens chips that accept a held app drag. */
public final class LensDropStrip {
    public interface Host {
        Activity activity();
        ViewGroup root();
        List<LensWorkspace> lenses();
        void onDrop(LensDragPayload payload, String targetLensId, String targetName);
    }

    private static final int MIN_CHIP_SIZE_DP = 48;
    private static final int HORIZONTAL_PADDING_DP = 12;
    private static final int VERTICAL_PADDING_DP = 8;
    private static final int CHIP_SPACING_DP = 8;
    private static final int TOP_MARGIN_DP = 8;
    private static final int HIGHLIGHT_ALPHA = 255;
    private static final int NORMAL_ALPHA = 210;

    private final Host host;
    private final FrameLayout strip;
    private final Map<String, TextView> chips = new HashMap<>();
    private LensDragPayload payload;
    private OnApplyWindowInsetsListener insetsListener;

    public LensDropStrip(Host host) {
        this.host = host;
        Activity activity = host.activity();
        strip = new FrameLayout(activity);
        strip.setVisibility(View.GONE);
        strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(TOP_MARGIN_DP);
        host.root().addView(strip, params);
        insetsListener = (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
            ViewGroup.LayoutParams raw = strip.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
                int target = top + dp(TOP_MARGIN_DP);
                if (lp.topMargin != target) {
                    lp.topMargin = target;
                    strip.setLayoutParams(lp);
                }
            }
            return insets;
        };
        ViewCompat.setOnApplyWindowInsetsListener(host.root(), insetsListener);
    }

    public void show(LensDragPayload dragPayload) {
        payload = dragPayload;
        strip.removeAllViews();
        chips.clear();
        HorizontalScrollView scroll = new HorizontalScrollView(host.activity());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        strip.addView(scroll, scrollParams);

        android.widget.LinearLayout row = new android.widget.LinearLayout(host.activity());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(dp(HORIZONTAL_PADDING_DP), dp(VERTICAL_PADDING_DP), dp(HORIZONTAL_PADDING_DP), dp(VERTICAL_PADDING_DP));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (LensWorkspace lens : LensDragPolicy.INSTANCE.targets(host.lenses(), dragPayload.getSourceLensId())) {
            TextView chip = new TextView(host.activity());
            chip.setText(lens.getName());
            chip.setGravity(Gravity.CENTER);
            chip.setMinWidth(dp(MIN_CHIP_SIZE_DP));
            chip.setMinHeight(dp(MIN_CHIP_SIZE_DP));
            chip.setPadding(dp(HORIZONTAL_PADDING_DP), 0, dp(HORIZONTAL_PADDING_DP), 0);
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(chip.getTypeface(), Typeface.BOLD);
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setCornerRadius(dp(MIN_CHIP_SIZE_DP));
            background.setColor(com.google.android.material.color.MaterialColors.getColor(
                    host.activity(), androidx.appcompat.R.attr.colorPrimary, Color.DKGRAY));
            chip.setBackground(background);
            chip.setContentDescription(host.activity().getString(R.string.lens_drag_targets_description) + ": " + lens.getName());
            chip.setAlpha(NORMAL_ALPHA / 255f);
            chip.setTag(lens.getId());
            chip.setOnDragListener((view, event) -> handleDrag(view, event.getAction(), event.getLocalState()));
            android.widget.LinearLayout.LayoutParams chipParams = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.setMargins(dp(CHIP_SPACING_DP / 2), 0, dp(CHIP_SPACING_DP / 2), 0);
            row.addView(chip, chipParams);
            chips.put(lens.getId(), chip);
        }
        strip.setVisibility(chips.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** What a chip does for a drag event; separate from the listener so tests need no framework DragEvent. */
    @androidx.annotation.VisibleForTesting
    boolean handleDrag(View view, int action, Object localState) {
        switch (action) {
            case android.view.DragEvent.ACTION_DRAG_STARTED:
                return localState instanceof LensDragPayload && payload == localState;
            case android.view.DragEvent.ACTION_DRAG_ENTERED:
                view.setAlpha(HIGHLIGHT_ALPHA / 255f);
                view.setScaleX(1.06f);
                view.setScaleY(1.06f);
                return true;
            case android.view.DragEvent.ACTION_DRAG_EXITED:
                restoreChip(view);
                return true;
            case android.view.DragEvent.ACTION_DROP:
                return dropOn(view);
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                restoreChip(view);
                hide();
                return true;
            default:
                return true;
        }
    }

    private boolean dropOn(View view) {
        LensDragPayload current = payload;
        if (current == null) return false;
        Object tag = view.getTag();
        if (!(tag instanceof String)) return false;
        String targetId = (String) tag;
        for (LensWorkspace lens : host.lenses()) {
            if (targetId.equals(lens.getId()) && !targetId.equals(current.getSourceLensId())) {
                host.onDrop(current, targetId, lens.getName());
                return true;
            }
        }
        return false;
    }

    private static void restoreChip(View view) {
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(NORMAL_ALPHA / 255f);
    }

    public void hide() {
        for (TextView chip : chips.values()) {
            chip.setOnDragListener(null);
            restoreChip(chip);
        }
        chips.clear();
        strip.removeAllViews();
        strip.setVisibility(View.GONE);
        payload = null;
    }

    public void onDestroy() {
        hide();
        if (insetsListener != null) {
            ViewCompat.setOnApplyWindowInsetsListener(host.root(), null);
            insetsListener = null;
        }
        ViewGroup parent = (ViewGroup) strip.getParent();
        if (parent != null) parent.removeView(strip);
    }

    /** Drops the held app on [chip] the way a real DROP would; false when the strip has no matching drag. */
    @androidx.annotation.VisibleForTesting
    public boolean dropForTest(View chip, LensView source, String packageName) {
        LensDragPayload current = payload;
        if (current == null || current.getSourceView() != source
                || !packageName.equals(current.getApp().getPackageName().toString())) return false;
        return handleDrag(chip, android.view.DragEvent.ACTION_DROP, current);
    }

    @androidx.annotation.VisibleForTesting
    public void endForTest(View chip) {
        handleDrag(chip, android.view.DragEvent.ACTION_DRAG_ENDED, payload);
    }

    public View viewForTest() {
        return strip;
    }

    private int dp(int value) {
        return Math.round(value * host.activity().getResources().getDisplayMetrics().density);
    }
}
