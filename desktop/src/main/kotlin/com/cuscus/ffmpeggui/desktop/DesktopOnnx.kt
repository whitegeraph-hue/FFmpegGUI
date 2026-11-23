package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.io.RandomAccessFile

/** Narrow single-file ONNX preflight. Never follows any model-supplied path.
 * Schema: https://github.com/onnx/onnx/blob/main/onnx/onnx.proto
 * Training graphs, local functions and sparse tensors are intentionally refused.
 */
object DesktopOnnx {
    fun singleFile(file:File) {
        require(file.length() in 1..300_000_000) {"Invalid ONNX file size"}
        RandomAccessFile(file,"r").use {input->
            var fields=0
            fun varint(end:Long):Long {
                var result=0L
                for(shift in 0..63 step 7) {
                    require(input.filePointer<end) {"Truncated ONNX field"}
                    val b=input.readUnsignedByte()
                    require(shift!=63 || b<=1) {"Invalid ONNX varint"}
                    result=result or ((b and 127).toLong() shl shift)
                    if(b and 128==0)return result
                }
                error("Invalid ONNX varint")
            }
            fun message(kind:String,end:Long,depth:Int) {
                require(depth<=32) {"ONNX graph nesting is too deep"}
                while(input.filePointer<end) {
                    require(++fields<=2_000_000) {"ONNX has too many fields"}
                    val tag=varint(end);val number=(tag ushr 3).toInt();val wire=(tag and 7).toInt()
                    require(number>0) {"Invalid ONNX field"}
                    if(kind=="tensor")require(number!=13 && number!=14) {"External-data ONNX tensors are not allowed. Import self-contained model files only."}
                    if(kind=="model")require(number!=20 && number!=25) {"Training graphs and local ONNX functions are not supported"}
                    if(kind=="graph")require(number!=15) {"Sparse ONNX tensors are not supported"}
                    if(kind=="attribute")require(number!=22 && number!=23) {"Sparse ONNX tensor attributes are not supported"}
                    when(wire) {
                        0->varint(end)
                        1->{require(end-input.filePointer>=8);input.seek(input.filePointer+8)}
                        5->{require(end-input.filePointer>=4);input.seek(input.filePointer+4)}
                        2->{
                            val size=varint(end);require(size>=0 && size<=end-input.filePointer) {"Invalid ONNX field size"}
                            val limit=input.filePointer+size
                            val child=when(kind) {
                                "model"->if(number==7)"graph"else null
                                "graph"->when(number){1->"node";5->"tensor";else->null}
                                "node"->if(number==5)"attribute"else null
                                "attribute"->when(number){5,10->"tensor";6,11->"graph";else->null}
                                else->null
                            }
                            if(child!=null)message(child,limit,depth+1)else input.seek(limit)
                            require(input.filePointer==limit) {"Malformed ONNX nested field"}
                        }
                        else->error("Unsupported ONNX wire format")
                    }
                }
                require(input.filePointer==end)
            }
            message("model",input.length(),0)
        }
    }
}
