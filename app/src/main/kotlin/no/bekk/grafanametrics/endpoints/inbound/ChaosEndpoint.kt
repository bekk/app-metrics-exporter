package no.bekk.grafanametrics.endpoints.inbound

import no.bekk.grafanametrics.states.Chaos
import no.bekk.grafanametrics.states.ChaosState
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

/**
 * Starter og stopper simulerte hendelser, for å teste burn rate-alarmer i SLO-pluginen.
 */
@RestController
@RequestMapping("/api/chaos")
class ChaosEndpoint(private val state: ChaosState) {

    data class ChaosRequest(
        val errorRate: Double = 0.0,
        val extraLatencyMs: Long = 0,
        val minutes: Long = 10,
    )

    @GetMapping
    fun get(): ResponseEntity<Chaos> = state.active()?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PostMapping
    fun start(@RequestBody request: ChaosRequest): ResponseEntity<Any> {
        when {
            request.errorRate !in 0.0..1.0 -> return badRequest("errorRate må være mellom 0 og 1")
            request.extraLatencyMs < 0 -> return badRequest("extraLatencyMs kan ikke være negativ")
            request.minutes <= 0 -> return badRequest("minutes må være større enn 0")
        }
        val chaos = Chaos(
            errorRate = request.errorRate,
            extraLatencyMs = request.extraLatencyMs,
            until = Instant.now() + Duration.ofMinutes(request.minutes),
        )
        state.start(chaos)
        return ResponseEntity.ok(chaos)
    }

    @DeleteMapping
    fun stop(): ResponseEntity<Unit> {
        state.stop()
        return ResponseEntity.noContent().build()
    }

    private fun badRequest(message: String): ResponseEntity<Any> = ResponseEntity.badRequest().body(mapOf("error" to message))
}