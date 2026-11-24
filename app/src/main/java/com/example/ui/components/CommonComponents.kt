package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.catalog.CommandRisk
import com.example.ui.ConnectionState
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.RecoveryAmber
import com.example.ui.theme.TerminalEmerald
import com.example.ui.theme.TerminalMonoStyle

@Composable
fun HeroConsoleHeader(
    isHebrew: Boolean,
    connectionState: ConnectionState,
    connectedLabel: String,
    transportBadge: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_recovery_hero),
                contentDescription = if (isHebrew) "באנר מערכת Recovery ADB" else "Recovery ADB schematic banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC0A0E14),
                                Color(0x990A0E14),
                                Color(0xF00A0E14),
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = PhosphorCyan.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, PhosphorCyan.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = PhosphorCyan,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (isHebrew) "100% אופליין • ללא Python" else "100% OFFLINE • NATIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorCyan,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    StatusChip(
                        isHebrew = isHebrew,
                        state = connectionState,
                        transportBadge = transportBadge,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recovery ADB Studio",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED_ADB,
                                ConnectionState.CONNECTED_FASTBOOT -> connectedLabel.ifBlank { transportBadge }
                                ConnectionState.AWAITING_RSA_AUTH ->
                                    if (isHebrew) "אשר את חלונית ניפוי השגיאות במכשיר היעד" else "Accept RSA prompt on target device"
                                ConnectionState.CONNECTING ->
                                    if (isHebrew) "מבצע לחיצת יד ADB..." else "Performing ADB handshake..."
                                ConnectionState.DISCONNECTED ->
                                    if (isHebrew) "מוכן לחיבור USB OTG או TCP מקומי" else "Ready for USB OTG or Local TCP"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB8C7D9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (connectionState != ConnectionState.DISCONNECTED) {
                        IconButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("disconnect_button")
                                .clip(CircleShape)
                                .background(CriticalCrimson.copy(alpha = 0.2f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = if (isHebrew) "נתק חיבור" else "Disconnect",
                                tint = CriticalCrimson,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    isHebrew: Boolean,
    state: ConnectionState,
    transportBadge: String,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED_ADB -> TerminalEmerald
            ConnectionState.CONNECTED_FASTBOOT -> RecoveryAmber
            ConnectionState.CONNECTING, ConnectionState.AWAITING_RSA_AUTH -> PhosphorCyan
            ConnectionState.DISCONNECTED -> Color(0xFF7B8CA3)
        },
        label = "statusAccent",
    )
    val label = when (state) {
        ConnectionState.CONNECTED_ADB -> transportBadge.ifBlank { if (isHebrew) "ADB מחובר" else "ADB ACTIVE" }
        ConnectionState.CONNECTED_FASTBOOT -> "FASTBOOT USB"
        ConnectionState.AWAITING_RSA_AUTH -> if (isHebrew) "ממתין ל-RSA" else "RSA AUTH"
        ConnectionState.CONNECTING -> if (isHebrew) "מתחבר..." else "CONNECTING"
        ConnectionState.DISCONNECTED -> if (isHebrew) "לא מחובר" else "OFFLINE"
    }

    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun RiskBadge(
    risk: CommandRisk,
    isHebrew: Boolean,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (risk) {
        CommandRisk.SAFE -> TerminalEmerald to (if (isHebrew) "בטוח" else "SAFE")
        CommandRisk.CAUTION -> RecoveryAmber to (if (isHebrew) "זהירות" else "CAUTION")
        CommandRisk.ADVANCED -> CriticalCrimson to (if (isHebrew) "מתקדם" else "ADVANCED")
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun MonoCodeBlock(
    code: String,
    isHebrew: Boolean,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                style = TerminalMonoStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = if (isHebrew) "העתק פקודה" else "Copy command",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun DismissibleBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (isError) MaterialTheme.colorScheme.error else TerminalEmerald
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.13f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Shield else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
