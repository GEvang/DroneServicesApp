package com.example.droneservicesapp.mavserver

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.core.util.Event
import com.example.droneservicesapp.data.mavlink.MissionService
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
    @Volatile private var missionDownloadInProgress = false
    @Volatile private var lastDownloadAttemptMs: Long = 0L
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
        val now = System.currentTimeMillis()
        if (now - lastDownloadAttemptMs < debounceMs) return
        lastDownloadAttemptMs = now

        if (missionDownloadInProgress) return
        missionDownloadInProgress = true

        repoDisposables.add(
            Single.fromCallable { missionService.downloadMission() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { missionDownloadInProgress = false }
                .subscribe(
                    { items -> missionItems.postValue(items) },
                    { err -> Log.e(logTag, "downloadMission failed: ${err.message}", err) }
                )
        )
    }

    fun uploadMission(
        items: ArrayList<MissionItemInt>,
        activityVm: MainActivityViewModel,
        uploadTimeoutMs: Long,
        logTag: String,
    ) {
        currentUploadCancelToken?.set(true)
        currentUploadDisposable?.dispose()
        currentUploadDisposable = null
        currentUploadCancelToken = null

        uploadProgressPercent.postValue(0)

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
                    if (currentUploadCancelToken === token) {
                        if (token.get()) {
                            uploadProgressPercent.postValue(0)
                        }
                        currentUploadDisposable = null
                        currentUploadCancelToken = null
                    }
                }
                .subscribe(
                    { ok ->
                        Log.i(logTag, "uploadMission result=$ok")
                        if (ok) {
                            uploadProgressPercent.postValue(100)
                            activityVm.mapAction.postValue(
                                Event(MainActivityViewModel.MapAction.UploadMissionSuccess)
                            )
                        } else {
                            uploadProgressPercent.postValue(0)
                            activityVm.mapAction.postValue(
                                Event(
                                    MainActivityViewModel.MapAction.UploadMissionFailed(
                                        "Upload rejected or timed out"
                                    )
                                )
                            )
                        }
                    },
                    { err ->
                        Log.e(logTag, "uploadMission failed: ${err.message}", err)
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
        currentUploadCancelToken?.set(true)
        currentUploadDisposable?.dispose()
        currentUploadDisposable = null
        currentUploadCancelToken = null
    }
}
