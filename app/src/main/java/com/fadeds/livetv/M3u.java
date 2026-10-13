package com.fadeds.livetv;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class M3u {

    public static final String REMOTE_URL =
            "https://gh-proxy.org/raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u";

    /** 主分组：直接生成频道。 */
    private static final String[] PRIMARY = {"央视频道", "卫视频道", "4K频道"};
    /** 备分组：只给已有频道补充备用线路，不新增频道。 */
    private static final String[] BACKUP = {"央视高清", "咪咕央视"};

    // ---------- 拉取 ----------

    public static String fetchRemote() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)");
        try {
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    public static void saveCache(Context context, String text) {
        try {
            FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), "playlist.m3u"));
            out.write(text.getBytes("UTF-8"));
            out.close();
        } catch (Exception e) {
            android.util.Log.e("M3u", "save cache failed", e);
        }
    }

    // ---------- 解析 ----------

    public static List<Channel> parse(String text) {
        LinkedHashMap<String, Channel> byKey = new LinkedHashMap<>();
        List<String[]> pendingBackups = new ArrayList<>();

        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXTINF")) continue;

            String name = extractName(line);
            String group = attr(line, "group-title");
            if (name.isEmpty()) continue;

            boolean primary = inGroup(group, PRIMARY);
            boolean backup = inGroup(group, BACKUP);
            if (!primary && !backup) continue;

            String url = null;
            for (int j = i + 1; j < lines.length; j++) {
                String u = lines[j].trim();
                if (u.isEmpty()) continue;
                if (u.startsWith("#")) break;
                url = u;
                break;
            }
            if (url == null) continue;
            url = normalizeUrl(url);

            String mk = mergeKey(name);
            Channel exist = byKey.get(mk);
            if (exist != null) {
                exist.addUrl(url);
            } else if (primary) {
                Channel c = new Channel(byKey.size(), name, group);
                c.addUrl(url);
                byKey.put(mk, c);
            } else {
                pendingBackups.add(new String[]{mk, url});
            }
        }

        for (String[] pb : pendingBackups) {
            Channel c = byKey.get(pb[0]);
            if (c != null) c.addUrl(pb[1]);
        }

        List<Channel> out = new ArrayList<>(byKey.values());
        for (Channel c : out) c.sortUrls();
        return out;
    }

    /** 名字归一（仅用于同名合并，不能抹掉中文）。 */
    static String mergeKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-·_]", "");
    }

    /**
     * 从 #EXTINF 行取显示名：取最后一个不在引号内的逗号之后的内容。
     * 兼容 "#EXTINF:-1 tvg-..." 和 "#EXTINF:-1,tvg-..." 两种写法。
     */
    static String extractName(String extinf) {
        if (extinf.length() <= "#EXTINF:".length()) return "";
        String s = extinf.substring("#EXTINF:".length());
        boolean inQuote = false;
        int lastComma = -1;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inQuote = !inQuote;
            else if (ch == ',' && !inQuote) lastComma = i;
        }
        if (lastComma < 0) return "";
        String name = s.substring(lastComma + 1).trim();
        if (name.isEmpty()) name = attr(extinf, "tvg-name");
        return name == null ? "" : name.trim();
    }

    static String attr(String line, String key) {
        int i = line.indexOf(key + "=\"");
        if (i < 0) return null;
        int start = i + key.length() + 2;
        int end = line.indexOf('"', start);
        if (end < 0) return null;
        return line.substring(start, end);
    }

    /** URL 里裸中文参数/路径做百分号编码（如 live.php?id=河北卫视）。 */
    static String normalizeUrl(String url) {
        boolean ascii = true;
        for (int i = 0; i < url.length(); i++) {
            if (url.charAt(i) > 127) { ascii = false; break; }
        }
        if (ascii) return url;
        try {
            URL u = new URL(url);
            return new java.net.URI(
                    u.getProtocol(), u.getUserInfo(), u.getHost(), u.getPort(),
                    u.getPath(), u.getQuery(), null).toASCIIString();
        } catch (Exception e) {
            return url;
        }
    }

    private static boolean inGroup(String group, String[] list) {
        if (group == null) return false;
        for (String g : list) if (g.equals(group)) return true;
        return false;
    }

    static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}
