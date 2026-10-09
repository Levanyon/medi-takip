package com.levanyon.meditakip.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean

/** GTINs are strings: zero-padding EAN-13 to 14 preserves product identity. */
object Gtin {
    fun normalize(value: String): String? {
        if (value.length !in 13..14 || value.any { it !in '0'..'9' } || value.all { it == '0' }) return null
        val sum = value.dropLast(1).reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return value.padStart(14, '0').takeIf { (10 - sum % 10) % 10 == value.last() - '0' }
    }
}

/** Month precision stays month precision; month-end is only used for expired comparisons. */
data class PackageExpiry(val year: Int, val month: Int, val day: Int?) {
    val lastUsableDate: LocalDate get() = day?.let { LocalDate.of(year, month, it) } ?: YearMonth.of(year, month).atEndOfMonth()
    fun expired(today: LocalDate) = lastUsableDate < today
    override fun toString(): String = day?.let { LocalDate.of(year, month, it).toString() } ?: YearMonth.of(year, month).toString()
    companion object {
        fun parseGs1(value: String, today: LocalDate): PackageExpiry? = runCatching {
            require(value.length == 6 && value.all { it in '0'..'9' })
            val yy = value.take(2).toInt()
            // GS1's rolling century window: 49 years before through 50 years after the current year.
            val year = (today.year - 49..today.year + 50).single { Math.floorMod(it, 100) == yy }
            val month = value.substring(2, 4).toInt()
            val day = value.takeLast(2).toInt().takeUnless { it == 0 }
            PackageExpiry(year, month, day).also { it.lastUsableDate }
        }.getOrNull()
        fun parseStored(value: String): PackageExpiry? = runCatching {
            when (value.length) {
                7 -> YearMonth.parse(value).let { PackageExpiry(it.year, it.monthValue, null) }
                10 -> LocalDate.parse(value).let { PackageExpiry(it.year, it.monthValue, it.dayOfMonth) }
                else -> error("Invalid expiry")
            }
        }.getOrNull()?.takeIf { it.toString() == value }
    }
}

data class ScannedProduct(val gtin: String, val serial: String? = null, val batch: String? = null, val expiry: PackageExpiry? = null)

/** Strict AI subset. Never guesses an AI boundary inside a variable-length serial/lot. */
object MedicineBarcode {
    fun parse(raw: String, today: LocalDate = LocalDate.now()): ScannedProduct? = runCatching {
        require(raw.length in 1..256)
        Gtin.normalize(raw)?.let { return ScannedProduct(it) }
        var text = raw.removePrefix("]d2").removePrefix("\u001d")
        val fields = linkedMapOf<String, String>()
        val humanReadable = text.startsWith("(")
        while (text.isNotEmpty()) {
            val ai: String
            if (humanReadable) {
                require(text.length >= 4 && text[0] == '(' && text[3] == ')')
                ai = text.substring(1, 3); text = text.drop(4)
            } else { require(text.length >= 2); ai = text.take(2); text = text.drop(2) }
            require(ai in setOf("01", "21", "17", "10") && ai !in fields)
            val length = when (ai) { "01" -> 14; "17" -> 6; else -> null }
            val end = length ?: if (humanReadable) text.indexOf('(').takeIf { it >= 0 } ?: text.length
                else text.indexOf('\u001d').takeIf { it >= 0 } ?: text.length
            require(end <= text.length)
            val value = text.take(end)
            require(value.isNotEmpty())
            if (length == null) require(value.length <= 20 && value.all { it in '!'..'~' && it != '(' && it != ')' })
            fields[ai] = value
            text = text.drop(end)
            if (!humanReadable && text.startsWith('\u001d')) { text = text.drop(1); require(text.isNotEmpty()) }
        }
        val gtin = fields["01"]?.takeIf { it.length == 14 }?.let(Gtin::normalize) ?: error("Missing GTIN")
        val expiry = fields["17"]?.let { PackageExpiry.parseGs1(it, today) ?: error("Invalid expiry") }
        ScannedProduct(gtin, fields["21"], fields["10"], expiry)
    }.getOrNull()
}

/** One accepted decode per scanner session, even if asynchronous frames finish together. */
class ScanGate {
    private val accepted = AtomicBoolean(false)
    fun accept() = accepted.compareAndSet(false, true)
}

enum class CameraAccess { READY, RATIONALE, DENIED, SETTINGS, UNAVAILABLE }
object CameraPolicy {
    fun state(hardware: Boolean, granted: Boolean, asked: Boolean, canAskAgain: Boolean) = when {
        !hardware -> CameraAccess.UNAVAILABLE
        granted -> CameraAccess.READY
        !asked -> CameraAccess.RATIONALE
        canAskAgain -> CameraAccess.DENIED
        else -> CameraAccess.SETTINGS
    }
}
