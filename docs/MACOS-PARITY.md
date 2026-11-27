# macOS 功能同步与验证说明

## 基线与方法

- macOS：QYss777/YunX-macOS `b6456090b49953b161f95146d7dc9ead97df58e4`。
- Windows：tidain/YunX-Desktop `00a030f778a65840e9be65baecb15497a94b04ec`。
- 两仓库历史中的 Mac `24d96a1` 与 Windows `57c774c` 文件树完全相同。以此为三方合并基线同步后续代码，逐处保留 Mac 的品牌、数据目录、JCEF 登录、Finder、Cookie 导入、打包和更新源。
- 包内 Windows 上游代码及新增文件保留其原始版权和 AGPL-3.0 协议。本次修改不代表上游作者认可或发布。

## 对照表

| 范围 | 本次源码中的实现 |
| --- | --- |
| 视频解析 | 同步七个平台解析器、解析界面、结果页、清晰度、图集、账号登录 |
| 媒体下载 | 同步 DASH 合轨、HLS、图集、Live Photo、小红书原图及回退流程 |
| 在线预览 | 同步 JavaFX 播放器、HLS/MP4 代理预览会话与播放控制；Mac 自动模式使用内嵌 JavaFX |
| FFmpeg | 原生 Apple Silicon / Intel 选择；固定版本、SHA-256、Mach-O 验证、私有安装、更新/卸载；附相应许可证 |
| FFplay | 支持检测系统安装及独立窗口播放；由播放窗口处理键盘控制，不移植 Win32 嵌窗 |
| 浏览器扩展 | 同步 Chrome / Edge MV3 扩展、本地 HTTP 桥、下载确认、嗅探、接管、归还、视频入口 |
| 多账号 | 同步数据库增列迁移、主账号、备注、逐个删除、多账号取链及视频账号；认证备份沿用加密格式 |
| GitHub 镜像 | 同步自动测速、自动选择、固定/自定义镜像、设置页状态 |
| P2P | 同步 magnet / ed2k / 本地种子、Tracker、DHT、peer 概览；保留 Mac Gopeed 可执行文件与架构适配 |
| 下载器 | 同步单/多线程直写、任务分组、缓存维护及上游修复 |
| 下载进度 | Mac 使用 Dock 百分比/进度和菜单栏提示；完成通知使用托盘原生通知，受系统设置影响 |
| Mac 保活 | caffeinate 子进程绑定本应用 PID，任务结束释放 |
| 系统代理 | 新增 scutil --proxy 读取和 HTTP(S) 解析；PAC 仍不支持，明确返回状态 |
| 密钥保护 | Mac 普通档使用 IOPlatformUUID；保留旧 mk:v1 格式读取；Keychain 通过 Security.framework 存取，不把密钥放进进程参数 |
| 窗口位置 | 保留上游记忆/恢复；Mac 拖动选位使用 AWT 对话框 |
| 完成后关机 | 保留显式开关和可取消倒计时；Mac 调用系统关机请求，结果取决于系统权限，后台执行以免阻塞 UI |
| 构建 | Wrapper 8.13、JDK 17、按主机选择 JavaFX/JCEF 原生依赖、macOS 双架构 CI、DMG/PKG |

## 已知平台差异

1. Windows 的 `SetParent` 嵌入式 FFplay 不能在 Mac 使用。Mac 默认使用内嵌 JavaFX；独立 FFplay 需要自行安装（例如 Homebrew），并在播放窗口使用 `p` / `m` / `f` 等快捷键。
2. Mac FFmpeg 自动安装只提供 FFmpeg 转封装/合轨工具，**不包含 FFplay**。安装 FFmpeg 不会切换自动预览到独立窗口。
3. Windows 通知中心可原地更新 toast 进度；Mac 在 Dock 和菜单栏显示持续进度，完成时才发通知。这里采用平台对应能力，没有宣称通知中心进度条完全相同。
4. SQLCipher 整库加密仍未实现（Windows 上游也未实现）。前三档保护凭证列和主密钥，其余数据库元数据仍是明文。
5. FFmpeg 自动安装固定于 ffmpeg-static `b6.1.1`；「重新下载/更新」安装该受校验版本，不跟随未经审核的新版本自动变更。
6. Chrome/Safari 等应用对 Cookie 和第三方登录的限制可能变化；这里没有扩大 Mac 已有 Cookie 导入支持范围。浏览器助手面向 Chrome/Edge，未提供 Safari 扩展。

## 验证

构建与测试：

```bash
./gradlew :desktop:test
./gradlew :desktop:packageDmg :desktop:packagePkg
```

测试在独立的 `desktop/build/test-home/` 使用数据和偏好，避免读取/迁移开发者真实账号。
已有下载器测试包含本地 MockWebServer 的断点续传、直写落盘、Range 回退和文件内容校验；新增测试覆盖旧单账号迁移/主账号切换/重开、浏览器桥 Origin/token 校验、Mac 代理解析与原生引擎架构选择。

`MacCredentialCompatibilityTest` 在 Mac 执行，覆盖旧版密钥读取、普通档重包裹及钥匙串往返；Linux 跳过这两项。

此工作环境为 Linux。编译与 JVM 测试结果见交付的验证记录；**未在这里生成或运行 DMG，也未运行远程 GitHub Actions**。仓库 `.github/workflows/macos.yml` 准备了两种 Mac 架构的测试及打包任务。

仍需真实 Mac 验收：首次启动和 JCEF 登录、JavaFX 实际解码/全屏、FFplay 独立窗口、钥匙串授权/锁定状态、Dock/菜单栏/通知、防休眠、Finder、各架构 Gopeed、DMG 安装启动。
线上平台解析与下载还需有效分享/作品链接及相应账号。本次没有用户账号，不能据编译成功宣称这些平台已经线上验证全部可用。

建议先在单独测试数据目录体验，再导入原有认证备份；升级前保留数据库、credential.key 和加密认证备份。
