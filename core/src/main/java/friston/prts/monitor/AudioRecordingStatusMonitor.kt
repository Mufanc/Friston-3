package friston.prts.monitor

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.AudioRecordingCallback
import android.media.AudioRecordingConfiguration
import android.media.AudioRecordingConfigurationHidden
import android.os.Handler
import android.os.Looper
import friston.prts.util.Logger

class AudioRecordingStatusMonitor(context: Context) : BaseMonitor() {

    companion object {
        private const val TAG = "AudioRecordingStatusMonitor"
    }

    private val mAudioManager = context.getSystemService(AudioManager::class.java)
    private val mHandler = Handler(Looper.getMainLooper())

    private fun onChange(configs: List<AudioRecordingConfiguration>?) {
        emit(MonitorEvent.AudioRecordingStatusChange(configs))

        Logger.i(TAG, "Recording config changed: ${configs?.size} active clients")

        configs?.forEach { config ->
            Logger.d(TAG, AudioRecordingConfigurationHidden.toLogFriendlyString(config))
        }
    }

    override fun init() {
        onChange(mAudioManager.activeRecordingConfigurations)

        mAudioManager.registerAudioRecordingCallback(
            object : AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                    onChange(configs)
                }
            },
            mHandler
        )
    }
}
