package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class PrayerTimes {
    private static final String ZONE = "JHR02";
    private static final String TAG_VIEW = "prayer_line";
    private static volatile boolean fetching = false;
    private static volatile long lastTry = 0;

    private PrayerTimes() {}

    static void attach(final Activity a) {
        try {
            View left = a.findViewById(R.id.leftBlock);
            if (!(left instanceof LinearLayout)) return;
            LinearLayout box = (LinearLayout) left;
            TextView found = (TextView) box.findViewWithTag(TAG_VIEW);
            if (found == null) {
                found = new TextView(a);
                found.setTag(TAG_VIEW);
                found.setTextColor(0xCCFFFFFF);
                found.setTextSize(15);
                found.setSingleLine(true);
                found.setEllipsize(TextUtils.TruncateAt.END);
                found.setGravity(Gravity.LEFT);
                found.setIncludeFontPadding(false);
                try { found.setTypeface(a.getResources().getFont(R.font.outfit)); } catch (Throwable ignored) {}
                try { found.setFontVariationSettings("'wght' 400"); } catch (Throwable ignored) {}
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = (int) (6 * a.getResources().getDisplayMetrics().density);
                box.addView(found, lp);
            }
            final TextView view = found;
            final SharedPreferences sp = a.getSharedPreferences("prayer", Activity.MODE_PRIVATE);
            String line = sp.getString("line", "");
            if (!line.isEmpty()) view.setText(line);
            final String today = today();
            if (today.equals(sp.getString("day", "")) || fetching) return;
            long now = System.currentTimeMillis();
            if (now - lastTry < 10 * 60 * 1000L) return;
            lastTry = now;
            fetching = true;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        final String text = fetch();
                        sp.edit().putString("day", today).putString("line", text).apply();
                        view.post(new Runnable() {
                            public void run() { view.setText(text); }
                        });
                    } catch (Throwable ignored) {
                    } finally {
                        fetching = false;
                    }
                }
            }).start();
        } catch (Throwable ignored) {
        }
    }

    private static String today() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return f.format(new Date());
    }

    private static String fetch() throws Exception {
        URL url = new URL("https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=today&zone=" + ZONE);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            JSONObject d = new JSONObject(sb.toString()).getJSONArray("prayerTime").getJSONObject(0);
            String dot = "   \u00B7   ";
            return "Subuh " + t(d.getString("fajr")) + dot
                    + "Zohor " + t(d.getString("dhuhr")) + dot
                    + "Asar " + t(d.getString("asr")) + dot
                    + "Maghrib " + t(d.getString("maghrib")) + dot
                    + "Isyak " + t(d.getString("isha"));
        } finally {
            c.disconnect();
        }
    }

    private static String t(String s) {
        String[] p = s.split(":");
        int h = Integer.parseInt(p[0]) % 12;
        if (h == 0) h = 12;
        return h + ":" + p[1];
    }
}
