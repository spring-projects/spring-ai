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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.modelcontextprotocol.client.McpClient.SyncSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.method.changed.prompt.SyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.SyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.SyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpSyncAnnotationCustomizerTests {

	@Mock
	private SyncSpec syncSpec;

	private List<SyncSamplingSpecification> samplingSpecs;

	private List<SyncLoggingSpecification> loggingSpecs;

	private List<SyncElicitationSpecification> elicitationSpecs;

	private List<SyncProgressSpecification> progressSpecs;

	private List<SyncToolListChangedSpecification> toolListChangedSpecs;

	private List<SyncResourceListChangedSpecification> resourceListChangedSpecs;

	private List<SyncPromptListChangedSpecification> promptListChangedSpecs;

	@BeforeEach
	void setUp() {
		this.samplingSpecs = new ArrayList<>();
		this.loggingSpecs = new ArrayList<>();
		this.elicitationSpecs = new ArrayList<>();
		this.progressSpecs = new ArrayList<>();
		this.toolListChangedSpecs = new ArrayList<>();
		this.resourceListChangedSpecs = new ArrayList<>();
		this.promptListChangedSpecs = new ArrayList<>();
	}

	@Test
	void constructorShouldInitializeAllFields() {
		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		assertThat(customizer).isNotNull();
	}

	@Test
	void constructorShouldAcceptNullLists() {
		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(null, null, null, null, null, null,
				null);

		assertThat(customizer).isNotNull();
	}

	@Test
	void customizeShouldNotRegisterAnythingWhenAllListsAreEmpty() {
		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		customizer.customize("test-client", this.syncSpec);

		verifyNoInteractions(this.syncSpec);
	}

	@Test
	void customizeShouldNotRegisterElicitationSpecForNonMatchingClient() {
		SyncElicitationSpecification elicitationSpec = mock(SyncElicitationSpecification.class);
		when(elicitationSpec.clients()).thenReturn(new String[] { "other-client" });
		this.elicitationSpecs.add(elicitationSpec);

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		customizer.customize("test-client", this.syncSpec);

		verifyNoInteractions(this.syncSpec);
	}

	@Test
	void customizeShouldThrowExceptionWhenDuplicateElicitationSpecRegistered() {
		SyncElicitationSpecification elicitationSpec1 = mock(SyncElicitationSpecification.class);
		SyncElicitationSpecification elicitationSpec2 = mock(SyncElicitationSpecification.class);

		when(elicitationSpec1.clients()).thenReturn(new String[] { "test-client" });
		when(elicitationSpec1.elicitationHandler()).thenReturn(request -> null);
		when(elicitationSpec2.clients()).thenReturn(new String[] { "test-client" });
		// No need to stub elicitationSpec2.elicitationHandler() as exception is thrown
		// before it's accessed

		this.elicitationSpecs.addAll(Arrays.asList(elicitationSpec1, elicitationSpec2));

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		assertThatThrownBy(() -> customizer.customize("test-client", this.syncSpec))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Client 'test-client' already has an elicitationSpec registered");
	}

	@Test
	void customizeShouldThrowExceptionWhenDuplicateSamplingSpecRegistered() {
		SyncSamplingSpecification samplingSpec1 = mock(SyncSamplingSpecification.class);
		SyncSamplingSpecification samplingSpec2 = mock(SyncSamplingSpecification.class);

		when(samplingSpec1.clients()).thenReturn(new String[] { "test-client" });
		when(samplingSpec1.samplingHandler()).thenReturn(request -> null);
		when(samplingSpec2.clients()).thenReturn(new String[] { "test-client" });
		// No need to stub samplingSpec2.samplingHandler() as exception is thrown before
		// it's accessed

		this.samplingSpecs.addAll(Arrays.asList(samplingSpec1, samplingSpec2));

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		assertThatThrownBy(() -> customizer.customize("test-client", this.syncSpec))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Client 'test-client' already has a samplingSpec registered");
	}

	@Test
	void customizeShouldSkipSpecificationsWithNonMatchingClientIds() {
		// Setup specs with different client IDs
		SyncLoggingSpecification loggingSpec = mock(SyncLoggingSpecification.class);
		SyncProgressSpecification progressSpec = mock(SyncProgressSpecification.class);
		SyncElicitationSpecification elicitationSpec = mock(SyncElicitationSpecification.class);

		when(loggingSpec.clients()).thenReturn(new String[] { "other-client" });
		when(progressSpec.clients()).thenReturn(new String[] { "another-client" });
		when(elicitationSpec.clients()).thenReturn(new String[] { "different-client" });

		this.loggingSpecs.add(loggingSpec);
		this.progressSpecs.add(progressSpec);
		this.elicitationSpecs.add(elicitationSpec);

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		customizer.customize("target-client", this.syncSpec);

		// None of the specifications should be registered since client IDs don't match
		verifyNoInteractions(this.syncSpec);
	}

	@Test
	void customizeShouldAllowElicitationSpecForDifferentClients() {
		SyncElicitationSpecification elicitationSpec1 = mock(SyncElicitationSpecification.class);
		SyncElicitationSpecification elicitationSpec2 = mock(SyncElicitationSpecification.class);

		when(elicitationSpec1.clients()).thenReturn(new String[] { "client1" });
		when(elicitationSpec1.elicitationHandler()).thenReturn(request -> null);
		when(elicitationSpec2.clients()).thenReturn(new String[] { "client2" });
		when(elicitationSpec2.elicitationHandler()).thenReturn(request -> null);

		this.elicitationSpecs.addAll(Arrays.asList(elicitationSpec1, elicitationSpec2));

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// Should not throw exception since they are for different clients
		SyncSpec syncSpec1 = mock(SyncSpec.class);
		customizer.customize("client1", syncSpec1);

		SyncSpec syncSpec2 = mock(SyncSpec.class);
		customizer.customize("client2", syncSpec2);

		// No exception should be thrown, indicating successful registration for different
		// clients
	}

	@Test
	void customizeShouldAllowSamplingSpecForDifferentClients() {
		SyncSamplingSpecification samplingSpec1 = mock(SyncSamplingSpecification.class);
		SyncSamplingSpecification samplingSpec2 = mock(SyncSamplingSpecification.class);

		when(samplingSpec1.clients()).thenReturn(new String[] { "client1" });
		when(samplingSpec1.samplingHandler()).thenReturn(request -> null);
		when(samplingSpec2.clients()).thenReturn(new String[] { "client2" });
		when(samplingSpec2.samplingHandler()).thenReturn(request -> null);

		this.samplingSpecs.addAll(Arrays.asList(samplingSpec1, samplingSpec2));

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// Should not throw exception since they are for different clients
		SyncSpec syncSpec1 = mock(SyncSpec.class);
		customizer.customize("client1", syncSpec1);

		SyncSpec syncSpec2 = mock(SyncSpec.class);
		customizer.customize("client2", syncSpec2);

		// No exception should be thrown, indicating successful registration for different
		// clients
	}

	@Test
	void customizeShouldPreventMultipleElicitationCallsForSameClient() {
		SyncElicitationSpecification elicitationSpec = mock(SyncElicitationSpecification.class);
		when(elicitationSpec.clients()).thenReturn(new String[] { "test-client" });
		when(elicitationSpec.elicitationHandler()).thenReturn(request -> null);
		this.elicitationSpecs.add(elicitationSpec);

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// First call should succeed
		customizer.customize("test-client", this.syncSpec);

		// Second call should throw exception
		SyncSpec syncSpec2 = mock(SyncSpec.class);
		assertThatThrownBy(() -> customizer.customize("test-client", syncSpec2))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Client 'test-client' already has an elicitationSpec registered");
	}

	@Test
	void customizeShouldPreventMultipleSamplingCallsForSameClient() {
		SyncSamplingSpecification samplingSpec = mock(SyncSamplingSpecification.class);
		when(samplingSpec.clients()).thenReturn(new String[] { "test-client" });
		when(samplingSpec.samplingHandler()).thenReturn(request -> null);
		this.samplingSpecs.add(samplingSpec);

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// First call should succeed
		customizer.customize("test-client", this.syncSpec);

		// Second call should throw exception
		SyncSpec syncSpec2 = mock(SyncSpec.class);
		assertThatThrownBy(() -> customizer.customize("test-client", syncSpec2))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Client 'test-client' already has a samplingSpec registered");
	}

	@Test
	void customizeShouldPerformCaseInsensitiveClientIdMatching() {
		SyncElicitationSpecification elicitationSpec = mock(SyncElicitationSpecification.class);
		when(elicitationSpec.clients()).thenReturn(new String[] { "TEST-CLIENT" });
		when(elicitationSpec.elicitationHandler()).thenReturn(request -> null);
		this.elicitationSpecs.add(elicitationSpec);

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// Should register elicitation spec when client ID matches case-insensitively
		customizer.customize("test-client", this.syncSpec);

		// Verify that a subsequent call for the same client (case-insensitive) throws
		// exception
		SyncSpec syncSpec2 = mock(SyncSpec.class);
		assertThatThrownBy(() -> customizer.customize("test-client", syncSpec2))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Client 'test-client' already has an elicitationSpec registered");
	}

	@Test
	void customizeShouldHandleEmptyClientName() {
		SyncLoggingSpecification loggingSpec = mock(SyncLoggingSpecification.class);
		when(loggingSpec.clients()).thenReturn(new String[] { "" });
		when(loggingSpec.loggingHandler()).thenReturn(message -> {
		});
		this.loggingSpecs.add(loggingSpec);

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// Should not throw exception when customizing for empty client name
		customizer.customize("", this.syncSpec);

	}

	@Test
	void customizeShouldAllowMultipleLoggingSpecsForSameClient() {
		SyncLoggingSpecification loggingSpec1 = mock(SyncLoggingSpecification.class);
		SyncLoggingSpecification loggingSpec2 = mock(SyncLoggingSpecification.class);

		when(loggingSpec1.clients()).thenReturn(new String[] { "test-client" });
		when(loggingSpec1.loggingHandler()).thenReturn(message -> {
		});
		when(loggingSpec2.clients()).thenReturn(new String[] { "test-client" });
		when(loggingSpec2.loggingHandler()).thenReturn(message -> {
		});

		this.loggingSpecs.addAll(Arrays.asList(loggingSpec1, loggingSpec2));

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// Should not throw exception for multiple logging specs for same client
		customizer.customize("test-client", this.syncSpec);

	}

	@Test
	void customizeShouldAllowMultipleProgressSpecsForSameClient() {
		SyncProgressSpecification progressSpec1 = mock(SyncProgressSpecification.class);
		SyncProgressSpecification progressSpec2 = mock(SyncProgressSpecification.class);

		when(progressSpec1.clients()).thenReturn(new String[] { "test-client" });
		when(progressSpec1.progressHandler()).thenReturn(notification -> {
		});
		when(progressSpec2.clients()).thenReturn(new String[] { "test-client" });
		when(progressSpec2.progressHandler()).thenReturn(notification -> {
		});

		this.progressSpecs.addAll(Arrays.asList(progressSpec1, progressSpec2));

		McpSyncAnnotationCustomizer customizer = new McpSyncAnnotationCustomizer(this.samplingSpecs, this.loggingSpecs,
				this.elicitationSpecs, this.progressSpecs, this.toolListChangedSpecs, this.resourceListChangedSpecs,
				this.promptListChangedSpecs);

		// Should not throw exception for multiple progress specs for same client
		customizer.customize("test-client", this.syncSpec);
	}

}
