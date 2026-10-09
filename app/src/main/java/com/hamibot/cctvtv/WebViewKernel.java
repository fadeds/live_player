package com.hamibot.cctvtv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.List;
import java.util.Locale;

public class WebViewKernel {

    /** 读取当前 WebView（系统内核）的版本号字符串，如 "105.0.5195.136" */
    public static String versionName(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            List<PackageInfo> pkgs = pm.getInstalledPackages(0);
            String best = "";
            long bestCode = -1;
            for (PackageInfo pi : pkgs) {
                String pkg = pi.packageName == null ? "" : pi.packageName.toLowerCase(Locale.ROOT);
                boolean isWebView = pkg.equals("com.google.android.webview")
                        || pkg.equals("com.android.webview")
                        || pkg.equals("com.huawei.webview")
                        || pkg.equals("com.sec.android.webview")
                        || pkg.endsWith(".webview")
                        || (pkg.contains("webview") && pkg.contains("chromium"))
                        || pkg.equals("org.chromium.webview_shell");
                if (isWebView && pi.versionCode > bestCode) {
                    bestCode = pi.versionCode;
                    best = pi.versionName == null ? "" : pi.versionName;
                }
            }
            if (best.isEmpty()) {
                try {
                    best = android.webkit.WebView.getDefaultUserAgent(ctx);
                } catch (Throwable t) {
                    best = "";
                }
            }
            return best;
        } catch (Throwable t) {
            return "";
        }
    }

    public static int majorVersion(String version) {
        if (version == null || version.isEmpty()) return -1;
        String parts = version.split("[^0-9]")[0];
        try {
            return Integer.parseInt(parts);
        } catch (Exception e) {
            return -1;
        }
    }
}