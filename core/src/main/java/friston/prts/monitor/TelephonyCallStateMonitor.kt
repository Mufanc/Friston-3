package friston.prts.monitor

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import friston.prts.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

class TelephonyCallStateMonitor(private val mContext: Context) : BaseMonitor() {

    companion object {
        private const val TAG = "TelephonyCallStateMonitor"
    }

    private val mTelephonyManager = mContext.getSystemService(TelephonyManager::class.java)
    private val mSubscriptionManager = mContext.getSystemService(SubscriptionManager::class.java)
    private val mExecutor = Dispatchers.IO.asExecutor()
    private val mCallbacks = mutableListOf<TelephonyCallback>()

    override fun init() {
        val subscriptions = runCatching {
            mSubscriptionManager.activeSubscriptionInfoList ?: emptyList()
        }.getOrElse {
            Logger.w(TAG, "Failed to get active subscriptions", it)
            emptyList()
        }

        if (subscriptions.isEmpty()) {
            registerFor(null)
        } else {
            subscriptions.forEach { registerFor(it.subscriptionId) }
        }
    }

    private fun registerFor(subId: Int?) {
        val telephony = subId?.let(mTelephonyManager::createForSubscriptionId) ?: mTelephonyManager
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                onChange(state, subId, telephony.phoneAccountHandle)
            }
        }

        mCallbacks += callback
        telephony.registerTelephonyCallback(mExecutor, callback)
        onChange(telephony.callState, subId, telephony.phoneAccountHandle)
    }

    private fun onChange(state: Int, subId: Int?, phoneAccount: PhoneAccountHandle?) {
        val componentName = phoneAccount?.componentName?.flattenToString()
        val accountId = phoneAccount?.id

        Logger.i(
            TAG,
            "Call state changed: state=$state, subId=$subId, component=$componentName, account=$accountId"
        )
        emit(
            MonitorEvent.TelephonyCallStateChange(
                state,
                subId,
                componentName,
                accountId,
                System.currentTimeMillis(),
            )
        )
    }
}
