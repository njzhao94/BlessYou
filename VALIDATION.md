# 验证记录

2026-11-25

## 构建成果

- APK：`BlessYou-0.1.0.apk`，14,570,656 bytes。
- 应用 ID：`app.blessyou.sneeze`；最低 API 26，目标 API 36。
- 构建源代码提交：`c9ffa7afc21eb30a5d7b0b37d995e589546a76a7`。
- 成功的构建：https://github.com/njzhao94/BlessYou/actions/runs/45605710962
- APK SHA-256：`22d153aa116b91e6ac52f2341830f806fc558e4d3b05a84463049567609416ff`。

## 已执行并通过

- Android Java 编译与 `assembleDebug` 打包。
- `testDebugUnitTest`：CoreChecks 的 32 项行为断言通过。
- `lintDebug`：无错误；剩余提示为本地化文本、Gradle 可用更新、备份配置和持续聆听会话的唤醒锁超时建议。
- Android SDK `apksigner verify --verbose --print-certs`：APK Signature Scheme v2 验证通过。
- `aapt2 dump badging`：应用标识、版本、目标 API 和 ARM64/x86_64 架构匹配。
- 从 Actions 下载的 ZIP 和其中 APK 的 SHA-256 分别与云端公布值一致。
- APK ZIP 完整性、Manifest、DEX、YAMNet 模型和 ARM64 原生库存在。
- 解析实际打包的 YAMNet 模型：FLOAT32 输入 `[15600]`，FLOAT32 输出 `[1, 521]`，无自定义算子。
- 类别表的第 44 类为 Sneeze；模型 SHA-256 为 `4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf`。
- ARM64 原生库的全部 LOAD 段对齐为 16,384 bytes。

## 尚未执行

- S24 Ultra 实机安装、系统英语 TTS、麦克风和通知授权验证。
- 实际喷嚏、咳嗽、拍手、谈话等声音的识别准确性测试。
- 锁屏、后台、通话冲突、长时间运行及耗电测试。

这是已编译并签名的测试版 APK；上述检查不等同于已完成真实声音或手机实测。
