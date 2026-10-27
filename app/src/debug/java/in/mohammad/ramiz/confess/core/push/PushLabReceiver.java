package in.mohammad.ramiz.confess.core.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

/**
 * DEBUG BUILDS ONLY — the push lab. Posts a Confess template notification exactly as a real push
 * would (same PushService.show path), without the server or Gemini:
 *
 *   adb shell am broadcast -a in.mohammad.ramiz.confess.PUSH_LAB -p in.mohammad.ramiz.confess \
 *       --es template whisper                       # spark · whisper · teaser · digest · winback · streak
 *       [--es art sleepy] [--es title "…"] [--es body "…"] [--es cta "…"]
 *
 * scripts/push_lab.sh fires each template, screenshots the pop-up and dumps its view bounds.
 */
public class PushLabReceiver extends BroadcastReceiver {

    private static final Map<String, String[]> SAMPLES = new HashMap<>();

    static {
        // template → {type, art, title, body, cta}
        SAMPLES.put("spark", new String[]{"nudge", "chai", "Chai ready hai? Confessions bhi",
                "Subah ki pehli chuski ke saath padho kya log chhupa rahe the.", "Dekho"});
        SAMPLES.put("whisper", new String[]{"nudge", "sleepy", "Neend nahi aa rahi? 🌙",
                "2 AM thoughts ko yahan chhod do. No names, ever.", "Bol de"});
        SAMPLES.put("teaser", new String[]{"new_post", "peek", "Yeh padhna toh banta hai",
                "“I still keep his hoodie in my cupboard and wear it when nobody's home…”", "Read now"});
        SAMPLES.put("digest", new String[]{"digest", "peek", "5 log tumhara wait kar rahe",
                "Reactions, replies aur ek naya follower. Dekho kaun hai.", "See who"});
        SAMPLES.put("streak", new String[]{"streak", "peek", "🔥 7 din ka streak khatre mein!",
                "Midnight se pehle ek tap, warna zero se shuru.", "Bachao streak"});
        SAMPLES.put("winback", new String[]{"winback", "hug", "midnight_owl, kahan gaye?",
                "Ek hafta ho gaya. Letty ne tumhari seat bacha ke rakhi hai.", "Wapas aao"});
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String template = intent.getStringExtra("template");
        if (template == null || !SAMPLES.containsKey(template)) template = "whisper";
        String[] s = SAMPLES.get(template);
        Map<String, String> data = new HashMap<>();
        data.put("type", s[0]);
        data.put("template", template);
        data.put("art", pick(intent, "art", s[1]));
        data.put("cta", pick(intent, "cta", s[4]));
        data.put("count", "streak".equals(template) ? "7" : "5");
        data.put("tag", "lab:" + template);         // each template its own notification
        PushService.show(context, data, pick(intent, "title", s[2]), pick(intent, "body", s[3]));
    }

    private static String pick(Intent i, String key, String fallback) {
        String v = i.getStringExtra(key);
        return v == null || v.isEmpty() ? fallback : v;
    }
}
