package com.fairland.fgroundcontrol.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.ui.theme.*

@Composable
internal fun SidebarTabs(second: Boolean,onSelect: (Boolean)->Unit,scale: Float,firstLabel: Int,secondLabel: Int) {
    Box(Modifier.fillMaxWidth().height(AppLayout.planningTabHeight*scale)) {
        Row(Modifier.fillMaxSize()) {
            listOf(false to firstLabel,true to secondLabel).forEach { (tab,label) ->
                val selected=second==tab
                val width=if(selected) Modifier.width(AppLayout.planningSelectedTabWidth*scale) else Modifier.weight(1f)
                Box(width.fillMaxHeight().clip(RoundedCornerShape(topStart=AppRadius.panel*scale,topEnd=AppRadius.panel*scale))
                    .then(if(selected) Modifier.background(AppColors.surface) else Modifier)
                    .clickable(interactionSource=remember { MutableInteractionSource() },indication=null,role=Role.Tab) {onSelect(tab)},contentAlignment=Alignment.Center) {
                    Column(Modifier.width(IntrinsicSize.Max),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(stringResource(label),style=AppType.planningTab.scaled(scale),color=if(selected) AppColors.active else AppColors.textPrimary,maxLines=1,softWrap=false)
                        // 下划线跟随文字宽度，未选中标签保留同高空间，避免切换时文字跳动。
                        Box(Modifier.fillMaxWidth().height(AppLayout.planningTabStroke*scale)
                            .then(if(selected) Modifier.background(AppColors.active) else Modifier))
                    }
                }
            }
        }
        // 圆角仅覆盖分界处底部，不再占据一列形成竖向缝隙。
        Image(painterResource(R.drawable.planning_tab_corner),null,
            Modifier.align(Alignment.BottomStart)
                .offset(x=(if(second) AppLayout.boundaryPanelWidth-AppLayout.planningSelectedTabWidth-AppRadius.panel else AppLayout.planningSelectedTabWidth)*scale)
                .size(AppRadius.panel*scale).graphicsLayer {scaleX=if(second) -1f else 1f})
    }
}
