package com.moonraker.android.data.settings

import com.moonraker.android.data.moonraker.WebcamEntry

fun WebcamEntry.orderKey(): String = uid.ifBlank { name }

fun List<WebcamEntry>.sortedByCameraOrder(order: List<String>): List<WebcamEntry> {
    if (order.isEmpty()) return this
    val index = order.withIndex().associate { it.value to it.index }
    return sortedWith(
        compareBy<WebcamEntry> { cam ->
            index[cam.orderKey()] ?: index[cam.name] ?: Int.MAX_VALUE
        }.thenBy { it.name },
    )
}

fun List<String>.moveItem(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}
