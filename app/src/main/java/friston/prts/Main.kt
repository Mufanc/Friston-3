package friston.prts

import friston.prts.app.FakeApp
import xyz.mufanc.aproc.annotation.AProcEntry

@AProcEntry
object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        FakeApp.main(args)
    }
}
