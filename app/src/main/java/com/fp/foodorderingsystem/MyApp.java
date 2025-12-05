package com.fp.foodorderingsystem;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

public class MyApp extends Application {
	private static final String TAG = "AppDebug";

	@Override
	public void onCreate() {
		super.onCreate();

        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebuggable) {
			StrictMode.ThreadPolicy.Builder threadBuilder = new StrictMode.ThreadPolicy.Builder()
					.detectDiskReads()
					.detectDiskWrites()
					.detectNetwork()
					.detectCustomSlowCalls()
					.penaltyLog();

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				threadBuilder.detectUnbufferedIo();
			}

			StrictMode.ThreadPolicy threadPolicy = threadBuilder.build();

			StrictMode.VmPolicy.Builder vmBuilder = new StrictMode.VmPolicy.Builder()
					.detectLeakedSqlLiteObjects()
					.detectLeakedClosableObjects()
					.penaltyLog();

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				vmBuilder.detectCleartextNetwork();
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				vmBuilder.detectContentUriWithoutPermission();
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				vmBuilder.detectNonSdkApiUsage();
			}

			StrictMode.VmPolicy vmPolicy = vmBuilder.build();
			StrictMode.setThreadPolicy(threadPolicy);
			StrictMode.setVmPolicy(vmPolicy);
		}

		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			Log.e(TAG, "Uncaught exception in thread: " + t.getName(), e);
		});
	}
}
