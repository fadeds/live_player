package com.hamibot.cctvtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    static final String HOME_URL = "https://www.yangshipin.cn/tv/home";
    static final long SWITCH_WATCHDOG_MS = 20_000;

    private WebView web;
    private FrameLayout overlay;
    private TextView overlayTitle;
    private TextView overlayState;
    private LinearLayout drawer;
    private ListView channelList;
    private TextView drawerHint;
    private View drawerScrim;
    private TextView channelFab;

    private ChannelStore store;
    private ArrayList<Item> items = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int current = 0;
    private boolean playerReady = false;
    private boolean usingFallback = false;
    private boolean drawerOpen = false;
    private Runnable watchdog;

    private String fixJs; // player_fix.js 内容

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = ChannelStore.load(this);
        buildChannelItems();

        web = findViewById(R.id.webview);
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        overlayState = findViewById(R.id.overlayState);
        drawer = findViewById(R.id.drawer);
        channelList = findViewById(R.id.channelList);
        drawerHint = findViewById(R.id.drawerHint);
        drawerScrim = findViewById(R.id.drawerScrim);
        channelFab = findViewById(R.id.channelFab);
        boolean isTv = getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK);
        drawerHint.setText(isTv
                ? "OK 打开/换台列表   ▲▼ 换台   搜索键 语音换台"
                : "≡ 打开频道列表   点频道换台   语音键换台");
        channelList.setAdapter(new ChannelAdapter());

        channelFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer();
            }
        });
        drawerScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer();
            }
        });
        channelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                toggleDrawer();
                switchChannel(position);
            }
        });

        setupWebView();
        loadFixJs();

        int major = WebViewKernel.majorVersion(WebViewKernel.versionName(this));
        if (major > 0 && major < 90) {
            new AlertDialog.Builder(this)
                    .setTitle("网页内核过旧")
                    .setMessage("设备 WebView 版本为 " + major + "，可能无法正常播放直播画面。"
                            + "建议到应用商店升级「Android System WebView」后重启应用。")
                    .setPositiveButton("知道了", null)
                    .show();
        }

        web.loadUrl(HOME_URL);

        Runnable guard = new Runnable() {
            @Override
            public void run() {
                if (web.getProgress() == 100 && web.getContentHeight() > 0) {
                    showOverlay(true);
                    overlayTitle.setText(store.get(current).name);
                    jsPlay(store.get(current).name);
                } else {
                    handler.postDelayed(this, 800);
                }
            }
        };
        handler.postDelayed(guard, 2500);
    }

    // ---------- WebView ----------
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        UaHelper.apply(web, this);

        web.addJavascriptInterface(new JsBridge(this), "CctvBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAndInit();
                if (usingFallback || web.getProgress() == 100) {
                    // 备用源/回源：仍走注入后播放当前台
                    try {
                        injectAndInit();
                        jsPlay(store.get(current).name);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                if (cm.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    android.util.Log.e("WebView", cm.message());
                }
                return true;
            }
        });
    }

    private void loadFixJs() {
        try {
            InputStream is = getAssets().open("player_fix.js");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) != -1) bos.write(b, 0, n);
            is.close();
            fixJs = bos.toString("UTF-8");
        } catch (Exception e) {
            fixJs = "";
        }
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    private void eval(String js) {
        try {
            web.evaluateJavascript(js, null);
        } catch (Throwable t) {
            android.util.Log.e("MainActivity", "eval fail", t);
        }
    }

    private void injectAndInit() {
        if (fixJs.isEmpty()) return;
        StringBuilder names = new StringBuilder("[");
        for (Channel c : store.all()) {
            if (names.length() > 1) names.append(',');
            names.append('"').append(esc(c.name)).append('"');
        }
        names.append(']');
        eval("(function(){ " + fixJs + " })();");
        eval("window.CctvFix && window.CctvFix.init('" + esc(names.toString()) + "');");
        eval("window.CctvFix && window.CctvFix.listChannels();");
    }

    private void jsPlay(String name) {
        eval("window.CctvFix && window.CctvFix.playChannel('" + esc(name) + "');");
    }

    // ---------- 换台 ----------
    private void switchChannel(int index) {
        if (index < 0) index = store.size() - 1;
        if (index >= store.size()) index = 0;
        current = index;
        playerReady = false;
        Channel ch = store.get(current);

        showOverlay(true);
        overlayTitle.setText(ch.displayNumber() + "  " + ch.name);
        overlayState.setText("正在切换...");

        if (watchdog != null) handler.removeCallbacks(watchdog);
        watchdog = new Runnable() {
            @Override
            public void run() {
                if (playingMatchesCurrent()) return;
                overlayState.setText("超时，切入备用源");
                loadFallback(ch);
            }
        };
        handler.postDelayed(watchdog, SWITCH_WATCHDOG_MS);

        if (usingFallback) {
            // 当前停在央视网备用页，先回央视频主页面再播目标台
            usingFallback = false;
            web.loadUrl(HOME_URL);
        } else {
            jsPlay(ch.name);
            injectAndInit();
        }
    }

    private void loadFallback(Channel ch) {
        String url = ch.fallbackUrl();
        if (url != null) {
            usingFallback = true;
            setOverlayStateLine("央视网备用源");
            web.loadUrl(url);
        } else {
            // 卫视没有确定的备用源，物理回源央视频首页再播
            setOverlayStateLine("卫视备用源暂无，重试央视频");
            web.reload();
        }
    }

    private boolean playingMatchesCurrent() {
        return playerReady && current >= 0 && current < store.size();
    }

    private void showOverlay(boolean show) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlay.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) overlay.requestFocus();
            }
        });
    }

    private void setOverlayStateLine(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    overlayState.setText(s);
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------- JS 回调 ----------
    public void onChannelsFromJs(final String json) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (json == null || json.equals("ping") || !json.trim().startsWith("[")) return;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    for (int i = 0; i < arr.length() && i < store.size(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        Channel c = store.get(i);
                        if (o.optString("name").equals(c.name)) {
                            c.program = o.optString("program", "");
                        }
                    }
                    buildChannelItems();
                    ((BaseAdapter) channelList.getAdapter()).notifyDataSetChanged();
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "channels parse fail", e);
                }
            }
        });
    }

    public void onProgramFromJs(final String name, final String program) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (name == null || program == null) return;
                for (Channel c : store.all()) {
                    if (c.name.equals(name)) {
                        c.program = program;
                        ((BaseAdapter) channelList.getAdapter()).notifyDataSetChanged();
                        break;
                    }
                }
            }
        });
    }

    public void onPlayerStateFromJs(final String raw) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (raw == null) return;
                String st = raw.split(":")[0];
                if (st.equals("playing")) {
                    playerReady = true;
                    if (watchdog != null) handler.removeCallbacks(watchdog);
                    showOverlay(false);
                } else {
                    playerReady = false;
                }
            }
        });
    }

    public void requestTap(final float x, final float y) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = SystemClock.uptimeMillis();
                    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                    web.dispatchTouchEvent(down);
                    down.recycle();
                    MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0);
                    web.dispatchTouchEvent(up);
                    up.recycle();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // ---------- 频道列表 ----------
    static class Item {
        String num;
        String name;
        String program;
        boolean current;

        Item(String n, String nm, String p, boolean c) {
            num = n; name = nm; program = p; current = c;
        }
    }

    private void buildChannelItems() {
        items.clear();
        for (Channel c : store.all()) {
            items.add(new Item(c.displayNumber(), c.name, c.program, c.index == current));
        }
    }

    class ChannelAdapter extends BaseAdapter {
        @Override
        public int getCount() { return items.size(); }

        @Override
        public Object getItem(int i) { return items.get(i); }

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                int padH = getResources().getDimensionPixelSize(R.dimen.ch_padding_h);
                int padV = getResources().getDimensionPixelSize(R.dimen.ch_padding_v);
                row.setPadding(padH, padV, padH, padV);
                row.setBackgroundResource(R.drawable.channel_selector);

                TextView num = new TextView(MainActivity.this);
                num.setId(R.id.chNum);
                num.setTextColor(Color.parseColor("#FFCC66"));
                num.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ch_num));
                row.addView(num);

                TextView name = new TextView(MainActivity.this);
                name.setId(R.id.chName);
                name.setTextColor(Color.WHITE);
                name.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ch_name));
                name.setTypeface(null, Typeface.BOLD);
                row.addView(name);

                TextView prog = new TextView(MainActivity.this);
                prog.setId(R.id.chProg);
                prog.setTextColor(Color.parseColor("#99FFFFFF"));
                prog.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ch_prog));
                row.addView(prog);
            }
            Item it = items.get(i);
            ((TextView) row.findViewById(R.id.chNum)).setText(it.num);
            ((TextView) row.findViewById(R.id.chName)).setText(it.name);
            TextView prog = (TextView) row.findViewById(R.id.chProg);
            prog.setText(it.program != null && !it.program.isEmpty() ? it.program : "正在获取节目...");
            if (it.current) {
                row.setBackgroundColor(Color.parseColor("#6633B5E5"));
            } else {
                row.setBackgroundResource(R.drawable.channel_selector);
            }
            return row;
        }
    }

    private void toggleDrawer() {
        drawerOpen = !drawerOpen;
        drawer.animate().translationX(drawerOpen ? 0 : -drawer.getWidth()).setDuration(160).start();
        drawerScrim.setVisibility(drawerOpen ? View.VISIBLE : View.GONE);
        if (drawerOpen) {
            channelList.requestFocus();
        } else {
            web.requestFocus();
        }
    }

    // ---------- 语音搜索 ----------
    private void startVoiceSearch() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "说出频道，例如 湖南卫视、CCTV-1、新闻频道");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            startActivityForResult(i, 1001);
        } catch (Exception e) {
            Toast.makeText(this, "系统没有语音识别，检查是否安装语音助手", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results == null || results.isEmpty()) {
                Toast.makeText(this, "没听清，再试一次", Toast.LENGTH_SHORT).show();
                return;
            }
            String text = results.get(0);
            int idx = VoiceSearch.match(store.all(), text);
            if (idx >= 0) {
                String msg = "语音：切到 " + store.get(idx).name;
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                if (drawerOpen) toggleDrawer();
                switchChannel(idx);
            } else {
                Toast.makeText(this, "没找到频道「" + text + "」", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- 遥控器 ----------
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (drawerOpen) {
                int sel = channelList.getSelectedItemPosition();
                if (sel < 0) sel = current;
                toggleDrawer();
                switchChannel(sel);
            } else {
                toggleDrawer();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
                || keyCode == KeyEvent.KEYCODE_ASSIST
                || keyCode == KeyEvent.KEYCODE_MEDIA_RECORD) {
            startVoiceSearch();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            if (drawerOpen) {
                moveListSelection(-1);
            } else {
                switchChannel(current - 1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            if (drawerOpen) {
                moveListSelection(1);
            } else {
                switchChannel(current + 1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawerOpen) {
                toggleDrawer();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void moveListSelection(int delta) {
        int sel = channelList.getSelectedItemPosition();
        int next = sel < 0 ? current : sel + delta;
        if (next < 0) next = 0;
        if (next >= items.size()) next = items.size() - 1;
        channelList.setSelection(next);
    }

    @Override
    protected void onDestroy() {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        try { web.destroy(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}