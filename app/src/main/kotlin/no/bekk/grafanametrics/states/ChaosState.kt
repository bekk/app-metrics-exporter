package no.bekk.grafanametrics.states

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * En simulert hendelse som gir alle endepunktene ekstra feil og/eller latency fram til `until`.
 */
data class Chaos(
    val errorRate: Double,
    val extraLatencyMs: Long,
    val until: Instant,
)

@Component
class ChaosState(registry: MeterRegistry) {

    @Volatile
    private var current: Chaos? = null

    init {
        // Én enkel serie som gjør det lett å markere chaos-perioder i dashboards.
        Gauge.builder("chaos.active") { if (active() != null) 1.0 else 0.0 }
            .description("1 når en simulert hendelse pågår")
            .register(registry)
    }

    fun start(chaos: Chaos) {
        current = chaos
    }

    fun stop() {
        current = null
    }

    /** Chaos som pågår nå, eller null hvis ingen er startet eller den har gått ut på tid. */
    fun active(): Chaos? = current?.takeIf { Instant.now() < it.until }
}