package com.mckimquyen.util;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.mckimquyen.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for managing icon packs
 * <p>
 * Supports loading and applying custom icon packs from other apps
 * Note: 2023.03.20 tried to convert to Kotlin but failed
 */
public class UtilIconPackManager {

    private static final String TAG = "IconPackManager";
    private static final String XML_TAG_ICONBACK = "iconback";
    private static final String XML_TAG_ICONMASK = "iconmask";
    private static final String XML_TAG_ICONUPON = "iconupon";
    private static final String XML_TAG_SCALE = "scale";
    private static final String XML_TAG_ITEM = "item";
    private static final String XML_ATTR_IMG = "img";
    private static final String XML_ATTR_IMG1 = "img1";
    private static final String XML_ATTR_FACTOR = "factor";
    private static final String XML_ATTR_COMPONENT = "component";
    private static final String XML_ATTR_DRAWABLE = "drawable";

    private Application mApplication;
    private ArrayList<IconPack> mIconPacks;

    /**
     * Represents an icon pack with its resources and metadata
     */
    public class IconPack {

        public String mPackageName;
        public String mName;

        private boolean mLoaded = false;
        private final HashMap<String, String> mPackagesDrawables = new HashMap<>();
        private final List<Bitmap> mBackImages = new ArrayList<>();
        private Bitmap mMaskImage;
        private Bitmap mFrontImage;
        private float mFactor = 1.0f;
        private final Paint mPaint;
        private Resources mIconPackRes;

        public IconPack() {
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setFilterBitmap(true);
            mPaint.setDither(true);
        }

        /**
         * Load icon pack resources from appfilter.xml
         */
        public void load() {
            // Clean up old bitmaps before loading new ones
            cleanup();

            PackageManager pm = mApplication.getPackageManager();
            try {
                mIconPackRes = pm.getResourcesForApplication(mPackageName);
                XmlPullParser xpp = getAppFilterParser();

                if (xpp != null) {
                    parseAppFilter(xpp);
                }
                mLoaded = true;
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Cannot load icon pack: " + mPackageName);
            } catch (XmlPullParserException e) {
                Log.d(TAG, "Cannot parse icon pack appfilter.xml");
            } catch (IOException e) {
                Log.e(TAG, "IO error loading icon pack", e);
            }
        }

        /**
         * Clean up bitmap resources to prevent memory leaks
         */
        public void cleanup() {
            // Recycle back images
            for (Bitmap bitmap : mBackImages) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            mBackImages.clear();

            // Recycle mask image
            if (mMaskImage != null && !mMaskImage.isRecycled()) {
                mMaskImage.recycle();
                mMaskImage = null;
            }

            // Recycle front image
            if (mFrontImage != null && !mFrontImage.isRecycled()) {
                mFrontImage.recycle();
                mFrontImage = null;
            }
        }

        @SuppressLint("DiscouragedApi")
        private XmlPullParser getAppFilterParser() throws IOException, XmlPullParserException {
            int appfilterId = mIconPackRes.getIdentifier("appfilter", "xml", mPackageName);
            if (appfilterId > 0) {
                return mIconPackRes.getXml(appfilterId);
            }

            // No resource found, try to open from assets
            try (InputStream appFilterStream = mIconPackRes.getAssets().open("appfilter.xml")) {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser xpp = factory.newPullParser();
                xpp.setInput(appFilterStream, "utf-8");
                return xpp;
            } catch (IOException e) {
                Log.d(TAG, "appfilter.xml not found in assets");
                return null;
            }
        }

        private void parseAppFilter(XmlPullParser xpp) throws XmlPullParserException, IOException {
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = xpp.getName();
                    switch (tagName) {
                        case XML_TAG_ICONBACK:
                            parseIconBack(xpp);
                            break;
                        case XML_TAG_ICONMASK:
                            mMaskImage = parseSimpleImage(xpp);
                            break;
                        case XML_TAG_ICONUPON:
                            mFrontImage = parseSimpleImage(xpp);
                            break;
                        case XML_TAG_SCALE:
                            parseScale(xpp);
                            break;
                        case XML_TAG_ITEM:
                            parseItem(xpp);
                            break;
                    }
                }
                eventType = xpp.next();
            }
        }

        private void parseIconBack(XmlPullParser xpp) {
            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                if (xpp.getAttributeName(i).startsWith(XML_ATTR_IMG)) {
                    Bitmap iconBack = loadBitmap(xpp.getAttributeValue(i));
                    if (iconBack != null) {
                        mBackImages.add(iconBack);
                    }
                }
            }
        }

        private Bitmap parseSimpleImage(XmlPullParser xpp) {
            if (xpp.getAttributeCount() > 0 && XML_ATTR_IMG1.equals(xpp.getAttributeName(0))) {
                return loadBitmap(xpp.getAttributeValue(0));
            }
            return null;
        }

        private void parseScale(XmlPullParser xpp) {
            if (xpp.getAttributeCount() > 0 && XML_ATTR_FACTOR.equals(xpp.getAttributeName(0))) {
                try {
                    mFactor = Float.parseFloat(xpp.getAttributeValue(0));
                } catch (NumberFormatException e) {
                    mFactor = 1.0f;
                }
            }
        }

        private void parseItem(XmlPullParser xpp) {
            String componentName = null;
            String drawableName = null;

            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                String attrName = xpp.getAttributeName(i);
                if (XML_ATTR_COMPONENT.equals(attrName)) {
                    componentName = xpp.getAttributeValue(i);
                } else if (XML_ATTR_DRAWABLE.equals(attrName)) {
                    drawableName = xpp.getAttributeValue(i);
                }
            }

            if (componentName != null && !mPackagesDrawables.containsKey(componentName)) {
                mPackagesDrawables.put(componentName, drawableName);
            }
        }

        @SuppressLint("DiscouragedApi")
        private Bitmap loadBitmap(String drawableName) {
            int id = mIconPackRes.getIdentifier(drawableName, "drawable", mPackageName);
            if (id > 0) {
                Drawable drawable = ResourcesCompat.getDrawable(mIconPackRes, id, null);
                if (drawable instanceof BitmapDrawable) {
                    return ((BitmapDrawable) drawable).getBitmap();
                }
            }
            return null;
        }

        /**
         * Get custom icon for a package, or generate one from default bitmap
         */
        public Bitmap getIconForPackage(String appPackageName, Bitmap defaultBitmap) {
            if (!mLoaded) {
                load();
            }

            String componentName = getComponentName(appPackageName);
            if (componentName == null) {
                return generateBitmap(defaultBitmap);
            }

            // Try to get icon from mapped drawables
            String drawable = mPackagesDrawables.get(componentName);
            if (drawable != null) {
                Bitmap bitmap = loadBitmap(drawable);
                return (bitmap != null) ? bitmap : generateBitmap(defaultBitmap);
            }

            // Try to get icon using component name as filename
            Bitmap iconFromComponentName = tryLoadFromComponentName(componentName);
            if (iconFromComponentName != null) {
                return iconFromComponentName;
            }

            return generateBitmap(defaultBitmap);
        }

        private String getComponentName(String appPackageName) {
            PackageManager pm = mApplication.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(appPackageName);
            if (launchIntent == null || launchIntent.getComponent() == null) {
                return null;
            }

            try {
                return launchIntent.getComponent().toString();
            } catch (Exception e) {
                return null;
            }
        }

        @SuppressLint("DiscouragedApi")
        private Bitmap tryLoadFromComponentName(String componentName) {
            int start = componentName.indexOf("{") + 1;
            int end = componentName.indexOf("}", start);
            if (end > start) {
                String drawable = componentName.substring(start, end)
                        .toLowerCase(Locale.getDefault())
                        .replace(".", "_")
                        .replace("/", "_");
                if (mIconPackRes.getIdentifier(drawable, "drawable", mPackageName) > 0) {
                    return loadBitmap(drawable);
                }
            }
            return null;
        }

        /**
         * Generate icon using icon pack background, mask, and front images
         */
        private Bitmap generateBitmap(Bitmap defaultBitmap) {
            if (defaultBitmap == null || mBackImages.isEmpty()) {
                return defaultBitmap;
            }

            Bitmap backImage = getMostAppropriateBackImage(defaultBitmap);
            int width = backImage.getWidth();
            int height = backImage.getHeight();

            Bitmap result = createBitmapSafe(width, height);
            if (result == null) return defaultBitmap;

            Canvas canvas = new Canvas(result);
            canvas.drawBitmap(backImage, 0, 0, null);

            // Calculate scaled destination rect
            Rect srcRect = new Rect(0, 0, defaultBitmap.getWidth(), defaultBitmap.getHeight());
            RectF destRect = calculateDestRect(width, height);

            // Apply mask if available
            if (mMaskImage != null) {
                Bitmap maskedIcon = applyMask(defaultBitmap, srcRect, destRect, width, height);
                if (maskedIcon != null) {
                    canvas.drawBitmap(maskedIcon, 0, 0, mPaint);
                }
            } else {
                canvas.drawBitmap(defaultBitmap, srcRect, destRect, mPaint);
            }

            // Draw front image if available
            if (mFrontImage != null) {
                canvas.drawBitmap(mFrontImage, 0, 0, mPaint);
            }

            return result;
        }

        private Bitmap createBitmapSafe(int width, int height) {
            try {
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Out of memory creating bitmap", e);
                return null;
            }
        }

        private RectF calculateDestRect(int width, int height) {
            float scaledWidth = mFactor * width;
            float scaledHeight = mFactor * height;
            float centerX = width / 2.0f;
            float centerY = height / 2.0f;
            return new RectF(
                    centerX - scaledWidth / 2.0f,
                    centerY - scaledHeight / 2.0f,
                    centerX + scaledWidth / 2.0f,
                    centerY + scaledHeight / 2.0f
            );
        }

        private Bitmap applyMask(Bitmap defaultBitmap, Rect srcRect, RectF destRect, int width, int height) {
            Bitmap mask = createBitmapSafe(width, height);
            if (mask == null) return null;

            Canvas maskCanvas = new Canvas(mask);
            maskCanvas.drawBitmap(defaultBitmap, srcRect, destRect, mPaint);
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            maskCanvas.drawBitmap(mMaskImage, 0, 0, mPaint);
            mPaint.setXfermode(null);

            return mask;
        }

        /**
         * Get most appropriate back image by matching color hue with default bitmap
         */
        private Bitmap getMostAppropriateBackImage(Bitmap defaultBitmap) {
            if (mBackImages.size() == 1) {
                return mBackImages.get(0);
            }

            float defaultHue = UtilColor.getHueColorFromColor(
                    UtilColor.getPaletteColorFromBitmap(defaultBitmap)
            );

            int bestIndex = 0;
            float minDifference = Float.MAX_VALUE;

            for (int i = 0; i < mBackImages.size(); i++) {
                float backHue = UtilColor.getHueColorFromColor(
                        UtilColor.getPaletteColorFromBitmap(mBackImages.get(i))
                );
                float difference = Math.abs(defaultHue - backHue);
                if (difference < minDifference) {
                    minDifference = difference;
                    bestIndex = i;
                }
            }

            return mBackImages.get(bestIndex);
        }
    }

    /**
     * Get list of available icon packs installed on device
     *
     * @param forceReload Force reload even if already cached
     * @param application Application context
     * @return List of available icon packs
     */
    public ArrayList<IconPack> getAvailableIconPacksWithIcons(boolean forceReload, Application application) {
        mApplication = application;

        if (mIconPacks == null || forceReload) {
            mIconPacks = loadIconPacks();
        }
        return mIconPacks;
    }

    private ArrayList<IconPack> loadIconPacks() {
        ArrayList<IconPack> iconPacks = new ArrayList<>();
        PackageManager pm = mApplication.getPackageManager();

        // Query all launcher activities that might be icon packs
        List<ResolveInfo> resolveInfos = new ArrayList<>();
        for (String launcher : mApplication.getResources().getStringArray(R.array.icon_pack_launchers)) {
            resolveInfos.addAll(pm.queryIntentActivities(new Intent(launcher), PackageManager.GET_META_DATA));
        }

        // Create IconPack objects for each found package
        for (ResolveInfo ri : resolveInfos) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(ri.activityInfo.packageName, PackageManager.GET_META_DATA);
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null) {
                    IconPack iconPack = new IconPack();
                    iconPack.mPackageName = ri.activityInfo.packageName;
                    iconPack.mName = label.toString();
                    iconPacks.add(iconPack);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Icon pack not found: " + ri.activityInfo.packageName);
            }
        }

        return iconPacks;
    }
}
