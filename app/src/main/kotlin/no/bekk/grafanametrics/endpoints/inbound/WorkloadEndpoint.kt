package no.bekk.grafanametrics.endpoints.inbound

import no.bekk.grafanametrics.shared.WorkloadPaths
import no.bekk.grafanametrics.stability.Chaos
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ThreadLocalRandom

/**
 * Endepunkter som later som de gjør arbeid, med ulik typisk latency og feilrate.
 * Hvert endepunkt har sin egen path, slik at `http_route` fra Beyla skiller dem
 */
@RestController
@RequestMapping(WorkloadPaths.BASE)
class WorkloadEndpoint(private val chaos: Chaos) {

    @GetMapping(WorkloadPaths.SHORT_DELAY)
    fun shortDelay() = simulateRequest(typicalLatencyMs = 250, errorRate = 0.01)

    @GetMapping(WorkloadPaths.LONG_DELAY)
    fun longDelay() = simulateRequest(typicalLatencyMs = 750, errorRate = 0.05)

    private fun simulateRequest(typicalLatencyMs: Long, errorRate: Double): ResponseEntity<Map<String, Any>> {
        val activeChaos = chaos.active()

        val latencyMs = randomLatencyMs(typicalLatencyMs) + (activeChaos?.extraLatencyMs ?: 0)
        Thread.sleep(latencyMs)

        val failedNormally = randomChance(errorRate)
        val failedFromChaos = activeChaos != null && randomChance(activeChaos.errorRate)

        return if (failedNormally || failedFromChaos) {
            ResponseEntity.internalServerError().body(mapOf("latencyMs" to latencyMs, "error" to "simulert feil"))
        } else {
            ResponseEntity.ok(mapOf("latencyMs" to latencyMs))
        }
    }

    /**
     * Varierer mellom halv og halvannen gang typisk latency. I tillegg blir en liten andel
     * requests mye tregere, slik at latency-SLO-ene ikke alltid er 100 %.
     */
    private fun randomLatencyMs(typicalLatencyMs: Long): Long {
        val variation = ThreadLocalRandom.current().nextDouble(0.5, 1.5)
        val outlierMultiplier = if (randomChance(OUTLIER_RATE)) OUTLIER_MULTIPLIER else 1.0
        return (typicalLatencyMs * variation * outlierMultiplier).toLong()
    }

    private fun randomChance(probability: Double) = ThreadLocalRandom.current().nextDouble() < probability

    companion object {
        private const val OUTLIER_RATE = 0.05
        private const val OUTLIER_MULTIPLIER = 4.0
    }
}