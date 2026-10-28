/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.projectgameface;

/**
 * Rising-edge counter for "double both-eyes blink" composite gesture (ticket 06).
 *
 * <p>Uses both-eyes blink score ({@code min(eyeBlinkLeft, eyeBlinkRight)}) so both eyelids must
 * close. Intermediate pulses within the window do <em>not</em> emit an event; only the second
 * pulse completes the composite. A minimum open (below-threshold) interval between rising edges
 * suppresses jitter from a single blink registering as two pulses. {@link ActionCooldown} must
 * only run after a successful fire (handled by the dispatch layer).
 */
public final class DoubleBlinkGesture {
  /** Default window for two blink rising edges to count as one composite. */
  public static final long DEFAULT_WINDOW_MS = 1000L;

  /**
   * Minimum continuous time the score must stay below threshold between two rising edges. Prevents
   * max/min score jitter within one blink from counting as a second pulse.
   */
  public static final long MIN_OPEN_INTERVAL_MS = 100L;

  public static final int REQUIRED_PULSES = 2;

  private final long windowMs;
  private final int requiredPulses;
  private final long minOpenIntervalMs;

  private int pulseCount;
  private long windowStartMs;
  private boolean wasAboveThreshold;
  /** Timestamp when score most recently fell below threshold; -1 if currently above or never. */
  private long lastFellBelowMs = -1L;

  public DoubleBlinkGesture() {
    this(DEFAULT_WINDOW_MS, REQUIRED_PULSES, MIN_OPEN_INTERVAL_MS);
  }

  public DoubleBlinkGesture(long windowMs, int requiredPulses) {
    this(windowMs, requiredPulses, MIN_OPEN_INTERVAL_MS);
  }

  public DoubleBlinkGesture(long windowMs, int requiredPulses, long minOpenIntervalMs) {
    this.windowMs = windowMs;
    this.requiredPulses = requiredPulses;
    this.minOpenIntervalMs = minOpenIntervalMs;
  }

  /**
   * Feed the current both-eyes blink score.
   *
   * @param score blendshape score in {@code [0, 1]}
   * @param threshold trigger threshold
   * @param nowMs monotonic clock ms
   * @return {@code true} only when the composite completes (fire once)
   */
  public boolean onScore(float score, float threshold, long nowMs) {
    if (pulseCount > 0 && nowMs - windowStartMs > windowMs) {
      pulseCount = 0;
      windowStartMs = 0L;
    }

    boolean above = score > threshold;
    if (!wasAboveThreshold && above) {
      // Rising edge: require a sustained open interval after the previous pulse.
      if (pulseCount > 0) {
        if (lastFellBelowMs < 0L || nowMs - lastFellBelowMs < minOpenIntervalMs) {
          // Jitter within the same blink — absorb, do not count.
          wasAboveThreshold = true;
          return false;
        }
      }
      wasAboveThreshold = true;
      lastFellBelowMs = -1L;
      if (pulseCount == 0) {
        pulseCount = 1;
        windowStartMs = nowMs;
        return false;
      }
      if (nowMs - windowStartMs <= windowMs) {
        pulseCount++;
        if (pulseCount >= requiredPulses) {
          pulseCount = 0;
          windowStartMs = 0L;
          return true;
        }
        return false;
      }
      // First pulse of a new window after expiry (already cleared above, but be safe).
      pulseCount = 1;
      windowStartMs = nowMs;
      return false;
    }

    if (wasAboveThreshold && !above) {
      wasAboveThreshold = false;
      lastFellBelowMs = nowMs;
    }
    return false;
  }

  public void reset() {
    pulseCount = 0;
    windowStartMs = 0L;
    wasAboveThreshold = false;
    lastFellBelowMs = -1L;
  }

  /** Visible for tests and sensitivity preview. */
  int getPulseCount() {
    return pulseCount;
  }
}
