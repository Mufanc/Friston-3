package friston.prts

import android.os.ProcessHidden
import friston.prts.app.FakeApp
import xyz.mufanc.aproc.annotation.AProcEntry

@AProcEntry
object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        ProcessHidden.setArgV0("Friston-3")
        FakeApp.main(args)
    }
}
