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
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class PageGestureGeometryTest {

  @Test
  public void tapBottomRight_usesConfiguredRatios() {
    float[] point = PageGestureGeometry.tapBottomRight(1080, 2400);
    assertEquals(1080 * 0.90f, point[0], 0.01f);
    assertEquals(2400 * 0.88f, point[1], 0.01f);
  }

  @Test
  public void tapBottomRight_rejectsNonPositiveSize() {
    assertThrows(IllegalArgumentException.class, () -> PageGestureGeometry.tapBottomRight(0, 100));
    assertThrows(IllegalArgumentException.class, () -> PageGestureGeometry.tapBottomRight(100, -1));
  }
}
