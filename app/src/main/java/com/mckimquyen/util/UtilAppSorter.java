package com.mckimquyen.util;

import com.mckimquyen.enums.SortType;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Utility class for sorting apps by various criteria
 * Note: 2023.03.20 tried to convert to Kotlin but failed
 */
public class UtilAppSorter {

    public static void sort(ArrayList<App> apps, SortType sortType) {
        switch (sortType) {
            case LABEL_DESCENDING:
                sortByLabelDescending(apps);
                break;
            case INSTALL_DATE_ASCENDING:
                sortByInstallDateAscending(apps);
                break;
            case INSTALL_DATE_DESCENDING:
                sortByInstallDateDescending(apps);
                break;
            case OPEN_COUNT_ASCENDING:
                sortByOpenCountAscending(apps);
                break;
            case OPEN_COUNT_DESCENDING:
                sortByOpenCountDescending(apps);
                break;
            case ICON_COLOR_ASCENDING:
                sortByIconColorAscending(apps);
                break;
            case ICON_COLOR_DESCENDING:
                sortByIconColorDescending(apps);
                break;
            default:
                sortByLabelAscending(apps);
                break;
        }
        // TimSort is stable: keep the selected base sort inside each user-approved zone.
        apps.sort(Comparator
                .comparingInt((App app) -> app.getPinnedZone().getRank())
                .thenComparing(app -> app.isFavorite() ? 0 : 1)
                .thenComparingInt(app -> app.getOrderNumber() < 0
                        ? Integer.MAX_VALUE
                        : app.getOrderNumber())
                .thenComparing(app -> app.getFolderName() == null ? "" : app.getFolderName(),
                        String.CASE_INSENSITIVE_ORDER));
    }

    private static void sortByLabelAscending(ArrayList<App> apps) {
        apps.sort(Comparator.comparing(app -> app.getLabel().toString().toLowerCase()));
    }

    private static void sortByLabelDescending(ArrayList<App> apps) {
        sortByLabelAscending(apps);
        Collections.reverse(apps);
    }

    private static void sortByInstallDateAscending(ArrayList<App> apps) {
        apps.sort(Comparator.comparingLong(App::getInstallDate).reversed());
    }

    private static void sortByInstallDateDescending(ArrayList<App> apps) {
        apps.sort(Comparator.comparingLong(App::getInstallDate));
    }

    private static void sortByOpenCountAscending(ArrayList<App> apps) {
        apps.sort((a1, a2) -> Long.compare(a2.getOpenCount(), a1.getOpenCount()));
    }

    private static void sortByOpenCountDescending(ArrayList<App> apps) {
        apps.sort((a1, a2) -> Long.compare(a1.getOpenCount(), a2.getOpenCount()));
    }

    private static void sortByIconColorAscending(ArrayList<App> apps) {
        apps.sort((a1, a2) -> {
            float hue1 = UtilColor.getHueColorFromApp(a1);
            float hue2 = UtilColor.getHueColorFromApp(a2);
            return Float.compare(hue2, hue1);
        });
    }

    private static void sortByIconColorDescending(ArrayList<App> apps) {
        apps.sort((a1, a2) -> {
            float hue1 = UtilColor.getHueColorFromApp(a1);
            float hue2 = UtilColor.getHueColorFromApp(a2);
            return Float.compare(hue1, hue2);
        });
    }
}
