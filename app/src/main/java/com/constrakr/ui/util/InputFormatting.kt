package com.constrakr.ui.util

import java.util.Locale

/** Title case for person/label fields, e.g. "juan dela cruz" → "Juan Dela Cruz". */
fun String.toTitleCaseWords(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.lowercase(Locale.getDefault()).replaceFirstChar { c ->
                c.titlecase(Locale.getDefault())
            }
        }
