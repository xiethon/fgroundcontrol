# M2 实施与验证

状态：M2当前阶段已验收，字段/链路故障证据及Review差异复验已补齐，准备本地源码收尾。目标硬件/长稳等范围限制保留，不伪造通过；Git实际结果见07。2026-11-17，分支feat/m2-readonly，基线main 490d454，验证对象为工作区源码。

## 范围与实现

- T02遥测子集、T06连接/遥测、T07地图/只读健康；不规划、上传、解锁、起飞、执行或恢复任务，不下载uLog。
- LocalMavsdkEngine订阅SDK4.1.0并保留单调接收时间；进程IPC250ms，主进程保留会话、字段质量；断线清空并隔离旧进程/旧订阅。MAVSDK数据率设置只调遥测流，不发送飞行行为命令。
- TelemetryState/Monitor处理字段/单位、3秒新鲜度、健康去重/解除、5000点分段轨迹和最多1Hz日志。位置保持WGS84；回放仍独立且带SIMULATION标记。
- MapRepository/MapStyles/OfflineArchive与ReadOnlyPanel负责MapLibre13.7.1、天地图影像/标注、MapTiler hybrid、授权本地PMTiles导入、署名/范围、飞机朝向及跟随。无朝向时用圆点，位置无效/过期时不显示飞机；缺少密钥不显示伪地图。
- 密钥通过页面填写保存至应用私有目录，不输出URL/密钥日志；供应商权限与额度按用户本地配置。PMTiles最多512MiB、PNG/JPEG/WebP、必须署名；包头检查不等于瓦片内容已验证。

## 自动验证证据

环境：macOS ARM64、JDK21、Android SDK36，MAVSDK4.1.0。所有设备探针均未启动Activity/MapView，不截图、不操作UI、不发送飞行操作。

| 场景 | 输入及预期 | 实际 |
| --- | --- | --- |
| 构建/回归 | README构建入口，主/测试APK、单测、Lint | assembleDebug/assembleDebugAndroidTest/testDebugUnitTest/lintDebug成功，26 tests，0 failures/errors；Lint有提示无error |
| 字段边界 | 3秒整/3001ms、0坐标、null/NaN/越界、未来时间、旧会话/乱序 | 6项TelemetryState测试通过；20%不告警、10%提醒、9.9%严重 |
| 离线边界 | 合法栅格头、截断/越界/矢量包、密钥格式与源路由 | 3项OfflineArchive测试通过 |
| 原生兼容 | 所有64位ELF LOAD段及ZIP16KiB对齐 | 10个库通过（含MapLibre），zipalign退出0 |
| 4KiB设备 | API35 ARM Tablet emulator-5554，现有开发SITL | 探针CODE -1，连接、位置/电量质量、生命周期、存储日志与地图配置通过 |
| 16KiB设备 | API35 google_apis_ps16k ARM64 emulator-5560，PAGE_SIZE16384 | 探针CODE -1，MapLibre原生加载、无飞控切换/停止、子进程恢复、存储日志与导入通过 |
| 固定稳定飞控 | 官方v1.17.0 d6f12ad1c4f70ad3230afd7d86e971421e02fef4，SIH quadx实例1，16KiB设备 | connected及实时位置/电量有效，探针CODE -1；未解锁/起飞 |
| 导入失败与替换 | 仅测试用虚构密钥及微型栅格包，不请求供应商；连续合法导入后导入坏包 | 保留旧样式，文件URI不复用；坏密钥/坏包不替换已有配置；未验证实际渲染 |

临时原始输出：`/private/tmp/fgc-m2-final-build.log`、`fgc-m2-device-final.txt`、`fgc-m2-device-16k.txt`、`fgc-m2-device-stable.txt`、`fgc-m2-px4-build-only.log`。这些文件位于临时目录，关键结论已保留本记录。

固定PX4在独立目录构建，tracked源码无改动。依赖Micro-CDR v2.0.1复用本机Git缓存（3d1b17703c7cf4f22def2910bc845bdb5152d7b5），只在构建进程配置URL，不改全局Git。macOS禁用SIH不使用的Gazebo/Java/Protobuf发现，避免OpenCV缺失及本机新版Abseil/C++14不兼容；这是构建环境选择，不代表Gazebo验证通过。纯构建命令见README。独立运行目录副本只改GCS目的14551，源18571；原用户实例0与14550链路未改。先前仿真目标尝试运行被已有实例0拒绝，后续改用明确纯构建目标。

## 自查修复

- SDK电量为0–100，不重复乘100；电压未知不掩盖已知低电量。
- 模式/解锁/健康为事件字段，未变化不虚假判定3秒过期；断线失效。健康数据丢失记为不可用，不伪报恢复。
- 会话、旧订阅代际、原始时间戳隔离；过期/断线不延长新鲜度或跨缺失区间连接轨迹。
- 样式异步结果带revision；唯一离线包文件名避免缓存混用，保留前包供旧请求读取。主线程仅展示/MapView生命周期，导入/校验/GeoJSON生成在IO。

## 待用户验证与剩余条件

1. 填写并保存地图密钥，分别切天地图/MapTiler：真实卫星与标注、署名正确；密钥错误/断网应有明确不可用提示。核对中国及欧美目标区域，不能以源配置通过替代覆盖验收。
2. 导入合法授权包，断网检查实际瓦片/坐标；坏包保留既有地图，包外位置有范围提示；拖动取消跟随、重新勾选可恢复。
3. 5寸/7寸横屏检查文字、滚动、地图手势与文件选择；进入后台/返回不崩溃、不重建飞控会话。
4. 断开再连接SITL，检查数据缺失/过期、飞机标记及轨迹断段；异常重复不刷屏、恢复有事件记录、无声音或飞行动作。

尚未验证：目标遥控器/Android9、全字段与飞控逐值对照、受控丢包/延迟及飞控重启、长稳/性能、在线供应商实际请求及离线包实际渲染。这些不标记通过；M2未具备源码收尾条件，不进入M3。

环境清理：本任务稳定版SIH实例1已正常shutdown，临时无窗口16KiB模拟器5560已关闭；用户原模拟器5554与既有SITL保留。源码、构建产物与验证记录保留，未提交或合并。

地图密钥输入变更验证：assembleDebug、assembleDebugAndroidTest、26项单元测试及lintDebug通过；emulator-5554无UI探针CODE -1，验证合法填写保存、去除首尾空白、单源替换时保留另一源、仓库重开读取、非法输入不覆盖旧样式；未发供应商请求。输出：/private/tmp/fgc-m2-key-input-build.log、/private/tmp/fgc-m2-key-input-device.txt。弹窗输入/取消/键盘横屏交互仍待用户验证；未启动UI、未提交/合并。

配置文件支持验证：Android主/测试APK构建、26项单元测试及Lint通过；4KiB无UI设备探针CODE -1，使用测试默认值验证未保存时读取配置默认值、两供应商切换、页面保存覆盖、单源保留与重开读取。未读取或写入用户真实token，未新增供应商请求；用户已确认天地图输入token后可加载，其他UI项仍待验收。证据：/private/tmp/fgc-m2-map-config-build.log、/private/tmp/fgc-m2-map-config-device.txt。未提交/合并。

最新结论以18-review-m2.md及07最新段落为准：用户已验收此前版本，本轮全量Review修复后27项单测及无UI回归通过；地图差异待复验，剩余未验证项目不转为通过。

## 收尾前补验（取代此前M2字段/故障及差异验收缺失状态）

用户已明确确认Review后的快速切源、状态/相机、错误token或断网提示/重选重试、选择重开保持均复验通过，并授权补齐后执行本地源码收尾。此次只增加验证代码/证据，没有新增飞行语义或改动已验收的产品实现。

标准MAVLink测试源：Python pymavlink common v2，sysid245/component1，SIMULATION protocol fixture，Android API35 ARM64/4096页，MAVSDK4.1.0。独立UDP14560/14660，不与真实飞控连通，不使用自定义MAVLink。

| 字段 | 测试输入及期望 | 实际 |
| --- | --- | --- |
| 经纬度 | GLOBAL_POSITION_INT 473977420/85455940 → 47.397742/8.545594度 | 误差≤1e-7度，通过 |
| 高度 | 相对12500/海拔488125mm → 12.5/488.125m | 误差≤0.01m，通过 |
| NED/展示速度 | 300/400/-200cm/s，LOCAL_POSITION_NED 3/4/-2m/s → 原始3/4/-2、地速5/升速2m/s | 误差≤0.01，通过 |
| 姿态 | roll0.1/pitch-0.2/yaw0.3rad及一致四元数 → 5.72958/-11.45916/17.18873度 | 误差≤0.01度，通过 |
| 电池 | 12000mV/55%；重启源变66% | 电压12V、电量55→66，必须收到新值后才通过 |
| GPS | 3D/12颗 | FIX_3D/12，通过 |
| 模式/解锁 | PX4 AUTO LOITER自定义模式，未设置armed位 | HOLD/0，通过；未发送解锁操作 |
| 健康 | 标准视觉定位present/healthy，HOME/LOCAL_POSITION已发送，其余校准/GPS健康及prearm位不置位 | 七标志0/0/0/1/0/1/0，通过 |
| 状态文本 | INFO + FGC_PROTOCOL_FIXTURE | INFO原文一致，通过 |

受控链路：每两包丢一个遥测包、1秒延迟、同值报文重复/逆序发送均继续收到新样本且数值一致；心跳保持但停遥测超过3秒产生stale与健康提示；全停数据后disconnected且readings清空；测试源重置boot时间/序号并将电量改66%，自动重连且各字段恢复。不同值旧飞控帧的乱序拒绝并非SDK到达时间契约的保证，不冒称已覆盖。

最初加强健康断言暴露测试源LOCAL_POSITION与SYS_STATUS定位健康输入不一致；修正夹具为明确VISION_POSITION健康位后重跑通过，没有修改产品健康规则迎合测试。

另对官方固定PX4 v1.17.0 d6f12ad1c4f70ad3230afd7d86e971421e02fef4做实际SIH实例1关闭/重新启动：全部周期字段有效→关闭后断开并清空→重新启动后自动连接并接收更晚的新样本，通过。独立运行目录/端口，不更改或重启用户实例0，结束已正常关闭并删除临时UDP映射。运行目录：/var/folders/__/h77pwxx96vv7_70ssw7qnr940000gn/T/fgc-m2-px4-restart-7k52zgxq。

最终证据：/private/tmp/fgc-m2-close-build.log、fgc-m2-protocol.txt、fgc-m2-px4-restart.txt、fgc-m2-close-device.txt；全部instrumentation CODE -1。27项单测、构建及Lint通过，原生ELF/ZIP对齐复核通过；原有生命周期/子进程恢复/存储日志/地图错误路径回归通过。

M2当前环境与用户验收完成。目标遥控器、Android9实机及长稳性能仍未实测，作为后续综合/发布验收限制保留；固定SITL或模拟器证据不替代这些结果。T02只读子集已落实，M0飞行/规划未冻结事项及T08以后不在本阶段展开。
