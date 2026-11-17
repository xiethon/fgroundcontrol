# FGroundControl

Kotlin + Compose Android 无人机作业系统，当前为 M2 只读遥测与地图阶段实施中。

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

自动 UDP 连接、只读遥测/健康提示、天地图与 MapTiler 切换、授权栅格 PMTiles 导入；保留原子本地记录、三类日志轮转/ZIP 导出和 CSV 回放。普通96/飞行32有界日志队列，JVM崩溃尽力同步保存；不保证原生崩溃或强杀堆栈。无规划、上传、飞行命令。
- [当前进度](docs/07-progress.md)
- [文档索引](docs/README.md)

连接预设参考原项目：P401D、Local UDP 14551（通用默认）、RC7、MK32、PX4 SITL。保存上次选择的预设；启动自动连接，切换预设自动替换连接，无连接/断开按钮。SITL 的 127.0.0.1 指运行 App 的设备，桌面 SITL 需要相应隧道，不代表手机可直接访问电脑回环地址。

仿真连接：选择 PX4 SITL，电脑仿真器向 Android 设备的局域网 IP:14540 发送 MAVLink UDP。App 监听并学习来包来源用于回送；电脑/手机需网络互通并允许该 UDP 流量。127.0.0.1 仅适用于同机或已建立转发的环境。当前支持只读遥测，不提供仿真飞行命令；当前环境与固定稳定版SITL的验证范围见M2记录。

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

M2 固定飞控验证基线（与 Android 构建及现有 SITL 隔离）：

```sh
scripts/prepare-px4-stable.sh
# 指定独立目录时：FGC_PX4_STABLE_DIR=/path/to/isolated-px4 scripts/prepare-px4-stable.sh
# 在已准备的官方 v1.17.0 目录内只构建、不启动：
make px4_sitl_default
```

仅需 SIH 的 macOS 环境，可禁用可选 Gazebo/jMAVSim 依赖发现：

```sh
make px4_sitl_default 'CMAKE_ARGS=-DCMAKE_DISABLE_FIND_PACKAGE_gz-transport=TRUE -DCMAKE_DISABLE_FIND_PACKAGE_Java=TRUE -DCMAKE_DISABLE_FIND_PACKAGE_Protobuf=TRUE'
```

`make px4_sitl sihsim_quadx` 会启动仿真，不能作为纯构建命令；该版本不可依赖 `DONT_RUN` 阻止启动。并行验证需使用独立实例、运行目录及UDP目的端口，避免混入现有飞控数据。

## M2 地图配置与用户验证

也可在仓库根目录已忽略的 `local.properties` 中配置（保留原有SDK配置）：

```properties
maps.tianditu.token=你的天地图token
maps.maptiler.key=你的MapTiler密钥
```

两项均可选，修改后需重新构建安装。优先级：页面保存的非空密钥 > 构建时本地配置；未配置则提示填写。配置会随APK打包，请使用可用于Android客户端的密钥，不在仓库或日志中记录真实值。

点击“填写地图密钥”，输入天地图和/或MapTiler密钥后保存。留空保留该源已保存的密钥；输入掩码显示，不回显旧密钥，取消不保存。配置持久化到应用私有目录，重启保留；不再通过JSON文件导入。

离线导入需合法授权的 PMTiles v3 PNG/JPEG/WebP 栅格包，最大512MiB，元数据包含非空 `attribution`；需要道路标注时由包内容提供。最多保留两个导入包；不提供供应商瓦片批量下载。包头合法不代表瓦片内容已经验证，最终以地图实际加载为准。

用户验证：在5/7寸横屏中检查遥测单位/布局；填写并保存密钥后切换天地图/MapTiler核对真实影像及署名；导入授权包后断网检查离线显示和坐标；拖地图取消跟随，再勾选恢复；断开SITL应显示缺失/过期或链路异常，重连后恢复且不连线跨越缺失轨迹。

M2详细验证及未验收项见 [M2记录](docs/17-m2-validation.md)。

M2无UI协议与故障回归（仅开发Android模拟器，需已构建主/测试APK、eth0/10.0.2.15可用）：

```sh
# 标准MAVLink测试源，需本地Python已安装pymavlink
ANDROID_HOME=/path/to/android-sdk python3 scripts/verify-m2-protocol.py emulator-5554
# 固定官方v1.17.0已构建目录；独立SIH实例1和临时运行目录
ANDROID_HOME=/path/to/android-sdk python3 scripts/verify-m2-px4-restart.py emulator-5554 /path/to/isolated-px4
```

使用专用UDP14560/14561、控制14660/14661及独立SIH的14551/18571；占用时停止，不覆盖已有映射。后者要求实例1空闲。脚本结束清理自己创建的映射/进程，保留运行日志目录；不会重启用户已有SITL或启动Activity。协议回归验证SDK接收时刻的新鲜度，不承诺拒绝所有飞控旧时间帧。
