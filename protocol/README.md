# MAVLink生成基线

当前使用pymavlink 2.4.49发行包内common.xml及其standard/minimal包含文件，输入SHA256见manifest.json；生成Java MAVLink2库334文件，命令 `python3 scripts/generate-mavlink-java.py`。生成产物可直接离线构建，不在Android运行时解释XML。XML保留原注释，Java产物不手工修改。

此基线已通过消息往返、CRC损坏、分帧与Android回环UDP测试；尚未完成固定PX4 v1.17.0的dialect版本对应核对，不能据此宣称全部PX4消息与微服务已兼容。当前未接入消息维持缺失，不伪造健康标志。

链路：GCS sysid255/compid190，每秒发送未签名GCS心跳；仅接受PX4 autopilot=12、compid1、非GCS的合法心跳建立目标，固定预设保留；会话锁定同来源/system/component，3秒无心跳清空并重新发现。UDP数据报内支持连续帧；跨数据报残帧丢弃，不混合不同来源。签名消息完整消费后按用户确认接收但不验签；CRC仍按原始帧头/负载和生成库CRC_EXTRA核验，不进行消息认证或签名防重放。发送端仍发送未签名心跳。

当前遥测：HEARTBEAT、GLOBAL_POSITION_INT、ATTITUDE、GPS_RAW_INT、BATTERY_STATUS、SYS_STATUS电池回退、STATUSTEXT。角度rad→deg、位置1e7、毫米→米、cm/s→m/s、向下速度改向上、电压mV→V。未发送解锁/起飞/任务或喷洒命令；尚未接入消息频率事务，依赖飞控已有遥测流。SDK原生探针已停用，默认测试runner为DirectUdpProbe，不能将旧SDK子进程探针作为新链路验收。
