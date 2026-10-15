package com.fadeds.livetv;

public class Channel {
    public final int index;
    public final String name;
    public final String key;
    public final String pid;
    public volatile String program = "";

    public Channel(int index, String name, String key, String pid) {
        this.index = index;
        this.name = name == null ? "" : name;
        this.key = key == null ? "" : key;
        this.pid = pid == null ? "" : pid;
    }

    public String displayNumber() {
        int n = index + 1;
        return (n < 10 ? "0" : "") + n;
    }

    public boolean isCctv() {
        return key != null && key.startsWith("cctv");
    }

    /** CCTV 频道在央视网的备用直播页。 */
    public String fallbackUrl() {
        if (isCctv()) {
            return "https://tv.cctv.com/live/" + key + "/index.shtml";
        }
        return null;
    }
}