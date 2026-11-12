package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ultra-compact vector icons (< 4 KB total) to avoid bundling material-icons-extended
 * and keep the release APK strictly under 2 MB.
 */
object FeatherIcons {

  val Shield: ImageVector by lazy {
    ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        lineTo(4f, 5f)
        verticalLineTo(11.09f)
        curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
        curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
        verticalLineTo(5f)
        lineTo(12f, 2f)
        close()
      }
    }.build()
  }

  val PrivateMask: ImageVector by lazy {
    ImageVector.Builder("PrivateMask", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 4f)
        curveTo(7f, 4f, 2.73f, 7.11f, 1f, 11.5f)
        curveTo(2.73f, 15.89f, 7f, 19f, 12f, 19f)
        curveTo(17f, 19f, 21.27f, 15.89f, 23f, 11.5f)
        curveTo(21.27f, 7.11f, 17f, 4f, 12f, 4f)
        close()
        moveTo(12f, 16.5f)
        curveTo(9.24f, 16.5f, 7f, 14.26f, 7f, 11.5f)
        curveTo(7f, 8.74f, 9.24f, 6.5f, 12f, 6.5f)
        curveTo(14.76f, 6.5f, 17f, 8.74f, 17f, 11.5f)
        curveTo(17f, 14.26f, 14.76f, 16.5f, 12f, 16.5f)
        close()
        moveTo(12f, 8.5f)
        curveTo(10.34f, 8.5f, 9f, 9.84f, 9f, 11.5f)
        curveTo(9f, 13.16f, 10.34f, 14.5f, 12f, 14.5f)
        curveTo(13.66f, 14.5f, 15f, 13.16f, 15f, 11.5f)
        curveTo(15f, 9.84f, 13.66f, 8.5f, 12f, 8.5f)
        close()
      }
    }.build()
  }

  val Bookmark: ImageVector by lazy {
    ImageVector.Builder("Bookmark", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(17f, 3f)
        horizontalLineTo(7f)
        curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
        verticalLineTo(21f)
        lineTo(12f, 18f)
        lineTo(19f, 21f)
        verticalLineTo(5f)
        curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
        close()
      }
    }.build()
  }

  val BookmarkOutline: ImageVector by lazy {
    ImageVector.Builder("BookmarkOutline", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(17f, 3f)
        horizontalLineTo(7f)
        curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
        verticalLineTo(21f)
        lineTo(12f, 18f)
        lineTo(19f, 21f)
        verticalLineTo(5f)
        curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
        close()
        moveTo(17f, 18f)
        lineTo(12f, 15.82f)
        lineTo(7f, 18f)
        verticalLineTo(5f)
        horizontalLineTo(17f)
        verticalLineTo(18f)
        close()
      }
    }.build()
  }

  val History: ImageVector by lazy {
    ImageVector.Builder("History", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(13f, 3f)
        curveTo(8.03f, 3f, 4f, 7.03f, 4f, 12f)
        horizontalLineTo(1f)
        lineTo(4.89f, 15.89f)
        lineTo(4.96f, 16.03f)
        lineTo(9f, 12f)
        horizontalLineTo(6f)
        curveTo(6f, 8.13f, 9.13f, 5f, 13f, 5f)
        curveTo(16.87f, 5f, 20f, 8.13f, 20f, 12f)
        curveTo(20f, 15.87f, 16.87f, 19f, 13f, 19f)
        curveTo(11.07f, 19f, 9.32f, 18.21f, 8.06f, 16.94f)
        lineTo(6.64f, 18.36f)
        curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
        curveTo(17.97f, 21f, 22f, 16.97f, 22f, 12f)
        curveTo(22f, 7.03f, 17.97f, 3f, 13f, 3f)
        close()
        moveTo(12f, 8f)
        verticalLineTo(13f)
        lineTo(16.28f, 15.54f)
        lineTo(17f, 14.33f)
        lineTo(13.5f, 12.25f)
        verticalLineTo(8f)
        horizontalLineTo(12f)
        close()
      }
    }.build()
  }

  val Download: ImageVector by lazy {
    ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 20f)
        horizontalLineTo(19f)
        verticalLineTo(18f)
        horizontalLineTo(5f)
        verticalLineTo(20f)
        close()
        moveTo(19f, 9f)
        horizontalLineTo(15f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineTo(9f)
        horizontalLineTo(5f)
        lineTo(12f, 16f)
        lineTo(19f, 9f)
        close()
      }
    }.build()
  }

  val Reader: ImageVector by lazy {
    ImageVector.Builder("Reader", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 3f)
        horizontalLineTo(5f)
        curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
        verticalLineTo(5f)
        curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
        close()
        moveTo(17f, 9f)
        horizontalLineTo(7f)
        verticalLineTo(7f)
        horizontalLineTo(17f)
        verticalLineTo(9f)
        close()
        moveTo(17f, 13f)
        horizontalLineTo(7f)
        verticalLineTo(11f)
        horizontalLineTo(17f)
        verticalLineTo(13f)
        close()
        moveTo(14f, 17f)
        horizontalLineTo(7f)
        verticalLineTo(15f)
        horizontalLineTo(14f)
        verticalLineTo(17f)
        close()
      }
    }.build()
  }

  val Copy: ImageVector by lazy {
    ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 1f)
        horizontalLineTo(4f)
        curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
        verticalLineTo(17f)
        horizontalLineTo(4f)
        verticalLineTo(3f)
        horizontalLineTo(16f)
        verticalLineTo(1f)
        close()
        moveTo(19f, 5f)
        horizontalLineTo(8f)
        curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
        verticalLineTo(21f)
        curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
        horizontalLineTo(19f)
        curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
        verticalLineTo(7f)
        curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
        close()
        moveTo(19f, 21f)
        horizontalLineTo(8f)
        verticalLineTo(7f)
        horizontalLineTo(19f)
        verticalLineTo(21f)
        close()
      }
    }.build()
  }

  val OpenInNew: ImageVector by lazy {
    ImageVector.Builder("OpenInNew", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 19f)
        horizontalLineTo(5f)
        verticalLineTo(5f)
        horizontalLineTo(12f)
        verticalLineTo(3f)
        horizontalLineTo(5f)
        curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
        verticalLineTo(12f)
        horizontalLineTo(19f)
        verticalLineTo(19f)
        close()
        moveTo(14f, 3f)
        verticalLineTo(5f)
        horizontalLineTo(17.59f)
        lineTo(7.76f, 14.83f)
        lineTo(9.17f, 16.24f)
        lineTo(19f, 6.41f)
        verticalLineTo(10f)
        horizontalLineTo(21f)
        verticalLineTo(3f)
        horizontalLineTo(14f)
        close()
      }
    }.build()
  }

  val NightMoon: ImageVector by lazy {
    ImageVector.Builder("NightMoon", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 3f)
        curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
        curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
        curveTo(15.32f, 21f, 18.21f, 19.2f, 19.77f, 16.53f)
        curveTo(18.67f, 16.84f, 17.5f, 17f, 16.29f, 17f)
        curveTo(11.71f, 17f, 8f, 13.29f, 8f, 8.71f)
        curveTo(8f, 6.53f, 8.84f, 4.55f, 10.22f, 3.07f)
        curveTo(10.8f, 3.02f, 11.4f, 3f, 12f, 3f)
        close()
      }
    }.build()
  }

  val NoImage: ImageVector by lazy {
    ImageVector.Builder("NoImage", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(21f, 5f)
        verticalLineTo(16.59f)
        lineTo(19f, 14.59f)
        verticalLineTo(5f)
        horizontalLineTo(9.41f)
        lineTo(7.41f, 3f)
        horizontalLineTo(19f)
        curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
        close()
        moveTo(2.81f, 2.81f)
        lineTo(1.39f, 4.22f)
        lineTo(3f, 5.83f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
        horizontalLineTo(18.17f)
        lineTo(19.78f, 22.61f)
        lineTo(21.19f, 21.19f)
        lineTo(2.81f, 2.81f)
        close()
        moveTo(5f, 19f)
        verticalLineTo(7.83f)
        lineTo(10.59f, 13.42f)
        lineTo(9f, 15.5f)
        lineTo(6.5f, 12.5f)
        lineTo(5f, 19f)
        close()
      }
    }.build()
  }

  val CodeSource: ImageVector by lazy {
    ImageVector.Builder("CodeSource", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(9.4f, 16.6f)
        lineTo(4.8f, 12f)
        lineTo(9.4f, 7.4f)
        lineTo(8f, 6f)
        lineTo(2f, 12f)
        lineTo(8f, 18f)
        lineTo(9.4f, 16.6f)
        close()
        moveTo(14.6f, 16.6f)
        lineTo(19.2f, 12f)
        lineTo(14.6f, 7.4f)
        lineTo(16f, 6f)
        lineTo(22f, 12f)
        lineTo(16f, 18f)
        lineTo(14.6f, 16.6f)
        close()
      }
    }.build()
  }

  val PrintPdf: ImageVector by lazy {
    ImageVector.Builder("PrintPdf", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 8f)
        horizontalLineTo(5f)
        curveTo(3.34f, 8f, 2f, 9.34f, 2f, 11f)
        verticalLineTo(17f)
        horizontalLineTo(6f)
        verticalLineTo(21f)
        horizontalLineTo(18f)
        verticalLineTo(17f)
        horizontalLineTo(22f)
        verticalLineTo(11f)
        curveTo(22f, 9.34f, 20.66f, 8f, 19f, 8f)
        close()
        moveTo(16f, 19f)
        horizontalLineTo(8f)
        verticalLineTo(14f)
        horizontalLineTo(16f)
        verticalLineTo(19f)
        close()
        moveTo(18f, 3f)
        horizontalLineTo(6f)
        verticalLineTo(7f)
        horizontalLineTo(18f)
        verticalLineTo(3f)
        close()
      }
    }.build()
  }

  val MediaSniff: ImageVector by lazy {
    ImageVector.Builder("MediaSniff", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 6f)
        horizontalLineTo(2f)
        verticalLineTo(20f)
        curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
        horizontalLineTo(18f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
        moveTo(20f, 2f)
        horizontalLineTo(8f)
        curveTo(6.9f, 2f, 6f, 2.9f, 6f, 4f)
        verticalLineTo(16f)
        curveTo(6f, 17.1f, 6.9f, 18f, 8f, 18f)
        horizontalLineTo(20f)
        curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
        verticalLineTo(4f)
        curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
        close()
        moveTo(12f, 14.5f)
        verticalLineTo(5.5f)
        lineTo(18f, 10f)
        lineTo(12f, 14.5f)
        close()
      }
    }.build()
  }

  val Fullscreen: ImageVector by lazy {
    ImageVector.Builder("Fullscreen", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 14f)
        horizontalLineTo(5f)
        verticalLineTo(19f)
        horizontalLineTo(10f)
        verticalLineTo(17f)
        horizontalLineTo(7f)
        verticalLineTo(14f)
        close()
        moveTo(5f, 10f)
        horizontalLineTo(7f)
        verticalLineTo(7f)
        horizontalLineTo(10f)
        verticalLineTo(5f)
        horizontalLineTo(5f)
        verticalLineTo(10f)
        close()
        moveTo(17f, 17f)
        horizontalLineTo(14f)
        verticalLineTo(19f)
        horizontalLineTo(19f)
        verticalLineTo(14f)
        horizontalLineTo(17f)
        verticalLineTo(17f)
        close()
        moveTo(14f, 5f)
        verticalLineTo(7f)
        horizontalLineTo(17f)
        verticalLineTo(10f)
        horizontalLineTo(19f)
        verticalLineTo(5f)
        horizontalLineTo(14f)
        close()
      }
    }.build()
  }

  val DesktopPc: ImageVector by lazy {
    ImageVector.Builder("DesktopPc", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(21f, 2f)
        horizontalLineTo(3f)
        curveTo(1.9f, 2f, 1f, 2.9f, 1f, 4f)
        verticalLineTo(16f)
        curveTo(1f, 17.1f, 1.9f, 18f, 3f, 18f)
        horizontalLineTo(10f)
        lineTo(8f, 21f)
        verticalLineTo(22f)
        horizontalLineTo(16f)
        verticalLineTo(21f)
        lineTo(14f, 18f)
        horizontalLineTo(21f)
        curveTo(22.1f, 18f, 23f, 17.1f, 23f, 16f)
        verticalLineTo(4f)
        curveTo(23f, 2.9f, 22.1f, 2f, 21f, 2f)
        close()
        moveTo(21f, 16f)
        horizontalLineTo(3f)
        verticalLineTo(4f)
        horizontalLineTo(21f)
        verticalLineTo(16f)
        close()
      }
    }.build()
  }

  val Translate: ImageVector by lazy {
    ImageVector.Builder("Translate", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12.87f, 15.07f)
        lineTo(10.33f, 12.56f)
        lineTo(10.36f, 12.53f)
        curveTo(12.1f, 10.59f, 13.34f, 8.36f, 14.07f, 6f)
        horizontalLineTo(17f)
        verticalLineTo(4f)
        horizontalLineTo(10f)
        verticalLineTo(2f)
        horizontalLineTo(8f)
        verticalLineTo(4f)
        horizontalLineTo(1f)
        verticalLineTo(6f)
        horizontalLineTo(12.17f)
        curveTo(11.5f, 7.92f, 10.44f, 9.75f, 9f, 11.35f)
        curveTo(8.07f, 10.32f, 7.3f, 9.19f, 6.69f, 8f)
        horizontalLineTo(4.69f)
        curveTo(5.42f, 9.63f, 6.42f, 11.17f, 7.67f, 12.56f)
        lineTo(2.58f, 17.58f)
        lineTo(4f, 19f)
        lineTo(9f, 14f)
        lineTo(12.11f, 17.11f)
        lineTo(12.87f, 15.07f)
        close()
        moveTo(18.5f, 10f)
        horizontalLineTo(16.5f)
        lineTo(12f, 22f)
        horizontalLineTo(14f)
        lineTo(15.12f, 19f)
        horizontalLineTo(19.87f)
        lineTo(21f, 22f)
        horizontalLineTo(23f)
        lineTo(18.5f, 10f)
        close()
        moveTo(15.88f, 17f)
        lineTo(17.5f, 12.67f)
        lineTo(19.12f, 17f)
        horizontalLineTo(15.88f)
        close()
      }
    }.build()
  }

  val FeatherLogo: ImageVector by lazy {
    ImageVector.Builder("BatCrest", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(12f, 9.5f)
        horizontalLineTo(10.4f)
        lineTo(9.4f, 5.5f)
        lineTo(8.1f, 9.8f)
        curveTo(6.4f, 10.3f, 4.4f, 9.2f, 1.5f, 6.8f)
        curveTo(2.4f, 10.4f, 3.8f, 13.0f, 5.7f, 14.3f)
        curveTo(6.8f, 15.1f, 7.6f, 16.8f, 8.2f, 17.8f)
        curveTo(9.2f, 16.5f, 10.2f, 16.4f, 11.1f, 17.5f)
        lineTo(12f, 19.6f)
        lineTo(12.9f, 17.5f)
        curveTo(13.8f, 16.4f, 14.8f, 16.5f, 15.8f, 17.8f)
        curveTo(16.4f, 16.8f, 17.2f, 15.1f, 18.3f, 14.3f)
        curveTo(20.2f, 13.0f, 21.6f, 10.4f, 22.5f, 6.8f)
        curveTo(19.6f, 9.2f, 17.6f, 10.3f, 15.9f, 9.8f)
        lineTo(14.6f, 5.5f)
        lineTo(13.6f, 9.5f)
        close()
      }
    }.build()
  }
}
