package com.hamibot.cctvtv;

import android.content.Context;
import android.webkit.WebView;

/**
 * 伪装成 PC 浏览器打开央视频，避免其弹出"下载 App"引导页。
 * Chrome 主版本号不写死，而是来自设备真实 WebView 内核。
 */
public class UaHelper {

    public static String desktopUa(Context ctx) {
        String real = WebViewKernel.versionName(ctx);
        int major = WebViewKernel.majorVersion(real);
        if (major <= 0) {
            major = 114;
        }
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/" + major + ".0.0.0 Safari/537.36";
    }

    /** 注入桌面 UA 到指定 WebView */
    public static void apply(WebView wv, Context ctx) {
        wv.getSettings().setUserAgentString(desktopUa(ctx));
    }
}