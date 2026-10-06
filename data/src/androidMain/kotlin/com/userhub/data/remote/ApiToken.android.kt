package com.userhub.data.remote

actual fun provideApiToken(): String = System.getenv("GOREST_API_TOKEN").orEmpty()
