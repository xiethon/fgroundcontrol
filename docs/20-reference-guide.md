# 设计与功能参考索引

AI 开始任务先读当前进度与确认记录；涉及界面或功能移植时，再按本索引定位参考。参考资料用于落实本阶段已确认需求，不自动扩大范围。

## 界面设计：Figma

文件：fgroundcontrol，file key `ktOZrqqgi1O4DQlVo4k64X`。以 `10 Screens` 页为当前已知设计入口，不以参考项目运行画面替代设计稿。

| 内容 | 节点 | 入口/用途 |
| --- | --- | --- |
| 10 Screens | `12:4` | [页面总览](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=12-4) |
| 主页区域 | `7333:19441` | [主页设计集合](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=7333-19441) |
| Home-Connected | `471:18513` | [已核对首页基准](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=471-18513)，1280×800 |
| 状态栏区域 | `7353:18616` | [飞行状态栏设计集合](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=7353-18616) |
| FlightInstrumentPanel | `4511:30723` | [飞行仪表盘](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=4511-30723)，底部仪表/数据条 |
| TopStatusBar | `254:7522` | [已核对飞行顶部栏](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=254-7522)，1280×72 |
| 选择作业方式区域 | `7333:19443` | [作业方式设计集合](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=7333-19443)，包含不同选择/语言/禁用示例 |
| Flight/TaskType | `6347:70785` | [已核对作业方式基准](https://www.figma.com/design/ktOZrqqgi1O4DQlVo4k64X/fgroundcontrol?node-id=6347-70785)，1280×800，大田彩色示例 |

- 打开 Figma 使用内置浏览器；读取尺寸、组件、变量和资源优先使用 Figma 工具，遵循适用的设计转代码 skill。不要修改设计文件，除非用户另行要求。
- 实现前核对目标节点的最新内容与变体，记录所用节点。其他页面、飞行顶栏或尚未索引的组件按需从总览查找，不猜测节点、尺寸或状态。
- 布局、颜色、字体、字重、字号、行高、间距、圆角和图标以目标设计为视觉依据；统一使用[样式 token](19-ui-tokens.md)，不要在页面重新硬编码。当前已接入Noto Sans SC 400/500、120%行高、零字距；飞行顶部栏数值另用设计的Anton 400及20/26行高；设计后续不同取值先核对再更新token。
- 使用设计提供的独立资源，不以整页截图充当界面。下载资源需本地化并记录节点/来源/哈希；不提交临时下载URL、密钥或令牌。既有资源记录见 `assets/design/startup/figma-home-assets.json`、`assets/design/methods/assets.json`、`assets/design/fonts/noto-sans-sc.json`、`assets/design/flight-topbar/assets.json`、`assets/design/fonts/anton.json`。
- 设计里的连接成功、升级数量、任务状态等是示例，不能据此伪造运行数据；用真实数据或已确认的未支持提示。设计稿不构成下发飞行指令的授权。

## 功能实现：参考项目

参考根目录：`/Users/xiethon/fairland/fgroundcontrol`，与当前 Android 仓库 `/Users/xiethon/repo/fgroundcontrol` 区分。已核对参考提交：`2383502523581d0755ebabb948b71df21aae845a`。

以下路径相对于参考根目录，使用前先核对文件及实际版本；根目录变化或文件缺失时说明缺口，不把未经核对的替代实现当作参考。参考仓库只读，不在其中修改或运行可能影响既有飞控/仿真的操作。

| 功能 | 参考文件/目录 |
| --- | --- |
| 启动、首页、选择方式及进入飞行页 | `src/qml/Application.qml`、`src/qml/app/home/HomePage.qml`、`src/qml/app/home/OperationMethodPage.qml` |
| 顶部状态分组与交互 | `src/qml/components/top_status_bar/TopStatusBar.qml`、同目录 `TopStatusPrimaryPanel.qml`、`TopStatusDevicePanel.qml`、`GNSSIndicator.qml`、`MapControlIndicator.qml` |
| 连接预设及UDP链路 | `src/support/settings/settings_defaults.hpp`、`src/core/link/udp_link.cc` |
| 地块与自由航线规划 | `src/core/mission/field/field_mission_planner_service.cc`、`src/core/mission/free_route/free_route_mission_planner_service.cc` |
| 执行与恢复核对入口 | `src/core/mission/execution/mission_execution_controller.cc`、`src/core/mission/mission_manager.cc` |
| 参考原始图标/背景 | `assets/icons/`及页面实际引用的资源路径；核对资源别名映射后使用 |

- 功能流程、规划、执行、恢复及异常路径按需参考该项目，迁移为本项目 Kotlin/Compose、标准MAVLink直连UDP架构，不引入Qt运行依赖。协议仍以[协议索引](08-protocol-index.md)的固定PX4和标准MAVLink为准。
- 提取行为时同时读取调用、状态转换和失败路径，记录参考提交、文件与本项目采用/不采用的差异；不得只照搬按钮处理函数。
- 飞行语义与参数以[已确认决策](05-decisions.md)、[状态机](09-state-machines.md)及已冻结契约为准。[规划契约草案](12-planning-contract.md)未确认部分不能作为实现依据。喷洒协议、自动解锁、乐观完成、跳过无效几何等参考行为不能自动继承。
- UI01当前仅移植启动→作业方式→只读飞行页的导航；大田/手动可进入，其他按钮提示“暂不支持”。参考项目有完整能力不代表本阶段获准实现任务、设备管理或飞行底层。

## 采用与验收

用户最新指令及本项目已确认需求优先。视觉查Figma，功能查参考源码，二者不能互相替代；冲突若影响范围、交互或飞行语义，集中向用户确认后记录，不自行选择未确认行为。

交付记录简述设计节点、参考源码版本/文件、采用差异、构建/回归证据与未验证项。Figma只读查看不等于APP界面验证授权；APP视觉与交互默认由用户验收，AI不启动APP、不截图或自动化APP界面，除非用户明确要求。

飞行仪表盘功能入口：参考根目录下 `src/qml/app/flight/TelemetryPanel.qml`、`src/qml/components/attitude_indicator/`、`src/qml/components/compass_indicator/`；已确认的高度/缺失数据差异见05及07，不照搬AGL命名和缺失补零。

UI02采用差异：仪表盘资源/配色参考4511:30723及5430:26822，最新用户整页图片覆盖旧版上下分层布局，采用横向组合和外置独立按钮；未连接指南针指北、姿态仪默认水平，相对高度缺失为破折号。地图指南针避让顶栏；卫星/电池图标固定白色，RC信号按254:7522使用图形，既有未接入预览不能作为遥测。参考TelemetryPanel.qml及姿态/罗盘组件的动态绘制，不移植飞行命令。视觉尚待最终用户验收。
