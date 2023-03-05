import java.io.File
import kotlin.system.exitProcess

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

const val MarkerEntity = "kiwi"

class ArcticMinecraftFunctionConverter(private val config: ArcticConfiguration) {

    fun convert(listener: ArcticMidiParserListener) {
        var result = "# minecraft function created by apteryx.\n"

        val entity = Entity.select(type="marker", name=MarkerEntity)
        val initialized = entity.copy(scores= mapOf("__${config.internalName}_i" to 1), limit= 1)

        if (config.includeInitializer) {
            result += """
                execute unless entity $entity run summon marker 0 0 0 {CustomName: "{\"text\": \"$MarkerEntity\"}"}
                execute unless entity $initialized run scoreboard objectives add __${config.internalName}_t dummy
                execute unless entity $initialized run scoreboard players set @e[type=marker,name=$MarkerEntity] __${config.internalName}_t 0
                execute unless entity $initialized run scoreboard objectives add __${config.internalName}_i dummy
                execute unless entity $initialized run scoreboard players set @e[type=marker,name=$MarkerEntity] __${config.internalName}_i 1
            """.trimIndent()
            result += "\n"
        }

        if (config.baseBlocks != null) {
            result += config.baseBlocks
                .mapIndexed { track, baseBlock ->
                    config.noteBlocks[track]
                        .map {
                            val coordinate = "${it[0]} ${it[1] - 1} ${it[2]}"
                            val block = "$coordinate $baseBlock"

                            if (baseBlock == null) "#"
                            else "execute unless block $block if entity @e[type=marker,name=$MarkerEntity,scores={__${config.internalName}_i=1},limit=1] run setblock $block"
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

            val notesByTrack = targets.sortedBy { it.tone }.groupBy { it.track }

            notesByTrack.forEach { (track, notes) ->
                notes.forEachIndexed { index, note ->
                    val noteBlockToneIndex = tones.indexOfFirst { it.contains(note.tone) }
                    val noteBlockIndexOfTone = ((noteBlockToneIndex + 12 * ((note.octave + 1) % 2).coerceAtLeast(0) + 6) % 24)

                    try {
                        val (x, y, z) = config.noteBlocks[track][index]
                        val (x1, y1, z1) = config.powerBlocks[track][index]
                        val noteCoordinate = "$x $y $z"
                        val powerCoordinate = "$x1 $y1 $z1"
                        val coordinate = "${x}_${y}_${z}"

                        val selector = Entity
                            .select(
                                type = "marker",
                                name = MarkerEntity,
                                scores = mapOf("__${config.internalName}_i" to 1, "__${config.internalName}_t" to tick),
                                limit = 1
                            )

                        var commandChain = ""
                        if (!config.reduce || noteBlockRef[coordinate] != noteBlockIndexOfTone) {
                            commandChain += "execute if entity $selector run setblock $noteCoordinate note_block[note=$noteBlockIndexOfTone]\n"
                        }
                        commandChain += "execute if entity $selector run setblock $powerCoordinate redstone_block"

                        playCommands.add(commandChain)

                        noteBlockRef[coordinate] = noteBlockIndexOfTone
                    } catch (e: IndexOutOfBoundsException) {
                        println("error in converting phase: track[$track] must have more than $index note blocks, but ${config.noteBlocks[track].size} was given.")
                        exitProcess(1)
                    }
                }
            }
        }

        result += playCommands.joinToString("\n")
        result += "\n"

        result += config.powerBlocks.flatten().joinToString("\n") {
            "execute if block ${it[0]} ${it[1]} ${it[2]} redstone_block if entity $initialized run setblock ${it[0]} ${it[1]} ${it[2]} air"
        }
        result += "\n"

        val timeAddCommands = listOf(
            "scoreboard players add $initialized __${config.internalName}_t 1",
            "execute if score $initialized __${config.internalName}_t matches ${listener.totalTicks + config.paddingEnd} run scoreboard players set $initialized __${config.internalName}_t 0"
        )

        result += timeAddCommands.joinToString("\n")

        val file = File("./_out/${config.outputName}")
        file.writeText(result)
    }

}

data class Entity constructor(
    private var type: String? = null,
    private var name: String? = null,
    private var scores: Map<String, Int>? = null,
    private var limit: Int? = null
) {

    companion object {

        fun select(
            type: String? = null,
            name: String? = null,
            scores: Map<String, Int>? = null,
            limit: Int? = null
        ): Entity {
            return Entity(type, name, scores, limit)
        }

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