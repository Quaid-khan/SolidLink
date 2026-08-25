package com.solidlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val SolidLinkNavy = Color(0xFF071B3A)
private val SolidLinkTeal = Color(0xFF208C9A)
private val SolidLinkBlue = Color(0xFF0867C6)
private val SolidLinkCanvas = Color(0xFFFCFCFD)
private val SolidLinkMuted = Color(0xFF637083)

private val SolidLinkLightColors = lightColorScheme(
    primary = SolidLinkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F4F6),
    onPrimaryContainer = SolidLinkNavy,
    secondary = SolidLinkTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F3F5),
    onSecondaryContainer = SolidLinkNavy,
    background = SolidLinkCanvas,
    onBackground = SolidLinkNavy,
    surface = Color.White,
    onSurface = SolidLinkNavy,
    surfaceVariant = Color(0xFFF1F4F7),
    onSurfaceVariant = SolidLinkMuted,
    outline = Color(0xFFD9E0E7),
)

private val SolidLinkShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val SolidLinkTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(color = SolidLinkNavy),
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SolidLinkTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun SolidLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolidLinkLightColors,
        typography = SolidLinkTypography,
        shapes = SolidLinkShapes,
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    SolidLinkTheme {
        HomeScreen()
    }
}
