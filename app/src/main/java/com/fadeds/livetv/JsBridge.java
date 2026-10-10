package com.fadeds.livetv;

import android.webkit.JavascriptInterface;

/** JS -> Android 妗ャ€傞〉闈㈤噷鐨勬敞鍏ヨ剼鏈€氳繃 window.CctvBridge 鍥炶皟杩欎簺鏂规硶銆?*/
public class JsBridge {

    private final MainActivity act;

    public JsBridge(MainActivity act) {
        this.act = act;
    }

    /** 椤甸潰涓婃姤棰戦亾鍒楄〃锛堝惈姝ｅ湪鎾嚭鑺傜洰锛夛紝json 涓?JSON 瀛楃涓叉暟缁?*/
    @JavascriptInterface
    public void onChannels(String json) {
        act.onChannelsFromJs(json);
    }

    /** 椤甸潰涓婃姤褰撳墠棰戦亾姝ｅ湪鎾嚭鐨勬爮鐩?*/
    @JavascriptInterface
    public void onProgram(String name, String program) {
        act.onProgramFromJs(name, program);
    }

    /** 鎾斁鐘舵€侊細playing:CCTV-1 缁煎悎 / loading:xx / stall:xx */
    @JavascriptInterface
    public void onPlayerState(String raw) {
        act.onPlayerStateFromJs(raw);
    }

    /** 璇锋眰鍦?WebView 鍐呬簬椤甸潰鍧愭爣 (x, y) 妯℃嫙涓€娆＄湡瀹炶Е鎽?*/
    @JavascriptInterface
    public void requestTap(final float x, final float y) {
        act.requestTap(x, y);
    }
}