package com.chaosconnect.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Only the three shell icons are bundled, avoiding an entire icon catalog. */
object ShellIcons {
    val Terminal = ImageVector.Builder("Terminal",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(5f,7f);lineTo(10f,12f);lineTo(5f,17f);moveTo(12f,17f);lineTo(20f,17f)
        }
    }.build()
    val Files = ImageVector.Builder("Files",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(3f,7f);lineTo(3f,20f);lineTo(21f,20f);lineTo(21f,7f);lineTo(12f,7f);lineTo(10f,4f);lineTo(3f,4f);close()
        }
    }.build()
    val Connect = ImageVector.Builder("Connect",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(3f,8f);curveTo(8f,2f,16f,2f,21f,8f)
            moveTo(6f,12f);curveTo(9f,8f,15f,8f,18f,12f)
            moveTo(9f,16f);curveTo(11f,14f,13f,14f,15f,16f)
            moveTo(12f,20f);lineTo(12f,20.2f)
        }
    }.build()
    val Home = ImageVector.Builder("Home",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(3f,10f);lineTo(12f,3f);lineTo(21f,10f)
            moveTo(5f,9f);lineTo(5f,21f);lineTo(10f,21f);lineTo(10f,14f);lineTo(14f,14f);lineTo(14f,21f);lineTo(19f,21f);lineTo(19f,9f)
        }
    }.build()
    val Devices = ImageVector.Builder("Devices",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(9f,5f);lineTo(15f,5f);lineTo(17f,7f);lineTo(17f,17f);lineTo(15f,19f);lineTo(9f,19f);lineTo(7f,17f);lineTo(7f,7f);close()
            moveTo(10f,1f);lineTo(14f,1f);moveTo(10f,23f);lineTo(14f,23f)
            moveTo(12f,11f);lineTo(12f,13f)
        }
    }.build()
    val Profile = ImageVector.Builder("Profile",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round) {
            moveTo(16f,7f);curveTo(16f,9.2f,14.2f,11f,12f,11f);curveTo(9.8f,11f,8f,9.2f,8f,7f)
            curveTo(8f,4.8f,9.8f,3f,12f,3f);curveTo(14.2f,3f,16f,4.8f,16f,7f);close()
            moveTo(4f,21f);curveTo(4f,12f,20f,12f,20f,21f)
        }
    }.build()
}
