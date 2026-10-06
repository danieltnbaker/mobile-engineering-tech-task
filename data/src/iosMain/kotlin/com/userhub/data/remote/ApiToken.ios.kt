package com.userhub.data.remote

import platform.Foundation.NSProcessInfo

actual fun provideApiToken(): String =
    NSProcessInfo.processInfo.environment["GOREST_API_TOKEN"] as? String ?: ""
