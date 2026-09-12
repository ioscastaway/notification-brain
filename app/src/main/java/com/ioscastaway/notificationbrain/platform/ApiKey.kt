package com.ioscastaway.notificationbrain.platform

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.ioscastaway.notificationbrain.BuildConfig

/**
 * The Anthropic key comes from `local.properties` at build time and reaches the app through
 * [BuildConfig]. Nothing stores it on the device and nothing types it in. Right for a sideloaded
 * experiment; a shipping app would put the call behind a backend that holds the credential.
 */
object ApiKey {
    val isConfigured: Boolean get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()

    fun client(): AnthropicClient? =
        BuildConfig.ANTHROPIC_API_KEY.takeIf { it.isNotBlank() }
            ?.let { AnthropicOkHttpClient.builder().apiKey(it).build() }
}
