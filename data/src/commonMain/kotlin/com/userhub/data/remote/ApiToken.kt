package com.userhub.data.remote

/**
 * Supplies the GoRest access token from a platform-specific, out-of-source location
 * (environment variable / build configuration). Returns an empty string when unset so the
 * app degrades to read-only rather than embedding a credential in the binary or the repository.
 */
expect fun provideApiToken(): String
