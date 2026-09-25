# Spring AI GPULlama3 Core

Spring AI `ChatModel` adapter backed by the local GPULlama3 runtime.

## Limitations

- Inference is local and blocking. `call()` blocks the caller thread. `stream()` schedules the blocking work on Reactor `boundedElastic`, but requests are still serialized by a single inference lock because the underlying model state is not treated as concurrently reusable.
- `stopSequences`, `topK`, `frequencyPenalty`, and `presencePenalty` are accepted for Spring AI `ChatOptions` compatibility but ignored by GPULlama3. GPULlama3 uses model-defined stop tokens and the sampler currently exposes only temperature, top-p, and seed.
- Streaming partial chunks expose raw generated text. If the model emits `<think>...</think>`, those tags can appear in partial stream chunks. The final stream metadata chunk and `call()` response parse thinking text into assistant metadata under `thinking`.
- Native image support is not provided or tested. The runtime depends on GPULlama3, JDK preview/vector features, and optionally TornadoVM.
