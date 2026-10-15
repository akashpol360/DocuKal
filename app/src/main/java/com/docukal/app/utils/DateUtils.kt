package com.docukal.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExpiryStatus { EXPIRED, EXPIRING_SOON, ACTIVE, NO_EXPIRY }

fun expiryStatus(expiry: Long?, warningDays: Int = 30, now: Long = System.currentTimeMillis()): ExpiryStatus {
    if (expiry == null) return ExpiryStatus.NO_EXPIRY
    val today = startOfDay(now)
    if (expiry < today) return ExpiryStatus.EXPIRED
    // warningDays <= 0 means "No reminder"; still flag documents expiring within 30 days.
    val warn = if (warningDays <= 0) 30 else warningDays
    return if (expiry <= today + warn * 86_400_000L) ExpiryStatus.EXPIRING_SOON else ExpiryStatus.ACTIVE
}

fun formatDate(ms: Long?): String =
    ms?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "—"

fun daysUntil(ms: Long?, now: Long = System.currentTimeMillis()): Long? =
    ms?.let { (startOfDay(it) - startOfDay(now)) / 86_400_000L }

fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
