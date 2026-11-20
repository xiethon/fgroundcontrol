package com.fairland.fgroundcontrol.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fairland.fgroundcontrol.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fairland.fgroundcontrol.FoundationViewModel
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.telemetry.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONObject
import java.util.Locale

@Composable
fun ReadOnlyPanel(model: FoundationViewModel, importArchive: () -> Unit, showMap: Boolean = true) {
    val readout by model.readout.collectAsStateWithLifecycle()
    val config by model.maps.collectAsStateWithLifecycle()
    var editingKeys by remember { mutableStateOf(false) }
    if (editingKeys) MapKeyDialog(onDismiss = { editingKeys = false }, onSave = { china, overseas ->
        model.saveMapKeys(china, overseas); editingKeys = false
    })
    Text(stringResource(R.string.telemetry_title), style = MaterialTheme.typography.titleLarge)
    val labels = listOf("position" to R.string.field_position, "altitude" to R.string.field_altitude, "speed" to R.string.field_velocity,
        "attitude" to R.string.field_attitude, "battery" to R.string.field_battery, "voltage" to R.string.field_voltage, "gps" to R.string.field_gps,
        "mode" to R.string.field_mode, "armed" to R.string.field_armed, "health" to R.string.field_health, "status" to R.string.field_status)
    labels.forEach { (topic, label) ->
        val reading = readout.vehicle.readings[topic]
        val quality = readout.vehicle.quality(topic, readout.nowMs)
        val format = when (topic) { "position" -> "%.7f"; "gps", "armed", "health" -> "%.0f"; else -> "%.2f" }
        val values = reading?.values?.joinToString(" / ") { it?.let { value -> String.format(Locale.ROOT, format, value) } ?: "—" }.orEmpty()
        val text = if (reading == null) "—" else values + " " + reading.text.orEmpty()
        Text("${stringResource(label)}: $text · ${stringResource(when (quality) { Quality.MISSING -> R.string.data_missing; Quality.INVALID -> R.string.data_invalid; Quality.STALE -> R.string.data_stale; Quality.VALID -> R.string.data_valid })}")
    }
    Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleMedium)
    if (readout.alerts.isEmpty()) Text(stringResource(R.string.health_clear))
    readout.alerts.forEach { alert ->
        Text(stringResource(when (alert.id) { "link" -> R.string.alert_link; "telemetry" -> R.string.alert_telemetry; "battery" -> R.string.alert_battery; "gps" -> R.string.alert_gps; else -> R.string.alert_health }) + " · " + stringResource(if (alert.severity == "critical") R.string.alert_critical else R.string.alert_warning), color = MaterialTheme.colorScheme.error)
    }
    Text(stringResource(R.string.map_title), style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.compact)) {
        listOf("tianditu" to R.string.map_tianditu, "maptiler" to R.string.map_maptiler, "offline" to R.string.map_offline).forEach { (id, title) ->
            OutlinedButton(enabled = config.status != "importing", onClick = { model.selectMap(id) }) { Text((if (config.provider == id) "✓ " else "") + stringResource(title)) }
        }
    }
    Text(stringResource(when (config.status) { "unconfigured" -> R.string.map_unconfigured; "no_archive" -> R.string.map_no_archive; "loading", "importing" -> R.string.map_loading; "ready" -> R.string.map_ready; else -> R.string.map_error }))
    config.bounds?.let { bounds ->
        if (readout.vehicle.quality("position", readout.nowMs) == Quality.VALID) {
            val values = readout.vehicle.readings.getValue("position").values
            if (values[0]!! !in bounds.south..bounds.north || values[1]!! !in bounds.west..bounds.east) Text(stringResource(R.string.map_outside_coverage), color = MaterialTheme.colorScheme.error)
        }
    }
    if (showMap) VehicleMap(config, readout, model, Modifier.fillMaxWidth().height(AppLayout.mapPreviewHeight))
    Text(config.attribution, style = MaterialTheme.typography.bodySmall)
    Row { Checkbox(checked = config.follow, onCheckedChange = model::followMap); Text(stringResource(R.string.map_follow)) }
    OutlinedButton(enabled = config.status != "importing", onClick = { editingKeys = true }) { Text(stringResource(R.string.map_edit_keys)) }
    OutlinedButton(enabled = config.status != "importing", onClick = importArchive) { Text(stringResource(R.string.map_import_archive)) }
}

@Composable
@android.annotation.SuppressLint("ClickableViewAccessibility")
fun MapHomeScreen(model: FoundationViewModel, modifier: Modifier = Modifier, onSettings: () -> Unit, onTaskMode: (() -> Unit)? = null, unsupported: (() -> Unit)? = null, selectedMode: String = "", editingBoundary: Boolean = false) {
    val readout by model.readout.collectAsStateWithLifecycle()
    val config by model.maps.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val preset by model.preset.collectAsStateWithLifecycle()
    var locateRequest by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val compassTop = with(density) { (AppLayout.flightBarHeight * AppLayout.scale(maxWidth,maxHeight) * fontScale.coerceAtLeast(1f) + AppSpace.xs).roundToPx() } + WindowInsets.displayCutout.getTop(density)
        VehicleMap(config, readout, model, Modifier.fillMaxSize(), locateRequest, compassTop, editingBoundary)
        com.fairland.fgroundcontrol.ui.TopStatusBar(readout, connection, preset.id == "sitl", config,
            onFollow = model::followMap, onLocate = { locateRequest++ }, onSettings = onSettings, onTaskMode = onTaskMode, unsupported = unsupported, selectedMode = selectedMode,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter))
        if (!editingBoundary) com.fairland.fgroundcontrol.ui.FlightDashboard(readout, AppLayout.scale(maxWidth,maxHeight), unsupported ?: {},
            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars)).padding(bottom = com.fairland.fgroundcontrol.ui.theme.AppSpace.xs))
    }
}

@Composable
private fun VehicleMap(config: MapConfiguration, readout: Readout, model: FoundationViewModel, modifier: Modifier, locateRequest: Int = 0, compassTop: Int = 0, editingBoundary: Boolean = false) {
    val camera = remember { mutableStateOf<CameraPosition?>(null) }
    // 视图与样式代际绑定，原生异步失败回调不能归属到新地图源。
    key(config.revision) { RevisionVehicleMap(config, readout, model, camera, modifier, locateRequest, compassTop, editingBoundary) }
}

@Composable
@android.annotation.SuppressLint("ClickableViewAccessibility")
private fun RevisionVehicleMap(config: MapConfiguration, readout: Readout, model: FoundationViewModel, camera: MutableState<CameraPosition?>, modifier: Modifier, locateRequest: Int, compassTop: Int, editingBoundary: Boolean) {
    val boundaryState by model.boundary.state.collectAsStateWithLifecycle()
    val selectedTask by model.boundary.selectedTask.collectAsStateWithLifecycle()
    val tasks by model.boundary.tasks.collectAsStateWithLifecycle()
    LaunchedEffect(model.boundary) { model.boundary.loadTaskPreviews() }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val presenter = remember(context) { MapPresenter(model, locateRequest) }
    val view = remember(context) { MapLibre.getInstance(context); org.maplibre.android.log.Logger.setVerbosity(org.maplibre.android.log.Logger.NONE); MapView(context).apply {
        onCreate(null)
        setOnTouchListener { target, event ->
            target.parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
            false
        }
    } }
    DisposableEffect(view, owner) {
        var started = false; var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { view.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { view.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { if (resumed) view.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { if (started) view.onStop(); started = false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        fun failed() { view.post {
            if (!presenter.destroyed) {
                presenter.resourceFailed = true
                model.mapLoaded(config.style, config.revision, false)
            }
        } }
        view.addOnDidFailLoadingMapListener { failed() }
        view.addOnTileActionListener { operation, _, _, _, _, _, _ -> if (operation == org.maplibre.android.tile.TileOperation.Error) failed() }
        view.addOnRenderErrorListener { failed() }
        view.getMapAsync { if (!presenter.destroyed) { camera.value?.let { saved -> it.cameraPosition = saved }; presenter.attach(it) } }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            camera.value = presenter.cameraPosition()
            presenter.destroyed = true
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }
    AndroidView(factory = { view }, modifier = modifier, update = {
        presenter.editingBoundary = editingBoundary
        presenter.drawTaskBoundaries(if(editingBoundary) emptyList() else tasks.filter { it.id != selectedTask?.id })
        presenter.drawBoundary((if(editingBoundary) boundaryState.draft else selectedTask?.draft)?.points.orEmpty())
        presenter.drawPlan((if(editingBoundary) boundaryState.draft else selectedTask?.draft)?.plan?.points.orEmpty())
        presenter.drawRouteEnds(if(editingBoundary) emptyList() else selectedTask?.draft?.plan?.points.orEmpty())
        presenter.drawSelection(if(editingBoundary) null else selectedTask?.draft?.name,if(editingBoundary) emptyList() else selectedTask?.draft?.points.orEmpty())
        presenter.updateCompassTop(compassTop)
        presenter.update(config, readout, locateRequest)
    })
}

private class MapPresenter(private val model: FoundationViewModel, initialLocateRequest: Int) {
    var destroyed = false
    var resourceFailed = false
    var configuration = MapConfiguration()
    private var latest = Readout()
    private var map: MapLibreMap? = null
    private var applied: String? = null
    private var appliedRevision = -1L
    private var hasStyle = false
    private var styleToken = 0
    private var lastTrack: String? = null
    private var lastPosition = -1L
    private var locateRequest = initialLocateRequest
    private var handledLocateRequest = initialLocateRequest
    private val empty = "{\"type\":\"FeatureCollection\",\"features\":[]}"
    var editingBoundary = false
    private var taskBoundaries = emptyList<com.fairland.fgroundcontrol.mission.SavedFieldTask>()
    private var taskBoundarySource: GeoJsonSource? = null
    fun drawTaskBoundaries(boundaries: List<com.fairland.fgroundcontrol.mission.SavedFieldTask>) {
        val unchanged = taskBoundaries == boundaries
        taskBoundaries = boundaries
        val source = map?.style?.getSourceAs<GeoJsonSource>("task-boundaries") ?: return
        if (unchanged && taskBoundarySource === source) return
        taskBoundarySource = source
        val style=map?.style ?: return
        taskLabelImages.forEach { style.removeImage(it) }
        taskLabelImages.clear()
        val features = boundaries.filter { it.draft.points.size >= 3 }.mapIndexed { index, task ->
            val points=task.draft.points
            val ring = (points + points.first()).joinToString(",") { "[${it.longitude},${it.latitude}]" }
            val image="task-unselected-$index"
            style.addImage(image,createTaskLabel(task.draft.name.orEmpty(),R.drawable.task_unselected_check))
            taskLabelImages.add(image)
            val (latitude,longitude)=com.fairland.fgroundcontrol.mission.taskLabelCenter(points)
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[$ring]]}}," +
                "{\"type\":\"Feature\",\"properties\":{\"image\":\"$image\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]}}"
        }
        source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[${features.joinToString(",")}]}")
    }
    private val taskLabelImages=mutableListOf<String>()
    private var boundaryPoints = emptyList<com.fairland.fgroundcontrol.mission.BoundaryPoint>()
    private var boundarySource: GeoJsonSource? = null
    fun drawBoundary(points: List<com.fairland.fgroundcontrol.mission.BoundaryPoint>) {
        val unchanged = boundaryPoints == points
        boundaryPoints = points
        val source=map?.style?.getSourceAs<GeoJsonSource>("boundary-draft") ?: return
        if (unchanged && boundarySource === source) return
        boundarySource = source
        val features=points.map { p -> "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${p.longitude},${p.latitude}]}}" }.toMutableList()
        if(points.size >= 2) {
            val route=(points + if(points.size>=3) listOf(points.first()) else emptyList()).joinToString(",") { "[${it.longitude},${it.latitude}]" }
            features.add("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$route]}}")
        }
        source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[${features.joinToString(",")}]}")
    }
    private var labelStyle: Style? = null
    private var labelName: String? = null
    private var selectionName: String? = null
    private var selectionPoints = emptyList<com.fairland.fgroundcontrol.mission.BoundaryPoint>()
    fun drawSelection(name: String?,points: List<com.fairland.fgroundcontrol.mission.BoundaryPoint>) {
        selectionName=name;selectionPoints=points
        val style=map?.style ?: return
        val active=name!=null && points.isNotEmpty()
        style.getLayerAs<LineLayer>("boundary-line")?.setProperties(lineColor((if(active) AppColors.taskSelectedMap else AppColors.textInverse).toArgb()),lineWidth(if(active) AppLayout.taskMapBorderWidth else AppLayout.mapTrackWidth))
        style.getLayerAs<org.maplibre.android.style.layers.CircleLayer>("boundary-points")?.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleColor((if(active) AppColors.taskSelectedMap else AppColors.boundaryPoint).toArgb()),
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(if(active) AppLayout.mapSelectedPointRadius else AppLayout.boundaryPointRadius),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(if(active) 0f else AppLayout.boundaryPointStroke))
        style.getSourceAs<GeoJsonSource>("task-selected-fill")?.setGeoJson(
            if(active && points.size>=3) {
                val ring=(points+points.first()).joinToString(",") { "[${it.longitude},${it.latitude}]" }
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[$ring]]}}]}"
            } else empty)
        val source=style.getSourceAs<GeoJsonSource>("task-selection") ?: return
        if(!active) {source.setGeoJson(empty);return}
        // 栅格底图没有glyphs服务；动态名称用项目字体本地绘制，不依赖在线字形。
        if(labelStyle!==style || labelName!=name) {
            style.addImage("task-selected-label",createTaskLabel(name.orEmpty(),R.drawable.task_selected_check))
            labelStyle=style;labelName=name
        }
        val (latitude,longitude)=com.fairland.fgroundcontrol.mission.taskLabelCenter(points)
        source.setGeoJson("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":${org.json.JSONObject.quote(name)}},"geometry":{"type":"Point","coordinates":[$longitude,$latitude]}}]}""")
    }
    private fun createTaskLabel(name: String, indicator: Int): Bitmap {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize=AppLayout.mapSelectedLabelSize/AppLayout.mapSelectedIconScale
                typeface=androidx.core.content.res.ResourcesCompat.getFont(model.getApplication<android.app.Application>(),R.font.noto_sans_sc)
            }
            val text=android.text.TextUtils.ellipsize(name.replace('\n',' ').replace('\r',' '),android.text.TextPaint(paint),AppLayout.mapSelectedLabelMaxWidth/AppLayout.mapSelectedIconScale,android.text.TextUtils.TruncateAt.END).toString()
            val padding=AppLayout.mapSelectedLabelPadding/AppLayout.mapSelectedIconScale
            val bounds=android.graphics.Rect()
            paint.getTextBounds(text,0,text.length,bounds)
            val textHeight=bounds.height().toFloat()
            val gap=AppLayout.mapTaskLabelGap/AppLayout.mapSelectedIconScale
            val checkSize=AppLayout.mapSelectedCheckSize/AppLayout.mapSelectedIconScale
            val width=kotlin.math.ceil(kotlin.math.max(paint.measureText(text)+padding*2,checkSize.toDouble().toFloat())).toInt()
            val height=kotlin.math.ceil(textHeight+padding*2+gap+checkSize).toInt()
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            val x=(width-paint.measureText(text))/2
            val baseline=padding-bounds.top
            paint.style=Paint.Style.STROKE;paint.strokeWidth=padding;paint.color=AppColors.dashboardPanel.toArgb();canvas.drawText(text,x,baseline,paint)
            paint.style=Paint.Style.FILL;paint.color=AppColors.textInverse.toArgb();canvas.drawText(text,x,baseline,paint)
            val check=android.graphics.BitmapFactory.decodeResource(model.getApplication<android.app.Application>().resources,indicator)
            canvas.drawBitmap(check,null,android.graphics.RectF((width-checkSize)/2,height-checkSize,(width+checkSize)/2,height.toFloat()),null)
            return bitmap
    }
    private var endpointPoints=emptyList<com.fairland.fgroundcontrol.mission.BoundaryPoint>()
    private var endpointSource: GeoJsonSource?=null
    fun drawRouteEnds(points: List<com.fairland.fgroundcontrol.mission.BoundaryPoint>) {
        val unchanged=endpointPoints==points
        endpointPoints=points
        val source=map?.style?.getSourceAs<GeoJsonSource>("route-ends") ?: return
        if(unchanged && endpointSource===source) return
        endpointSource=source
        if(points.size<2) {source.setGeoJson(empty);return}
        val features=listOf(true,false).mapNotNull { start ->
            val rotation=com.fairland.fgroundcontrol.mission.routeEndpointRotation(points,start) ?: return@mapNotNull null
            val icon=if(start) "route-start" else "route-end"
            val p=if(start) points.first() else points.last()
            "{\"type\":\"Feature\",\"properties\":{\"icon\":\"$icon\",\"letter\":\"$icon-letter\",\"rotation\":$rotation},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${p.longitude},${p.latitude}]}}"
        }
        source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[${features.joinToString(",")}]}")
    }
    private fun routeMarker(start: Boolean, letterOnly: Boolean = false): Bitmap {
        val scale=AppLayout.mapSelectedIconScale
        val size=AppLayout.routeMarkerCanvas/scale
        val center=size/2
        val bitmap=Bitmap.createBitmap(size.toInt(),size.toInt(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val resources=model.getApplication<android.app.Application>().resources
        val icon=android.graphics.BitmapFactory.decodeResource(resources,if(start) R.drawable.task_route_start else R.drawable.task_route_end)
        val left=center-(if(start) AppLayout.routeStartCenter else AppLayout.routeEndCenter)/scale
        val top=center-AppLayout.routeMarkerHeight/scale/2
        if(!letterOnly) {
        if(start) canvas.drawBitmap(icon,null,android.graphics.RectF(left,top,left+AppLayout.routeMarkerWidth/scale,top+AppLayout.routeMarkerHeight/scale),null)
        else {
            // 终点原SVG为竖向24×38，旋转呈现，不拉伸成横向。
            canvas.save()
            canvas.translate(left+AppLayout.routeMarkerWidth/scale/2,top+AppLayout.routeMarkerHeight/scale/2)
            canvas.rotate(AppLayout.routeEndRotation)
            canvas.drawBitmap(icon,null,android.graphics.RectF(-AppLayout.routeMarkerHeight/scale/2,-AppLayout.routeMarkerWidth/scale/2,AppLayout.routeMarkerHeight/scale/2,AppLayout.routeMarkerWidth/scale/2),null)
            canvas.restore()
        }
        }
        if(letterOnly) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize=AppLayout.routeMarkerText/scale
            val font=androidx.core.content.res.ResourcesCompat.getFont(model.getApplication(),R.font.noto_sans_sc)
            typeface=android.graphics.Typeface.create(font,AppLayout.routeMarkerTextWeight,false)
            style=Paint.Style.FILL_AND_STROKE
            strokeWidth=AppLayout.routeMarkerTextStroke/scale
            color=(if(start) AppColors.planningRoute else AppColors.routeEnd).toArgb()
            textAlign=Paint.Align.CENTER
        }
        val letter=if(start) "S" else "E"
        val bounds=android.graphics.Rect()
        paint.getTextBounds(letter,0,letter.length,bounds)
        // 字形四角也留在圆环内，避免粗笔画与同色圆环相接而看起来缺失。
        val diagonal=kotlin.math.hypot(bounds.width().toDouble(),bounds.height().toDouble()).toFloat()
        val available=AppLayout.routeMarkerTextSafeDiameter/scale-paint.strokeWidth
        if(diagonal>available) {
            paint.textSize*=available/diagonal
            paint.getTextBounds(letter,0,letter.length,bounds)
        }
        paint.textAlign=Paint.Align.LEFT
        canvas.drawText(letter,center-bounds.exactCenterX(),center-bounds.exactCenterY(),paint)
        }
        return bitmap
    }
    private var planPoints = emptyList<com.fairland.fgroundcontrol.mission.BoundaryPoint>()
    private var planSource: GeoJsonSource? = null
    fun drawPlan(points: List<com.fairland.fgroundcontrol.mission.BoundaryPoint>) {
        val unchanged = planPoints == points
        planPoints = points
        val source = map?.style?.getSourceAs<GeoJsonSource>("field-plan") ?: return
        if (unchanged && planSource === source) return
        planSource = source
        if (points.size < 2) { source.setGeoJson(empty); return }
        val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        source.setGeoJson("""{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}]}""")
    }
    fun cameraPosition(): CameraPosition? = map?.cameraPosition
    private var compassTop = 0
    fun updateCompassTop(top: Int) {
        compassTop = top
        val settings = map?.uiSettings ?: return
        if (settings.compassMarginTop != top) settings.setCompassMargins(settings.compassMarginLeft, top, settings.compassMarginRight, settings.compassMarginBottom)
    }
    fun attach(map: MapLibreMap) {
        map.addOnMapClickListener { point ->
            if (editingBoundary) false else {
                val hit = model.boundary.tasks.value.asReversed().firstOrNull {
                    com.fairland.fgroundcontrol.mission.containsTaskPoint(it.draft.points, point.latitude, point.longitude)
                }
                if (hit != null) model.boundary.selectTask(hit)
                hit != null
            }
        }

        if (destroyed) return
        this.map = map
        updateCompassTop(compassTop)
        map.uiSettings.apply {
            isZoomGesturesEnabled = true
            isDoubleTapGesturesEnabled = true
            isQuickZoomGesturesEnabled = true
        }
        fun centerChanged() {
            val target=map.cameraPosition.target
            model.boundaryCenter.value=target?.let { com.fairland.fgroundcontrol.mission.BoundaryPoint(it.latitude,it.longitude) }
        }
        map.addOnCameraIdleListener { if (!destroyed) centerChanged() }
        centerChanged()
        map.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) model.followMap(false) }
        update(configuration, latest)
    }
    fun update(config: MapConfiguration, readout: Readout, locate: Int = locateRequest) {
        locateRequest = locate
        configuration = config; latest = readout
        if (!readout.vehicle.connected) model.mapCenteredSession = null
        val map = map ?: return
        if (!hasStyle || applied != config.style || appliedRevision != config.revision) {
            hasStyle = true; applied = config.style; appliedRevision = config.revision; lastTrack = null; lastPosition = -1
            val token = ++styleToken
            val builder = if (config.remoteStyle && config.style != null) Style.Builder().fromUri(config.style)
                else Style.Builder().fromJson(config.style ?: "{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"${String.format(Locale.ROOT, "#%06x", AppColors.mapBackground.toArgb() and 0xffffff)}\"}}]}")
            map.setStyle(builder) { style ->
                if (destroyed || token != styleToken) return@setStyle
                style.addSource(GeoJsonSource("task-boundaries", empty))
                style.addLayer(LineLayer("task-boundaries-line", "task-boundaries").withProperties(lineColor(AppColors.textInverse.toArgb()),lineWidth(AppLayout.taskMapBorderWidth)))
                style.addLayerBelow(org.maplibre.android.style.layers.FillLayer("task-boundaries-fill","task-boundaries").withProperties(
                    org.maplibre.android.style.layers.PropertyFactory.fillColor(AppColors.taskMapFill.toArgb()),
                    org.maplibre.android.style.layers.PropertyFactory.fillOpacity(AppColors.taskMapFillOpacity)),"task-boundaries-line")
                style.addLayer(SymbolLayer("task-boundaries-label","task-boundaries").apply { minZoom=AppLayout.mapTaskLabelMinZoom }.withProperties(
                    iconImage(org.maplibre.android.style.expressions.Expression.get("image")),
                    org.maplibre.android.style.layers.PropertyFactory.iconSize(AppLayout.mapSelectedIconScale),
                    org.maplibre.android.style.layers.PropertyFactory.iconAnchor("center"),
                    iconAllowOverlap(true),iconIgnorePlacement(true)))
                drawTaskBoundaries(taskBoundaries)
                style.addSource(GeoJsonSource("boundary-draft", empty))
                style.addLayer(LineLayer("boundary-line", "boundary-draft").withProperties(lineColor(AppColors.textInverse.toArgb()),lineWidth(AppLayout.mapTrackWidth)))
                style.addSource(GeoJsonSource("task-selected-fill",empty))
                style.addLayerBelow(org.maplibre.android.style.layers.FillLayer("task-selected-fill-layer","task-selected-fill").withProperties(
                    org.maplibre.android.style.layers.PropertyFactory.fillColor(AppColors.taskSelectedMap.toArgb()),
                    org.maplibre.android.style.layers.PropertyFactory.fillOpacity(AppColors.taskSelectedFillOpacity)),"boundary-line")
                style.addLayer(org.maplibre.android.style.layers.CircleLayer("boundary-points", "boundary-draft").withProperties(org.maplibre.android.style.layers.PropertyFactory.circleColor(AppColors.boundaryPoint.toArgb()), org.maplibre.android.style.layers.PropertyFactory.circleRadius(AppLayout.boundaryPointRadius), org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor(AppColors.textInverse.toArgb()),org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(AppLayout.boundaryPointStroke)))
                drawBoundary(boundaryPoints)
                style.addSource(GeoJsonSource("field-plan", empty))
                style.addLayer(LineLayer("field-plan-line", "field-plan").withProperties(lineColor(AppColors.planningRoute.toArgb()),lineWidth(AppLayout.mapTrackWidth)))
                drawPlan(planPoints)
                style.addSource(GeoJsonSource("task-selection",empty))
                style.addLayer(SymbolLayer("task-selection-label","task-selection").apply { minZoom=AppLayout.mapTaskLabelMinZoom }.withProperties(
                    iconImage("task-selected-label"),org.maplibre.android.style.layers.PropertyFactory.iconSize(AppLayout.mapSelectedIconScale),
                    org.maplibre.android.style.layers.PropertyFactory.iconAnchor("center"),
                    iconAllowOverlap(true),iconIgnorePlacement(true)))
                drawSelection(selectionName,selectionPoints)
                style.addImage("route-start",routeMarker(true))
                style.addImage("route-end",routeMarker(false))
                style.addImage("route-start-letter",routeMarker(true,true))
                style.addImage("route-end-letter",routeMarker(false,true))
                style.addSource(GeoJsonSource("route-ends",empty))
                style.addLayer(SymbolLayer("route-ends-label","route-ends").withProperties(
                    iconImage(Expression.get("icon")),org.maplibre.android.style.layers.PropertyFactory.iconSize(AppLayout.mapSelectedIconScale*AppLayout.routeMarkerScale),
                    iconRotate(Expression.get("rotation")),iconRotationAlignment("map"),
                    iconAllowOverlap(true),iconIgnorePlacement(true)))
                style.addLayer(SymbolLayer("route-ends-letters","route-ends").withProperties(
                    iconImage(Expression.get("letter")),org.maplibre.android.style.layers.PropertyFactory.iconSize(AppLayout.mapSelectedIconScale*AppLayout.routeMarkerScale),
                    iconRotationAlignment("viewport"),iconAllowOverlap(true),iconIgnorePlacement(true)))
                drawRouteEnds(endpointPoints)
                style.addSource(GeoJsonSource("vehicle", empty)); style.addSource(GeoJsonSource("track", empty))
                style.addLayer(LineLayer("track", "track").withProperties(lineColor(AppColors.mapTrack.toArgb()), lineWidth(AppLayout.mapTrackWidth)))
                val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                Canvas(icon).drawPath(Path().apply { moveTo(24f, 2f); lineTo(43f, 44f); lineTo(24f, 33f); lineTo(5f, 44f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppColors.mapAircraft.toArgb() })
                style.addImage("aircraft", icon)
                val dot = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                Canvas(dot).drawCircle(24f, 24f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppColors.mapAircraft.toArgb() })
                style.addImage("position-only", dot)
                style.addLayer(SymbolLayer("aircraft", "vehicle").withProperties(org.maplibre.android.style.layers.PropertyFactory.iconSize(AppLayout.mapAircraftIconScale), iconImage(Expression.get("icon")), iconRotate(Expression.get("heading")), iconRotationAlignment("map"), iconAllowOverlap(true), iconIgnorePlacement(true)))
                config.bounds?.let { bounds ->
                    if (!config.follow || latest.vehicle.quality("position", latest.nowMs) != Quality.VALID) map.cameraPosition = CameraPosition.Builder().target(LatLng((bounds.south + bounds.north) / 2, (bounds.west + bounds.east) / 2)).zoom(bounds.zoom.toDouble()).build()
                }
                if (config.style != null) model.mapLoaded(config.style, config.revision, !resourceFailed)
                draw()
            }
            return
        }
        draw()
    }
    private fun draw() {
        if (destroyed) return
        val map = map ?: return
        val style = map.style ?: return
        if (lastTrack != latest.trackGeoJson) { style.getSourceAs<GeoJsonSource>("track")?.setGeoJson(latest.trackGeoJson); lastTrack = latest.trackGeoJson }
        val position = latest.vehicle.readings["position"]
        val attitude = latest.vehicle.readings["attitude"]
        if (latest.vehicle.quality("position", latest.nowMs) != Quality.VALID || position == null) {
            style.getSourceAs<GeoJsonSource>("vehicle")?.setGeoJson(empty); return
        }
        val heading = if (latest.vehicle.quality("attitude", latest.nowMs) == Quality.VALID) attitude?.values?.getOrNull(2) ?: 0.0 else 0.0
        val feature = JSONObject().put("type", "Feature").put("properties", JSONObject().put("heading", heading).put("icon", if (latest.vehicle.quality("attitude", latest.nowMs) == Quality.VALID) "aircraft" else "position-only"))
            .put("geometry", JSONObject().put("type", "Point").put("coordinates", org.json.JSONArray().put(position.values[1]).put(position.values[0])))
        style.getSourceAs<GeoJsonSource>("vehicle")?.setGeoJson(feature.toString())
        val firstPosition = model.mapCenteredSession != latest.vehicle.session
        if (firstPosition) {
            map.cameraPosition = CameraPosition.Builder(map.cameraPosition)
                .target(LatLng(position.values[0]!!, position.values[1]!!))
                .zoom(AppLayout.aircraftInitialZoom).build()
            model.mapCenteredSession = latest.vehicle.session
            lastPosition = position.receivedMs
        }
        if (locateRequest != handledLocateRequest) {
            map.cameraPosition = CameraPosition.Builder(map.cameraPosition).target(LatLng(position.values[0]!!, position.values[1]!!)).build()
            handledLocateRequest = locateRequest
        }
        if (configuration.follow && position.receivedMs != lastPosition) {
            // 仅首次定位设置初始级别，后续遥测跟随不覆盖用户缩放。
            val zoom = if (lastPosition < 0) maxOf(map.cameraPosition.zoom, AppLayout.aircraftInitialZoom) else map.cameraPosition.zoom
            map.cameraPosition = CameraPosition.Builder(map.cameraPosition).target(LatLng(position.values[0]!!, position.values[1]!!)).zoom(zoom).build()
            lastPosition = position.receivedMs
        }
    }
}

@Composable
private fun MapKeyDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    // 密钥草稿只存内存，不写入savedInstanceState。
    var china by remember { mutableStateOf("") }
    var overseas by remember { mutableStateOf("") }
    val valid = MapStyles.validKey(china.trim()) && MapStyles.validKey(overseas.trim())
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_edit_keys)) },
        text = { Column {
            Text(stringResource(R.string.map_key_hint))
            OutlinedTextField(value = china, onValueChange = { if (it.length <= 256) china = it }, label = { Text(stringResource(R.string.map_tianditu)) }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                isError = !MapStyles.validKey(china.trim()))
            OutlinedTextField(value = overseas, onValueChange = { if (it.length <= 256) overseas = it }, label = { Text(stringResource(R.string.map_maptiler)) }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                isError = !MapStyles.validKey(overseas.trim()))
            if (!valid) Text(stringResource(R.string.map_key_invalid), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = valid && (china.isNotBlank() || overseas.isNotBlank()), onClick = { onSave(china, overseas) }) { Text(stringResource(R.string.map_key_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_key_cancel)) } })
}
