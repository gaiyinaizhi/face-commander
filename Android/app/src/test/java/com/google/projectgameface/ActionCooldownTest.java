package com.google.projectgameface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ActionCooldownTest {
  @Test
  public void durationIsEightHundredMs() {
    assertEquals(800L, ActionCooldown.DURATION_MS);
  }

  @Test
  public void firstActionAlwaysAllowed() {
    assertTrue(ActionCooldown.shouldDispatch(1000L, 0L));
    assertTrue(ActionCooldown.shouldDispatch(1000L, -1L));
  }

  @Test
  public void blocksInsideWindow() {
    assertFalse(ActionCooldown.shouldDispatch(1500L, 1000L));
    assertFalse(ActionCooldown.shouldDispatch(1799L, 1000L));
  }

  @Test
  public void allowsAtOrAfterWindow() {
    assertTrue(ActionCooldown.shouldDispatch(1800L, 1000L));
    assertTrue(ActionCooldown.shouldDispatch(2000L, 1000L));
  }
}
