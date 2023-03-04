//import kotlinx.coroutines.*
import org.jfugue.midi.MidiParser
import org.jfugue.midi.MidiParserListener
import org.jfugue.theory.Note
import java.io.File
import javax.sound.midi.MidiSystem

data class MinecraftNote(
    val time: Double,
    val tone: String,
    val octave: Byte,
    val duration: Double,
    val rest: Boolean,
    val freq: Double,
    val track: Int
)

data class McFunctionConfiguration(
    val trackInstruments: List<String>,
    val trackCoordinates: List<List<List<Int>>>,
    val totalTicks: Int
)

val tones = listOf(
    listOf("C"),
    listOf("C#", "Db"),
    listOf("D"),
    listOf("D#", "Eb"),
    listOf("E"),
    listOf("F"),
    listOf("F#", "Gb"),
    listOf("G"),
    listOf("G#", "Ab"),
    listOf("A"),
    listOf("A#", "Bb"),
    listOf("B"),
)
class ParserListener: MidiParserListener() {

    private var time: Double = 0.0
    private var currentTrack: Int = 0
    private var bpm = 0

    var notes = mutableListOf<MinecraftNote>()

    override fun onTempoChanged(tempoBPM: Int) {
        super.onTempoChanged(tempoBPM)
        bpm = tempoBPM
    }

//    override fun onTimeSignatureParsed(numerator: Byte, powerOfTwo: Byte) {
//        super.onTimeSignatureParsed(numerator, powerOfTwo)
//    }

    override fun onTrackBeatTimeRequested(time: Double) {
        super.onTrackBeatTimeRequested(time)
        this.time = time * (60f / bpm * 4)
    }

    override fun onTrackChanged(track: Byte) {
        super.onTrackChanged(track)
        this.currentTrack = track.toInt()
        this.time = 0.0
    }

    override fun onNoteParsed(note: Note) {
        super.onNoteParsed(note)

        if (currentTrack == 9) return

        notes.add(MinecraftNote(time, note.toneString, note.octave, note.duration * (60f / bpm * 4), note.isRest, Note.getFrequencyForNote("${note.toneString}${note.octave}"), currentTrack))

        time += note.duration * (60f / bpm * 4)
    }

    override fun afterParsingFinished() {
        super.afterParsingFinished()

        notes = notes.filter { !it.rest }.toMutableList()
    }
}

//val job: CompletableJob = Job()
//val scope = CoroutineScope(Dispatchers.IO + job)

fun main(args: Array<String>) {
    println("Hello World!")
    println()

    println("parsing midi...")

    val parser = MidiParser()
    val listener = ParserListener()
    parser.addParserListener(listener)
//    parser.parse(MidiSystem.getSequence(File("/home/hoonkun/Downloads/Tatrisa.mid")))
    parser.parse(MidiSystem.getSequence(File("./../_midi2mcfunction_tests/electroman_adventure_octave.mid")))

    val configuration = McFunctionConfiguration(
        parseArgs(args, "ti"),
        parseArgs(args, "tc").map { track -> track.split("/").map { variant -> variant.split(".").map { coordinate -> coordinate.toInt() } } },
        listener.notes.maxOf { it.time + it.duration }.toInt() * 20
    )

    println("parse finished - total ${listener.notes.size} notes, ${configuration.totalTicks} ticks.")

    println()
    println("test playing midi, please check this is right.")

//    scope.launch {
//        playTest(listener.notes)
//        job.complete()
//    }

    println()
    println("preparing to convert mcfunction, instruments by track given by user is: \n${configuration.trackInstruments.mapIndexed { index, string -> index to string }.joinToString { "${it.first}: ${it.second}" }}")

    convert(listener.notes, configuration, parseArgs(args, "o")[0])

//    waitForComplete(job)
}

fun convert(notes: List<MinecraftNote>, config: McFunctionConfiguration, into: String) {

    val instrumentCommands = config.trackInstruments
        .mapIndexed { track, instrument ->
            config.trackCoordinates[track].joinToString("\n") {
                val coord = "${it[0]} ${it[1] - 1} ${it[2]}"
                "execute unless block $coord ${if (instrument == "_") "oak_planks" else instrument} if entity @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1},limit=1] run setblock $coord ${if (instrument == "_") "oak_planks" else instrument}"
            }
        }

    val timeAddCommands = listOf(
        "scoreboard players add @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1},limit=1] __electroman_time 1",
        "execute if score @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1},limit=1] __electroman_time matches ${config.totalTicks + 100} run scoreboard players set @e[type=marker,name=_ArcticData_,limit=1] __electroman_time 0"
    )

    val noteCommands = mutableListOf<String>()
    var queue = notes.toList()
    for (tick in 0 until config.totalTicks + 50) {
        val targets = queue.filter { it.time * 20 < tick }
        queue = queue.filter { it.time * 20 >= tick }

        val notesByTrack = targets.groupBy { it.track }

        notesByTrack.forEach { (track, notes) ->
            notes.forEachIndexed { index, note ->
                val noteBlockToneIndex = tones.indexOfFirst { it.contains(note.tone) }
                val noteBlockIndexOfTone = ((noteBlockToneIndex + 12 * (if (track == 0) note.octave - 3 else if (track == 1) note.octave - 2 else note.octave - 1).toInt().coerceAtLeast(0) + 6) % 24)

                val (x, y, z) = config.trackCoordinates[track][index]

                noteCommands.add(
                    """
                        execute if entity @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1,__electroman_time=$tick},limit=1] run setblock $x $y $z note_block[note=$noteBlockIndexOfTone]
                        execute if entity @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1,__electroman_time=$tick},limit=1] run setblock $x $y ${z + 1} redstone_block
                        execute if entity @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1,__electroman_time=$tick},limit=1] run setblock $x $y ${z + 1} air
                    """.trimIndent()
                )
            }
        }
    }

    val result = "${instrumentCommands.joinToString("\n")}\n${noteCommands.joinToString("\n")}\n${timeAddCommands.joinToString("\n")}".trimIndent()

    val file = File("./$into")
    file.writeText(result)

    print("convert to mcfunction finished")

}

fun parseArgs(args: Array<String>, flag: String): List<String> {
    val start = args.indexOfFirst { it.startsWith("--${flag}") } + 1
    val end = args.slice(start until args.size).indexOfFirst { it.startsWith("--") }
    if (flag == "tc") {
        println("${start}, ${end}, ${args.size}")
    }
    return args.slice(start until if (end < 0) args.size else start + end)
}

//fun waitForComplete(job: CompletableJob) {
//    while (!job.isCompleted)
//        Thread.sleep(1000)
//}

//suspend fun playTest(notes: MutableList<MinecraftNote>) {
//    var elapsed = 0.0
//    var queue = notes.toList()
//
//    while (queue.isNotEmpty()) {
//        val haveToPlay = queue.filter { it.time < elapsed }
//        queue = queue.filter { it.time >= elapsed }
//        haveToPlay.forEach { note ->
//            val command = listOf(
//                "ffplay",
//                "-f", "lavfi", "-i", "sine=frequency=${note.freq * 2}:duration=${note.duration}",
////                "/home/hoonkun/c5.mp3", "-af", "asetrate=44100*${note.freq / 523.25 * 2}",
//                "-autoexit", "-nodisp"
//            )
//            ProcessBuilder(command).start()
//        }
//        delay(1)
////        Thread.sleep(1)
//        elapsed += 0.001
//    }
//}