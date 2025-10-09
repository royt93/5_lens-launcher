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
     * Calculate equispaced grid based on screen dimensions and item count
     * Source: <a href="http://math.stackexchange.com/questions/466198/algorithm-to-get-the-maximum-size-of-n-squares-that-fit-into-a-rectangle-with-a">...</a>
     */
    public static Grid calculateGrid(Context context, int screenWidth, int screenHeight, int itemCount) {
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
        UtilSettings utilSettings = new UtilSettings(context);
        float itemSize = convertDpToPixel(utilSettings.getFloat(UtilSettings.KEY_ICON_SIZE), context);

        // Calculate spacing
        float spacingHorizontal = (screenWidth - (itemCountHorizontal * itemSize)) / (itemCountHorizontal + 1);
        float spacingVertical = (screenHeight - (itemCountVertical * itemSize)) / (itemCountVertical + 1);

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
    public static float shiftPoint(Context context, float lensPosition, float itemPosition, float boundary, float multiplier) {
        if (lensPosition < 0) return itemPosition;

        UtilSettings utilSettings = new UtilSettings(context);
        float a = Math.abs(lensPosition - itemPosition);
        float b = Math.max(lensPosition, boundary - lensPosition);
        float x = a / b;
        float d = multiplier * utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR);
        float y = ((1.0f + d) * x) / (1.0f + (d * x));
        float newDistanceFromCenter = b * y;

        return (lensPosition >= itemPosition)
                ? lensPosition - newDistanceFromCenter
                : lensPosition + newDistanceFromCenter;
    }

    /**
     * Graphical Fisheye Lens algorithm for scaling
     */
    public static float scalePoint(Context context, float lensPosition, float itemPosition, float itemSize, float boundary, float multiplier) {
        if (lensPosition < 0) return itemSize;

        UtilSettings utilSettings = new UtilSettings(context);
        float scaleDifference = utilSettings.getFloat(UtilSettings.KEY_SCALE_FACTOR) - UtilSettings.MIN_SCALE_FACTOR;
        float d = UtilSettings.MIN_SCALE_FACTOR + scaleDifference * multiplier;

        float adjustedPosition = (lensPosition >= itemPosition)
                ? itemPosition - d * (itemSize / 2.0f)
                : itemPosition + d * (itemSize / 2.0f);

        return shiftPoint(context, lensPosition, adjustedPosition, boundary, multiplier);
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
