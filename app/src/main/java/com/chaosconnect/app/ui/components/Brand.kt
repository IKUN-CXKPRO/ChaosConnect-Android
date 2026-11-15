package com.chaosconnect.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaosconnect.app.R
import com.chaosconnect.app.ui.theme.LocalShellColors

@Composable
fun Label(text: String, size: Int = 14, muted: Boolean = false, bold: Boolean = false, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalShellColors.current
    BasicText(text, modifier, style = TextStyle(color = color ?: if (muted) c.muted else c.text,
        fontSize = size.sp, lineHeight = (size * 1.5f).sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal))
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.chaos_logo), "ChaosConnect 标志", modifier.clip(RoundedCornerShape(22.dp)).background(Color.White))
}

enum class Glyph { HOME, DEVICES, PROFILE, SETTINGS, BACK, NEXT, PLUS, CONNECT, INFO, PALETTE, CHECK, MOON, SHIELD }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, color: Color = LocalShellColors.current.text) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(x,y), Offset(a,b), 1.7f, StrokeCap.Round)
            fun path(vararg points: Pair<Float,Float>, close: Boolean = false) {
                val p = Path().apply { moveTo(points[0].first, points[0].second); points.drop(1).forEach { lineTo(it.first,it.second) }; if(close) close() }
                drawPath(p,color,style=stroke)
            }
            when(glyph) {
                Glyph.HOME -> { path(3f to 10f,12f to 3f,21f to 10f); path(5f to 9f,5f to 21f,10f to 21f,10f to 14f,14f to 14f,14f to 21f,19f to 21f,19f to 9f) }
                Glyph.DEVICES -> { drawRoundRect(color,Offset(7f,5f),androidx.compose.ui.geometry.Size(10f,14f),androidx.compose.ui.geometry.CornerRadius(3f),style=stroke); line(10f,1f,14f,1f);line(10f,23f,14f,23f);drawCircle(color,1.2f,Offset(12f,12f)) }
                Glyph.PROFILE -> { drawCircle(color,4f,Offset(12f,7f),style=stroke);drawArc(color,180f,180f,false,Offset(4f,14f),androidx.compose.ui.geometry.Size(16f,14f),style=stroke) }
                Glyph.SETTINGS -> { drawCircle(color,7f,Offset(12f,12f),style=stroke);drawCircle(color,2.5f,Offset(12f,12f),style=stroke);for(i in 0..7){val a=i*Math.PI/4;line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())} }
                Glyph.BACK -> {path(14f to 5f,7f to 12f,14f to 19f)}
                Glyph.NEXT -> path(9f to 6f,15f to 12f,9f to 18f)
                Glyph.PLUS -> { line(5f,12f,19f,12f);line(12f,5f,12f,19f) }
                Glyph.CONNECT -> {drawArc(color,210f,120f,false,Offset(2f,2f),androidx.compose.ui.geometry.Size(20f,20f),style=stroke);drawArc(color,210f,120f,false,Offset(6f,7f),androidx.compose.ui.geometry.Size(12f,12f),style=stroke);drawCircle(color,1.5f,Offset(12f,18f))}
                Glyph.INFO -> {drawCircle(color,9f,Offset(12f,12f),style=stroke);line(12f,11f,12f,17f);drawCircle(color,.9f,Offset(12f,7f))}
                Glyph.CHECK -> path(5f to 12f,10f to 17f,19f to 7f)
                Glyph.PALETTE -> {drawCircle(color,9f,Offset(12f,12f),style=stroke);drawCircle(color,1.5f,Offset(8f,8f));drawCircle(color,1.5f,Offset(15f,7f));drawCircle(color,1.5f,Offset(18f,13f));drawCircle(color,2.5f,Offset(9f,16f),style=stroke)}
                Glyph.MOON -> {drawArc(color,45f,270f,false,Offset(3f,3f),androidx.compose.ui.geometry.Size(18f,18f),style=stroke);path(18f to 5f,15f to 11f,19f to 17f)}
                Glyph.SHIELD -> path(12f to 2f,21f to 6f,19f to 16f,12f to 22f,5f to 16f,3f to 6f,12f to 2f)
            }
        }
    }
}

/** Static vector artwork: no work is scheduled while the screen is idle. */
@Composable
fun ConnectionArtwork(modifier: Modifier = Modifier) {
    val c = LocalShellColors.current
    Canvas(modifier) {
        val center = center
        val unit = size.minDimension / 2.3f
        repeat(3) { i -> drawCircle(c.accent.copy(alpha = .13f - i * .035f),unit * (.5f + i * .25f), center,style=Stroke(1.2.dp.toPx())) }
        val points = listOf(Offset(center.x-unit*.8f,center.y-unit*.45f),Offset(center.x+unit*.8f,center.y-unit*.45f),Offset(center.x,center.y+unit*.85f))
        points.forEach { p ->
            drawLine(c.accent.copy(alpha=.22f),center,p,1.2.dp.toPx())
            drawCircle(c.card,15.dp.toPx(),p)
            drawCircle(c.accent.copy(alpha=.15f),15.dp.toPx(),p,style=Stroke(1.dp.toPx()))
            drawCircle(c.accent,4.dp.toPx(),p)
        }
        drawCircle(c.accent.copy(alpha=.08f),36.dp.toPx(),center)
        drawCircle(c.accent,26.dp.toPx(),center)
        val p = Path().apply {moveTo(center.x-8.dp.toPx(),center.y);lineTo(center.x-1.dp.toPx(),center.y+6.dp.toPx());lineTo(center.x+10.dp.toPx(),center.y-7.dp.toPx())}
        drawPath(p,c.onAccent,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
    }
}
