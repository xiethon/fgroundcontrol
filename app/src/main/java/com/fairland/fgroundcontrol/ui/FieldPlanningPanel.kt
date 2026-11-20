package com.fairland.fgroundcontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.mission.*
import com.fairland.fgroundcontrol.ui.theme.*
import java.math.BigDecimal
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape

/** Figma3909:32999/33000；折叠只改变布局，参数表单仍保留。 */
@Composable
internal fun FieldPlanningPanel(state: BoundaryEditorState, editor: BoundaryEditor, scale: Float, unsupported: () -> Unit, onParametersReady: (Boolean)->Unit, modifier: Modifier) {
    val parameters = state.draft?.parameters
    val altitude by remember(parameters) { mutableStateOf((parameters?.altitude ?: 4.0).toString()) }
    val speed by remember(parameters) { mutableStateOf((parameters?.speed ?: 5.0).toString()) }
    var spacing by remember(parameters) { mutableStateOf((parameters?.spacing ?: 5.0).toString()) }
    var inset by remember(parameters) { mutableStateOf((parameters?.inset ?: 1.0).toString()) }
    var obstacleMargin by remember(parameters) { mutableStateOf((parameters?.obstacleMargin ?: 1.0).toString()) }
    var advanced by remember { mutableStateOf(false) }
    var startSettings by remember { mutableStateOf(false) }
    var sweepSettings by remember { mutableStateOf(false) }
    var sweepPreview by remember { mutableIntStateOf(0) }
    BackHandler(enabled=startSettings || sweepSettings) { startSettings=false; sweepSettings=false }
    var expanded by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val entered = runCatching { FieldPlanningParameters(altitude.toDouble(),speed.toDouble(),spacing.toDouble(),inset.toDouble(),obstacleMargin.toDouble()) }.getOrNull()
    SideEffect {onParametersReady(entered!=null && entered==parameters)}
    LaunchedEffect(entered,state.loaded,state.draft!=null,state.busy,state.error) {
        if(entered!=null && entered!=state.draft?.parameters && !state.busy && !state.error) {
            kotlinx.coroutines.delay(300)
            editor.configure(entered)
        }
    }
    LaunchedEffect(state.draft?.revision,state.draft?.parameters,state.error) {
        if(state.draft?.parameters!=null && state.draft.plan==null && !state.error &&
            runCatching { FieldBoundaryValidation.projectAndValidate(state.draft.points) }.isSuccess) {
            kotlinx.coroutines.delay(300)
            editor.generate()
        }
    }
    val enabled = state.loaded && !state.busy && !state.error
    Row(modifier, verticalAlignment=Alignment.CenterVertically) {
        if(expanded) Surface(Modifier.width(AppLayout.boundaryPanelWidth*scale).fillMaxHeight(),shape=RoundedCornerShape(AppRadius.panel*scale),color=AppColors.planningHeaderSurface) {
            if(startSettings) StartPointSidebar(scale,onCancel={startSettings=false},unsupported=unsupported)
            else if(sweepSettings) SweepTypeSidebar(scale,sweepPreview,{sweepPreview=it},{sweepSettings=false})
            else Column {
                Text(stringResource(R.string.planning_settings),style=AppType.planningHeader.scaled(scale),color=AppColors.planningSecondary,
                    modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=AppSpace.sm*scale))
                SidebarTabs(advanced,{advanced=it},scale,R.string.planning_basic,R.string.planning_advanced)
                Column(Modifier.weight(1f).fillMaxWidth().background(AppColors.surface).verticalScroll(scroll).padding(AppSpace.md*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.lg*scale)) {
                    if(state.planningError) Text(stringResource(R.string.planning_failed),style=AppType.headerAction.scaled(scale),color=AppColors.error)
                    if(advanced) {
                        AdvancedRouteSettings(scale,unsupported,onSweep={sweepSettings=true})
                    } else {
                        PlanningNumber(R.string.planning_spacing_label,R.string.planning_unit_meter,spacing,{spacing=it},1.5..70.0,"0.5",scale,enabled)
                        PlanningNumber(R.string.planning_margin_label,R.string.planning_unit_meter,inset,{inset=it},0.0..99.0,"0.5",scale,enabled,
                            beforeInput={
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(AppRadius.panel*scale)).background(AppColors.surfaceAlternative).padding(AppSpace.xxs*scale)) {
                                    Box(Modifier.weight(1f).height(AppLayout.planningTabHeight*scale).clip(RoundedCornerShape(AppRadius.alternative*scale)).background(AppColors.surface).border(AppLayout.planningBorder*scale,AppColors.outline,RoundedCornerShape(AppRadius.alternative*scale)),contentAlignment=Alignment.Center) {
                                        Text(stringResource(R.string.planning_overall),style=AppType.planningHeader.scaled(scale),color=AppColors.active)
                                    }
                                    Row(Modifier.weight(1f).height(AppLayout.planningTabHeight*scale).clickable(role=Role.Button,onClick=unsupported),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                                        Text(stringResource(R.string.planning_single),style=AppType.planningHeader.scaled(scale),color=AppColors.textPrimary)
                                        Image(painterResource(R.drawable.planning_arrow_right),null,Modifier.size(AppLayout.planningNavigationIcon*scale))
                                    }
                                }
                            })
                        PlanningNumber(R.string.planning_obstacle_margin,R.string.planning_unit_meter,obstacleMargin,{obstacleMargin=it},1.0..5.0,"0.5",scale,enabled)
                        Row(Modifier.fillMaxWidth().heightIn(min=AppLayout.planningTabHeight*scale).clickable(role=Role.Button) {startSettings=true},verticalAlignment=Alignment.CenterVertically) {
                            Text(stringResource(R.string.planning_start_point),style=AppType.planningTitle.scaled(scale),color=AppColors.planningUnit,modifier=Modifier.weight(1f))
                            Image(painterResource(R.drawable.planning_arrow_right),null,Modifier.size(AppLayout.planningNavigationIcon*scale))
                        }
                    }
                }

            }
        }
        Column(horizontalAlignment=Alignment.Start) {
            Image(painterResource(R.drawable.planning_drawer_corner),null,Modifier.size(AppRadius.panel*scale))
            Box(Modifier.width(AppLayout.planningDrawerWidth*scale).height(AppLayout.planningDrawerHeight*scale)
                .clip(RoundedCornerShape(topEnd=AppRadius.panel*scale,bottomEnd=AppRadius.panel*scale))
                .background(AppColors.dashboardPanel).clickable(role=Role.Button) {expanded=!expanded},contentAlignment=Alignment.Center) {
                Image(painterResource(R.drawable.planning_drawer_toggle),stringResource(if(expanded) R.string.planning_collapse else R.string.planning_expand),
                    Modifier.size(AppLayout.planningStepIcon*scale).graphicsLayer {scaleX=if(expanded) -1f else 1f})
            }
            Image(painterResource(R.drawable.planning_drawer_corner),null,Modifier.size(AppRadius.panel*scale).graphicsLayer {scaleY=-1f})
        }
    }
}

@Composable
private fun PlanningNumber(label: Int,unit: Int,text: String,update: (String)->Unit,range: ClosedFloatingPointRange<Double>,step: String,scale: Float,enabled: Boolean,beforeInput: @Composable ()->Unit = {}) {
    val value=text.toDoubleOrNull()
    val valid=value!=null && value.isFinite() && value in range
    Column(verticalArrangement=Arrangement.spacedBy(AppSpace.md*scale)) {
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(AppSpace.xxs*scale)) {
            Text(stringResource(label),style=AppType.planningTitle.scaled(scale),color=AppColors.textPrimary,modifier=Modifier.weight(1f,fill=false))
            Text(stringResource(unit),style=AppType.headerAction.scaled(scale),color=AppColors.planningUnit)
        }
        beforeInput()
        Row(Modifier.fillMaxWidth().height(AppLayout.planningInputHeight*scale).clip(RoundedCornerShape(AppRadius.panel*scale)).background(AppColors.surfaceAlternative),verticalAlignment=Alignment.CenterVertically) {
            fun adjusted(increase: Boolean): String? = runCatching {
                val current=if(text.isBlank()) BigDecimal.valueOf(range.start) else BigDecimal(text)
                val next=if(text.isBlank()) current else if(increase) current+BigDecimal(step) else current-BigDecimal(step)
                if(next.toDouble() in range) next.stripTrailingZeros().toPlainString() else null
            }.getOrNull()
            listOf(false,true).forEach { plus ->
                if(plus) Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.surface).border(AppLayout.planningBorder*scale,AppColors.outline),contentAlignment=Alignment.Center) {
                    BasicTextField(text,update,enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                        textStyle=AppType.planningValue.scaled(scale).copy(color=if(text.isBlank() || valid) AppColors.active else AppColors.error,textAlign=androidx.compose.ui.text.style.TextAlign.Center),modifier=Modifier.fillMaxWidth())
                }
                Box(Modifier.size(AppLayout.planningInputHeight*scale).clickable(enabled=enabled && adjusted(plus)!=null,role=Role.Button) {
                    adjusted(plus)?.let(update)
                },contentAlignment=Alignment.Center) {
                    Image(painterResource(if(plus) R.drawable.planning_plus else R.drawable.planning_minus),stringResource(if(plus) R.string.planning_increase else R.string.planning_decrease),Modifier.size(AppLayout.planningStepIcon*scale))
                }
            }
        }
        if(text.isNotBlank() && !valid) Text("${range.start}–${range.endInclusive}",style=AppType.headerAction.scaled(scale),color=AppColors.error)
    }
}


/** 仅展示起始点设置流程；尚未实现选边，不写入规划参数。 */
@Composable
private fun StartPointSidebar(scale: Float,onCancel: ()->Unit,unsupported: ()->Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.planning_start_settings),style=AppType.planningHeader.scaled(scale),color=AppColors.planningSecondary,
            modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=AppSpace.sm*scale))
        Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart=AppRadius.panel*scale,topEnd=AppRadius.panel*scale))
            .background(AppColors.surface).padding(AppSpace.md*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.md*scale)) {
            listOf(R.string.planning_start_edge,R.string.planning_start_parallel).forEachIndexed { index,label ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(AppRadius.alternative*scale)).background(AppColors.surfaceAlternative)
                    .clickable(role=Role.Button,onClick=unsupported).padding(AppSpace.sm*scale),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                    Box(Modifier.size(AppLayout.iconControl*scale).background(AppColors.warning,CircleShape),contentAlignment=Alignment.Center) {
                        Text((index+1).toString(),style=AppType.headerAction.scaled(scale),color=AppColors.textInverse)
                    }
                    Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=AppColors.planningSecondary,modifier=Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(AppSpace.xs*scale),verticalAlignment=Alignment.CenterVertically) {
            listOf(R.string.boundary_cancel,R.string.planning_start_confirm).forEachIndexed { index,label ->
                if(index>0) Box(Modifier.width(AppLayout.planningBorder*scale)
                    .height(AppLayout.planningTabHeight*scale-AppSpace.md*scale).background(AppColors.outline))
                Box(Modifier.weight(1f).height(AppLayout.planningTabHeight*scale).clickable(role=Role.Button) {
                    if(label==R.string.boundary_cancel) onCancel() else unsupported()
                },contentAlignment=Alignment.Center) {
                    Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=if(label==R.string.boundary_cancel) AppColors.textPrimary else AppColors.active)
                }
            }
        }
    }
}


/** 用户提供的高级页，仅移植展示，不向算法或飞控写入未确认选项。 */
@Composable
private fun AdvancedRouteSettings(scale: Float,unsupported: ()->Unit,onSweep: ()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(AppSpace.md*scale)) {
        AdvancedRouteEntry(R.string.planning_coverage,scale,onSweep)
        Row(Modifier.fillMaxWidth().height(AppLayout.planningTabHeight*scale).clip(RoundedCornerShape(AppRadius.alternative*scale))
            .background(AppColors.surfaceAlternative).clickable(role=Role.Button,onClick=unsupported),verticalAlignment=Alignment.CenterVertically) {
            listOf(R.string.planning_coverage_current,R.string.planning_route_only).forEach { label ->
                Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=if(label==R.string.planning_route_only) AppColors.active else AppColors.textPrimary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min=AppLayout.planningTabHeight*scale).clickable(role=Role.Button,onClick=unsupported),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.planning_single_spray),style=AppType.planningHeader.scaled(scale),color=AppColors.textPrimary,modifier=Modifier.weight(1f))
            Box(Modifier.width(AppLayout.planningDrawerWidth*scale).height(AppLayout.iconAction*scale)
                .clip(RoundedCornerShape(AppRadius.panel*scale)).background(AppColors.outline).padding(AppSpace.xxs*scale),contentAlignment=Alignment.CenterStart) {
                Box(Modifier.size(AppLayout.iconControl*scale).background(AppColors.surface,CircleShape),contentAlignment=Alignment.Center) {
                    Box(Modifier.width(AppSpace.sm*scale).height(AppLayout.planningBorder*scale).background(AppColors.outline))
                }
            }
        }
    }
}

@Composable
private fun AdvancedRouteEntry(label: Int,scale: Float,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=AppLayout.planningTabHeight*scale).clickable(role=Role.Button,onClick=onClick),verticalAlignment=Alignment.CenterVertically) {
        Text(stringResource(label),style=AppType.planningHeader.scaled(scale),color=AppColors.textPrimary,modifier=Modifier.weight(1f))
        Image(painterResource(R.drawable.planning_arrow_right),null,Modifier.size(AppLayout.planningNavigationIcon*scale))
    }
}


/** 扫边选项为当前侧栏的临时预览，不写入规划输入。 */
@Composable
private fun SweepTypeSidebar(scale: Float,selection: Int,onSelect: (Int)->Unit,onBack: ()->Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min=AppLayout.planningTabHeight*scale).clickable(role=Role.Button,onClick=onBack)
            .padding(horizontal=AppSpace.md*scale),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
            Image(painterResource(R.drawable.planning_arrow_right),null,Modifier.size(AppLayout.planningNavigationIcon*scale).graphicsLayer {scaleX=-1f})
            Text(stringResource(R.string.planning_sweep_title),style=AppType.planningHeader.scaled(scale),color=AppColors.planningSecondary)
        }
        Column(Modifier.weight(1f).fillMaxWidth().background(AppColors.surface).padding(AppSpace.md*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.sm*scale)) {
            listOf(R.string.planning_sweep_none,R.string.planning_sweep_both,R.string.planning_sweep_only).forEachIndexed { index,label ->
                val selected=selection==index
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(AppRadius.alternative*scale))
                    .border(AppLayout.planningBorder*scale,AppColors.outline,RoundedCornerShape(AppRadius.alternative*scale))
                    .clickable(role=Role.RadioButton) {onSelect(index)}.padding(AppSpace.sm*scale),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(AppLayout.iconControl*scale).border(AppLayout.planningBorder*scale,if(selected) AppColors.active else AppColors.outline,CircleShape),contentAlignment=Alignment.Center) {
                        if(selected) Box(Modifier.size(AppSpace.sm*scale).background(AppColors.active,CircleShape))
                    }
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                        Canvas(Modifier.size(AppLayout.iconAction*scale)) {
                            val color=AppColors.active
                            val stroke=androidx.compose.ui.graphics.drawscope.Stroke(width=AppLayout.planningBorder.toPx()*scale)
                            drawRect(color,style=stroke)
                            if(index!=2) for(row in 1..3) drawLine(color,androidx.compose.ui.geometry.Offset(size.width/5,size.height*row/4),androidx.compose.ui.geometry.Offset(size.width*4/5,size.height*row/4),stroke.width)
                            if(index!=0) drawRect(color,topLeft=androidx.compose.ui.geometry.Offset(size.width/6,size.height/6),size=androidx.compose.ui.geometry.Size(size.width*2/3,size.height*2/3),style=stroke)
                        }
                        Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=if(selected) AppColors.active else AppColors.textPrimary)
                    }
                }
            }
        }
    }
}
