# ToolCallAdvisor Streaming Design Document

This document describes the design and implementation of streaming support in `ToolCallAdvisor`, particularly when used with external memory advisors like `MessageChatMemoryAdvisor`.

## Problem Statement

When using `ToolCallAdvisor` with `disableInternalConversationHistory()` and an external `MessageChatMemoryAdvisor`, the non-streaming (call) implementation works correctly, but the original streaming implementation failed due to:

1. **Tool call detection on individual chunks**: The original implementation checked `hasToolCalls()` on each streaming chunk instead of the complete aggregated response
2. **Race conditions with memory updates**: `MessageChatMemoryAdvisor.after()` fires via `doOnComplete` after all chunks are emitted, but tool call detection happened per-chunk, causing memory inconsistency
3. **Incorrect recursion timing**: Recursive tool call iterations started before the current stream completed

### Why Call (Non-Streaming) Works

In the synchronous call flow, each iteration waits for a **complete response** before checking for tool calls:

```java
do {
    chatClientResponse = callAdvisorChain.nextCall(request);
    isToolCall = chatResponse != null && chatResponse.hasToolCalls();
    if (isToolCall) {
        // Execute tools and prepare next iteration
    }
} while (isToolCall);
```

### The Streaming Challenge

Streaming responses arrive as individual chunks. We don't know if the response contains tool calls until we've aggregated the **complete** response, but we want to stream chunks in real-time.

---

## Solution: Parallel Streaming with Deferred Recursion

The solution uses `publish()` to multicast the stream, enabling parallel streaming and aggregation:

```
Model Stream ──► publish() ──┬──► streamingBranch ──► emit chunks immediately
                             │
                             └──► aggregation ──► detect tool calls ──► recurse if needed
```

### Implementation

The `internalStream` method handles each iteration:

```java
private Flux<ChatClientResponse> internalStream(StreamAdvisorChain streamAdvisorChain,
        ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy, List<Message> instructions) {

    return Flux.deferContextual(contextView -> {
        var processedRequest = ChatClientRequest.builder()
            .prompt(new Prompt(instructions, optionsCopy))
            .context(originalRequest.context())
            .build();

        processedRequest = this.doBeforeStream(processedRequest, streamAdvisorChain);
        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.copy(this).nextStream(processedRequest);
        AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference<>();

        return streamWithToolCallResponses(responseFlux, aggregatedResponseRef, processedRequest,
                streamAdvisorChain, originalRequest, optionsCopy);
    });
}
```

The `streamWithToolCallResponses` method uses `publish()` for parallel processing:

```java
private Flux<ChatClientResponse> streamWithToolCallResponses(Flux<ChatClientResponse> responseFlux,
        AtomicReference<ChatClientResponse> aggregatedResponseRef, ChatClientRequest finalRequest,
        StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest,
        ToolCallingChatOptions optionsCopy) {

    return responseFlux.publish(shared -> {
        // Branch 1: Stream chunks immediately for real-time UX
        Flux<ChatClientResponse> streamingBranch = new ChatClientMessageAggregator()
            .aggregateChatClientResponse(shared, aggregatedResponseRef::set);

        // Branch 2: After streaming completes, check for tool calls and recurse
        Flux<ChatClientResponse> recursionBranch = Flux
            .defer(() -> handleToolCallRecursion(aggregatedResponseRef.get(), finalRequest,
                streamAdvisorChain, originalRequest, optionsCopy));

        return streamingBranch.concatWith(recursionBranch);
    })
    .filter(ccr -> this.streamToolCallResponses
            || !(ccr.chatResponse() != null && ccr.chatResponse().hasToolCalls()));
}
```

### How It Works

**For a tool call iteration:**
```
Model emits:  [chunk1] [chunk2] [chunk3:tool_call] [complete]
                 │        │           │                │
Streaming:    emit     emit        emit               │  ◄── Real-time to downstream
                                                       │
Aggregation:  ─────────────────────────────────► complete
                                                       │
                                                       ▼
                                                 detect tool call → execute → recurse
```

**For the final answer:**
```
Model emits:  [chunk1] [chunk2] ... [chunkN] [complete]
                 │        │           │          │
Streaming:    emit     emit        emit         │  ◄── Real-time to downstream
                                                 │
Aggregation:  ───────────────────────────► complete
                                                 │
                                                 ▼
                                           no tool call → done
```

---

## Configuration: Filtering Tool Call Responses

The `streamToolCallResponses` option controls whether intermediate tool call responses are emitted downstream:

```java
// Default: Only stream final answer (tool call responses filtered out)
ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

// Stream all chunks including intermediate tool calls
ToolCallAdvisor advisor = ToolCallAdvisor.builder()
    .streamToolCallResponses(true)
    .build();
```

| Configuration | Intermediate Tool Calls | Final Answer |
|--------------|------------------------|--------------|
| `streamToolCallResponses(false)` (default) | Filtered out | Streamed |
| `streamToolCallResponses(true)` | Streamed | Streamed |

The filtering is implemented as a terminal filter on the stream:

```java
.filter(ccr -> this.streamToolCallResponses
        || !(ccr.chatResponse() != null && ccr.chatResponse().hasToolCalls()))
```

### Use Cases

- **API backend**: Use default to only receive the final answer
- **Chat UI with progress feedback**: Use `streamToolCallResponses(true)` to show tool execution in real-time
- **Debugging**: Use `streamToolCallResponses(true)` to see all intermediate responses

---

## Key Benefits

1. **Real-time streaming**: Chunks are emitted immediately as they arrive
2. **Correct tool call detection**: Based on aggregated response, not individual chunks
3. **Memory consistency**: Aggregation completes before recursion, ensuring proper sequencing
4. **Configurable output**: Filter intermediate tool calls based on use case
5. **Simple implementation**: Single code path with terminal filter
