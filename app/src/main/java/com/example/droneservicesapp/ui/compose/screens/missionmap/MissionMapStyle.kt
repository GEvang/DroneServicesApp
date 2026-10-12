package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droneservicesapp.R

/** Colours and shapes of the mission map chrome, carried over from its XML drawables. */
internal object MapChrome {
    val overlay = Color(0xE6091117)
    val overlayStroke = Color(0x2EFFFFFF)
    val statusCard = Color(0xDD091117)
    val statusCardStroke = Color(0x33FFFFFF)
    val pillInactive = Color(0x990A1117)
    val pillActiveSurface = Color(0x2929E6DA)
    val pillActiveStroke = Color(0xB329E6DA)
    val sliderPill = Color(0xCC1F2328)
    val sliderPillStroke = Color(0x18FFFFFF)
    val iconButton = Color(0xD92A2F35)
    val iconButtonStroke = Color(0x22FFFFFF)
    val dockButton = Color(0x1AFFFFFF)
    val dockButtonStroke = Color(0x33FFFFFF)
    val dockItemSelected = Color(0x2429E6DA)
    val dockItemSelectedStroke = Color(0x8029E6DA)
    val popup = Color(0xF20A141B)
    val popupStroke = Color(0x6635D8D0)
    val scrim = Color(0x26000000)
    val active = Color(0xFF29E6DA)
    val textPrimary = Color(0xFFF2F2F3)
    val textSecondary = Color(0xFFA7A7AD)
    val unselected = Color(0xFFC9D0D6)
    val warning = Color(0xFFE4A640)
    val danger = Color(0xFFFF5B5B)
    val shellStroke = Color(0x3DFFFFFF)
    val onAccent = Color(0xFF0B0B0D)

    val panelShape: Shape = RoundedCornerShape(8.dp)
    val pillShape: Shape = RoundedCornerShape(8.dp)
    val overlayCardShape: Shape = RoundedCornerShape(16.dp)
    val sliderShape: Shape = RoundedCornerShape(percent = 50)
}

/** Translucent rounded panel used by the side panels, docks and overlay cards. */
@Composable
internal fun MapOverlayPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MapChrome.panelShape,
    color: Color = MapChrome.overlay,
    border: Color = MapChrome.overlayStroke,
    contentPadding: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
internal fun MapSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MapChrome.unselected) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
internal fun MapPanelHeader(
    title: String,
    @DrawableRes iconRes: Int? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        iconRes?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(MapChrome.active),
            )
        }
        Text(
            text = title,
            color = MapChrome.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Two-state pill used for workflow, altitude-reference and obstacle-shape selectors. */
@Composable
internal fun MapPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    height: Dp = 36.dp,
    selectedColor: Color = MapChrome.active,
) {
    val contentColor = if (selected) selectedColor else MapChrome.textPrimary
    Row(
        modifier = modifier
            .height(height)
            .clip(MapChrome.pillShape)
            .background(if (selected) MapChrome.pillActiveSurface else MapChrome.pillInactive)
            .border(1.dp, if (selected) MapChrome.pillActiveStroke else MapChrome.overlayStroke, MapChrome.pillShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (iconRes == null) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        iconRes?.let {
            Image(painterResource(it), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(contentColor))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Panel action button with an optional leading icon. */
@Composable
internal fun MapPanelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    contentColor: Color = MapChrome.textPrimary,
    iconTinted: Boolean = true,
    highlighted: Boolean = false,
    height: Dp = 40.dp,
    contentDescription: String = text,
) {
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(MapChrome.pillShape)
            .background(if (highlighted) MapChrome.pillActiveSurface else MapChrome.pillInactive)
            .border(1.dp, if (highlighted) MapChrome.pillActiveStroke else MapChrome.overlayStroke, MapChrome.pillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        iconRes?.let {
            Image(
                painterResource(it),
                null,
                Modifier.size(18.dp),
                colorFilter = if (iconTinted) ColorFilter.tint(contentColor) else null,
            )
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MapDivider() {
    HorizontalDivider(Modifier.padding(vertical = 6.dp), thickness = 1.dp, color = MapChrome.shellStroke)
}

/** Round neutral icon button used for the slider rows' minus/plus actions. */
@Composable
internal fun MapRoundIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    background: Color = MapChrome.iconButton,
    border: Color = MapChrome.iconButtonStroke,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(iconRes), null, Modifier.size(iconSize), colorFilter = ColorFilter.tint(MapChrome.textPrimary))
    }
}

/**
 * Label + `[-] slider value unit [+]` row; the value is driven by an integer [step] index so the
 * row behaves like the previous SeekBar (one step per button press).
 */
@Composable
internal fun MapSliderField(
    label: String,
    stepIndex: Int,
    maxStepIndex: Int,
    valueText: String,
    unit: String,
    onStepChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minStepIndex: Int = 0,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, color = MapChrome.textPrimary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(MapChrome.sliderShape)
                .background(MapChrome.sliderPill)
                .border(1.dp, MapChrome.sliderPillStroke, MapChrome.sliderShape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MapRoundIconButton(
                R.drawable.ic_remove_24,
                stringResource(R.string.decrease),
                onClick = { onStepChange((stepIndex - 1).coerceAtLeast(minStepIndex)) },
                size = 28.dp,
                iconSize = 16.dp,
            )
            Slider(
                value = stepIndex.coerceIn(minStepIndex, maxStepIndex).toFloat(),
                onValueChange = { onStepChange(it.toInt().coerceIn(minStepIndex, maxStepIndex)) },
                valueRange = minStepIndex.toFloat()..maxStepIndex.toFloat().coerceAtLeast(minStepIndex + 1f),
                steps = (maxStepIndex - minStepIndex - 1).coerceAtLeast(0),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF29DCD0),
                    inactiveTrackColor = Color(0xFF2A2F35),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Text(
                valueText,
                color = MapChrome.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp),
            )
            Text(unit, color = MapChrome.textSecondary, fontSize = 11.sp)
            MapRoundIconButton(
                R.drawable.ic_add_24,
                stringResource(R.string.increase),
                onClick = { onStepChange((stepIndex + 1).coerceAtMost(maxStepIndex)) },
                size = 28.dp,
                iconSize = 16.dp,
            )
        }
    }
}

@Composable
internal fun MapCaption(text: String, color: Color = MapChrome.textSecondary, fontSize: TextUnit = 11.sp, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = fontSize, modifier = modifier)
}
