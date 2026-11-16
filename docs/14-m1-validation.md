# M1 验证记录

## 版本和环境

- 准备基线：8d2004a；M1 源码为 feat/m1-foundation 未提交工作区。
- 本机 macOS ARM、JDK 21.0.10、SDK 36、AGP 9.2.1、Gradle 9.4.1；Kotlin/Compose 2.3.10，MAVSDK Java/server 2.1.0/2.1.6。
- AGP 兼容依据：https://developer.android.com/build/releases/agp-9-2-0-release-notes 。MAVSDK 版本依据官方 Android 示例：https://github.com/mavlink/MAVSDK-Java/blob/main/examples/android-client/app/build.gradle 。不是对 PX4/目标硬件兼容已验证的声明。

## 自动验证

命令：`env -u ANDROID_SDK_ROOT ANDROID_HOME=/Users/xiethon/Library/Android/sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。

结果：通过。首次环境失败为 SDK 变量冲突，仅对进程移除旧变量后解决，未更改用户全局环境。7 项单元测试覆盖记录原子替换/重开/残留临时文件、非法路径与容量、日志轮转和 ZIP 清单、回放乱序/无效坐标、存储目录及导出失败。没有实机或飞行验证。

报告：`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/reports/lint-results-debug.html`。新增本机 UDP 双向转发、固定目标/学习来源双发及关闭释放测试通过；SDK 适配器经真实 jar API 检查与 Android 编译，仍不代表目标设备 MAVSDK/PX4 链路验收通过。

## 用户设备验收（待执行）

1. 安装 `app/build/outputs/apk/debug/app-debug.apk`，在 7 寸/5 寸及手机横屏查看：按钮可操作，内容可滚动，无截断影响操作；本地存储显示 ready。
2. 确认没有端口输入框；选择实际设备预设，自动连接 PX4 UDP 对端：有心跳后显示 connected；中断链路后不得显示旧连接。通用默认 Local UDP 14551；不要将设备回环地址误认为桌面电脑。
3. 连接一次所选预设后重启 App：恢复上次预设并自动连接；旧 udp-port 配置不再使用。
4. 运行离线样例、导出 ZIP：包含 manifest 与相应 APP/FLIGHT 日志，样例有 SIMULATION 标记，不发送真实命令。取消保存不报成功；不可写目标显示错误。
5. 退到后台再返回：记录实际行为；当前未承诺系统保活，不能用于真实飞行。

## 剩余边界

未测试 native 崩溃、磁盘满、队列过载及全部连接故障；日志策略为开发默认值（每类单文件 256KiB，3 个归档，128 项队列，溢出丢弃并显示诊断），不视为用户批准的正式保留策略。回放当前为只读解析与内置样例入口，未提供外部文件导入及按时间播放。需要后续补齐；M1 未验收，不进入 M2。

## 连接预设改动依据

参考提交 23835025 的 `src/support/settings/settings_defaults.hpp`、`src/core/link/udp_link.cc`。预设保留本地监听和固定远端目标，学习到的来源只作额外发送目标，不覆盖固定配置。MAVSDK 2.x 只在回环地址监听内部端口，由 UDP 数据报适配层转发；不新增 MAVLink 解析或飞行命令。连接切换/断开关闭桥接 socket 并清理订阅；仅在 SDK 心跳发现后显示连接成功。

默认按参考项目非 AR8030 分支使用 Local UDP 14551，未假定目标遥控器型号。自动连接在进程启动加载配置后发生；页面重建不新建连接，切换预设先清理旧会话。新增测试验证预设值、未知预设回退、双向数据内容、固定远端不被入站覆盖及端口关闭后可重绑。

## 自动连接与仿真补充验收（待用户执行）

- 页面无连接/断开按钮。启动按上次预设自动连接；首次为 Local UDP 14551。预设选择在初始化结束后启用，持久化写入与切换串行，旧回调由会话编号隔离。
- 选择 PX4 SITL，显示仿真标识；仿真器配置向 Android 局域网地址:14540 输出 UDP，有心跳后连接。电脑端回环地址不等于 Android 地址；仅保留一个所选连接，不并行扫描设备和仿真。
- 端口绑定等运行错误延迟 3 秒自动重试；无心跳时保持监听等待，不发送解锁/开始/继续命令。原生 SDK 发现期间的可取消性与无对端切换响应仍需实机验证，不能用单元测试替代。
- 自动连接启动、预设切换及 SDK 原生端到端行为尚无设备证据。本轮 Android 构建、7 项现有回归测试及 Lint 通过，仅证明编译/底层数据报回归，不声称真实 SITL 已连接。

## connecting 缺陷修复

- 复现：用户已运行本机 PX4 gz_x500 与 Android emulator-5554，App 选择 PX4 SITL 后持续 connecting。
- 证据：本机 `px4-rc.mavlink` 输出 GCS 到 127.0.0.1:14550；Android 模拟器起初无 UDP redir。App 原生日志显示 Waiting to discover system，实际原生核心报告 v2.14.1（Maven server 工件版本仍为 2.1.6）。
- 源码缺陷：SDK 2.x 的 udp://host:port 是远端模式，原内部桥接误用此形式；改为 udp://:port 监听，桥接在 SDK 发现前启动。官方依据：https://mavsdk.mavlink.io/v2.0/en/cpp/guide/connections.html 。
- 环境修复：已添加宿主机 UDP14550 → emulator-5554 UDP14540；脚本 scripts/connect-sitl-emulator.sh 重复运行确认幂等。
- 回归：assembleDebug、7 项单元回归、lintDebug 通过；修复 APK 已 adb install -r 成功，未启动 UI。
- 待用户复验：重新打开 App，保持 PX4 SITL 预设，预期收到心跳后 connected。未取得修复后端到端连接证据，不标记缺陷或 M1 已验收。

## 二次 connecting 排查结果

- 修复后的原生日志确认为 udp://:42994，监听 URL 修复已生效；转发映射也存在，但仍无发现。
- 临时以只读 UDP socket 接收宿主机14550，实测收到 127.0.0.1:18570 的 42 字节 MAVLink 数据，随后恢复原映射，证明 PX4 发包正常。
- Android 起初只有 lo，无网络 IP；连接 AndroidWifi 后获得10.0.2.17，但 console redir 依赖的 eth0/10.0.2.15 仍关闭。针对当前可 root 的开发模拟器启用该接口/地址后，日志立即出现 system ID 1、Component Autopilot (1)、System discovered、Server started（gRPC34959）。未发送飞行命令，未操作 UI。
- 当前 SDK 发现和 gRPC 启动已有端到端日志证据；页面是否显示 connected 仍由用户核对，不将 native 发现替代全部 UI/生命周期验收。
- scripts/connect-sitl-emulator.sh 新增接口检查；默认缺少接口时报错，显式 FGC_REPAIR_EMULATOR_NETWORK=1 才修复，且先确认 ro.kernel.qemu=1，避免作用到真机。脚本语法与当前环境幂等检查通过；无 App 源码变化，沿用上一轮构建/7测试/Lint结果。

## 文件回放、日志策略与前台服务

- 构建：执行 gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug 成功；后续最终源码增量验证成功。clean仅清工程产物，不是无缓存环境验证。
- 当前11项测试：原7项加播放时间差/完成、停止与替换隔离、空/超大/过长文件、过期日志清理。报告位于 app/build/reports/tests/testDebugUnitTest/index.html。
- 回放CSV：无表头，offsetMs,latitude,longitude，起始时间相对0ms，最多24小时、10000条、1Mi字符；samples/replay.csv正常样例。无数据区间保留时间间隔，不插值补轨迹；目前不推断缺失原因。导入完成校验后才替换当前播放，失败显示错误。
- 日志正式策略见05，过期在追加/导出时清理，队列总128，普通96/飞行32隔离，崩溃直接尽力落盘；关键执行数据未来仍须独立持久化，不能依赖日志无丢失。
- 前台服务类型 connectedDevice，声明对应权限和CHANGE_NETWORK_STATE，在可见Activity启动；不从开机后台拉起。通知、页面退出后台、系统终止/服务重建均待设备验证，不能保证系统杀进程后持续运行。依据：https://developer.android.com/about/versions/14/changes/fgs-types-required 。
- Lint没有错误，但存在版本更新、目标API、缺应用图标/备份规则及MAVSDK libc++_shared.so未满足16KiB对齐警告。此前“无Lint问题”表述以本轮结果纠正。原生依赖兼容问题未解决，M1不通过。

用户验收：
1. 安装最新APK后启动，预设自动连接；通知/系统前台任务可见。退出页面再返回不重复建立连接，记录后台行为。
2. 导入样例CSV，预期第0/1/3秒依序显示3条，最终completed；停止后不再跳点，重新导入不显示旧样例。空/乱序/越界文件应报错，真实连接状态不被回放覆盖。
3. 导出ZIP，检查APP/FLIGHT/CRASH已有文件与清单；未发生崩溃时不要求生成CRASH日志。
4. 服务销毁、无对端切换、端口占用恢复和真实设备页大小兼容仍待补验。无UI自动化或截图。

## SDK阻塞与16KiB修复证据

- 依赖MAVSDK Java/server4.1.0，UDP用udpin://127.0.0.1:内部端口。官方发布：https://github.com/mavlink/MAVSDK-Java/releases/tag/4.1.0 。
- 升级后直接无飞控run探针5秒超时，采用独立进程；期间探针还定位并修复缺C++运行库和Application attach前Context初始化问题，失败不计通过。
- 最终ARM模拟器无UI instrumentation输出：result=PASS: isolated no-vehicle start, switch, disconnect, port release；sitl=PASS: SDK connectionState=connected；switch_without_vehicle_ms=711；INSTRUMENTATION_CODE=-1。探针源码在app/src/androidTest，命令见README。
- 最终APK: 全部arm64-v8a/x86_64原生库（含NDK28 C++运行库）ELF加载段>=16384；zipalign检查通过；Lint Aligned16KB=0。验证脚本scripts/check-native-alignment.py。不等于16KiB页设备运行验收。
- 13项JVM单元回归与Android构建/Lint通过，Lint仍有依赖更新、目标API、图标/备份等非对齐warning。未启动UI，不新增飞行命令。

## M1 当前环境自动验证补齐

- 空Gradle缓存：/private/tmp/fgc-m1-fresh-gradle；独立源码副本/private/tmp/fgc-m1-clean-source；初次下载并构建4m19s成功（记录/private/tmp/fgc-m1-clean-build.log）。最终源码再次同步后clean构建29s成功（/private/tmp/fgc-m1-clean-final.log；本轮新缓存已存在，不冒称二次仍空缓存）。最后仅基础状态文本变化另经正常构建/Lint验证。
- 15项JVM测试通过，新增LogEventQueue满载保留飞行槽/优先消费和空队列等待取消；修复SDK进程退出后旧connected残留、服务停止与后台connect提交竞争。
- 最终4KiB无UI探针（/private/tmp/fgc-m1-device-4k.txt）：page_size4096；notification_permission DENIED但FGS保持；SITL connected；storage_logs PASS；lifecycle PASS；切换713ms；INSTRUMENTATION_CODE -1。
- 最终16KiB无窗口系统（/private/tmp/fgc-m1-device-16k.txt）：官方android-35/google_apis_ps16k/arm64-v8a，page_size16384；通知GRANTED且activeNotification存在；storage_logs/lifecycle/无飞控切换释放PASS，切换764ms；INSTRUMENTATION_CODE -1。临时emulator-5560已关闭。
- 前台服务生命周期检查：系统foreground标志、通知授权/拒绝、终止SDK子进程后恢复绑定及端口、重复start不重复实例、stop后释放连接端口。Shell直接启动非导出服务被正常拒绝，未为测试放宽权限；改用同UID instrumentation验证。
- 4KiB首次新增通知断言因权限被拒绝失败；已修正为区分授权/拒绝，两种场景最终均通过，不计失败轮为成功。
- 本机PX4现有源码git30bbd6ecd4，describe v1.17.0-alpha1-1219-g30bbd6ecd4-dirty；该连接仅为当前仿真环境证据，固定v1.17.0及真实硬件仍待验收。
- 无Activity启动、截图或界面自动化；设备自动验证不替代用户UI验收。执行入口scripts/verify-m1-device.sh，步骤及待办见16。

### JVM异常记录最终补验

提取CrashHandler复用到App默认异常处理器。CrashHandlerTest用真实未捕获线程异常验证CRASH.log落盘并委托原处理器；另验证写盘失败仍报告诊断并委托。两项通过，最终共17项JVM测试。测试不终止Android主进程，不将其冒称原生崩溃/系统强杀验证。最终源码再次同步至独立副本后，使用本轮已建立依赖缓存执行离线clean构建，结果以最终工具输出为准。
