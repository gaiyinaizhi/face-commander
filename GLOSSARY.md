# FaceCommander

本地 Android 应用：用脸部动作触发系统级翻页手势，基于 Google Project Gameface 的 fork 定制。

## Language

**阅读翻页器**:
默认产品形态：不依赖头控光标，用离散脸部动作触发全局点击或滑动，以便在其他阅读类 App 前台翻页。
_Avoid_: 游戏鼠标模式, 连续光标控制（作可选能力时另称「光标模式」）

**光标模式**:
可选形态：保留原版头部位移控制屏幕光标，再用表情触发点击等动作。

**脸部动作**:
由 MediaPipe Face Landmarker blendshape 经阈值边沿检测得到的一次离散触发（如张嘴、挑眉）。
_Avoid_: 手势（易与触控手势混淆）, 表情事件

**全局动作**:
经无障碍服务 `dispatchGesture` 注入、可作用在任意前台 App 上的点击或滑动。
_Avoid_: 无障碍操作（过宽）, 系统注入

**右下角点击**:
在屏幕固定比例坐标（默认约宽 90%、高 90%）处注入的单击，用于模拟阅读 App 的「点按翻页」。
_Avoid_: 光标处点击, CURSOR_TOUCH

**上滑翻页**:
从屏幕中部附近向上注入的滑动，用于模拟阅读 App 的「上滑翻页」。
_Avoid_: 从光标起滑, 原版 SWIPE_UP（未改几何前）

**本地安装**:
仅通过 Android Studio / APK sideload 使用，不上架应用商店。
_Avoid_: 发布, 上架

**默认绑定**:
出厂预设：张嘴触发右下角点击；挑右眉触发上滑翻页。用户可在绑定页更改。

**通用竖屏阅读**:
首期默认手势几何所针对的场景类别（微信读书、Kindle、浏览器长文等），不做按 App 分档。

**动作冷却**:
两次全局动作之间的最短间隔（默认约 800ms），用于降低连续误触。

**应用身份**:
本地包名与显示名：`com.gaiyinaizhi.facecommander` / FaceCommander，以便与原版 Project Gameface 并装。
