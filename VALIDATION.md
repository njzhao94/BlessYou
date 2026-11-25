# 验证记录：0.1.1

2026-11-25

## 修复内容

- 使用内置的 `res/raw/bless_you.mp3` 直接播放祝福。
- 移除系统 TextToSpeech 引擎和英语语音包要求。
- 试听按钮保持可操作，说明加载或播放错误；显示媒体音量，音量为零时提示调高。

## 构建与校验

- 源代码提交：`6f7557c879542d22eab5ac0ad0c28d0be8938571`。
- 成功构建：https://github.com/njzhao94/BlessYou/actions/runs/45608303491
- `testDebugUnitTest`、`lintDebug`、`assembleDebug` 通过；核心逻辑包含 32 项行为断言。
- 内置音频可由 FFmpeg 解码，时长 1.320 秒，平均音量 -16.9 dB，峰值 -4.0 dB。
- 最终 APK 内的音频与源音频字节一致，且未做 ZIP 压缩，可使用原始资源文件描述符播放。
- 实际 APK 的 DEX 包含 MediaPlayer，不包含 TextToSpeech 类引用。
- APK 重新使用保留的开发密钥签名，v2 和 v3 签名验证通过，ZIP 完整性检查通过。
- 最终对话交付文件：`BlessYou-0.1.1.apk`，14,582,767 bytes。
- 对话交付 APK SHA-256：`6df88bbcfd2e91e8625b557ca1b1d6278bff5e877a20ef47bd170fc20a6e87e1`。
- 对话交付签名证书 SHA-256：`0109b13ac9ad150c1d1f875223b1dbe1d1a98d818fb6798b37d0ad03a047c50c`。

GitHub Actions 的直接下载文件是临时调试签名；以上最终 APK 哈希对应对话提供的重新签名文件。私钥和密码未上传到公开仓库。

## 真机待验证

本次修复针对用户反馈的英语离线语音包缺失问题。S24 Ultra 的扬声器输出、蓝牙路由、实际喷嚏识别、锁屏和长时间运行仍需实机测试。
