package app.blessyou.sneeze;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(35, 52, 49);
    private static final int MUTED = Color.rgb(99, 114, 108);
    private static final int GREEN = Color.rgb(38, 114, 104);
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, detail, count, last, score, sensitivityLabel, cooldownLabel, voiceDetail;
    private Button toggle, test;
    private SharedPreferences preferences;
    private GreetingSpeaker speaker;
    private String voiceError = "";
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); main.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = Preferences.of(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 245, 239));
        LinearLayout content = column();
        content.setPadding(dp(24), dp(18), dp(24), dp(28));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            scroll.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();
        add(content, text("Bless You", 34, INK, true), 0);
        add(content, text("让每个喷嚏，都得到回应。", 15, MUTED, false), 5);

        LinearLayout card = card();
        add(content, card, 26);
        TextView star = text("✦", 58, GREEN, false);
        star.setGravity(Gravity.CENTER);
        add(card, star, 0);
        status = text("准备好送出祝福", 22, INK, true);
        status.setGravity(Gravity.CENTER);
        add(card, status, 8);
        detail = text("点开始，听见喷嚏后自动说 Bless you。", 14, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        add(card, detail, 10);
        score = text("", 12, MUTED, false);
        score.setGravity(Gravity.CENTER);
        add(card, score, 12);
        count = text("", 15, INK, true);
        count.setGravity(Gravity.CENTER);
        add(card, count, 10);
        last = text("", 12, MUTED, false);
        last.setGravity(Gravity.CENTER);
        add(card, last, 5);

        toggle = button("开始聆听", GREEN, Color.WHITE);
        add(content, toggle, 20);
        toggle.setOnClickListener(view -> {
            if (DetectionService.active) stopService(new Intent(this, DetectionService.class));
            else requestStart();
        });
        test = button("试听 Bless you", Color.WHITE, GREEN);
        add(content, test, 10);
        test.setOnClickListener(view -> {
            if (DetectionService.active) {
                startService(new Intent(this, DetectionService.class).setAction(DetectionService.TEST));
            } else if (speaker == null || !speaker.speak()) {
                Toast.makeText(this, voiceError.isEmpty() ? "英语语音正在准备，请稍等。" : voiceError, Toast.LENGTH_LONG).show();
            }
        });
        voiceDetail = text("正在准备英语语音…", 12, MUTED, false);
        add(content, voiceDetail, 8);

        LinearLayout settings = card();
        add(content, settings, 20);
        sensitivityLabel = text("", 16, INK, true);
        add(settings, sensitivityLabel, 0);
        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setContentDescription("喷嚏识别灵敏度，向右提高灵敏度");
        sensitivity.setMax(100);
        sensitivity.setProgress(preferences.getInt("sensitivity", 65));
        add(settings, sensitivity, 10);
        sensitivity.setOnSeekBarChangeListener(new Slider() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) preferences.edit().putInt("sensitivity", value).apply();
                updateLabels();
            }
        });
        add(settings, text("调低减少误报；漏掉喷嚏时可适当调高。", 12, MUTED, false), 0);
        cooldownLabel = text("", 16, INK, true);
        add(settings, cooldownLabel, 20);
        SeekBar cooldown = new SeekBar(this);
        cooldown.setContentDescription("两次祝福之间的最短间隔，2 至 10 秒");
        cooldown.setMax(8);
        cooldown.setProgress(preferences.getInt("cooldownSeconds", 5) - 2);
        add(settings, cooldown, 10);
        cooldown.setOnSeekBarChangeListener(new Slider() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) preferences.edit().putInt("cooldownSeconds", value + 2).apply();
                updateLabels();
            }
        });
        updateLabels();

        TextView privacy = text("声音在本机处理，不保存录音。\n开始聆听后可以锁屏，通知栏可随时停止。", 13, MUTED, false);
        privacy.setLineSpacing(dp(4), 1f);
        add(content, privacy, 20);
        Button help = button("使用帮助", Color.TRANSPARENT, GREEN);
        add(content, help, 10);
        help.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("使用帮助")
                .setMessage("1. 先试听，确认能听见 Bless you，媒体音量已打开。\n\n2. 点开始并允许麦克风权限。状态显示“正在聆听”后再测试。\n\n3. 手机放在附近，保持麦克风无遮挡。识别可能漏报或误报，可调节灵敏度。附近其他人的喷嚏也可能触发。\n\n4. 如果锁屏后停止：打开应用设置 → 电池，允许后台运行或选择不受限制；各版本菜单可能不同。\n\n5. 通话、其他录音应用、系统麦克风开关可能影响识别。手机重启或系统结束应用后，需要再次点开始。\n\n6. 如提示没有英语语音包，可在三星“设置 → 常规管理 → 文字转语音”中下载英语语音。\n\n实验版本 0.1 · 尚需在实际手机上验证。")
                .setPositiveButton("知道了", null)
                .setNeutralButton("应用设置", (dialog, which) -> appSettings()).show());
        speaker = new GreetingSpeaker(this, new GreetingSpeaker.Listener() {
            public void ready() { voiceError = ""; render(); }
            public void error(String message) { voiceError = message; render(); }
        });
        render();
    }

    private void updateLabels() {
        if (sensitivityLabel != null) sensitivityLabel.setText("灵敏度  " + preferences.getInt("sensitivity", 65));
        if (cooldownLabel != null) cooldownLabel.setText("祝福间隔  " + preferences.getInt("cooldownSeconds", 5) + " 秒");
    }
    private void requestStart() {
        ArrayList<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("askedNotifications", false)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
            preferences.edit().putBoolean("askedNotifications", true).apply();
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 10);
        else startListening();
    }
    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("需要麦克风权限")
                    .setMessage("允许麦克风访问后，应用才能识别喷嚏。")
                    .setPositiveButton("应用设置", (dialog, which) -> appSettings())
                    .setNegativeButton("取消", null).show();
            return;
        }
        try {
            startForegroundService(new Intent(this, DetectionService.class).setAction(DetectionService.START));
        } catch (RuntimeException problem) {
            DetectionService.error = "无法开始，请检查麦克风权限并重试。";
        }
        render();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10) startListening();
    }
    private void appSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
    private void render() {
        if (status == null) return;
        boolean active = DetectionService.active;
        boolean ready = DetectionService.modelReady && DetectionService.speechReady;
        status.setText(active ? ready ? "正在聆听" : "正在准备…" : DetectionService.error.isEmpty() ? "准备好送出祝福" : "需要处理一下");
        detail.setText(active ? ready ? "安心做你的事，听见喷嚏后我会回应。" : "正在准备声音识别和英语语音。"
                : DetectionService.error.isEmpty() ? "点开始，听见喷嚏后自动说 Bless you。" : DetectionService.error);
        score.setText(active && ready ? String.format(Locale.ROOT, "当前喷嚏识别分数：%.0f%%", DetectionService.latestScore * 100f) : "声音留在你的手机里");
        toggle.setText(active ? "停止聆听" : "开始聆听");
        count.setText("已送出 " + preferences.getInt("blessings", 0) + " 次祝福");
        long time = preferences.getLong("lastBlessing", 0);
        last.setText(time == 0 ? "等待第一声 Bless you" : "最近一次：" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time)));
        test.setEnabled(active ? DetectionService.speechReady : speaker != null && speaker.isReady());
        voiceDetail.setText(!voiceError.isEmpty() ? voiceError : speaker != null && speaker.isReady() ? "英语语音已就绪；播放音量使用手机媒体音量。" : "正在准备英语语音…");
    }
    @Override protected void onResume() { super.onResume(); main.post(refresh); }
    @Override protected void onPause() { main.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); if (speaker != null) speaker.close(); super.onDestroy(); }

    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout card() { LinearLayout layout = column(); layout.setPadding(dp(20), dp(20), dp(20), dp(20)); layout.setBackground(background(Color.WHITE, 24)); return layout; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return view;
    }
    private Button button(String title, int fill, int ink) {
        Button button = new Button(this); button.setText(title); button.setTextSize(16); button.setAllCaps(false);
        button.setTextColor(ink); button.setBackground(background(fill, 17)); button.setMinHeight(dp(56));
        button.setPadding(dp(12), dp(10), dp(12), dp(10)); return button;
    }
    private GradientDrawable background(int color, int radius) { GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(radius)); return result; }
    private void add(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin); parent.addView(view, params);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private abstract static class Slider implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar bar) { }
        public void onStopTrackingTouch(SeekBar bar) { }
    }
}
