package no.bekk.grafanametrics.shared

/**
 * Stiene til de simulerte endepunktene, brukt både av endepunktet og av trafikkgeneratoren.
 */
object WorkloadPaths {
    const val BASE = "/api/workload"
    const val SHORT_DELAY = "/delay/short"
    const val LONG_DELAY = "/delay/long"

    val ALL = listOf(BASE + SHORT_DELAY, BASE + LONG_DELAY)
}
