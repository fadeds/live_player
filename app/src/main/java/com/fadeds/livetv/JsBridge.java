package com.fadeds.livetv;

import android.webkit.JavascriptInterface;

/** JS -> Android 桥。页面里的注入脚本通过 window.CctvBridge 回调这些方法。 */
public class JsBridge {

    private final MainActivity act;

    public JsBridge(MainActivity act) {
        this.act = act;
    }

    /** 页面上报频道列表（含正在播出节目），json 为 JSON 字符串数组 */
    @JavascriptInterface
    public void onChannels(String json) {
        act.onChannelsFromJs(json);
    }

    /** 页面上报当前频道正在播出的栏目 */
    @JavascriptInterface
    public void onProgram(String name, String program) {
        act.onProgramFromJs(name, program);
    }

    /** 播放状态：playing:CCTV-1 综合 / loading:xx / stall:xx */
    @JavascriptInterface
    public void onPlayerState(String raw) {
        act.onPlayerStateFromJs(raw);
    }

    /** 请求在 WebView 内于页面坐标 (x, y) 模拟一次真实触摸 */
    @JavascriptInterface
    public void requestTap(final float x, final float y) {
        act.requestTap(x, y);
    }
}