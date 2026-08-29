package com.halo.yunquevoice.ui

import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.nadeemiqbal.liquidglass.GlassButton
import io.github.nadeemiqbal.liquidglass.GlassCard
import io.github.nadeemiqbal.liquidglass.rememberLiquidGlassState
import com.halo.yunquevoice.voice.Store

/** 玻璃主题下使用真实 GlassButton，其他主题使用 Material Button。 */
@Composable
fun YunqueGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glass17 = Store.themeMode(context) == Store.THEME_GLASS17
    if (glass17) {
        val state = rememberLiquidGlassState()
        GlassCard(
            state = state,
            modifier = modifier
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        MaterialTheme.shapes.large
                    )
                    .padding(vertical = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(text, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
fun ConfirmDeleteDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 二级页面通用玻璃卡片：玻璃主题下使用真实 GlassCard，其他主题使用普通 Card。 */
@Composable
fun YunqueGlassCard(
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.CardColors = CardDefaults.cardColors(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val glass17 = Store.themeMode(context) == Store.THEME_GLASS17
    if (glass17) {
        val state = rememberLiquidGlassState()
        GlassCard(state = state, modifier = modifier, content = content)
    } else {
        Card(modifier = modifier, colors = colors) { content() }
    }
}

/** 统一主题入口：纸感/Monet/旧玻璃/玻璃17。 */
@Composable
fun GlassMaterialTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val mode = Store.themeMode(context)
    val base = when {
        mode == Store.THEME_LARK -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFE8A87C),
                onPrimary = Color(0xFF3B1B0A),
                primaryContainer = Color(0xFF5A3325),
                onPrimaryContainer = Color(0xFFFFDBC7),
                background = Color(0xFF1E1B16),
                surface = Color(0xFF26221C),
                surfaceVariant = Color(0xFF38332C),
                onSurface = Color(0xFFF0E9E2),
                onSurfaceVariant = Color(0xFFC7BAA8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFB45A28),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFE3D3),
                onPrimaryContainer = Color(0xFF3B1B0A),
                background = Color(0xFFFFF8F2),
                surface = Color(0xFFFFF4EA),
                surfaceVariant = Color(0xFFF4E4D4),
                onSurface = Color(0xFF2A211C),
                onSurfaceVariant = Color(0xFF6B5C4D)
            )
        }
        else -> if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) darkColorScheme() else lightColorScheme()
        }
    }
    val glassLike = mode == Store.THEME_GLASS || mode == Store.THEME_GLASS17
    val is17 = mode == Store.THEME_GLASS17
    val scheme = if (glassLike) {
        base.copy(
            surface = base.surface.copy(alpha = if (is17) 0.50f else 0.72f),
            surfaceVariant = base.surfaceVariant.copy(alpha = if (is17) 0.34f else 0.55f),
            background = base.background.copy(alpha = 0.94f),
            primaryContainer = base.primaryContainer.copy(alpha = if (is17) 0.52f else 0.68f),
            secondaryContainer = base.secondaryContainer.copy(alpha = if (is17) 0.45f else 0.60f),
            tertiaryContainer = base.tertiaryContainer.copy(alpha = if (is17) 0.45f else 0.60f)
        )
    } else {
        base
    }
    if (glassLike) {
        val wallpaperBitmap = remember(context) {
            WallpaperReader.load(context)
        }
        Box(Modifier.fillMaxSize()) {
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(if (is17) 36.dp else 28.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (is17) {
                            Brush.linearGradient(
                                listOf(
                                    base.primary.copy(alpha = 0.35f),
                                    base.tertiary.copy(alpha = 0.30f),
                                    base.surface.copy(alpha = 0.45f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    base.primary.copy(alpha = 0.20f),
                                    base.surface.copy(alpha = 0.75f),
                                    base.tertiary.copy(alpha = 0.12f)
                                )
                            )
                        }
                    )
            )
            MaterialTheme(colorScheme = scheme, content = content)
        }
    } else {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
