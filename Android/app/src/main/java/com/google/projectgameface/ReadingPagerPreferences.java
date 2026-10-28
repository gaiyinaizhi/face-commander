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

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Reading-pager product defaults (ticket 04): cursor and camera preview off unless the user
 * opts back into classic Gameface overlays. Ticket 08 adds an independent “user dismissed
 * floating assist” flag so Close hides the overlay without fighting the show-preview default.
 */
public final class ReadingPagerPreferences {
  public static final String PREFS_NAME = "GameFaceLocalConfig";
  public static final String KEY_SHOW_CURSOR = "pref_show_cursor";
  public static final String KEY_SHOW_CAMERA_PREVIEW = "pref_show_camera_preview";
  /** User closed the floating assist UI; stays hidden until Main reopens it. */
  public static final String KEY_FLOAT_ASSIST_DISMISSED = "pref_float_assist_dismissed";
  /** Broadcast action: service should re-apply overlay visibility from prefs. */
  public static final String ACTION_APPLY_UI = "APPLY_READING_PAGER_UI";

  private ReadingPagerPreferences() {}

  public static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  /** Default false: hide head-controlled cursor in reading-pager mode. */
  public static boolean isShowCursor(Context context) {
    return prefs(context).getBoolean(KEY_SHOW_CURSOR, false);
  }

  /** Default false: hide floating camera preview. */
  public static boolean isShowCameraPreview(Context context) {
    return prefs(context).getBoolean(KEY_SHOW_CAMERA_PREVIEW, false);
  }

  /** True after user taps Close on the floating assist until Main reopens it. */
  public static boolean isFloatAssistDismissed(Context context) {
    return prefs(context).getBoolean(KEY_FLOAT_ASSIST_DISMISSED, false);
  }

  /**
   * Whether the floating assist region (camera box / status icon) should be on screen.
   * Requires show-camera-preview opt-in and not user-dismissed.
   */
  public static boolean shouldShowFloatAssist(Context context) {
    return isShowCameraPreview(context) && !isFloatAssistDismissed(context);
  }

  public static void setShowCursor(Context context, boolean show) {
    prefs(context).edit().putBoolean(KEY_SHOW_CURSOR, show).apply();
  }

  public static void setShowCameraPreview(Context context, boolean show) {
    prefs(context).edit().putBoolean(KEY_SHOW_CAMERA_PREVIEW, show).apply();
  }

  public static void setFloatAssistDismissed(Context context, boolean dismissed) {
    prefs(context).edit().putBoolean(KEY_FLOAT_ASSIST_DISMISSED, dismissed).apply();
  }

  /** Clear dismiss and ensure camera-preview opt-in so Main can reopen the assist UI. */
  public static void reopenFloatAssist(Context context) {
    prefs(context)
        .edit()
        .putBoolean(KEY_FLOAT_ASSIST_DISMISSED, false)
        .putBoolean(KEY_SHOW_CAMERA_PREVIEW, true)
        .apply();
  }
}
