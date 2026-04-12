package friston.prts.util

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@Suppress("unused")
abstract class EventReceiver<T : Any>(
    private val mEventType: Class<T>,
    mode: ThreadMode = ThreadMode.MAIN
) {

    protected abstract fun onEvent(event: T)

    private inner class MainProxy {
        @Subscribe(threadMode = ThreadMode.MAIN)
        fun handle(event: Any) {
            if (mEventType.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                onEvent(event as T)
            }
        }
    }

    private inner class PostingProxy {
        @Subscribe(threadMode = ThreadMode.POSTING)
        fun handle(event: Any) {
            if (mEventType.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                onEvent(event as T)
            }
        }
    }

    private val proxy: Any = when (mode) {
        ThreadMode.MAIN -> MainProxy()
        ThreadMode.POSTING -> PostingProxy()
        else -> error("Unsupported ThreadMode: $mode")
    }

    protected fun register() {
        EventBus.getDefault().register(proxy)
    }

    protected fun unregister() {
        EventBus.getDefault().unregister(proxy)
    }
}
