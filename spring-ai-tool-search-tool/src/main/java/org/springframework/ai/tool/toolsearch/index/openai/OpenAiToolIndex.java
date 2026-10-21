package org.springframework.ai.tool.toolsearch.index.openai;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.ChatModel;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseToolSearchOutputItem;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolSearchTool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse.SearchMetadata;
import org.springframework.util.Assert;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI-based tool searcher that delegates tool matching to the OpenAI Responses API
 * tool-search capability.
 * <p>
 * Tools are stored per session and sent as function tool definitions when a search is
 * executed. The OpenAI model returns the function tools that best match the search query.
 * <p>
 * <a href="https://developers.openai.com/api/docs/guides/tools-tool-search"> OpenAI
 * Developers - Tool Search </a>
 *
 */
public class OpenAiToolIndex implements ToolIndex, Closeable {

	private static final Log logger = LogFactory.getLog(OpenAiToolIndex.class);

	private static final ToolSearchTool TOOL_SEARCH_TOOL = ToolSearchTool.builder()
		.execution(ToolSearchTool.Execution.SERVER)
		.build();

	private final OpenAIClient openAiClient;

	private final String model;

	/**
	 * Function tools indexed by session. OpenAI's tool-search API accepts function tool
	 * definitions, so non-function tool references are represented by their name and
	 * description only.
	 */
	private final Map<String, List<FunctionTool>> sessionTools = new ConcurrentHashMap<>();

	private OpenAiToolIndex(OpenAIClient openAiClient, String model) {
		this.openAiClient = openAiClient;
		this.model = model;
	}

	@Override
	public void indexTool(String sessionId, ToolReference toolReference) {
		this.indexTools(sessionId, List.of(toolReference));
	}

	@Override
	public void indexTools(String sessionId, List<ToolReference> toolReferences) {
		List<FunctionTool> sessionTools = this.sessionTools.getOrDefault(sessionId, List.of());
		List<FunctionTool> newSessionTools = new ArrayList<>(sessionTools);

		toolReferences.stream().map(this::toOpenAiFunctionTool).forEach(newSessionTools::add);

		this.sessionTools.put(sessionId, List.copyOf(newSessionTools));

	}

	@Override
	public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
		String query = toolSearchRequest.query();
		String sessionId = toolSearchRequest.sessionId();

		List<FunctionTool> sessionTools = this.sessionTools.getOrDefault(sessionId, List.of());
		if (sessionTools.isEmpty()) {
			return ToolSearchResponse.builder().build();
		}

		Response response = createToolSearchResponse(query, sessionTools);

		List<ToolReference> toolReferences = getToolSearchOutputTools(response).stream()
			.map(this::toToolReference)
			.toList();

		SearchMetadata searchMetadata = buildSearchMetadata(query, response);

		return ToolSearchResponse.builder()
			.toolReferences(toolReferences)
			.totalMatches(toolReferences.size())
			.searchMetadata(searchMetadata)
			.build();
	}

	@Override
	public void clearIndex(String sessionId) {
		this.sessionTools.remove(sessionId);
	}

	/**
	 * Creates a Responses API request that asks OpenAI to select matching tools for the
	 * query.
	 * @param query the search query
	 * @param tools the function tools available for the current session
	 * @return the OpenAI response containing tool-search output items
	 */
	private Response createToolSearchResponse(String query, List<FunctionTool> tools) {
		ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder()
			.model(this.model)
			.input(query)
			.parallelToolCalls(false)
			.addTool(TOOL_SEARCH_TOOL);
		tools.forEach(paramsBuilder::addTool);

		return this.openAiClient.responses().create(paramsBuilder.build());
	}

	/**
	 * Extracts function tools selected by OpenAI's tool-search output.
	 * @param response the OpenAI response to inspect
	 * @return selected function tools from tool-search output items
	 */
	private List<FunctionTool> getToolSearchOutputTools(Response response) {
		List<ResponseToolSearchOutputItem> toolSearchOutputItems = response.output()
			.stream()
			.filter(ResponseOutputItem::isToolSearchOutput)
			.map(ResponseOutputItem::asToolSearchOutput)
			.toList();

		return toolSearchOutputItems.stream()
			.flatMap(item -> item.tools().stream())
			.filter(Tool::isFunction)
			.map(Tool::asFunction)
			.toList();
	}

	/**
	 * Builds search metadata from the OpenAI response timestamps.
	 * @param query the original search query
	 * @param response the OpenAI response
	 * @return metadata describing the search execution
	 */
	private SearchMetadata buildSearchMetadata(String query, Response response) {
		SearchMetadata.Builder builder = SearchMetadata.builder()
			.searchType(this.getClass().getSimpleName())
			.query(query);

		response.completedAt()
			.map(completedAt -> completedAt * 1_000 - response.createdAt() * 1_000)
			.map(Double::longValue)
			.ifPresent(builder::searchTimeMs);

		return builder.build();
	}

	/**
	 * Converts a {@link ToolReference} into an OpenAI function tool definition.
	 * @param toolReference the indexed tool reference
	 * @return an OpenAI function tool for tool-search requests
	 */
	private FunctionTool toOpenAiFunctionTool(ToolReference toolReference) {
		return FunctionTool.builder()
			.name(toolReference.toolName())
			.description(toolReference.summary())
			// ToolReference contains searchable metadata, but not an input schema.
			.parameters(Optional.empty())
			// Strict tool validation is configured by ToolCallingOptions.
			.strict(false)
			.build();
	}

	/**
	 * Converts an OpenAI function tool returned by tool search into a
	 * {@link ToolReference}.
	 * @param functionTool the selected OpenAI function tool
	 * @return a tool reference for the search response
	 */
	private ToolReference toToolReference(FunctionTool functionTool) {
		return ToolReference.builder()
			.toolName(functionTool.name())
			.summary(functionTool.description().orElse(""))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public void close() {
		this.openAiClient.close();
	}

	/**
	 * Builder for {@link OpenAiToolIndex}.
	 */
	public static class Builder {

		@Nullable private OpenAIClient openAiClient;

		@Nullable private String model;

		/**
		 * Configure the OpenAI client used for tool-search requests.
		 * @param openAiClient the OpenAI client
		 * @return this builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Configure the OpenAI model used for tool-search requests.
		 * @param model the OpenAI model name
		 * @return this builder
		 */
		public Builder model(String model) {
			this.model = model;
			return this;
		}

		/**
		 * Build an {@link OpenAiToolIndex}.
		 * @return a configured OpenAI-backed tool index
		 */
		public OpenAiToolIndex build() {
			Assert.notNull(this.openAiClient, "openAiClient must not be null");
			Assert.notNull(this.model, "model must not be null");

			ChatModel chatModel = ChatModel.of(this.model);
			if (chatModel.value().ordinal() > ChatModel.GPT_5_4_MINI.value().ordinal()) {
				throw new OpenAIException("Only gpt-5.4 and later models support tool search.");
			}

			return new OpenAiToolIndex(this.openAiClient, this.model);
		}

	}

}
