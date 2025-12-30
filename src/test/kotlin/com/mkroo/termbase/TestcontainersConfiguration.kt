package com.mkroo.termbase

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun elasticsearchContainer(): ElasticsearchContainer =
        ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.2"),
        ).withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(3))
}
