package com.cuscus.ffmpeggui.core.sound

import java.io.OutputStream
import kotlin.math.*
import java.util.Random

/** Original algorithmic cues. No downloaded recordings, branded assets or licensed library samples. */
enum class OriginalSound(val title:String,val family:String,val seconds:Double) {
    WHOOSH("Air whoosh","Transitions",.65), REVERSE_WHOOSH("Reverse whoosh","Transitions",.8), SWIPE("Quick swipe","Transitions",.25), RISER("Rising sweep","Transitions",1.6), DOWNER("Falling sweep","Transitions",1.3),
    IMPACT("Bass impact","Impacts",.8), THUD("Soft thud","Impacts",.25), BOOM("Low boom","Impacts",1.2), HIT("Metal hit","Impacts",.7), GLITCH("Digital glitch","Digital",.6),
    CLICK("Button click","Digital",.08), POP("Bubble pop","Digital",.16), BLEEP("Censor bleep","Digital",.65), NOTIFY("Notification","Digital",.5), ERROR("Error buzz","Digital",.4),
    SUCCESS("Success chime","Musical",.9), SPARKLE("Sparkle","Musical",1.2), BELL("Bell","Musical",1.2), TICK("Clock tick","Everyday",.12), SHUTTER("Camera shutter","Everyday",.2),
    PHONE_RING("Telephone ring","Everyday",1.8), BUSY("Telephone busy","Everyday",1.5), HEARTBEAT("Heartbeat","Everyday",1.1), RAIN("Synthetic rain","Atmosphere",3.0), WIND("Synthetic wind","Atmosphere",3.0);

    companion object {
        // Version-one sound identity. Never reorder, remove or reuse an entry here.
        private val STABLE_IDS="WHOOSH REVERSE_WHOOSH SWIPE RISER DOWNER IMPACT THUD BOOM HIT GLITCH CLICK POP BLEEP NOTIFY ERROR SUCCESS SPARKLE BELL TICK SHUTTER PHONE_RING BUSY HEARTBEAT RAIN WIND".split(' ')
    }

    /** Private immutable cache. Validate the complete deterministic bytes and atomically replace only a bad cache. */
    @Synchronized fun ensureFile(directory:java.io.File):java.io.File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create sound-cue folder" }
        val target=java.io.File(directory,name.lowercase()+".wav")
        val bytes=java.io.ByteArrayOutputStream().also {writeWav(it)}.toByteArray()
        if(target.isFile && target.length()==bytes.size.toLong() && target.readBytes().contentEquals(bytes))return target
        val temporary=java.io.File.createTempFile(".cue-", ".part", directory)
        try {
            java.io.FileOutputStream(temporary).use {it.write(bytes);it.fd.sync()}
            java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {temporary.delete()}
        return target
    }

    /** Bounded 24kHz 16-bit mono PCM WAV, deterministic and playable on both targets. */
    fun writeWav(out:OutputStream) {
        val rate=24000; val count=(seconds*rate).roundToInt(); val size=count*2
        fun le(v:Int,n:Int) {repeat(n){out.write((v ushr (8*it)) and 255)}}
        out.write("RIFF".toByteArray()); le(36+size,4);out.write("WAVEfmt ".toByteArray());le(16,4);le(1,2);le(1,2);le(rate,4);le(rate*2,4);le(2,2);le(16,2);out.write("data".toByteArray());le(size,4)
        val rng=Random(7000L+STABLE_IDS.indexOf(name).also{check(it>=0)});var smooth=0.0
        for(i in 0 until count) {
            val t=i.toDouble()/rate;val p=t/seconds;val n=rng.nextDouble()*2-1;smooth=.94*smooth+.06*n
            fun tone(hz:Double)=sin(2*PI*hz*t)
            fun decay(k:Double)=exp(-k*t)
            val v=when(this) {
                WHOOSH,SWIPE -> n*sin(PI*p).pow(2)*(.3+.7*p)
                REVERSE_WHOOSH -> n*p.pow(2)
                RISER -> .5*n*p+.4*sin(2*PI*(150*t+1800*t*t))*p
                DOWNER -> .55*n*(1-p)+.3*sin(2*PI*(1800*t-500*t*t))*(1-p)
                IMPACT,BOOM,THUD -> sin(2*PI*(if(this==BOOM)42.0 else 65.0)*t+10*(1-exp(-20*t)))*decay(if(this==THUD)20.0 else 6.0)+n*.22*decay(35.0)
                HIT -> (tone(390.0)+.5*tone(833.0)+.25*tone(1421.0))*decay(8.0)
                GLITCH -> (if((t*23).toInt()%3==0)n else sign(tone(180+((t*14).toInt()%5)*170.0))*.4)*sin(PI*p)
                CLICK,TICK,SHUTTER -> n*decay(if(this==SHUTTER)25.0 else 90.0)+.25*tone(1300.0)*decay(65.0)
                POP -> sin(2*PI*(700*t-1200*t*t))*decay(30.0)
                BLEEP -> tone(1000.0)*.6
                NOTIFY -> (if(t<.17)tone(880.0) else tone(1174.66))*decay(4.0)
                ERROR -> (tone(150.0)+.3*tone(225.0))*decay(3.0)
                SUCCESS -> (tone(if(t<.2)523.25 else if(t<.4)659.25 else 783.99)) * decay(2.0)
                SPARKLE -> (tone(1400.0)+.4*tone(2100.0)+.2*tone(2800.0))*decay(3.5)*(.7+.3*sin(25*t))
                BELL -> (tone(880.0)+.45*tone(1763.0)+.2*tone(2648.0))*decay(4.0)
                PHONE_RING -> if(t%.9<.6)(tone(440.0)+tone(480.0))*.4*(.8+.2*tone(20.0)) else 0.0
                BUSY -> if(t%.5<.25)(tone(480.0)+tone(620.0))*.35 else 0.0
                HEARTBEAT -> tone(60.0)*(exp(-((t-.15)/.045).pow(2))+ .7*exp(-((t-.35)/.045).pow(2)))
                RAIN -> n*.35+smooth*.8
                WIND -> smooth*2*(.6+.4*tone(.7))
            }
            val edge=min(1.0,min(t/.008,(seconds-t)/.015)).coerceAtLeast(0.0)
            le((v*edge*.55*32767).roundToInt().coerceIn(-32767,32767),2)
        }
    }
}
