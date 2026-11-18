package com.fairland.fgroundcontrol.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.fairland.fgroundcontrol.ui.theme.*
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.maps.MapConfiguration
import com.fairland.fgroundcontrol.telemetry.*
import java.util.Locale

/** 参考TopStatusBar的主状态/设备指标分组，只显示既有只读状态。 */
@Composable
fun TopStatusBar(readout: Readout, connection: String, simulation: Boolean, map: MapConfiguration,
    onFollow: (Boolean) -> Unit, onLocate: () -> Unit, onSettings: () -> Unit, onTaskMode: (() -> Unit)? = null, unsupported: (() -> Unit)? = null, modifier: Modifier = Modifier, selectedMode: String = "") {
    var showAlerts by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    val vehicle = readout.vehicle
    fun valid(topic: String) = connection == "connected" && vehicle.quality(topic, readout.nowMs) == Quality.VALID
    @Composable fun unavailable(topic: String): String = if (connection != "connected") "—" else when (vehicle.quality(topic, readout.nowMs)) {
        Quality.STALE -> stringResource(R.string.data_stale)
        Quality.INVALID -> stringResource(R.string.data_invalid)
        else -> "—"
    }
    val hasPosition = valid("position")
    val warning = AppColors.warning
    val danger = AppColors.flightDanger
    val white = AppColors.textInverse
    val severity = readout.alerts.any { it.severity == "critical" }
    val connected = connection == "connected" && vehicle.connected
    val statusColor = if (!connected || severity) AppColors.primaryDanger else if (readout.alerts.isNotEmpty()) warning else AppColors.active
    val stateLabel = stringResource(when (connection) {
        "connected" -> R.string.state_connected
        "connecting" -> R.string.state_connecting
        "disconnected" -> R.string.state_disconnected
        else -> R.string.state_connection_error
    })
    val battery = if (valid("battery")) String.format(Locale.ROOT, "%.0f%%", vehicle.readings.getValue("battery").values[0]) else unavailable("battery")
    val voltage = if (valid("voltage")) String.format(Locale.ROOT, "%.1f V", vehicle.readings.getValue("voltage").values[0]) else unavailable("voltage")
    val batteryColor = if (!valid("battery")) warning else when {
        vehicle.readings.getValue("battery").values[0]!! < 10 -> danger
        vehicle.readings.getValue("battery").values[0]!! < 20 -> warning
        else -> AppColors.active
    }
    val primaryLabel = if (!connected) stringResource(R.string.primary_no_vehicle) else stateLabel
    val modeDescription = stringResource(if (selectedMode == "manual") R.string.startup_manual else R.string.startup_field)
    val alertDescription = stringResource(R.string.top_alerts, readout.alerts.size)
    val batteryDescription = stringResource(R.string.top_battery, battery)
    BoxWithConstraints(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout)) {
        val scale = AppLayout.scale(maxWidth, maxHeight)
        val textHeightScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        fun unit(value: androidx.compose.ui.unit.Dp) = value * scale
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.panel))
        Box(Modifier.fillMaxWidth().height(unit(AppLayout.flightBarHeight) * textHeightScale)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = unit(AppRadius.panel), bottomEnd = unit(AppRadius.panel)))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
            Image(painterResource(R.drawable.figma_top_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Row(Modifier.fillMaxSize().padding(horizontal = unit(AppSpace.xs), vertical = unit(AppSpace.xxs)),
                horizontalArrangement = Arrangement.spacedBy(unit(AppLayout.flightGroupGap)), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).fillMaxHeight().clip(shape).background(AppColors.flightGroup).padding(end = unit(AppSpace.xs)),
                    horizontalArrangement = Arrangement.spacedBy(unit(AppLayout.flightControlGap)), verticalAlignment = Alignment.CenterVertically) {
                    if (onTaskMode != null) Box(Modifier.size(unit(AppLayout.flightCell)).clip(shape).background(AppColors.flightControl)
                        .clickable(role = Role.Button, onClick = onTaskMode).semantics { contentDescription = modeDescription }, contentAlignment = Alignment.Center) {
                        Image(painterResource(if (selectedMode == "manual") R.drawable.figma_method_manual_icon else R.drawable.figma_top_mode_field), null,
                            if (selectedMode == "manual") Modifier.requiredSize(unit(AppLayout.manualIconWidth), unit(AppLayout.manualIconHeight))
                            else Modifier.size(unit(AppLayout.iconMode)), contentScale = ContentScale.Fit)
                        Image(painterResource(R.drawable.figma_top_indicator), null,
                            Modifier.align(Alignment.BottomEnd).size(unit(AppLayout.flightIconDrop)))
                    }
                    if (readout.alerts.isNotEmpty()) Box(Modifier.size(unit(AppLayout.flightAlert)).clip(shape)
                        .background(if (readout.alerts.isEmpty()) AppColors.flightGroup else if (severity) danger else warning)
                        .clickable(role = Role.Button) { if (unsupported != null) unsupported() else showAlerts = true }
                        .semantics { contentDescription = alertDescription }, contentAlignment = Alignment.Center) {
                        Text(if (readout.alerts.size > 99) "99+" else readout.alerts.size.toString(), color = white, style = AppType.flightLabel.scaled(scale))
                    }
                    Row(Modifier.weight(1f).height(unit(AppLayout.flightAlert) * textHeightScale).clip(androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.alternative)))
                        .background(Brush.horizontalGradient(colorStops = arrayOf(0f to statusColor.copy(alpha = AppColors.statusGradientStartAlpha), AppColors.statusGradientFadePosition to statusColor.copy(alpha = 0f), 1f to statusColor.copy(alpha = 0f))))
                        .padding(horizontal = unit(AppSpace.xs)), horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.xs)), verticalAlignment = Alignment.CenterVertically) {
                        Text(primaryLabel, Modifier.weight(1f), color = white, style = AppType.display.scaled(scale), maxLines = 1, overflow = TextOverflow.Ellipsis)

                    }
                }
                Row(Modifier.fillMaxHeight().clip(shape).background(AppColors.flightGroup).padding(horizontal = unit(AppSpace.xs)),
                    horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.xs)), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.xxs))) {
                        Image(painterResource(R.drawable.figma_top_rc), null, Modifier.size(unit(AppLayout.iconAction)))
                        Column(Modifier.width(unit(AppLayout.signalColumnWidth)), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(R.drawable.figma_top_signal_status), null, Modifier.size(unit(AppLayout.signalStatusSize)))
                            Image(painterResource(R.drawable.figma_top_signal_strength), stringResource(R.string.top_signal_strength), Modifier.size(unit(AppLayout.iconAction)))
                        }
                    }
                    Row(Modifier.padding(horizontal = unit(AppSpace.xxs)), horizontalArrangement = Arrangement.spacedBy(unit(AppLayout.flightMetricGap)), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.figma_top_gnss), "GNSS", Modifier.size(unit(AppLayout.iconAction)), colorFilter = ColorFilter.tint(AppColors.textInverse))
                        Column(Modifier.width(unit(AppLayout.flightFixWidth)), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (valid("gps")) vehicle.readings.getValue("gps").values[0]!!.toInt().toString() else unavailable("gps"),
                                color = if (valid("gps")) AppColors.active else warning,
                                style = if (valid("gps")) AppType.numberSmall.scaled(scale) else AppType.headerAction.scaled(scale), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (valid("gps")) vehicle.readings.getValue("gps").text.orEmpty() else "GNSS", color = if (valid("gps")) AppColors.active else warning,
                                style = if (valid("gps")) AppType.numberValue.scaled(scale) else AppType.headerAction.scaled(scale), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.padding(horizontal = unit(AppSpace.xxs)), horizontalArrangement = Arrangement.spacedBy(unit(AppLayout.flightMetricGap)), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(unit(AppLayout.iconAction)), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.figma_top_battery), batteryDescription,
                                Modifier.requiredSize(unit(AppLayout.iconAction), unit(AppLayout.flightBatteryHeight)),
                                colorFilter = ColorFilter.tint(AppColors.textInverse))
                        }
                        Column(Modifier.width(unit(if (valid("battery") || valid("voltage")) AppLayout.flightNumberWidth else AppLayout.flightMissingWidth))) {
                            Text(voltage, color = white, style = if (valid("voltage")) AppType.numberSmall.scaled(scale) else AppType.headerAction.scaled(scale), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (valid("battery")) String.format(Locale.ROOT, "%.0f", vehicle.readings.getValue("battery").values[0]) else unavailable("battery"),
                                    color = batteryColor, style = if (valid("battery")) AppType.numberValue.scaled(scale) else AppType.headerAction.scaled(scale), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                if (valid("battery")) Image(painterResource(R.drawable.figma_top_percent), null,
                                    Modifier.size(unit(AppLayout.percentWidth), unit(AppLayout.percentHeight)), colorFilter = ColorFilter.tint(batteryColor))
                            }
                        }
                    }
                }
                DemoMetric(R.drawable.figma_top_battery, FlightPreview.secondBattery, scale)
                DemoMetric(R.drawable.figma_top_tank, FlightPreview.tank, scale)
                Box {
                    FlightBarButton(R.drawable.figma_top_map, stringResource(R.string.map_title), scale) {
                        if (unsupported != null) unsupported() else showMap = true
                    }
                    DropdownMenu(expanded = showMap, onDismissRequest = { showMap = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.top_locate)) }, enabled = hasPosition,
                            onClick = { onLocate(); showMap = false })
                        DropdownMenuItem(text = { Text(stringResource(if (map.follow) R.string.top_stop_follow else R.string.top_start_follow)) }, enabled = hasPosition || map.follow,
                            onClick = { onFollow(!map.follow); showMap = false })
                    }
                }
                FlightBarButton(R.drawable.figma_top_settings, stringResource(R.string.top_settings), scale, onSettings)
            }
        }
    }
    if (showAlerts) AlertDialog(onDismissRequest = { showAlerts = false }, title = { Text(stringResource(R.string.health_title)) },
        text = { Column {
            if (readout.alerts.isEmpty()) Text(stringResource(R.string.health_clear))
            readout.alerts.forEach { alert -> Text(stringResource(when (alert.id) {
                "link" -> R.string.alert_link; "telemetry" -> R.string.alert_telemetry; "battery" -> R.string.alert_battery; "gps" -> R.string.alert_gps; else -> R.string.alert_health
            }) + " · " + stringResource(if (alert.severity == "critical") R.string.alert_critical else R.string.alert_warning)) }
        } }, confirmButton = { TextButton(onClick = { showAlerts = false }) { Text(stringResource(R.string.top_close)) } })
}

@Composable
private fun FlightBarButton(icon: Int, label: String, scale: Float, action: () -> Unit) {
    Box(Modifier.size(AppLayout.flightCell * scale).clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.panel * scale))
        .background(AppColors.flightControl).clickable(role = Role.Button, onClick = action), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), label, Modifier.size(AppLayout.iconMode * scale))
    }
}

/** 仅UI预览，不能作为遥测、健康判定或日志输入。 */
private object FlightPreview {
    const val secondBattery = "40%"
    const val tank = "120.5 L"
}

@Composable
private fun DemoMetric(icon: Int, value: String, scale: Float) {
    Column(Modifier.width(AppLayout.flightPreviewWidth * scale), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.xxs * scale)) {
            Image(painterResource(icon), null, Modifier.size(AppLayout.iconAction * scale))
            Text(value, color = AppColors.textInverse, style = AppType.numberSmall.scaled(scale), maxLines = 1)
        }
    }
}
