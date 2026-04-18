package com.vsoininen.b6companion.data

import android.content.Context

class CapacityPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCapacityMah(): Int = prefs.getInt(KEY_CAPACITY_MAH, DEFAULT_CAPACITY_MAH)

    fun setCapacityMah(capacityMah: Int) {
        prefs.edit().putInt(KEY_CAPACITY_MAH, capacityMah).apply()
    }

    companion object {
        private const val PREFS_NAME = "b6_companion_prefs"
        private const val KEY_CAPACITY_MAH = "battery_capacity_mah"
        const val DEFAULT_CAPACITY_MAH = 2200
    }
}
