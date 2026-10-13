package com.fadeds.livetv;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class ChannelStore {
    private final List<Channel> list = new ArrayList<>();

    public static ChannelStore load(Context context) {
        ChannelStore store = new ChannelStore();
        String text = null;
        try {
            File f = new File(context.getFilesDir(), "playlist.m3u");
            if (f.exists() && f.length() > 0) {
                text = M3u.readAll(new FileInputStream(f));
            }
        } catch (Exception ignored) {
        }
        if (text == null || text.isEmpty()) {
            try {
                text = M3u.readAll(context.getAssets().open("ipv4.m3u"));
            } catch (Exception ignored) {
            }
        }
        if (text != null) {
            store.list.addAll(M3u.parse(text));
        }
        return store;
    }

    public void replaceAll(List<Channel> fresh) {
        list.clear();
        list.addAll(fresh);
    }

    public int size() { return list.size(); }

    public Channel get(int i) {
        if (i < 0) i = 0;
        if (i >= list.size()) i = list.size() - 1;
        return list.get(i);
    }

    public int indexOfName(String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    public List<Channel> all() { return list; }
}
