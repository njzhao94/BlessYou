package app.blessyou.sneeze;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Runs on the host JVM as well as through Gradle/JUnit. */
public final class CoreChecks {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < 0.000001f, message + ": " + actual);
    }
    public static void main(String[] args) {
        assertions = 0;
        RecognitionGate gate = new RecognitionGate();
        check(gate.accept(0, 0.8f, 0.1f, 0.35f, 5000), "First sneeze should trigger immediately");
        check(!gate.accept(240, 0.7f, 0.1f, 0.35f, 5000), "Overlapping window must not repeat");
        check(!gate.accept(6000, 0.8f, 0.1f, 0.35f, 5000), "Sustained classification must not repeat after cooldown");
        check(!gate.accept(6200, 0.01f, 0.1f, 0.35f, 5000), "Quiet window should rearm without triggering");
        check(gate.accept(6500, 0.8f, 0.1f, 0.35f, 5000), "A distinct later sneeze should trigger");
        check(!gate.accept(6800, 0.01f, 0.1f, 0.35f, 5000), "Quiet must not count as a sneeze");
        check(!gate.accept(7000, 0.8f, 0.1f, 0.35f, 5000), "A distinct sneeze within cooldown must be suppressed");
        check(gate.accept(11500, 0.8f, 0.1f, 0.35f, 5000), "Cooldown boundary should permit a new event");

        gate = new RecognitionGate();
        check(!gate.accept(0, 0.2f, 0.1f, 0.35f, 5000), "Low confidence must not trigger");
        check(!gate.accept(240, 0.7f, 0.8f, 0.35f, 5000), "A more likely cough/clap must not trigger");
        check(!gate.accept(480, 0.7f, 0.7f, 0.35f, 5000), "An ambiguous tie must not trigger");
        check(!gate.accept(720, Float.NaN, 0.1f, 0.35f, 5000), "NaN must not trigger");
        check(!gate.accept(960, 0.7f, Float.NaN, 0.35f, 5000), "NaN rival must not trigger");
        check(!gate.accept(1200, 1.1f, 0.1f, 0.35f, 5000), "Invalid score must not trigger");
        check(!gate.accept(1400, 0.8f, 0.1f, 0, 5000), "Invalid threshold must not trigger");
        check(gate.accept(1600, 0.35f, 0.1f, 0.35f, 5000), "Exact threshold should be accepted");

        AudioWindow window = new AudioWindow(4);
        ByteBuffer buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        window.push(new short[]{Short.MIN_VALUE, 0}, 2);
        check(!window.ready(), "Partial window must not be classified");
        boolean refusedPartial = false;
        try { window.copyTo(buffer); } catch (IllegalStateException expected) { refusedPartial = true; }
        check(refusedPartial, "Copying a partial window should be refused");
        window.push(new short[]{16384, Short.MAX_VALUE}, 2);
        check(window.ready(), "A full window should be ready");
        window.copyTo(buffer);
        near(-1, buffer.getFloat(), "PCM negative scale");
        near(0, buffer.getFloat(), "PCM zero");
        near(0.5f, buffer.getFloat(), "PCM positive scale");
        near(32767f / 32768f, buffer.getFloat(), "PCM positive peak");
        window.push(new short[]{8192, -8192}, 2);
        window.copyTo(buffer);
        near(0.5f, buffer.getFloat(), "Wrapped oldest sample");
        near(32767f / 32768f, buffer.getFloat(), "Wrapped second sample");
        near(0.25f, buffer.getFloat(), "Wrapped new sample");
        near(-0.25f, buffer.getFloat(), "Wrapped newest sample");
        window.clear();
        check(!window.ready(), "Clear must prevent stale playback audio from being classified");
        window.push(new short[]{1, 2, 3, 4, 5, 6}, 6);
        window.copyTo(buffer);
        near(3f / 32768f, buffer.getFloat(), "Oversized chunk should retain only the latest samples");
        near(4f / 32768f, buffer.getFloat(), "Oversized chunk order 2");
        near(5f / 32768f, buffer.getFloat(), "Oversized chunk order 3");
        near(6f / 32768f, buffer.getFloat(), "Oversized chunk order 4");
        System.out.println("PASS: " + assertions + " core behavior checks");
    }
}
