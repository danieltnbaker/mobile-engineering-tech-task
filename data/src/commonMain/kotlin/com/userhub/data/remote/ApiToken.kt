package com.userhub.data.remote

/**
 * Supplies the GoRest access token.
 *
 * The value is injected at build time from an out-of-source location (local.properties / a Gradle
 * property / the GOREST_API_TOKEN env var) into the generated [BuildTokenConfig], so no credential is
 * committed and the same source feeds both the Android app and the iOS framework. When unset it is
 * empty, so the app degrades to read-only rather than embedding a credential.
 */
fun provideApiToken(): String = BuildTokenConfig.API_TOKEN
