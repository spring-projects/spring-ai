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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.method.changed.prompt.AsyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.AsyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.AsyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.AsyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.AsyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.AsyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.AsyncSamplingSpecification;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.util.CollectionUtils;

import io.modelcontextprotocol.client.McpClient.AsyncSpec;

/**
 * @author Christian Tzolov
 */
public class McpAsyncAnnotationCustomizer implements McpAsyncClientCustomizer {

	private static final Logger logger = LoggerFactory.getLogger(McpAsyncAnnotationCustomizer.class);

	private final List<AsyncSamplingSpecification> asyncSamplingSpecifications;

	private final List<AsyncLoggingSpecification> asyncLoggingSpecifications;

	private final List<AsyncElicitationSpecification> asyncElicitationSpecifications;

	private final List<AsyncProgressSpecification> asyncProgressSpecifications;

	private final List<AsyncToolListChangedSpecification> asyncToolListChangedSpecifications;

	private final List<AsyncResourceListChangedSpecification> asyncResourceListChangedSpecifications;

	private final List<AsyncPromptListChangedSpecification> asyncPromptListChangedSpecifications;

	// Tracking registered specifications per client
	private final Map<String, Boolean> clientElicitationSpecs = new ConcurrentHashMap<>();

	private final Map<String, Boolean> clientSamplingSpecs = new ConcurrentHashMap<>();

	public McpAsyncAnnotationCustomizer(List<AsyncSamplingSpecification> asyncSamplingSpecifications,
			List<AsyncLoggingSpecification> asyncLoggingSpecifications,
			List<AsyncElicitationSpecification> asyncElicitationSpecifications,
			List<AsyncProgressSpecification> asyncProgressSpecifications,
			List<AsyncToolListChangedSpecification> asyncToolListChangedSpecifications,
			List<AsyncResourceListChangedSpecification> asyncResourceListChangedSpecifications,
			List<AsyncPromptListChangedSpecification> asyncPromptListChangedSpecifications) {

		this.asyncSamplingSpecifications = asyncSamplingSpecifications;
		this.asyncLoggingSpecifications = asyncLoggingSpecifications;
		this.asyncElicitationSpecifications = asyncElicitationSpecifications;
		this.asyncProgressSpecifications = asyncProgressSpecifications;
		this.asyncToolListChangedSpecifications = asyncToolListChangedSpecifications;
		this.asyncResourceListChangedSpecifications = asyncResourceListChangedSpecifications;
		this.asyncPromptListChangedSpecifications = asyncPromptListChangedSpecifications;
	}

	@Override
	public void customize(String name, AsyncSpec clientSpec) {

		if (!CollectionUtils.isEmpty(asyncElicitationSpecifications)) {
			this.asyncElicitationSpecifications.forEach(elicitationSpec -> {
				if (elicitationSpec.clientId().equalsIgnoreCase(name)) {

					// Check if client already has an elicitation spec
					if (clientElicitationSpecs.containsKey(name)) {
						throw new IllegalArgumentException("Client '" + name
								+ "' already has an elicitationSpec registered. Only one elicitationSpec is allowed per client.");
					}

					clientElicitationSpecs.put(name, Boolean.TRUE);
					clientSpec.elicitation(elicitationSpec.elicitationHandler());

					logger.info("Registered elicitationSpec for client '{}'.", name);

				}
			});
		}

		if (!CollectionUtils.isEmpty(asyncSamplingSpecifications)) {
			this.asyncSamplingSpecifications.forEach(samplingSpec -> {
				if (samplingSpec.clientId().equalsIgnoreCase(name)) {

					// Check if client already has a sampling spec
					if (clientSamplingSpecs.containsKey(name)) {
						throw new IllegalArgumentException("Client '" + name
								+ "' already has a samplingSpec registered. Only one samplingSpec is allowed per client.");
					}
					clientSamplingSpecs.put(name, Boolean.TRUE);

					clientSpec.sampling(samplingSpec.samplingHandler());

					logger.info("Registered samplingSpec for client '{}'.", name);
				}
			});
		}

		if (!CollectionUtils.isEmpty(asyncLoggingSpecifications)) {
			this.asyncLoggingSpecifications.forEach(loggingSpec -> {
				if (loggingSpec.clientId().equalsIgnoreCase(name)) {
					clientSpec.loggingConsumer(loggingSpec.loggingHandler());
					logger.info("Registered loggingSpec for client '{}'.", name);
				}
			});
		}

		if (!CollectionUtils.isEmpty(asyncProgressSpecifications)) {
			this.asyncProgressSpecifications.forEach(progressSpec -> {
				if (progressSpec.clientId().equalsIgnoreCase(name)) {
					clientSpec.progressConsumer(progressSpec.progressHandler());
					logger.info("Registered progressSpec for client '{}'.", name);
				}
			});
		}

		if (!CollectionUtils.isEmpty(asyncToolListChangedSpecifications)) {
			this.asyncToolListChangedSpecifications.forEach(toolListChangedSpec -> {
				if (toolListChangedSpec.clientId().equalsIgnoreCase(name)) {
					clientSpec.toolsChangeConsumer(toolListChangedSpec.toolListChangeHandler());
					logger.info("Registered toolListChangedSpec for client '{}'.", name);
				}
			});
		}

		if (!CollectionUtils.isEmpty(asyncResourceListChangedSpecifications)) {
			this.asyncResourceListChangedSpecifications.forEach(resourceListChangedSpec -> {
				if (resourceListChangedSpec.clientId().equalsIgnoreCase(name)) {
					clientSpec.resourcesChangeConsumer(resourceListChangedSpec.resourceListChangeHandler());
					logger.info("Registered resourceListChangedSpec for client '{}'.", name);
				}
			});
		}

		if (!CollectionUtils.isEmpty(asyncPromptListChangedSpecifications)) {
			this.asyncPromptListChangedSpecifications.forEach(promptListChangedSpec -> {
				if (promptListChangedSpec.clientId().equalsIgnoreCase(name)) {
					clientSpec.promptsChangeConsumer(promptListChangedSpec.promptListChangeHandler());
					logger.info("Registered promptListChangedSpec for client '{}'.", name);
				}
			});
		}
	}

}
