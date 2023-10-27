# Local Embedding Client (SBERT)

The `SbertEmbeddingClient` is a `EmbeddingClient` implementation that computes, locally, [sentence embeddings](https://www.sbert.net/examples/applications/computing-embeddings/README.html#sentence-embeddings-with-transformers) with [SBERT](https://www.sbert.net/) transformers.

It uses [pre-trained](https://www.sbert.net/docs/pretrained_models.html) sbert models, serialized into the [Open Neural Network Exchange (ONNX)](https://onnx.ai/) format.

The [Deep Java Library](https://djl.ai/) and the Microsoft [ONNX Java Runtime](https://onnxruntime.ai/docs/get-started/with-java.html) libraries are applied to run the ONNX models and compute the embeddings.

## Serialize the Tokenizer and Model

To run things in Java, we need to serialize the SBERT Tokenizer and the Model into ONNX format.

### Serialize with optimum-cli

For this we can use the [optimum-cli](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model#exporting-a-model-to-onnx-using-the-cli) to export the selected transformers into an ONNX format.

For example the following snippet creates an python virtual environment, installs the required packages and runs the export:

```bash
python3 -m venv venv
source ./venv/bin/activate
(venv) pip install --upgrade pip
(venv) pip install optimum onnx onnxruntime
(venv) optimum-cli export onnx --model sentence-transformers/all-MiniLM-L6-v2 onnx-output
```

The `optimum-cli` command exports the [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) transformer into the `onnx-output` folder, including the `tokenizer.json` and `model.onnx` files required by the embedding client.

## Apply the ONNX model

Use the `setTokenizerResource(tokenizerJsonUri)` and `setModelResource(modelOnnxUri)` methods to set the URI locations of the exported `tokenizer.json` and `model.onnx` files.
The `classpath:`, `file:` or `https:` URI schemas are supported.

If no other model is explicitly set, the `SbertEmbeddingClient` defaults to [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) model:

|     |  |
| -------- | ------- |
| Dimensions  |384    |
| Avg. performance | 58.80     |
| Speed    | 14200 sentences/sec    |
| Size    | 80MB    |











