package friston.prts.recorder

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.AudioRecordingConfigurationHidden
import android.media.MediaRecorder
import dev.rikka.tools.refine.Refine
import friston.prts.monitor.MonitorEvent
import friston.prts.util.EventReceiver
import friston.prts.util.Logger
import friston.prts.util.Ref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.ThreadMode
import java.io.File

class RecordingController(private val mContext: Context) : EventReceiver<MonitorEvent>(
    MonitorEvent::class.java,
    ThreadMode.MAIN
) {

    companion object {
        private const val TAG = "RecordingController"
        private val OUTPUT_FILE = File("/data/misc/perfetto-traces/output.aac")
    }

    private val mScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mAudioMode = Ref(AudioManager.MODE_NORMAL)
    private val mRecordingConfigurations = Ref<List<AudioRecordingConfiguration>?>(null)

    private val mRecording3rdPartyApps = Ref.compute(null, mRecordingConfigurations) {
        mRecordingConfigurations.value?.filter {
            val hidden: AudioRecordingConfigurationHidden = Refine.unsafeCast(it)

            hidden.clientUid >= android.os.Process.FIRST_APPLICATION_UID
                    && hidden.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }

    private var mIsRecording = false
    private var mRecorder: VoipRecorder? = null
    private var mRecordingJob: Job? = null

    fun init() {
        Ref.subscribe(mAudioMode, mRecording3rdPartyApps) {
            val mode = mAudioMode.value
            val configs = mRecording3rdPartyApps.value

            Logger.v(TAG, "mode = $mode, configs.size = ${configs?.size}")

            val recording = mode == AudioManager.MODE_IN_COMMUNICATION && !configs.isNullOrEmpty()

            if (mIsRecording != recording) {
                mIsRecording = recording

                if (recording) {
                    start()
                } else {
                    stop()
                }
            }
        }

        register()
    }

    override fun onEvent(event: MonitorEvent) {
        Logger.v(TAG, "Handle event: $event")

        when (event) {
            is MonitorEvent.AudioModeChange -> mAudioMode.value = event.mode
            is MonitorEvent.AudioRecordingStatusChange -> mRecordingConfigurations.value = event.configs
        }
    }

    private fun start() {
        Logger.i(TAG, "VoIP call detected, starting recording")

        val recorder = VoipRecorder(mContext)
        mRecorder = recorder

        mRecordingJob = mScope.launch {
            try {
                recorder.start(OUTPUT_FILE)
            } catch (e: Exception) {
                Logger.e(TAG, "Recorder error", e)
            }
        }
    }

    private fun stop() {
        Logger.i(TAG, "VoIP call ended, stopping recording")

        mRecordingJob?.cancel()
        mRecordingJob = null

        mRecorder?.release()
        mRecorder = null
    }
}
