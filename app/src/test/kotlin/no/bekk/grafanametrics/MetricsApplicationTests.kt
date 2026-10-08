package no.bekk.grafanametrics

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["traffic.level=OFF"])
class MetricsApplicationTests {

    @Test
    fun contextLoads() {
    }

}