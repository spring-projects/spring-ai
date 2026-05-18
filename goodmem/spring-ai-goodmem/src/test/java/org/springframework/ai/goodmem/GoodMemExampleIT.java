/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.goodmem;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Runnable example showing how to drive {@link GoodMemTools} from a Spring AI
 * {@link ChatClient}. Three scenarios cover the integration's common usage patterns:
 *
 * <ol>
 * <li>A single agent that stores project facts and answers later questions from them.
 * Rotating the {@link ChatMemory#CONVERSATION_ID} forgets chat-history continuity while
 * keeping the GoodMem-backed memories available.
 * <li>Two cooperating agents over a shared space: a Scribe ingests notes via
 * {@code goodmem_create_memory} and an Analyst answers questions via
 * {@code goodmem_retrieve_memories}.
 * <li>An activity log with category metadata: a Tagger writes entries with
 * {@code metadataJson}, then a Release manager filters retrieval with
 * {@code metadataFilter}.
 * </ol>
 *
 * <p>
 * Skipped unless both {@code GOODMEM_API_KEY} and {@code OPENAI_API_KEY} are set.
 * Functional coverage of the underlying tool surface lives in
 * {@link GoodMemIntegrationIT}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "GOODMEM_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class GoodMemExampleIT {

	private static final String OPENAI_MODEL = "gpt-4o-mini";

	private static final Duration GOODMEM_TIMEOUT = Duration.ofSeconds(60);

	private static final long INDEXING_DELAY_MS = 5000L;

	private final GoodMemClient goodMem;

	private final GoodMemTools tools;

	private final OpenAiChatModel chatModel;

	private final String embedderId;

	private final String spaceId;

	private final String teamSpaceId;

	private final String taggedSpaceId;

	GoodMemExampleIT() {
		String baseUrl = System.getenv().getOrDefault("GOODMEM_BASE_URL", "https://localhost:8080");
		String apiKey = System.getenv("GOODMEM_API_KEY");
		boolean verifySsl = Boolean.parseBoolean(System.getenv().getOrDefault("GOODMEM_VERIFY_SSL", "false"));

		this.goodMem = GoodMemClient.builder()
			.baseUrl(baseUrl)
			.apiKey(apiKey)
			.verifySsl(verifySsl)
			.timeout(GOODMEM_TIMEOUT)
			.build();
		this.tools = new GoodMemTools(this.goodMem);

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.apiKey(System.getenv("OPENAI_API_KEY"))
			.model(OPENAI_MODEL)
			.build();
		this.chatModel = OpenAiChatModel.builder().options(options).build();

		this.embedderId = firstEmbedderId();
		this.spaceId = createSpace("spring-ai-goodmem-example");
		this.teamSpaceId = createSpace("spring-ai-goodmem-example-team");
		this.taggedSpaceId = createSpace("spring-ai-goodmem-example-tagged");
	}

	private String firstEmbedderId() {
		Map<String, Object> response = this.tools.listEmbedders();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> embedders = (List<Map<String, Object>>) response.get("embedders");
		if (embedders == null || embedders.isEmpty()) {
			throw new IllegalStateException("No embedders configured on the GoodMem server.");
		}
		return String.valueOf(embedders.get(0).get("embedderId"));
	}

	private String createSpace(String name) {
		Map<String, Object> res = this.tools.createSpace(name, this.embedderId, "recursive", 512, 50);
		return String.valueOf(res.get("spaceId"));
	}

	@AfterAll
	void cleanup() {
		for (String sid : List.of(this.spaceId, this.teamSpaceId, this.taggedSpaceId)) {
			try {
				this.goodMem.deleteSpace(sid);
			}
			catch (RuntimeException ignored) {
				// best-effort cleanup
			}
		}
	}

	@Test
	void scenario1_persistentProjectContextAcrossSessions() throws Exception {
		System.out.println();
		System.out.println("=== Scenario 1: Persistent project context across sessions ===");

		ChatMemory memory = MessageWindowChatMemory.builder().build();
		ChatClient agent = ChatClient.builder(this.chatModel).defaultSystem("""
				You are an engineering team assistant. Store project facts the user shares \
				in GoodMem space '%s' using goodmem_create_memory. Answer questions by calling \
				goodmem_retrieve_memories on that space. Do not answer from your conversational \
				memory.
				""".formatted(this.spaceId)).defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build()).build();

		String session1 = UUID.randomUUID().toString();
		for (String turn : List.of("I'm building a customer support assistant for our SaaS product.",
				"The team uses Python 3.12 with FastAPI and Postgres.",
				"For tests we use pytest with at least 80% coverage required.")) {
			System.out.println();
			System.out.println("User:  " + turn);
			String reply = agent.prompt()
				.user(turn)
				.tools(this.tools)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session1))
				.call()
				.content();
			System.out.println("Agent: " + reply);
		}

		// Wait for asynchronous indexing on the server before querying.
		Thread.sleep(INDEXING_DELAY_MS);

		// A fresh conversation id drops the prior chat history. Tools stay available, so
		// the agent must satisfy the question through GoodMem retrieval.
		String session2 = UUID.randomUUID().toString();
		String question = "Remind me what our coverage requirement is.";
		System.out.println();
		System.out.println("User:  " + question);
		String reply = agent.prompt()
			.user(question)
			.tools(this.tools)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session2))
			.call()
			.content();
		System.out.println("Agent: " + reply);
	}

	@Test
	void scenario2_twoAgentTeamKnowledgePipeline() throws Exception {
		System.out.println();
		System.out.println("=== Scenario 2: Two-agent team knowledge pipeline ===");

		ChatClient scribe = ChatClient.builder(this.chatModel).defaultSystem("""
				You are a team Scribe. Store each note verbatim in GoodMem space '%s' using \
				goodmem_create_memory. Confirm storage briefly.
				""".formatted(this.teamSpaceId)).build();
		for (String note : List.of("Q2 goal: reduce customer support response time to under 2 hours.",
				"Our main services are auth-service, billing-service, and notifications-service.",
				"Known issue: notifications-service drops messages during high load.",
				"Team retro: the CI pipeline is too slow; we should parallelize tests.")) {
			scribe.prompt().user(note).tools(this.tools).call().content();
		}
		Thread.sleep(INDEXING_DELAY_MS);

		ChatClient analyst = ChatClient.builder(this.chatModel).defaultSystem("""
				You are a team Analyst. Search GoodMem space '%s' using \
				goodmem_retrieve_memories to answer team questions.
				""".formatted(this.teamSpaceId)).build();
		String question = "What do we know about our services and current priorities?";
		System.out.println();
		System.out.println("User:  " + question);
		String reply = analyst.prompt().user(question).tools(this.tools).call().content();
		System.out.println("Agent: " + reply);
	}

	@Test
	void scenario3_structuredActivityLogAndMetadataFilter() throws Exception {
		System.out.println();
		System.out.println("=== Scenario 3: Structured team activity log ===");

		ChatClient tagger = ChatClient.builder(this.chatModel).defaultSystem("""
				You are a release tagger. For each entry the user gives you, call \
				goodmem_create_memory on space '%s' with metadataJson recording the entry's \
				category (one of: 'feat', 'fix', 'chore', 'docs'). Example metadataJson: \
				{"category": "feat"}.
				""".formatted(this.taggedSpaceId)).build();
		List<Map.Entry<String, String>> entries = List.of(
				Map.entry("Added user profile editing to the dashboard.", "feat"),
				Map.entry("Built the CSV export feature.", "feat"),
				Map.entry("Resolved slow login on the mobile app.", "fix"),
				Map.entry("Fixed crash when opening large attachments.", "fix"),
				Map.entry("Upgraded Python version across services.", "chore"),
				Map.entry("Updated the API reference for billing endpoints.", "docs"));
		for (Map.Entry<String, String> e : entries) {
			tagger.prompt()
				.user("Store: '" + e.getKey() + "' with category '" + e.getValue() + "'.")
				.tools(this.tools)
				.call()
				.content();
		}
		Thread.sleep(INDEXING_DELAY_MS);

		ChatClient releaseManager = ChatClient.builder(this.chatModel).defaultSystem("""
				You are a release manager. The team activity log lives in GoodMem space '%s'. \
				Each memory has a 'category' metadata field (one of 'feat', 'fix', 'chore', \
				'docs'). To answer category-specific questions, call goodmem_retrieve_memories \
				with spaceIds set to '%s' and metadataFilter set to \
				"CAST(val('$.category') AS TEXT) = 'feat'" (or whichever category is asked \
				about). Report each result by its chunkText.
				""".formatted(this.taggedSpaceId, this.taggedSpaceId)).build();
		String question = "Show me the new features we've shipped.";
		System.out.println();
		System.out.println("User:  " + question);
		String reply = releaseManager.prompt().user(question).tools(this.tools).call().content();
		System.out.println("Agent: " + reply);
	}

}
