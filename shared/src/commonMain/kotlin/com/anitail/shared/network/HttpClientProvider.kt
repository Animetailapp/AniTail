package com.anitail.shared.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
