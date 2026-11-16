# FGroundControl

Kotlin + Compose Android 无人机作业系统，当前为 M1 基础工程实施中。

## 环境与构建

JDK 21（编译目标 Java 17）、Android SDK 36 / Build Tools 36.0.0、Gradle Wrapper 9.4.1、AGP 9.2.1、内置 Kotlin 2.3.10 / Compose 插件 2.3.10。最低 Android 9，targetSdk 36。

设置 `ANDROID_HOME` 指向 SDK；若已有 `ANDROID_SDK_ROOT`，保持同一路径或移除。运行：

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint：`app/build/reports/lint-results-debug.html`。

MAVSDK Java/server 4.1.0 配套固定，C++ 共享运行库来自 NDK 28.2.13676358；仅连接原型，不开放飞行控制。需要支持的 ARM 设备验证，不能用构建通过替代链路验收。

## 当前能力与限制

工程启动页、原子本地记录、三类文件日志轮转/ZIP 导出底层、离线回放解析、MAVSDK UDP 连接适配器。已提供 UDP 连接预设选择（无需输入端口）、断开、离线样例和日志导出入口，日志后台队列上限 128。崩溃日志仅尽力记录未捕获 JVM 异常摘要，不保证原生崩溃或系统杀进程记录；已加入 connectedDevice 前台服务，但其后台行为仍待设备验证。无规划、上传、飞行命令。

- [当前进度](docs/07-progress.md)
- [文档索引](docs/README.md)

连接预设参考原项目：P401D、Local UDP 14551（通用默认）、RC7、MK32、PX4 SITL。保存上次选择的预设；启动自动连接，切换预设自动替换连接，无连接/断开按钮。SITL 的 127.0.0.1 指运行 App 的设备，桌面 SITL 需要相应隧道，不代表手机可直接访问电脑回环地址。

仿真连接：选择 PX4 SITL，电脑仿真器向 Android 设备的局域网 IP:14540 发送 MAVLink UDP。App 监听并学习来包来源用于回送；电脑/手机需网络互通并允许该 UDP 流量。127.0.0.1 仅适用于同机或已建立转发的环境。当前仅连接状态，不提供仿真飞行命令；尚未完成真实 SITL 联调。

Android 模拟器与本机 PX4 SITL 联调（默认 PX4 GCS 输出到宿主机 14550）：

```sh
ANDROID_HOME=/path/to/android-sdk scripts/connect-sitl-emulator.sh emulator-5554
```

该脚本将宿主机 UDP14550 映射至 Android 模拟器 UDP14540，不启动 App 或飞控。模拟器重启后按需重建。真机不使用此脚本。

若 Android 模拟器仅有 Wi-Fi 地址10.0.2.17，而 eth0/10.0.2.15 不可用，console UDP 转发可能无法收包。脚本会先检查并明确报错；仅在可 root 的开发模拟器可显式使用 `FGC_REPAIR_EMULATOR_NETWORK=1` 重新运行以启用该接口。该操作仅适用于 Android 模拟器，不用于真机。

回放：导入无表头CSV `offsetMs,latitude,longitude`，以相对毫秒按时序播放，可停止；示例 `samples/replay.csv`。上限10000条、1Mi字符、24小时；回放与真实连接状态隔离。

日志正式上限：每类20MiB（5MiB当前文件+3归档）、最多7天。原生16KiB对齐警告已消除，最终APK的ELF/ZIP对齐检查通过，但仍不能替代16KiB页设备运行验证。

原生接入：SDK运行在非导出的 `:mavsdk` 独立进程；主进程用Messenger管理会话。切换/停止退出旧SDK进程，避免等待飞控时阻塞主进程或并发销毁native对象。NDK 28.2.13676358为构建必需项，可通过SDK Manager安装；运行库及NOTICE在构建时从固定NDK生成，不依赖参考仓库。

无UI设备探针：构建 `:app:assembleDebugAndroidTest`，安装主/测试APK后执行：

```sh
adb -s emulator-5554 shell am instrument -w com.fairland.fgroundcontrol.test/com.fairland.fgroundcontrol.SdkProbeInstrumentation
# 已准备PX4 SITL与模拟器UDP映射时：
adb -s emulator-5554 shell am instrument -w -e sitl true com.fairland.fgroundcontrol.test/com.fairland.fgroundcontrol.SdkProbeInstrumentation
python3 scripts/check-native-alignment.py app/build/outputs/apk/debug/app-debug.apk
```

探针会重启应用进程并连接/断开测试链路，不启动Activity、不发送飞行命令；仅用于开发/仿真环境。成功输出 `INSTRUMENTATION_CODE: -1`，失败不得视为通过。ZIP另用SDK的 `zipalign -c -P 16 4` 检查。

完整设备自动验证：先构建 `:app:assembleDebugAndroidTest`，再执行 `scripts/verify-m1-device.sh emulator-5554 true`（需要已运行SITL并配置映射）；第二参数false可只检查原生生命周期/存储/日志。脚本明确校验INSTRUMENTATION_CODE，不以adb退出码替代测试结果。

M1当前环境自动检查完成，用户及目标硬件待验收项见[清单](docs/16-m1-acceptance.md)。应用首次启动请求通知权限，拒绝不阻止连接服务，但通知栏可能不显示。
