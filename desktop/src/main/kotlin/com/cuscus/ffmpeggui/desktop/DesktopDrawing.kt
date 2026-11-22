package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.StrokeShape
import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import kotlin.math.*

/** Java2D counterpart of the original Android StrokePainter, transparent drawing only. */
object DesktopDrawing {
    fun render(strokes:List<Stroke>,width:Int,height:Int):BufferedImage {
        require(width in 1..4096 && height in 1..4096)
        val image=BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB)
        val g=image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            for(s in strokes) {
                if(s.points.size<2 || s.points.size%2!=0)continue
                require(s.points.all {it.isFinite()} && s.width.isFinite()) {"Invalid drawing coordinates"}
                val line=max(1f,s.width.coerceIn(.0001f,1f)*width)
                g.stroke=BasicStroke(line,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
                g.composite=if(s.eraser)AlphaComposite.Clear else AlphaComposite.SrcOver
                val argb=s.color.toInt();val alpha=(((argb ushr 24) and 255)*s.opacity.coerceIn(0f,1f)).toInt()
                g.color=Color((alpha shl 24) or (argb and 0xffffff),true)
                val xy=s.points.chunked(2).map {Point2D.Float(it[0]*width,it[1]*height)}
                val a=xy.first();val b=xy.last()
                if(xy.size==1 || (s.shape==StrokeShape.FREE && xy.size==2 && a==b)) {g.fill(Ellipse2D.Float(a.x-line/2,a.y-line/2,line,line));continue}
                val shape=if(s.eraser)StrokeShape.FREE else s.shape
                when(shape) {
                    StrokeShape.FREE->{val path=Path2D.Float();path.moveTo(a.x,a.y);var last=a;xy.drop(1).forEach {p->path.quadTo(last.x,last.y,(last.x+p.x)/2,(last.y+p.y)/2);last=p};path.lineTo(last.x,last.y);g.draw(path)}
                    StrokeShape.LINE->g.draw(Line2D.Float(a,b))
                    StrokeShape.ARROW->{val head=max(line*3,min(width,height)*.035f);val angle=atan2(b.y-a.y,b.x-a.x);val path=Path2D.Float();path.moveTo(a.x,a.y);path.lineTo(b.x,b.y);path.moveTo(b.x-head*cos(angle-.5f),b.y-head*sin(angle-.5f));path.lineTo(b.x,b.y);path.lineTo(b.x-head*cos(angle+.5f),b.y-head*sin(angle+.5f));g.draw(path)}
                    StrokeShape.RECTANGLE->g.draw(Rectangle2D.Float(min(a.x,b.x),min(a.y,b.y),abs(a.x-b.x),abs(a.y-b.y)))
                    StrokeShape.OVAL->g.draw(Ellipse2D.Float(min(a.x,b.x),min(a.y,b.y),abs(a.x-b.x),abs(a.y-b.y)))
                }
            }
        } finally {g.dispose()}
        return image
    }
}
