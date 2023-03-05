import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jfugue.midi.MidiParser
import java.io.File
import javax.sound.midi.MidiSystem
import kotlin.system.exitProcess

fun main() {
    println("Hello World!")
    println()

    println("reading configuration file from: ./_in/config.json")
    val config: ArcticConfiguration
    try {
        config = Json.decodeFromString(File("./_in/config.json").readText())
    } catch (e: Exception) {
        println("invalid configuration format: ${e.message}.")
        exitProcess(1)
    }

    if (config.noteBlocks.any { blocksInTrack -> blocksInTrack.any { it.size != 3 } }) {
        println("invalid configuration format: note_blocks field's most nested array must have 3 elements(x, y, z).")
        exitProcess(1)
    }

    println()
    println("parsing midi file from: ./_in/${config.inputName}")

    val parser = MidiParser()
    val listener = ArcticMidiParserListener()
    parser.addParserListener(listener)
    parser.parse(MidiSystem.getSequence(File("./_in/${config.inputName}")))

    println("midi parsed: total ${listener.notes.size} notes with ${listener.totalTracks} tracks, will be played during ${listener.totalTicks} ticks.")

    println()
    println("convert into ./_out/${config.outputName}")

    val converter = ArcticMinecraftFunctionConverter(config)

    converter.convert(listener)

    println("all jobs completed successfully.")
}