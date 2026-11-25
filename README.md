# Bless You — 安卓喷嚏祝福应用

为 Galaxy S24 Ultra 等 Android 手机上“检测喷嚏后自动说 Bless you”而写的原生安卓实验项目。

**当前交付的是源代码项目，尚未生成 APK，也未完成 Android 编译或真机测试。不能把这个 ZIP 当作安装包。**

已运行并通过 32 项 JVM 核心行为检查；这些检查覆盖声音缓冲、重叠窗口去重、播报间隔和分类分数边界，不代表喷嚏识别准确率或真机兼容性已经得到验证。

## 使用效果

1. 先点“试听 Bless you”，确认英语语音和媒体音量正常。
2. 点“开始聆听”，允许麦克风权限。允许通知后可以从通知栏随时停止。
3. 状态显示“正在聆听”后，应用分析周围声音，检测到喷嚏时播报 Bless you。
4. 可调节灵敏度及 2–10 秒祝福间隔；默认间隔 5 秒。
5. 按“停止聆听”结束麦克风访问。重启手机或系统结束应用后，需要手动重新开始。

声音识别使用打包在 APK 中的 YAMNet 模型；约 1 秒声音窗口留在内存中，不写录音文件。应用没有 INTERNET、存储、通讯录、定位或相机权限。祝福使用系统的离线英语语音包。首次构建需下载模型和依赖；手机正常运行时不需要网络。

本应用识别声音类别，无法区分是你还是旁边的人打喷嚏，也可能对电视中的喷嚏回应。未校准的分类分数不等于识别准确率。

## 得到 APK 的方式 A：Android Studio

1. 安装 Android Studio，使用它自带的 JDK 17 或更新版本。
2. 解压项目。先在项目目录运行 `gradlew.bat --version`（Windows），或 `sh gradlew --version`（macOS/Linux），下载官方 Gradle 启动文件。
3. 在 Android Studio 中打开 `BlessYou` 文件夹，等待同步。根据提示安装 Android SDK 36 和 Build Tools 35.0.0。
4. 菜单中选择构建 APK，或在项目目录运行：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 对应命令：

```bat
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

预期 APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`。这个文件只有成功构建后才会存在。

## 得到 APK 的方式 B：GitHub Actions

项目附带手动构建流程，尚未上传到 GitHub，也尚未执行。

1. 建立你自己的私有 GitHub 仓库。
2. 将本文件旁边的项目文件上传到仓库根目录，保留 `.github/workflows/build-apk.yml`；不要在仓库根目录外再套一层 BlessYou 文件夹。
3. 进入 Actions → Build Android APK → Run workflow。
4. 成功后下载页面底部的 `BlessYou-Android-APK`，解压获得 `BlessYou-0.1.0.apk`。
5. 若失败，保留失败步骤的日志；不要将尚未构建的项目当成 APK。

流程只在手动点击运行后执行，不发布应用，不创建 Releases，不授予仓库写入权限。

## 在 S24 Ultra 上安装和测试

拿到实际构建成功的 APK 后，将其传到手机，打开文件进行安装。若系统询问安装权限，只为你此次使用的文件管理器或浏览器允许安装。若企业管理或三星的安全设置阻止安装，按手机给出的提示处理。

先在应用中试听。如果缺少英语语音包，通常可到“设置 → 常规管理 → 文字转语音”中下载；实际菜单随 One UI 版本变化。

锁屏测试需要实机验证。若后台被系统结束，可在“设置 → 应用程序 → Bless You → 电池”查看后台运行限制；不用在开始测试前一次改动多个系统设置。通话或其他录音应用可能占用麦克风。

建议先在安静环境测试几个自然发生的喷嚏，再测试咳嗽、拍手及正常说话是否误触发。漏报时调高灵敏度，误报时调低。

## 验证状态和限制

见 `VALIDATION.md`。当前不能保证下载依赖、构建、锁屏运行或识别效果成功。持续使用麦克风和模型会耗电；只在需要时开始聆听。

## 构建配置

- Android API 26+；编译/目标 API 36。
- 原生 Java 17；Android Gradle Plugin 8.13.2 / Gradle 8.13。
- LiteRT 1.4.1 的 Interpreter API；ARM64 真机 / x86_64 模拟器。
- 官方 YAMNet float32 模型版本 1；16 kHz 单声道 PCM。
- 模型输出第 44 类为 Sneeze；构建时下载官方类别表并验证该映射。
- 0.24 秒步进，约 0.975 秒窗口；阻止同一事件的重叠窗口重复播报。
- 只有用户在界面点开始后，才启动 microphone 类型前台服务。
- 没有开机自动运行、广告、账户、统计 SDK 或云端声音上传。

`scripts/check_core.py` 可以在安装了 JDK 17+ 的电脑上独立运行核心检查，不需要 Android SDK。

构建产生的是开发测试签名。换电脑或重新创建签名后，升级可能需要先卸载旧版本；卸载会清除设置和祝福计数。若后续长期使用，应保存自己的签名密钥。

## 上游来源

- 模型：https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
- 模型类别：https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv
- YAMNet：https://www.tensorflow.org/hub/tutorials/yamnet
- 麦克风前台服务：https://developer.android.com/develop/background-work/services/fgs/service-types#microphone
- 构建配置：https://developer.android.com/build/releases/agp-8-13-0-release-notes

许可见 `LICENSE` 和 `THIRD_PARTY_NOTICES.md`。
