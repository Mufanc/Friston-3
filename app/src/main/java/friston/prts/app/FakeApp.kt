package friston.prts.app

import android.app.ActivityThread
import android.os.Handler
import android.os.Looper
import android.system.Os
import friston.prts.Configs
import friston.prts.recorder.VoipRecorder
import friston.prts.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object FakeApp {

    private const val TAG = "AppLike"

    @Suppress("DEPRECATION")
    private fun prepare() {
        if (Os.getuid() == 0 && Configs.CFG_UID != Os.getuid()) {
            Os.setuid(Configs.CFG_UID)
        }

        Thread.setDefaultUncaughtExceptionHandler { th, err ->
            Logger.e(TAG, "uncaught exception in thread: $th", err)
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

    fun main(args: Array<String>) {
        Logger.d(TAG, "main")
        prepare()
        mHandler.post { appMain(args) }
        Looper.loop()
    }

    fun appMain(args: Array<String>) {
        Logger.d(TAG, "app main")

        val recorder = VoipRecorder(mContext)
        val outputFile = File("/data/misc/perfetto-traces/output.aac")

        Logger.d(TAG, "output file: ${outputFile.absolutePath}")

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                recorder.start(outputFile)
            } catch (e: Exception) {
                Logger.e(TAG, "recorder error", e)
            }
        }
    }
}