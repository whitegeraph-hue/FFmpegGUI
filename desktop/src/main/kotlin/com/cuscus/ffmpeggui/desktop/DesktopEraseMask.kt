package com.cuscus.ffmpeggui.desktop

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

/** Scoped source-pixel alpha mask editor. Never changes the source or project drawing. */
object DesktopEraseMask {
    fun paint(source:File,owner:java.awt.Frame):File? {
        // Source decode is bounded before allocation; EXIF normalization happens in the eraser.
        require(DesktopStillInputs.orientation(source)==1) {"For rotated JPEGs use an upright PNG copy or an external matching upright mask"}
        val picture=DesktopErase.decode(source.path)
        val mask=BufferedImage(picture.width,picture.height,BufferedImage.TYPE_INT_ARGB)
        val dialog=JDialog(owner,"Paint magic erase mask",true)
        val maxW=900;val maxH=600;val scale=minOf(1.0,maxW.toDouble()/picture.width,maxH.toDouble()/picture.height)
        val w=(picture.width*scale).toInt().coerceAtLeast(1);val h=(picture.height*scale).toInt().coerceAtLeast(1)
        val history=ArrayDeque<IntArray>();var accepted=false;var erase=false
        val size=JSlider(2,120,24)
        val canvas=object:JPanel() {
            init {preferredSize=Dimension(w,h)}
            override fun paintComponent(g:Graphics) {
                super.paintComponent(g);g.drawImage(picture,0,0,width,height,null)
                val overlay=BufferedImage(mask.width,mask.height,BufferedImage.TYPE_INT_ARGB)
                val row=IntArray(mask.width)
                for(y in 0 until mask.height){mask.getRGB(0,y,mask.width,1,row,0,mask.width);for(x in row.indices)row[x]=if(row[x] ushr 24>127)0x88ff4040.toInt() else 0;overlay.setRGB(0,y,mask.width,1,row,0,mask.width)}
                try {g.drawImage(overlay,0,0,width,height,null)}finally {overlay.flush()}
            }
        }
        var last:Point?=null
        fun stroke(a:Point,b:Point) {
            val g=mask.createGraphics()
            try {
                g.composite=if(erase)AlphaComposite.Clear else AlphaComposite.Src
                g.color=Color.WHITE
                g.stroke=BasicStroke(size.value.toFloat()/canvas.width*picture.width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
                val x=(a.x.toDouble()/canvas.width*picture.width).toInt();val y=(a.y.toDouble()/canvas.height*picture.height).toInt()
                val xx=(b.x.toDouble()/canvas.width*picture.width).toInt();val yy=(b.y.toDouble()/canvas.height*picture.height).toInt()
                g.drawLine(x,y,xx,yy)
            }finally {g.dispose()};canvas.repaint()
        }
        val mouse=object:MouseAdapter() {
            override fun mousePressed(e:MouseEvent) {if(history.size>=5)history.removeFirst();history.addLast(mask.getRGB(0,0,mask.width,mask.height,null,0,mask.width));last=e.point;stroke(e.point,e.point)}
            override fun mouseDragged(e:MouseEvent) {last?.let {stroke(it,e.point)};last=e.point}
            override fun mouseReleased(e:MouseEvent) {last=null}
        }
        canvas.addMouseListener(mouse);canvas.addMouseMotionListener(mouse)
        val controls=JPanel();controls.add(JLabel("Brush"));controls.add(size)
        controls.add(JCheckBox("Unselect").apply {addActionListener {erase=isSelected}})
        controls.add(JButton("Undo").apply {addActionListener {if(history.isNotEmpty()){mask.setRGB(0,0,mask.width,mask.height,history.removeLast(),0,mask.width);canvas.repaint()}}})
        controls.add(JButton("Clear").apply {addActionListener {val g=mask.createGraphics();try {g.composite=AlphaComposite.Clear;g.fillRect(0,0,mask.width,mask.height)}finally {g.dispose()};history.clear();canvas.repaint()}})
        controls.add(JButton("Use mask").apply {addActionListener {accepted=true;dialog.dispose()}})
        controls.add(JButton("Cancel").apply {addActionListener {dialog.dispose()}})
        dialog.layout=BorderLayout();dialog.add(JLabel("Paint red over what to remove. The source remains unchanged."),BorderLayout.NORTH);dialog.add(canvas,BorderLayout.CENTER);dialog.add(controls,BorderLayout.SOUTH)
        dialog.pack();dialog.setLocationRelativeTo(owner)
        return try {
            dialog.isVisible=true
            if(accepted)File.createTempFile("ffmpeggui-erase-mask-",".png").also {check(ImageIO.write(mask,"png",it));it.deleteOnExit()} else null
        }finally {picture.flush();mask.flush();dialog.dispose()}
    }
}
