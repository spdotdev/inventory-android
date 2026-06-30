package dev.scuttle.inventory

/**
 * Singleton URL shared between MockWebServer (started before Hilt) and
 * TestNetworkModule (which reads it at graph-construction time).
 */
object MockWebServerHolder {
    var url: String = "http://localhost/"
}
