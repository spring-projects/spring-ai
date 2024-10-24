/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.transformers.samples;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import org.springframework.core.io.DefaultResourceLoader;

// https://www.sbert.net/examples/applications/computing-embeddings/README.html#sentence-embeddings-with-transformers

public class ONNXSample {

	public static NDArray meanPooling(NDArray tokenEmbeddings, NDArray attentionMask) {

		NDArray attentionMaskExpanded = attentionMask.expandDims(-1)
			.broadcast(tokenEmbeddings.getShape())
			.toType(DataType.FLOAT32, false);

		// Multiply token embeddings with expanded attention mask
		NDArray weightedEmbeddings = tokenEmbeddings.mul(attentionMaskExpanded);

		// Sum along the appropriate axis
		NDArray sumEmbeddings = weightedEmbeddings.sum(new int[] { 1 });

		// Clamp the attention mask sum to avoid division by zero
		NDArray sumMask = attentionMaskExpanded.sum(new int[] { 1 }).clip(1e-9f, Float.MAX_VALUE);

		// Divide sum embeddings by sum mask
		return sumEmbeddings.div(sumMask);
	}

	public static void main(String[] args) throws Exception {
		String TOKENIZER_URI = "classpath:/onnx/tokenizer.json";
		String MODEL_URI = "classpath:/onnx/generative.onnx";

		var tokenizerResource = new DefaultResourceLoader().getResource(TOKENIZER_URI);
		var modelResource = new DefaultResourceLoader().getResource(MODEL_URI);

		String[] sentences = new String[] { "Hello world" };

		// https://docs.djl.ai/extensions/tokenizers/index.html
		HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(tokenizerResource.getInputStream(), Map.of());
		Encoding[] encodings = tokenizer.batchEncode(sentences);

		long[][] input_ids0 = new long[encodings.length][];
		long[][] attention_mask0 = new long[encodings.length][];
		long[][] token_type_ids0 = new long[encodings.length][];

		for (int i = 0; i < encodings.length; i++) {
			input_ids0[i] = encodings[i].getIds();
			attention_mask0[i] = encodings[i].getAttentionMask();
			token_type_ids0[i] = encodings[i].getTypeIds();
		}

		// https://onnxruntime.ai/docs/get-started/with-java.html
		OrtEnvironment environment = OrtEnvironment.getEnvironment();
		OrtSession session = environment.createSession(modelResource.getContentAsByteArray());

		OnnxTensor inputIds = OnnxTensor.createTensor(environment, input_ids0);
		OnnxTensor attentionMask = OnnxTensor.createTensor(environment, attention_mask0);
		OnnxTensor tokenTypeIds = OnnxTensor.createTensor(environment, token_type_ids0);

		Map<String, OnnxTensor> inputs = new HashMap<>();
		inputs.put("input_ids", inputIds);
		inputs.put("attention_mask", attentionMask);
		inputs.put("token_type_ids", tokenTypeIds);

		try (OrtSession.Result results = session.run(inputs)) {

			OnnxValue lastHiddenState = results.get(0);

			float[][][] tokenEmbeddings = (float[][][]) lastHiddenState.getValue();

			System.out.println(tokenEmbeddings[0][0][0]);
			System.out.println(tokenEmbeddings[0][1][0]);
			System.out.println(tokenEmbeddings[0][2][0]);
			System.out.println(tokenEmbeddings[0][3][0]);

			try (NDManager manager = NDManager.newBaseManager()) {
				NDArray ndTokenEmbeddings = create(tokenEmbeddings, manager);
				NDArray ndAttentionMask = manager.create(attention_mask0);
				System.out.println(ndTokenEmbeddings);

				var embedding = meanPooling(ndTokenEmbeddings, ndAttentionMask);
				System.out.println(embedding);
			}

		}
	}

	public static NDArray create(float[][][] data, NDManager manager) {
		FloatBuffer buffer = FloatBuffer.allocate(data.length * data[0].length * data[0][0].length);
		for (float[][] data2 : data) {
			for (float[] d : data2) {
				buffer.put(d);
			}
		}
		buffer.rewind();
		return manager.create(buffer, new Shape(data.length, data[0].length, data[0][0].length));
	}

}
