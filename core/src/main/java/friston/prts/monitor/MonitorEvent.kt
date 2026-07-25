package friston.prts.monitor

import android.media.AudioRecordingConfiguration

sealed interface MonitorEvent {
    data class AudioModeChange(val mode: Int) : MonitorEvent
    data class AudioRecordingStatusChange(val configs: List<AudioRecordingConfiguration>?) : MonitorEvent
    data class TelephonyCallStateChange(
        val state: Int,
        val subId: Int?,
        val phoneAccountComponentName: String?,
        val phoneAccountId: String?,
        val timestampMs: Long,
    ) : MonitorEvent
}
