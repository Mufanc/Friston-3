package friston.prts.recorder

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.AudioRecordingConfigurationHidden
import android.media.MediaRecorder
import android.provider.CallLog
import android.telephony.TelephonyManager
import dev.rikka.tools.refine.Refine
import friston.prts.monitor.MonitorEvent
import friston.prts.util.EventReceiver
import friston.prts.util.Logger
import friston.prts.util.Ref
import kotlin.coroutines.cancellation.CancellationException
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
    private var mRecordingType: RecordingType? = null
    private var mRecorder: Any? = null
    private var mRecordingJob: Job? = null
    private var mCallSessionStartMs: Long? = null
    private var mCallSawRinging = false
    private var mCallSubId: Int? = null
    private var mCallPhoneAccountComponentName: String? = null
    private var mCallPhoneAccountId: String? = null
    private var mCallOutputFile: File? = null

    fun init() {
        Ref.subscribe(mAudioMode, mRecording3rdPartyApps) {
            val mode = mAudioMode.value
            val configs = mRecording3rdPartyApps.value

            Logger.v(TAG, "mode = $mode, configs.size = ${configs?.size}")

            val voipRecording = mode == AudioManager.MODE_IN_COMMUNICATION && !configs.isNullOrEmpty()

            if (mRecordingType != RecordingType.CALL && mIsRecording != voipRecording) {
                mIsRecording = voipRecording

                if (voipRecording) startVoip(configs) else stop()
            }
        }

        register()
    }

    override fun onEvent(event: MonitorEvent) {
        Logger.v(TAG, "Handle event: $event")

        when (event) {
            is MonitorEvent.AudioModeChange -> mAudioMode.value = event.mode
            is MonitorEvent.AudioRecordingStatusChange -> mRecordingConfigurations.value = event.configs
            is MonitorEvent.TelephonyCallStateChange -> handleTelephonyCallState(event)
        }
    }

    private fun startVoip(configs: List<AudioRecordingConfiguration>?) {
        val config = configs?.firstOrNull() ?: return
        val packageName = RecordingPathUtil.getPackageNameFromConfig(config) ?: "unknown"
        val outputFile = RecordingPathUtil.generatePath(RecordingType.VOIP, packageName)

        Logger.i(TAG, "VoIP call detected, starting recording to ${outputFile.absolutePath}")

        val recorder = VoipRecorder(mContext)
        mRecorder = recorder
        mRecordingType = RecordingType.VOIP

        mRecordingJob = mScope.launch {
            try {
                recorder.start(outputFile)
            } catch (_: CancellationException) {
                Logger.d(TAG, "Recording stopped")
            } catch (e: Exception) {
                Logger.e(TAG, "Recorder error", e)
            }
        }
    }

    private fun handleTelephonyCallState(event: MonitorEvent.TelephonyCallStateChange) {
        when (event.state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                mCallSawRinging = true
                captureCallStart(event)
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                captureCallStart(event)
                if (mRecordingType != RecordingType.CALL) {
                    if (mRecordingType != null) stop()
                    startCall()
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (mCallSessionStartMs != null && event.subId != null && event.subId != mCallSubId) {
                    return
                }

                if (mRecordingType == RecordingType.CALL) {
                    stop()
                    resolveCallLog(event.timestampMs)
                }
                resetCallSession()
            }
        }
    }

    private fun captureCallStart(event: MonitorEvent.TelephonyCallStateChange) {
        if (mCallSessionStartMs == null) {
            mCallSessionStartMs = event.timestampMs
            mCallSubId = event.subId
        }
        mCallPhoneAccountComponentName =
            event.phoneAccountComponentName ?: mCallPhoneAccountComponentName
        mCallPhoneAccountId = event.phoneAccountId ?: mCallPhoneAccountId
    }

    private fun startCall() {
        val outputFile = RecordingPathUtil.generatePath(RecordingType.CALL, "unknown")

        Logger.i(TAG, "Cellular call detected, starting recording to ${outputFile.absolutePath}")

        val recorder = CallRecorder()
        mRecorder = recorder
        mRecordingType = RecordingType.CALL
        mCallOutputFile = outputFile
        mIsRecording = true

        mRecordingJob = mScope.launch {
            try {
                recorder.start(outputFile)
            } catch (_: CancellationException) {
                Logger.d(TAG, "Call recording stopped")
            } catch (e: Exception) {
                Logger.e(TAG, "Call recorder error", e)
            }
        }
    }

    private fun stop() {
        val type = mRecordingType
        Logger.i(TAG, "Stopping recording: $type")

        mRecordingJob?.cancel()
        mRecordingJob = null

        when (val recorder = mRecorder) {
            is VoipRecorder -> recorder.release()
            is CallRecorder -> recorder.release()
        }
        mRecorder = null
        mRecordingType = null
        mIsRecording = false
    }

    private fun resolveCallLog(endWallMs: Long) {
        val startWallMs = mCallSessionStartMs ?: return
        val file = mCallOutputFile ?: return
        val session = CellularCallSession(
            startWallMs = startWallMs,
            endWallMs = endWallMs,
            type = if (mCallSawRinging) {
                CallLog.Calls.INCOMING_TYPE
            } else {
                CallLog.Calls.OUTGOING_TYPE
            },
            phoneAccountComponentName = mCallPhoneAccountComponentName,
            phoneAccountId = mCallPhoneAccountId,
        )

        mScope.launch {
            repeat(5) { attempt ->
                val match = CallLogMatcher(mContext).findMatch(session)
                if (match != null) {
                    CallLogMatcher(mContext).renameWithMatch(file, match)
                    return@launch
                }
                Logger.d(TAG, "CallLog match not ready, attempt=${attempt + 1}")
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    private fun resetCallSession() {
        mCallSessionStartMs = null
        mCallSawRinging = false
        mCallSubId = null
        mCallPhoneAccountComponentName = null
        mCallPhoneAccountId = null
        mCallOutputFile = null
    }
}
