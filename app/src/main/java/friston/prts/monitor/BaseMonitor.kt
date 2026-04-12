package friston.prts.monitor

import friston.prts.util.EventEmitter

abstract class BaseMonitor : EventEmitter<MonitorEvent>() {
    abstract fun init()
}
