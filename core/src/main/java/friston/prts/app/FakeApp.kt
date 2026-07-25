package friston.prts.app

import android.app.ActivityThread
import android.os.Handler
import android.os.Looper
import android.system.Os
import friston.prts.Configs
import friston.prts.monitor.AudioModeChangeMonitor
import friston.prts.monitor.AudioRecordingStatusMonitor
import friston.prts.monitor.TelephonyCallStateMonitor
import friston.prts.recorder.RecordingController
import friston.prts.recorder.RecordingPathUtil
import friston.prts.util.Logger

object FakeApp {

    private const val TAG = "AppLike"

    @Suppress("DEPRECATION")
    private fun prepare() {
        if (Os.getuid() == 0 && Configs.CFG_UID != Os.getuid()) {
            Os.setuid(Configs.CFG_UID)
        }
    }

    @Suppress("DEPRECATION")
    fun main(args: Array<String>) {
        Logger.d(TAG, "main")
        prepare()

        Looper.prepareMainLooper()
        ActivityThread.initializeMainlineModules()

        val activityThread = ActivityThread.systemMain()
        val handler = Handler(Looper.getMainLooper())
        val context = FakeContext(activityThread.systemContext)

        Thread.setDefaultUncaughtExceptionHandler { th, err ->
            Logger.e(TAG, "Uncaught exception in thread: $th", err)
        }

        handler.post { appMain(context) }
        Looper.loop()
    }

    private fun appMain(context: FakeContext) {
        Logger.d(TAG, "app main")

        RecordingPathUtil.ensureOutputDir()

        val controller = RecordingController(context)
        controller.init()

        AudioRecordingStatusMonitor(context).init()
        AudioModeChangeMonitor(context).init()
        TelephonyCallStateMonitor(context).init()

        Logger.i(TAG, "Monitors and recording controller initialized")
    }
}
