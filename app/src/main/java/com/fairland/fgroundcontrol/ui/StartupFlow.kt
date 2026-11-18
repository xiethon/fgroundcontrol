package com.fairland.fgroundcontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.fairland.fgroundcontrol.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fairland.fgroundcontrol.FoundationViewModel
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.maps.MapHomeScreen

/** 仅移植Home→作业方式→飞行页面导航，不绑定任务或飞行操作。 */
@Composable
fun StartupFlow(model: FoundationViewModel, unsupported: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("home") }
    var lastMode by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = page != "home") { page = if (page == "flight") "methods" else "home" }
    when (page) {
        "flight" -> MapHomeScreen(model, Modifier.fillMaxSize(), onSettings = unsupported,
            onTaskMode = { page = "methods" }, unsupported = unsupported, selectedMode = lastMode)
        "methods" -> MethodSelection(onHome = { page = "home" }, onChoose = { mode -> lastMode = mode; page = "flight" }, unsupported = unsupported)
        else -> {
            val connection by model.connection.collectAsStateWithLifecycle()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val scale = AppLayout.scale(maxWidth, maxHeight)
                fun unit(value: androidx.compose.ui.unit.Dp) = value * scale
                val headerHeight = unit(AppLayout.homeHeader)
                val footerHeight = unit(AppLayout.homeFooter)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val footerTop = maxHeight - unit(AppLayout.homeFooterInset)
                val backgroundModifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = AppLayout.homeImageScaleX
                    translationX = size.width * AppLayout.homeImageOffsetX
                }
                Image(painterResource(R.drawable.figma_home_bg), null, backgroundModifier, contentScale = ContentScale.FillBounds)
                // 背景副本仅在玻璃面板区域绘制，不能模糊前景文字和图标。
                Image(painterResource(R.drawable.figma_home_bg), null,
                    Modifier.fillMaxSize().drawWithContent {
                        clipRect(0f, 0f, size.width, with(density) { headerHeight.toPx() }) { this@drawWithContent.drawContent() }
                        val margin = with(density) { unit(AppSpace.sm).toPx() }
                        val radius = with(density) { unit(AppRadius.panel).toPx() }
                        val panelPath = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                androidx.compose.ui.geometry.Rect(margin, with(density) { footerTop.toPx() } + margin, size.width - margin, size.height - margin),
                                androidx.compose.ui.geometry.CornerRadius(radius)))
                        }
                        clipPath(panelPath) { this@drawWithContent.drawContent() }
                    }.blur(unit(AppLayout.blur)).then(backgroundModifier), contentScale = ContentScale.FillBounds)
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth().height(headerHeight).clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = unit(AppRadius.panel), bottomEnd = unit(AppRadius.panel))).background(AppColors.homeHeader).padding(horizontal = unit(AppSpace.lg)), verticalAlignment = Alignment.CenterVertically) {
                        HeaderAction(R.drawable.figma_home_user, R.string.startup_user, unsupported, scale, iconOnly = true)
                        Spacer(Modifier.weight(1f))
                        HeaderAction(R.drawable.figma_home_settings, R.string.startup_system, unsupported, scale)
                        Spacer(Modifier.width(unit(AppSpace.lg)))
                        HeaderAction(R.drawable.figma_home_notify, R.string.startup_notifications, unsupported, scale)
                    }
                    Surface(modifier = Modifier.padding(unit(AppSpace.sm)).fillMaxWidth().height(footerHeight), color = AppColors.homeFooter, shape = androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.panel)), shadowElevation = unit(AppLayout.shadow)) {
                        Row(Modifier.padding(unit(AppSpace.md)), horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.sm))) {
                            Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.sm))) {
                                listOf(R.drawable.figma_home_file to R.string.startup_files, R.drawable.figma_home_device to R.string.startup_device, R.drawable.figma_home_logs to R.string.startup_logs).forEach { (icon, title) ->
                                    Box(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.alternative))).background(AppColors.surface).clickable(onClick = unsupported), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(unit(AppSpace.xxs))) {
                                            Image(painterResource(icon), null, Modifier.size(unit(AppLayout.iconAction)))
                                            Text(stringResource(title), color = AppColors.textHome, style = AppType.homeAction.scaled(scale))
                                        }
                                    }
                                }
                            }
                            Row(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.alternative))).background(AppColors.surface).padding(unit(AppSpace.lg)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.md))) {
                                Row(Modifier.weight(1f).fillMaxHeight().clickable(onClick = unsupported), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(unit(AppLayout.iconAction)).background(if (connection == "connected") AppColors.activeSubtle else AppColors.warningSubtle, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                        Image(painterResource(R.drawable.figma_home_connected), null, Modifier.size(unit(AppLayout.iconSmall)), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (connection == "connected") AppColors.active else AppColors.warning))
                                    }
                                    Spacer(Modifier.width(unit(AppSpace.xs)))
                                    Text(stringResource(when(connection) { "connected" -> R.string.state_connected; "connecting" -> R.string.state_connecting; else -> R.string.state_disconnected }), color = AppColors.textPrimary, style = AppType.status.scaled(scale), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Box(Modifier.width(unit(AppLayout.beginWidth)).height(unit(AppLayout.actionHeight)).clip(androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.card))).background(AppColors.active).clickable { page = if (lastMode.isEmpty()) "methods" else "flight" }, contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.startup_begin), color = AppColors.textInverse, style = AppType.display.scaled(scale))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: Int, label: Int, action: () -> Unit, scale: Float, iconOnly: Boolean = false) {
    Row(Modifier.clickable(onClick = action), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((AppSpace.xs * scale))) {
        Box(Modifier.size((AppLayout.avatar * scale)).background(AppColors.surface, androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.panel * scale)), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), stringResource(label), Modifier.size((AppLayout.iconAction * scale)))
        }
        if (!iconOnly) Text(stringResource(label), color = AppColors.textHome, style = AppType.headerAction.scaled(scale))
    }
}

@Composable
private fun MethodSelection(onHome: () -> Unit, onChoose: (String) -> Unit, unsupported: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surfaceAlternative).windowInsetsPadding(WindowInsets.displayCutout)) {
        val scale = AppLayout.scale(maxWidth, maxHeight)
        fun unit(value: androidx.compose.ui.unit.Dp) = value * scale
        Column(Modifier.fillMaxSize()) {
            Surface(color = AppColors.surface, shadowElevation = unit(AppLayout.shadow)) {
                Row(Modifier.fillMaxWidth().height(unit(AppLayout.actionHeight)).padding(horizontal = unit(AppSpace.lg)), verticalAlignment = Alignment.CenterVertically) {
                    // 设计稿首层隐藏返回箭头，保留对称槽位让标题居中。
                    Spacer(Modifier.size(unit(AppLayout.iconMode)))
                    Text(stringResource(R.string.startup_select_mode), Modifier.weight(1f), color = AppColors.textPrimary, style = AppType.display.scaled(scale), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Box(Modifier.size(unit(AppLayout.iconMode)).clickable(onClick = onHome), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.figma_method_close), stringResource(R.string.top_close), Modifier.fillMaxSize())
                    }
                }
            }
            Row(Modifier.weight(1f).padding(unit(AppSpace.xl)), horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.lg))) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(unit(AppSpace.lg))) {
                    Row(Modifier.fillMaxWidth().height(unit(AppLayout.actionHeight)).clip(androidx.compose.foundation.shape.RoundedCornerShape(unit(AppRadius.card))).background(AppColors.active).clickable(onClick = onHome),
                        horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.xs), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.figma_method_return_home), null, Modifier.size(unit(AppLayout.iconMode)))
                        Text(stringResource(R.string.startup_return_home), color = AppColors.textInverse, style = AppType.display.scaled(scale))
                    }
                    MethodTile(R.drawable.figma_method_field, R.drawable.figma_method_field_icon, R.string.startup_field, Modifier.weight(1f), scale, AppLayout.fieldScrim, field = true) { onChoose("field") }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(unit(AppSpace.lg))) {
                    MethodTile(R.drawable.figma_method_manual, R.drawable.figma_method_manual_icon, R.string.startup_manual, Modifier.weight(1f), scale, AppLayout.manualScrim) { onChoose("manual") }
                    MethodTile(R.drawable.figma_method_lift, R.drawable.figma_method_lift_icon, R.string.startup_lift, Modifier.weight(1f), scale, AppLayout.liftScrim, action = unsupported)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(unit(AppSpace.lg))) {
                    MethodTile(R.drawable.figma_method_orchard, R.drawable.figma_method_orchard_icon, R.string.startup_orchard, Modifier.weight(1f), scale, AppLayout.orchardScrim, textColor = AppColors.textDisabled, action = unsupported)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(unit(AppSpace.lg))) {
                        MethodTile(R.drawable.figma_method_field_survey, R.drawable.figma_method_field_survey_icon, R.string.startup_field_survey, Modifier.weight(1f), scale, AppLayout.surveyScrim, action = unsupported)
                        MethodTile(R.drawable.figma_method_orchard_survey, R.drawable.figma_method_orchard_survey_icon, R.string.startup_orchard_survey, Modifier.weight(1f), scale, AppLayout.surveyScrim, action = unsupported)
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodTile(image: Int, icon: Int, title: Int, modifier: Modifier, scale: Float, scrim: Float,
                       field: Boolean = false, textColor: Color = AppColors.textInverse, action: () -> Unit) {
    Box(modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.card * scale)).clickable(onClick = action), contentAlignment = Alignment.Center) {
        val imageModifier = if (field) Modifier.fillMaxSize().graphicsLayer {
            // Figma背景使用304.46%宽、111.56%高及指定偏移，不能用居中Crop代替。
            scaleX = AppLayout.fieldImageScaleX
            scaleY = AppLayout.fieldImageScaleY
            translationX = size.width * (AppLayout.fieldImageLeft + (AppLayout.fieldImageScaleX - 1f) / 2f)
            translationY = size.height * (AppLayout.fieldImageTop + (AppLayout.fieldImageScaleY - 1f) / 2f)
        } else Modifier.fillMaxSize()
        Image(painterResource(image), null, imageModifier, contentScale = ContentScale.FillBounds)
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(AppColors.scrim.copy(alpha = scrim)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((AppSpace.lg * scale))) {
            // 导出的图标含设计中的投影边界，保持其原始比例和40px主体槽位。
            val dimensions = when (icon) {
                R.drawable.figma_method_field_icon -> AppLayout.fieldIconWidth to AppLayout.fieldIconHeight
                R.drawable.figma_method_manual_icon -> AppLayout.manualIconWidth to AppLayout.manualIconHeight
                R.drawable.figma_method_field_survey_icon -> AppLayout.surveyIconWidth to AppLayout.surveyIconHeight
                else -> AppLayout.iconMode to AppLayout.iconMode
            }
            Box(Modifier.size((AppLayout.iconMode * scale)), contentAlignment = Alignment.Center) {
                Image(painterResource(icon), null, Modifier.requiredSize(dimensions.first * scale, dimensions.second * scale))
            }
            Text(stringResource(title), color = textColor, maxLines = 1,
                style = AppType.display.scaled(scale).copy(shadow = androidx.compose.ui.graphics.Shadow(AppColors.iconShadow, androidx.compose.ui.geometry.Offset(0f, AppLayout.iconShadowOffset * scale), AppLayout.iconShadowBlur * scale)))
        }
    }
}
