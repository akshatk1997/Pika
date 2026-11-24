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

  val SunLight: ImageVector by lazy {
    ImageVector.Builder("SunLight", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 7f)
        curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
        curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
        curveTo(17f, 17f, 17f, 14.76f, 17f, 12f)
        curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
        close()
        moveTo(13f, 2f)
        horizontalLineTo(11f)
        verticalLineTo(5f)
        horizontalLineTo(13f)
        verticalLineTo(2f)
        close()
        moveTo(13f, 19f)
        horizontalLineTo(11f)
        verticalLineTo(22f)
        horizontalLineTo(13f)
        verticalLineTo(19f)
        close()
        moveTo(22f, 11f)
        verticalLineTo(13f)
        horizontalLineTo(19f)
        verticalLineTo(11f)
        horizontalLineTo(22f)
        close()
        moveTo(5f, 11f)
        verticalLineTo(13f)
        horizontalLineTo(2f)
        verticalLineTo(11f)
        horizontalLineTo(5f)
        close()
        moveTo(18.36f, 4.22f)
        lineTo(19.78f, 5.64f)
        lineTo(17.66f, 7.76f)
        lineTo(16.24f, 6.34f)
        lineTo(18.36f, 4.22f)
        close()
        moveTo(6.34f, 16.24f)
        lineTo(7.76f, 17.66f)
        lineTo(5.64f, 19.78f)
        lineTo(4.22f, 18.36f)
        lineTo(6.34f, 16.24f)
        close()
        moveTo(19.78f, 18.36f)
        lineTo(18.36f, 19.78f)
        lineTo(16.24f, 17.66f)
        lineTo(17.66f, 16.24f)
        lineTo(19.78f, 18.36f)
        close()
        moveTo(7.76f, 6.34f)
        lineTo(6.34f, 4.22f)
        lineTo(4.22f, 5.64f)
        lineTo(5.64f, 7.76f)
        lineTo(7.76f, 6.34f)
        close()
      }
    }.build()
  }

  val DataSaver: ImageVector by lazy {
    ImageVector.Builder("DataSaver", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
        curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
        curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
        curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
        close()
        moveTo(11f, 7f)
        horizontalLineTo(13f)
        verticalLineTo(11f)
        horizontalLineTo(17f)
        verticalLineTo(13f)
        horizontalLineTo(13f)
        verticalLineTo(17f)
        horizontalLineTo(11f)
        verticalLineTo(13f)
        horizontalLineTo(7f)
        verticalLineTo(11f)
        horizontalLineTo(11f)
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

  val Extension: ImageVector by lazy {
    ImageVector.Builder("Extension", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(20.5f, 11f)
        horizontalLineTo(19f)
        verticalLineTo(7f)
        curveTo(19f, 5.9f, 18.1f, 5f, 17f, 5f)
        horizontalLineTo(13f)
        verticalLineTo(3.5f)
        curveTo(13f, 2.12f, 11.88f, 1f, 10.5f, 1f)
        curveTo(9.12f, 1f, 8f, 2.12f, 8f, 3.5f)
        verticalLineTo(5f)
        horizontalLineTo(4f)
        curveTo(2.9f, 5f, 2.01f, 5.9f, 2.01f, 7f)
        verticalLineTo(10.8f)
        horizontalLineTo(3.5f)
        curveTo(4.99f, 10.8f, 6.2f, 12.01f, 6.2f, 13.5f)
        curveTo(6.2f, 14.99f, 4.99f, 16.2f, 3.5f, 16.2f)
        horizontalLineTo(2f)
        verticalLineTo(20f)
        curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
        horizontalLineTo(7.8f)
        verticalLineTo(20.5f)
        curveTo(7.8f, 19.01f, 9.01f, 17.8f, 10.5f, 17.8f)
        curveTo(11.99f, 17.8f, 13.2f, 19.01f, 13.2f, 20.5f)
        verticalLineTo(22f)
        horizontalLineTo(17f)
        curveTo(18.1f, 22f, 19f, 21.1f, 19f, 20f)
        verticalLineTo(16f)
        horizontalLineTo(20.5f)
        curveTo(21.88f, 16f, 23f, 14.88f, 23f, 13.5f)
        curveTo(23f, 12.12f, 21.88f, 11f, 20.5f, 11f)
        close()
      }
    }.build()
  }

  val Announcer: ImageVector by lazy {
    ImageVector.Builder("Announcer", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 9f)
        verticalLineTo(15f)
        horizontalLineTo(7f)
        lineTo(12f, 20f)
        verticalLineTo(4f)
        lineTo(7f, 9f)
        horizontalLineTo(3f)
        close()
        moveTo(16.5f, 12f)
        curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
        verticalLineTo(16.02f)
        curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
        close()
        moveTo(14f, 3.23f)
        verticalLineTo(5.29f)
        curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
        curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
        verticalLineTo(20.77f)
        curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
        curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
        close()
      }
    }.build()
  }

  val Pause: ImageVector by lazy {
    ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 19f)
        horizontalLineTo(10f)
        verticalLineTo(5f)
        horizontalLineTo(6f)
        verticalLineTo(19f)
        close()
        moveTo(14f, 5f)
        verticalLineTo(19f)
        horizontalLineTo(18f)
        verticalLineTo(5f)
        horizontalLineTo(14f)
        close()
      }
    }.build()
  }

  val SkipPrev: ImageVector by lazy {
    ImageVector.Builder("SkipPrev", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 6f)
        horizontalLineTo(8f)
        verticalLineTo(18f)
        horizontalLineTo(6f)
        close()
        moveTo(9.5f, 12f)
        lineTo(18f, 18f)
        verticalLineTo(6f)
        close()
      }
    }.build()
  }

  val SkipNext: ImageVector by lazy {
    ImageVector.Builder("SkipNext", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 18f)
        lineTo(14.5f, 12f)
        lineTo(6f, 6f)
        verticalLineTo(18f)
        close()
        moveTo(16f, 6f)
        verticalLineTo(18f)
        horizontalLineTo(18f)
        verticalLineTo(6f)
        horizontalLineTo(16f)
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
    ImageVector.Builder("KioLogo", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(12f, 2.5f)
        curveTo(17.25f, 2.5f, 21.5f, 6.75f, 21.5f, 12f)
        curveTo(21.5f, 17.25f, 17.25f, 21.5f, 12f, 21.5f)
        curveTo(6.75f, 21.5f, 2.5f, 17.25f, 2.5f, 12f)
        curveTo(2.5f, 6.75f, 6.75f, 2.5f, 12f, 2.5f)
        close()
      }
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(12f, 6.5f)
        curveTo(15.04f, 6.5f, 17.5f, 8.96f, 17.5f, 12f)
        curveTo(17.5f, 15.04f, 15.04f, 17.5f, 12f, 17.5f)
        curveTo(8.96f, 17.5f, 6.5f, 15.04f, 6.5f, 12f)
        curveTo(6.5f, 8.96f, 8.96f, 6.5f, 12f, 6.5f)
        close()
      }
      path(
        fill = SolidColor(Color.Black)
      ) {
        moveTo(12f, 9.8f)
        curveTo(13.22f, 9.8f, 14.2f, 10.78f, 14.2f, 12f)
        curveTo(14.2f, 13.22f, 13.22f, 14.2f, 12f, 14.2f)
        curveTo(10.78f, 14.2f, 9.8f, 13.22f, 9.8f, 12f)
        curveTo(9.8f, 10.78f, 10.78f, 9.8f, 12f, 9.8f)
        close()
      }
    }.build()
  }

  val Help: ImageVector by lazy {
    ImageVector.Builder("Help", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        moveTo(13f, 19f)
        horizontalLineTo(11f)
        verticalLineTo(17f)
        horizontalLineTo(13f)
        verticalLineTo(19f)
        close()
        moveTo(15.07f, 11.25f)
        lineTo(14.17f, 12.17f)
        curveTo(13.45f, 12.9f, 13f, 13.5f, 13f, 15f)
        horizontalLineTo(11f)
        verticalLineTo(14.5f)
        curveTo(11f, 13.4f, 11.45f, 12.4f, 12.17f, 11.67f)
        lineTo(13.41f, 10.41f)
        curveTo(13.78f, 10.05f, 14f, 9.55f, 14f, 9f)
        curveTo(14f, 7.9f, 13.1f, 7f, 12f, 7f)
        curveTo(10.9f, 7f, 10f, 7.9f, 10f, 9f)
        horizontalLineTo(8f)
        curveTo(8f, 6.79f, 9.79f, 5f, 12f, 5f)
        curveTo(14.21f, 5f, 16f, 6.79f, 16f, 9f)
        curveTo(16f, 9.88f, 15.64f, 10.68f, 15.07f, 11.25f)
        close()
      }
    }.build()
  }

  val Play: ImageVector by lazy {
    ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 5f)
        verticalLineTo(19f)
        lineTo(19f, 12f)
        close()
      }
    }.build()
  }

  val BurnFlame: ImageVector by lazy {
    ImageVector.Builder("BurnFlame", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(13.5f, 0.67f)
        curveTo(13.5f, 0.67f, 14.24f, 3.32f, 14.24f, 5.47f)
        curveTo(14.24f, 7.53f, 12.89f, 9.2f, 10.83f, 9.2f)
        curveTo(8.76f, 9.2f, 7.2f, 7.53f, 7.2f, 5.47f)
        lineTo(7.23f, 5.11f)
        curveTo(5.21f, 7.51f, 4f, 10.62f, 4f, 14f)
        curveTo(4f, 18.42f, 7.58f, 22f, 12f, 22f)
        curveTo(16.42f, 22f, 20f, 18.42f, 20f, 14f)
        curveTo(20f, 8.61f, 17.41f, 3.8f, 13.5f, 0.67f)
        close()
        moveTo(11.71f, 19f)
        curveTo(9.93f, 19f, 8.49f, 17.6f, 8.49f, 15.84f)
        curveTo(8.49f, 14.22f, 9.54f, 13.08f, 11.3f, 12.72f)
        curveTo(13.07f, 12.36f, 14.9f, 11.51f, 15.92f, 10.14f)
        curveTo(16.31f, 11.43f, 16.51f, 12.79f, 16.51f, 14.2f)
        curveTo(16.51f, 16.85f, 14.36f, 19f, 11.71f, 19f)
        close()
      }
    }.build()
  }

  val BroomWipe: ImageVector by lazy {
    ImageVector.Builder("BroomWipe", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(19.36f, 2.72f)
        lineTo(20.78f, 4.14f)
        lineTo(15.06f, 9.85f)
        lineTo(17.18f, 11.97f)
        lineTo(15.77f, 13.38f)
        lineTo(14.36f, 11.97f)
        lineTo(10.82f, 15.51f)
        lineTo(12.23f, 16.92f)
        lineTo(10.82f, 18.34f)
        lineTo(4.46f, 11.97f)
        lineTo(5.87f, 10.56f)
        lineTo(7.29f, 11.97f)
        lineTo(10.82f, 8.44f)
        lineTo(9.41f, 7.03f)
        lineTo(10.82f, 5.61f)
        lineTo(12.94f, 7.73f)
        lineTo(19.36f, 2.72f)
        close()
        moveTo(3f, 20f)
        horizontalLineTo(21f)
        verticalLineTo(22f)
        horizontalLineTo(3f)
        verticalLineTo(20f)
        close()
      }
    }.build()
  }

  val Video: ImageVector by lazy {
    ImageVector.Builder("Video", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(17f, 10.5f)
        verticalLineTo(7f)
        curveTo(17f, 6.45f, 16.55f, 6f, 16f, 6f)
        horizontalLineTo(4f)
        curveTo(3.45f, 6f, 3f, 6.45f, 3f, 7f)
        verticalLineTo(17f)
        curveTo(3f, 17.55f, 3.45f, 18f, 4f, 18f)
        horizontalLineTo(16f)
        curveTo(16.55f, 18f, 17f, 17.55f, 17f, 17f)
        verticalLineTo(13.5f)
        lineTo(21f, 17.5f)
        verticalLineTo(6.5f)
        lineTo(17f, 10.5f)
        close()
      }
    }.build()
  }

  val ImageBox: ImageVector by lazy {
    ImageVector.Builder("ImageBox", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(21f, 19f)
        verticalLineTo(5f)
        curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
        horizontalLineTo(5f)
        curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
        verticalLineTo(19f)
        curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
        close()
        moveTo(8.5f, 13.5f)
        lineTo(11f, 16.51f)
        lineTo(14.5f, 12f)
        lineTo(19f, 18f)
        horizontalLineTo(5f)
        lineTo(8.5f, 13.5f)
        close()
      }
    }.build()
  }

  val CameraCapture: ImageVector by lazy {
    ImageVector.Builder("CameraCapture", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 2f)
        lineTo(7.17f, 4f)
        horizontalLineTo(4f)
        curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
        verticalLineTo(18f)
        curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
        horizontalLineTo(20f)
        curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
        verticalLineTo(6f)
        curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
        horizontalLineTo(16.83f)
        lineTo(15f, 2f)
        horizontalLineTo(9f)
        close()
        moveTo(12f, 17f)
        curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
        curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
        curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
        curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
        close()
      }
    }.build()
  }

  val EyeFilter: ImageVector by lazy {
    ImageVector.Builder("EyeFilter", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 4.5f)
        curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
        curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
        curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
        curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
        close()
        moveTo(12f, 17f)
        curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
        curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
        curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
        curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
        close()
      }
    }.build()
  }

  val OneHand: ImageVector by lazy {
    ImageVector.Builder("OneHand", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(17f, 1f)
        lineTo(7f, 1f)
        curveTo(5.9f, 1f, 5f, 1.9f, 5f, 3f)
        verticalLineTo(21f)
        curveTo(5f, 22.1f, 5.9f, 23f, 7f, 23f)
        horizontalLineTo(17f)
        curveTo(18.1f, 23f, 19f, 22.1f, 19f, 21f)
        verticalLineTo(3f)
        curveTo(19f, 1.9f, 18.1f, 1f, 17f, 1f)
        close()
        moveTo(17f, 19f)
        horizontalLineTo(7f)
        verticalLineTo(5f)
        horizontalLineTo(17f)
        verticalLineTo(19f)
        close()
        moveTo(12f, 16f)
        lineTo(8.5f, 12.5f)
        lineTo(9.91f, 11.09f)
        lineTo(11f, 12.17f)
        verticalLineTo(8f)
        horizontalLineTo(13f)
        verticalLineTo(12.17f)
        lineTo(14.09f, 11.09f)
        lineTo(15.5f, 12.5f)
        lineTo(12f, 16f)
        close()
      }
    }.build()
  }

  val Grayscale: ImageVector by lazy {
    ImageVector.Builder("Grayscale", 24.dp, 24.dp, 24f, 24f).apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        verticalLineTo(4f)
        curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
        curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
        close()
      }
    }.build()
  }
}
