package app.blessyou.sneeze;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DetectionService extends Service {
    static final String START = "app.blessyou.START";
    static final String STOP = "app.blessyou.STOP";
    static final String TEST = "app.blessyou.TEST";
    static volatile boolean active;
    static volatile boolean speechReady;
    static volatile boolean modelReady;
    static volatile String error = "";
    static volatile float latestScore;
    private static final String CHANNEL = "listening";
    private static final int NOTIFICATION = 1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean responsePending = new AtomicBoolean();
    private volatile long muteUntil;
    private volatile AudioRecord recorder;
    private Thread worker;
    private GreetingSpeaker speaker;
    private PowerManager.WakeLock wakeLock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (TEST.equals(action)) {
            if (active && speaker != null && speaker.isReady()) {
                muteUntil = SystemClock.elapsedRealtime() + 2500;
                speaker.speak();
            } else if (!active) stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(action) || active) { if (!active) stopSelf(); return START_NOT_STICKY; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("需要允许麦克风权限，才能识别喷嚏。");
            return START_NOT_STICKY;
        }
        try {
            error = "";
            NotificationChannel channel = new NotificationChannel(CHANNEL, "聆听状态", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示喷嚏识别状态，并提供随时停止的按钮。");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION, notification("正在准备声音识别…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else startForeground(NOTIFICATION, notification("正在准备声音识别…"));
            active = true;
            running.set(true);
            speechReady = false;
            modelReady = false;
            PowerManager power = getSystemService(PowerManager.class);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BlessYou:Listening");
            // This lock lasts only for the user-started session and is released in onDestroy.
            wakeLock.acquire();
            speaker = new GreetingSpeaker(this, new GreetingSpeaker.Listener() {
                public void ready() { speechReady = true; updateNotification(); }
                public void error(String message) { fail(message); }
            });
            worker = new Thread(this::capture, "blessyou-audio");
            worker.start();
        } catch (RuntimeException problem) {
            fail("无法开始聆听，请确认麦克风权限后，在应用界面点开始。");
        }
        // Do not restart microphone capture from the background after OS termination.
        return START_NOT_STICKY;
    }

    @android.annotation.SuppressLint("MissingPermission") // Checked before this user-started worker is launched.
    private void capture() {
        Interpreter interpreter = null;
        AudioRecord localRecorder = null;
        AudioWindow window = null;
        short[] hop = new short[3840];
        try {
            ByteBuffer model;
            try (AssetFileDescriptor asset = getAssets().openFd("yamnet.tflite");
                 FileInputStream stream = new FileInputStream(asset.getFileDescriptor())) {
                model = stream.getChannel().map(FileChannel.MapMode.READ_ONLY, asset.getStartOffset(), asset.getDeclaredLength());
            }
            interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(2).setUseXNNPACK(true));
            interpreter.allocateTensors();
            if (interpreter.getInputTensorCount() != 1 || interpreter.getInputTensor(0).dataType() != DataType.FLOAT32)
                throw new IllegalStateException("Unsupported model input");
            int sampleCount = interpreter.getInputTensor(0).numElements();
            if (sampleCount < 10000 || sampleCount > 64000) throw new IllegalStateException("Unexpected audio length");
            int scoresIndex = -1;
            for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
                int[] shape = interpreter.getOutputTensor(i).shape();
                if (shape.length > 0 && shape[shape.length - 1] == 521
                        && interpreter.getOutputTensor(i).dataType() == DataType.FLOAT32) { scoresIndex = i; break; }
            }
            if (scoresIndex < 0) throw new IllegalStateException("Missing YAMNet scores");
            ByteBuffer input = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder());
            ByteBuffer output = ByteBuffer.allocateDirect(interpreter.getOutputTensor(scoresIndex).numBytes()).order(ByteOrder.nativeOrder());
            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(scoresIndex, output);
            window = new AudioWindow(sampleCount);
            RecognitionGate gate = new RecognitionGate();
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Microphone format unavailable");
            if (!running.get()) return;
            localRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, sampleCount * 4));
            recorder = localRecorder;
            if (localRecorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Microphone unavailable");
            localRecorder.startRecording();
            if (localRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Microphone not recording");
            modelReady = true;
            main.post(this::updateNotification);
            while (running.get()) {
                int length = localRecorder.read(hop, 0, hop.length, AudioRecord.READ_BLOCKING);
                if (!running.get()) break;
                if (length <= 0) throw new IllegalStateException("Microphone read failed");
                if (SystemClock.elapsedRealtime() < muteUntil) { window.clear(); continue; }
                window.push(hop, length);
                if (!window.ready()) continue;
                window.copyTo(input);
                output.clear();
                interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);
                output.rewind();
                float sneeze = 0, rival = 0;
                int frames = output.capacity() / (4 * 521);
                for (int frame = 0; frame < frames; frame++) {
                    float frameSneeze = output.getFloat((frame * 521 + 44) * 4);
                    float frameRival = 0;
                    for (int index : new int[]{42, 43, 45, 57, 58})
                        frameRival = Math.max(frameRival, output.getFloat((frame * 521 + index) * 4));
                    if (frameSneeze > sneeze) { sneeze = frameSneeze; rival = frameRival; }
                }
                latestScore = sneeze;
                if (speechReady && !responsePending.get() && gate.accept(SystemClock.elapsedRealtime(), sneeze, rival,
                        Preferences.threshold(Preferences.of(this)), Preferences.cooldown(Preferences.of(this)))) {
                    responsePending.set(true);
                    muteUntil = SystemClock.elapsedRealtime() + 2500;
                    window.clear();
                    main.post(() -> {
                        try {
                            if (running.get() && speaker != null && speaker.speak()) {
                                android.content.SharedPreferences preferences = Preferences.of(this);
                                preferences.edit().putInt("blessings", preferences.getInt("blessings", 0) + 1)
                                        .putLong("lastBlessing", System.currentTimeMillis()).apply();
                            }
                        } finally { responsePending.set(false); }
                    });
                }
            }
        } catch (Exception | LinkageError problem) {
            if (running.get()) main.post(() -> fail("声音识别未能运行，请关闭通话或录音应用后重试。若仍失败，请保留错误信息："
                    + problem.getClass().getSimpleName() + ": " + problem.getMessage()));
        } finally {
            recorder = null;
            if (localRecorder != null) {
                try { localRecorder.stop(); } catch (RuntimeException ignored) { }
                localRecorder.release();
            }
            if (interpreter != null) interpreter.close();
            if (window != null) window.clear();
            java.util.Arrays.fill(hop, (short) 0);
        }
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, DetectionService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_listening)
                .setContentTitle("Bless You 正在聆听").setContentText(text)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "停止聆听", stop).build()).build();
    }
    private void updateNotification() {
        if (active) getSystemService(NotificationManager.class).notify(NOTIFICATION,
                notification(modelReady && speechReady ? "检测到喷嚏后会说 Bless you，可锁屏使用。" : "正在准备声音识别和祝福音频…"));
    }
    private void fail(String message) {
        error = message;
        running.set(false);
        stopSelf();
    }
    @Override public void onDestroy() {
        running.set(false);
        active = false;
        modelReady = false;
        speechReady = false;
        latestScore = 0;
        AudioRecord current = recorder;
        if (current != null) { try { current.stop(); } catch (RuntimeException ignored) { } }
        if (worker != null) worker.interrupt();
        if (speaker != null) speaker.close();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
