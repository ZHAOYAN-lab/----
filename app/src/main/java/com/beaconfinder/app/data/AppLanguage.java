package com.beaconfinder.app.data;

import android.content.Context;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translate UI literals only; product, warehouse and beacon names stay as entered. */
public final class AppLanguage {
    private static final Map<String, String> zh = new HashMap<>(), ja = new HashMap<>();
    private static Pattern zhPattern, jaPattern;
    private static volatile String language = "zh";
    public static synchronized Locale locale(Context context) {
        language = context.getSharedPreferences("ui_language", Context.MODE_PRIVATE).getString("language", "zh");
        if (zh.isEmpty()) {
            try (java.io.InputStream input = context.getAssets().open("ui-translations.json")) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONArray pairs = new JSONArray(output.toString("UTF-8"));
                for (int i = 0; i < pairs.length(); i++) {
                    JSONArray pair = pairs.getJSONArray(i); zh.put(pair.getString(1), pair.getString(0)); ja.put(pair.getString(0), pair.getString(1));
                }
                zhPattern = pattern(zh); jaPattern = pattern(ja);
            } catch (Exception e) { throw new IllegalStateException("UI language resources", e); }
        }
        return language.equals("ja") ? Locale.JAPAN : Locale.SIMPLIFIED_CHINESE;
    }
    private static Pattern pattern(Map<String, String> dictionary) {
        java.util.List<String> keys = new java.util.ArrayList<>(dictionary.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder expression = new StringBuilder();
        for (String key : keys) { if (expression.length() > 0) expression.append('|'); expression.append(Pattern.quote(key)); }
        return Pattern.compile(expression.toString());
    }
    public static String text(String value) {
        if (value == null) return "";
        value = value.replace("\\n", "\n");
        Map<String, String> dictionary = language.equals("ja") ? ja : zh;
        if (dictionary.containsKey(value)) return dictionary.get(value);
        Pattern pattern = language.equals("ja") ? jaPattern : zhPattern;
        if (pattern == null) return value;
        Matcher matcher = pattern.matcher(value); StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement(dictionary.get(matcher.group())));
        matcher.appendTail(output); return output.toString();
    }
    public static void toggle(Context context) {
        String current = locale(context).getLanguage();
        context.getSharedPreferences("ui_language", Context.MODE_PRIVATE).edit().putString("language", current.equals("ja") ? "zh" : "ja").commit();
        locale(context);
    }
}
