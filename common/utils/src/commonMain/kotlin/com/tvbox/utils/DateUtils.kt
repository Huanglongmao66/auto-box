package com.tvbox.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 跨平台日期时间工具类
 * 基于 kotlinx-datetime 实现全平台兼容
 */
object DateUtils {

    const val FORMAT_DATE = "yyyy-MM-dd"
    const val FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss"
    const val FORMAT_TIME = "HH:mm:ss"
    const val FORMAT_TIMESTAMP = "yyyy-MM-dd HH:mm:ss.SSS"

    /**
     * 获取当前时间戳（毫秒）
     */
    fun currentTimeMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }

    /**
     * 获取当前时间戳（秒）
     */
    fun currentTimeSeconds(): Long {
        return Clock.System.now().epochSeconds
    }

    /**
     * 获取当前日期
     */
    fun today(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    /**
     * 获取当前日期时间
     */
    fun now(): LocalDateTime {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    /**
     * 时间戳转日期字符串
     * @param timestampMs 毫秒时间戳
     * @param pattern 格式模板
     */
    fun formatTimestamp(timestampMs: Long, pattern: String = FORMAT_DATETIME): String {
        val instant = Instant.fromEpochMilliseconds(timestampMs)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return formatDateTime(dateTime, pattern)
    }

    /**
     * 格式化日期时间
     */
    fun formatDateTime(dateTime: LocalDateTime, pattern: String = FORMAT_DATETIME): String {
        return pattern
            .replace("yyyy", dateTime.year.toString().padStart(4, '0'))
            .replace("MM", dateTime.monthNumber.toString().padStart(2, '0'))
            .replace("dd", dateTime.dayOfMonth.toString().padStart(2, '0'))
            .replace("HH", dateTime.hour.toString().padStart(2, '0'))
            .replace("mm", dateTime.minute.toString().padStart(2, '0'))
            .replace("ss", dateTime.second.toString().padStart(2, '0'))
    }

    /**
     * 格式化时长（毫秒转可读字符串）
     * @param durationMs 毫秒时长
     * @return 如 "01:23:45" 或 "23:45"
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }

    /**
     * 相对时间描述
     * @param timestampMs 毫秒时间戳
     * @return 如 "刚刚"、"3分钟前"、"2小时前"、"昨天"、"3天前"
     */
    fun relativeTime(timestampMs: Long): String {
        val diff = currentTimeMillis() - timestampMs
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days == 1L -> "昨天"
            days < 30 -> "${days}天前"
            days < 365 -> "${days / 30}个月前"
            else -> "${days / 365}年前"
        }
    }

    /**
     * 判断是否为今天
     */
    fun isToday(timestampMs: Long): Boolean {
        val instant = Instant.fromEpochMilliseconds(timestampMs)
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return date == today()
    }
}
