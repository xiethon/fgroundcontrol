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
