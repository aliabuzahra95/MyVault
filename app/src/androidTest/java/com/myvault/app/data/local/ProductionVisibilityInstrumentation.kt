package com.myvault.app.data.local

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

/** Framework-only entry point: never starts the ordinary Room/Restore test suites. */
class ProductionVisibilityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = arguments ?: Bundle()
        if (args.getString("visibilityApproved") != "true") {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("result", "Explicit disposable-test approval required") })
            return
        }
        InstrumentationRegistry.registerInstance(this, args)
        start()
    }

    override fun onStart() {
        super.onStart()
        try {
            val test = ProductionDriveVisibilityTest()
            test.verifyIsolatedProductionOAuthVisibility()
            finish(Activity.RESULT_OK, Bundle().apply {
                putString("result", "PASS: isolated production OAuth visibility phase")
                putString("proof", test.redactedEvidence())
            })
        } catch (failure: Throwable) {
            // Google exceptions can contain account/credential details: never serialize them.
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("result", "FAIL: ${failure.javaClass.simpleName}; no credential details logged")
                if (failure is NoSuchMethodError || failure is NoClassDefFoundError) putString("missingTestApi", failure.message)
                try { putString("proof", ProductionDriveVisibilityTest().redactedEvidence()) }
                catch (_: Throwable) { putString("proof", "unavailable") }
            })
        }
    }
}
