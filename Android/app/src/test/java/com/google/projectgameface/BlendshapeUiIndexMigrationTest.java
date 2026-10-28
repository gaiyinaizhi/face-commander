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
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BlendshapeUiIndexMigrationTest {
  private SharedPreferences preferences;

  @Before
  public void setUp() {
    preferences =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences("GameFaceLocalConfig", Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
  }

  @Test
  public void legacyNoneIndex8_migratesToNewNone_andPreservesRealGestures() {
    SharedPreferences.Editor editor = preferences.edit();
    // Old NONE
    editor.putInt(BlendshapeEventTriggerConfig.EventType.CURSOR_TOUCH.name(), 8);
    editor.putInt(BlendshapeEventTriggerConfig.EventType.CURSOR_TOUCH.name() + "_size", 50);
    // Real gesture OPEN_MOUTH (0) must stay
    editor.putInt(BlendshapeEventTriggerConfig.EventType.SWIPE_UP_PAGE.name(), 0);
    editor.putInt(BlendshapeEventTriggerConfig.EventType.SWIPE_UP_PAGE.name() + "_size", 40);
    // RAISE_LEFT_EYEBROW was UI index 5
    editor.putInt(BlendshapeEventTriggerConfig.EventType.HOME.name(), 5);
    editor.putInt(BlendshapeEventTriggerConfig.EventType.HOME.name() + "_size", 60);
    editor.commit();

    BlendshapeEventTriggerConfig.migrateBlendshapeUiIndicesIfNeeded(preferences);

    assertTrue(preferences.getBoolean(BlendshapeEventTriggerConfig.PREFS_KEY_BLENDSHAPE_UI_ORDER_V2, false));
    assertEquals(
        BlendshapeEventTriggerConfig.noneUiIndex(),
        preferences.getInt(BlendshapeEventTriggerConfig.EventType.CURSOR_TOUCH.name(), -1));
    assertEquals(
        0, preferences.getInt(BlendshapeEventTriggerConfig.EventType.SWIPE_UP_PAGE.name(), -1));
    assertEquals(5, preferences.getInt(BlendshapeEventTriggerConfig.EventType.HOME.name(), -1));

    assertEquals(
        BlendshapeEventTriggerConfig.Blendshape.NONE,
        BlendshapeEventTriggerConfig.blendshapeFromPrefs(
            preferences, BlendshapeEventTriggerConfig.EventType.CURSOR_TOUCH.name()));
    assertEquals(
        BlendshapeEventTriggerConfig.Blendshape.OPEN_MOUTH,
        BlendshapeEventTriggerConfig.blendshapeFromPrefs(
            preferences, BlendshapeEventTriggerConfig.EventType.SWIPE_UP_PAGE.name()));
    assertEquals(
        BlendshapeEventTriggerConfig.Blendshape.RAISE_LEFT_EYEBROW,
        BlendshapeEventTriggerConfig.blendshapeFromPrefs(
            preferences, BlendshapeEventTriggerConfig.EventType.HOME.name()));
  }

  @Test
  public void migrationRunsOnlyOnce_soNewBrowsInnerUpAt8IsKept() {
    // After migration flag set, index 8 means BROWS_INNER_UP, not NONE.
    preferences
        .edit()
        .putBoolean(BlendshapeEventTriggerConfig.PREFS_KEY_BLENDSHAPE_UI_ORDER_V2, true)
        .putInt(BlendshapeEventTriggerConfig.EventType.TAP_BOTTOM_RIGHT.name(), 8)
        .putInt(BlendshapeEventTriggerConfig.EventType.TAP_BOTTOM_RIGHT.name() + "_size", 30)
        .commit();

    assertEquals(
        BlendshapeEventTriggerConfig.Blendshape.BROWS_INNER_UP,
        BlendshapeEventTriggerConfig.blendshapeFromPrefs(
            preferences, BlendshapeEventTriggerConfig.EventType.TAP_BOTTOM_RIGHT.name()));
  }
}
