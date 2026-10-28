package com.mckimquyen.ui.home;

import android.app.Activity;
import android.view.View;

import com.google.android.material.tabs.TabLayoutMediator;
import com.mckimquyen.model.App;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;

import java.util.ArrayList;

/** ARCH-002: what {@link LensViewHolder} needs from {@code ActHome}; one-line delegates there. */
public interface LensViewHolderHost {
    Activity activity();

    /** ActHome's own settings instance (may be null before setupViews ran). */
    UtilSettings utilSettings();

    /** The last loaded app list (may be null). */
    ArrayList<App> listApp();

    ArrayList<App> appsForLens(String lensId);

    boolean isSearchShowing();

    void refreshHiddenRecovery();

    void offerPinchConfirmation(float curvature);

    void offerLensMenu(View anchor);

    void dismissLensMenu();

    View lensMenuAnchorAt(View lens, float x, float y);

    void requestLensManagement();

    void requestSearch();

    boolean pendingAutoExportLens();

    void clearPendingAutoExportLens();

    void requestLensExport();

    /** FISH-038b: a held app began a drag; show the strip of other lenses to drop it on. */
    void offerDropStrip(com.mckimquyen.views.LensDragPayload payload);

    /** FISH-038b: the system refused the drag; remove the strip before showing the fallback menu. */
    void dismissDropStrip();

    /** The single writer of {@code ActHome.lensViews}; the field stays on ActHome because tests read it by name. */
    void setActiveLensView(LensView view);

    LensView activeLensView();

    /** Storage stays on ActHome for ActHomeLensMediatorLeakTest, which reads the private field by reflection. */
    void setLensTabMediator(TabLayoutMediator mediator);

    TabLayoutMediator lensTabMediator();
}
