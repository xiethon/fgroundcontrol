package com.fairland.fgroundcontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fairland.fgroundcontrol.FoundationViewModel
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.maps.MapHomeScreen
import com.fairland.fgroundcontrol.ui.theme.*

@Composable
fun BoundaryFlow(model: FoundationViewModel, editing: Boolean, onEdit: () -> Unit, onExit: () -> Unit, unsupported: () -> Unit) {
    val state by model.boundary.state.collectAsStateWithLifecycle()
    val selectedTask by model.boundary.selectedTask.collectAsStateWithLifecycle()
    val saveVersion by model.boundary.saveVersion.collectAsStateWithLifecycle()
    var parametersReady by remember { mutableStateOf(false) }
    var savingVersion by remember { mutableLongStateOf(0) }
    val center by model.boundaryCenter.collectAsStateWithLifecycle()
    var listExpanded by remember(editing) { mutableStateOf(false) }
    var exit by remember { mutableStateOf(false) }
    var pointSources by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var taskName by remember { mutableStateOf("") }
    var savingName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savingName,saveVersion,state.busy,state.error,state.draft) {
        if(savingName!=null && !state.busy) {
            val success=!state.error && saveVersion>savingVersion
            savingName=null
            if(success) {naming=false;onExit()}
        }
    }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(creating,state.busy,state.error,state.draft) {
        if(creating && !state.busy) { creating=false; if(!state.error && state.draft!=null) onEdit() }
    }
    BackHandler { if(pointSources) pointSources=false else if(editing) exit=true else onExit() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale=AppLayout.scale(maxWidth,maxHeight)
        val pointSourceGapPx=with(androidx.compose.ui.platform.LocalDensity.current) { (AppSpace.sm*scale).roundToPx() }
        MapHomeScreen(model,Modifier.fillMaxSize(),unsupported,selectedMode="field",editingBoundary=editing)
        if(editing) {
            FieldPlanningPanel(state,model.boundary,scale,unsupported,onParametersReady={parametersReady=it},modifier=Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars))
                .padding(top=AppLayout.flightBarHeight*scale,start=AppSpace.xs*scale,bottom=AppSpace.xs*scale)
                .fillMaxHeight())
            Image(painterResource(R.drawable.field_crosshair),stringResource(R.string.boundary_crosshair),Modifier.align(Alignment.Center).size(AppLayout.boundaryCrosshair*scale))
            Column(Modifier.align(Alignment.CenterEnd).windowInsetsPadding(WindowInsets.displayCutout).padding(AppSpace.xs).width(AppLayout.boundaryToolsWidth*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale),color=AppColors.surface) {
                    Column(Modifier.padding(AppSpace.xxs*scale)) {
                        listOf(R.string.boundary_no_spray,R.string.boundary_round_obstacle,R.string.boundary_obstacle,R.string.boundary_point).forEach { label ->
                            val selected=label==R.string.boundary_point
                            Surface(color=if(selected) AppColors.creatorSelected else AppColors.surface,shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale)) {
                                Row(Modifier.fillMaxWidth().height(AppLayout.boundaryToolHeight*scale).clickable(role=Role.Button) { if(!selected) unsupported() }.padding(AppSpace.xs*scale),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                                    Image(painterResource(when(label) { R.string.boundary_point -> R.drawable.figma_point_boundary; R.string.boundary_no_spray -> R.drawable.figma_point_nospray; R.string.boundary_round_obstacle -> R.drawable.figma_point_round; else -> R.drawable.figma_point_obstacle }),null,Modifier.size(AppLayout.iconControl*scale))
                                    Text(stringResource(label),style=AppType.body.scaled(scale),color=if(selected) AppColors.active else AppColors.textPrimary)
                                }
                            }
                        }
                    }
                }
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale),color=AppColors.surface) {
                    Row(Modifier.fillMaxWidth().height(AppLayout.boundaryToolHeight*scale)) {
                        Box(Modifier.width(AppLayout.creatorLeadingWidth*scale).fillMaxHeight().background(AppColors.surfaceAlternative).clickable(role=Role.Button) {pointSources=!pointSources},contentAlignment=Alignment.Center) {
                            Image(painterResource(R.drawable.field_crosshair),stringResource(R.string.boundary_crosshair),Modifier.size(AppLayout.iconAction*scale),colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(AppColors.pointSourceIcon))
                            Image(painterResource(R.drawable.field_task_type_expand),stringResource(R.string.point_source),Modifier.align(Alignment.BottomStart).padding(AppSpace.xxs*scale).size(AppLayout.creatorExpandSize*scale))
                            Box(Modifier.align(Alignment.CenterStart)) {
                                if(pointSources) androidx.compose.ui.window.Popup(alignment=Alignment.CenterEnd,offset=androidx.compose.ui.unit.IntOffset(-pointSourceGapPx,0),onDismissRequest={pointSources=false},properties=androidx.compose.ui.window.PopupProperties(focusable=false, dismissOnClickOutside=true)) {
                                    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale),color=AppColors.surface) {
                                        Column(Modifier.width((AppLayout.creatorLeadingWidth+AppLayout.creatorActionWidth)*scale).padding(AppSpace.xxs*scale)) {
                                            listOf(R.drawable.field_crosshair to R.string.boundary_crosshair,R.drawable.figma_source_aircraft to R.string.point_aircraft,R.drawable.figma_top_rc to R.string.point_remote).forEach { (icon,label) ->
                                                val selected=label==R.string.boundary_crosshair
                                                Surface(color=if(selected) AppColors.creatorSelected else AppColors.surface,shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale)) {
                                                    Row(Modifier.fillMaxWidth().height(AppLayout.boundaryToolHeight*scale).clickable(role=Role.Button) {pointSources=false;if(!selected) unsupported()}.padding(AppSpace.xxs*scale),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                                                        Image(painterResource(icon),null,Modifier.size(AppLayout.iconAction*scale),colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(if(selected) AppColors.active else AppColors.pointSourceIcon))
                                                        Text(stringResource(label),style=AppType.body.scaled(scale),color=if(selected) AppColors.active else AppColors.textPrimary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val canAdd=state.loaded && !state.busy && !state.error && state.draft?.points.orEmpty().size < 128 && center!=null && center !in state.draft?.points.orEmpty()
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled=canAdd,role=Role.Button) {center?.let(model.boundary::append)},contentAlignment=Alignment.Center) {
                            Text(stringResource(R.string.boundary_add),style=AppType.headerAction.scaled(scale),color=if(canAdd) AppColors.textPrimary else AppColors.buttonDisabledText)
                        }
                    }
                }
            }
            Surface(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars)).padding(AppSpace.xs),shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.panel*scale),color=AppColors.dashboardPanel) {
                Column(Modifier.width(AppLayout.boundaryToolsWidth*scale).padding(AppSpace.xs*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                    listOf(R.drawable.boundary_cancel to R.string.boundary_cancel,R.drawable.boundary_save to R.string.boundary_save).forEach { (icon,label) ->
                        Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale),color=AppColors.surface) {
                            TextButton(enabled=!state.busy && !state.error && (label==R.string.boundary_cancel || parametersReady),onClick={if(label==R.string.boundary_cancel) exit=true else {taskName=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));naming=true}},modifier=Modifier.fillMaxWidth().height(AppLayout.boundaryToolHeight*scale)) {
                                Image(painterResource(icon),null,Modifier.size(AppLayout.iconControl*scale))
                                Spacer(Modifier.width(AppSpace.xs*scale))
                                Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=AppColors.textPrimary)
                            }
                        }
                    }
                }
            }
        } else {
            if(selectedTask==null) MissionCreatorButton(scale,state.loaded && !state.busy && !state.error,
                onCreate={creating=true;model.boundary.create()},
                unsupported=unsupported,modifier=Modifier.align(Alignment.CenterEnd).padding(AppSpace.xs))
            if(selectedTask!=null) Surface(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars)).padding(AppSpace.xs*scale),
                shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.panel*scale),color=AppColors.dashboardPanel) {
                Column(Modifier.width(AppLayout.taskActionWidth*scale).padding(AppSpace.xs*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.md*scale)) {
                    listOf(R.drawable.task_selected_exit to R.string.task_selected_exit,R.drawable.task_selected_edit to R.string.task_selected_edit,R.drawable.task_selected_load to R.string.task_selected_load).forEach { (icon,label) ->
                        Row(Modifier.fillMaxWidth().height(AppLayout.boundaryToolHeight*scale)
                            .background(AppColors.surface,androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale))
                            .clickable(role=Role.Button) {if(label==R.string.task_selected_exit) model.boundary.clearTaskSelection() else unsupported()},
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale,Alignment.CenterHorizontally)) {
                            Image(painterResource(icon),null,Modifier.size(AppLayout.iconControl*scale))
                            Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=AppColors.textPrimary)
                        }
                    }
                }
            }
            TaskListSidebar(model,scale,unsupported,expanded=listExpanded,onClose={listExpanded=!listExpanded},
                modifier=Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars))
                    .padding(top=AppLayout.flightBarHeight*scale,start=AppSpace.xs*scale,bottom=AppSpace.xs*scale).fillMaxHeight())
        }
    }
    if(naming) AlertDialog(onDismissRequest={if(savingName==null) naming=false},
        title={Text(stringResource(R.string.boundary_save_name))},
        text={OutlinedTextField(value=taskName,onValueChange={if(it.length<=80) taskName=it},singleLine=true,enabled=savingName==null,label={Text(stringResource(R.string.boundary_task_name))})},
        confirmButton={TextButton(enabled=parametersReady && taskName.isNotBlank() && savingName==null && !state.busy && !state.error && !state.draft?.points.isNullOrEmpty(),onClick={
            savingVersion=saveVersion
            if(model.boundary.save(taskName.trim())) savingName=taskName.trim()
        }) {Text(stringResource(R.string.boundary_save))}},
        dismissButton={TextButton(enabled=savingName==null,onClick={naming=false}) {Text(stringResource(R.string.boundary_cancel))}})
    if(exit) AlertDialog(onDismissRequest={exit=false},title={Text(stringResource(R.string.boundary_exit))},text={Text(stringResource(R.string.boundary_exit_hint))},confirmButton={TextButton(enabled=!state.busy && !state.error,onClick={exit=false;onExit()}) {Text(stringResource(R.string.boundary_exit))}},dismissButton={TextButton(onClick={exit=false}) {Text(stringResource(R.string.boundary_continue))}})
    if(state.error) AlertDialog(onDismissRequest={},title={Text(stringResource(R.string.boundary_error))},text={Text(stringResource(R.string.boundary_error_hint))},confirmButton={TextButton(onClick=model.boundary::retry) {Text(stringResource(R.string.boundary_retry))}})
}

/** 参考MissionCreatorPanel：左段选择类型，右段创建；本轮仅开放地块。 */
@Composable
private fun MissionCreatorButton(scale: Float, enabled: Boolean, onCreate: () -> Unit, unsupported: () -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    BackHandler(enabled=expanded) {expanded=false}
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xxs*scale)) {
        Box {
        if(expanded) androidx.compose.ui.window.Popup(
            alignment=Alignment.CenterEnd,
            onDismissRequest={expanded=false},
            properties=androidx.compose.ui.window.PopupProperties(focusable=false, dismissOnClickOutside=true)
        ) {
        Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.panel*scale),color=AppColors.surface) {
            Column(Modifier.width((AppLayout.creatorLeadingWidth+AppLayout.creatorActionWidth)*scale).padding(AppSpace.xxs*scale)) {
                listOf(R.drawable.field_task_type_field to R.string.creator_field,R.drawable.field_task_type_free to R.string.creator_free,R.drawable.field_task_type_ab to R.string.creator_ab).forEach { (icon,label) ->
                    Surface(color=if(label==R.string.creator_field) AppColors.creatorSelected else AppColors.surface,shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale)) {
                        Row(Modifier.fillMaxWidth().heightIn(min=AppLayout.creatorHeight*scale).clickable(enabled=enabled,role=Role.Button) { expanded=false;if(label!=R.string.creator_field) unsupported() }.padding(AppSpace.xs*scale),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                            Image(painterResource(icon),null,Modifier.size(AppLayout.iconAction*scale))
                            Text(stringResource(label),style=AppType.headerAction.scaled(scale),color=if(label==R.string.creator_field) AppColors.active else AppColors.textPrimary)
                        }
                    }
                }
            }
        }
        }
        }
        Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.panel*scale),color=AppColors.dashboardPanel) {
            Surface(Modifier.padding(AppSpace.xxs*scale),shape=androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.alternative*scale),color=AppColors.surface) {
                Row(Modifier.height(AppLayout.creatorHeight*scale)) {
                    Box(Modifier.width(AppLayout.creatorLeadingWidth*scale).fillMaxHeight().background(AppColors.surfaceAlternative).clickable(enabled=enabled,role=Role.Button) {expanded=!expanded},contentAlignment=Alignment.Center) {
                        Image(painterResource(R.drawable.field_task_type_field),stringResource(R.string.creator_field),Modifier.size(AppLayout.iconAction*scale))
                        Image(painterResource(R.drawable.field_task_type_expand),stringResource(R.string.creator_types),Modifier.align(Alignment.BottomStart).padding(AppSpace.xxs*scale).size(AppLayout.creatorExpandSize*scale))
                    }
                    Row(Modifier.width(AppLayout.creatorActionWidth*scale).fillMaxHeight().clickable(enabled=enabled,role=Role.Button) {expanded=false;onCreate()},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        Image(painterResource(R.drawable.field_task_plan_indicator),null,Modifier.size(AppLayout.iconControl*scale))
                        Spacer(Modifier.width(AppSpace.xs*scale))
                        Text(stringResource(R.string.creator_plan),style=AppType.headerAction.scaled(scale),color=AppColors.textPrimary)
                    }
                }
            }
        }
    }
}
