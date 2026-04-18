package friston.prts.recorder

import android.media.AudioRecordingConfiguration
import android.media.AudioRecordingConfigurationHidden
import dev.rikka.tools.refine.Refine
import friston.prts.util.Logger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RecordingPathUtil {
    private const val TAG = "RecordingPathUtil"
    private const val OUTPUT_DIR = "/data/local/tmp/Friston-3/"
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmm")

    private val FILE_PREFIX = mapOf(
        RecordingType.VOIP to "voip",
        RecordingType.CALL to "call",
        RecordingType.MIC to "mic",
    )

    fun generatePath(type: RecordingType, label: String): File {
        ensureOutputDir()

        val prefix = FILE_PREFIX.getValue(type)
        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
        val fileName = "${prefix}-${label}-${timestamp}.aac"

        return File(OUTPUT_DIR, fileName)
    }

    fun getPackageNameFromConfig(config: AudioRecordingConfiguration): String? {
        return try {
            val hidden = Refine.unsafeCast<AudioRecordingConfigurationHidden>(config)
            hidden.clientPackageName
        } catch (err: Exception) {
            Logger.w(TAG, "Failed to get package name from config", err)
            null
        }
    }

    fun ensureOutputDir(): File {
        val dir = File(OUTPUT_DIR)

        if (!dir.exists()) {
            dir.mkdirs()
            Logger.i(TAG, "Created output directory: $OUTPUT_DIR")
        }

        return dir
    }
}