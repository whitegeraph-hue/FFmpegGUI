package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.awt.Font

/** Validate a user-selected single font before handing it to the renderer. */
object DesktopFonts {
    fun validate(file:File) {
        require(file.isFile && file.canRead() && file.length() in 1..(16L*1024*1024)) {"Choose a readable TTF or OTF font, at most 16 MB"}
        require(file.extension.lowercase() in listOf("ttf","otf")) {"Choose a TTF or OTF font"}
        file.inputStream().use {Font.createFont(Font.TRUETYPE_FONT,it)}
    }
}
