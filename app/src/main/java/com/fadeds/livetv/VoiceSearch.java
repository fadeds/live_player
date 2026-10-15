package com.fadeds.livetv;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语音/文字频道路由：把识别到的自然语言映射到 48 个频道之一。
 * 优先级：完整频道名 > CCTV数字 > 频道别称 > 省份短名 > 特殊别名。
 */
public class VoiceSearch {

    private static final String[][] KV = {
            {"两套", "2"}, {"一套", "1"}, {"二套", "2"}, {"三套", "3"},
            {"四套", "4"}, {"五加", "5+"}, {"五套", "5"}, {"六套", "6"},
            {"七套", "7"}, {"八套", "8"}, {"九套", "9"}, {"十套", "10"},
            {"十一套", "11"}, {"十二套", "12"}, {"十三套", "13"},
            {"十四套", "14"}, {"十五套", "15"}, {"十六套", "16"}, {"十七套", "17"},
            {"一台", "1"}, {"二台", "2"}, {"三台", "3"}, {"四台", "4"},
            {"五台", "5"}, {"六台", "6"}, {"七台", "7"}, {"八台", "8"},
            {"九台", "9"}, {"十台", "10"}, {"十一台", "11"}, {"十二台", "12"},
            {"十三台", "13"}, {"十四台", "14"}, {"十五台", "15"}, {"十六台", "16"},
            {"十七台", "17"}
    };

    private static final String[][] CCTV_KEYWORDS = {
            {"体育赛事", "CCTV-5+ 体育赛事"}, {"中文国际", "CCTV-4 中文国际"},
            {"农业农村", "CCTV-17 农业农村"}, {"奥林匹克", "CCTV-16 奥林匹克"},
            {"国防军事", "CCTV-7 国防军事"}, {"社会与法", "CCTV-12 社会与法"},
            {"综合", "CCTV-1 综合"}, {"财经", "CCTV-2 财经"}, {"综艺", "CCTV-3 综艺"},
            {"体育", "CCTV-5 体育"}, {"电影", "CCTV-6 电影"}, {"电视剧", "CCTV-8 电视剧"},
            {"纪录", "CCTV-9 纪录"}, {"科教", "CCTV-10 科教"}, {"戏曲", "CCTV-11 戏曲"},
            {"新闻", "CCTV-13 新闻"}, {"少儿", "CCTV-14 少儿"}, {"音乐", "CCTV-15 音乐"}
    };

    private static final String[][] SPECIAL_ALIAS = {
            {"上海", "东方卫视"}, {"芒果", "湖南卫视"}, {"福建", "东南卫视"}
    };

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(java.util.Locale.ROOT).trim();
        s = s.replaceAll("[，。·、：:；;—\\-~～()（）\\[\\]【】?？!！\\s]+", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') {
                sb.append((char) ('0' + (c - '０')));
            } else {
                sb.append(c);
            }
        }
        s = sb.toString();
        for (int i = 0; i < KV.length; i++) {
            s = s.replace(KV[i][0], KV[i][1]);
        }
        return s;
    }

    public static int match(List<Channel> channels, String raw) {
        String q = normalize(raw);
        if (q.isEmpty()) return -1;

        // 1) 完整频道名（最长匹配优先）
        int best = -1, bestLen = 0;
        for (Channel c : channels) {
            String n = normalize(c.name);
            if (n.length() >= 2 && q.contains(n) && n.length() > bestLen) {
                bestLen = n.length();
                best = c.index;
            }
        }
        if (best >= 0) return best;

        // 2) cctv 数字：央视5+ / 央视13 / 14套 / cctv1 ...
        Matcher m = Pattern.compile("(\\d{1,2})(\\+)?").matcher(q.replaceAll("(?i)cctv|央视|中央", ""));
        int num = -1;
        boolean plus = false;
        if (m.find()) {
            num = Integer.parseInt(m.group(1));
            plus = m.group(2) != null;
        }
        if (num >= 1 && num <= 17) {
            if (plus && num == 5) {
                for (Channel c : channels) {
                    if (c.key != null && c.key.equals("cctv5plus")) return c.index;
                }
            }
            for (Channel c : channels) {
                if (c.key != null && c.key.equals("cctv" + num)) return c.index;
            }
        }

        // 3) CCTV 频道别称（按长度优先）
        for (int i = 0; i < CCTV_KEYWORDS.length; i++) {
            for (int j = i + 1; j < CCTV_KEYWORDS.length; j++) {
                if (CCTV_KEYWORDS[j][0].length() > CCTV_KEYWORDS[i][0].length()) {
                    String[] t = CCTV_KEYWORDS[i];
                    CCTV_KEYWORDS[i] = CCTV_KEYWORDS[j];
                    CCTV_KEYWORDS[j] = t;
                }
            }
        }
        for (int i = 0; i < CCTV_KEYWORDS.length; i++) {
            if (q.contains(CCTV_KEYWORDS[i][0])) {
                for (Channel c : channels) {
                    if (c.name.equals(CCTV_KEYWORDS[i][1])) return c.index;
                }
                return -1;
            }
        }

        // 4) 特殊别名（上海->东方卫视 等）
        for (int i = 0; i < SPECIAL_ALIAS.length; i++) {
            if (q.contains(SPECIAL_ALIAS[i][0])) {
                for (Channel c : channels) {
                    if (c.name.equals(SPECIAL_ALIAS[i][1])) return c.index;
                }
                return -1;
            }
        }

        // 5) 省份短名
        for (Channel c : channels) {
            if (c.name.endsWith("卫视")) {
                String shortName = c.name.replace("卫视", "");
                if (shortName.length() >= 2 && q.contains(shortName)) return c.index;
            }
        }

        return -1;
    }
}