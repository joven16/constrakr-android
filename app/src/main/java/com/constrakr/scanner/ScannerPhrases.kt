package com.constrakr.scanner

import com.constrakr.domain.CheckType

object ScannerPhrases {
    const val READY = "Scanner ready. Look at the camera."

    fun punchRecorded(name: String, checkType: CheckType): String =
        "$name — ${checkType.displayName} recorded"

    fun punchAlready(name: String, checkType: CheckType): String =
        "$name already ${checkType.displayName.lowercase()} today"
}
