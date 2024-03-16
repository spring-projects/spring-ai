/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public class MongoDBAtlasContainer extends GenericContainer<MongoDBAtlasContainer> {

	public MongoDBAtlasContainer() {
		super("mongodb/atlas:v1.15.1");
		withPrivilegedMode(true);
		withCommand("/bin/bash", "-c",
				"atlas deployments setup local-test --type local --port 27778 --bindIpAll --username root --password root --force && tail -f /dev/null");
		withExposedPorts(27778);
		waitingFor(Wait.forLogMessage(".*Deployment created!.*\\n", 1));
		withStartupTimeout(Duration.ofMinutes(5)).withReuse(true);
	}

	public String getConnectionString() {
		return String.format("mongodb://root:root@%s:%s/?directConnection=true", getHost(), getMappedPort(27778));
	}

}