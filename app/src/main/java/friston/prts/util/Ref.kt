package friston.prts.util

import java.util.LinkedList
import java.util.Queue

open class Ref<T>(initial: T) {

    protected var mInner: T = initial

    open var value: T
        get() = mInner
        set(value) {
            mInner = value
            onUpdate()
        }

    protected val mSuccessors = HashSet<Ref<*>>()
    protected var mCallback: (() -> Unit)? = null

    private fun dfs(rc: HashMap<Ref<*>, Int>) {
        if (!rc.contains(this)) rc[this] = 0
        mSuccessors.forEach { node ->
            node.dfs(rc)
            rc[node] = rc[node]!! + 1
        }
    }

    private fun onUpdate() {
        val nodes = HashMap<Ref<*>, Int>().apply { dfs(this) }
        val queue: Queue<Ref<*>> = LinkedList<Ref<*>>().also { it.offer(this) }

        val topological = LinkedList<Ref<*>>()

        while (queue.isNotEmpty()) {
            val node = queue.poll()

            topological.offer(node)

            node.mSuccessors.forEach { next ->
                nodes[next] = nodes[next]!! - 1
                if (nodes[next] == 0) queue.offer(next)
            }
        }

        assert(topological.size == nodes.size) { "Circular dependency detected!" }

        topological.forEach { it.mCallback?.invoke() }
    }

    companion object {

        fun <T> compute(initial: T, vararg refs: Ref<*>, calc: () -> T): Ref<T> {
            val holder = ComputedRef(initial)

            refs.forEach {
                it.mSuccessors.add(holder)
            }

            holder.mCallback = {
                holder.mInner = calc()
            }

            return holder
        }

        fun subscribe(vararg refs: Ref<*>, callback: () -> Unit) {
            compute(null, *refs) {
                callback()
            }
        }
    }

    class ComputedRef<T>(initial: T) : Ref<T>(initial) {
        override var value
            get() = super.value
            set(_) {
                throw UnsupportedOperationException("Cannot update a passive Ref!")
            }
    }
}
