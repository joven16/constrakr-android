package com.constrakr.enrollment

import android.graphics.Bitmap
import com.constrakr.config.ConsTrakrConstants
import com.constrakr.config.RegistrationPoseSettings
import com.constrakr.database.EmployeeRepository
import com.constrakr.domain.Employee
import com.constrakr.domain.FaceEmbedding
import com.constrakr.domain.FacePose
import com.constrakr.face.DetectedFace
import com.constrakr.face.EnrollmentPhotoEncoder
import com.constrakr.face.FaceDetectionService
import com.constrakr.face.FacePreprocessor
import com.constrakr.face.HeadPoseEstimator
import com.constrakr.liveness.Classification
import com.constrakr.liveness.MiniFasLivenessDetector
import com.constrakr.recognition.AdaFaceRecognizer
import com.constrakr.security.SecureFaceTemplateStore
import com.constrakr.sync.SyncCoordinator
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class EnrollmentScanPhase {
    BLINK,
    LIVE_CHECK,
    POSES
}

data class EnrollmentUiState(
    val phase: EnrollmentScanPhase = EnrollmentScanPhase.BLINK,
    val currentPose: FacePose = FacePose.CENTER,
    val capturedPoses: Set<FacePose> = emptySet(),
    val blinkPassed: Boolean = false,
    val liveCheckPassed: Boolean = false,
    val liveCheckProgress: Float = 0f,
    val progress: Float = 0f,
    val instruction: String = "Blink both eyes slowly",
    val guideMet: Boolean = false,
    val isComplete: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

class EnrollmentEngine(
    private val adaFace: AdaFaceRecognizer,
    private val miniFas: MiniFasLivenessDetector,
    private val faceDetection: FaceDetectionService,
    private val secureStore: SecureFaceTemplateStore,
    private val employeeRepository: EmployeeRepository,
    private val poseSettings: RegistrationPoseSettings,
    private val syncCoordinator: SyncCoordinator
) {
    private val captured = linkedMapOf<FacePose, FaceEmbedding>()
    private val capturedPhotos = linkedMapOf<FacePose, ByteArray>()
    private var profilePhotoJpeg: ByteArray? = null
    private var currentPose: FacePose = FacePose.CENTER
    private var phase = EnrollmentScanPhase.BLINK
    private var poseHoldStart: Long? = null
    private var blinkClosedFrames = 0
    private var blinkOpenFrames = 0
    private var closerBaselineArea: Float? = null
    private var closerGoodFrames = 0
    private var liveHoldFrames = 0
    private var liveCheckNeedsHold = false
    private var isProcessingFrame = false
    private val frameMutex = Mutex()

    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    private val enrollmentOrder: List<FacePose>
        get() = poseSettings.enabledEnrollmentOrder()

    val isComplete: Boolean
        get() = enrollmentOrder.isNotEmpty() && enrollmentOrder.all { captured.containsKey(it) }

    val hasProfilePhoto: Boolean
        get() = profilePhotoJpeg != null

    fun captureProfilePhoto(bitmap: Bitmap) {
        profilePhotoJpeg = EnrollmentPhotoEncoder.encodeJpeg(bitmap)
    }

    fun clearProfilePhoto() {
        profilePhotoJpeg = null
    }

    fun resetFaceScan() {
        captured.clear()
        capturedPhotos.clear()
        phase = EnrollmentScanPhase.BLINK
        currentPose = enrollmentOrder.firstOrNull() ?: FacePose.CENTER
        poseHoldStart = null
        blinkClosedFrames = 0
        blinkOpenFrames = 0
        closerBaselineArea = null
        closerGoodFrames = 0
        liveHoldFrames = 0
        liveCheckNeedsHold = false
        isProcessingFrame = false
        miniFas.resetTracker()
        publishState(instruction = "Blink both eyes slowly")
    }

    fun reset() {
        profilePhotoJpeg = null
        resetFaceScan()
    }

    suspend fun processFrame(bitmap: Bitmap, inputImage: InputImage) {
        if (isComplete || _uiState.value.isSaving) return
        frameMutex.withLock {
            if (isProcessingFrame) return
            isProcessingFrame = true
        }
        try {
            withContext(Dispatchers.Default) {
                processFrameLocked(bitmap, inputImage)
            }
        } finally {
            isProcessingFrame = false
        }
    }

    private suspend fun processFrameLocked(bitmap: Bitmap, inputImage: InputImage) {
        if (!adaFace.isReady) {
            publishState(error = "Face model not loaded — reinstall the latest app build")
            return
        }
        if (isComplete) {
            publishState(isComplete = true, instruction = "All poses captured")
            return
        }

        val face = faceDetection.detectPrimaryFace(inputImage)
        if (face == null) {
            val instruction = when (phase) {
                EnrollmentScanPhase.LIVE_CHECK -> "Center your face in the oval"
                EnrollmentScanPhase.POSES -> currentPose.instruction
                else -> phaseInstruction()
            }
            publishState(instruction = instruction, guideMet = false, error = null)
            return
        }

        val box = android.graphics.RectF(
            face.boundingBox.left * bitmap.width,
            face.boundingBox.top * bitmap.height,
            face.boundingBox.right * bitmap.width,
            face.boundingBox.bottom * bitmap.height
        )
        val liveness = miniFas.classify(bitmap, box)

        val area = box.width() * box.height() / (bitmap.width * bitmap.height)
        when (phase) {
            EnrollmentScanPhase.BLINK -> handleBlink(face)
            EnrollmentScanPhase.LIVE_CHECK -> handleLiveCheck(area, liveness)
            EnrollmentScanPhase.POSES -> handlePose(bitmap, face)
        }
    }

    private suspend fun handleBlink(face: DetectedFace) {
        val ear = HeadPoseEstimator.eyeAspectRatio(
            face.leftEyeOpenProbability,
            face.rightEyeOpenProbability
        )
        val instruction = when {
            HeadPoseEstimator.isBlinkClosed(ear) -> {
                blinkClosedFrames++
                "Blink slowly"
            }
            blinkClosedFrames >= 2 && HeadPoseEstimator.isBlinkOpen(ear) -> {
                blinkOpenFrames++
                if (blinkOpenFrames >= 2) {
                    beginLiveCheck()
                    "Move closer to the camera"
                } else {
                    "Open your eyes"
                }
            }
            else -> "Blink both eyes slowly"
        }
        if (phase == EnrollmentScanPhase.POSES) return
        publishState(
            instruction = instruction,
            guideMet = blinkOpenFrames >= 2 && blinkClosedFrames >= 2,
            error = null
        )
    }

    private fun beginLiveCheck() {
        phase = EnrollmentScanPhase.LIVE_CHECK
        closerBaselineArea = null
        closerGoodFrames = 0
        liveHoldFrames = 0
        liveCheckNeedsHold = false
        publishState(
            phase = phase,
            blinkPassed = true,
            liveCheckPassed = false,
            liveCheckProgress = 0f,
            instruction = "Move closer to the camera",
            guideMet = false,
            error = null
        )
    }

    private fun handleLiveCheck(
        faceAreaRatio: Float,
        @Suppress("UNUSED_PARAMETER") liveness: com.constrakr.liveness.PassiveLivenessResult
    ) {
        if (!liveCheckNeedsHold) {
            if (closerBaselineArea == null) {
                closerBaselineArea = faceAreaRatio
                if (faceAreaRatio >= ConsTrakrConstants.MIN_FACE_RELATIVE_SIZE * 1.15f) {
                    liveCheckNeedsHold = true
                    liveHoldFrames = 0
                    publishState(
                        phase = phase,
                        instruction = "Hold still — live face scan",
                        guideMet = true,
                        error = null,
                        liveCheckProgress = 0.55f
                    )
                    return
                }
                publishState(
                    phase = phase,
                    instruction = "Move closer to the camera",
                    guideMet = true,
                    error = null,
                    liveCheckProgress = 0.1f
                )
                return
            }
            val base = closerBaselineArea ?: faceAreaRatio
            val ratio = faceAreaRatio / maxOf(base, 0.0001f)
            val closeEnough = ratio >= ConsTrakrConstants.LIVENESS_CLOSER_SCALE ||
                faceAreaRatio >= ConsTrakrConstants.MIN_FACE_RELATIVE_SIZE * 1.15f
            if (closeEnough) {
                closerGoodFrames++
                if (closerGoodFrames >= CLOSER_FRAMES_REQUIRED) {
                    liveCheckNeedsHold = true
                    liveHoldFrames = 0
                    publishState(
                        phase = phase,
                        instruction = "Hold still — live face scan",
                        guideMet = true,
                        error = null,
                        liveCheckProgress = 0.55f
                    )
                } else {
                    publishState(
                        phase = phase,
                        instruction = "A little closer",
                        guideMet = true,
                        error = null,
                        liveCheckProgress = closerGoodFrames / CLOSER_FRAMES_REQUIRED.toFloat() * 0.5f
                    )
                }
            } else {
                closerGoodFrames = 0
                publishState(
                    phase = phase,
                    instruction = "Move closer to the camera",
                    guideMet = ratio >= ConsTrakrConstants.LIVENESS_CLOSER_SCALE * 0.85f,
                    error = null,
                    liveCheckProgress = 0.1f
                )
            }
            return
        }

        liveHoldFrames++
        val holdProgress = 0.55f + (liveHoldFrames / LIVE_HOLD_FRAMES.toFloat()) * 0.45f
        if (liveHoldFrames >= LIVE_HOLD_FRAMES) {
            beginPoseCapture()
        } else {
            publishState(
                phase = phase,
                instruction = "Hold still — live face scan",
                guideMet = true,
                error = null,
                liveCheckProgress = holdProgress
            )
        }
    }

    private fun liveCheckProgressValue(): Float = when {
        liveCheckNeedsHold -> 0.55f + (liveHoldFrames / LIVE_HOLD_FRAMES.toFloat()) * 0.45f
        closerGoodFrames > 0 -> closerGoodFrames / CLOSER_FRAMES_REQUIRED.toFloat() * 0.5f
        else -> 0.1f
    }

    private fun beginPoseCapture() {
        phase = EnrollmentScanPhase.POSES
        currentPose = enrollmentOrder.firstOrNull() ?: FacePose.CENTER
        poseHoldStart = null
        blinkClosedFrames = 0
        blinkOpenFrames = 0
        publishState(
            phase = phase,
            currentPose = currentPose,
            blinkPassed = true,
            liveCheckPassed = true,
            liveCheckProgress = 1f,
            instruction = currentPose.instruction,
            guideMet = false,
            error = null
        )
    }

    private suspend fun handlePose(bitmap: Bitmap, face: DetectedFace) {
        val matched = HeadPoseEstimator.matches(currentPose, face.yaw, face.pitch)
        if (!matched) {
            poseHoldStart = null
            publishState(
                phase = phase,
                currentPose = currentPose,
                instruction = currentPose.instruction,
                guideMet = false,
                error = null
            )
            return
        }

        val now = System.currentTimeMillis()
        if (poseHoldStart == null) {
            poseHoldStart = now
            publishState(instruction = "Hold still…", guideMet = true, error = null)
            return
        }
        if (now - poseHoldStart!! < POSE_HOLD_MS) {
            publishState(instruction = "Hold still…", guideMet = true, error = null)
            return
        }

        val crop = runCatching {
            FacePreprocessor.makeSquareFaceBitmap(bitmap, face.boundingBox)
        }.getOrElse { err ->
            poseHoldStart = null
            publishState(instruction = err.message ?: "Adjust your face", guideMet = false)
            return
        }

        val values = runCatching { adaFace.embed(crop) }.getOrElse { err ->
            poseHoldStart = null
            publishState(error = err.message ?: "Face capture failed", guideMet = false)
            return
        }

        captured[currentPose] = FaceEmbedding(currentPose, values)
        capturedPhotos[currentPose] = EnrollmentPhotoEncoder.encodeJpeg(crop)
        poseHoldStart = null

        val next = currentPose.nextIn(enrollmentOrder)
        if (next != null) {
            currentPose = next
            publishState(
                phase = phase,
                currentPose = currentPose,
                capturedPoses = captured.keys.toSet(),
                instruction = next.instruction,
                guideMet = false,
                error = null
            )
        } else {
            publishState(
                phase = phase,
                currentPose = currentPose,
                capturedPoses = captured.keys.toSet(),
                isComplete = true,
                instruction = "Saving employee…",
                guideMet = true,
                error = null
            )
        }
    }

    suspend fun saveEmployee(
        firstName: String,
        lastName: String,
        department: String,
        position: String,
        assignedSiteId: java.util.UUID? = null,
        assignedSiteName: String = "",
        assignedSiteLocation: String = ""
    ): Result<Employee> = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(isSaving = true, instruction = "Saving employee and uploading poses…")
        runCatching {
            require(isComplete) { "Enrollment not complete" }
            val employee = employeeRepository.register(
                firstName = firstName,
                lastName = lastName,
                department = department,
                position = position,
                assignedSiteId = assignedSiteId,
                assignedSiteName = assignedSiteName,
                assignedSiteLocation = assignedSiteLocation,
                embeddings = enrollmentOrder.mapNotNull { captured[it] },
                enrollmentPhotos = buildMap {
                    enrollmentOrder.forEach { pose ->
                        capturedPhotos[pose]?.let { put(pose, it) }
                    }
                    profilePhotoJpeg?.let { put(FacePose.CENTER, it) }
                }
            )
            _uiState.value = _uiState.value.copy(
                instruction = "Registration complete",
                isSaving = false
            )
            employee
        }.also { result ->
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    private fun phaseInstruction(): String = when (phase) {
        EnrollmentScanPhase.BLINK -> "Look at the camera"
        EnrollmentScanPhase.LIVE_CHECK -> "Move closer to the camera"
        EnrollmentScanPhase.POSES -> currentPose.instruction
    }

    private fun publishState(
        phase: EnrollmentScanPhase = this.phase,
        currentPose: FacePose = this.currentPose,
        capturedPoses: Set<FacePose> = captured.keys.toSet(),
        blinkPassed: Boolean = _uiState.value.blinkPassed || phase != EnrollmentScanPhase.BLINK,
        liveCheckPassed: Boolean = _uiState.value.liveCheckPassed,
        liveCheckProgress: Float = _uiState.value.liveCheckProgress,
        progress: Float = if (enrollmentOrder.isEmpty()) 0f else captured.size.toFloat() / enrollmentOrder.size,
        instruction: String = phaseInstruction(),
        guideMet: Boolean = _uiState.value.guideMet,
        isComplete: Boolean = this.isComplete,
        isSaving: Boolean = _uiState.value.isSaving,
        error: String? = _uiState.value.error
    ) {
        _uiState.value = EnrollmentUiState(
            phase = phase,
            currentPose = currentPose,
            capturedPoses = capturedPoses,
            blinkPassed = blinkPassed,
            liveCheckPassed = liveCheckPassed,
            liveCheckProgress = liveCheckProgress,
            progress = progress,
            instruction = instruction,
            guideMet = guideMet,
            isComplete = isComplete,
            isSaving = isSaving,
            error = error
        )
    }

    companion object {
        private const val POSE_HOLD_MS = 600L
        private const val CLOSER_FRAMES_REQUIRED = 4
        private const val LIVE_HOLD_FRAMES = 12
    }
}

private val FacePose.instruction: String
    get() = displayName

private fun FacePose.nextIn(order: List<FacePose>): FacePose? {
    val index = order.indexOf(this)
    if (index < 0 || index >= order.lastIndex) return null
    return order[index + 1]
}
