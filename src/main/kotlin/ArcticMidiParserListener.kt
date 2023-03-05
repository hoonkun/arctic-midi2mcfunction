import org.jfugue.midi.MidiParserListener
import org.jfugue.theory.Note


data class ArcticNote(
    val time: Double,
    val tone: String,
    val octave: Byte,
    val track: Int
)

class ArcticMidiParserListener : MidiParserListener() {

    private var currentTime: Double = 0.0
    private var currentTrack: Int = -1
    private var currentTimeMultiplier: Float = 1f

    val notes = mutableListOf<ArcticNote>()
    var totalTicks = 0
    var totalTracks = 0

    override fun onTempoChanged(tempoBPM: Int) {
        super.onTempoChanged(tempoBPM)
        currentTimeMultiplier = 60f / tempoBPM * 4
    }

//    override fun onTimeSignatureParsed(numerator: Byte, powerOfTwo: Byte) {
//        super.onTimeSignatureParsed(numerator, powerOfTwo)
//    }

    override fun onTrackBeatTimeRequested(time: Double) {
        super.onTrackBeatTimeRequested(time)
        currentTime = time * currentTimeMultiplier
    }

    override fun onTrackChanged(track: Byte) {
        super.onTrackChanged(track)
        currentTrack += 1
        currentTime = 0.0
        totalTracks = currentTrack
    }

    override fun onNoteParsed(note: Note) {
        super.onNoteParsed(note)

        if (!note.isRest)
            notes.add(ArcticNote(currentTime, note.toneString, note.octave, currentTrack))

        currentTime += note.duration * currentTimeMultiplier
    }

    override fun afterParsingFinished() {
        super.afterParsingFinished()
        totalTicks = (notes.maxOf { it.time } * 20).toInt()
    }

}
