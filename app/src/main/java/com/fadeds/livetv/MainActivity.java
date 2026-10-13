package com.fadeds.livetv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.HlsMediaSource;
import androidx.media3.exoplayer.upstream.DefaultHttpDataSource;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    static final long SWITCH_WATCHDOG_MS = 15_000;
    static final String STREAM_UA =
            "Mozilla/5.0 (Linux; Android 10; LivePlayer) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private PlayerView playerView;
    private ExoPlayer player;
    private FrameLayout overlay;
    private TextView overlayTitle;
    private TextView overlayState;
    private LinearLayout drawer;
    private ListView channelList;
    private TextView drawerHint;
    private View drawerScrim;
    private TextView channelFab;
    private TextView resBadge;

    private ChannelStore store;
    private ArrayList<Item> items = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int current = 0;
    private int candidate = 0;
    private int restarts = 0;
    private boolean switching = false;
    private boolean resShown = false;
    private boolean started = false;
    private boolean drawerOpen = false;
    private Runnable watchdog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = ChannelStore.load(this);
        buildChannelItems();

        playerView = findViewById(R.id.playerView);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && switching) {
                    onPlaying();
                } else if (state == Player.STATE_ENDED && !switching) {
                    restartCurrent("播放结束，重试");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (switching) {
                    advanceCandidate("播放失败，切换线路");
                } else {
                    restartCurrent("连接中断，重试");
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (!switching || resShown) return;
                if (videoSize.width > 0 && videoSize.height > 0) {
                    resShown = true;
                    resBadge.setText(videoSize.width + "x" + videoSize.height);
                    resBadge.setVisibility(View.VISIBLE);
                }
            }
        });

        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        overlayState = findViewById(R.id.overlayState);
        drawer = findViewById(R.id.drawer);
        channelList = findViewById(R.id.channelList);
        drawerHint = findViewById(R.id.drawerHint);
        drawerScrim = findViewById(R.id.drawerScrim);
        channelFab = findViewById(R.id.channelFab);
        resBadge = findViewById(R.id.resBadge);
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

        int last = getSharedPreferences("cfg", MODE_PRIVATE).getInt("last", 0);
        if (store.size() > 0) {
            switchChannel(last);
        } else {
            showOverlay(true);
            overlayTitle.setText("Live Player");
            overlayState.setText("正在加载频道列表...");
        }
        refreshM3uAsync();
    }

    // ---------- 频道列表远程刷新 ----------
    private void refreshM3uAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String text = M3u.fetchRemote();
                    final List<Channel> parsed = M3u.parse(text);
                    if (parsed.isEmpty()) return;
                    M3u.saveCache(MainActivity.this, text);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String curName = store.size() > 0 ? store.get(current).name : null;
                            store.replaceAll(parsed);
                            if (curName != null) {
                                int idx = store.indexOfName(curName);
                                current = idx >= 0 ? idx : 0;
                            }
                            buildChannelItems();
                            ((BaseAdapter) channelList.getAdapter()).notifyDataSetChanged();
                            if (!started && store.size() > 0) {
                                switchChannel(0);
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "refresh m3u failed", e);
                }
            }
        }).start();
    }

    // ---------- 换台 ----------
    private void switchChannel(int index) {
        if (store.size() == 0) return;
        if (index < 0) index = store.size() - 1;
        if (index >= store.size()) index = 0;
        current = index;
        candidate = 0;
        restarts = 0;
        started = true;
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putInt("last", current).apply();

        Channel ch = store.get(current);
        showOverlay(true);
        overlayTitle.setText(ch.displayNumber() + "  " + ch.name);
        overlayState.setText(ch.urlCount() > 1 ? "正在切换...（" + ch.urlCount() + " 条线路）" : "正在切换...");
        resBadge.setVisibility(View.GONE);
        resShown = false;
        switching = true;
        buildChannelItems();
        ((BaseAdapter) channelList.getAdapter()).notifyDataSetChanged();
        playCandidate();
    }

    private void playCandidate() {
        Channel ch = store.get(current);
        String url = ch.urlAt(candidate);
        if (url == null) {
            failAll("该频道没有可用线路，按▲▼换台");
            return;
        }
        startWatchdog();
        DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory();
        dsFactory.setConnectTimeoutMs(10000);
        dsFactory.setReadTimeoutMs(15000);
        dsFactory.setCrossProtocolRedirects(true);
        dsFactory.setUserAgent(STREAM_UA);
        HlsMediaSource source = new HlsMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(url));
        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void advanceCandidate(String msg) {
        if (!switching) return;
        candidate++;
        overlayState.setText(msg + "（" + Math.min(candidate + 1, store.get(current).urlCount())
                + "/" + store.get(current).urlCount() + "）");
        playCandidate();
    }

    private void failAll(String msg) {
        switching = false;
        if (watchdog != null) handler.removeCallbacks(watchdog);
        overlayState.setText(msg);
        try { player.stop(); } catch (Throwable ignored) {}
    }

    private void restartCurrent(String msg) {
        if (switching || store.size() == 0) return;
        restarts++;
        if (restarts > 3) {
            failAll("线路不稳定，按▲▼换台");
            return;
        }
        candidate = 0;
        resShown = false;
        resBadge.setVisibility(View.GONE);
        switching = true;
        showOverlay(true);
        overlayState.setText(msg);
        playCandidate();
    }

    private void startWatchdog() {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        watchdog = new Runnable() {
            @Override
            public void run() {
                if (!switching) return;
                advanceCandidate("超时，切换线路");
            }
        };
        handler.postDelayed(watchdog, SWITCH_WATCHDOG_MS);
    }

    private void onPlaying() {
        switching = false;
        restarts = 0;
        if (watchdog != null) handler.removeCallbacks(watchdog);
        showOverlay(false);
    }

    private void showOverlay(boolean show) {
        overlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) overlay.requestFocus();
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
            items.add(new Item(c.displayNumber(), c.name, c.group, c.index == current));
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
            prog.setText(it.program != null && !it.program.isEmpty() ? it.program : " ");
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
            channelList.setSelection(current);
        } else {
            channelList.clearFocus();
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
        handler.removeCallbacksAndMessages(null);
        try {
            player.release();
        } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
