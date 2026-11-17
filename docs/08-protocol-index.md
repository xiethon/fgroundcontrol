# 协议资源索引

- 标准 MAVLink 交互参考 QGroundControl 与 PX4，包括连接、遥测、任务上传、命令 ACK 和执行状态同步。
- 以固定 PX4 v1.17.0 及标准 MAVLink为准；参考实现记录版本/提交，不直接照搬或视为已验证行为。
- QGroundControl 与 PX4 行为存在差异时，核对协议和目标飞控日志，确认后实现。复用代码前检查许可证。

补充参考：FGroundControl 资料仓库 `/Users/xiethon/fairland/fgroundcontrol`，参考提交 `23835025`。以下资料用于核对协议，不决定本项目行为。

| 参考资料相对路径 | 用途 |
| --- | --- |
| docs/飞控系统与地面站的MAVLINK协议.md | 标准及自定义通信约定 |
| docs/喷洒指令与状态相关协议.md | 仅识别需排除的私有喷洒内容，不作为实现契约 |
| docs/mission_upload_flow.md | 上传流程 |
| docs/mission_state_and_index_contract.md | 状态和航点索引 |
| docs/mission_execution_reattach.md | 重连与任务恢复 |
| src/core/mission/execution/mission_upload_protocol.cc | 核对实际上传实现 |
| src/core/mission/execution/mission_execution_controller.cc | 核对实际执行实现 |

T02 需在本仓库固化：飞控版本、标准 dialect/生成版本、消息字段、目标路由、坐标/高度/单位、航点映射、命令前置条件、ACK/状态确认、超时重试及恢复识别。每项注明来源版本与确认状态，冲突需核对目标飞控，不直接照搬参考实现。

构建不得依赖参考仓库的本地路径。未固化协议前，不实现对应飞行命令。

## 已确认基线（2026-11-16）

- PX4 v1.17.0，标准 MAVLink，不引入自定义 XML 或私有命令。
- 首条链路为无线；采用 UDP；端口、地址与发现机制待确认。
- 首期不控制喷洒，参考项目私有喷洒协议及指令不纳入本项目契约。

## MAVSDK 契约补充

接入使用 MAVSDK；T02 增加 SDK/Java/server 版本对应、可用插件/API、结果到业务错误映射、内部超时重试与取消行为、订阅/会话销毁、任务项/索引及只读下载能力核对。优先复用 SDK 协议实现，不再要求 App 自写上传握手；实际协议字段、坐标单位和状态证据仍须明确。Android 接入限制及官方来源见 02。

## M2 只读遥测契约（固定MAVSDK4.1.0）

| 主题 | SDK来源 | 单位/有效性 |
| --- | --- | --- |
| position | Telemetry.position.latitudeDeg/longitudeDeg | 原始WGS84经纬度（度），有限且范围合法，(0,0)不作缺失哨兵 |
| altitude | relativeAltitudeM / absoluteAltitudeM | 相对起飞点m / AMSL海拔m；分别标注，不换算为椭球高 |
| velocity/speed | velocityNed.northMS/eastMS/downMS | 原始NED m/s；展示地速sqrt(N²+E²)及升降速度-down，向上为正 |
| attitude | attitudeEuler.rollDeg/pitchDeg/yawDeg | 度；未知/过期姿态不画航向 |
| battery/voltage | battery.remainingPercent / voltageV | 4.1.0为0–100百分比，禁止再次乘100；V独立有效性，显示电池ID |
| GPS | gpsInfo.numSatellites/fixType | 卫星数/定位类型；3D、DGPS、RTK FLOAT/FIXED为定位正常候选 |
| mode/armed | flightMode/armed | 事件状态，未收到未知；连接失效即清空，不能用缺失值当未解锁 |
| health | SDK七项Health标志 | 校准、位置、Home、armable分别展示；非控制依据 |
| status | statusText | 类型+原文，512字符上限；去重事件记录，不把文本当可自动清除的飞行状态 |

周期主题实际SDK回调接收时记录设备elapsedRealtime；超过3秒过期，恰好3秒不算超限。mode/armed/health/status是变化事件，不因状态未变而单独套3秒TTL；连接失效全部无效。时间为SDK交付时刻，不冒称飞控原始帧时间。请求位置/速度2Hz、姿态5Hz、电池/GPS1Hz，失败记录诊断，实际新鲜度以收到数据为准。

独立进程IPC每250ms发送最新快照，原接收时间保留；主进程按token拒绝旧会话/旧序样本。切换预设/SDK进程重启清空数据与轨迹，断链不桥接缺失轨迹；每会话最多5000点。界面与日志均不发送解锁、开始、暂停、继续等操作；频率请求仅调整遥测消息间隔。

来源：官方Maven mavsdk-4.1.0-sources.jar的TelemetryProto注释、实际jar API，以及https://raw.githubusercontent.com/mavlink/MAVSDK-Proto/main/protos/telemetry/telemetry.proto 。固定包注释已核对电量与高度；最新main不替代版本包定义。
