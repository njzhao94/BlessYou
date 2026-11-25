package app.blessyou.sneeze;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

final class GreetingSpeaker implements AutoCloseable {
    interface Listener {
        void ready();
        void error(String message);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private TextToSpeech tts;
    private boolean ready;
    private boolean closed;
    private boolean focusHeld;
    private String activeUtterance;
    private final Listener listener;

    GreetingSpeaker(Context context, Listener listener) {
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS && tts != null) {
                        tts.stop();
                        activeUtterance = null;
                        releaseFocus();
                    }
                }, main).build();
        tts = new TextToSpeech(context.getApplicationContext(), result -> main.post(() -> {
            if (closed) return;
            if (result != TextToSpeech.SUCCESS) { listener.error("无法启动语音，请检查手机的文字转语音设置。"); return; }
            int language = tts.setLanguage(Locale.UK);
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                language = tts.setLanguage(Locale.US);
            }
            if (language < 0) { listener.error("请先在手机的文字转语音设置中安装英语语音包。"); return; }
            Set<Voice> voices = tts.getVoices();
            Voice offline = voices == null ? null : voices.stream()
                    .filter(v -> "en".equals(v.getLocale().getLanguage()) && !v.isNetworkConnectionRequired())
                    .min(Comparator.comparingInt(v -> "GB".equals(v.getLocale().getCountry()) ? 0
                            : "US".equals(v.getLocale().getCountry()) ? 1 : 2)).orElse(null);
            if (offline == null || tts.setVoice(offline) == TextToSpeech.ERROR) {
                listener.error("请先安装可离线使用的英语语音包，再重新打开应用。"); return;
            }
            tts.setAudioAttributes(attributes);
            tts.setSpeechRate(0.92f);
            tts.setPitch(1.02f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(() -> finished(id)); }
                @Override public void onError(String id) { main.post(() -> {
                    if (id.equals(activeUtterance)) {
                        finished(id);
                        if (!closed) listener.error("语音播放失败，请检查英语语音包和媒体音量。");
                    }
                }); }
                @Override public void onStop(String id, boolean interrupted) { main.post(() -> finished(id)); }
            });
            ready = true;
            listener.ready();
        }));
    }
    boolean isReady() { return ready && !closed; }
    boolean speak() {
        if (!isReady()) return false;
        try {
            focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!focusHeld) { listener.error("暂时无法播放，请在通话或其他音频结束后重试。"); return false; }
            Bundle parameters = new Bundle();
            parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            activeUtterance = "bless-" + System.nanoTime();
            int result = tts.speak("Bless you!", TextToSpeech.QUEUE_FLUSH, parameters, activeUtterance);
            if (result == TextToSpeech.ERROR) { activeUtterance = null; releaseFocus(); return false; }
            return true;
        } catch (RuntimeException error) {
            releaseFocus();
            listener.error("暂时无法播放语音，请重试。");
            return false;
        }
    }
    private void finished(String id) {
        if (id.equals(activeUtterance)) { activeUtterance = null; releaseFocus(); }
    }
    private void releaseFocus() {
        if (focusHeld) { audio.abandonAudioFocusRequest(focus); focusHeld = false; }
    }
    @Override public void close() {
        closed = true;
        ready = false;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        releaseFocus();
    }
}
