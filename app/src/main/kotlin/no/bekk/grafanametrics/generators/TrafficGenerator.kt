package no.bekk.grafanametrics.generators

import no.bekk.grafanametrics.shared.WorkloadPaths
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom

/**
 * Hvor mye trafikk appen sender til seg selv. Lavt nivå holder ressursbruken nede når
 * appen bare skal holde SLO-ene i gang, høyt nivå gir mer data raskere.
 */
enum class TrafficLevel(val requestsPerSecond: Int) {
    OFF(0),
    LOW(2),
    HIGH(5),
}

/**
 * Kaller appens egne endepunkter over HTTP, slik at Spring lager de ekte
 * `http_server_requests_seconds`-metrikkene som SLO-ene bygger på.
 */
@Component
class TrafficGenerator(
    @Value("\${traffic.level}") initialLevel: TrafficLevel,
    @Value("\${server.port:8080}") private val port: Int,
) : AutoCloseable {

    @Volatile
    var level: TrafficLevel = initialLevel

    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val client: RestClient = buildRestClient()

    @Scheduled(fixedRate = 1000)
    fun sendRequestsForThisSecond() {
        repeat(level.requestsPerSecond) {
            executor.execute {
                // Spre requestene tilfeldig utover sekundet i stedet for å sende alle samtidig.
                Thread.sleep(ThreadLocalRandom.current().nextLong(1000))
                callRandomEndpoint()
            }
        }
    }

    private fun callRandomEndpoint() {
        val path = WorkloadPaths.ALL.random()
        try {
            client.get().uri(path).exchange { _, response -> response.statusCode }
        } catch (e: Exception) {
            log.debug("Kall mot {} feilet: {}", path, e.message)
        }
    }

    override fun close() = executor.close()

    private fun buildRestClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(15))
        }
        log.info("Trafikkgenerator sender til port {} med nivå {}", port, level)
        return RestClient.builder()
            .baseUrl("http://localhost:$port")
            .requestFactory(requestFactory)
            .build()
    }
}