/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.buildpack.platform.docker.transport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.buildpack.platform.docker.configuration.DockerConfiguration;
import org.springframework.boot.buildpack.platform.docker.ssl.SslContextFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RemoteHttpClientTransport}
 *
 * @author Scott Frederick
 * @author Phillip Webb
 */
class RemoteHttpClientTransportTests {

	private final Map<String, String> environment = new LinkedHashMap<>();

	private final DockerConfiguration dockerConfiguration = DockerConfiguration.withDefaults();

	@Test
	void createIfPossibleWhenDockerHostIsNotSetReturnsNull() {
		RemoteHttpClientTransport transport = RemoteHttpClientTransport.createIfPossible(this.environment::get,
				this.dockerConfiguration);
		assertThat(transport).isNull();
	}

	@Test
	void createIfPossibleWhenDockerHostIsFileReturnsNull(@TempDir Path tempDir) throws IOException {
		String dummySocketFilePath = Files.createTempFile(tempDir, "remote-transport", null).toAbsolutePath()
				.toString();
		this.environment.put("DOCKER_HOST", dummySocketFilePath);
		RemoteHttpClientTransport transport = RemoteHttpClientTransport.createIfPossible(this.environment::get,
				this.dockerConfiguration);
		assertThat(transport).isNull();
	}

	@Test
	void createIfPossibleWhenDockerHostIsAddressReturnsTransport() {
		this.environment.put("DOCKER_HOST", "tcp://192.168.1.2:2376");
		RemoteHttpClientTransport transport = RemoteHttpClientTransport.createIfPossible(this.environment::get,
				this.dockerConfiguration);
		assertThat(transport).isNotNull();
	}

	@Test
	void createIfPossibleWhenTlsVerifyWithMissingCertPathThrowsException() {
		this.environment.put("DOCKER_HOST", "tcp://192.168.1.2:2376");
		this.environment.put("DOCKER_TLS_VERIFY", "1");
		assertThatIllegalArgumentException().isThrownBy(
				() -> RemoteHttpClientTransport.createIfPossible(this.environment::get, this.dockerConfiguration))
				.withMessageContaining("DOCKER_CERT_PATH");
	}

	@Test
	void createIfPossibleWhenNoTlsVerifyUsesHttp() {
		this.environment.put("DOCKER_HOST", "tcp://192.168.1.2:2376");
		RemoteHttpClientTransport transport = RemoteHttpClientTransport.createIfPossible(this.environment::get,
				this.dockerConfiguration);
		assertThat(transport.getHost()).satisfies(hostOf("http", "192.168.1.2", 2376));
	}

	@Test
	void createIfPossibleWhenTlsVerifyUsesHttps() throws Exception {
		this.environment.put("DOCKER_HOST", "tcp://192.168.1.2:2376");
		this.environment.put("DOCKER_TLS_VERIFY", "1");
		this.environment.put("DOCKER_CERT_PATH", "/test-cert-path");
		SslContextFactory sslContextFactory = mock(SslContextFactory.class);
		given(sslContextFactory.forDirectory("/test-cert-path")).willReturn(SSLContext.getDefault());
		RemoteHttpClientTransport transport = RemoteHttpClientTransport.createIfPossible(this.environment::get,
				this.dockerConfiguration, sslContextFactory);
		assertThat(transport.getHost()).satisfies(hostOf("https", "192.168.1.2", 2376));
	}

	@Test
	void createIfPossibleWithDockerConfigurationUserAuthReturnsTransport() {
		this.environment.put("DOCKER_HOST", "tcp://192.168.1.2:2376");
		RemoteHttpClientTransport transport = RemoteHttpClientTransport.createIfPossible(this.environment::get,
				DockerConfiguration.withRegistryUserAuthentication("user", "secret", "http://docker.example.com",
						"docker@example.com"));
		assertThat(transport).isNotNull();
	}

	private Consumer<HttpHost> hostOf(String scheme, String hostName, int port) {
		return (host) -> {
			assertThat(host).isNotNull();
			assertThat(host.getSchemeName()).isEqualTo(scheme);
			assertThat(host.getHostName()).isEqualTo(hostName);
			assertThat(host.getPort()).isEqualTo(port);
		};
	}

}
