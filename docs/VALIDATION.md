# 本地验证记录

日期：2026-11-27。执行环境：Linux x86_64，Temurin JDK 17，Gradle 8.13。

- `./gradlew :desktop:test :desktop:assemble`：通过。
- 94 项测试：92 通过，0 失败，2 跳过。跳过的两项是仅在 Mac 执行的钥匙串往返与旧 Mac 密钥兼容测试。
- 真实 JDBC 测试：旧单账号表增列、旧明文凭证加密迁移、多账号新增、主账号切换、备注、删除主账号后回退及数据库重开。
- 本地 HTTP 桥测试：扩展发现客户端、普通网站 Origin 拒绝、无令牌下载请求拒绝。
- 下载器测试：本地 MockWebServer 驱动，覆盖直写、分片、断点恢复、Range 不支持时回退和结果字节一致性。
- Mac 代理配置解析与原生 FFmpeg 架构选择测试：通过。
- 浏览器扩展 `background.js`、`popup.js`、`content.js` 的 `node --check`：通过。
- `git diff --check`、合并冲突标记检查：通过。
- FFmpeg 两个架构的固定 SHA-256：从发布资产实际流式读取计算，并写入私有安装器校验。
- Mac CI runner 标签：已核对 actions/runner-images 的当前文档；`macos-15` 为 ARM64，`macos-15-intel` 为 x64。

**未执行**：macOS 原生界面/解码/登录/钥匙串交互、Gopeed 实际 P2P 下载、线上账号与有效分享链接下载、DMG/PKG 打包与安装、GitHub Actions 远程运行。

编译及 JVM 测试通过不代表上述未执行项目已验收。本次交付仍标记为 Beta。完整平台差异与验收项见 `MACOS-PARITY.md`。

源码在本地独立分支保存，未推送至任一远程仓库，也未修改两个原作者的仓库。
