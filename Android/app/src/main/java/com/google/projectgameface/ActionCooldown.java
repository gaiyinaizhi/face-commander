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

/** Pure cooldown gate for global face actions (ticket 04: ~800ms). */
public final class ActionCooldown {
  /** Default gap between successful action dispatches. */
  public static final long DURATION_MS = 800L;

  private ActionCooldown() {}

  /**
   * @param nowMs monotonic clock ms
   * @param lastDispatchMs last successful dispatch time, or &lt;=0 if never
   * @return true if a new action may be dispatched
   */
  public static boolean shouldDispatch(long nowMs, long lastDispatchMs) {
    if (lastDispatchMs <= 0L) {
      return true;
    }
    return nowMs - lastDispatchMs >= DURATION_MS;
  }
}
