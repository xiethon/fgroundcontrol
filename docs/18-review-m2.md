# M2 全量 Review（2026-11-17）

## 结论与范围

按rules/review.md核对main 490d454至feat/m2-readonly当前工作区的全部阶段改动，包括未跟踪的maps/telemetry实现、单测、稳定版准备脚本及文档；不是仅审查token配置。用户已明确验收此前交付版本通过，保留此结论；本轮地图修复产生的差异仍待简短复验。不提交、合并或推进M3。

核对01需求、04验收、05确认、06任务、08协议、17证据；检查连接/IPC与SDK订阅、UI生命周期、地图源与离线包、健康/轨迹/日志、构建配置及异常路径。源码无规划/上传/解锁/飞行动作新增；频率设置仅为遥测请求，飞行语义未变。

## 有依据的问题与修复

| 严重度 | 位置、触发与影响 | 修复及证据 |
| --- | --- | --- |
| P2 | TelemetryMonitor：存在遥测异常后断链，旧告警消失被记resolved，缺少真实恢复证据 | HealthRules统一判定解除依据；断链/过期记suspended_data_unavailable；新增单测覆盖3秒边界及真实恢复 |
| P2 | TelemetryMonitor：调度停顿期间未观察到stale，恢复时两次位置间隔>3秒仍连线，虚构缺失段轨迹 | 按原始接收时间差主动断段，新增3000/3001ms断言；无飞行操作变化 |
| P2 | MapRepository：离线文件指针与选中源分两条记录提交；第二次写入失败可导致内存与重开配置不一致 | 原子map-selection记录同时保存provider/offlineFile；成功提交后再更新内存；兼容原map-provider/offline-file；失败删除新包、保留旧选择。设备故障注入验证选择和导入失败后页面/磁盘/重开一致 |
| P2 | ReadOnlyPanel：旧源失败事件使用当前configuration归属，新源可能误显示错误 | MapView按revision隔离并销毁旧代际，保留切源时相机位置，销毁后回调忽略；仓库拒绝旧revision，设备断言旧失败不能覆盖新ready。真实MapView切源仍由用户复验 |
| P2 | ReadOnlyPanel：仅监听地图整体加载失败，独立瓦片或渲染错误未明确提示 | 按固定13.7.1实际API补TileOperation.Error及渲染失败监听，失败锁定到当前revision；重选地图源可重试。编译通过，实际错误提示待用户复验 |
| P2 | prepare-px4-stable.sh：原diff只检查未暂存改动，已暂存修改可绕过固定源码检查 | 改为diff HEAD同时覆盖暂存/未暂存；sh -n通过，不更改用户飞控源码 |

MapLibre错误接口依据：固定13.7.1 AAR的MapView/TileOperation实际签名；[官方同版本原生回调](https://raw.githubusercontent.com/maplibre/maplibre-native/android-v13.7.1/platform/android/MapLibreAndroid/src/cpp/native_map_view.cpp)。未把“样式已就绪”当成影像已验收。

## 回归结果

- assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug成功；27 tests，0 failures/errors/skipped。Lint剩余提示不视为功能验证失败，也不据此声称目标硬件通过。
- API35 ARM64 Tablet emulator-5554/PAGE_SIZE4096无UI设备探针INSTRUMENTATION_CODE -1：无飞控启动/切换/停止/端口释放、SDK子进程崩溃恢复、前台服务、存储/三类日志/ZIP、当前SITL连接与位置/电量、地图默认密钥/保存覆盖、源切换、旧revision拒绝、离线包替换/坏包回退与原子写盘故障通过。
- 当前SITL仍是v1.17.0-alpha1-1219-g30bbd6ecd4-dirty，只作开发环境回归。官方固定v1.17.0及16KiB运行证据沿用17，不冒称本轮重跑。最终APK的10个64位库ELF及ZIP16KiB对齐复核通过。
- 本地token文件未被Git跟踪，候选提交文件未发现真实token；不输出token，不对供应商发测试请求。文档引用、脚本语法及diff空白检查通过。
- 原始输出：/private/tmp/fgc-m2-review-build.log、/private/tmp/fgc-m2-review-device.txt；设备探针不创建Activity/MapView，无截图或界面自动化。

## 验收与源码收尾条件

本轮未发现已确认未修复的P1；上述问题均已修复。用户验收通过作为用户证据记录，不扩写其设备、区域、时长或逐项实测细节。

新增待用户复验：快速切换地图源后等待旧请求结束，确认新源状态/相机不被旧源影响；使用错误token或断网触发瓦片失败，应有错误提示，恢复网络并重选源后可重试；导入/切源后重开保留选择。无需重做此前全部UI验收。

仍无AI实测证据：全字段与飞控逐值对照、受控丢包/延迟/飞控重启、目标遥控器/Android9及长稳性能。此类限制不因用户一句验收通过而伪造结果；保留为未验证。当前源码收尾条件尚未满足：Review修复的地图差异待复验，T06上述协议/故障证据仍缺；T07保留待用户验证。不进入下一阶段，不提交或合并。

## 补验与阻塞解除

用户已明确回复Review地图差异“以上差异已复验通过”。字段逐值及50%遥测包丢失/1秒延迟/重复逆序/过期/断链/测试源重启回归已通过；另补固定官方PX4 v1.17.0实际SIH关闭/重新启动与遥测失效/新鲜重连验证。详细输入/结果及测试夹具修正见17最新段落。新增验证代码已自查，无产品飞行语义变化。

此前阻塞来源已补齐，不再沿用“字段证据缺失/地图差异待验收”结论。当前M2具备本地源码收尾条件；用户授权提交、验收后合入本地main和清理阶段分支。目标硬件/Android9/长稳仍未验证，保留为综合/发布验收限制，不写成已通过。Git结果见07，不推进M3。
