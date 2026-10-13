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
    private final List<String> uas = new ArrayList<>();
    private final List<String> refs = new ArrayList<>();

    public Channel(int index, String name, String group) {
        this.index = index;
        this.name = name;
        this.group = group == null ? "" : group;
        this.key = deriveKey(name);
    }

    public void addUrl(String url) {
        if (url == null || url.isEmpty()) return;
        if (seen.add(url)) { urls.add(url); uas.add(null); refs.add(null); }
    }

    public int urlCount() {
        return urls.size();
    }

    public String urlAt(int i) {
        return i >= 0 && i < urls.size() ? urls.get(i) : null;
    }

    /** 与该下标 URL 对应的 http-user-agent / http-referer（无则 null）。 */
    public void setMeta(int i, String ua, String referer) {
        if (i >= 0 && i < uas.size()) { uas.set(i, ua); refs.set(i, referer); }
    }

    public String uaAt(int i) {
        return i >= 0 && i < uas.size() ? uas.get(i) : null;
    }

    public String refererAt(int i) {
        return i >= 0 && i < refs.size() ? refs.get(i) : null;
    }

    /** 线路排序：m3u8 优先，php 跳转其次，其它（rtp/flv/udp）殿后；UA/Referer 跟随对应 URL。 */
    public void sortUrls() {
        List<String> hls = new ArrayList<>();
        List<String> php = new ArrayList<>();
        List<String> other = new ArrayList<>();
        List<String> hlsUa = new ArrayList<>();
        List<String> phpUa = new ArrayList<>();
        List<String> otherUa = new ArrayList<>();
        List<String> hlsRef = new ArrayList<>();
        List<String> phpRef = new ArrayList<>();
        List<String> otherRef = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String u = urls.get(i);
            String ua = uas.get(i);
            String rf = refs.get(i);
            if (u.contains(".m3u")) { hls.add(u); hlsUa.add(ua); hlsRef.add(rf); }
            else if (u.contains(".php")) { php.add(u); phpUa.add(ua); phpRef.add(rf); }
            else { other.add(u); otherUa.add(ua); otherRef.add(rf); }
        }
        urls.clear(); uas.clear(); refs.clear();
        urls.addAll(hls); urls.addAll(php); urls.addAll(other);
        uas.addAll(hlsUa); uas.addAll(phpUa); uas.addAll(otherUa);
        refs.addAll(hlsRef); refs.addAll(phpRef); refs.addAll(otherRef);
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
