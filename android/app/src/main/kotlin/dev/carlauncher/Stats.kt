package dev.carlauncher

import android.app.ActivityManager
import android.content.Context
import java.io.File

// Small live RAM + CPU readout (owner 2026-11-06). Read only while visible, ~every 2 s.
object Stats {
    data class Ram(val totalMb: Long, val availMb: Long) { val usedPct get() = ((totalMb - availMb) * 100 / totalMb).toInt() }

    fun ram(ctx: Context): Ram {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return Ram(mi.totalMem / 1048576, mi.availMem / 1048576)
    }

    private var lastIdle = 0L; private var lastTotal = 0L

    /**
     * System CPU %. Apps can't read /proc/stat on Android 8+ (SELinux) — try it, then fall back to the average
     * current/max core frequency as a load proxy. null = this firmware exposes neither (then the UI shows RAM only).
     */
    fun cpuPct(): Int? {
        runCatching {
            val p = File("/proc/stat").bufferedReader().use { it.readLine() }.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            val idle = p[3] + p.getOrElse(4) { 0 }; val total = p.sum()
            val d = total - lastTotal; val di = idle - lastIdle
            lastTotal = total; lastIdle = idle
            if (d > 0) return ((d - di) * 100 / d).toInt()
        }
        return runCatching {
            val cores = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) } ?: return null
            val ratios = cores.mapNotNull { c ->
                val cur = File(c, "cpufreq/scaling_cur_freq").readText().trim().toDoubleOrNull()
                val max = File(c, "cpufreq/cpuinfo_max_freq").readText().trim().toDoubleOrNull()
                if (cur != null && max != null && max > 0) cur / max else null
            }
            if (ratios.isEmpty()) null else (ratios.average() * 100).toInt()
        }.getOrNull()
    }
}
