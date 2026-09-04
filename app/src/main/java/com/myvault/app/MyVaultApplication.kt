package com.myvault.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.myvault.app.widget.note.NoteWidgetUpdateCoordinator
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyVaultApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var noteWidgetUpdateCoordinator: NoteWidgetUpdateCoordinator
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { removeLegacySafetyBackups(filesDir) }
        PDFBoxResourceLoader.init(this)
        noteWidgetUpdateCoordinator.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

internal fun removeLegacySafetyBackups(filesDir: File) {
    runCatching {
        File(filesDir, "emergency_backups").deleteRecursively()
    }
}
