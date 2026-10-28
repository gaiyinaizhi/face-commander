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

/** Pure-logic tests for ticket 06 double-blink window. */
public class DoubleBlinkGestureTest {
  private static final float THRESHOLD = 0.5f;

  private DoubleBlinkGesture detector;

  @Before
  public void setUp() {
    detector = new DoubleBlinkGesture();
  }

  /** Simulate a blink rising edge then release. */
  private boolean pulse(long riseMs) {
    assertFalse(detector.onScore(0.1f, THRESHOLD, riseMs - 1));
    boolean fired = detector.onScore(0.9f, THRESHOLD, riseMs);
    detector.onScore(0.1f, THRESHOLD, riseMs + 50);
    return fired;
  }

  @Test
  public void defaultWindowIs700Ms() {
    assertEquals(700L, DoubleBlinkGesture.DEFAULT_WINDOW_MS);
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
    // Outside 700ms window: starts a new window, does not fire.
    assertFalse(pulse(1000L + 701L));
    // Second of the new window fires.
    assertTrue(pulse(1000L + 701L + 200L));
  }

  @Test
  public void intermediatePulseDoesNotFire() {
    assertFalse("first pulse must not emit", pulse(2000L));
  }

  @Test
  public void thirdPulseAfterSuccessStartsNewComposite() {
    assertFalse(pulse(3000L));
    assertTrue(pulse(3300L));
    assertFalse(pulse(3600L));
    assertTrue(pulse(3900L));
  }

  @Test
  public void cooldownOnlyAfterSuccessfulFire_semantics() {
    // Documented contract vs ActionCooldown: intermediate rising edges return false so the
    // dispatch layer never starts the 800ms cooldown. Only a completed composite returns true.
    long t0 = 5000L;
    assertFalse(detector.onScore(0.9f, THRESHOLD, t0)); // rise 1
    detector.onScore(0.1f, THRESHOLD, t0 + 40);
    // Still inside ActionCooldown.DURATION_MS (800) after first pulse — but we never fired.
    assertTrue(
        "intermediate pulse must not be treated as a dispatch",
        ActionCooldown.shouldDispatch(t0 + 100L, /* lastDispatchMs= */ 0L));
    assertTrue(detector.onScore(0.9f, THRESHOLD, t0 + 300L)); // rise 2 completes
    long lastDispatch = t0 + 300L;
    assertFalse(ActionCooldown.shouldDispatch(lastDispatch + 100L, lastDispatch));
    assertTrue(ActionCooldown.shouldDispatch(lastDispatch + ActionCooldown.DURATION_MS, lastDispatch));
  }
}
