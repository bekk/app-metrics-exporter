package no.bekk.grafanametrics.endpoints.inbound

import no.bekk.grafanametrics.generators.TrafficGenerator
import no.bekk.grafanametrics.generators.TrafficLevel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Leser og endrer hvor mye trafikk appen sender til seg selv mens den kjører.
 */
@RestController
@RequestMapping("/api/traffic")
class TrafficLevelEndpoint(private val generator: TrafficGenerator) {

    data class TrafficRequest(val level: TrafficLevel)

    data class TrafficResponse(val level: TrafficLevel, val requestsPerSecond: Int)

    @GetMapping
    fun get() = TrafficResponse(generator.level, generator.level.requestsPerSecond)

    @PutMapping
    fun set(@RequestBody request: TrafficRequest): TrafficResponse {
        generator.level = request.level
        return get()
    }
}