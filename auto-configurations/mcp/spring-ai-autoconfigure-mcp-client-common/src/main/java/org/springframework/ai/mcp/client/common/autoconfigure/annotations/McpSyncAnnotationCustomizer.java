/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.client.common.autoconfigure.annotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpClient.SyncSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.method.changed.prompt.SyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.SyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.SyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;

import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class McpSyncAnnotationCustomizer implements McpSyncClientCustomizer {

	private static final Logger logger = LoggerFactory.getLogger(McpSyncAnnotationCustomizer.class);

	private final List<SyncSamplingSpecification> syncSamplingSpecifications;

	private final List<SyncLoggingSpecification> syncLoggingSpecifications;

	private final List<SyncElicitationSpecification> syncElicitationSpecifications;

	private final List<SyncProgressSpecification> syncProgressSpecifications;

	private final List<SyncToolListChangedSpecification> syncToolListChangedSpecifications;

	private final List<SyncResourceListChangedSpecification> syncResourceListChangedSpecifications;

	private final List<SyncPromptListChangedSpecification> syncPromptListChangedSpecifications;

	// Tracking registered specifications per client
	private final Map<String, Boolean> clientElicitationSpecs = new ConcurrentHashMap<>();

	private final Map<String, Boolean> clientSamplingSpecs = new ConcurrentHashMap<>();

	public McpSyncAnnotationCustomizer(List<SyncSamplingSpecification> syncSamplingSpecifications,
			List<SyncLoggingSpecification> syncLoggingSpecifications,
			List<SyncElicitationSpecification> syncElicitationSpecifications,
			List<SyncProgressSpecification> syncProgressSpecifications,
			List<SyncToolListChangedSpecification> syncToolListChangedSpecifications,
			List<SyncResourceListChangedSpecification> syncResourceListChangedSpecifications,
			List<SyncPromptListChangedSpecification> syncPromptListChangedSpecifications) {

		this.syncSamplingSpecifications = syncSamplingSpecifications;
		this.syncLoggingSpecifications = syncLoggingSpecifications;
		this.syncElicitationSpecifications = syncElicitationSpecifications;
		this.syncProgressSpecifications = syncProgressSpecifications;
		this.syncToolListChangedSpecifications = syncToolListChangedSpecifications;
		this.syncResourceListChangedSpecifications = syncResourceListChangedSpecifications;
		this.syncPromptListChangedSpecifications = syncPromptListChangedSpecifications;
	}

	@Override
	public void customize(String name, SyncSpec clientSpec) {

		if (!CollectionUtils.isEmpty(this.syncElicitationSpecifications)) {
			this.syncElicitationSpecifications.forEach(elicitationSpec -> {
				Stream.of(elicitationSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {
						// Check if client already has an elicitation spec
						if (this.clientElicitationSpecs.containsKey(name)) {
							throw new IllegalArgumentException("Client '" + name
									+ "' already has an elicitationSpec registered. Only one elicitationSpec is allowed per client.");
						}

						this.clientElicitationSpecs.put(name, Boolean.TRUE);
						clientSpec.elicitation(elicitationSpec.elicitationHandler());

						logger.info("Registered elicitationSpec for client '{}'.", name);
					}
				});
			});
		}

		if (!CollectionUtils.isEmpty(this.syncSamplingSpecifications)) {
			this.syncSamplingSpecifications.forEach(samplingSpec -> {
				Stream.of(samplingSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {

						// Check if client already has a sampling spec
						if (this.clientSamplingSpecs.containsKey(name)) {
							throw new IllegalArgumentException("Client '" + name
									+ "' already has a samplingSpec registered. Only one samplingSpec is allowed per client.");
						}
						this.clientSamplingSpecs.put(name, Boolean.TRUE);

						clientSpec.sampling(samplingSpec.samplingHandler());

						logger.info("Registered samplingSpec for client '{}'.", name);
					}
				});
			});
		}

		if (!CollectionUtils.isEmpty(this.syncLoggingSpecifications)) {
			this.syncLoggingSpecifications.forEach(loggingSpec -> {
				Stream.of(loggingSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {
						clientSpec.loggingConsumer(loggingSpec.loggingHandler());
						logger.info("Registered loggingSpec for client '{}'.", name);
					}
				});
			});
		}

		if (!CollectionUtils.isEmpty(this.syncProgressSpecifications)) {
			this.syncProgressSpecifications.forEach(progressSpec -> {
				Stream.of(progressSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {
						clientSpec.progressConsumer(progressSpec.progressHandler());
						logger.info("Registered progressSpec for client '{}'.", name);
					}
				});
			});
		}

		if (!CollectionUtils.isEmpty(this.syncToolListChangedSpecifications)) {
			this.syncToolListChangedSpecifications.forEach(toolListChangedSpec -> {
				Stream.of(toolListChangedSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {
						clientSpec.toolsChangeConsumer(toolListChangedSpec.toolListChangeHandler());
						logger.info("Registered toolListChangedSpec for client '{}'.", name);
					}
				});
			});
		}

		if (!CollectionUtils.isEmpty(this.syncResourceListChangedSpecifications)) {
			this.syncResourceListChangedSpecifications.forEach(resourceListChangedSpec -> {
				Stream.of(resourceListChangedSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {
						clientSpec.resourcesChangeConsumer(resourceListChangedSpec.resourceListChangeHandler());
						logger.info("Registered resourceListChangedSpec for client '{}'.", name);
					}
				});
			});
		}

		if (!CollectionUtils.isEmpty(this.syncPromptListChangedSpecifications)) {
			this.syncPromptListChangedSpecifications.forEach(promptListChangedSpec -> {
				Stream.of(promptListChangedSpec.clients()).forEach(clientId -> {
					if (clientId.equalsIgnoreCase(name)) {
						clientSpec.promptsChangeConsumer(promptListChangedSpec.promptListChangeHandler());
						logger.info("Registered promptListChangedSpec for client '{}'.", name);
					}
				});
			});
		}
	}

}
