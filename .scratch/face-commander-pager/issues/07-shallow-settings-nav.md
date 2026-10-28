# 07: 配置页浅导航（返回只 pop）

**What to build:** 手势配置流栈保持 `Main → CursorBinding → ChooseGesture → GestureSize`；返回 / Done 只 pop 或 `CLEAR_TOP|SINGLE_TOP` 到已有父页，禁止 `FLAG_ACTIVITY_CLEAR_TASK`，避免多次操作来回跳转。

**Blocked by:** None

**Status:** done

- [x] `ChooseGestureActivity` back / NONE Done：`finish()` 回已有 `CursorBinding`（不再 `startActivity` 父页）
- [x] `ChooseGestureActivity` Next→GestureSize：普通 `startActivity`，保留 ChooseGesture 在栈上（去掉 CLEAR_TASK/CLEAR_TOP）
- [x] `GestureSizeActivity` Done：写 prefs + Toast + restore service 后，`CursorBinding` + `CLEAR_TOP|SINGLE_TOP`（无 CLEAR_TASK）再 `finish()`
- [x] `GestureSizeActivity` back / ActionBar home：保持 `finish()`
- [x] `CursorBinding` onBack / ActionBar home：仅 `finish()`，不再 `startActivity(MainActivity)`
- [x] 检查 `CursorSpeed` / Tutorial / GrantPermission：无同类 CLEAR_TASK；Tutorial/Grant 进 Main 的线性流可保留
- [x] Manifest `parentActivityName` 保留；无 Navigation Component 重构
