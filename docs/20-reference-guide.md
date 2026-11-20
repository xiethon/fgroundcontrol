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

大田边界增量：10.2 Task页997:19952，3909:32939为加点面板参考，1199:23173为边界范围调整（不照搬其参数操作），3084:43960为准星资源；列表入口6815:31066。参考FieldMissionToolbar.qml的CROSS_HAIR/map.center及FieldMissionActionPanel.qml退出流程；仅实现单边界草稿及已确认启动续编，不生成路线或喷洒数据。新增BoundaryFlow/BoundaryEditor/BoundaryDraft与地图草稿图层，未做全量规划界面移植。

## M3单地块本地闭环采用记录

2026-11-19核对Figma3909:32939及内部3909:32999（DrawerRouteSettingsForm）：336宽、顶部标题/56高标签、28标签字/30数值字、72高输入、40加减图标；复用AppTokens并新增同源规划token。只实现基本行距/统一内缩及飞行高度/速度，第二标签改“飞行”；不渲染尚未实现的障碍安全距、单边设置、起点选择。1199:23173经核对是边界范围滑块设计，本轮未移植。静态地图/路线/仪表图片不作为运行内容；加减图标独立导出，原SVG与VectorDrawable路径/填色保持一致，清单assets/design/field-planning/assets.json，不使用临时资源URL。视觉未验收。

本轮功能参考实际HEAD a73f9c6312a5904fe606cecd9850fec3870bd532（与早期参数取值参考2383502区分）：`src/qml/app/mission/edit/field_mission/FieldMissionPropertyPanel.qml`、`src/qml/app/mission/load/MissionLoadPropertyPanel.qml`、`src/core/mission/planner/field/planner.cc`、`src/core/mission/planner/geometry_adapter.cc`及纯算法。采用同原点局部投影、首点回退起点、原始首边方向参考；不引入Qt包装、坐标全局简化函数或喷洒开关。纯C++原文和本地5个缺陷补丁哈希见native/field-planner/manifest.json，应用新增安全桥独立于参考目录。JNI输出距离/坐标转米，面积仅估算覆盖；后台计算/版本隔离/原子草稿由Kotlin实现，不照搬参考Qt状态。源码/设计参考存在与构建通过都不代表视觉或完整M3验收。

当前地图预览采用连续黄色动态折线，不照搬设计静态路线图片或障碍示例；S/E和方向箭头尚未接入，不能声称已完成整张设计稿的视觉移植。内缩分离、分解面积缺失、空扫线或未覆盖分区明确失败，替代参考静默取首分区/跳过的行为；算法版本field-a73f9c6-fgc3，采用补丁见manifest。

左栏最新视觉采用：按用户两图及3909:32999/33000恢复“基础/高级”命名、标签下划线、标题/单位层级、统一/单边行、障碍/起点入口及折叠把手。高级仍承载现有高度/速度；单边、障碍边距和起点仅Toast，障碍值为“—”，不照搬设计假数据。为保留本项目已有应用/生成，固定紧凑底部操作区，正文可滚动，区别于参考无这些按钮的示例。折叠保留表单和地图状态；FGCPropertyPanel.qml用于滚动内容/标题结构参考，未移植规划行为。新增4项Figma资源及转换哈希见assets/design/field-planning/assets.json；渲染根尺寸按40/24/16基准，图标不改路径；字体/颜色/几何在AppTokens统一定义。UI未验收。

标签专项：采用192:4134的选中160宽、56高与3高下划线；192:4092连接圆角在底部分界叠加，不独占整列。切换镜像连接圆角，文字行高一致；UI仍待用户验证。

起始点设置侧栏本轮以用户2026-11-20提供的窄侧栏图片为视觉依据：标题、两条编号提示卡和底部取消/确定。尚未核对其独立Figma节点，不伪称节点一致；只切换展示与取消返回，卡片/确定仍提示未支持，不接入参考FieldMissionStartPointSetPropertyPanel的算法或飞行行为。

高级侧栏最新以用户2026-11-20提供的Route Settings截图为准，覆盖此前高级页放置高度/速度的采用差异：覆盖区域、当前/仅航线、关闭样式单侧喷洒、航线范围、航线分段仅作展示，点击Toast，不引入喷洒控制或未确认规划语义。底层高度/速度参数保留；未宣称已核对截图对应独立Figma节点。

扫边类型侧栏以用户2026-11-20最新截图为依据，入口纠正为“扫边类型”（替代此前“航线范围”译名），三项不扫边/扫边/仅扫边为UI预览。线条图标本轮采用Compose示意，尚未核对独立Figma图标节点，不宣称导出资源一致；未接入任何扫边算法或飞控行为。

高级页术语最新以用户2026-11-20中文截图为准：扫边类型（Boundary Sweep Type）、当前（Current）、不扫边（No Boundary Sweep）、单侧喷洒（Single-Sided Spraying）、航线方向（Route Direction）、航线分割（Route Splitting）。覆盖此前Coverage Area、Route Only、Course Range、Route Division译法及误挂的扫边入口；首行进入扫边子页，方向和分割仍未支持。

高级侧栏最新范围：用户2026-11-20要求移除航线方向/航线分割，覆盖此前截图全量展示这两个入口的采用记录；保留扫边类型和单侧喷洒展示。

任务列表最新采用：Figma6815:31066只读核对确认是收起状态，展开态按用户2026-11-20提供截图实现卡片列表/搜索排序/选中绿色边框与管理入口，尚未核对独立展开组件节点，不宣称全视觉一致。已本地化field_task_type_field和planning_drawer_toggle沿用现有资源；不新增设计整页图片。功能参考a73f9c6下app/mission/view/MissionViewTopPanel.qml与MissionViewItem.qml；名称/坐标/面积/时间来自本地记录，非设计示例。采用时间排序替代未实现的距离排序，坐标替代缺少解析服务的地址；未完成仅未支持提示，没有执行假状态。预览为独立StateFlow，不把选择动作覆盖当前草稿，不移植任务加载/喷洒/飞行行为。

任务列表入口最新采用用户2026-11-20指令：取消文字按钮和单独列表导航，直接在飞行页挂载默认收起的侧栏，仅保留展开把手，原位展开/收起；覆盖此前“作业列表”文字入口的实现。

任务列表最新依用户要求：任务/未完成与基础/高级复用SidebarTabs；搜索仅🔍图标入口，输入在弹窗；排序下拉时间/距离，距离采用有效飞行器到任务首点，过期回退时间。未完成仅显示执行记录未接入，不伪造执行状态。覆盖此前纯时间按钮与未完成不可切换的采用差异。

任务列表搜索资源最新改用参考a73f9c6的assets/icons/pages/flight/search.svg，icons.qrc别名icons/pages/search.svg；32基准、2x渲染，源与运行哈希见field-planning/assets.json。替代emoji。排序控件参考FGCComboBox的内边距/箭头结构，复用AppType.planningHeader24和已有高度/图标token；当前选中、禁用及下拉尺寸统一，实际距离/时间规则不变。

任务类型资源改为参考assets/icons/pages/mission/field_mission.svg（icons.qrc别名icons/pages/field_mission.svg），替代规划入口图标。地址参考a73f9c6下FieldMissionEditorMapLayer.qml、maps/FlightMap.qml、support/location/geo_coding_manager_engine.cc与geo_code_reply.cc及resources_url_config.json：边界首点→天地图逆地理编码→formatted_address，失败才回退经纬度。应用采用后台有界请求/缓存和取消隔离，不复制Qt依赖或有误分隔的URL，不复制参考密钥。用户已明确授权坐标/现有密钥出站到该天地图目的地；真实服务未验证，夹具不能替代视觉/在线验收。列表继续编辑入口按最新要求移除。

地址时机最新以用户2026-11-20修订为准：保存时查询并持久化，展开列表仅补查缺失项；已有地址直接读，不再以卡片可见触发。回写保护任务首点/已有地址/删除与重命名，保留原任务修改时间；仍采用参考天地图首点解析和失败坐标回退。

任务选中态新增已核对节点1199:22984（Drawer-TaskList-Selected），由997:19952元数据定位；此次读取完整设计与截图，覆盖此前“未核对展开选中节点”的缺口。采用卡片浅绿4宽边、64高操作栏/22 Medium字与分隔、地图绿色边界小点/实际任务名/勾选、右下176宽退出/编辑/加载按钮组。5个设计资源见assets/design/task-selected/assets.json。设计示例地址、障碍图形和路线不复制；地图始终使用真实选中记录，动态名称用本地项目字体渲染，不依赖在线glyphs。退出取消选择，编辑/加载只Toast未支持；不移植设计中的任务执行或喷洒语义。相机不自动定位，视觉待用户验收。

任务地图预览选择规则（2026-11-20）：功能依据a73f9c6312a5904fe606cecd9850fec3870bd532的`src/qml/app/mission/view/MissionViewMapLayer.qml`，采用全部任务boundaryEntries与选中任务selectedMissionDisplayModel分层；未选中仅地块边界，选中才显示对应路径。当前仅大田单选，不移植多选、自动相机适配、加载执行；无新增设计资源，沿用现有地图token。构建/单测不代替视觉验收，见07。

未选中地块（2026-11-20）：沿用a73f9c6的MissionViewMapLayer.qml标签/未选中指示器与foundation/semantics/MapVisualRules.qml白色10%填充。复制assets/icons/pages/mission/mission_unselected_indicator.svg，来源及转换哈希见assets/design/task-selected/assets.json；当前轮廓按用户要求调细至token 1，保留已确认的绿色选中态，不采用参考的橙色选中态。名称采用本地字体位图以适配无glyphs底图，不新增在线字形请求。

航线端点（2026-11-20）：Figma1199:22984及176:4186/176:4180，黄色S/绿色E独立SVG，保存至assets/design/task-selected/route_start.svg、route_end.svg，2x运行图及哈希见同目录assets.json。只映射已保存计划首末点；固定屏幕朝向示意，不增加方向箭头、飞行动作或任务执行语义。视觉待用户验证。

端点方向修订（2026-11-20）：覆盖此前固定屏幕朝向示意。沿用Figma起终点原资源，按实际首/末非零段方位旋转；S/E文字独立保持屏幕正向，资源不重画、不修改规划或飞行行为。

选中地块填充（2026-11-20）：Figma1199:22984 / I1199:23046;165:5069的完整上下文、截图与独立SVG样式元数据确认绿色#00D96F/20%填充。运行时动态Polygon采用真实任务坐标，不复制示例轮廓，不照搬参考MapVisualRules的橙色选中填充。
