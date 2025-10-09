package com.mckimquyen.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import com.mckimquyen.model.Grid;

//2023.03.20 tried to convert kotlin but failed
public class UtilCalculator {

    // Algorithm for calculating equispaced grid
    public static Grid calculateGrid(Context context, int screenWidth, int screenHeight, int itemCount) {
        // Calculate item counts
        int itemCountHorizontal, itemCountVertical;
        if (itemCount == 0 || itemCount == 1) {
            itemCountHorizontal = itemCount;
            itemCountVertical = itemCount;
        } else {
            double optimalSquareSize = calculateOptimalSquareSize(screenWidth, screenHeight, itemCount);
            itemCountHorizontal = (int) Math.ceil(screenWidth / optimalSquareSize);
            itemCountVertical = (int) Math.ceil((double) itemCount / (double) itemCountHorizontal);
        }

        // Calculate item size
        UtilSettings utilSettings = new UtilSettings(context);
        float itemSize = UtilCalculator.convertDpToPixel(utilSettings.getFloat(UtilSettings.KEY_ICON_SIZE), context);

        // Calculate spacing
        float spacingHorizontal = (((float) screenWidth) - ((float) itemCountHorizontal * itemSize)) / ((float) (itemCountHorizontal + 1));
        float spacingVertical = (((float) screenHeight) - ((float) itemCountVertical * itemSize)) / ((float) (itemCountVertical + 1));

        // Create immutable Grid instance with all calculated values
        return new Grid(
            itemCount,
            itemCountHorizontal,
            itemCountVertical,
            itemSize,
            spacingHorizontal,
            spacingVertical
        );
    }

    // Algorithm for calculating optimal square side length given width, height and number of items
    public static double calculateOptimalSquareSize(int screenWidth, int screenHeight, int itemCount) {
        // Source: http://math.stackexchange.com/questions/466198/algorithm-to-get-the-maximum-size-of-n-squares-that-fit-into-a-rectangle-with-a
        double px = Math.ceil(Math.sqrt((double) itemCount * (double) screenWidth / (double) screenHeight));
        double sx, sy;
        if (Math.floor(px * (double) screenHeight / (double) screenWidth) * px < (double) itemCount) {
            sx = (double) screenHeight / Math.ceil(px * (double) screenHeight / (double) screenWidth);
        } else {
            sx = (double) screenWidth / px;
        }
        double py = Math.ceil(Math.sqrt((double) itemCount * (double) screenHeight / (double) screenWidth));
        if (Math.floor(py * (double) screenWidth / (double) screenHeight) * py < (double) itemCount) {
            sy = (double) screenWidth / Math.ceil((double) screenWidth * py / (double) screenHeight);
        } else {
            sy = (double) screenHeight / py;
        }
        return Math.max(sx, sy);
    }

    // Algorithm for circular distance
//    public static double calculateDistance(float x1, float x2, float y1, float y2) {
//        return Math.sqrt(Math.pow((double) (x2 - x1), 2) + Math.pow((double) (y2 - y1), 2));
//    }

    // Algorithm for determining whether a rect is within a given lens (centered at touchX, touchY)

    // Graphical Fisheye Lens algorithm for shifting
    public static float shiftPoint(Context context, float lensPosition, float itemPosition, float boundary, float multiplier) {
        if (lensPosition < 0) {
            return itemPosition;
        }
        UtilSettings utilSettings = new UtilSettings(context);
        float shiftedPosition;
        float a = Math.abs(lensPosition - itemPosition);
        float b = Math.max(lensPosition, boundary - lensPosition);
        float x = a / b;
        float d = multiplier * utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR);
        float y = ((1.0f + d) * x) / (1.0f + (d * x));
        float newDistanceFromCenter = b * y;
        if (lensPosition >= itemPosition) {
            shiftedPosition = lensPosition - newDistanceFromCenter;
        } else {
            shiftedPosition = lensPosition + newDistanceFromCenter;
        }
        return shiftedPosition;
    }

    // Graphical Fisheye Lens algorithm for scaling
    public static float scalePoint(Context context, float lensPosition, float itemPosition, float itemSize, float boundary, float multiplier) {
        if (lensPosition < 0) {
            return itemSize;
        }
        UtilSettings utilSettings = new UtilSettings(context);
        float scaleDifference = utilSettings.getFloat(UtilSettings.KEY_SCALE_FACTOR) - UtilSettings.MIN_SCALE_FACTOR;
        float d = UtilSettings.MIN_SCALE_FACTOR + scaleDifference * multiplier;
        if (lensPosition >= itemPosition) {
            itemPosition = itemPosition - d * (itemSize / 2.0f);
        } else {
            itemPosition = itemPosition + d * (itemSize / 2.0f);
        }
        return UtilCalculator.shiftPoint(context, lensPosition, itemPosition, boundary, multiplier);
    }

    // Graphical Fisheye Lens algorithm for determining final scaled size
    public static float calculateSquareScaledSize(float scaledPositionX, float shiftedPositionX, float scaledPositionY, float shiftedPositionY) {
        return 2.0f * Math.min(Math.abs(scaledPositionX - shiftedPositionX), Math.abs(scaledPositionY - shiftedPositionY));
    }

    // Algorithm for calculating new rect
    public static RectF calculateRect(float newCenterX, float newCenterY, float newSize) {
        return new RectF(
                newCenterX - newSize / 2.0f,
                newCenterY - newSize / 2.0f,
                newCenterX + newSize / 2.0f,
                newCenterY + newSize / 2.0f);
    }

    // Algorithm for determining if touch point is within rect
    public static boolean isInsideRect(float x, float y, RectF rect) {
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    // Algorithm for converting dp measurements to pixels
    public static float convertDpToPixel(float dp, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    // Algorithm for converting pixels to dp measurements
//    public static float convertPixelsToDp(float px, Context context) {
//        Resources resources = context.getResources();
//        DisplayMetrics metrics = resources.getDisplayMetrics();
//        return px / ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
//    }
}
