package friston.prts.recorder

import android.app.ActivityManagerHidden
import android.content.Context
import android.content.ContentResolver
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.provider.CallLog
import friston.prts.util.Logger
import java.io.File

data class CellularCallSession(
    val startWallMs: Long,
    val endWallMs: Long,
    val type: Int?,
    val phoneAccountComponentName: String?,
    val phoneAccountId: String?,
)

data class CallLogMatch(
    val number: String,
    val date: Long,
    val type: Int,
    val duration: Long,
)

class CallLogMatcher(private val mContext: Context) {

    companion object {
        private const val TAG = "CallLogMatcher"
        private const val DATE_BEFORE_MS = 3_000L
        private const val DATE_AFTER_MS = 8_000L
        private const val DURATION_TOLERANCE_SEC = 5L
    }

    fun findMatch(session: CellularCallSession): CallLogMatch? {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )

        val clauses = mutableListOf("${CallLog.Calls.DATE} BETWEEN ? AND ?")
        val args = mutableListOf(
            (session.startWallMs - DATE_BEFORE_MS).toString(),
            (session.startWallMs + DATE_AFTER_MS).toString(),
        )

        session.type?.let {
            clauses += "${CallLog.Calls.TYPE} = ?"
            args += it.toString()
        }

        session.phoneAccountComponentName?.let {
            clauses += "${CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME} = ?"
            args += it
        }

        session.phoneAccountId?.let {
            clauses += "${CallLog.Calls.PHONE_ACCOUNT_ID} = ?"
            args += it
        }

        val maxElapsedSec = ((session.endWallMs - session.startWallMs) / 1000).coerceAtLeast(0)
        val candidates = mutableListOf<CallLogMatch>()

        val authority = CallLog.Calls.CONTENT_URI.authority ?: return null
        val userId = 0
        val token = Binder()
        val activityManager = ActivityManagerHidden.getService()
        val holder = activityManager.getContentProviderExternal(authority, userId, token, TAG)
            ?: return null
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, clauses.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${CallLog.Calls.DATE} DESC",
            )
        }
        val callingPackage = when (Process.myUid()) {
            Process.ROOT_UID -> "root"
            Process.SHELL_UID -> "com.android.shell"
            else -> mContext.opPackageName
        }
        val attributionSource = android.content.AttributionSource.Builder(Process.myUid())
            .setPackageName(callingPackage)
            .build()

        try {
            val provider = holder.provider ?: return null
            provider.query(
                attributionSource,
                CallLog.Calls.CONTENT_URI,
                projection,
                queryArgs,
                null,
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)
                    val duration = cursor.getLong(durationIndex)

                    if (number.isNullOrBlank()) continue
                    if (duration > maxElapsedSec + DURATION_TOLERANCE_SEC) continue

                    candidates += CallLogMatch(
                        number = number,
                        date = cursor.getLong(dateIndex),
                        type = cursor.getInt(typeIndex),
                        duration = duration,
                    )
                }
            }
        } finally {
            activityManager.removeContentProviderExternalAsUser(authority, token, userId)
        }

        return when (candidates.size) {
            0 -> {
                Logger.w(TAG, "No matching call log found")
                null
            }
            1 -> candidates.single()
            else -> {
                Logger.w(TAG, "Multiple matching call logs found: $candidates")
                null
            }
        }
    }

    fun renameWithMatch(file: File, match: CallLogMatch): File? {
        val safeNumber = match.number.replace(Regex("[^0-9A-Za-z+._-]"), "_")
        val renamed = File(file.parentFile, file.name.replaceFirst("call-unknown-", "call-$safeNumber-"))

        return if (file.renameTo(renamed)) {
            RecordingPathUtil.setFilePermissions(renamed)
            Logger.i(TAG, "Renamed call recording to ${renamed.absolutePath}")
            renamed
        } else {
            Logger.w(TAG, "Failed to rename ${file.absolutePath} to ${renamed.absolutePath}")
            null
        }
    }
}
