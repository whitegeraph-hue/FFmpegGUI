package com.cuscus.ffmpeggui.desktop

import ai.onnxruntime.*
import com.cuscus.ffmpeggui.core.translate.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Original Marian tokenizer/greedy decoding in a disposable worker, not an OS security sandbox. */
object DesktopTranslation {
    val files=listOf("config.json","vocab.json","source.spm","encoder_model_quantized.onnx","decoder_model_quantized.onnx")
    val root:File get()=File(DesktopModels.root,"translation")
    data class Model(val repo:String,val directory:File) {
        val pair:Pair<String,String> get()=Languages.pairOf(repo)!!
    }
    fun installed():List<Model> = root.listFiles()?.filter {it.isDirectory && !it.name.startsWith(".")}?.mapNotNull {directory->
        runCatching {
            val repo=Json.parseToJsonElement(File(directory,"manifest.json").readText()).jsonObject["repo"]!!.jsonPrimitive.content
            require(validRepo(repo));Model(repo,directory)
        }.getOrNull()
    } ?: emptyList()
    fun validRepo(repo:String)=Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo) && Languages.pairOf(repo)?.let { !Languages.isGroup(it.first) && !Languages.isGroup(it.second) }==true
    private fun hash(file:File):String = file.inputStream().use {input->val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)};digest.digest().joinToString(""){"%02x".format(it)}}
    fun verify(directory:File) {
        val meta=File(directory,"manifest.json");require(meta.length() in 1..10000) {"Missing translation manifest"}
        val data=Json.parseToJsonElement(meta.readText()).jsonObject
        require(validRepo(data["repo"]!!.jsonPrimitive.content)) {"Invalid translation model identity"}
        val hashes=data["hashes"]!!.jsonObject
        files.forEach {name->
            val f=File(directory,name)
            val limit=when(name){"config.json"->100_000L;"vocab.json"->8_000_000L;"source.spm"->8_000_000L;else->300_000_000L}
            require(f.isFile && !Files.isSymbolicLink(f.toPath()) && f.length() in 1..limit && hash(f)==hashes[name]!!.jsonPrimitive.content) {"Translation model changed or is invalid: $name. Import it again."}
        }
    }
    suspend fun import(repo:String,picked:List<File>,worker:DesktopModelWorker)=withContext(Dispatchers.IO) {
        require(validRepo(repo)) {"Use a direct pair such as Xenova/opus-mt-en-fr. Multilingual and pivot routes are still being ported."}
        require(picked.size==files.size && picked.map {it.name}.toSet()==files.toSet()) {"Select exactly config.json, vocab.json, source.spm and the two quantized encoder/decoder files"}
        root.mkdirs()
        val stage=Files.createTempDirectory(root.toPath(),".import-").toFile()
        val target=File(root,repo.replace('/','_'))
        require(!target.exists()) {"This model is already installed. Remove it before replacing it."}
        try {
            require(stage.usableSpace>picked.sumOf {it.length()}+100_000_000) {"Not enough free disk space to import this translation model"}
            val hashes=buildJsonObject {picked.forEach {source->
                val limit=if(source.name.endsWith(".onnx"))300_000_000L else if(source.name=="config.json")100_000L else 8_000_000L
                require(source.length() in 1..limit) {"Translation file is too large: ${source.name}"}
                val f=File(stage,source.name)
                source.inputStream().use {input->f.outputStream().use {output->val buffer=ByteArray(65536);var total=0L;while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=limit){"Translation file grew beyond its limit"};output.write(buffer,0,n)}}}
                put(source.name,hash(f))
            }}
            File(stage,"manifest.json").writeText(buildJsonObject {put("repo",repo);put("hashes",hashes)}.toString())
            worker.checkTranslation(stage)
            Files.move(stage.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
        } finally {stage.deleteRecursively()}
    }
    suspend fun execute(directory:File,texts:List<String>,checkOnly:Boolean,progress:(String)->Unit):List<String> = withContext(Dispatchers.IO) {
        verify(directory)
        DesktopSmartModels.checkAvailableMemory(700L*1024*1024)
        require(texts.size<=500 && texts.sumOf {it.length}<=100_000 && texts.all {it.length<=1000}) {"Translate up to 500 short cues at once (1000 characters each, 100000 total)"}
        files.filter {it.endsWith(".onnx")}.forEach {DesktopOnnx.singleFile(File(directory,it))}
        val vocab=MarianTokenizer.vocab(File(directory,"vocab.json").readText())
        require(vocab.size in 100..100_000 && vocab.values.all {it in 0..100_000}) {"Invalid translation vocabulary"}
        val config=MarianConfig.parse(File(directory,"config.json").readText())
        val claimedRepo=Json.parseToJsonElement(File(directory,"manifest.json").readText()).jsonObject["repo"]!!.jsonPrimitive.content
        require(config.name?.let {Languages.pairOf(it)}==Languages.pairOf(claimedRepo)) {"Model repository language pair does not match config.json"}
        val tokenizer=MarianTokenizer(SentencePieceModel.parse(File(directory,"source.spm").readBytes()),vocab,config)
        require(config.vocabSize==0 || config.vocabSize in 100..100_001) {"Invalid translation vocabulary size"}
        require(tokenizer.targetTokens.isEmpty()) {"Multilingual target-token models are not supported yet"}
        val env=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use {options->
            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);options.setCPUArenaAllocator(false);options.setMemoryPatternOptimization(false)
            env.createSession(File(directory,"encoder_model_quantized.onnx").path,options).use {encoder->
                env.createSession(File(directory,"decoder_model_quantized.onnx").path,options).use {decoder->
                    require(encoder.inputNames==setOf("input_ids","attention_mask")) {"Unsupported Marian encoder inputs"}
                    require(decoder.inputNames==setOf("input_ids","encoder_hidden_states","encoder_attention_mask")) {"Unsupported Marian decoder inputs"}
                    if(checkOnly)return@withContext emptyList()
                    texts.mapIndexed {index,text->
                        currentCoroutineContext().ensureActive();progress("Translating cue ${index+1} of ${texts.size}")
                        if(text.isBlank())text else {
                            try {
                            val ids=tokenizer.encode(text,maxTokens=257)
                            require(ids.size<=256) {"A cue is too long for translation. Split it into shorter cues first."}
                            val mask=LongArray(ids.size){1L};val shape=longArrayOf(1,ids.size.toLong())
                            val (hidden,hiddenShape)=OnnxTensor.createTensor(env,LongBuffer.wrap(ids),shape).use {input->
                                OnnxTensor.createTensor(env,LongBuffer.wrap(mask),shape).use {attention->
                                    encoder.run(mapOf("input_ids" to input,"attention_mask" to attention)).use {result->
                                        val tensor=result[0] as OnnxTensor;val buffer=tensor.floatBuffer
                                        FloatArray(buffer.remaining()).also {buffer.get(it)} to tensor.info.shape
                                    }
                                }
                            }
                            val context=currentCoroutineContext()
                            val maxNew=minOf(config.maxLength,ids.size*3+16)
                            val written=OnnxTensor.createTensor(env,FloatBuffer.wrap(hidden),hiddenShape).use {states->
                                OnnxTensor.createTensor(env,LongBuffer.wrap(mask),shape).use {attention->
                                    GreedyDecoder.decode(config,maxNew) {sofar->
                                        context.ensureActive()
                                        OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(sofar.size){sofar[it].toLong()}),longArrayOf(1,sofar.size.toLong())).use {input->
                                            decoder.run(mapOf("input_ids" to input,"encoder_hidden_states" to states,"encoder_attention_mask" to attention)).use {result->
                                                val tensor=result[0] as OnnxTensor;val size=tensor.info.shape.last().toInt();require(size in 100..100_001)
                                                val buffer=tensor.floatBuffer;buffer.position(buffer.limit()-size);FloatArray(size).also {buffer.get(it)}
                                            }
                                        }
                                    }
                                }
                            }
                            require(written.size<maxNew && !(written.size>=3 && written.takeLast(3).distinct().size==1)) {"Translation stopped at its length/repetition limit. Split this cue and retry; no subtitles were replaced."}
                            tokenizer.decode(written).also {require(it.isNotBlank()){ "The model returned an empty translation. No subtitles were replaced." }}
                            } catch(e:CancellationException){throw e} catch(e:Exception){throw IllegalArgumentException("Cue ${index+1}: ${e.message}",e)}
                        }
                    }
                }
            }
        }
    }
}
