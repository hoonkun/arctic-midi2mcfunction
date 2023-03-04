import java.io.File

private val tones = listOf(
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

class ArcticMinecraftFunctionConverter(private val config: ArcticConfiguration) {

    fun convert(listener: ArcticMidiParserListener) {
        var result = "# minecraft function created by arctic.\n#\n"

        if (config.baseBlocks != null) {
            result += config.baseBlocks
                .mapIndexed { track, baseBlock ->
                    config.noteBlocks[track]
                        .map {
                            val coordinate = "${it[0]} ${it[1] - 1} ${it[2]}"
                            val block = "$coordinate $baseBlock"

                            if (baseBlock == null) "#"
                            else "execute unless block $block if entity @e[type=marker,name=_ArcticData_,scores={__electroman_initialized=1},limit=1] run setblock $block"
                        }
                        .filter { it != "#" }
                        .joinToString("\n")
                }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            result += "\n"
        }

        val noteBlockRef = mutableMapOf<String, Int>()
        val playCommands = mutableListOf<String>()
        var queue = listener.notes.toList()
        for (tick in 0 until listener.totalTicks + 50) {
            val targets = queue.filter { it.time * 20 < tick }
            queue = queue.filter { it.time * 20 >= tick }

            val notesByTrack = targets.groupBy { it.track }

            notesByTrack.forEach { (track, notes) ->
                notes.forEachIndexed { index, note ->
                    val noteBlockToneIndex = tones.indexOfFirst { it.contains(note.tone) }
                    val noteBlockIndexOfTone = ((noteBlockToneIndex + 12 * ((note.octave + 1) % 2).coerceAtLeast(0) + 6) % 24)

                    val (x, y, z) = config.noteBlocks[track][index]
                    val coordinate = "${x}_${y}_${z}"

                    val selector = entity()
                        .select(
                            type="marker",
                            name="_ArcticData_",
                            scores=mapOf("__electroman_initialized" to 1, "__electroman_time" to tick),
                            limit=1
                        )

                    var commandChain = ""
                    if (!config.reduce || noteBlockRef[coordinate] != noteBlockIndexOfTone) {
                        commandChain += "execute if entity $selector run setblock $x $y $z note_block[note=$noteBlockIndexOfTone]\n"
                    }
                    commandChain += """
                        execute if entity $selector run setblock $x $y ${z + 1} redstone_block
                        execute if entity $selector run setblock $x $y ${z + 1} air
                    """.trimIndent()

                    playCommands.add(commandChain)

                    noteBlockRef[coordinate] = noteBlockIndexOfTone
                }
            }
        }

        result += playCommands.joinToString("\n")
        result += "\n"

        val selector = entity().select(type="marker", name="_ArcticData_", scores=mapOf("__electroman_initialized" to 1), limit=1)

        val timeAddCommands = listOf(
            "scoreboard players add $selector __electroman_time 1",
            "execute if score $selector __electroman_time matches ${listener.totalTicks + 100} run scoreboard players set $selector __electroman_time 0"
        )

        result += timeAddCommands.joinToString("\n")

        val file = File("./_out/${config.outputName}")
        file.writeText(result)
    }

    private fun entity(): Entity {
        return Entity()
    }

}

class Entity {
    private var type: String? = null
    private var name: String? = null
    private var scores: Map<String, Int>? = null
    private var limit: Int? = null

    fun select(type: String? = null, name: String? = null, scores: Map<String, Int>? = null, limit: Int? = null): Entity {
        this.type = type
        this.name = name
        this.scores = scores
        this.limit = limit
        return this
    }

    override fun toString(): String {
        var result = "@e"
        val selectors = mutableListOf<String>()
        if (type != null) selectors.add("type=$type")
        if (name != null) selectors.add("name=$name")
        if (scores != null) selectors.add("scores={${scores!!.entries.joinToString(",") { (key, value) -> "$key=$value" }}}")
        if (limit != null) selectors.add("limit=$limit")
        if (selectors.isNotEmpty()) result += "[${selectors.joinToString(",")}]"
        return result
    }
}