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
    private static final int STRIP_ELEVATION_DP = 24;
    private static final int HIGHLIGHT_ALPHA = 255;
    private static final int NORMAL_ALPHA = 210;
    private static final int SCROLL_EDGE_DP = 48;
    private static final int SCROLL_STEP_DP = 8;
    private static final float HIGHLIGHT_SCALE = 1.06f;

    private final Host host;
    private final FrameLayout strip;
    private final Map<String, TextView> chips = new HashMap<>();
    private LensDragPayload payload;
    private HorizontalScrollView scroll;
    private int scrollDelta;
    private final Runnable edgeScroll = new Runnable() {
        @Override public void run() {
            HorizontalScrollView current = scroll;
            if (current == null || payload == null || scrollDelta == 0 || !strip.isShown()) return;
            int before = current.getScrollX();
            current.scrollBy(scrollDelta, 0);
            if (current.getScrollX() != before) strip.postOnAnimation(this);
        }
    };

    public LensDropStrip(Host host) {
        this.host = host;
        Activity activity = host.activity();
        strip = new FrameLayout(activity);
        strip.setVisibility(View.GONE);
        strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(TOP_MARGIN_DP);
        host.root().addView(strip, params);
        // Above the SearchBar, which has its own elevation and would otherwise draw over the chips.
        strip.setElevation(dp(STRIP_ELEVATION_DP));
    }

    public void show(LensDragPayload dragPayload) {
        hide();
        payload = dragPayload;
        // Read the current insets instead of listening: ActHome already owns the root's inset listener
        // and a second one would replace it.
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(host.root());
        int top = rootInsets == null ? 0 : rootInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
        ViewGroup.LayoutParams raw = strip.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) raw).topMargin = top + dp(TOP_MARGIN_DP);
            strip.setLayoutParams(raw);
        }
        strip.removeAllViews();
        chips.clear();
        scroll = new HorizontalScrollView(host.activity());
        scroll.setOnDragListener((view, event) -> handleDrag(view, event.getAction(), event.getLocalState(), event.getX()));
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        strip.setOnDragListener((view, event) -> handleDrag(view, event.getAction(), event.getLocalState(), event.getX()));
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
            chip.setOnDragListener((view, event) -> handleDrag(view, event.getAction(), event.getLocalState(), event.getX()));
            android.widget.LinearLayout.LayoutParams chipParams = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.setMargins(dp(CHIP_SPACING_DP / 2), 0, dp(CHIP_SPACING_DP / 2), 0);
            row.addView(chip, chipParams);
            chips.put(lens.getId(), chip);
        }
        strip.setVisibility(chips.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Chiếu tọa độ con về viewport để cuộn khi ngón tay giữ ở mép. */
    @androidx.annotation.VisibleForTesting
    boolean handleDrag(View view, int action, Object localState, float x) {
        if (payload == null || payload != localState) return false;
        switch (action) {
            case android.view.DragEvent.ACTION_DRAG_STARTED:
                return true;
            case android.view.DragEvent.ACTION_DRAG_LOCATION:
                if (scroll != null) {
                    int[] point = new int[2];
                    int[] viewport = new int[2];
                    view.getLocationOnScreen(point);
                    scroll.getLocationOnScreen(viewport);
                    scrollDelta = LensDragPolicy.INSTANCE.edgeScrollDelta(
                            x + point[0] - viewport[0], scroll.getWidth(), dp(SCROLL_EDGE_DP), dp(SCROLL_STEP_DP));
                    strip.removeCallbacks(edgeScroll);
                    if (scrollDelta != 0) strip.postOnAnimation(edgeScroll);
                }
                return true;
            case android.view.DragEvent.ACTION_DRAG_ENTERED:
                if (view instanceof TextView) {
                    view.setAlpha(HIGHLIGHT_ALPHA / 255f);
                    view.setScaleX(HIGHLIGHT_SCALE);
                    view.setScaleY(HIGHLIGHT_SCALE);
                }
                return true;
            case android.view.DragEvent.ACTION_DRAG_EXITED:
                stopScrolling();
                restoreChip(view);
                return true;
            case android.view.DragEvent.ACTION_DROP:
                stopScrolling();
                return dropOn(view);
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                hide();
                return true;
            default:
                return true;
        }
    }

    private void stopScrolling() {
        scrollDelta = 0;
        strip.removeCallbacks(edgeScroll);
    }

    private boolean dropOn(View view) {
        LensDragPayload current = payload;
        if (current == null || !current.getSourceView().isAttachedToWindow()
                || !LensMenuCoordinator.containsLens(host.lenses(), current.getSourceLensId())) return false;
        Object tag = view.getTag();
        if (!(tag instanceof String)) return false;
        String targetId = (String) tag;
        for (LensWorkspace lens : host.lenses()) {
            if (targetId.equals(lens.getId()) && !targetId.equals(current.getSourceLensId())) {
                // Tiêu thụ trước callback: event lặp không được tạo snapshot Undo thứ hai.
                payload = null;
                host.onDrop(current, targetId, lens.getName());
                hide();
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
        stopScrolling();
        strip.setOnDragListener(null);
        if (scroll != null) scroll.setOnDragListener(null);
        scroll = null;
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
        ViewGroup parent = (ViewGroup) strip.getParent();
        if (parent != null) parent.removeView(strip);
    }

    /** Drops the held app on [chip] the way a real DROP would; false when the strip has no matching drag. */
    @androidx.annotation.VisibleForTesting
    public boolean dropForTest(View chip, LensView source, String packageName) {
        LensDragPayload current = payload;
        if (current == null || current.getSourceView() != source
                || !packageName.equals(current.getApp().getPackageName().toString())) return false;
        return handleDrag(chip, android.view.DragEvent.ACTION_DROP, current, 0f);
    }

    @androidx.annotation.VisibleForTesting
    public void endForTest(View chip) {
        handleDrag(chip, android.view.DragEvent.ACTION_DRAG_ENDED, payload, 0f);
    }

    public View viewForTest() {
        return strip;
    }

    private int dp(int value) {
        return Math.round(value * host.activity().getResources().getDisplayMetrics().density);
    }
}
