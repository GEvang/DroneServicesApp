package com.example.droneservicesapp.ui.compose.screens.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens

@Composable
fun MoreScreen(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                color = Color(0xCC091117),
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
            }
            Column {
                Text(title, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 14.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
fun TwoColumnLayout(
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = left)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = right)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                left()
                right()
            }
        }
    }
}

@Composable
fun MorePanel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xCC091117),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xA629E6DA)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(7.dp))
            content()
        }
    }
}

@Composable
fun MoreSettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (onClick == null) Modifier else Modifier
                    .clickable(enabled = enabled, onClick = onClick)
                    .semantics { role = Role.Button }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        iconRes?.let { MoreCircleIcon(it, enabled = enabled) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            )
            supportingText?.let {
                Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value.isNotBlank()) {
            Text(
                value,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
fun MoreDivider() {
    HorizontalDivider(color = Color(0xA629E6DA))
}

@Composable
fun MoreCircleIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.size(46.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.padding(10.dp),
            colorFilter = ColorFilter.tint(
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)
            ),
        )
    }
}

@Composable
fun MoreField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .then(if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onClick)),
        color = Color(0x99111B20),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Color(0x8AC9D0D6)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                iconRes?.let {
                    Image(
                        painterResource(it),
                        null,
                        Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f)),
                    )
                }
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .45f))
            }
            Text(
                value,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = valueColor.copy(alpha = if (enabled) 1f else .45f),
            )
        }
    }
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            iconRes?.let {
                Image(painterResource(it), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            }
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}
