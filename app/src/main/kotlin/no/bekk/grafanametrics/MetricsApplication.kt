package no.bekk.grafanametrics

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MetricsApplication

fun main(args: Array<String>) {
    runApplication<MetricsApplication>(*args)
}
