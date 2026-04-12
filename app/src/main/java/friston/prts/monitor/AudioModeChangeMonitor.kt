package friston.prts.monitor

import android.content.Context
import android.media.AudioManager
import friston.prts.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

class AudioModeChangeMonitor(context: Context) : BaseMonitor() {

    companion object {
        private const val TAG = "AudioModeChangeMonitor"
    }

    private val mAudioManager = context.getSystemService(AudioManager::class.java)
    private val mExecutor = Dispatchers.IO.asExecutor()

    private fun onChange(mode: Int) {
        emit(MonitorEvent.AudioModeChange(mode))
        Logger.i(TAG, "Audio mode changed: $mode")
    }

    override fun init() {
        onChange(mAudioManager.mode)

        mAudioManager.addOnModeChangedListener(mExecutor) { mode ->
            onChange(mode)
        }
    }
}
