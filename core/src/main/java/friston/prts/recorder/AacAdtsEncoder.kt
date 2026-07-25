package friston.prts.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.FileOutputStream

class AacAdtsEncoder(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bitRate: Int,
) {

    companion object {
        private const val ADTS_PROFILE = 2 // AAC-LC

        private val ADTS_FREQ_INDEX = mapOf(
            96000 to 0,
            88200 to 1,
            64000 to 2,
            48000 to 3,
            44100 to 4,
            32000 to 5,
            24000 to 6,
            22050 to 7,
            16000 to 8,
            12000 to 9,
            11025 to 10,
            8000 to 11,
            7350 to 12,
        )
    }

    private val mFreqIndex = ADTS_FREQ_INDEX[sampleRate] ?: error("Unsupported AAC sample rate: $sampleRate")
    private var mCodec: MediaCodec? = null
    private var mPresentationTimeUs = 0L

    fun start() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)

        mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    fun encodePcm(buffer: ByteArray, size: Int, output: FileOutputStream) {
        val encoder = mCodec ?: return
        val index = encoder.dequeueInputBuffer(10_000)

        if (index >= 0) {
            val input = encoder.getInputBuffer(index) ?: return
            input.clear()
            input.put(buffer, 0, size)
            encoder.queueInputBuffer(index, 0, size, mPresentationTimeUs, 0)
            mPresentationTimeUs += size.toLong() / 2 / channelCount * 1_000_000L / sampleRate
        }

        drain(output)
    }

    fun release(output: FileOutputStream?) {
        val encoder = mCodec

        if (encoder != null && output != null) {
            val index = encoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                encoder.queueInputBuffer(index, 0, 0, mPresentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(output)
        }

        mCodec?.let { it.stop(); it.release() }
        mCodec = null
    }

    private fun drain(output: FileOutputStream) {
        val encoder = mCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val index = encoder.dequeueOutputBuffer(bufferInfo, 0)
            if (index < 0) break

            val buffer = encoder.getOutputBuffer(index)
            if (buffer != null && bufferInfo.size > 0) {
                val data = ByteArray(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.get(data)
                output.write(encodeAdtsHeader(data.size))
                output.write(data)
            }

            val flags = bufferInfo.flags
            encoder.releaseOutputBuffer(index, false)
            if (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }

    private fun encodeAdtsHeader(aacFrameLength: Int): ByteArray {
        val frameLen = aacFrameLength + 7
        val header = ByteArray(7)

        header[0] = 0xFF.toByte()
        header[1] = 0xF9.toByte()
        header[2] = (((ADTS_PROFILE - 1) shl 6) or (mFreqIndex shl 2) or (channelCount shr 2)).toByte()
        header[3] = (((channelCount and 0x3) shl 6) or (frameLen shr 11)).toByte()
        header[4] = ((frameLen shr 3) and 0xFF).toByte()
        header[5] = (((frameLen and 0x7) shl 5) or 0x1F).toByte()
        header[6] = 0xFC.toByte()

        return header
    }
}
