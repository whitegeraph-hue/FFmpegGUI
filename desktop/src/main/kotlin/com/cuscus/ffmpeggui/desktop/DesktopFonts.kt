package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.awt.Font

/** Validate a user-selected single font before handing it to the renderer. */
object DesktopFonts {
    /** ASS styles are comma-separated records, never accept embedded record delimiters. */
    fun assFamily(name:String):String = name.map {if(it==',' || it.isISOControl()) ' ' else it}.joinToString("").trim().ifEmpty {"DejaVu Sans"}
    fun validate(file:File) {
        require(file.isFile && file.canRead() && file.length() in 1..(16L*1024*1024)) {"Choose a readable TTF or OTF font, at most 16 MB"}
        require(file.extension.lowercase() in listOf("ttf","otf")) {"Choose a TTF or OTF font"}
        file.inputStream().use {Font.createFont(Font.TRUETYPE_FONT,it)}
    }
}
