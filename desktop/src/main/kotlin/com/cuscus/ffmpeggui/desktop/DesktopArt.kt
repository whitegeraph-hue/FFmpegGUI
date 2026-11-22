package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.textart.*
import com.cuscus.ffmpeggui.core.vector.VectorTracer
import com.cuscus.ffmpeggui.core.model.OutputFormat
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Desktop font measurement adapter; all art selection, colouring and writing is original core. */
object DesktopArt {
    private fun atlas(settings:TextArtSettings):GlyphAtlas {
        val font=Font("DejaVu Sans Mono",Font.PLAIN,24)
        val probe=BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).createGraphics()
        probe.font=font
        val metrics=probe.fontMetrics
        val width=metrics.charWidth('M').coerceAtLeast(1);val height=metrics.height
        val ascent=metrics.ascent
        probe.dispose()
        val offered=(GlyphGroup.entries.flatMap { Glyphs.of(it).toList() }+Glyphs.codes(settings.alphabet.extra).toList()+Glyphs.codes(settings.phrase).toList()).distinct()
        val coverage=HashMap<Int,FloatArray>();val missing=HashSet<Int>();val wide=HashSet<Int>()
        for(code in offered) {
            if(!font.canDisplay(code)) {missing+=code;continue}
            val image=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
            val g=image.createGraphics();g.color=Color.BLACK;g.fillRect(0,0,width,height);g.color=Color.WHITE;g.font=font
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            if(g.fontMetrics.charWidth(code)!=width)wide+=code
            g.drawString(String(Character.toChars(code)),0,ascent);g.dispose()
            coverage[code]=FloatArray(24) { i ->
                val x0=i%4*width/4;val x1=(i%4+1)*width/4
                val y0=i/4*height/6;val y1=(i/4+1)*height/6
                var total=0f;var n=0
                for(y in y0 until y1)for(x in x0 until x1){total+=(image.getRGB(x,y) and 255)/255f;n++}
                if(n==0)0f else total/n
            }
        }
        return GlyphAtlas(width.toFloat()/height,4,6,coverage,missing,wide)
    }
    fun write(p:DesktopProject,png:File,target:File) {
        val image=ImageIO.read(png) ?: error("Cannot decode rendered art source.")
        val raster=Raster(image.width,image.height,image.getRGB(0,0,image.width,image.height,null,0,image.width))
        if(p.format==OutputFormat.SVG) {
            target.writeText(VectorTracer.trace(raster,p.image.trace).toSvg());return
        }
        val settings=p.image.textArt
        val art=TextArtRenderer.render(raster,settings,atlas(settings),p.format==OutputFormat.ANSI)
        val laid=TextArtLayout.compose(art,settings.layout,settings.layout.encoding==ArtEncoding.CP437 && p.format==OutputFormat.ANSI)
        target.writeBytes(if(p.format==OutputFormat.ANSI)TextArtWriter.ansiBytes(laid,settings) else TextArtWriter.plainBytes(laid,settings.layout))
    }
}
