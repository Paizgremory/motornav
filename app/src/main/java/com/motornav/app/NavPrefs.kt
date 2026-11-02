package com.motornav.app

import android.content.Context

/** v3.28: pengaturan navigasi pintar (disimpan di HP, SharedPreferences "motornav" yang sama dengan pengaturan lain). */
object NavPrefs {
    private const val PREF = "motornav"

    /** Preferensi pemilihan rute. */
    @Volatile var pref: Pref = Pref.BALANCED
    /** Hindari jalan tol dan feri (sepeda motor umumnya dilarang masuk tol). */
    @Volatile var avoidToll = true
    /** Beralih otomatis ke rute yang jauh lebih cepat (dicek tiap ±2,5 menit saat berkendara). */
    @Volatile var autoAlt = true
    /** Peringatan suara: GPS lemah, berlawanan arah, rute berisiko. */
    @Volatile var guardVoice = true

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        pref = Pref.of(p.getInt("rpref", 0))
        avoidToll = p.getBoolean("avoidtoll", true)
        autoAlt = p.getBoolean("autoalt", true)
        guardVoice = p.getBoolean("guardvoice", true)
    }

    private fun edit(ctx: Context, f: android.content.SharedPreferences.Editor.() -> Unit) {
        val e = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        e.f()
        e.apply()
    }

    fun cyclePref(ctx: Context): Pref {
        pref = Pref.of((pref.ordinal + 1) % Pref.values().size)
        edit(ctx) { putInt("rpref", pref.ordinal) }
        return pref
    }

    fun setAvoidToll(ctx: Context, on: Boolean) { avoidToll = on; edit(ctx) { putBoolean("avoidtoll", on) } }
    fun setAutoAlt(ctx: Context, on: Boolean) { autoAlt = on; edit(ctx) { putBoolean("autoalt", on) } }
    fun setGuardVoice(ctx: Context, on: Boolean) { guardVoice = on; edit(ctx) { putBoolean("guardvoice", on) } }
}
