package app.blessyou.sneeze;

import java.nio.ByteBuffer;

/** A bounded in-memory PCM ring; samples never reach disk. */
public final class AudioWindow {
    private final float[] samples;
    private int next;
    private int count;

    public AudioWindow(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity");
        samples = new float[capacity];
    }
    public void push(short[] pcm, int length) {
        if (length < 0 || length > pcm.length) throw new IllegalArgumentException("length");
        for (int i = 0; i < length; i++) {
            samples[next] = pcm[i] / 32768.0f;
            next = (next + 1) % samples.length;
            count = Math.min(count + 1, samples.length);
        }
    }
    public boolean ready() { return count == samples.length; }
    public void clear() {
        java.util.Arrays.fill(samples, 0f);
        next = 0;
        count = 0;
    }
    public void copyTo(ByteBuffer target) {
        if (!ready()) throw new IllegalStateException("Audio window is not full");
        if (target.capacity() < samples.length * Float.BYTES) throw new IllegalArgumentException("buffer");
        target.clear();
        for (int i = 0; i < samples.length; i++) target.putFloat(samples[(next + i) % samples.length]);
        target.rewind();
    }
}
