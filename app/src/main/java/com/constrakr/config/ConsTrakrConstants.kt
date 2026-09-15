package com.constrakr.config

/**
 * Mirrors iOS [AppConstants] in ConsTrakr/Utilities/Constants.swift.
 * Do not change values without iOS parity review.
 */
object ConsTrakrConstants {
    const val APP_NAME = "ConsTrakr"
    const val API_BASE_URL = "https://ims.rentelloph.com"
    const val API_PATH_PREFIX = "/constrakr-api"

    // AdaFace cosine — same person often ~0.42–0.80; strangers usually << 0.35
    const val ADA_FACE_MATCH_THRESHOLD = 0.45f
    const val ADA_FACE_SOLO_GALLERY_THRESHOLD = 0.48f
    const val FACE_MATCH_THRESHOLD_HANDCRAFTED = 0.88f
    const val SOLO_GALLERY_MATCH_THRESHOLD_HANDCRAFTED = 0.90f

    const val MULTI_POSE_MEAN_SLACK = 0.16f
    const val FACE_MATCH_MARGIN = 0.06f
    const val MIN_FACE_CONFIDENCE = 0.55f
    const val POSE_HOLD_DURATION_MS = 600L
    const val SCANNER_COOLDOWN_MS = 3_000L
    const val UNKNOWN_PERSON_AUTO_CANCEL_MS = 3_000L
    const val SCANNER_RESULT_DISMISS_MS = 2_800L
    const val SCANNER_WARMUP_FRAMES = 3
    const val SCANNER_CONSENSUS_FRAMES = 2
    const val REGISTRATION_RESET_DELAY_MS = 2_500L
    const val MAX_CLOCK_DRIFT_SECONDS = 300L
    const val MAX_CLOCK_JUMP_TOLERANCE_SECONDS = 300L
    const val CLOCK_CHECKPOINT_MAX_AGE_SECONDS = 86_400L

    // AdaFace IR-18 — CoreMLFaceRecognizer.swift
    const val ADA_FACE_INPUT_SIZE = 112
    const val ADA_FACE_EMBEDDING_DIM = 512

    // MiniFASNetV2 — CoreMLAntiSpoof.swift
    const val MINIFAS_INPUT_SIZE = 80
    const val MINIFAS_CROP_SCALE = 2.7f
    const val MINIFAS_LIVE_THRESHOLD = 0.72f
    const val MINIFAS_REJECT_AFTER_STREAK = 3

    // FaceImagePreprocessor.swift
    const val MIN_FACE_RELATIVE_SIZE = 0.22f
    const val MIN_CROP_CONTRAST = 0.04f

    // LivenessChecker.swift
    const val LIVENESS_CLOSED_EAR = 0.16f
    const val LIVENESS_OPEN_EAR = 0.21f
    const val LIVENESS_YAW_TARGET = 0.16f
    const val LIVENESS_CLOSER_SCALE = 1.45f

    // PresentationSpoofDetector — scanner threshold
    const val PRESENTATION_SPOOF_REJECT_THRESHOLD = 0.58f

    // ML pipeline rates (Galaxy S8 target)
    const val FACE_DETECTION_TARGET_FPS = 12
    const val CAMERA_PREVIEW_TARGET_FPS = 30

    const val DEVICE_HEADER = "X-Device-Local-Id"
    const val ADMIN_CODE_DIGITS = 6
}
