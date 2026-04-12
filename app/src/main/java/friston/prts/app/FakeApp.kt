package friston.prts.app

import android.app.ActivityThread
import android.os.Handler
import android.os.Looper
import android.system.Os
import friston.prts.Configs
import friston.prts.monitor.AudioModeChangeMonitor
import friston.prts.monitor.AudioRecordingStatusMonitor
import friston.prts.recorder.RecordingController
import friston.prts.util.Logger

object FakeApp {

    private const val TAG = "AppLike"

    @Suppress("DEPRECATION")
    private fun prepare() {
        if (Os.getuid() == 0 && Configs.CFG_UID != Os.getuid()) {
            Os.setuid(Configs.CFG_UID)
        }

        Thread.setDefaultUncaughtExceptionHandler { th, err ->
            Logger.e(TAG, "Uncaught exception in thread: $th", err)
        }
    }

    @Suppress("DEPRECATION")
    private val mLooper: Looper = run {
        Looper.prepareMainLooper()
        Looper.getMainLooper()
    }

    private val mHandler = Handler(mLooper)

    private val mActivityThread = ActivityThread.systemMain()

    private val mContext = FakeContext(mActivityThread.systemContext)

    fun context() = mContext

    fun main(args: Array<String>) {
        Logger.d(TAG, "main")
        prepare()
        mHandler.post { appMain(args) }
        Looper.loop()
    }

    fun appMain(args: Array<String>) {
        Logger.d(TAG, "app main")

        val controller = RecordingController(mContext)
        controller.init()

        AudioRecordingStatusMonitor(mContext).init()
        AudioModeChangeMonitor(mContext).init()

        Logger.i(TAG, "Monitors and recording controller initialized")
    }
}