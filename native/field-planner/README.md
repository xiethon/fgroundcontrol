# 本地地块规划核心

纯C++参考算法，经JNI调用，不包含Qt包装或飞行控制。上游提交a73f9c6312a5904fe606cecd9850fec3870bd532，原文件和5个本地补丁哈希见manifest.json：files表示上游原文，localPatches表示本地采用版本。

CMake接入Android externalNativeBuild；固定NDK28.2.13676358、CMake3.22.1、C++17、静态libc++、16KiB链接参数。Boost1.90归档固定SHA256离线解包，Clipper2_1.5.2源码随项目保存。GPLv3及Boost/Clipper许可证、来源NOTICE随APK打包于licenses/field-planner，源码版权头保留。

## 接口与安全边界

NativeFieldPlanner.plan接收局部米制XY/行距/统一内缩，内部转换毫米，返回错误码/距离/估算覆盖面积及米制点列；面积不是地块并集面积或执行报告。FieldPlanningService绑定输入revision/指纹/算法版本field-a73f9c6-fgc4，接入BoundaryEditor原子boundary-v2草稿及地图预览。UI待用户验收。

planning_bridge统一JNI/主机检查：3–128点、50mm近重复、坐标绝对值与包围盒对角≤5km、复杂度预算(ceil(对角/行距)+1)×点数≤8192、输出≤4096点。超限拒绝，不截断。对实际内缩轮廓逐段作差校验，边界浮点容差0.1mm，越界不提供可用结果。

修复轴向有向直线丢方向、近水平射线奇偶性、垂直事件采样及空分解越界；新增分解面积、空扫线/遗漏分区检查。内缩产生多个分离区域时明确失败，替代原offset静默取首分区，避免遗漏区域仍报成功。

JNI异常分别映射IllegalArgumentException或IllegalStateException。取消不能强制中断C++，后台串行化并丢弃取消/过期结果；输入预算不是硬超时保证。不开放障碍输入、喷洒或飞行命令。

## 回归

主机ASan/CTest通过矩形6行12端点650m、5m半行距/10m行距、反向边、2m内缩626m、旋转/凹L形、3/128点、1.5/70m行距、坏几何/超限/内缩为空与分离内缩拒绝。有效段与边界作差检查；近水平和空邻接有单独断言。

Android API35/arm64-v8a模拟器无Activity实际JNI、领域/编辑器生成→保存重开→改参失效→重算通过；69项JVM单测含迁移、旧结果、写盘失败和重试。四ABI构建/打包、64位ELF及ZIP对齐通过；其他ABI运行、真机、UI与障碍未验证，不能当完整M3验收。具体日志见../../docs/07-progress.md。

主机构建在仓库外目录，配置CMake时加入`-DCMAKE_CXX_FLAGS="-g -fsanitize=address -fno-omit-frame-pointer" -DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address`，构建后执行`ctest --test-dir <build目录> --output-on-failure`。

Android按根README构建主APK，另执行`./gradlew :app:assembleDebugAndroidTest`，安装两APK到开发设备，然后：

```sh
adb -s <开发设备> shell am instrument -w -e nativePlanner true com.fairland.fgroundcontrol.test/com.fairland.fgroundcontrol.DirectUdpProbe
```

不启动Activity，不发送飞行指令；成功需输出native_planner=PASS和INSTRUMENTATION_CODE: -1，不以adb退出码替代验证结果。
