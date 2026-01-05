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
    companion object {
        private const val IMAGE_NAME = "termbase-test-es:9.0.0-nori"

        // 싱글톤 컨테이너 - 모든 테스트에서 공유 (Podman 호환성 및 병렬 실행 문제 해결)
        @JvmStatic
        val elasticsearchContainer: ElasticsearchContainer by lazy {
            ensureImageExists()
            ElasticsearchContainer(
                DockerImageName
                    .parse(IMAGE_NAME)
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"),
            ).withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
                .withStartupTimeout(Duration.ofMinutes(5))
                .withReuse(true) // 컨테이너 재사용
        }

        // 이미지가 존재하는지 확인 (Podman 호환성 문제 회피)
        private fun imageExists(): Boolean =
            try {
                val process = ProcessBuilder("docker", "images", "-q", IMAGE_NAME).start()
                val result =
                    process.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                process.waitFor()
                result.isNotEmpty()
            } catch (e: Exception) {
                false
            }

        // 이미지가 없을 때만 빌드
        private fun ensureImageExists() {
            if (!imageExists()) {
                val image =
                    ImageFromDockerfile(IMAGE_NAME, false)
                        .withDockerfileFromBuilder { builder ->
                            builder
                                .from("docker.elastic.co/elasticsearch/elasticsearch:9.0.0")
                                .run("bin/elasticsearch-plugin install analysis-nori")
                                .build()
                        }
                image.get()
            }
        }
    }

    @Bean
    @ServiceConnection
    fun elasticsearchContainer(): ElasticsearchContainer = Companion.elasticsearchContainer
}
