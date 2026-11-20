# UI 样式 token

设计文件、目标节点与功能参考边界见[设计与功能参考索引](20-reference-guide.md)。

统一入口：[AppTokens.kt](../app/src/main/java/com/fairland/fgroundcontrol/ui/theme/AppTokens.kt)。MainActivity使用GroundControlTheme，业务页面不再定义颜色常量、dp/sp字面量或独立字体族。

| 分类 | 入口 | 用途 |
| --- | --- | --- |
| 语义颜色 | AppColors | 背景、文字、激活、连接/异常、透明底板、地图覆盖物 |
| 间距 | AppSpace | xxs/compact/xs/sm/md/lg/xl，共用间距阶梯 |
| 圆角 | AppRadius | compact/card/alternative/panel/dialog，同时映射Material Shapes |
| 排版 | AppType | Noto Sans SC 400/500、120%行高；顶部栏数值Anton 400、20/26行高；零字距；Material Typography复用同源定义 |
| 布局 | AppLayout | 设计基准、组件尺寸、图标、阴影、模糊、图片裁切、遮罩、提示时长 |

首页及作业方式页依据Figma 1280×800设计基准，由AppLayout.scale统一计算比例，Dp与TextStyle.scaled应用相同比例；sp仍遵循系统字体缩放。TopBar按1280×800设计基准使用同一比例入口，外高72、分组64、状态48；系统字体放大时相应增高栏和状态区。新增页面复用现有语义；确需新值时在统一入口命名并说明用途，不在页面写新的数值常量。

图像/SVG原始内容及矢量路径坐标属于静态资源，不改写为间距token。地图缩放级别、飞控/电量阈值、遥测单位与业务时间不属于视觉样式，本次不更改。地图自身底图样式由供应商提供，应用生成的背景/飞机/轨迹颜色则使用AppColors。

本次仅重构既有UI样式来源，不增加主题切换、暗色主题、任务/飞行功能；未声称已同步Figma全部变量。UI视觉与交互仍由用户验收。

## Figma字体对齐

应用内置Google Fonts官方Noto Sans SC可变字体，400与500字重分别绑定Regular/Medium，禁用合成字重，字体来源/固定版本及SHA256记录在[字体清单](../assets/design/fonts/noto-sans-sc.json)，OFL许可证随APK打包于assets/licenses。字体缺失不得退回系统SansSerif冒充对齐。

Anton数值字体的固定来源/版本与SHA256见[Anton清单](../assets/design/fonts/anton.json)，许可证同样随APK打包。

AppType统一设置letterSpacing=0、includeFontPadding=false，行高居中分配且不裁去首尾行高留白，对齐Figma行框语义。首页/作业方式的32/28/24/20设计字号通过既有屏幕比例缩放；sp仍尊重用户系统字体设置，不锁死辅助功能。以系统默认字体缩放核对设计基准；不同Android渲染器与Figma的栅格化差异不能仅凭构建宣称完全一致。

仪表盘数据层级：dashboardLabel 18、dashboardNumber 28（Anton）、dashboardUnit 14；DashboardTokens.metricRowHeight最小72，适配比例沿用AppLayout.scale，单位与数值基线对齐。尺寸为1280×800基准，最新用户参考图优先于旧版上下分层布局。

M3规划面板新增同源token：planningHeader 24 Medium、planningTab 26、planningTitle 28、planningValue 30 Medium；输入高72、标签高56、边框1、面板宽复用boundaryPanelWidth336。间距/圆角/图标尺寸复用既有阶梯，路线色planningRoute #FFE500。取自Figma3909:32999，范围适配见20；未宣称视觉验收通过。

左栏细化：planningHeaderSurface/Secondary/Unit对应半透明浅灰标题底和两级灰文字；planningStepIcon40、planningDrawerWidth56/Height88、planningTabStroke3对应3909:32999/33000。输入72、标签56、单位20、标签28及数值30沿用前述基准；统一/单边复用既有圆角/间距/边框，不在页面硬编码视觉常量。

用户要求侧栏导航箭头放大：planningNavigationIcon=32dp（原24），仅用于侧栏入口与返回箭头，同AppLayout.scale缩放。

用户要求Toast字体小一档：AppType.toast统一14sp，替代默认16sp，继续遵循统一字体、行高和系统字体缩放。

选中任务新增1199:22984同源token：taskSelectedBorder浅绿/宽4、taskSelectedMap绿色、taskCardAction22 Medium/操作高64、taskActionWidth176；地图标注尺寸使用mapSelectedLabel/Check等token，短标签本地Noto字体绘制，长名称省略。

用户尺寸修订（2026-11-20）：地图任务名mapSelectedLabelSize由14增至18，选择标记mapSelectedCheckSize由24增至32；选中/未选中共用，长名称仍按既有最大宽度省略。

用户再次放大（2026-11-20）：mapSelectedLabelSize 18→22，mapSelectedCheckSize 32→40；选中/未选中共用，其他规则不变。

选中态修订（2026-11-20）：任务名与圆框两态共用字号22/尺寸40、比例和bottom锚点；无状态特有offset，共用地块中心算法。

航线端点（2026-11-20）：38×24原始资源尺寸、18字重标注、绿色#00E676终点与既有planningRoute黄色起点集中在AppTokens；透明画布用于将圆心锚定真实端点，图形保持原比例。

地图标签间距修订（2026-11-20）：mapTaskLabelGap=2，与实际字形边界排版配合；整组使用center锚点，选中/未选中一致，覆盖此前bottom锚点说明。

任务地图标签最低显示层级mapTaskLabelMinZoom=16（2026-11-20），两态统一采用，依据参考项目同名阈值。

用户再次放大地图标注（2026-11-20）：mapSelectedLabelSize=26、mapSelectedCheckSize=48，覆盖此前尺寸；选中/未选中共用，居中/间距/可见层级不变。

任务端点放大（2026-11-20）：routeMarkerScale=1.25，仅作用于route-ends-label整组图形及字样；圆心仍锚定路径首末点。

端点尺寸/字重修订（2026-11-20）：routeMarkerScale=1.5，routeMarkerTextWeight=900，routeMarkerTextStroke=0.5；保留文字18基准及箭头/文字独立旋转规则。

端点字形安全区域（2026-11-20）：routeMarkerTextSafeDiameter=16；实际字形包围盒居中，连同描边适配安全圆，保留字重和整体放大规则。

选中地块填充（2026-11-20）：taskSelectedMap=#00D96F，taskSelectedFillOpacity=0.2，依据Figma1199:22984的FieldBoundary填充元数据。
