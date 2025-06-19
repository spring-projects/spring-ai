package org.springframework.ai.chat.model;

/**
 * Helper class to support Streaming function calling. It can merge the streamed
 * ChatCompletionChunk in case of function calling message.
 */
public interface StreamFunctionCallingHelper<T> {
	T merge(T previous, T current);

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call.
	 */
	boolean isStreamingToolFunctionCall(T chatCompletion);

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call and it is
	 * the last one.
	 */
	boolean isStreamingToolFunctionCallFinish(T chatCompletion);
}
