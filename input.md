# FaceCommander — 阅读翻页器定制需求

> 来源：grill-with-docs 共识（2026-10-26，Asia/Shanghai）  
> 仓库：https://github.com/gaiyinaizhi/face-commander（fork of google/project-gameface）  
> 词表：见 `GLOSSARY.md`；关键决策：见 `docs/adr/0001-fixed-coordinate-tap.md`

## Problem Statement

用户希望在阅读类 App（微信读书、Kindle、浏览器长文等）前台时，用脸部动作完成翻页，而不是用手触控。上游 Project Gameface 已能「脸部 blendshape → 无障碍全局手势」，但默认是头控光标 + 光标处点击，缺少固定右下角点击、适合翻页的上滑几何，以及「阅读翻页器」产品形态。

## Solution

在 fork 上做少量 Android 定制，得到本地安装应用 **FaceCommander**：默认以**阅读翻页器**运行（冻结/隐藏光标、隐藏相机预览），将脸部动作绑定到**右下角点击**与**上滑翻页**等全局动作；保留光标模式与原版绑定能力作为可选。仅 Android sideload，不动 Windows，不上架商店。

## User Stories

1. As a 阅读用户, I want 用张嘴在屏幕右下角触发单击, so that 我可以在阅读 App 里点按翻页而不用手。
2. As a 阅读用户, I want 用挑右眉触发屏幕中部上滑, so that 我可以在需要滑动翻页的 App 里翻页。
3. As a 阅读用户, I want 默认不显示头控光标和相机预览小窗, so that 阅读内容不被挡。
4. As a 阅读用户, I want 仍可打开光标模式或相机预览, so that 我对脸或需要精细点击时有退路。
5. As a 阅读用户, I want 出厂默认已绑好张嘴→右下角点击、挑右眉→上滑, so that 开箱即用。
6. As a 阅读用户, I want 在绑定页改脸部动作与全局动作的对应, so that 我能换成更顺手的表情。
7. As a 阅读用户, I want 绑定页里翻页相关动作排在前面, so that 我先看到右下角点击和上滑翻页。
8. As a 阅读用户, I want 仍能绑定 Home/Back/通知等原版动作（排在后面）, so that 不失去 Gameface 原有能力。
9. As a 阅读用户, I want 两次翻页之间有约 800ms 冷却, so that 说话或表情残留不会连翻多页。
10. As a 阅读用户, I want 在系统无障碍设置里能认出「FaceCommander」, so that 不会和原版 Gameface 搞混。
11. As a 开发者, I want 本机可与原版 Gameface 并装, so that 对比行为方便。
12. As a 阅读用户, I want 仅本地安装使用, so that 不必走应用商店审核。
13. As a 阅读用户, I want 上滑从屏幕中部向上而不是从光标出发, so that 光标隐藏时滑动仍然有效。
14. As a 阅读用户, I want 右下角点击略避开底部手势条, so that 不会点到系统导航。
15. As a 阅读用户, I want 可暂停脸部控制, so that 临时说话或离开镜头时不会误触。

## Implementation Decisions

- 保持现有管线：Face Landmarker（blendshape）→ 事件映射 → `DispatchEventHelper` → `CursorAccessibilityService.dispatchGesture`。
- 新增全局动作「右下角点击」：按屏幕尺寸比例点击（默认约 `0.90W × 0.88H`），不依赖光标位置（见 ADR 0001）。
- 「上滑翻页」：从约 `(0.50W, 0.70H)` 滑到 `(0.50W, 0.30H)`，时长约 200–300ms；可复用/调整现有滑动派发，但几何不跟光标。
- 默认绑定：张嘴 → 右下角点击；挑右眉 → 上滑翻页。
- 阅读翻页器默认：光标冻结或隐藏；相机预览默认隐藏；设置中可恢复。
- 绑定 UI：翻页相关项置顶；原版 Home/Back/通知/全部应用等保留且排后。
- 全局动作冷却：默认约 800ms（保留既有边沿触发）。
- 应用身份：`applicationId` = `com.gaiyinaizhi.facecommander`，显示名 FaceCommander。
- 范围：仅 `Android/`；忽略 `Windows/`；不上架 Play。
- 工单：本地 markdown，路径 `.scratch/face-commander-pager/issues/`。
- 上游 archived：依赖升级不在本需求 MVP 内，除非编译/运行被堵。

## Testing Decisions

- 好的测试只验证可观察行为（给定脸部触发配置与屏幕尺寸，派发出的手势类型与坐标/轨迹符合约定），不绑死私有实现细节。
- 优先缝（seams，实现前与用户确认）：
  1. **手势构建缝**：输入「动作类型 + 屏幕尺寸」→ 输出点击坐标或滑动起止与时长（纯逻辑，可 JVM 单测）。
  2. **绑定/默认配置缝**：默认与持久化后的「脸部动作 → 全局动作」映射可读可写。
  3. **冷却缝**：在冷却窗内第二次触发不派发。
- 真机手工验收：无障碍开启后，在目标阅读 App 前台验证张嘴/挑眉翻页；不要求对 MediaPipe 做仪器化 UI 测试（成本高、不稳定）。
- 仓库若几乎无测试，允许在 `Android/` 增加最小单元测试模块或局部可测类；不引入与行为无关的大框架。

## Out of Scope

- Windows 工程与跨平台统一。
- 上架 Google Play / 国内应用商店。
- 按 App 包名分配置档、云同步配置。
- Root / 非无障碍注入方案。
- 前台服务保活、完整 OEM 适配（可作为后续）。
- MediaPipe / targetSdk 大升级（除非阻塞构建）。
- 重新设计完整游戏光标 UX（仅作可选模式保留）。

## Further Notes

- 术语一律以 `GLOSSARY.md` 为准。
- 右下角点击必须用固定坐标，禁止「先移光标再 CURSOR_TOUCH」作为阅读翻页器主路径（ADR 0001）。
- 拆票与实现顺序见 `.scratch/face-commander-pager/issues/`（经 to-tickets 确认后写入）。
