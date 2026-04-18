package friston.prts.util

import org.greenrobot.eventbus.EventBus

open class EventEmitter<T : Any> {

    protected fun emit(event: T) {
        EventBus.getDefault().post(event)
    }
}
