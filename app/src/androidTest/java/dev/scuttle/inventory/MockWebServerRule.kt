package dev.scuttle.inventory

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource

/**
 * JUnit rule that starts MockWebServer before each test and shuts it down after.
 *
 * Two modes:
 * - **Route mode** (default): call [route] to register URL-path → body mappings.
 *   Each path match can be consumed once (queue-per-route) or configured to repeat.
 * - **Queue mode**: call [enqueue] to push ordered responses (used for simple tests).
 *
 * The server URL is written to [MockWebServerHolder] so TestNetworkModule picks it up.
 */
class MockWebServerRule : ExternalResource() {

    val server = MockWebServer()

    // path prefix → ordered list of responses to serve
    private val routes = mutableMapOf<String, ArrayDeque<MockResponse>>()
    // fallback FIFO queue (used when no route matches or when using queue mode)
    private val queue = ArrayDeque<MockResponse>()

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: "/"
            // Find the longest matching route prefix
            val key = routes.keys
                .filter { path.startsWith(it) }
                .maxByOrNull { it.length }
            if (key != null) {
                val responses = routes[key]!!
                if (responses.isNotEmpty()) {
                    return responses.removeFirst()
                }
            }
            // Fall back to FIFO queue
            if (queue.isNotEmpty()) return queue.removeFirst()
            return MockResponse().setResponseCode(500).setBody("""{"error":"No mock response for $path"}""")
        }
    }

    override fun before() {
        server.dispatcher = dispatcher
        server.start()
        MockWebServerHolder.url = server.url("/").toString()
    }

    override fun after() {
        routes.clear()
        queue.clear()
        server.shutdown()
    }

    /** Register a URL-path prefix → response mapping. Responses are served in order per path. */
    fun route(path: String, body: String, code: Int = 200) {
        routes.getOrPut(path) { ArrayDeque() }.addLast(
            MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    /** Push a response to the FIFO queue (used when path routing is not needed). */
    fun enqueue(body: String, code: Int = 200) {
        queue.addLast(
            MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    fun enqueueEmpty(code: Int = 204) {
        queue.addLast(MockResponse().setResponseCode(code))
    }
}
