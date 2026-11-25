package app.blessyou.sneeze;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/** Plays bundled speech without a system TTS engine or an installed voice pack. */
final class GreetingSpeaker implements AutoCloseable {
    interface Listener {
        void ready();
        void error(String message);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final Listener listener;
    private MediaPlayer player;
    private boolean ready;
    private boolean closed;
    private boolean focusHeld;
    private String status = "正在加载内置祝福音频…";

    GreetingSpeaker(Context context, Listener listener) {
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (closed || player == null) return;
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (ready && player.isPlaying()) {
                            player.pause();
                            player.seekTo(0);
                        }
                        status = "播放已暂停，可再次点试听。";
                        releaseFocus();
                    }
                }, main).build();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setVolume(1f, 1f);
            player.setOnPreparedListener(prepared -> {
                if (closed) return;
                ready = true;
                status = "内置祝福音频已就绪";
                listener.ready();
            });
            player.setOnCompletionListener(completed -> {
                status = "祝福音频已播放";
                releaseFocus();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                ready = false;
                releaseFocus();
                status = "音频播放失败（" + what + "/" + extra + "），请重新打开应用。";
                if (!closed) listener.error(status);
                return true;
            });
            try (AssetFileDescriptor asset = context.getResources().openRawResourceFd(R.raw.bless_you)) {
                player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
            }
            player.prepareAsync();
        } catch (Exception problem) {
            status = "无法加载内置祝福音频，请重新打开应用。";
            main.post(() -> { if (!closed) listener.error(status); });
        }
    }
    boolean isReady() { return ready && !closed; }
    String getStatus() { return status; }
    int mediaVolume() { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
    int mediaMaximum() { return audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); }

    boolean speak() {
        if (!isReady()) {
            if (!closed) listener.error(status);
            return false;
        }
        if (mediaVolume() == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            status = "媒体音量为 0，请按音量键调高“媒体”音量后再试听。";
            listener.error(status);
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
            return false;
        }
        try {
            focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!focusHeld) {
                status = "暂时无法播放，请在通话或其他音频结束后重试。";
                listener.error(status);
                return false;
            }
            player.seekTo(0);
            player.start();
            status = "正在播放 Bless you…";
            return true;
        } catch (RuntimeException problem) {
            releaseFocus();
            status = "暂时无法播放音频，请重新打开应用。";
            listener.error(status);
            return false;
        }
    }
    private void releaseFocus() {
        if (focusHeld) { audio.abandonAudioFocusRequest(focus); focusHeld = false; }
    }
    @Override public void close() {
        closed = true;
        ready = false;
        if (player != null) {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.release();
            player = null;
        }
        releaseFocus();
    }
}
