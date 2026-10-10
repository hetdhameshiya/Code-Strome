package com.codestrome.app.ui.theme   // <- change to your own package name

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Risk levels shown with ONLY the two brand colours.
 * Colour is never the only signal: every level also has its own icon and word,
 * so it also works for colour-blind users and in grayscale.
 *
 *  SAFE        calm: neutral card, cream text, check icon
 *  SUSPICIOUS  attention: neutral card with a red outline and red text, warning icon
 *  DANGEROUS   alarm: solid red card with dark text, warning icon
 */
enum class RiskTone { SAFE, SUSPICIOUS, DANGEROUS }

data class RiskStyle(
    val labelKey: String,        // map this to a string resource (EN / GU / HI)
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val border: Color,
)

@Suppress("unused")
fun riskStyle(tone: RiskTone): RiskStyle = when (tone) {
    RiskTone.SAFE -> RiskStyle("risk_safe", Icons.Filled.CheckCircle, CsCard, CsCream, CsBorder)
    RiskTone.SUSPICIOUS -> RiskStyle("risk_suspicious", Icons.Filled.Warning, CsCard, CsRed, CsRed)
    RiskTone.DANGEROUS -> RiskStyle("risk_dangerous", Icons.Filled.Warning, CsRed, CsDark, CsRed)
}
