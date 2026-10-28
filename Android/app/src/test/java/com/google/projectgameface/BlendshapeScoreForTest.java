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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.projectgameface.BlendshapeEventTriggerConfig.Blendshape;
import java.util.List;
import org.junit.Test;

/** Composite / differential score + UI order invariants. */
public class BlendshapeScoreForTest {

  private static float[] shapes(int len) {
    return new float[len];
  }

  @Test
  public void uiOrder_keepsLegacy0to7_andNoneLastBeforeSpacer() {
    List<Blendshape> order = BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI;
    assertEquals(Blendshape.OPEN_MOUTH, order.get(0));
    assertEquals(Blendshape.MOUTH_LEFT, order.get(1));
    assertEquals(Blendshape.MOUTH_RIGHT, order.get(2));
    assertEquals(Blendshape.ROLL_LOWER_MOUTH, order.get(3));
    assertEquals(Blendshape.RAISE_RIGHT_EYEBROW, order.get(4));
    assertEquals(Blendshape.RAISE_LEFT_EYEBROW, order.get(5));
    assertEquals(Blendshape.LOWER_RIGHT_EYEBROW, order.get(6));
    assertEquals(Blendshape.LOWER_LEFT_EYEBROW, order.get(7));
    assertEquals(Blendshape.BROWS_INNER_UP, order.get(8));
    assertEquals(Blendshape.DOUBLE_BLINK, order.get(15));
    assertEquals(Blendshape.NONE, order.get(16));
    assertEquals(16, BlendshapeEventTriggerConfig.noneUiIndex());
    assertEquals(8, BlendshapeEventTriggerConfig.LEGACY_NONE_UI_INDEX);
  }

  @Test
  public void smile_subtractsMouthSide_clampedAtZero() {
    float[] b = shapes(52);
    b[44] = 0.2f;
    b[45] = 0.8f; // smileMax = 0.8
    b[39] = 0.1f;
    b[33] = 0.3f; // mouthSideMax = 0.3
    assertEquals(0.5f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.SMILE, b), 1e-5);
    // When mouth side dominates, smile score is 0.
    b[33] = 0.9f;
    assertEquals(0.f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.SMILE, b), 1e-5);
  }

  @Test
  public void mouthLeft_subtractsSmile_clampedAtZero() {
    float[] b = shapes(52);
    b[39] = 0.7f; // mouthLeft
    b[44] = 0.2f;
    b[45] = 0.1f; // smileMax = 0.2
    assertEquals(0.5f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.MOUTH_LEFT, b), 1e-5);
    b[44] = 0.8f;
    assertEquals(0.f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.MOUTH_LEFT, b), 1e-5);
  }

  @Test
  public void mouthRight_subtractsSmile_clampedAtZero() {
    float[] b = shapes(52);
    b[33] = 0.6f; // mouthRight
    b[44] = 0.1f;
    b[45] = 0.15f; // smileMax = 0.15
    assertEquals(0.45f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.MOUTH_RIGHT, b), 1e-5);
  }

  @Test
  public void eyeBlinkLeft_isDifferenceFromRight() {
    float[] b = shapes(52);
    b[9] = 0.9f;
    b[10] = 0.2f;
    assertEquals(0.7f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYE_BLINK_LEFT, b), 1e-5);
    // Both closed (normal blink) → left-only score ~0
    b[10] = 0.85f;
    assertEquals(0.05f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYE_BLINK_LEFT, b), 1e-5);
    b[10] = 0.95f;
    assertEquals(0.f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYE_BLINK_LEFT, b), 1e-5);
  }

  @Test
  public void eyeBlinkRight_isDifferenceFromLeft() {
    float[] b = shapes(52);
    b[9] = 0.1f;
    b[10] = 0.8f;
    assertEquals(0.7f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYE_BLINK_RIGHT, b), 1e-5);
  }

  @Test
  public void eyesWide_usesMaxOfLeftAndRight() {
    float[] b = shapes(52);
    b[21] = 0.3f;
    b[22] = 0.7f;
    assertEquals(0.7f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYES_WIDE, b), 1e-5);
  }

  @Test
  public void doubleBlink_usesMinBlink() {
    float[] b = shapes(52);
    b[9] = 0.4f;
    b[10] = 0.95f;
    assertEquals(0.4f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.DOUBLE_BLINK, b), 1e-5);
    b[9] = 0.9f;
    b[10] = 0.85f;
    assertEquals(0.85f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.DOUBLE_BLINK, b), 1e-5);
  }

  @Test
  public void atomic_readsMediaPipeIndex() {
    float[] b = shapes(52);
    b[3] = 0.55f;
    b[38] = 0.66f;
    b[6] = 0.77f;
    assertEquals(0.55f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.BROWS_INNER_UP, b), 1e-5);
    assertEquals(0.66f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.MOUTH_PUCKER, b), 1e-5);
    assertEquals(0.77f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.CHEEK_PUFF, b), 1e-5);
  }

  @Test
  public void openMouthLegacyIndexUnchanged() {
    float[] b = shapes(52);
    b[25] = 0.42f;
    assertEquals(0.42f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.OPEN_MOUTH, b), 1e-5);
    assertSame(Blendshape.OPEN_MOUTH, BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI.get(0));
  }

  @Test
  public void compositeFlags() {
    assertTrue(Blendshape.SMILE.isComposite());
    assertTrue(Blendshape.EYES_WIDE.isComposite());
    assertTrue(Blendshape.DOUBLE_BLINK.isComposite());
  }
}
