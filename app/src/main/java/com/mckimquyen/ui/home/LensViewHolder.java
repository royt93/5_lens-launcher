package com.mckimquyen.ui.home;

/** ARCH-002: owns the lens pager state of ActHome. Pure decisions first; the pager code moves in next. */
public final class LensViewHolder {
    private LensViewHolder() {}

    /** FISH-014: the dots indicator and lens name show only on plain multi-lens home. */
    public static boolean chromeVisible(boolean cleanMode, boolean listMode, boolean searchShowing, int lensCount) {
        return !cleanMode && !listMode && !searchShowing && lensCount > 1;
    }

    /** True when [position] indexes into a list of [size] lenses. */
    public static boolean isValidPosition(int position, int size) {
        return position >= 0 && position < size;
    }
}
