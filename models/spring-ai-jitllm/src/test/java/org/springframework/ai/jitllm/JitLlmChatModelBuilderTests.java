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

package org.springframework.ai.jitllm;

import java.nio.file.Path;
import java.util.List;

import org.beehive.jitllm.runtime.memory.BufferClass;
import org.beehive.jitllm.runtime.memory.MemoryComponent;
import org.beehive.jitllm.runtime.memory.MemoryPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link JitLlmChatModel.Builder} that do not load a model.
 *
 * @author Michalis Papadimitriou
 */
class JitLlmChatModelBuilderTests {

	@Test
	void exactlyOneModelSourceIsRequired() {
		assertThatIllegalArgumentException().isThrownBy(() -> JitLlmChatModel.builder().build());
		assertThatIllegalArgumentException().isThrownBy(() -> JitLlmChatModel.builder()
			.modelPath(Path.of("model.gguf"))
			.modelUrl("hf://owner/repo/model.gguf")
			.build());
	}

	@Test
	void anOverBudgetModelIsReportedWithWhatToChange() {
		MemoryPlan plan = new MemoryPlan(
				List.of(new MemoryComponent("layer weights", BufferClass.WEIGHTS_PER_LAYER, 6L << 30, 1, 0)), 4L << 30,
				MemoryPlan.Confidence.EXACT, "test");

		assertThat(JitLlmChatModel.Builder.doesNotFit(Path.of("/models/big.gguf"), plan))
			.startsWith("big.gguf does not fit the device memory budget: 6144 MiB predicted, 4096 MiB configured")
			.contains("-Dtornado.device.memory")
			.contains("layer weights");
	}

}
