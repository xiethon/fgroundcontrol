package com.fairland.fgroundcontrol.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.*
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.telemetry.Readout
import com.fairland.fgroundcontrol.ui.theme.*

@Composable
fun FlightDashboard(readout: Readout, scale: Float, unsupported: () -> Unit, modifier: Modifier = Modifier) {
    val state=FlightDashboardState.from(readout)
    fun unit(value: Dp)=value*scale
    Row(modifier.width(unit(DashboardTokens.panelWidth)).height(IntrinsicSize.Max),
        horizontalArrangement=Arrangement.spacedBy(unit(AppSpace.xs)),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(CircleShape).background(AppColors.dashboardPanel).padding(unit(AppSpace.xs)),
            horizontalArrangement=Arrangement.spacedBy(unit(AppSpace.xs)),verticalAlignment=Alignment.CenterVertically) {
            CompassGauge(state,scale)
            Column(verticalArrangement=Arrangement.spacedBy(unit(AppSpace.xxs))) {
                Row(horizontalArrangement=Arrangement.spacedBy(unit(AppSpace.xs))) {
                    Metric(FlightDashboardState.format(state.relativeHeight),"m",stringResource(R.string.dashboard_relative_height),scale)
                    Metric(FlightDashboardState.format(state.speed),"m/s",stringResource(R.string.dashboard_speed),scale)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(unit(AppSpace.xs))) {
                    Metric("—","m",stringResource(R.string.dashboard_distance),scale)
                    Metric("—","L/min",stringResource(R.string.dashboard_flow),scale)
                }
            }
            AttitudeGauge(state,scale)
        }
        Column(
            Modifier.width(unit(DashboardTokens.controlSize)).fillMaxHeight(),
            verticalArrangement=Arrangement.spacedBy(unit(AppSpace.xs), Alignment.CenterVertically),
            horizontalAlignment=Alignment.CenterHorizontally
        ) {
            listOf(R.drawable.dashboard_obstacle_bypass to R.string.dashboard_bypass,R.drawable.dashboard_obstacle_avoidance to R.string.dashboard_avoidance,R.drawable.dashboard_altitude_stabilization to R.string.dashboard_stabilization).forEach { (icon,label) ->
                Box(Modifier.size(unit(DashboardTokens.controlSize)).clip(RoundedCornerShape(unit(AppRadius.alternative))).background(AppColors.dashboardPanel).clickable(role=Role.Button,onClick=unsupported),contentAlignment=Alignment.Center) {
                    Image(painterResource(icon),stringResource(label),Modifier.size(unit(AppLayout.iconControl)))
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, unit: String, label: String, scale: Float) {
    Column(Modifier.width(DashboardTokens.metricWidth*scale).heightIn(min=DashboardTokens.metricRowHeight*scale).padding(horizontal=AppSpace.xs*scale), verticalArrangement=Arrangement.Center) {
        Text(label,color=AppColors.textInverse,style=AppType.dashboardLabel.scaled(scale),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Row(horizontalArrangement=Arrangement.spacedBy(AppSpace.xxs*scale)) {
            Text(value,color=AppColors.textInverse,style=AppType.dashboardNumber.scaled(scale),modifier=Modifier.alignByBaseline())
            if (value != "—") Text(unit,color=AppColors.textInverse,style=AppType.dashboardUnit.scaled(scale),modifier=Modifier.alignByBaseline())
        }

    }
}

@Composable
private fun AttitudeGauge(state: FlightDashboardState, scale: Float) {
    val label=stringResource(R.string.dashboard_attitude)
    val measurer=rememberTextMeasurer()
    // 无数据时仅以水平姿态绘制默认仪表，不回写遥测。
    val pitch=state.pitch ?: 0.0
    val roll=state.roll ?: 0.0
    Box(Modifier.size(DashboardTokens.gaugeSize*scale).clip(CircleShape).semantics { contentDescription=label },contentAlignment=Alignment.Center) {
        run {
            Canvas(Modifier.fillMaxSize()) {
                val shift=pitch.toFloat()*size.height/DashboardTokens.horizonPitchRange
                rotate(-roll.toFloat()) {
                    drawRect(AppColors.horizonSky,topLeft=androidx.compose.ui.geometry.Offset(-size.width*2,-size.height*4),size=androidx.compose.ui.geometry.Size(size.width*5,size.height*8))
                    drawRect(AppColors.horizonGround,topLeft=androidx.compose.ui.geometry.Offset(-size.width*2,size.height/2+shift),size=androidx.compose.ui.geometry.Size(size.width*5,size.height*5))
                    val pitchSize=size.height*DashboardTokens.pitchSizeRatio
                    val slot=pitchSize*DashboardTokens.pitchSpacingRatio+DashboardTokens.stroke.toPx()*scale
                    for (angle in -90..90 step DashboardTokens.pitchStep) {
                        val y=center.y+(pitch.toFloat()-angle)*slot/DashboardTokens.pitchStep
                        val dash=pitchSize*(if (angle%10==0) DashboardTokens.pitchLongRatio else DashboardTokens.pitchShortRatio)
                        drawLine(AppColors.textInverse,androidx.compose.ui.geometry.Offset(center.x-dash/2,y),androidx.compose.ui.geometry.Offset(center.x+dash/2,y),DashboardTokens.stroke.toPx()*scale)
                        if (angle!=0 && angle%10==0 && y in 0f..size.height) {
                            val text=measurer.measure(angle.toString(),AppType.body.copy(fontSize=(DashboardTokens.gaugeSize.value*DashboardTokens.pitchSizeRatio*DashboardTokens.pitchFontRatio*scale).sp))
                            val offset=pitchSize*DashboardTokens.pitchLongRatio*DashboardTokens.pitchLabelOffsetRatio
                            listOf(center.x-offset-text.size.width/2,center.x+offset-text.size.width/2).forEach { x -> drawText(text,AppColors.textInverse,androidx.compose.ui.geometry.Offset(x,y-text.size.height/2)) }
                        }
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                val stroke = DashboardTokens.stroke.toPx() * scale
                drawCircle(AppColors.textInverse, radius - stroke / 2, style = Stroke(stroke))
                rotate(-roll.toFloat()) {
                    for (angle in 0 until 360 step DashboardTokens.rimStep) {
                        val radians = Math.toRadians(angle.toDouble())
                        val length = radius * if (angle % DashboardTokens.rimMajorStep == 0) DashboardTokens.rimMajorRatio else DashboardTokens.rimMinorRatio
                        fun point(distance: Float) = androidx.compose.ui.geometry.Offset(
                            center.x + kotlin.math.sin(radians).toFloat() * distance,
                            center.y - kotlin.math.cos(radians).toFloat() * distance)
                        drawLine(AppColors.textInverse, point(radius - stroke), point(radius - length), stroke)
                    }
                }
            }
            val glyphScale=DashboardTokens.gaugeSize.value/DashboardTokens.attitudeAssetBase*scale
            Row(horizontalArrangement=Arrangement.spacedBy(DashboardTokens.attitudeWingGap*glyphScale),verticalAlignment=Alignment.CenterVertically) {
                Image(painterResource(R.drawable.dashboard_figma_attitude_wing),null,Modifier.size(DashboardTokens.attitudeWingWidth*glyphScale,DashboardTokens.attitudeWingHeight*glyphScale))
                Image(painterResource(R.drawable.dashboard_figma_attitude_center),null,Modifier.size(DashboardTokens.attitudeCenterWidth*glyphScale,DashboardTokens.attitudeCenterHeight*glyphScale))
                Image(painterResource(R.drawable.dashboard_figma_attitude_wing),null,Modifier.size(DashboardTokens.attitudeWingWidth*glyphScale,DashboardTokens.attitudeWingHeight*glyphScale))
            }
        }
    }
}

@Composable
private fun CompassGauge(state: FlightDashboardState, scale: Float) {
    val label=stringResource(R.string.dashboard_heading)
    Box(Modifier.size(DashboardTokens.gaugeSize*scale).clip(CircleShape).semantics { contentDescription=label },contentAlignment=Alignment.Center) {
        Image(painterResource(R.drawable.dashboard_compass_instrument_dial),null,Modifier.fillMaxSize())
        if (state.heading!=null) Image(painterResource(R.drawable.dashboard_compass_instrument_arrow),null,Modifier.fillMaxSize(DashboardTokens.compassArrowRatio).graphicsLayer { rotationZ=state.heading.toFloat() })
        else Text("—",color=AppColors.textInverse,style=AppType.dashboardNumber.scaled(scale))
    }
}
