package app.blessyou.sneeze;

/** Prevents several overlapping audio windows from blessing the same sneeze. */
public final class RecognitionGate {
    private boolean armed = true;
    private long lastTrigger = -1;

    public boolean accept(long nowMs, float sneeze, float competing, float threshold, long cooldownMs) {
        if (nowMs < 0 || cooldownMs < 0 || !Float.isFinite(sneeze)
                || !Float.isFinite(competing) || !Float.isFinite(threshold)
                || threshold <= 0 || threshold > 1 || sneeze < 0 || sneeze > 1
                || competing < 0 || competing > 1) return false;
        // A quiet/non-sneeze interval must separate distinct events.
        if (sneeze < threshold * 0.5f) armed = true;
        if (!armed || (lastTrigger >= 0 && nowMs - lastTrigger < cooldownMs)) return false;
        // Coughs, throat-clears, sniffs and claps must not be more likely.
        if (sneeze < threshold || sneeze <= competing) return false;
        lastTrigger = nowMs;
        armed = false;
        return true;
    }
}
