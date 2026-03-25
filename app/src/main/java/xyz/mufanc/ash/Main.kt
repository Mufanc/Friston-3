package xyz.mufanc.ash

import xyz.mufanc.aproc.annotation.AProcEntry

@AProcEntry
object Main {

    @JvmStatic
    fun main(args: Array<String>) {
//        AProcHelper.fixLoadLibrary()
        println("args: ${args.contentToString()}")
    }
}
