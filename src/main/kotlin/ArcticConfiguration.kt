import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TODO: 초기화 스크립트 포함 여부, 레드스톤 블럭 설치 위치 설정 가능하게 할 것 / 커멘드 내 스코어보드 이름 설정 가능하게 할 것
@Serializable
data class ArcticConfiguration(
    @SerialName("note_blocks")
    val noteBlocks: List<List<List<Int>>>,
    @SerialName("power_blocks")
    val powerBlocks: List<List<List<Int>>>,
    @SerialName("base_blocks")
    val baseBlocks: List<String?>? = null,
    @SerialName("reduce_commands")
    val reduce: Boolean,
    @SerialName("include_initializer")
    val includeInitializer: Boolean,
    @SerialName("input_name")
    val inputName: String,
    @SerialName("output_name")
    val outputName: String,
    @SerialName("internal_name")
    val internalName: String
)

