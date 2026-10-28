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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** Pure-logic tests for double-blink window + min open interval. */
public class DoubleBlinkGestureTest {
  private static final float THRESHOLD = 0.5f;

  private DoubleBlinkGesture detector;

  @Before
  public void setUp() {
    detector = new DoubleBlinkGesture();
  }

  /**
   * Simulate a blink rising edge then a sustained release (>= MIN_OPEN_INTERVAL_MS).
   */
  private boolean pulse(long riseMs) {
    assertFalse(detector.onScore(0.1f, THRESHOLD, riseMs - 1));
    boolean fired = detector.onScore(0.9f, THRESHOLD, riseMs);
    // Stay above briefly, then fall and remain below past the min open interval.
    detector.onScore(0.9f, THRESHOLD, riseMs + 20);
    detector.onScore(0.1f, THRESHOLD, riseMs + 40);
    detector.onScore(
        0.1f, THRESHOLD, riseMs + 40 + DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS);
    return fired;
  }

  @Test
  public void defaultWindowIs1000Ms_andMinOpenIs100Ms() {
    assertEquals(1000L, DoubleBlinkGesture.DEFAULT_WINDOW_MS);
    assertEquals(100L, DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS);
    assertEquals(2, DoubleBlinkGesture.REQUIRED_PULSES);
  }

  @Test
  public void twoPulsesInsideWindow_firesOnce() {
    assertFalse(pulse(1000L));
    assertTrue(pulse(1000L + 400L));
  }

  @Test
  public void twoPulsesAcrossWindow_doNotMerge() {
    assertFalse(pulse(1000L));
    // Outside 1000ms window: starts a new window, does not fire.
    assertFalse(pulse(1000L + 1001L));
    // Second of the new window fires.
    assertTrue(pulse(1000L + 1001L + 200L));
  }

  @Test
  public void intermediatePulseDoesNotFire() {
    assertFalse("first pulse must not emit", pulse(2000L));
  }

  @Test
  public void thirdPulseAfterSuccessStartsNewComposite() {
    assertFalse(pulse(3000L));
    assertTrue(pulse(3500L));
    assertFalse(pulse(4000L));
    assertTrue(pulse(4500L));
  }

  @Test
  public void jitterWithinSameBlink_doesNotCountAsSecondPulse() {
    long t0 = 6000L;
    // First rising edge.
    assertFalse(detector.onScore(0.9f, THRESHOLD, t0));
    // Brief dip below threshold (jitter), then rise again too soon.
    detector.onScore(0.1f, THRESHOLD, t0 + 30);
    assertFalse(
        "rise after only 20ms open must be ignored",
        detector.onScore(0.9f, THRESHOLD, t0 + 50));
    assertEquals(1, detector.getPulseCount());
    // Proper open interval, then second blink fires.
    detector.onScore(0.1f, THRESHOLD, t0 + 60);
    detector.onScore(0.1f, THRESHOLD, t0 + 60 + DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS);
    assertTrue(detector.onScore(0.9f, THRESHOLD, t0 + 60 + DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS + 1));
  }

  @Test
  public void openIntervalTooShort_doesNotCount() {
    long t0 = 7000L;
    assertFalse(detector.onScore(0.9f, THRESHOLD, t0));
    detector.onScore(0.1f, THRESHOLD, t0 + 40);
    // Rise at 40 + 50 = 90ms after fall — below MIN_OPEN_INTERVAL_MS (100).
    assertFalse(detector.onScore(0.9f, THRESHOLD, t0 + 90));
    assertEquals(1, detector.getPulseCount());
  }

  @Test
  public void twoLegalPulsesInsideWindow_fire() {
    long t0 = 8000L;
    assertFalse(detector.onScore(0.9f, THRESHOLD, t0));
    detector.onScore(0.1f, THRESHOLD, t0 + 50);
    long secondRise = t0 + 50 + DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS + 10;
    assertTrue(
        "second rising edge after sufficient open must fire",
        detector.onScore(0.9f, THRESHOLD, secondRise));
  }

  @Test
  public void cooldownOnlyAfterSuccessfulFire_semantics() {
    // Documented contract vs ActionCooldown: intermediate rising edges return false so the
    // dispatch layer never starts the 800ms cooldown. Only a completed composite returns true.
    long t0 = 5000L;
    assertFalse(detector.onScore(0.9f, THRESHOLD, t0)); // rise 1
    detector.onScore(0.1f, THRESHOLD, t0 + 40);
    detector.onScore(0.1f, THRESHOLD, t0 + 40 + DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS);
    // Still inside ActionCooldown.DURATION_MS (800) after first pulse — but we never fired.
    assertTrue(
        "intermediate pulse must not be treated as a dispatch",
        ActionCooldown.shouldDispatch(t0 + 100L, /* lastDispatchMs= */ 0L));
    long secondRise = t0 + 40 + DoubleBlinkGesture.MIN_OPEN_INTERVAL_MS + 10;
    assertTrue(detector.onScore(0.9f, THRESHOLD, secondRise)); // rise 2 completes
    long lastDispatch = secondRise;
    assertFalse(ActionCooldown.shouldDispatch(lastDispatch + 100L, lastDispatch));
    assertTrue(ActionCooldown.shouldDispatch(lastDispatch + ActionCooldown.DURATION_MS, lastDispatch));
  }
}
