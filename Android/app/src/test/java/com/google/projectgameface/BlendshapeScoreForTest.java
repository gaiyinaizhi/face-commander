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

/** Composite score + UI order invariants (ticket 05). */
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
  public void smile_usesMaxOfLeftAndRight() {
    float[] b = shapes(52);
    b[44] = 0.2f;
    b[45] = 0.8f;
    assertEquals(0.8f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.SMILE, b), 1e-5);
    b[44] = 0.9f;
    b[45] = 0.1f;
    assertEquals(0.9f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.SMILE, b), 1e-5);
  }

  @Test
  public void eyesWide_usesMaxOfLeftAndRight() {
    float[] b = shapes(52);
    b[21] = 0.3f;
    b[22] = 0.7f;
    assertEquals(0.7f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYES_WIDE, b), 1e-5);
  }

  @Test
  public void doubleBlink_previewScore_usesMaxBlink() {
    float[] b = shapes(52);
    b[9] = 0.4f;
    b[10] = 0.95f;
    assertEquals(0.95f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.DOUBLE_BLINK, b), 1e-5);
  }

  @Test
  public void atomic_readsMediaPipeIndex() {
    float[] b = shapes(52);
    b[3] = 0.55f;
    b[38] = 0.66f;
    b[6] = 0.77f;
    b[9] = 0.11f;
    b[10] = 0.22f;
    assertEquals(0.55f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.BROWS_INNER_UP, b), 1e-5);
    assertEquals(0.66f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.MOUTH_PUCKER, b), 1e-5);
    assertEquals(0.77f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.CHEEK_PUFF, b), 1e-5);
    assertEquals(0.11f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYE_BLINK_LEFT, b), 1e-5);
    assertEquals(0.22f, BlendshapeEventTriggerConfig.scoreFor(Blendshape.EYE_BLINK_RIGHT, b), 1e-5);
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
