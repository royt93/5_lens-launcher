package com.mckimquyen.util;

import android.content.Context;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import com.mckimquyen.model.Grid;

/**
 * Utility class for calculations related to fisheye lens effect and grid layout
 * <p>
 * Note: 2023.03.20 tried to convert to Kotlin but failed
 */
public class UtilCalculator {

    /**
     * Calculate equispaced grid based on screen dimensions, item count, and icon size in DP
     */
    public static Grid calculateGrid(Context context, int screenWidth, int screenHeight, int itemCount, float iconSizeDp) {
        // Calculate item counts
        int itemCountHorizontal, itemCountVertical;
        if (itemCount <= 1) {
            itemCountHorizontal = itemCount;
            itemCountVertical = itemCount;
        } else {
            double optimalSquareSize = calculateOptimalSquareSize(screenWidth, screenHeight, itemCount);
            itemCountHorizontal = (int) Math.ceil(screenWidth / optimalSquareSize);
            itemCountVertical = (int) Math.ceil((double) itemCount / itemCountHorizontal);
        }

        // Calculate item size
        float itemSize = convertDpToPixel(iconSizeDp, context);

        // Calculate spacing
        float spacingHorizontal = (screenWidth - (itemCountHorizontal * itemSize)) / (itemCountHorizontal + 1);
        float spacingVertical = (screenHeight - (itemCountVertical * itemSize)) / (itemCountVertical + 1);

        // If spacing is negative, recalculate itemSize to fit the screen
        if (spacingHorizontal < 0 || spacingVertical < 0) {
            // Recalculate item size with minimal spacing of 1px between items
            float maxItemSizeH = (screenWidth - (itemCountHorizontal + 1)) / (float) itemCountHorizontal;
            float maxItemSizeV = (screenHeight - (itemCountVertical + 1)) / (float) itemCountVertical;
            itemSize = Math.min(maxItemSizeH, maxItemSizeV);

            // Recalculate spacing with new item size
            spacingHorizontal = (screenWidth - (itemCountHorizontal * itemSize)) / (itemCountHorizontal + 1);
            spacingVertical = (screenHeight - (itemCountVertical * itemSize)) / (itemCountVertical + 1);
        }

        // Ensure spacing is non-negative as a final safeguard
        spacingHorizontal = Math.max(0, spacingHorizontal);
        spacingVertical = Math.max(0, spacingVertical);

        return new Grid(itemCount, itemCountHorizontal, itemCountVertical, itemSize, spacingHorizontal, spacingVertical);
    }

    /**
     * Calculate optimal square side length given width, height and number of items
     */
    public static double calculateOptimalSquareSize(int screenWidth, int screenHeight, int itemCount) {
        double px = Math.ceil(Math.sqrt((double) itemCount * screenWidth / screenHeight));
        double sx = (Math.floor(px * screenHeight / screenWidth) * px < itemCount)
                ? screenHeight / Math.ceil(px * screenHeight / screenWidth)
                : screenWidth / px;

        double py = Math.ceil(Math.sqrt((double) itemCount * screenHeight / screenWidth));
        double sy = (Math.floor(py * screenWidth / screenHeight) * py < itemCount)
                ? screenWidth / Math.ceil(screenWidth * py / screenHeight)
                : screenHeight / py;

        return Math.max(sx, sy);
    }

    /**
     * Graphical Fisheye Lens algorithm for shifting item position
     */
    public static float shiftPoint(float lensPosition, float itemPosition, float boundary, float multiplier, float distortionFactor) {
        if (lensPosition < 0) return itemPosition;

        float a = Math.abs(lensPosition - itemPosition);
        float b = Math.max(lensPosition, boundary - lensPosition);
        float x = a / b;
        float d = multiplier * distortionFactor;
        float y = ((1.0f + d) * x) / (1.0f + (d * x));
        float newDistanceFromCenter = b * y;

        return (lensPosition >= itemPosition)
                ? lensPosition - newDistanceFromCenter
                : lensPosition + newDistanceFromCenter;
    }

    /**
     * Graphical Fisheye Lens algorithm for scaling
     */
    public static float scalePoint(float lensPosition, float itemPosition, float itemSize, float boundary, float multiplier, float scaleFactor, float distortionFactor) {
        if (lensPosition < 0) return itemSize;

        float scaleDifference = scaleFactor - UtilSettings.MIN_SCALE_FACTOR;
        float d = UtilSettings.MIN_SCALE_FACTOR + scaleDifference * multiplier;

        float adjustedPosition = (lensPosition >= itemPosition)
                ? itemPosition - d * (itemSize / 2.0f)
                : itemPosition + d * (itemSize / 2.0f);

        return shiftPoint(lensPosition, adjustedPosition, boundary, multiplier, distortionFactor);
    }

    /**
     * Graphical Fisheye Lens algorithm for determining final scaled size
     */
    public static float calculateSquareScaledSize(float scaledPositionX, float shiftedPositionX, float scaledPositionY, float shiftedPositionY) {
        return 2.0f * Math.min(Math.abs(scaledPositionX - shiftedPositionX), Math.abs(scaledPositionY - shiftedPositionY));
    }

    /**
     * Calculate new rect from center position and size
     */
    public static RectF calculateRect(float newCenterX, float newCenterY, float newSize) {
        float halfSize = newSize / 2.0f;
        return new RectF(
                newCenterX - halfSize,
                newCenterY - halfSize,
                newCenterX + halfSize,
                newCenterY + halfSize
        );
    }

    /**
     * PERF-001: same math as {@link #calculateRect(float, float, float)} but writes into an
     * existing RectF instead of allocating a new one - called once per grid cell per frame while
     * the lens is being dragged, so avoiding the allocation there matters.
     */
    public static void calculateRect(RectF dest, float newCenterX, float newCenterY, float newSize) {
        float halfSize = newSize / 2.0f;
        dest.set(
                newCenterX - halfSize,
                newCenterY - halfSize,
                newCenterX + halfSize,
                newCenterY + halfSize
        );
    }

    /**
     * Check if touch point is within rect
     */
    public static boolean isInsideRect(float x, float y, RectF rect) {
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    /**
     * Convert dp measurements to pixels
     */
    public static float convertDpToPixel(float dp, Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * (metrics.densityDpi / (float) DisplayMetrics.DENSITY_DEFAULT);
    }
}
