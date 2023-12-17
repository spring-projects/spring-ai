# (WIP) Bedrock Titan Embedding

Use the [TitanEmbeddingBedrockApi.java](src/main/java/org/springframework/ai/bedrock/titan/api/TitanEmbeddingBedrockApi.java) Bedrock Embedding client to implement `EmbeddingClient`.

Consult the the existing Cohere embedding client implementation.
Mind that Titan doesn't support batch embedding. You have to either emulate it (could be very expensive) or throw a not supported exception.
