# 05: 易做面部动作接入配置页

**What to build:** 在现有 MediaPipe 52 维已输出的前提下，把「比单侧挑眉更好做」的动作加入手势选择页，可绑到任意 EventType（含翻页）。

**Blocked by:** none（依赖已合并的中文/品牌）

**Status:** done

新增可绑定动作（原子上升沿，与现有张嘴等同层）：

- [x] 双眉上扬 — MediaPipe `browInnerUp`（index 3）
- [x] 嘟嘴 — `mouthPucker`（index 38）
- [x] 微笑 — `max(mouthSmileLeft, mouthSmileRight)`（44/45），合成分数
- [x] 鼓腮 — `cheekPuff`（index 6）
- [x] 眨左眼 — `eyeBlinkLeft`（9）
- [x] 眨右眼 — `eyeBlinkRight`（10）
- [x] 睁大眼 — `max(eyeWideLeft, eyeWideRight)`（21/22），合成分数

实现约束：

- [x] `Blendshape` 枚举 + `BLENDSHAPE_FROM_ORDER_IN_UI`：**追加在 NONE 之前**，保证旧下标 0–7 不变；迁移：旧 prefs 中 index==8（旧 NONE）改写为新 NONE 下标
- [x] `values` / `values-zh-rCN` 文案；choose_gesture 布局增加条目（可用简洁 vector，风格对齐现有）
- [x] `getBeautifyBlendshapeName` / 灵敏度预览对合成项用同一分数源
- [x] 单测：合成分数、UI 顺序、NONE 下标迁移
