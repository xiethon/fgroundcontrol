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

应用内置Google Fonts官方Noto Sans SC可变字体，400与500字重分别绑定Regular/Medium，禁用合成字重，字体来源/固定版本及SHA256记录在[字体清单](../artwork/fonts/noto-sans-sc.json)，OFL许可证随APK打包于assets/licenses。字体缺失不得退回系统SansSerif冒充对齐。

Anton数值字体的固定来源/版本与SHA256见[Anton清单](../artwork/fonts/anton.json)，许可证同样随APK打包。

AppType统一设置letterSpacing=0、includeFontPadding=false，行高居中分配且不裁去首尾行高留白，对齐Figma行框语义。首页/作业方式的32/28/24/20设计字号通过既有屏幕比例缩放；sp仍尊重用户系统字体设置，不锁死辅助功能。以系统默认字体缩放核对设计基准；不同Android渲染器与Figma的栅格化差异不能仅凭构建宣称完全一致。
