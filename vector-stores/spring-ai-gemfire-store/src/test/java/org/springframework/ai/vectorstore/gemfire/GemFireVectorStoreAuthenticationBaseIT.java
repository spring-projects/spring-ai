/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.gemfire;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.vmware.gemfire.testcontainers.GemFireCluster;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

import static org.hamcrest.Matchers.hasSize;

/**
 * @author Geet Rawat
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Jason Huynh
 * @author Nabarun Nag
 * @since 1.0.0
 */
@Testcontainers
public abstract class GemFireVectorStoreAuthenticationBaseIT {

	static final String INDEX_NAME = "spring-ai-index1";

	static final int HTTP_SERVICE_PORT = 9090;

	static final int LOCATOR_COUNT = 1;

	static final int SERVER_COUNT = 1;

	static GemFireCluster gemFireCluster;

	final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(getTestApplicationClass());

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@AfterAll
	public static void stopGemFireCluster() {
		gemFireCluster.close();
	}

	@BeforeAll
	public static void startGemFireCluster() {
		Ports.Binding hostPort = Ports.Binding.bindPort(HTTP_SERVICE_PORT);
		ExposedPort exposedPort = new ExposedPort(HTTP_SERVICE_PORT);
		PortBinding mappedPort = new PortBinding(hostPort, exposedPort);
		gemFireCluster = new GemFireCluster(GemFireImage.DEFAULT_IMAGE, LOCATOR_COUNT, SERVER_COUNT);
		gemFireCluster.withConfiguration(GemFireCluster.SERVER_GLOB,
				container -> container.withExposedPorts(HTTP_SERVICE_PORT)
					.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(mappedPort)));
		gemFireCluster.withGemFireProperty(GemFireCluster.SERVER_GLOB, "http-service-port",
				Integer.toString(HTTP_SERVICE_PORT));
		gemFireCluster.withGemFireProperty(GemFireCluster.ALL_GLOB, "security-manager",
				"org.apache.geode.examples.SimpleSecurityManager");

		gemFireCluster.withGemFireProperty(GemFireCluster.ALL_GLOB, "security-username", "clusterManage");
		gemFireCluster.withGemFireProperty(GemFireCluster.ALL_GLOB, "security-password", "clusterManage");
		gemFireCluster.acceptLicense().start();

		System.setProperty("spring.data.gemfire.pool.locators",
				String.format("localhost[%d]", gemFireCluster.getLocatorPort()));
	}

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void authenticateOperations() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(this.documents);
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());
			Awaitility.await()
				.atMost(1, java.util.concurrent.TimeUnit.MINUTES)
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Great Depression").topK(3).build()), hasSize(0));
		});
	}

	abstract Class getTestApplicationClass();

}
