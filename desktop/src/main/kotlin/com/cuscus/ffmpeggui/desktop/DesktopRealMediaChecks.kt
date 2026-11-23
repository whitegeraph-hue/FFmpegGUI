package com.cuscus.ffmpeggui.desktop

import java.io.File
import kotlinx.coroutines.runBlocking
import com.cuscus.ffmpeggui.core.editing.FollowCropTrack
import com.cuscus.ffmpeggui.core.model.Stabilization

/** Opt-in, externally supplied real footage. No downloaded fixture is bundled. */
object DesktopRealMediaChecks {
    @JvmStatic fun main(args:Array<String>)=runBlocking {
        require(args.size==3) {"Usage: model-directory video-path output-directory"}
        val model=File(args[0],"u2netp.onnx")
        val media=DesktopMedia()
        val source=media.probe(args[1])
        val folder=File(args[2]).apply {mkdirs()}
        val tracked=DesktopModelWorker().track(source.path,source.info.durationMs,model){}
        check(tracked.points.size>=5 && tracked.points.all {it.x.isFinite() && it.y.isFinite()})
        val size=FollowCropTrack.windowSize(tracked.frameWidth,tracked.frameHeight,9,16)
        val track=FollowCropTrack(tracked.points,tracked.frameWidth,tracked.frameHeight,size.first,size.second,source.path)
        val project=DesktopProject.create(source).copy(followCrop=track)
        val output=DesktopExport(media).export(project,File(folder,"person-follow.mp4"))
        check(output.info.width==size.first && output.info.height==size.second)
        check(kotlin.math.abs(output.info.durationMs-source.info.durationMs)<180)
        val combined=DesktopExport(media).export(project.copy(stabilization=Stabilization()),File(folder,"person-follow-stabilized.mp4"))
        check(combined.info.width==output.info.width && combined.info.height==output.info.height)
        for(at in listOf(0L,1000L,2000L,3000L)) {
            javax.imageio.ImageIO.write(media.frame(output.path,at),"png",File(folder,"follow-$at.png"))
            javax.imageio.ImageIO.write(media.frame(combined.path,at),"png",File(folder,"combined-$at.png"))
        }
        val saved=File(folder,"person-follow.ffgui.json");DesktopProjects.save(project,saved)
        println("PASS real-person model inference ${tracked.points.size} points, actual follow export ${output.info.width}x${output.info.height}, combined stabilization export, persistence; inspect frames for quality")
        println("Points: ${tracked.points}")
    }
}
