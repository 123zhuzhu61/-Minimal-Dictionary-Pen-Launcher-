# DictPenLauncher

> 为安卓词典笔打造的第三方桌面启动器（Launcher）。
> 极简、横屏、可定制 —— 让词典笔的每一寸屏幕都用在刀刃上。

- **版本**：3.0
- **最低系统**：Android 5.0（API 21），已在 Android 5 上测试成功运行
- **目标 / 编译**：Android 16（API 36）
- **包名**：`com.example.dictpenlauncher`
- **开源**：本项目以开源方式发布，欢迎学习、二次开发与贡献（许可证见文末）。

---

## ✨ 功能特性

- **开机即桌面**：开机自启并设为默认 HOME，开机完成（`RECEIVE_BOOT_COMPLETED`）后自动进入。
- **拼音 A–Z 分组**：应用列表按拼音 / 字母自动分组排序，数字与符号归为「#」，一秒定位应用。
- **控制中心**：全屏任意位置**向右滑动**呼出，集成
  - WiFi 开关、蓝牙开关
  - 屏幕亮度滑块、媒体音量滑块
  - 当前时间 / 日期 / 电量显示
  - 支持 12/24 小时制、是否显示秒的设置
- **编辑模式**：在「全部应用」界面**长按应用**进入编辑模式，可
  - 将应用**添加到主屏**
  - **卸载**应用
- **全屏信息展示**：独立全屏信息页，可展示自定义信息内容。
- **自由添加**：支持添加**网页快捷方式**与**应用快捷方式**，把常用的钉在主屏。
- **布局持久化**：主屏应用列表以 JSON 形式保存，重启后仍保留自定义布局。
- **自动刷新**：监听系统应用安装 / 卸载广播，列表自动更新。
- **横屏优先**：专为词典笔横屏设计，UI 以 4 列网格布局呈现，大字号、留白、护眼。

---

## 📱 使用方式

1. 安装后，在系统「默认应用 / 主屏幕应用」设置中选择 **DictPenLauncher** 作为默认桌面（或在开机/按下 HOME 时选择它并设为默认）。
2. **查找应用**：应用列表已按拼音 / 字母分组，可直接滑动浏览或点字母标题跳转。
3. **呼出控制中心**：在桌面任意位置**向右滑动**，即可调节 WiFi、蓝牙、亮度与音量。
4. **整理桌面**：进入「全部应用」界面，**长按应用**进入编辑模式，添加应用或卸载。
5. **添加快捷方式**：在应用抽屉中可添加网页快捷方式与自定义应用，一键钉到主屏。

> 以上说明亦可在应用内「关于 DictPenLauncher」中查看。

---

## 🔧 系统要求与权限

| 项目 | 说明 |
| --- | --- |
| 最低 Android 版本 | 5.0（API 21），已实测通过 |
| 屏幕方向 | 横屏（landscape） |
| 所需权限 | `RECEIVE_BOOT_COMPLETED`（开机自启）、`QUERY_ALL_PACKAGES`（读取已装应用）、WiFi / 蓝牙相关权限（控制中心开关）、`WRITE_SETTINGS` / `WAKE_LOCK`（亮度与唤醒）、`REQUEST_DELETE_PACKAGES`（卸载）、前台服务等 |

> 应用启动器本身**不需要联网**，网页快捷方式由系统浏览器打开。

---

## 🛠 构建与安装

### 从源码构建

项目使用 Gradle（已内置 Gradle Wrapper），无需单独安装 Gradle。

```bash
# 克隆或解压本项目后，在项目根目录执行：

# Linux / macOS
./gradlew assembleRelease

# Windows
gradlew.bat assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/release/app-release-unsigned.apk`。
如需可安装的签名包，请在 `app/build.gradle.kts` 中配置签名，或使用 Android Studio 打开本项目后「Build → Generate Signed Bundle / APK」。

### 直接安装

若已获得预构建的发布包（如 `DictPenLauncher-v3.0.apk`），将其拷贝到设备并安装即可，首次启动按提示设为默认桌面。

---

## 📂 项目结构

```
DictPenLauncher2/
├── app/
│   └── src/main/java/com/example/dictpenlauncher/
│       ├── MainActivity.java        # 主界面：主屏 + 应用抽屉 + 手势
│       ├── AppAdapter.java          # 应用/快捷方式列表适配器（含字母分组头）
│       ├── EditAppAdapter.java      # 编辑模式适配器（添加主屏 / 卸载）
│       ├── ControlCenterManager.java# 控制中心（右滑呼出）
│       ├── PinyinUtils.java         # 拼音转换与排序（pinyin4j）
│       └── BootReceiver.java        # 开机自启广播接收器
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat            # Gradle Wrapper
```

主要布局 / 资源位于 `app/src/main/res/`：
- `layout/`：主界面、控制中心、各类弹窗、列表项
- `drawable` / `drawable-v24/`：应用图标矢量定义（`ic_launcher_background` 蓝色底 + `ic_launcher_foreground` 词典笔图形）
- `values/`：字符串、颜色、主题

---

## 🤝 开源与贡献

欢迎提交 Issue 与 Pull Request。建议贡献前先阅读本项目代码风格与既有实现，确保改动与「极简、横屏、为词典笔而生」的设计目标一致。

### 许可证

本项目以开源协议发布。请在仓库中添加你选用的许可证文件（如 `MIT` 或 `Apache-2.0`）后再行分发。

---

*DictPenLauncher —— 让每一次使用，都更专注。*
