package friston.prts.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManagerHidden
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
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
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 640  // 40ms @ 16kHz
        private const val FRAME_BYTES = FRAME_SAMPLES * 2  // 16bit = 2 bytes/sample
        private const val BIT_RATE = 64000

        // ADTS constants for AAC-LC, 16kHz, mono
        private const val ADTS_PROFILE = 2        // AAC-LC
        private const val ADTS_FREQ_INDEX = 8      // 16kHz
        private const val ADTS_CHANNEL_CONFIG = 1  // mono
    }

    private val mAudioManager = mContext.getSystemService(AudioManager::class.java)

    private var mAudioPolicy: AudioPolicy? = null
    private var mDownlinkRecord: AudioRecord? = null
    private var mUplinkRecord: AudioRecord? = null
    private var mCodec: MediaCodec? = null
    private var mOutputStream: FileOutputStream? = null
    private var mPresentationTimeUs = 0L
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

        mUplinkRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
    }

    private fun initCodec() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)

        mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
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

    /**
     * Build 7-byte ADTS header for AAC-LC, 16kHz, Mono
     */
    private fun encodeAdtsHeader(aacFrameLength: Int): ByteArray {
        val frameLen = aacFrameLength + 7
        val header = ByteArray(7)

        header[0] = 0xFF.toByte()
        header[1] = 0xF9.toByte()  // MPEG-4, Layer 0, no CRC
        header[2] = (((ADTS_PROFILE - 1) shl 6) or (ADTS_FREQ_INDEX shl 2) or (ADTS_CHANNEL_CONFIG shr 2)).toByte()
        header[3] = (((ADTS_CHANNEL_CONFIG and 0x3) shl 6) or (frameLen shr 11)).toByte()
        header[4] = ((frameLen shr 3) and 0xFF).toByte()
        header[5] = (((frameLen and 0x7) shl 5) or 0x1F).toByte()
        header[6] = 0xFC.toByte()

        return header
    }

    private fun drainEncoder() {
        val encoder = mCodec ?: return
        val stream = mOutputStream ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val index = encoder.dequeueOutputBuffer(bufferInfo, 0)
            if (index < 0) break

            val buffer = encoder.getOutputBuffer(index)
            if (buffer != null && bufferInfo.size > 0) {
                val data = ByteArray(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.get(data)
                stream.write(encodeAdtsHeader(data.size))
                stream.write(data)
            }

            encoder.releaseOutputBuffer(index, false)
        }
    }

    private fun encodeFrame(size: Int) {
        val encoder = mCodec ?: return
        val index = encoder.dequeueInputBuffer(10_000)

        if (index >= 0) {
            val buffer = encoder.getInputBuffer(index) ?: return
            buffer.clear()
            buffer.put(mMixedBuffer.array(), 0, size)
            encoder.queueInputBuffer(index, 0, size, mPresentationTimeUs, 0)
            mPresentationTimeUs += size.toLong() / 2 * 1_000_000L / SAMPLE_RATE
        }

        drainEncoder()
    }

    suspend fun start(outputFile: File) = withContext(Dispatchers.IO) {
        initDownlink()
        initUplink()
        initCodec()

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

        mCodec?.let { it.stop(); it.release() }
        mCodec = null

        mOutputStream?.close()
        mOutputStream = null

        mAudioPolicy?.let { policy ->
            Refine.unsafeCast<AudioManagerHidden>(mAudioManager).unregisterAudioPolicy(policy)
        }
        mAudioPolicy = null
    }
}
