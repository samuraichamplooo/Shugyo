package dev.shugyo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sumi ink, washi paper and a vermilion seal, like Yomiage, with a few game colors on top.
val Ink = Color(0xFF0E0D0C)
val Card = Color(0xFF181614)
val CardHigh = Color(0xFF231F1C)
val Line = Color(0xFF3A3530)
val Hairline = Color(0x22FFFFFF)
val Paper = Color(0xFFF3EDE4)
val Soft = Color(0xFFD9D1C8)
val Faint = Color(0xFF9A928A)
val Seal = Color(0xFFD8462B)
val SealDeep = Color(0xFF5A1E12)
val Gold = Color(0xFFE8B04A)
val Good = Color(0xFF8FD694)
val GoodDeep = Color(0xFF1F3A24)
val Bad = Color(0xFFFF8A75)
val BadDeep = Color(0xFF4A1F18)

// One color per way of studying.
val LearnColor = Color(0xFF7C9BE0)
val CardsColor = Gold
val QuizColor = Seal
val NotesColor = Color(0xFF5BBFB3)
val BattleColor = Color(0xFFB487E0)

val Mincho = FontFamily.Serif

private val scheme = darkColorScheme(
    primary = Seal,
    onPrimary = Paper,
    primaryContainer = SealDeep,
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Gold,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Card,
    onSurface = Paper,
    surfaceVariant = CardHigh,
    onSurfaceVariant = Soft,
    surfaceContainer = Card,
    surfaceContainerHigh = CardHigh,
    surfaceContainerHighest = CardHigh,
    surfaceContainerLow = Card,
    outline = Line,
    outlineVariant = Hairline,
    error = Bad,
)

@Composable
fun ShugyoTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val type = base.copy(
        headlineMedium = base.headlineMedium.copy(fontFamily = Mincho, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Mincho, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Mincho, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Faint)
