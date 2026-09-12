package com.ioscastaway.notificationbrain.platform

import android.content.Context
import android.util.Log
import com.ioscastaway.notificationbrain.brain.Policy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Where the mutable part of the app lives. Stage 1: one JSON file in app-private storage. */
interface PolicyStore {
    val policy: StateFlow<Policy>
    fun current(): Policy = policy.value
    fun save(policy: Policy)
}

class FilePolicyStore(context: Context) : PolicyStore {
    private val file = File(context.filesDir, "policy.json")
    private val state = MutableStateFlow(load())
    override val policy: StateFlow<Policy> get() = state

    private fun load(): Policy = runCatching {
        if (file.exists()) Policy.parse(file.readText()) else Policy.seed()
    }.getOrElse {
        Log.w(TAG, "policy.json unreadable, falling back to seed", it)
        Policy.seed()
    }

    @Synchronized
    override fun save(policy: Policy) {
        val tmp = File(file.parentFile, "policy.json.tmp")
        tmp.writeText(policy.encode())
        if (!tmp.renameTo(file)) file.writeText(policy.encode())
        state.value = policy
    }

    private companion object { const val TAG = "PolicyStore" }
}
