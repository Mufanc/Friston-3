package friston.prts.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManagerHidden
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiopolicy.AudioMix
import android.media.audiopolicy.AudioMixingRule
import android.media.audiopolicy.AudioPolicy
import dev.rikka.tools.refine.Refine
import friston.prts.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@SuppressLint("MissingPermission")
class VoipRecorder(private val mContext: Context) {

    companion object {
        private const val TAG = "VoipRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_COUNT = 1
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 640  // 40ms @ 16kHz
        private const val FRAME_BYTES = FRAME_SAMPLES * 2  // 16bit = 2 bytes/sample
        private const val BIT_RATE = 64000
    }

    private val mAudioManager = mContext.getSystemService(AudioManager::class.java)

    private var mAudioPolicy: AudioPolicy? = null
    private var mDownlinkRecord: AudioRecord? = null
    private var mUplinkRecord: AudioRecord? = null
    private var mEncoder: AacAdtsEncoder? = null
    private var mOutputStream: FileOutputStream? = null
    private val mMixedBuffer = ByteBuffer.allocate(FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN)

    private fun initDownlink() {
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        val rule = AudioMixingRule.Builder()
            .addRule(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build(),
                AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE
            )
            .build()

        val mix = AudioMix.Builder(rule)
            .setFormat(format)
            .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK_RENDER)
            .build()

        val policy = AudioPolicy.Builder(mContext)
            .addMix(mix)
            .build()

        mAudioPolicy = policy
        Refine.unsafeCast<AudioManagerHidden>(mAudioManager).registerAudioPolicy(policy)
        mDownlinkRecord = policy.createAudioRecordSink(mix)
    }

    private fun initUplink() {
        var bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        bufferSize = maxOf(bufferSize, FRAME_BYTES * 4)

        mUplinkRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setPrivacySensitive(false)
            .build()
    }

    private fun mixFrames(downBuf: ByteArray, downLen: Int, upBuf: ByteArray, upLen: Int): Int {
        val samples = minOf(downLen, upLen) / 2
        val downShorts = ByteBuffer.wrap(downBuf, 0, downLen).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val upShorts = ByteBuffer.wrap(upBuf, 0, upLen).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val mixedShorts = mMixedBuffer.asShortBuffer()

        for (i in 0 until samples) {
            mixedShorts.put(i, ((downShorts[i] + upShorts[i]) / 2).toShort())
        }

        return samples * 2
    }

    private fun initEncoder() {
        val encoder = AacAdtsEncoder(SAMPLE_RATE, CHANNEL_COUNT, BIT_RATE)
        mEncoder = encoder
        encoder.start()
    }

    private fun encodeFrame(size: Int) {
        val encoder = mEncoder ?: return
        val stream = mOutputStream ?: return
        encoder.encodePcm(mMixedBuffer.array(), size, stream)
    }

    suspend fun start(outputFile: File) = withContext(Dispatchers.IO) {
        initDownlink()
        initUplink()
        initEncoder()

        mOutputStream = FileOutputStream(outputFile)
        RecordingPathUtil.setFilePermissions(outputFile)

        val downRecord = mDownlinkRecord ?: error("Downlink AudioRecord not initialized")
        val upRecord = mUplinkRecord ?: error("Uplink AudioRecord not initialized")

        downRecord.startRecording()
        upRecord.startRecording()
        Logger.d(TAG, "Recording started")

        val downBuffer = ByteArray(FRAME_BYTES)
        val upBuffer = ByteArray(FRAME_BYTES)

        try {
            while (coroutineContext.isActive) {
                val downRead = downRecord.read(downBuffer, 0, FRAME_BYTES)
                val upRead = upRecord.read(upBuffer, 0, FRAME_BYTES)

                if (downRead > 0 && upRead > 0) {
                    val size = mixFrames(downBuffer, downRead, upBuffer, upRead)
                    encodeFrame(size)
                } else {
                    if (downRead < 0) Logger.e(TAG, "Downlink read error: $downRead")
                    if (upRead < 0) Logger.e(TAG, "Uplink read error: $upRead")
                }
            }
        } finally {
            release()
        }
    }

    @Synchronized
    fun release() {
        mDownlinkRecord?.let { it.stop(); it.release() }
        mDownlinkRecord = null

        mUplinkRecord?.let { it.stop(); it.release() }
        mUplinkRecord = null

        mEncoder?.release(mOutputStream)
        mEncoder = null

        mOutputStream?.close()
        mOutputStream = null

        mAudioPolicy?.let { policy ->
            Refine.unsafeCast<AudioManagerHidden>(mAudioManager).unregisterAudioPolicy(policy)
        }
        mAudioPolicy = null
    }
}
