package com.fairland.fgroundcontrol.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fairland.fgroundcontrol.FoundationViewModel
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.mission.SavedFieldTask
import com.fairland.fgroundcontrol.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 只读取本地保存事实，不把规划结果当已加载/执行任务。 */
@Composable
internal fun TaskListSidebar(model: FoundationViewModel,scale: Float,unsupported: ()->Unit,expanded: Boolean,onClose: ()->Unit,modifier: Modifier) {
    val tasks by model.boundary.tasks.collectAsStateWithLifecycle()
    val selected by model.boundary.selectedTask.collectAsStateWithLifecycle()
    val error by model.boundary.taskListError.collectAsStateWithLifecycle()
    val editor by model.boundary.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var byDistance by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var incomplete by remember { mutableStateOf(false) }
    val readout by model.readout.collectAsStateWithLifecycle()
    val origin=if(readout.vehicle.quality("position",readout.nowMs)==com.fairland.fgroundcontrol.telemetry.Quality.VALID)
        readout.vehicle.readings["position"]?.values?.let { values -> runCatching { com.fairland.fgroundcontrol.mission.BoundaryPoint(values[0]!!,values[1]!!) }.getOrNull() } else null
    LaunchedEffect(origin==null) { if(origin==null) byDistance=false }
    var rename by remember { mutableStateOf<SavedFieldTask?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SavedFieldTask?>(null) }
    LaunchedEffect(expanded) { if(expanded) model.boundary.refreshTasks() }
    val visible=remember(tasks,query,byDistance,origin,incomplete) {
        tasks.filter { !incomplete && it.draft.name.orEmpty().contains(query,ignoreCase=true) }.let { list ->
            if(byDistance && origin!=null) list.sortedWith(compareBy<SavedFieldTask> { com.fairland.fgroundcontrol.mission.TaskOrdering.distance(origin,it.draft.points.firstOrNull()) }.thenBy { it.id }) else list.sortedWith(compareByDescending<SavedFieldTask> { it.modifiedMs }.thenBy { it.id })
        }
    }
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        if(expanded) Surface(Modifier.width(AppLayout.boundaryPanelWidth*scale).fillMaxHeight(),shape=RoundedCornerShape(AppRadius.panel*scale),color=AppColors.surfaceAlternative) {
            Column {
                SidebarTabs(incomplete,{incomplete=it},scale,R.string.task_list_tasks,R.string.task_list_incomplete)
                Row(Modifier.fillMaxWidth().background(AppColors.surface).padding(AppSpace.xs*scale),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                    Box(Modifier.size(AppLayout.planningTabHeight*scale).border(AppLayout.planningBorder*scale,AppColors.outline,RoundedCornerShape(AppRadius.card*scale))
                        .clickable(role=Role.Button,onClickLabel=stringResource(R.string.task_list_search)) {searchOpen=true},contentAlignment=Alignment.Center) {
                        Image(painterResource(R.drawable.task_search),stringResource(R.string.task_list_search),Modifier.size(AppLayout.iconAction*scale))
                    }
                    Box(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().height(AppLayout.planningTabHeight*scale).border(AppLayout.planningBorder*scale,AppColors.outline,RoundedCornerShape(AppRadius.card*scale))
                            .clickable(role=Role.Button) {sortOpen=true}.padding(horizontal=AppSpace.md*scale),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                            Text(stringResource(if(byDistance) R.string.task_list_distance else R.string.task_list_time),style=AppType.planningHeader.scaled(scale),color=AppColors.textPrimary,modifier=Modifier.weight(1f),maxLines=1,softWrap=false)
                            Image(painterResource(R.drawable.planning_arrow_right),null,Modifier.size(AppLayout.planningNavigationIcon*scale).graphicsLayer {rotationZ=if(sortOpen) 270f else 90f})
                        }
                        DropdownMenu(expanded=sortOpen,onDismissRequest={sortOpen=false},
                            modifier=Modifier.width((AppLayout.boundaryPanelWidth-AppLayout.planningTabHeight-AppSpace.lg)*scale),containerColor=AppColors.surface) {
                            listOf(false to R.string.task_list_time,true to R.string.task_list_distance).forEach { (distance,label) ->
                                val enabled=!distance || origin!=null
                                val chosen=byDistance==distance
                                Row(Modifier.fillMaxWidth().height(AppLayout.planningTabHeight*scale)
                                    .background(if(chosen) AppColors.creatorSelected else AppColors.surface)
                                    .clickable(enabled=enabled,role=Role.Button) {byDistance=distance;sortOpen=false}
                                    .padding(horizontal=AppSpace.md*scale),verticalAlignment=Alignment.CenterVertically) {
                                    Text(stringResource(label),style=AppType.planningHeader.scaled(scale),color=if(!enabled) AppColors.buttonDisabledText else if(chosen) AppColors.active else AppColors.textPrimary)
                                }
                            }
                            if(origin==null) Text(stringResource(R.string.task_list_distance_missing),style=AppType.headerAction.scaled(scale),color=AppColors.planningUnit,modifier=Modifier.padding(AppSpace.xs*scale))
                        }
                    }
                }
                if(byDistance && origin==null) Text(stringResource(R.string.task_list_distance_missing),style=AppType.headerAction.scaled(scale),color=AppColors.planningUnit)
                if(error) TextButton(onClick={model.boundary.refreshTasks()}) {Text(stringResource(R.string.task_list_retry),style=AppType.headerAction.scaled(scale),color=AppColors.error)}
                LazyColumn(Modifier.weight(1f).padding(AppSpace.xxs*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                    if(visible.isEmpty()) item {Text(stringResource(if(incomplete) R.string.task_list_incomplete_missing else R.string.task_list_empty),style=AppType.headerAction.scaled(scale),modifier=Modifier.padding(AppSpace.md*scale))}
                    items(visible,key={it.id}) { task ->
                        val active=selected?.id==task.id
                        Surface(Modifier.fillMaxWidth().border((if(active) AppLayout.taskSelectedBorderWidth else AppLayout.planningBorder)*scale,if(active) AppColors.taskSelectedBorder else AppColors.outline,RoundedCornerShape(AppRadius.card*scale))
                            .clickable(interactionSource=remember(task.id) { MutableInteractionSource() },indication=null,role=Role.Button) {model.boundary.selectTask(task)},shape=RoundedCornerShape(AppRadius.card*scale),color=AppColors.surface) {
                            Column {
                                Column(Modifier.padding(AppSpace.sm*scale),verticalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(task.draft.name.orEmpty(),style=AppType.planningHeader.scaled(scale),color=AppColors.textPrimary,modifier=Modifier.weight(1f))
                                        Image(painterResource(R.drawable.task_type_field),null,Modifier.size(AppLayout.iconControl*scale))
                                    }
                                    task.draft.points.firstOrNull()?.let { point ->
                                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppSpace.xs*scale)) {
                                            Text(task.draft.address ?: String.format(Locale.getDefault(),"%.6f, %.6f",point.latitude,point.longitude),style=AppType.headerAction.scaled(scale),color=AppColors.planningUnit,modifier=Modifier.weight(1f))
                                            Image(painterResource(R.drawable.task_selected_location),null,Modifier.size(AppLayout.iconControl*scale))
                                        }
                                    }
                                    val area=remember(task.draft.points) { runCatching {
                                        val points=com.fairland.fgroundcontrol.mission.FieldBoundaryValidation.projectAndValidate(task.draft.points)
                                        kotlin.math.abs(points.indices.sumOf { i -> val a=points[i];val b=points[(i+1)%points.size];a.east*b.north-b.east*a.north })/20000.0
                                    }.getOrNull() }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                        Text(area?.let { String.format(Locale.getDefault(),"%.2f ha",it) } ?: "—",style=AppType.headerAction.scaled(scale),color=AppColors.textPrimary)
                                        Text(Instant.ofEpochMilli(task.modifiedMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd HH:mm")),style=AppType.headerAction.scaled(scale),color=AppColors.planningSecondary)
                                    }
                                }
                                if(active) Row(Modifier.fillMaxWidth().height(AppLayout.taskCardActionHeight*scale).background(AppColors.surfaceAlternative),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Button) {name=task.draft.name.orEmpty();rename=task},contentAlignment=Alignment.Center) {
                                        Text(stringResource(R.string.task_list_rename),style=AppType.taskCardAction.scaled(scale),color=AppColors.textPrimary)
                                    }
                                    Box(Modifier.width(AppLayout.planningBorder*scale).height(AppLayout.iconAction*scale).background(AppColors.outline))
                                    Box(Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Button) {deleting=task},contentAlignment=Alignment.Center) {
                                        Text(stringResource(R.string.task_list_delete),style=AppType.taskCardAction.scaled(scale),color=AppColors.primaryDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.width(AppLayout.planningDrawerWidth*scale).height(AppLayout.planningDrawerHeight*scale)
            .background(AppColors.dashboardPanel,RoundedCornerShape(topEnd=AppRadius.panel*scale,bottomEnd=AppRadius.panel*scale))
            .clickable(role=Role.Button,onClick=onClose),contentAlignment=Alignment.Center) {
            Image(painterResource(R.drawable.planning_drawer_toggle),stringResource(if(expanded) R.string.planning_collapse else R.string.planning_expand),Modifier.size(AppLayout.planningStepIcon*scale).graphicsLayer {scaleX=if(expanded) -1f else 1f})
        }
    }
    if(searchOpen) AlertDialog(onDismissRequest={searchOpen=false},title={Text(stringResource(R.string.task_list_search))},
        text={OutlinedTextField(query,{query=it},singleLine=true)},
        confirmButton={TextButton(onClick={searchOpen=false}) {Text(stringResource(R.string.planning_start_confirm))}},
        dismissButton={TextButton(onClick={query="";searchOpen=false}) {Text(stringResource(R.string.task_list_clear_search))}})
    rename?.let { task -> AlertDialog(onDismissRequest={rename=null},title={Text(stringResource(R.string.task_list_rename))},
        text={OutlinedTextField(name,{if(it.length<=80) name=it},singleLine=true)},
        confirmButton={TextButton(enabled=name.isNotBlank(),onClick={model.boundary.renameTask(task,name.trim());rename=null}) {Text(stringResource(R.string.planning_start_confirm))}},
        dismissButton={TextButton(onClick={rename=null}) {Text(stringResource(R.string.boundary_cancel))}}) }
    deleting?.let { task -> AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.task_list_delete))},text={Text(task.draft.name.orEmpty())},
        confirmButton={TextButton(onClick={model.boundary.deleteTask(task);deleting=null}) {Text(stringResource(R.string.task_list_delete),color=AppColors.error)}},
        dismissButton={TextButton(onClick={deleting=null}) {Text(stringResource(R.string.boundary_cancel))}}) }
}
