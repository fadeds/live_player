package com.fadeds.livetv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class Channel {
    public final int index;
    public final String name;
    public final String key;
    public final String group;
    public final List<String> urls = new ArrayList<>();
    private final LinkedHashSet<String> seen = new LinkedHashSet<>();

    public Channel(int index, String name, String group) {
        this.index = index;
        this.name = name;
        this.group = group == null ? "" : group;
        this.key = deriveKey(name);
    }

    public void addUrl(String url) {
        if (url == null || url.isEmpty()) return;
        if (seen.add(url)) urls.add(url);
    }

    public int urlCount() {
        return urls.size();
    }

    public String urlAt(int i) {
        return i >= 0 && i < urls.size() ? urls.get(i) : null;
    }

    /** 线路排序：m3u8 优先，php 跳转其次，其它（rtp/flv/udp）殿后。 */
    public void sortUrls() {
        List<String> hls = new ArrayList<>();
        List<String> php = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String u : urls) {
            if (u.contains(".m3u")) hls.add(u);
            else if (u.contains(".php")) php.add(u);
            else other.add(u);
        }
        urls.clear();
        urls.addAll(hls);
        urls.addAll(php);
        urls.addAll(other);
    }

    public String displayNumber() {
        int n = index + 1;
        return (n < 10 ? "0" : "") + n;
    }

    public boolean isCctv() {
        return key != null && key.startsWith("cctv");
    }

    /** 语音检索用的键：CCTV-5+ → cctv5plus，CCTV13 → cctv13。 */
    static String deriveKey(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches("^cctv\\d{1,2}\\+$")) {
            return n.replace("+", "plus");
        }
        return n.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
    }
}
