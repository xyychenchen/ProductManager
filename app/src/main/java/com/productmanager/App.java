package com.productmanager;

import android.app.Application;
import androidx.multidex.MultiDex;

/**
 * 应用程序入口类
 * 启用MultiDex支持以容纳Apache POI库
 */
public class App extends Application {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
