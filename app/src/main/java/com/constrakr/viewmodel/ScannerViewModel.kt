package com.constrakr.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.constrakr.ConsTrakrApp
import com.constrakr.attendance.AttendancePhotoStore
import com.constrakr.attendance.VerificationFrameInput
import com.constrakr.attendance.VerificationResult
import com.constrakr.face.EnrollmentPhotoEncoder
import com.constrakr.domain.CheckType
import com.constrakr.domain.FaceEmbedding
import com.constrakr.domain.FacePose
import com.constrakr.face.FacePreprocessor
import com.constrakr.liveness.Classification
import com.constrakr.ui.components.ScannerBorderState
import com.constrakr.scanner.ScannerPhrases
import com.google.mlkit.vision.common.InputImage
import com.constrakr.config.ConsTrakrConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ScannerViewModel : ViewModel() {
    private val container get() = ConsTrakrApp.instance.container

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _speakEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val speakEvent: SharedFlow<String> = _speakEvent.asSharedFlow()

    private val _secondaryStatus = MutableStateFlow<String?>(null)
    val secondaryStatus: StateFlow<String?> = _secondaryStatus.asStateFlow()

    private val _lastSimilarity = MutableStateFlow<Float?>(null)
    val lastSimilarity: StateFlow<Float?> = _lastSimilarity

    private val _checkType = MutableStateFlow(CheckType.CHECK_IN)
    val checkTypeState = _checkType.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _pendingConfirm = MutableStateFlow<CheckType?>(null)
    val pendingConfirm: StateFlow<CheckType?> = _pendingConfirm.asStateFlow()

    private val _borderState = MutableStateFlow(ScannerBorderState.Ready)
    val borderState: StateFlow<ScannerBorderState> = _borderState.asStateFlow()

    private val _locationBlocked = MutableStateFlow(false)
    val locationBlocked: StateFlow<Boolean> = _locationBlocked.asStateFlow()

    private val _locationMessage = MutableStateFlow<String?>(null)
    val locationMessage: StateFlow<String?> = _locationMessage.asStateFlow()

    val geofenceRequired: Boolean
        get() = container.geofenceSettings.isRequired

    val needsLocationPermission: Boolean
        get() = geofenceRequired && !container.locationGate.hasLocationPermission()

    val operatingSiteLabel: String
        get() = container.accessSession.operatorSiteTitle ?: "No site assigned"

    val modelsReady: Boolean
        get() = ConsTrakrApp.instance.adaFaceRecognizer.isReady

    private var checkType: CheckType = CheckType.CHECK_IN
    private var sessionIdentity: java.util.UUID? = null
    private var lastPunchMillis: Long = 0L
    @Volatile var frameAnalysisEnabled: Boolean = false
        private set
    private var enrolledCache: List<com.constrakr.domain.Employee> = emptyList()
    private val frameMutex = Mutex()
    @Volatile private var sessionFinalized = false
    private var lastSpokenCaption: String? = null

    init {
        refreshLocationGate()
    }

    fun refreshLocationGate() {
        viewModelScope.launch {
            if (!container.geofenceSettings.isRequired) {
                _locationBlocked.value = false
                _locationMessage.value = null
                return@launch
            }
            if (!container.locationGate.hasLocationPermission()) {
                _locationBlocked.value = true
                _locationMessage.value =
                    "Location permission required. Tap Allow Location below."
                return@launch
            }
            val site = container.jobSiteStore.defaultSite
            if (site == null || !site.hasCoordinate) {
                _locationBlocked.value = true
                _locationMessage.value = "Default job site has no GPS pin. Set it under More → Job Sites."
                return@launch
            }
            container.locationGate.isInsideDefaultSite()
                .onSuccess { inside ->
                    _locationBlocked.value = !inside
                    _locationMessage.value = if (inside) {
                        null
                    } else {
                        "You are outside ${site.displayTitle}. Move to the site or tap Recheck site."
                    }
                }
                .onFailure { err ->
                    _locationBlocked.value = true
                    _locationMessage.value = err.message
                }
        }
    }

    fun onLocationPermissionGranted() {
        refreshLocationGate()
    }

    fun startPunch(type: CheckType) {
        if (_isSessionActive.value) return
        if (!modelsReady) {
            _status.value = "Face model not loaded — reinstall the app"
            _borderState.value = ScannerBorderState.Error
            return
        }
        if (_locationBlocked.value) {
            _status.value = _locationMessage.value ?: "Location check failed"
            _borderState.value = ScannerBorderState.Warning
            return
        }
        checkType = type
        _checkType.value = type
        beginSession(type)
    }

    fun requestStart() = startPunch(checkType)

    fun cancelConfirm() {
        _pendingConfirm.value = null
    }

    fun confirmStart() {
        val type = _pendingConfirm.value ?: return
        _pendingConfirm.value = null
        beginSession(type)
    }

    private fun beginSession(type: CheckType) {
        checkType = type
        _checkType.value = type
        container.clockGuard.bootstrapIfNeeded()
        container.verificationEngine.resetSession()
        container.livenessManager.reset()
        sessionIdentity = null
        sessionFinalized = false
        lastSpokenCaption = null
        frameAnalysisEnabled = true
        viewModelScope.launch { enrolledCache = container.employeeRepository.getAllEnrolled() }
        _isSessionActive.value = true
        updateCaption("Look at the camera", ScannerBorderState.Active)
        _secondaryStatus.value = null
        viewModelScope.launch {
            _speakEvent.emit(ScannerPhrases.READY)
        }
    }

    fun cancelSession() {
        resetToIdle()
    }

    private fun resetToIdle() {
        frameAnalysisEnabled = false
        sessionFinalized = false
        lastSpokenCaption = null
        _isSessionActive.value = false
        container.verificationEngine.resetSession()
        container.livenessManager.reset()
        sessionIdentity = null
        _lastSimilarity.value = null
        _borderState.value = ScannerBorderState.Ready
        _status.value = ""
        _secondaryStatus.value = null
    }

    private fun updateCaption(
        text: String,
        border: ScannerBorderState = _borderState.value
    ) {
        _status.value = text
        _borderState.value = border
    }

    private fun finishWithAnnouncement(
        message: String,
        border: ScannerBorderState,
        dismissMs: Long = ConsTrakrConstants.SCANNER_RESULT_DISMISS_MS
    ) {
        if (sessionFinalized) return
        sessionFinalized = true
        frameAnalysisEnabled = false
        _isSessionActive.value = false
        _status.value = message
        _secondaryStatus.value = null
        _borderState.value = border
        container.verificationEngine.resetSession()
        container.livenessManager.reset()
        sessionIdentity = null
        viewModelScope.launch {
            lastSpokenCaption = message
            _speakEvent.emit(message)
            delay(dismissMs)
            resetToIdle()
        }
    }

    fun onFrame(bitmap: Bitmap, inputImage: InputImage) {
        if (!frameAnalysisEnabled || !_isSessionActive.value || sessionFinalized) return
        viewModelScope.launch {
            if (!frameMutex.tryLock()) return@launch
            try {
                if (!frameAnalysisEnabled || !_isSessionActive.value || sessionFinalized) return@launch
                processFrame(bitmap, inputImage)
            } finally {
                frameMutex.unlock()
            }
        }
    }

    private suspend fun processFrame(bitmap: Bitmap, inputImage: InputImage) {
            if (System.currentTimeMillis() - lastPunchMillis < ConsTrakrConstants.SCANNER_COOLDOWN_MS) return
            val app = ConsTrakrApp.instance
            if (!app.adaFaceRecognizer.isReady) {
                updateCaption("AdaFace model not loaded", ScannerBorderState.Error)
                return
            }
            val face = container.faceDetection.detectPrimaryFace(inputImage) ?: run {
                updateCaption("Look at the camera", ScannerBorderState.Active)
                return
            }
            val box = android.graphics.RectF(
                face.boundingBox.left * bitmap.width,
                face.boundingBox.top * bitmap.height,
                face.boundingBox.right * bitmap.width,
                face.boundingBox.bottom * bitmap.height
            )
            val area = box.width() * box.height() / (bitmap.width * bitmap.height)
            val passive = app.miniFasDetector.classify(bitmap, box)

            var probe: FaceEmbedding? = null
            runCatching {
                val crop = FacePreprocessor.makeSquareFaceBitmap(bitmap, face.boundingBox)
                val values = app.adaFaceRecognizer.embed(crop)
                probe = FaceEmbedding(FacePose.CENTER, values)
                if (enrolledCache.isEmpty()) {
                    enrolledCache = container.employeeRepository.getAllEnrolled()
                }
                if (enrolledCache.isNotEmpty()) {
                    val (match, _) = container.matchingService.match(probe!!, enrolledCache)
                    _lastSimilarity.value = match?.similarity
                    sessionIdentity = match?.employeeId ?: sessionIdentity
                    match?.employeeName?.let { name ->
                        updateCaption("$name — hold still", ScannerBorderState.Active)
                    }
                }
            }.onFailure {
                updateCaption(it.message ?: "Face processing failed", ScannerBorderState.Warning)
                return
            }

            val needsChallenge = passive.classification == Classification.UNCERTAIN &&
                !container.livenessManager.isComplete
            if (needsChallenge && container.livenessManager.challenge == null) {
                container.livenessManager.startRandomChallenge()
            }
            if (container.livenessManager.challenge != null && !container.livenessManager.isComplete) {
                container.livenessManager.update(face, area)?.let { challengeText ->
                    updateCaption(challengeText, ScannerBorderState.Active)
                }
                return
            }

            if (enrolledCache.isEmpty()) {
                enrolledCache = container.employeeRepository.getAllEnrolled()
            }
            val employees = enrolledCache
            if (employees.isEmpty()) {
                updateCaption("No enrolled employees — register under Employees", ScannerBorderState.Warning)
                return
            }

            val matchId = sessionIdentity
            val already = matchId?.let {
                container.attendanceRepository.hasRecordedToday(it, checkType)
            } ?: false

            when (val result = container.verificationEngine.evaluateFrame(
                VerificationFrameInput(
                    detectedFace = face,
                    probeEmbedding = probe,
                    passiveLiveness = passive,
                    enrolledEmployees = employees,
                    checkType = checkType,
                    alreadyRecordedToday = already,
                    activeChallengeIncomplete = needsChallenge && !container.livenessManager.isComplete,
                    sessionIdentityId = sessionIdentity
                )
            )) {
                is VerificationResult.Accepted -> {
                    val matched = employees.firstOrNull { it.id == result.employeeId } ?: return
                    container.locationGate.verifyAttendanceSite(matched).onFailure {
                        updateCaption(it.message ?: "Wrong job site", ScannerBorderState.Error)
                        return
                    }
                    container.clockGuard.verifyBeforePunch().onFailure {
                        updateCaption(it.message ?: "Clock check failed", ScannerBorderState.Error)
                        return
                    }
                    val record = container.attendanceRepository.record(
                        employeeId = result.employeeId,
                        employeeServerId = matched.serverId,
                        checkType = result.checkType,
                        confidence = result.confidence,
                        notes = "Matched ${result.matchedPose.raw}",
                        timestampMillis = container.clockGuard.preferredPunchTimestampMillis()
                    )
                    runCatching {
                        val crop = FacePreprocessor.makeSquareFaceBitmap(bitmap, face.boundingBox)
                        val jpeg = EnrollmentPhotoEncoder.encodeJpeg(crop)
                        AttendancePhotoStore.save(ConsTrakrApp.instance, record.id, jpeg)
                    }
                    container.clockGuard.recordSuccessfulPunch()
                    lastPunchMillis = System.currentTimeMillis()
                    container.syncCoordinator.syncPending()
                    finishWithAnnouncement(
                        message = ScannerPhrases.punchRecorded(result.employeeName, result.checkType),
                        border = ScannerBorderState.Success
                    )
                }
                is VerificationResult.Rejected -> {
                    if (result.reason.contains("already", ignoreCase = true)) {
                        finishWithAnnouncement(
                            message = result.reason,
                            border = ScannerBorderState.Warning,
                            dismissMs = 2_800L
                        )
                    } else {
                        val border = when {
                            result.reason.contains("Not recognized", ignoreCase = true) ->
                                ScannerBorderState.Error
                            else -> ScannerBorderState.Warning
                        }
                        updateCaption(result.reason, border)
                    }
                }
                is VerificationResult.NeedsAction -> {
                    updateCaption(result.message, ScannerBorderState.Active)
                }
            }
    }
}
