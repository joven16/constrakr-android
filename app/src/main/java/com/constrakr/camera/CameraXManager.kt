package com.constrakr.camera

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Port of iOS [CameraManager.swift] — CameraX front camera, KEEP_ONLY_LATEST analysis.
 * Depth: NoDepthProvider on Galaxy S8/A17 (no AVDepthData equivalent).
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    var onFrame: ((androidx.camera.core.ImageProxy) -> Unit)? = null

    fun bind(previewView: PreviewView) {
        previewView.post {
            bindWhenReady(previewView)
        }
    }

    private fun bindWhenReady(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            provider.unbindAll()

            val targetRotation = previewView.display?.rotation
                ?: run {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay?.rotation
                }
                ?: Surface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetRotation(targetRotation)
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val analysis = ImageAnalysis.Builder()
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(analysisExecutor) { proxy ->
                        try {
                            onFrame?.invoke(proxy)
                        } finally {
                            proxy.close()
                        }
                    }
                }

            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))
    }


    fun shutdown() {
        analysisExecutor.shutdown()
    }
}
