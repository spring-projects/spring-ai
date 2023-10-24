# SBERT, Local, Embedding Client

The `SbertEmbeddingClient` is a `EmbeddingClient` implementation that computes, locally, [sentence embeddings](https://www.sbert.net/examples/applications/computing-embeddings/README.html#sentence-embeddings-with-transformers) with [SBERT](https://www.sbert.net/) transformers.

It can use any [pre-trained](https://www.sbert.net/docs/pretrained_models.html) sbert model, serialized into [Open Neural Network Exchange (ONNX)](https://onnx.ai/) format.

The [Deep Java Library](https://djl.ai/) and the Microsoft [ONNX Java Runtime](https://onnxruntime.ai/docs/get-started/with-java.html) libraries are used to run the ONNX models and compute the embeddings.


## Serialize the Tokenizer and Model

To run things in Java, we need to serialize the SBERT Tokenizer and the Model into ONNX format.

We can use the [optimum-cli](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model#exporting-a-model-to-onnx-using-the-cli) to export the selected transformers into ONNX format.

For example run:

```
optimum-cli export onnx --model         sentence-transformers/all-MiniLM-L6-v2 all-MiniLM-L6-v2-onnx
```

It exports the [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) as ONNX into the `all-MiniLM-L6-v2-onnx` folder.

In the `all-MiniLM-L6-v2-onnx` you can find the `tokenizer.json` and the `model.onnx` files that the `EmbeddingClient` needs.

Use the `SbertEmbeddingClient#setTokenizerResource(uri)` and `SbertEmbeddingClient#setModelResource(uri)` to specify the desired transformer onnx model.

By default the [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) is configured (dimensions: 384, size: 80MB, avg. performance: 58.80, speed: 14200 sent/sec).







