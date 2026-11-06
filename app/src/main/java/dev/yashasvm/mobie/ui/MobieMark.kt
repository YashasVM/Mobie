package dev.yashasvm.mobie.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Mobie brand mark used in the chats drawer and the empty chat home. Placeholder wordmark;
 * the final logo replaces this body and keeps the signature. [size] is the mark's height.
 */
@Composable
fun MobieMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Text(
        "Mobie",
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = (size.value * .72f).sp,
            lineHeight = size.value.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}
