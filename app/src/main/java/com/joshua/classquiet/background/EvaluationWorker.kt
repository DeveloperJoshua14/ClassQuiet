package com.joshua.classquiet.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.joshua.classquiet.ClassQuietApplication

class EvaluationWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ClassQuietApplication
        val source = inputData.getString(KEY_SOURCE) ?: "periodic"
        return runCatching {
            app.coordinator.performEvaluation(source)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SOURCE = "source"
    }
}

