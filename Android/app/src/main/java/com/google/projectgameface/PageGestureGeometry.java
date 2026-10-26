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
 * Fixed-screen gesture geometry for reading-pager actions.
 * Pure math so unit tests do not need AccessibilityService.
 */
public final class PageGestureGeometry {
  /** Default X ratio for bottom-right tap (ticket 02 / input.md). */
  public static final float TAP_BOTTOM_RIGHT_X_RATIO = 0.90f;
  /** Default Y ratio for bottom-right tap; slightly above true bottom to clear gesture bars. */
  public static final float TAP_BOTTOM_RIGHT_Y_RATIO = 0.88f;

  public static final long TAP_DURATION_MS = 250L;


  /** Start Y ratio for page swipe-up (from lower mid-screen). */
  public static final float SWIPE_UP_START_Y_RATIO = 0.70f;
  /** End Y ratio for page swipe-up (toward upper mid-screen). */
  public static final float SWIPE_UP_END_Y_RATIO = 0.30f;
  /** X ratio shared by start/end (screen center). */
  public static final float SWIPE_UP_X_RATIO = 0.50f;
  /** Duration for page swipe-up (input.md: ~200–300ms). */
  public static final int SWIPE_UP_DURATION_MS = 250;

  /**
   * @return float[4] as {startX, startY, endX, endY} in pixels
   */
  public static float[] swipeUpPage(int screenWidthPx, int screenHeightPx) {
    if (screenWidthPx <= 0 || screenHeightPx <= 0) {
      throw new IllegalArgumentException("screen size must be positive");
    }
    float x = screenWidthPx * SWIPE_UP_X_RATIO;
    float startY = screenHeightPx * SWIPE_UP_START_Y_RATIO;
    float endY = screenHeightPx * SWIPE_UP_END_Y_RATIO;
    return new float[] {x, startY, x, endY};
  }

  private PageGestureGeometry() {}

  /**
   * @param screenWidthPx physical screen width
   * @param screenHeightPx physical screen height
   * @return float[2] as {x, y} in pixels
   */
  public static float[] tapBottomRight(int screenWidthPx, int screenHeightPx) {
    if (screenWidthPx <= 0 || screenHeightPx <= 0) {
      throw new IllegalArgumentException("screen size must be positive");
    }
    return new float[] {
      screenWidthPx * TAP_BOTTOM_RIGHT_X_RATIO,
      screenHeightPx * TAP_BOTTOM_RIGHT_Y_RATIO
    };
  }
}
