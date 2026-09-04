package com.example.droneservicesapp.mavserver

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.core.util.Event
import com.example.droneservicesapp.data.mavlink.MissionService
import com.example.droneservicesapp.data.mavlink.MissionUploadResult
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import io.dronefleet.mavlink.common.MissionItemInt
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicBoolean

internal class DroneMissionController(
    private val missionService: MissionService,
    private val missionItems: MutableLiveData<ArrayList<MissionItemInt>>,
    private val uploadProgressPercent: MutableLiveData<Int>,
    private val repoDisposables: CompositeDisposable,
) {
    private val uploadLock = Any()
    @Volatile private var missionDownloadInProgress = false
    @Volatile private var lastDownloadAttemptMs: Long = 0L
    @Volatile private var currentDownloadDisposable: Disposable? = null
    @Volatile private var currentDownloadCancelToken: AtomicBoolean? = null
    @Volatile private var uploadInProgress = false
    @Volatile private var lastUploadFinishedMs: Long = 0L
    @Volatile private var currentUploadDisposable: Disposable? = null
    @Volatile private var currentUploadCancelToken: AtomicBoolean? = null

    fun updateTargetIds(systemId: Int, componentId: Int) {
        missionService.targetSystemId = systemId
        missionService.targetComponentId = componentId
    }

    fun downloadMission(
        debounceMs: Long,
        logTag: String,
    ) {
        synchronized(uploadLock) {
            val now = System.currentTimeMillis()
            if (now - lastDownloadAttemptMs < debounceMs) return
            lastDownloadAttemptMs = now

            if (uploadInProgress) {
                Log.d(logTag, "Skipping mission download while upload is in progress")
                DiagnosticLog.event("mission", "download_skipped", "WARN", mapOf("reason" to "upload_in_progress"))
                return
            }

            if (missionDownloadInProgress) return
            missionDownloadInProgress = true
        }

        val token = AtomicBoolean(false)
        currentDownloadCancelToken = token

        val disposable = Single.fromCallable {
            try {
                missionService.downloadMission(cancel = token)
            } catch (interrupted: InterruptedException) {
                Log.d(logTag, "downloadMission interrupted during cancellation")
                Thread.currentThread().interrupt()
                ArrayList<MissionItemInt>()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally {
                synchronized(uploadLock) {
                    if (currentDownloadCancelToken === token) {
                        currentDownloadDisposable = null
                        currentDownloadCancelToken = null
                    }
                    missionDownloadInProgress = false
                }
            }
            .subscribe(
                { items ->
                    if (!token.get()) {
                        missionItems.postValue(items)
                        DiagnosticLog.event("mission", "download_succeeded", data = mapOf("itemCount" to items.size))
                    }
                },
                { err ->
                    Log.e(logTag, "downloadMission failed: ${err.message}", err)
                    DiagnosticLog.event("mission", "download_failed", "ERROR", mapOf("error" to err.javaClass.simpleName, "reason" to (err.message ?: "unknown")))
                }
            )

        currentDownloadDisposable = disposable
        repoDisposables.add(disposable)
    }

    fun uploadMission(
        items: ArrayList<MissionItemInt>,
        activityVm: MainActivityViewModel,
        uploadTimeoutMs: Long,
        logTag: String,
    ) {
        synchronized(uploadLock) {
            if (uploadInProgress) {
                val reason = "Mission upload already in progress. Wait for it to finish before retrying."
                Log.w(logTag, reason)
                activityVm.mapAction.postValue(
                    Event(MainActivityViewModel.MapAction.UploadMissionFailed(reason))
                )
                DiagnosticLog.event("mission", "upload_rejected", "WARN", mapOf("reason" to reason, "itemCount" to items.size))
                return
            }

            val now = System.currentTimeMillis()
            val remainingCooldownMs = UPLOAD_RETRY_COOLDOWN_MS - (now - lastUploadFinishedMs)
            if (remainingCooldownMs > 0L) {
                val seconds = ((remainingCooldownMs + 999L) / 1000L).coerceAtLeast(1L)
                val reason = "Mission upload is resetting. Retry in ${seconds}s."
                Log.w(logTag, reason)
                activityVm.mapAction.postValue(
                    Event(MainActivityViewModel.MapAction.UploadMissionFailed(reason))
                )
                return
            }

            if (missionDownloadInProgress) {
                Log.w(logTag, "Cancelling mission download before starting upload")
                currentDownloadCancelToken?.set(true)
                currentDownloadDisposable?.dispose()
                currentDownloadDisposable = null
                currentDownloadCancelToken = null
                missionDownloadInProgress = false
            }

            uploadInProgress = true
        }

        uploadProgressPercent.postValue(0)
        DiagnosticLog.event("mission", "upload_started", data = mapOf("itemCount" to items.size, "timeoutMs" to uploadTimeoutMs))

        val token = AtomicBoolean(false)
        currentUploadCancelToken = token

        val disposable =
            Single.fromCallable {
                missionService.uploadMission(
                    items,
                    timeoutMs = uploadTimeoutMs,
                    cancel = token,
                    onProgress = { _, _, percent ->
                        uploadProgressPercent.postValue(percent)
                    }
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    synchronized(uploadLock) {
                        if (currentUploadCancelToken === token) {
                            if (token.get()) {
                                uploadProgressPercent.postValue(0)
                            }
                            currentUploadDisposable = null
                            currentUploadCancelToken = null
                        }
                        uploadInProgress = false
                        lastUploadFinishedMs = System.currentTimeMillis()
                    }
                }
                .subscribe(
                    { result ->
                        when (result) {
                            MissionUploadResult.Success -> {
                                Log.i(logTag, "uploadMission result=success")
                                DiagnosticLog.event("mission", "upload_succeeded", data = mapOf("itemCount" to items.size))
                                uploadProgressPercent.postValue(100)
                                activityVm.mapAction.postValue(
                                    Event(MainActivityViewModel.MapAction.UploadMissionSuccess)
                                )
                            }
                            is MissionUploadResult.Failure -> {
                                Log.i(logTag, "uploadMission result=false reason=${result.reason}")
                                DiagnosticLog.event("mission", "upload_failed", "ERROR", mapOf("itemCount" to items.size, "reason" to result.reason))
                                uploadProgressPercent.postValue(0)
                                activityVm.mapAction.postValue(
                                    Event(MainActivityViewModel.MapAction.UploadMissionFailed(result.reason))
                                )
                            }
                        }
                    },
                    { err ->
                        Log.e(logTag, "uploadMission failed: ${err.message}", err)
                        DiagnosticLog.event("mission", "upload_failed", "ERROR", mapOf("itemCount" to items.size, "error" to err.javaClass.simpleName, "reason" to (err.message ?: "unknown")))
                        uploadProgressPercent.postValue(0)
                        activityVm.mapAction.postValue(
                            Event(
                                MainActivityViewModel.MapAction.UploadMissionFailed(
                                    err.message ?: "Upload error"
                                )
                            )
                        )
                    }
                )

        currentUploadDisposable = disposable
        repoDisposables.add(disposable)
    }

    fun clear() {
        synchronized(uploadLock) {
            currentUploadCancelToken?.set(true)
            currentUploadDisposable?.dispose()
            currentUploadDisposable = null
            currentUploadCancelToken = null
            currentDownloadCancelToken?.set(true)
            currentDownloadDisposable?.dispose()
            currentDownloadDisposable = null
            currentDownloadCancelToken = null
            missionDownloadInProgress = false
            uploadInProgress = false
            lastUploadFinishedMs = System.currentTimeMillis()
        }
    }

    private companion object {
        private const val UPLOAD_RETRY_COOLDOWN_MS = 10_000L
    }
}
