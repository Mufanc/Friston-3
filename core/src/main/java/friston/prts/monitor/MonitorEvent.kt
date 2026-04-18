package friston.prts.monitor

import android.media.AudioRecordingConfiguration

sealed interface MonitorEvent {
    data class AudioModeChange(val mode: Int) : MonitorEvent
    data class AudioRecordingStatusChange(val configs: List<AudioRecordingConfiguration>?) : MonitorEvent
}
