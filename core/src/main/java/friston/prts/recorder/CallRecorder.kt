package friston.prts.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import friston.prts.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@SuppressLint("MissingPermission")
class CallRecorder {

    companion object {
        private const val TAG = "CallRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_COUNT = 1
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 640
        private const val FRAME_BYTES = FRAME_SAMPLES * 2
        private const val BIT_RATE = 64000
    }

    private var mAudioRecord: AudioRecord? = null
    private var mEncoder: AacAdtsEncoder? = null
    private var mOutputStream: FileOutputStream? = null

    private fun initAudioRecord() {
        var bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        bufferSize = maxOf(bufferSize, FRAME_BYTES * 4)

        mAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
    }

    suspend fun start(outputFile: File) = withContext(Dispatchers.IO) {
        initAudioRecord()

        val encoder = AacAdtsEncoder(SAMPLE_RATE, CHANNEL_COUNT, BIT_RATE)
        mEncoder = encoder
        encoder.start()

        mOutputStream = FileOutputStream(outputFile)
        RecordingPathUtil.setFilePermissions(outputFile)

        val record = mAudioRecord ?: error("AudioRecord not initialized")
        val stream = mOutputStream ?: error("Output stream not initialized")

        record.startRecording()
        Logger.d(TAG, "Recording started")

        val buffer = ByteArray(FRAME_BYTES)

        try {
            while (coroutineContext.isActive) {
                val read = record.read(buffer, 0, FRAME_BYTES)

                if (read > 0) {
                    encoder.encodePcm(buffer, read, stream)
                } else if (read < 0) {
                    Logger.e(TAG, "VOICE_CALL read error: $read")
                }
            }
        } finally {
            release()
        }
    }

    @Synchronized
    fun release() {
        mAudioRecord?.let { it.stop(); it.release() }
        mAudioRecord = null

        mEncoder?.release(mOutputStream)
        mEncoder = null

        mOutputStream?.close()
        mOutputStream = null
    }
}
