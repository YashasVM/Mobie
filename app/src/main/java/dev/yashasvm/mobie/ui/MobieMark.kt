package dev.yashasvm.mobie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashasvm.mobie.ui.theme.MobieTheme

// The Mobie mark: a lowercase "m" made of two arches, followed by a full stop, on a soft amber
// tile. The "m." reads as a finished sentence typed on the phone itself, which is the product:
// a private assistant that answers locally. Geometry is on a 24-unit grid; the launcher icon
// (res/drawable/ic_launcher_foreground.xml) uses the same glyph paths.

private const val TILE_PATH =
    "M10,2.5H14A7.5,7.5 0 0 1 21.5,10V14A7.5,7.5 0 0 1 14,21.5H10A7.5,7.5 0 0 1 2.5,14V10A7.5,7.5 0 0 1 10,2.5Z"
private const val M_PATH =
    "M5.05,15.1V11.1A3.35,3.35 0 0 1 10.6,8.574A3.35,3.35 0 0 1 16.15,11.1V15.1A1.15,1.15 0 0 1 13.85,15.1V11.1" +
        "A1.05,1.05 0 0 0 11.75,11.1V15.1A1.15,1.15 0 0 1 9.45,15.1V11.1A1.05,1.05 0 0 0 7.35,11.1V15.1" +
        "A1.15,1.15 0 0 1 5.05,15.1Z"
private const val DOT_PATH = "M16.45,15.1A1.25,1.25 0 1 1 18.95,15.1A1.25,1.25 0 1 1 16.45,15.1Z"

/** Brand colors. Fixed across light and dark themes so the mark is always recognizable. */
private val MarkAmber = Color(0xFFF2A93B)
private val MarkInk = Color(0xFF1B1A17)

private val MobieMarkVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "MobieMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(addPathNodes(TILE_PATH), fill = SolidColor(MarkAmber))
        .addPath(addPathNodes(M_PATH), fill = SolidColor(MarkInk))
        .addPath(addPathNodes(DOT_PATH), fill = SolidColor(MarkInk))
        .build()
}

/** The Mobie logo mark. Legible from 16dp up; the tile carries its own colors on any background. */
@Composable
fun MobieMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Image(
        painter = rememberVectorPainter(MobieMarkVector),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Mobie" },
    )
}

/** Mark plus the "Mobie" name, for welcome screens, drawer headers, and About. */
@Composable
fun MobieWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = rememberVectorPainter(MobieMarkVector),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(
            "Mobie",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "Mark sizes · dark", showBackground = true, backgroundColor = 0xFF1A1A18)
@Composable
private fun MobieMarkDarkPreview() {
    MobieTheme(darkTheme = true) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MobieMark(size = 16.dp)
                    MobieMark(size = 24.dp)
                    MobieMark()
                    MobieMark(size = 48.dp)
                    MobieMark(size = 96.dp)
                }
                MobieWordmark()
            }
        }
    }
}

@Preview(name = "Mark sizes · light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun MobieMarkLightPreview() {
    MobieTheme(darkTheme = false) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MobieMark(size = 24.dp)
                    MobieMark(size = 48.dp)
                }
                MobieWordmark()
            }
        }
    }
}
