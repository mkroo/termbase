package com.mkroo.termbase

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun elasticsearchContainer(): ElasticsearchContainer {
        val image =
            ImageFromDockerfile("termbase-test-es:9.0.0-nori", false)
                .withDockerfileFromBuilder { builder ->
                    builder
                        .from("docker.elastic.co/elasticsearch/elasticsearch:9.0.0")
                        .run("bin/elasticsearch-plugin install analysis-nori")
                        .build()
                }

        image.get()

        return ElasticsearchContainer(
            DockerImageName
                .parse("termbase-test-es:9.0.0-nori")
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"),
        ).withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(5))
    }
}
