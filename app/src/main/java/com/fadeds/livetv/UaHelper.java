package com.fadeds.livetv;

import android.content.Context;
import android.webkit.WebView;

/**
 * 浼鎴?PC 娴忚鍣ㄦ墦寮€澶棰戯紝閬垮厤鍏跺脊鍑?涓嬭浇 App"寮曞椤点€?
 * Chrome 涓荤増鏈彿涓嶅啓姝伙紝鑰屾槸鏉ヨ嚜璁惧鐪熷疄 WebView 鍐呮牳銆?
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

    /** 娉ㄥ叆妗岄潰 UA 鍒版寚瀹?WebView */
    public static void apply(WebView wv, Context ctx) {
        wv.getSettings().setUserAgentString(desktopUa(ctx));
    }
}