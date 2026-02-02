# ToolCallAdvisor Streaming Design Document

This document describes the design and implementation decisions for the streaming support in `ToolCallAdvisor`, particularly when used with external memory advisors like `MessageChatMemoryAdvisor`.

## Problem Statement

When using `ToolCallAdvisor` with `disableMemory()` and an external `MessageChatMemoryAdvisor`, the non-streaming (call) implementation works correctly, but the streaming implementation fails. The root cause is a fundamental mismatch between how the two implementations handle:

1. **Tool call detection timing**: Call waits for complete response; Stream processes individual chunks
2. **Memory update sequencing**: Call ensures sequential updates; Stream has race conditions
3. **Recursive iteration**: Call loops synchronously; Stream's reactive pipeline complicates recursion

## Analysis of the Original Streaming Issue

### Architecture Overview

The test setup uses:
1. **ToolCallAdvisor** with `disableMemory()` - handles the tool calling loop but delegates conversation history to an external advisor
2. **MessageChatMemoryAdvisor** - manages conversation history in `ChatMemory`

The advisors are ordered so `ToolCallAdvisor` processes the request first, then delegates to `MessageChatMemoryAdvisor` via `streamAdvisorChain.copy(this).nextStream()`.

### Why Call (Non-Streaming) Works

In `ToolCallAdvisor.adviseCall`, the synchronous flow is straightforward:

```java
do {
    // 1. Build request with current instructions
    // 2. Call chain (which invokes MessageChatMemoryAdvisor)
    chatClientResponse = callAdvisorChain.copy(this).nextCall(processedChatClientRequest);

    // 3. Check tool call on complete response
    isToolCall = chatResponse != null && chatResponse.hasToolCalls();

    if (isToolCall) {
        // 4. Execute tools
        // 5. Build next instructions
        instructions = this.doGetNextInstructionsForToolCall(...);
    }
} while (isToolCall);
```

The key is that **each iteration waits for a complete response** before checking for tool calls and iterating.

### The Streaming Problem (Original Implementation)

The original streaming implementation had a fundamental flaw:

```java
Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);

return new ChatClientMessageAggregator().aggregateChatClientResponse(responseFlux, aggregated -> {
    // This callback fires when aggregation completes (doOnComplete)
    System.out.println("Aggregated chunk: " + aggregated);
}).flatMap(chatClientResponse -> {
    // ⚠️ PROBLEM: This processes EACH individual streaming chunk!
    chatClientResponse = this.doAfterStream(chatClientResponse, streamAdvisorChain);

    ChatResponse chatResponse = chatClientResponse.chatResponse();
    boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();

    if (isToolCall) {
        // Recursive call triggered per-chunk
        return executeToolsAndRecurse(...);
    } else {
        return Flux.just(chatClientResponse);
    }
});
```

#### Issue 1: Tool Call Detection on Individual Chunks

The `flatMap` operator processes **each streaming chunk** and checks `hasToolCalls()` on each one:
- Early chunks likely have no tool call info (`isToolCall = false`) → passed through
- A later chunk might contain tool call info (`isToolCall = true`) → triggers recursion
- But the aggregated response (with complete tool call data) is only passed to the **callback**, not emitted in the flux

#### Issue 2: Memory Update Timing

`MessageChatMemoryAdvisor.adviseStream`:

```java
return Mono.just(chatClientRequest)
    .publishOn(scheduler)
    .map(request -> this.before(request, streamAdvisorChain))  // Adds user/tool message to memory
    .flatMapMany(streamAdvisorChain::nextStream)
    .transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
            response -> this.after(response, streamAdvisorChain)));  // Saves assistant response to memory
```

The `after()` method is invoked via `doOnComplete` in the aggregator - meaning it fires **after all chunks have been emitted**. But in `ToolCallAdvisor`, the `flatMap` processes chunks **as they arrive**.

This creates a race condition:
1. A streaming chunk with tool call info arrives
2. `ToolCallAdvisor` detects it and starts executing tools
3. `ToolCallAdvisor` makes a recursive call with the tool result
4. **But** `MessageChatMemoryAdvisor.after()` hasn't been called yet (waiting for stream completion)
5. Memory doesn't have the assistant's tool call message yet

#### Issue 3: Triple Aggregation

There are three levels of `ChatClientMessageAggregator`:
1. In `ToolCallAdvisor.internalStream`
2. In `DefaultAroundAdvisorChain.nextStream`
3. In `MessageChatMemoryAdvisor.adviseStream`

Each level has its own `doOnComplete` callback, and the timing of when these fire relative to the `flatMap` processing can be inconsistent.

### Comparison Table

| Aspect | Call (Works) | Stream (Failed) |
|--------|--------------|-----------------|
| Tool call detection | On complete response | On individual chunks |
| Memory `after()` timing | Before loop iteration | After all chunks (race condition) |
| Processing model | Synchronous loop | Reactive flatMap per chunk |

---

## Solution: Streaming with Deferred Recursion

### The Core Insight

During a tool calling conversation, there are two types of model responses:

| Response Type | User Cares About Streaming? | Action After Completion |
|---------------|----------------------------|------------------------|
| Tool call request | Not really (it's just tool metadata) | Execute tool, recurse |
| Final answer | Yes! (real-time content) | Return to user |

The problem is we don't know which type until we've seen the **complete** response.

### Approach: Don't Stream Intermediate Iterations

The chosen approach buffers chunks during intermediate iterations and only streams for the final answer:

- **Buffers** chunks during intermediate iterations (user doesn't see tool call requests)
- **Streams** chunks only for the final answer
- Simpler implementation and easier to maintain
- The final answer (what users care about) still streams in real-time
- Memory consistency is guaranteed

### Conceptual Flow

```
                                    ┌─────────────────────────────┐
                                    │  Collect chunks silently    │
                                    │  (buffer during iteration)  │
                                    └─────────────────────────────┘
                                                 │
                                                 ▼
                                    ┌─────────────────────────────┐
                                    │  Aggregate response         │
                                    │  (wait for completion)      │
                                    └─────────────────────────────┘
                                                 │
                                                 ▼
                                    ┌─────────────────────────────┐
                                    │  Check for tool calls       │
                                    └─────────────────────────────┘
                                                 │
                         ┌───────────────────────┴───────────────────────┐
                         │                                               │
                         ▼                                               ▼
              ┌─────────────────────┐                      ┌─────────────────────┐
              │  Tool call detected │                      │  No tool call       │
              │  Execute tools      │                      │  (Final answer)     │
              │  Recurse silently   │                      │  Emit all chunks    │
              └─────────────────────┘                      └─────────────────────┘
```

### Implementation Details

The key change is in the `internalStream` method:

```java
private Flux<ChatClientResponse> internalStream(StreamAdvisorChain streamAdvisorChain,
        ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy, List<Message> instructions) {

    return Flux.deferContextual(contextView -> {
        // Build request with current instructions
        var processedRequest = ChatClientRequest.builder()
            .prompt(new Prompt(instructions, optionsCopy))
            .context(originalRequest.context())
            .build();

        processedRequest = this.doBeforeStream(processedRequest, streamAdvisorChain);
        StreamAdvisorChain chainCopy = streamAdvisorChain.copy(this);
        final ChatClientRequest finalRequest = processedRequest;

        // Get the streaming response
        Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);

        // Holders for aggregated response and collected chunks
        AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference<>();
        AtomicReference<List<ChatClientResponse>> chunksRef = new AtomicReference<>(new ArrayList<>());

        // Collect all chunks and aggregate, then decide whether to recurse or emit
        return new ChatClientMessageAggregator()
            .aggregateChatClientResponse(responseFlux, aggregatedResponseRef::set)
            .doOnNext(chunk -> chunksRef.get().add(chunk))
            .ignoreElements()
            .cast(ChatClientResponse.class)
            .concatWith(Flux.defer(() -> processAggregatedResponse(
                aggregatedResponseRef.get(),
                chunksRef.get(),
                finalRequest,
                streamAdvisorChain,
                originalRequest,
                optionsCopy)));
    });
}
```

The `processAggregatedResponse` method handles the decision:

```java
private Flux<ChatClientResponse> processAggregatedResponse(ChatClientResponse aggregatedResponse,
        List<ChatClientResponse> chunks, ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain,
        ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy) {

    if (aggregatedResponse == null) {
        return Flux.fromIterable(chunks);
    }

    aggregatedResponse = this.doAfterStream(aggregatedResponse, streamAdvisorChain);

    ChatResponse chatResponse = aggregatedResponse.chatResponse();
    boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();

    if (isToolCall) {
        // Execute tool calls on bounded elastic scheduler
        // Don't emit intermediate chunks for tool call iterations
        Flux<ChatClientResponse> toolCallFlux = Flux.deferContextual(ctx -> {
            ToolExecutionResult toolExecutionResult = this.toolCallingManager
                .executeToolCalls(finalRequest.prompt(), chatResponse);

            if (toolExecutionResult.returnDirect()) {
                return Flux.just(/* direct response */);
            } else {
                // Recursive call with updated conversation history
                List<Message> nextInstructions = this.doGetNextInstructionsForToolCallStream(...);
                return this.internalStream(streamAdvisorChain, originalRequest, optionsCopy, nextInstructions);
            }
        });
        return toolCallFlux.subscribeOn(Schedulers.boundedElastic());
    } else {
        // Final answer - emit all collected chunks for streaming output
        return this.doFinalizeLoopStream(Flux.fromIterable(chunks), streamAdvisorChain);
    }
}
```

### How This Works

**For an intermediate iteration (tool call):**

```
Time ──────────────────────────────────────────────────────────────────►

Model emits:  [chunk1] [chunk2] [chunk3:tool_call] [complete]
                 │        │           │                │
                 ▼        ▼           ▼                │
Collect:      add      add         add               │
                                                       │
                                                       ▼
Aggregation:  ─────────────────────────────────► aggregate complete
                                                       │
                                                       ▼
                                                 detect tool call
                                                       │
                                                       ▼
                                                 execute tool
                                                       │
                                                       ▼
                                                 recurse ────► (next iteration)
                                                 (chunks NOT emitted)
```

**For the final iteration (answer):**

```
Time ──────────────────────────────────────────────────────────────────►

Model emits:  [chunk1] [chunk2] [chunk3] ... [chunkN] [complete]
                 │        │        │           │          │
                 ▼        ▼        ▼           ▼          │
Collect:      add      add      add         add         │
                                                          │
                                                          ▼
Aggregation:  ────────────────────────────────────► aggregate complete
                                                          │
                                                          ▼
                                                    no tool call
                                                          │
                                                          ▼
                                                    emit all chunks ──► User sees streaming
```

### Benefits

1. **Real-time streaming preserved**: User sees chunks as they arrive for the final answer
2. **Correct sequencing**: Recursion only happens after aggregation completes
3. **Memory consistency**: The `doAfterStream` (and thus memory `after()`) fires before recursion
4. **Clean output**: Users don't see intermediate tool call metadata streaming

### Trade-offs

| Aspect | Behavior |
|--------|----------|
| Intermediate tool call chunks | Buffered, not streamed to user |
| Memory update timing | Correct - happens before recursion |
| Latency | Minimal - only waits for aggregation before recursion decision |
| Final answer streaming | Full real-time streaming preserved |

---

## Summary of Changes

### 1. ToolCallAdvisor.java

**Added imports:**
- `java.util.ArrayList`
- `java.util.concurrent.atomic.AtomicReference`

**Rewrote `internalStream` method:**
- Now collects all streaming chunks into a list while aggregating
- Waits for aggregation to complete before checking for tool calls
- Uses `ignoreElements()` + `concatWith(Flux.defer(...))` pattern to ensure sequential processing

**Added new `processAggregatedResponse` method:**
- Extracted the tool call detection and recursion logic into a separate method
- Checks tool calls on the **aggregated** response (not individual chunks)
- If tool call detected: executes tools and recurses (intermediate chunks are NOT emitted)
- If no tool call (final answer): emits all collected chunks for streaming output

**Added new hook method `doFinalizeLoopStream`:**
- Allows subclasses to customize finalization behavior for streaming
- Consistent with the `doFinalizeLoop` method in the call API

### 2. ToolCallAdvisorTests.java

**Updated `createMockResponse` method:**
- Now creates real `AssistantMessage` objects with actual tool calls (instead of mocking `hasToolCalls()`)
- This is necessary because the aggregator creates new `ChatResponse` objects that don't inherit mock behavior
- Added proper context handling for the mock responses

---

## Limitation of "Don't Stream Intermediate Iterations" Approach

The initial implementation described above has a significant limitation: **it waits for the entire input stream to complete before emitting anything downstream**.

Looking at the implementation:

```java
return new ChatClientMessageAggregator()
    .aggregateChatClientResponse(responseFlux, aggregatedResponseRef::set)
    .doOnNext(chunk -> chunksRef.get().add(chunk))
    .ignoreElements()  // ⚠️ Waits for entire stream to complete!
    .cast(ChatClientResponse.class)
    .concatWith(Flux.defer(() -> processAggregatedResponse(...)));
```

This means:
- **Intermediate iterations (tool calls)**: Buffer, don't stream ✓ (intentional)
- **Final answer**: Also buffers everything, then replays all at once ✗ (loses streaming benefit)

For the final answer, `Flux.fromIterable(chunks)` replays all chunks at once - this is essentially **batch mode, not true streaming**.

---

## Follow-up: Parallel Streaming + Aggregation (Implemented)

To address the limitation above, a follow-up implementation uses `publish()` to multicast the stream, allowing true real-time streaming for ALL iterations.

### Approach

The parallel streaming approach:
1. **Streams chunks immediately** to the user in real-time
2. **Aggregates in parallel** to detect tool calls
3. **Recurses after streaming completes** if tool call detected

This provides true real-time streaming for ALL iterations, including intermediate tool call responses.

### Conceptual Flow

```
                                    ┌─────────────────────────────┐
                                    │  Stream chunks immediately  │
                                    │  (real-time to user)        │
                                    └─────────────────────────────┘
                                                 ▲
                                                 │ branch 1
                                                 │
Model Stream ──────► publish() ──────┼──────────────────────────────────►  concat()  ──► Final Output
                                                 │
                                                 │ branch 2
                                                 ▼
                                    ┌─────────────────────────────┐
                                    │  Aggregate silently         │
                                    │  On complete:               │
                                    │    - Check for tool calls   │
                                    │    - If tool call: recurse  │
                                    │      and concat results     │
                                    │    - If no tool call: done  │
                                    └─────────────────────────────┘
```

### Implementation Details

The updated `internalStream` method:

```java
private Flux<ChatClientResponse> internalStream(StreamAdvisorChain streamAdvisorChain,
        ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy, List<Message> instructions) {

    return Flux.deferContextual(contextView -> {
        // Build request with current instructions
        var processedRequest = ChatClientRequest.builder()
            .prompt(new Prompt(instructions, optionsCopy))
            .context(originalRequest.context())
            .build();

        processedRequest = this.doBeforeStream(processedRequest, streamAdvisorChain);
        StreamAdvisorChain chainCopy = streamAdvisorChain.copy(this);
        final ChatClientRequest finalRequest = processedRequest;

        // Get the streaming response
        Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);

        // Holder for aggregated response (set when aggregation completes)
        AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference<>();

        // Use publish() to multicast the stream: one branch streams immediately,
        // another aggregates to detect tool calls
        return responseFlux.publish(shared -> {
            // Branch 1: Stream chunks immediately for real-time streaming UX
            Flux<ChatClientResponse> streamingBranch = new ChatClientMessageAggregator()
                .aggregateChatClientResponse(shared, aggregatedResponseRef::set);

            // Branch 2: After streaming completes, check for tool calls and
            // potentially recurse.
            Flux<ChatClientResponse> recursionBranch = Flux
                .defer(() -> handleToolCallRecursion(aggregatedResponseRef.get(), finalRequest,
                    streamAdvisorChain, originalRequest, optionsCopy));

            // Emit all streaming chunks first, then append any recursive results
            return streamingBranch.concatWith(recursionBranch);
        });
    });
}
```

The `handleToolCallRecursion` method handles tool call detection and recursion:

```java
private Flux<ChatClientResponse> handleToolCallRecursion(ChatClientResponse aggregatedResponse,
        ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain,
        ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy) {

    if (aggregatedResponse == null) {
        return Flux.empty();
    }

    aggregatedResponse = this.doAfterStream(aggregatedResponse, streamAdvisorChain);

    ChatResponse chatResponse = aggregatedResponse.chatResponse();
    boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();

    if (!isToolCall) {
        // No tool call - streaming already happened, nothing more to emit
        return this.doFinalizeLoopStream(Flux.empty(), streamAdvisorChain);
    }

    // Execute tool calls and recurse
    Flux<ChatClientResponse> toolCallFlux = Flux.deferContextual(ctx -> {
        ToolExecutionResult toolExecutionResult = this.toolCallingManager
            .executeToolCalls(finalRequest.prompt(), chatResponse);

        if (toolExecutionResult.returnDirect()) {
            return Flux.just(/* direct response */);
        } else {
            // Recursive call with updated conversation history
            List<Message> nextInstructions = this.doGetNextInstructionsForToolCallStream(...);
            return this.internalStream(streamAdvisorChain, originalRequest, optionsCopy, nextInstructions);
        }
    });
    return toolCallFlux.subscribeOn(Schedulers.boundedElastic());
}
```

### How This Works

**For an intermediate iteration (tool call):**

```
Time ──────────────────────────────────────────────────────────────────►

Model emits:  [chunk1] [chunk2] [chunk3:tool_call] [complete]
                 │        │           │                │
                 ▼        ▼           ▼                │
Streaming:    emit     emit        emit               │  ◄── User sees chunks in real-time
                                                       │
                                                       ▼
Aggregation:  ─────────────────────────────────► aggregate complete
                                                       │
                                                       ▼
                                                 detect tool call
                                                       │
                                                       ▼
                                                 execute tool
                                                       │
                                                       ▼
                                                 recurse ────► (next iteration streams)
```

**For the final iteration (answer):**

```
Time ──────────────────────────────────────────────────────────────────►

Model emits:  [chunk1] [chunk2] [chunk3] ... [chunkN] [complete]
                 │        │        │           │          │
                 ▼        ▼        ▼           ▼          │
Streaming:    emit     emit     emit        emit         │  ◄── User sees chunks in real-time
                                                          │
                                                          ▼
Aggregation:  ────────────────────────────────────► aggregate complete
                                                          │
                                                          ▼
                                                    no tool call
                                                          │
                                                          ▼
                                                    emit nothing (done)
```

### Benefits of Parallel Streaming

1. **True real-time streaming**: User sees ALL chunks as they arrive, including intermediate iterations
2. **Correct sequencing**: Recursion only happens after the current iteration's stream completes
3. **Memory consistency**: The `doAfterStream` (and thus memory `after()`) fires before recursion
4. **Complete output**: Users see progress during tool execution (tool call responses stream too)
5. **Concatenated results**: Each iteration's chunks are followed by the next iteration's chunks

### Trade-offs

| Aspect | Behavior |
|--------|----------|
| Intermediate tool call chunks | Streamed to user in real-time |
| Memory update timing | Correct - happens after each iteration completes |
| Latency | Minimal - streaming happens immediately |
| Output volume | Higher - includes all iterations' chunks |

### Additional Test Changes

With the parallel streaming approach, tests were updated to reflect the new behavior:
- `testAdviseStreamWithSingleToolCallIteration`: Now expects 2 results (tool call response + final response)
- `testAdviseStreamWithReturnDirectToolExecution`: Now expects 2 results (tool call response + direct result)

---

## Comparison of Approaches

| Aspect | Original (Broken) | Buffer Intermediate | Parallel Streaming |
|--------|-------------------|--------------------|--------------------|
| Tool call detection | Per-chunk (wrong) | On aggregated response | On aggregated response |
| Intermediate streaming | Unpredictable | Buffered | Real-time ✓ |
| Final answer streaming | Broken | Replayed (batch) | Real-time ✓ |
| Memory consistency | Race conditions | Correct | Correct |
| Implementation complexity | Low | Medium | Higher |
| User experience | Broken | Acceptable | Best ✓ |

---

## Follow-up: Configurable Tool Call Streaming (Implemented)

While the parallel streaming approach provides the best user experience by streaming all chunks in real-time, some use cases may want to **filter out intermediate tool call responses** from the downstream stream. This is useful when:

1. **Clean output**: The downstream consumer only cares about the final answer, not intermediate tool call metadata
2. **Simplified handling**: Client code doesn't need to differentiate between tool call chunks and answer chunks
3. **Backwards compatibility**: Behavior more similar to the non-streaming (call) API

### Configuration Option

A new `streamToolCallResponses` configuration option was added to control this behavior:

```java
// Default: Only stream final answer chunks (intermediate tool calls filtered out)
ToolCallAdvisor advisor = ToolCallAdvisor.builder().build();

// Option 1: Explicitly disable (same as default)
ToolCallAdvisor advisor = ToolCallAdvisor.builder()
    .streamToolCallResponses(false)
    .build();

// Option 2: Convenience method to disable (same as default)
ToolCallAdvisor advisor = ToolCallAdvisor.builder()
    .suppressToolCallStreaming()
    .build();

// Option 3: Enable - stream all chunks including intermediate tool calls
ToolCallAdvisor advisor = ToolCallAdvisor.builder()
    .streamToolCallResponses(true)
    .build();
```

### Behavior Comparison

| Configuration | Intermediate Tool Call Chunks | Final Answer Chunks |
|--------------|------------------------------|---------------------|
| `streamToolCallResponses(false)` (default) | Filtered out (not emitted) | Streamed in real-time |
| `streamToolCallResponses(true)` | Streamed in real-time | Streamed in real-time |

### Implementation Details

The `internalStream` method now branches based on the configuration:

```java
private Flux<ChatClientResponse> internalStream(StreamAdvisorChain streamAdvisorChain,
        ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy, List<Message> instructions) {

    return Flux.deferContextual(contextView -> {
        // ... setup code ...

        if (this.streamToolCallResponses) {
            // Stream all chunks immediately (including tool call responses)
            return streamWithToolCallResponses(responseFlux, aggregatedResponseRef, finalRequest,
                    streamAdvisorChain, originalRequest, optionsCopy);
        }
        else {
            // Buffer chunks and only emit for final (non-tool-call) responses
            return streamWithoutToolCallResponses(responseFlux, aggregatedResponseRef, finalRequest,
                    streamAdvisorChain, originalRequest, optionsCopy);
        }
    });
}
```

**When `streamToolCallResponses = true` (default):**
- Uses `publish()` to multicast the stream
- Streaming branch emits chunks immediately
- Recursion branch handles tool calls after stream completes
- All iterations' chunks are visible to downstream consumers

**When `streamToolCallResponses = false`:**
- Collects all chunks into a buffer
- Waits for aggregation to complete
- If tool call detected: recurses without emitting buffered chunks
- If final answer: emits all buffered chunks
- Only the final answer's chunks are visible to downstream consumers

### Flow Diagram (With `suppressToolCallStreaming`)

```
Iteration 1 (Tool Call):
Model emits:  [chunk1] [chunk2] [chunk3:tool_call] [complete]
                 │        │           │                │
                 ▼        ▼           ▼                │
Buffer:       add      add         add               │
                                                       │
                                                       ▼
Aggregation:  ─────────────────────────────────► aggregate complete
                                                       │
                                                       ▼
                                                 detect tool call
                                                       │
                                                       ▼
                                                 execute tool
                                                       │
                                                       ▼
                                                 recurse (buffer discarded)
                                                       │
Downstream:                                      (nothing emitted)

Iteration 2 (Final Answer):
Model emits:  [chunk1] [chunk2] [chunk3] ... [chunkN] [complete]
                 │        │        │           │          │
                 ▼        ▼        ▼           ▼          │
Buffer:       add      add      add         add         │
                                                          │
                                                          ▼
Aggregation:  ────────────────────────────────────► aggregate complete
                                                          │
                                                          ▼
                                                    no tool call
                                                          │
                                                          ▼
                                                    emit all buffered chunks
                                                          │
Downstream:   ◄────────────────────────────────────────[chunk1][chunk2]...[chunkN]
```

### Use Cases

1. **API backend**: Use default (`streamToolCallResponses(false)`) when only the final answer matters
2. **Clean output**: Use default when client code doesn't need to differentiate between tool call chunks and answer chunks
3. **Chat UI with real-time feedback**: Use `streamToolCallResponses(true)` to show users that the AI is executing tools
4. **Testing/Debugging**: Use `streamToolCallResponses(true)` to see all intermediate responses
