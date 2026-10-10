package com.fadeds.livetv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ChannelStore {
    private final List<Channel> list = new ArrayList<>();

    public static ChannelStore load(Context context) {
        ChannelStore store = new ChannelStore();
        try {
            InputStream is = context.getAssets().open("channels.json");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            JSONArray arr = new JSONArray(bos.toString("UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                store.list.add(new Channel(
                        i,
                        o.optString("name"),
                        o.optString("key"),
                        o.optString("pid")));
            }
        } catch (Exception e) {
            android.util.Log.e("ChannelStore", "load channels failed", e);
        }
        if (store.list.isEmpty()) {
            for (int i = 0; i < 48; i++) {
                store.list.add(new Channel(i, "棰戦亾 " + (i + 1), "cctv1", ""));
            }
        }
        return store;
    }

    public int size() { return list.size(); }

    public Channel get(int i) {
        if (i < 0) i = 0;
        if (i >= list.size()) i = list.size() - 1;
        return list.get(i);
    }

    public List<Channel> all() { return list; }
}