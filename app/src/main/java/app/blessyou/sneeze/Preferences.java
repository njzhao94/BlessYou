package app.blessyou.sneeze;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {
    static SharedPreferences of(Context context) {
        return context.getSharedPreferences("blessyou", Context.MODE_PRIVATE);
    }
    static float threshold(SharedPreferences preferences) {
        int level = Math.max(0, Math.min(100, preferences.getInt("sensitivity", 65)));
        return 0.80f - 0.70f * level / 100.0f;
    }
    static long cooldown(SharedPreferences preferences) {
        return Math.max(2, Math.min(10, preferences.getInt("cooldownSeconds", 5))) * 1000L;
    }
}
