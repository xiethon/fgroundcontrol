package com.fairland.fgroundcontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontSynthesis
import com.fairland.fgroundcontrol.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma语义样式入口；页面不得重新定义同类颜色或设计尺寸。 */
object AppColors {
    val buttonDisabledText = Color(0xFFD9DCDF)
    val pointSourceIcon = Color(0xFF6B7280)
    val creatorSelected = Color(0x1A00D96F)
    val taskSelectedBorder = Color(0x9900D96F)
    val routeEnd = Color(0xFF00E676)
    val taskMapFill = Color(0xFFFFFFFF)
    const val taskMapFillOpacity = .1f
    val taskSelectedMap = Color(0xFF00D96F)
    const val taskSelectedFillOpacity = .2f
    val planningHeaderSurface = Color(0xE6F5F7F9)
    val planningSecondary = Color(0xFF54565E)
    val planningUnit = Color(0xFF84868C)
    val planningRoute = Color(0xFFFFE500)
    val boundaryPoint = Color(0xFFB94CFF)
    val dashboardPanel = Color(0x66000000)
    val horizonSky = Color(0xFF0A84FF)
    val horizonGround = Color(0xFF00E676)
    val surface = Color.White
    val surfaceAlternative = Color(0xFFF5F7F9)
    val textPrimary = Color(0xFF0A0E19)
    val textHome = Color(0xFF0C121F)
    val textInverse = Color.White
    val textDisabled = Color(0xFFDADBDD)
    val error = Color(0xFFB3261E)
    val outline = Color(0xFFDAE1E8)
    val active = Color(0xFF00D96F)
    val warning = Color(0xFFFFAB00)
    val activeSubtle = Color(0x1F00D96F)
    val warningSubtle = Color(0x1FFFAB00)
    val homeHeader = Color(0x99FFFFFF)
    val homeFooter = Color(0xE6F5F7F9)
    val primaryDanger = Color(0xFFFF3640)
    val primaryInfo = Color(0xFF656B79)
    const val statusGradientFadePosition = .78f
    val flightGroup = Color(0x29000000)
    val flightControl = Color(0x33000000)
    const val statusGradientStartAlpha = .8f
    const val statusGradientEndAlpha = .1f
    val flightBar = Color(0xE6151D27)
    val flightWarning = Color(0xFFFFC857)
    val flightDanger = Color(0xFFFF7676)
    val flightHealthy = Color(0xFF75D5A2)
    val scrim = Color.Black
    val toast = Color.Black.copy(alpha = .72f)
    val iconShadow = Color(0x29000000)
    val mapBackground = Color(0xFFDCE2E5)
    val mapTrack = Color(0xFF2087D0)
    // 对齐compass_instrument_arrow.svg的主红色。
    val compassArrowRed = Color(0xFFEE3424)
    val mapAircraft = compassArrowRed
}

object AppSpace {
    val xxs = 4.dp
    val compact = 6.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
object AppRadius {
    val compact = 4.dp
    val card = 8.dp
    val alternative = 12.dp
    val panel = 16.dp
    val dialog = 28.dp
}
object AppType {
    // 随APK打包，避免不同设备的系统字体替代设计字体。
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val family = FontFamily(
        Font(R.font.noto_sans_sc, weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.noto_sans_sc, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))))
    private fun style(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = family, fontWeight = weight, fontSynthesis = FontSynthesis.None,
        fontSize = size.sp, lineHeight = (size * 1.2f).sp, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None))
    val display = style(32, FontWeight.Medium)
    val homeAction = style(28, FontWeight.Medium)
    val status = style(24)
    val planningTitle = style(28)
    val planningValue = style(30, FontWeight.Medium)
    val planningTab = style(26)
    val taskCardAction = style(22, FontWeight.Medium)
    val planningHeader = style(24, FontWeight.Medium)
    val headerAction = style(20)
    val toast = style(14)
    val body = style(16)
    val caption = style(12)
    val micro = style(11)
    val flightLabel = style(20, FontWeight.Medium)
    private val numberFamily = FontFamily(Font(R.font.anton_regular, weight = FontWeight.Normal))
    val numberSmall = style(20).copy(fontFamily = numberFamily, lineHeight = 20.sp)
    val dashboardNumber = style(28).copy(fontFamily = numberFamily)
    val dashboardLabel = style(18)
    val dashboardUnit = style(14)
    val numberValue = style(26).copy(fontFamily = numberFamily, lineHeight = 26.sp)
    val material = Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = display, headlineMedium = homeAction, headlineSmall = status,
        titleLarge = headerAction, titleMedium = body.copy(fontWeight = FontWeight.Medium), titleSmall = caption,
        bodyLarge = body, bodyMedium = body, bodySmall = caption,
        labelLarge = body.copy(fontWeight = FontWeight.Medium), labelMedium = caption, labelSmall = micro)
}

object AppLayout {
    const val mapAircraftIconScale = 1.4f
    const val aircraftInitialZoom = 16.0
    val creatorLeadingWidth = 64.dp
    val creatorActionWidth = 120.dp
    val creatorHeight = 64.dp
    val creatorExpandSize = 8.dp
    val boundaryToolsWidth = 200.dp
    val boundaryToolHeight = 56.dp
    val taskSelectedBorderWidth = 4.dp
    val taskCardActionHeight = 64.dp
    val taskActionWidth = 176.dp
    const val routeEndRotation = -90f
    const val routeMarkerScale = 1.5f
    const val routeMarkerCanvas = 52f
    const val routeMarkerWidth = 38f
    const val routeMarkerHeight = 24f
    const val routeMarkerText = 18f
    const val routeMarkerTextWeight = 900
    const val routeMarkerTextStroke = .5f
    const val routeMarkerTextSafeDiameter = 16f
    const val routeStartCenter = 12f
    const val routeEndCenter = 26f
    const val taskMapBorderWidth = 1f
    const val mapSelectedPointRadius = 4f
    const val mapSelectedLabelMaxWidth = 320f
    const val mapTaskLabelMinZoom = 16f
    const val mapTaskLabelGap = 2f
    const val mapSelectedLabelPadding = 4f
    const val mapSelectedCheckSize = 48f
    const val mapSelectedLabelSize = 26f
    const val mapSelectedIconScale = .5f
    val planningNavigationIcon = 32.dp
    val planningStepIcon = 40.dp
    val planningDrawerWidth = 56.dp
    val planningDrawerHeight = 88.dp
    val planningSelectedTabWidth = 160.dp
    val planningTabStroke = 3.dp
    val planningInputHeight = 72.dp
    val planningTabHeight = 56.dp
    val planningBorder = 1.dp
    val boundaryPanelWidth = 336.dp
    val boundaryPointRadius = 7f
    val boundaryPointStroke = 2f
    val boundaryCrosshair = 56.dp
    val signalColumnWidth = 40.dp
    val signalStatusSize = 20.dp
    const val designWidth = 1280f
    const val designHeight = 800f
    fun scale(width: Dp, height: Dp) = minOf(width.value / designWidth, height.value / designHeight)
    val iconSmall = 16.dp
    val iconStatus = 22.dp
    val iconControl = 24.dp
    val iconAction = 32.dp
    val iconMode = 40.dp
    val avatar = 56.dp
    val shadow = 2.dp
    val blur = 8.dp
    val homeHeader = 80.dp
    val homeFooter = 168.dp
    val homeFooterInset = 192.dp
    val beginWidth = 288.dp
    val actionHeight = 88.dp
    val flightBarMin = 64.dp
    val flightGroupGap = AppSpace.xs
    val flightControlGap = AppSpace.xs
    val flightMetricGap = AppSpace.xxs
    val flightMissingWidth = 48.dp
    val flightBarHeight = 72.dp
    val flightCell = 64.dp
    val flightAlert = 48.dp
    val flightInfoHeight = 56.dp
    val flightNumberWidth = 72.dp
    val flightPreviewWidth = 96.dp
    val flightFixWidth = 72.dp
    val flightIconDrop = 12.dp
    val flightBatteryHeight = 39.dp
    val percentWidth = 12.dp
    val percentHeight = 20.dp
    val gpsMaxWidth = 112.dp
    val mapPreviewHeight = 320.dp
    const val homeImageScaleX = 1423f / designWidth
    const val homeImageOffsetX = 5.5f / designWidth
    const val fieldImageScaleX = 3.0446f
    const val fieldImageScaleY = 1.1156f
    const val fieldImageLeft = -1.1801f
    const val fieldImageTop = -.0581f
    val fieldIconWidth = 46.dp
    val fieldIconHeight = 46.dp
    val manualIconWidth = 48.dp
    val manualIconHeight = 46.dp
    val surveyIconWidth = 45.dp
    val surveyIconHeight = 44.dp
    const val fieldScrim = 0f
    const val manualScrim = .2f
    const val liftScrim = .3f
    const val orchardScrim = .05f
    const val surveyScrim = .2f
    const val iconShadowOffset = 2f
    const val iconShadowBlur = 4f
    const val toastDurationMs = 2000L
    const val mapTrackWidth = 3f
}

@Composable
fun GroundControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = AppColors.active, onPrimary = AppColors.textInverse,
        secondary = AppColors.active, onSecondary = AppColors.textInverse,
        tertiary = AppColors.warning, onTertiary = AppColors.textPrimary,
        primaryContainer = AppColors.activeSubtle, onPrimaryContainer = AppColors.textPrimary,
        secondaryContainer = AppColors.activeSubtle, onSecondaryContainer = AppColors.textPrimary,
        tertiaryContainer = AppColors.warningSubtle, onTertiaryContainer = AppColors.textPrimary,
        background = AppColors.surfaceAlternative, onBackground = AppColors.textPrimary,
        surface = AppColors.surface, onSurface = AppColors.textPrimary,
        surfaceVariant = AppColors.surfaceAlternative, onSurfaceVariant = AppColors.textPrimary,
        outline = AppColors.outline, outlineVariant = AppColors.outline,
        inverseSurface = AppColors.flightBar, inverseOnSurface = AppColors.textInverse, inversePrimary = AppColors.active,
        scrim = AppColors.scrim, surfaceTint = AppColors.active,
        error = AppColors.error, onError = AppColors.textInverse),
        typography = AppType.material,
        shapes = Shapes(extraSmall = RoundedCornerShape(AppRadius.compact), small = RoundedCornerShape(AppRadius.card),
            medium = RoundedCornerShape(AppRadius.alternative), large = RoundedCornerShape(AppRadius.panel),
            extraLarge = RoundedCornerShape(AppRadius.dialog)), content = content)
}

fun TextStyle.scaled(scale: Float) = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

/** 仪表盘设计4511:30723及参考动态图层比例。 */
object DashboardTokens {
    val panelWidth = 616.dp
    val gaugeSize = 148.dp
    val metricWidth = 116.dp
    val metricRowHeight = 72.dp
    val controlSize = 40.dp
    val attitudeWingWidth = 26.dp
    val attitudeWingHeight = 2.dp
    val attitudeCenterWidth = 38.dp
    val attitudeCenterHeight = 11.928.dp
    val attitudeWingGap = 6.dp
    const val rimStep = 10
    const val rimMajorStep = 30
    const val rimMajorRatio = .12f
    const val rimMinorRatio = .06f
    const val attitudeAssetBase = 160f
    const val compassArrowRatio = .44f
    const val pitchSizeRatio = .55f
    const val pitchSpacingRatio = .15f
    const val pitchLongRatio = .35f
    const val pitchShortRatio = .25f
    const val pitchLabelOffsetRatio = .8f
    const val pitchFontRatio = .12f
    const val pitchStep = 5
    const val horizonPitchRange = 45f
    val stroke = 1.dp
}
