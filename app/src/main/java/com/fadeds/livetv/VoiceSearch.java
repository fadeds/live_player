package com.fadeds.livetv;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 璇煶/鏂囧瓧棰戦亾璺敱锛氭妸璇嗗埆鍒扮殑鑷劧璇█鏄犲皠鍒?48 涓閬撲箣涓€銆?
 * 浼樺厛绾э細瀹屾暣棰戦亾鍚?> CCTV鏁板瓧 > 棰戦亾鍒О > 鐪佷唤鐭悕 > 鐗规畩鍒悕銆?
 */
public class VoiceSearch {

    private static final String[][] KV = {
            {"涓ゅ", "2"}, {"涓€濂?, "1"}, {"浜屽", "2"}, {"涓夊", "3"},
            {"鍥涘", "4"}, {"浜斿姞", "5+"}, {"浜斿", "5"}, {"鍏", "6"},
            {"涓冨", "7"}, {"鍏", "8"}, {"涔濆", "9"}, {"鍗佸", "10"},
            {"鍗佷竴濂?, "11"}, {"鍗佷簩濂?, "12"}, {"鍗佷笁濂?, "13"},
            {"鍗佸洓濂?, "14"}, {"鍗佷簲濂?, "15"}, {"鍗佸叚濂?, "16"}, {"鍗佷竷濂?, "17"},
            {"涓€鍙?, "1"}, {"浜屽彴", "2"}, {"涓夊彴", "3"}, {"鍥涘彴", "4"},
            {"浜斿彴", "5"}, {"鍏彴", "6"}, {"涓冨彴", "7"}, {"鍏彴", "8"},
            {"涔濆彴", "9"}, {"鍗佸彴", "10"}, {"鍗佷竴鍙?, "11"}, {"鍗佷簩鍙?, "12"},
            {"鍗佷笁鍙?, "13"}, {"鍗佸洓鍙?, "14"}, {"鍗佷簲鍙?, "15"}, {"鍗佸叚鍙?, "16"},
            {"鍗佷竷鍙?, "17"}
    };

    private static final String[][] CCTV_KEYWORDS = {
            {"浣撹偛璧涗簨", "CCTV-5+ 浣撹偛璧涗簨"}, {"涓枃鍥介檯", "CCTV-4 涓枃鍥介檯"},
            {"鍐滀笟鍐滄潙", "CCTV-17 鍐滀笟鍐滄潙"}, {"濂ユ灄鍖瑰厠", "CCTV-16 濂ユ灄鍖瑰厠"},
            {"鍥介槻鍐涗簨", "CCTV-7 鍥介槻鍐涗簨"}, {"绀句細涓庢硶", "CCTV-12 绀句細涓庢硶"},
            {"缁煎悎", "CCTV-1 缁煎悎"}, {"璐㈢粡", "CCTV-2 璐㈢粡"}, {"缁艰壓", "CCTV-3 缁艰壓"},
            {"浣撹偛", "CCTV-5 浣撹偛"}, {"鐢靛奖", "CCTV-6 鐢靛奖"}, {"鐢佃鍓?, "CCTV-8 鐢佃鍓?},
            {"绾綍", "CCTV-9 绾綍"}, {"绉戞暀", "CCTV-10 绉戞暀"}, {"鎴忔洸", "CCTV-11 鎴忔洸"},
            {"鏂伴椈", "CCTV-13 鏂伴椈"}, {"灏戝効", "CCTV-14 灏戝効"}, {"闊充箰", "CCTV-15 闊充箰"}
    };

    private static final String[][] SPECIAL_ALIAS = {
            {"涓婃捣", "涓滄柟鍗"}, {"鑺掓灉", "婀栧崡鍗"}, {"绂忓缓", "涓滃崡鍗"}
    };

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(java.util.Locale.ROOT).trim();
        s = s.replaceAll("[锛屻€偮枫€侊細:锛?鈥擻\-~锝?)锛堬級\\[\\]銆愩€?锛?锛乗\s]+", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '锛? && c <= '锛?) {
                sb.append((char) ('0' + (c - '锛?)));
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

        // 1) 瀹屾暣棰戦亾鍚嶏紙鏈€闀垮尮閰嶄紭鍏堬級
        int best = -1, bestLen = 0;
        for (Channel c : channels) {
            String n = normalize(c.name);
            if (n.length() >= 2 && q.contains(n) && n.length() > bestLen) {
                bestLen = n.length();
                best = c.index;
            }
        }
        if (best >= 0) return best;

        // 2) cctv 鏁板瓧锛氬ぎ瑙?+ / 澶13 / 14濂?/ cctv1 ...
        Matcher m = Pattern.compile("(\\d{1,2})(\\+)?").matcher(q.replaceAll("(?i)cctv|澶|涓ぎ", ""));
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

        // 3) CCTV 棰戦亾鍒О锛堟寜闀垮害浼樺厛锛?
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

        // 4) 鐗规畩鍒悕锛堜笂娴?>涓滄柟鍗 绛夛級
        for (int i = 0; i < SPECIAL_ALIAS.length; i++) {
            if (q.contains(SPECIAL_ALIAS[i][0])) {
                for (Channel c : channels) {
                    if (c.name.equals(SPECIAL_ALIAS[i][1])) return c.index;
                }
                return -1;
            }
        }

        // 5) 鐪佷唤鐭悕
        for (Channel c : channels) {
            if (c.name.endsWith("鍗")) {
                String shortName = c.name.replace("鍗", "");
                if (shortName.length() >= 2 && q.contains(shortName)) return c.index;
            }
        }

        return -1;
    }
}