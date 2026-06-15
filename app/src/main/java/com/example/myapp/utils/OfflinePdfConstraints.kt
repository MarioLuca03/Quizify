package com.example.myapp.utils

object OfflinePdfConstraints {

    fun effectivePageRange(totalPages: Int, useFull: Boolean, from: Int, to: Int): IntRange? {
        if (useFull || totalPages <= 0) return null
        val a = from.coerceIn(1, totalPages)
        val b = to.coerceIn(1, totalPages)
        return if (a <= b) a..b else b..a
    }

    /**
     * Offline: utilizatorul poate alege orice interval sau tot PDF-ul;
     * aplicatia alege automat cele [maxPages] pagini cele mai relevante din domeniu.
     */
    fun enforcePageWindow(
        isOffline: Boolean,
        totalPages: Int,
        rangeFrom: Int,
        rangeTo: Int,
        onUpdate: (from: Int, to: Int) -> Unit
    ) {
        if (!isOffline) return
        val from = rangeFrom.coerceIn(1, totalPages)
        val to = rangeTo.coerceIn(1, totalPages)
        onUpdate(from, if (from <= to) to else from)
    }

    fun validateBeforeGenerate(
        isOffline: Boolean,
        totalPages: Int,
        useFull: Boolean,
        from: Int,
        to: Int
    ): String? {
        if (!isOffline) return null
        if (totalPages <= 0) return "PDF-ul nu are pagini."
        if (!useFull) {
            val range = effectivePageRange(totalPages, false, from, to) ?: return null
            if (range.first < 1 || range.last > totalPages) {
                return "Interval de pagini invalid."
            }
        }
        return null
    }

    fun pageSpan(totalPages: Int, from: Int, to: Int): Int {
        val a = from.coerceIn(1, totalPages)
        val b = to.coerceIn(1, totalPages)
        return if (a <= b) b - a + 1 else a - b + 1
    }
}
