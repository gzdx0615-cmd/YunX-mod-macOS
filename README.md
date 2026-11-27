<div align="center">
<img src="desktop/src/main/resources/icon.png" width="100" alt="YunX-OS" />

# YunX-OS · macOS 功能同步预览版

网盘、视频作品、GitHub、磁力 / BT / ed2k 下载工具。

</div>

基于 [QYss777/YunX-macOS](https://github.com/QYss777/YunX-macOS)，同步
[tidain/YunX-Desktop](https://github.com/tidain/YunX-Desktop) 的桌面功能。
原安卓项目为 [CYQawa/YunX](https://github.com/CYQawa/YunX)。保留原作者署名及 AGPL-3.0 协议。

本次同步基线：Mac `b6456090b49953b161f95146d7dc9ead97df58e4`；Windows `00a030f778a65840e9be65baecb15497a94b04ec`。
这是社区修改的 **Beta 源码**，不是原作者已发布或认证的安装包。

## 功能

- 网盘：夸克、UC、迅雷、百度、139、123、115、光鸭、蓝奏云、蓝奏云优享版。各平台受接口、登录和分享限制影响；识别链接不等于所有类型都能下载。
- 视频及作品解析：B站、抖音、快手、微博、小红书、AcFun、Pixiv。支持清晰度选择、图集、HLS、DASH 合轨、Live Photo 等上游流程；部分平台需要登录。
- Mac 默认内嵌 JavaFX 预览，支持播放、暂停、拖动、倍速、静音、全屏；HLS 使用上游的本地预览会话。FFmpeg 原生 ARM64 / Intel 引擎可在线安装到应用私有目录，用于合轨及 MP4 转封装。
- FFplay 可作为独立窗口预览（需另行安装，如 `brew install ffmpeg`），使用播放窗口自己的键盘控制。Mac 不使用 Win32 嵌窗与按键转发。
- Chrome / Edge 浏览器助手：资源嗅探、浏览器下载接管、右键下载、视频页快捷下载。扩展位于 [`browser-extension/`](browser-extension/README.md)。先启动客户端，再加载未打包扩展。
- 多账号：每个平台多个账号、主账号、备注、逐个删除；旧单账号表自动补列迁移。加密认证备份包含网盘和视频平台账号。
- GitHub：浏览仓库、账号和 Releases，README 渲染；镜像并发测速、自动选择、固定或自定义前缀。
- P2P：磁力 / ed2k、本地 `.torrent`、Tracker 列表、DHT 开关、连接信息；需要安装并启用 Gopeed 内核。
- 内置下载器：分片并发、断点续传、限速、校验、慢连接抢占；实验性单线程 / 多线程直写。
- Mac 集成：Finder 定位、原生文件选择、菜单栏托盘、下载期间防休眠、Dock 进度、完成通知、窗口位置选择与恢复。
- 安全存储：普通档使用机器指纹派生密钥，保留旧 Mac 密钥格式读取；较为严格档使用 macOS Keychain。整体数据库 SQLCipher 加密仍与上游一样未实现。

完整功能与平台差异见 [功能清单](docs/FEATURES.md) 和 [本次移植与验证说明](docs/MACOS-PARITY.md)。

## 构建

要求 macOS 12+、对应架构的 JDK 17。Apple Silicon 和 Intel 安装包须在相应架构上分别构建。

```bash
./gradlew :desktop:test
./gradlew :desktop:run
./gradlew :desktop:packageDmg :desktop:packagePkg
```

产物位于 `desktop/build/compose/binaries/main/dmg/`、`pkg/`。
首次运行 Wrapper 会下载 Gradle 8.13。未签名 / 未公证的应用需要通过 macOS 的正常安全设置允许打开。

仓库附带 [macOS 双架构 CI](.github/workflows/macos.yml)：运行测试后生成 DMG / PKG，并上传构建产物。该配置随源码提供，不代表远程构建已执行。

## 验证范围

Linux 可验证 Kotlin/JVM 编译、数据库迁移、下载器与本地桥接测试。
macOS 原生 GUI、JCEF、JavaFX 解码、钥匙串、Dock、菜单栏、Gopeed、安装包仍需真实 Mac 验证。
各平台线上解析与实际下载需要可用链接，部分需要本人登录账号；本次没有使用用户的账号。

## 开源协议

永久免费开源，保留 [GNU AGPL-3.0](LICENSE)。FFmpeg 私有安装器使用 `eugeneware/ffmpeg-static` 的固定版本 `b6.1.1`，校验固定 SHA-256，并保存相应架构的许可证。
