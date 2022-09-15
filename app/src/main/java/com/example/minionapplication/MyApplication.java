package com.example.minionapplication;

import android.app.Application;

import timber.log.Timber;

/**
 * @author albert
 * @date 2022/9/15 11:50
 * @mail 416392758@@gmail.com
 * @since v1
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
    }
}
