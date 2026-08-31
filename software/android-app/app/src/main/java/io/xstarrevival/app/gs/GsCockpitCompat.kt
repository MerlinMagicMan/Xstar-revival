package io.xstarrevival.app.gs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Three-argument overload keeps call sites concise while the accented variant lives in GsCockpit. */
@Composable
internal fun GsRailButton(glyph: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp)
            .background(Color.Black.copy(alpha = .64f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = GsColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = GsColors.Muted, fontSize = 8.sp)
    }
}
