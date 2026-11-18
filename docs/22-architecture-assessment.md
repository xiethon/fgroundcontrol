# 当前架构梳理与优化建议

本轮仅静态检查源码与既有证据，不修改实现、不重跑构建或UI，不作为全量Review/迁移验收。

## 当前实现与目标的区别

当前仅一个app Gradle模块，按ui/connection/telemetry/maps/data分包；任务/规划/上传/执行/恢复/报告仍是后续目标，不能将02中的规划当作已实现架构。

存在两套尚未集成的开发树：主工作区feat/ui-map-home承载Figma UI重构及MAVSDK链路；/private/tmp/fgc-mavlink-direct的feat/mavlink-direct-udp承载直接UDP/生成Java协议，不包含新UI。两者均有未提交改动，main仍是d95a2ec。安装任一分支APK会替换同applicationId的另一版本；这是已经出现过的调试混淆来源，当前最先需要治理。

已落地的数据流：ConnectionService管理前台生命周期→GroundControlApp持有连接、配置、日志、回放和地图对象→连接发布VehicleTelemetry→TelemetryMonitor生成健康/轨迹/飞行日志及Readout→FoundationViewModel→Compose/MapLibre。整体有StateFlow、后台IO、会话代际、样式代际、原子存储和有界日志队列，应该保留。

## 优先级与优化目标

| 优先级 | 依据/问题 | 建议与完成标准 |
| --- | --- | --- |
| 高 | 两工作树UI/通信互不包含，02仍有“迁移尚未执行”等旧描述 | 先列清两边差异与验收状态，再按用户授权完成集成；构建显示版本/分支或生成清单，让安装产物可追踪。不要在未验收前擅自合并或夹带改动 |
| 高 | DirectMavlinkConnection同时承担socket、心跳发现、目标锁定、帧分派和字段换算 | 拆成UdpTransport、MavlinkCodec、VehicleSession、TelemetryMapper；以接口和构造注入接入，UI/领域不依赖生成消息类型。更换链路不再改Application和大量探针 |
| 高 | 新链路仍缺完整健康映射、消息频率事务、多包STATUSTEXT，XML未与固定PX4版本核对 | 优先补齐迁移契约和证据；健康/文本/频率拒绝超时、旧会话/重启、固定飞控逐字段对照通过后才宣称等价替换，不用假值消除缺失 |
| 高 | 连接state和telemetry分别发布；session Int仅在connect/disconnect改变；直接UDP用固定时间常量和字符串错误 | 建立单一连接快照（会话、目标、阶段、错误、遥测），明确重连/飞控重启的代际边界，注入单调时钟和连接策略；覆盖超时前后、旧来源重入和快速切换，不把ACK当实际状态 |
| 中 | GroundControlApp直接管理存储写入、日志、连接重试、地图和回放；FoundationViewModel透传Application | Application保留轻量组装入口；拆ConnectionCoordinator、配置/日志/回放服务并注入页面ViewModel。先构造注入，不为此引入大型DI框架 |
| 中 | VehicleTelemetry以字符串topic、List<Double?>和text承载异构数据；GPS/模式/health多处字符串判断 | 按主题引入具名数据类型、枚举及字段质量；统一单位、无效哨兵、源消息和采样时间，边界仅做一次转换，减少索引/字符串耦合 |
| 中 | ReadOnlyPanel.kt同时含诊断UI、MapView生命周期、MapPresenter与原生绘图；StartupFlow集中多页；TopBar内有假数据 | 按页面/地图呈现器/遥测展示模型拆分；假数据放独立预览数据源并由明确配置控制，保持用户要求的不显示标签，避免进入真实领域状态；token与原始素材继续共享 |
| 中 | TelemetryMonitor每250ms结合健康、轨迹、GeoJSON和日志，直接使用Android时钟 | 分离纯健康规则、轨迹累计器与日志事件投影；仅轨迹变化生成GeoJSON，注入时钟便于确定性测试；测量后再优化5000点集合复制，不能无证据宣称已有性能瓶颈 |
| 中 | 生成334个Java文件与业务同目录，生成脚本依赖开发机pymavlink，旧SDK探针/桥接残留 | 独立generated源码目录或协议模块，固定生成器/定义/许可证、CI校验重生成差异；清理不再使用的SDK探针和桥接，保留清楚的历史文档，不删除仍被MapLibre需要的原生运行库 |
| 后续 | FoundationStore适合小配置，尚无任务版本/执行快照持久化模型 | 本阶段维持原子文件；进入任务阶段后再确认事务/迁移/版本模型，按需要引入数据库，不提前把所有配置改成数据库 |

## 建议依赖方向

UI → 页面ViewModel → 应用服务 → 领域状态/规则；协议/UDP、文件/日志、地图平台实现通过接口供应用服务调用。生成Java消息仅停留在协议适配边界。

暂不拆成大量Gradle模块。先完成包内职责与接口分离，生成协议可作为首个独立边界；只有依赖隔离或构建收益明确后再做多模块化。

## 验证和实施顺序

1. 固化安装产物身份、分支差异及当前需求版本，消除“改A分支、运行B版本”的问题。
2. 补齐直接UDP迁移合同与协议/固定SITL故障回归，保持原UI工作树完整。
3. 将会话/编解码/遥测映射拆为可独立测试的实现，单一快照与纯时钟测试同步开展。
4. 精简Application与ViewModel职责，拆地图/UI文件，隔离预览数据；在授权集成后验证统一版本。
5. 下一阶段再建设任务/规划/上传/执行持久化，不在架构整理中提前实现飞行行为。

旧UI基线27项测试与直接UDP36项测试、当前实际SITL心跳/位置探针分别记录在07及迁移worktree进度；不是“新UI+新通信”集成版本通过证据。完整健康、频率事务、故障/目标硬件验证仍未完成。接收签名但不验签是用户已确认的当前策略，本轮不自行更改。

## 第一版落地状态

用户授权后，独立通信分支已实现UdpTransport、TelemetryMapper、ConnectionPolicy/时钟注入、ConnectionSnapshot和构建身份日志/源码指纹；40项单测、构建/Lint及无UI回环/当前SITL基础连接通过。旧Flow仍保留兼容，上层消费迁移、Application精简、UI拆分及完整健康/频率契约尚未完成；不以本次第一版替代全部优化验收。原UI工作区未合入通信改动。
