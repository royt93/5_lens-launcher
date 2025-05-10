package com.mckimquyen.app;

import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.mckimquyen.sdkadbmob.AdMobManager;
import com.mckimquyen.services.EditedObservable;
import com.mckimquyen.services.TaskSortApps;
import com.mckimquyen.services.TaskUpdateApps;
import com.mckimquyen.services.UpdatedObservable;
import com.orm.SugarApp;

import java.util.Observable;
import java.util.Observer;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

//done
//review in app
//120hz
//apply new logic applovin utils
//splash screen <<< app nay k can splash dau loi dep trai
//screen screenOrientation
//keystore
//bug khi toggle lock/unlock, hide/unhide ko work
//khi cai ung dung dau tien, bi loi UI light mode
//bug load apps vo cuc o tab APPS
//dialog policy first
//ic_launcher
//setting search icon pack
//check home default khi start launcher
//lock/unlock app
//github
//license
//showMediationDebuggerApplovin
//switch customized like ios
//internal webview
//changelog view
//font scale
//app launcher uninstall app
//app launcher app infor

//2023.03.18 tried to convert to kotlin but failed
public class RApplication extends SugarApp implements Observer {

    @Override
    public void onCreate() {
        super.onCreate();

//        ApplovinKt.setupApplovinAd(this);
        setupAdmob();
        UpdatedObservable.getInstance().addObserver(this);
        EditedObservable.getInstance().addObserver(this);
        updateApps();
    }

    private void setupAdmob() {
        new Thread(() -> {
            MobileAds.initialize(RApplication.this, initializationStatus -> {
                // Không làm gì
            });
            AdMobManager.INSTANCE.init(this, new Function2<Boolean, String, Unit>() {
                @Override
                public Unit invoke(Boolean success, String gaidCurrent) {
                    Log.d("roy93~", "AdMobManager init success " + success + ", gaidCurrent " + gaidCurrent);
                    return null;
                }
            });
        }).start();
//        registerActivityLifecycleCallbacks(new AppLifecycleListener(new Function2<Boolean, Activity, Unit>() {
//            @Override
//            public Unit invoke(Boolean isForeground, Activity activity) {
//                if (isForeground) {
////                    Log.d("roy93~", "App moved to Foreground");
////                    Log.d("roy93~", "activity.getClass().getSimpleName() " + activity.getClass().getSimpleName());
////                    Log.d("roy93~", "SplashActivity.class.getSimpleName() " + SplashActivity.class.getSimpleName());
//                } else {
////                    Log.d("roy93~", "App moved to Background");
//                }
//                return null;
//            }
//        }, new Function1<Activity, Unit>() {
//            @Override
//            public Unit invoke(Activity activity) {
////                Log.d("roy93~", "callbackActivityCreated");
////                Log.d("roy93~", "activity.getClass().getSimpleName() " + activity.getClass().getSimpleName());
////                Log.d("roy93~", "SplashActivity.class.getSimpleName() " + SplashActivity.class.getSimpleName());
//                return null;
//            }
//        }));
    }

    @Override
    public void update(Observable observable, Object data) {
        if (observable instanceof UpdatedObservable) {
            updateApps();
        } else if (observable instanceof EditedObservable) {
            editApps();
        }
    }

    private void updateApps() {
        new TaskUpdateApps(
                getPackageManager(),
                getApplicationContext(),
                this)
                .execute();
    }

    private void editApps() {
        new TaskSortApps(
                getApplicationContext(),
                this)
                .execute();
    }
}
