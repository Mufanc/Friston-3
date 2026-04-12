package friston.prts.app

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import friston.prts.Configs

class FakeContext(ctx: Context) : ContextWrapper(ctx) {

    override fun getPackageName(): String {
        return Configs.CFG_PACKAGE
    }

    override fun getOpPackageName(): String {
        return Configs.CFG_PACKAGE
    }

    override fun getAttributionSource(): AttributionSource {
        return AttributionSource.Builder(Configs.CFG_UID)
            .apply { setPackageName(Configs.CFG_PACKAGE) }
            .build()
    }
}
