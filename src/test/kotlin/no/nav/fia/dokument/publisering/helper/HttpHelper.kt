package no.nav.fia.dokument.publisering.helper

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import org.testcontainers.containers.GenericContainer

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
    followRedirects = true
}

internal suspend inline fun <reified T> GenericContainer<*>.performPut(
    url: String,
    body: T,
    crossinline config: HttpRequestBuilder.() -> Unit = {},
) = performRequest(url) {
    config()
    method = HttpMethod.Put
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(body)
}

internal suspend inline fun <reified T> GenericContainer<*>.performPost(
    url: String,
    body: T,
    crossinline config: HttpRequestBuilder.() -> Unit = {},
) = performRequest(url) {
    config()
    method = HttpMethod.Post
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(body)
}

internal suspend fun GenericContainer<*>.performGet(
    url: String,
    config: HttpRequestBuilder.() -> Unit = {},
) = performRequest(url) {
    config()
    method = HttpMethod.Get
}

private suspend fun GenericContainer<*>.performRequest(
    url: String,
    config: HttpRequestBuilder.() -> Unit = {},
) = httpClient.request {
    config()
    header(HttpHeaders.Accept, "application/json")
    url {
        protocol = URLProtocol.HTTP
        host = this@performRequest.host
        port = firstMappedPort
        path(url)
    }
}
