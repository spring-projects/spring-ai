/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MethodToolCallback}.
 *
 * @author Cui-xf
 */
class MethodToolCallbackTests {

	@Test
	void callTest() {
		ToolCallback[] tools = ToolCallbacks.from(new Tools());
		ToolCallback inspirationalTool = findToolByName(tools, "getInspirationalQuote");
		String quote = inspirationalTool.call("{}");
		assertThat(quote).isEqualTo("\"不要等待机会，而要创造机会。\"");

		ToolCallback loveTool = findToolByName(tools, "getLoveQuote");
		String loveQuote = loveTool.call("{}");
		assertThat(loveQuote).isEqualTo("\"你是我扭转乾坤的理由，我是你放肆温柔的港湾。\"");

		ToolCallback poemsTool = findToolByName(tools, "getPoems");
		String poems = poemsTool.call("{}");
		assertThat(poems).isEqualTo("\"举杯邀明月，对影成三人。\"\n\"人生得意须尽欢，莫使金樽空对月。\"\n\"床前明月光，疑是地上霜。\"");

		ToolCallback foodTool = findToolByName(tools, "getFood");
		String food = foodTool.call("{}");
		assertThat(food)
			.isEqualTo("{\"name\":\"麻婆豆腐\",\"description\":\"川菜代表作，嫩滑的豆腐配上麻辣的酱汁，令人欲罢不能\",\"spicyLevel\":4}");

		ToolCallback foodAsyncTool = findToolByName(tools, "getFoodAsync");
		String foodAsync = foodAsyncTool.call("{}");
		assertThat(foodAsync).isEqualTo("{\"name\":\"红烧肉\",\"description\":\"肥而不腻，入口即化，带着浓郁的酱香\",\"spicyLevel\":1}");

		ToolCallback foodListTool = findToolByName(tools, "getFoodList");
		String foodList = foodListTool.call("{}");
		assertThat(foodList).isEqualTo(
				"{\"name\":\"火锅\",\"description\":\"暖心暖胃的快乐源泉\",\"spicyLevel\":5}\n{\"name\":\"小笼包\",\"description\":\"一口一个，汤汁四溢\",\"spicyLevel\":1}\n{\"name\":\"烤鸭\",\"description\":\"外酥内嫩，香而不腻\",\"spicyLevel\":2}");
	}

	@Test
	void reactiveCallTest() {
		ToolCallback[] tools = ToolCallbacks.from(new Tools());

		ToolCallback inspirationalTool = findToolByName(tools, "getInspirationalQuote");
		Mono<String> quote = inspirationalTool.reactiveCall("{}");
		StepVerifier.create(quote).expectNext("\"不要等待机会，而要创造机会。\"").verifyComplete();

		ToolCallback loveTool = findToolByName(tools, "getLoveQuote");
		Mono<String> loveQuote = loveTool.reactiveCall("{}");
		StepVerifier.create(loveQuote).expectNext("\"你是我扭转乾坤的理由，我是你放肆温柔的港湾。\"").verifyComplete();

		ToolCallback poemsTool = findToolByName(tools, "getPoems");
		Mono<String> poems = poemsTool.reactiveCall("{}");
		StepVerifier.create(poems)
			.expectNext("\"举杯邀明月，对影成三人。\"\n\"人生得意须尽欢，莫使金樽空对月。\"\n\"床前明月光，疑是地上霜。\"")
			.verifyComplete();

		ToolCallback foodTool = findToolByName(tools, "getFood");
		Mono<String> food = foodTool.reactiveCall("{}");
		StepVerifier.create(food)
			.expectNext("{\"name\":\"麻婆豆腐\",\"description\":\"川菜代表作，嫩滑的豆腐配上麻辣的酱汁，令人欲罢不能\",\"spicyLevel\":4}")
			.verifyComplete();

		ToolCallback foodAsyncTool = findToolByName(tools, "getFoodAsync");
		Mono<String> foodAsync = foodAsyncTool.reactiveCall("{}");
		StepVerifier.create(foodAsync)
			.expectNext("{\"name\":\"红烧肉\",\"description\":\"肥而不腻，入口即化，带着浓郁的酱香\",\"spicyLevel\":1}")
			.verifyComplete();

		ToolCallback foodListTool = findToolByName(tools, "getFoodList");
		Mono<String> foodList = foodListTool.reactiveCall("{}");
		StepVerifier.create(foodList)
			.expectNext(
					"{\"name\":\"火锅\",\"description\":\"暖心暖胃的快乐源泉\",\"spicyLevel\":5}\n{\"name\":\"小笼包\",\"description\":\"一口一个，汤汁四溢\",\"spicyLevel\":1}\n{\"name\":\"烤鸭\",\"description\":\"外酥内嫩，香而不腻\",\"spicyLevel\":2}")
			.verifyComplete();

	}

	private ToolCallback findToolByName(ToolCallback[] tools, String name) {
		for (ToolCallback tool : tools) {
			if (tool.getToolDefinition().name().equals(name)) {
				return tool;
			}
		}
		throw new IllegalArgumentException("Tool not found: " + name);
	}

}

class Tools {

	@Tool(description = "获取一句励志名言")
	public String getInspirationalQuote() {
		return "不要等待机会，而要创造机会。";
	}

	@Tool(description = "异步获取一句情话")
	public Mono<String> getLoveQuote() {
		return Mono.just("你是我扭转乾坤的理由，我是你放肆温柔的港湾。");
	}

	@Tool(description = "获取一组古诗词")
	public Flux<String> getPoems() {
		return Flux.just("举杯邀明月，对影成三人。", "人生得意须尽欢，莫使金樽空对月。", "床前明月光，疑是地上霜。");
	}

	@Tool(description = "推荐一道美食")
	public FoodRecommendation getFood() {
		return new FoodRecommendation("麻婆豆腐", "川菜代表作，嫩滑的豆腐配上麻辣的酱汁，令人欲罢不能", 4);
	}

	@Tool(description = "异步推荐一道美食")
	public Mono<FoodRecommendation> getFoodAsync() {
		return Mono.fromSupplier(() -> new FoodRecommendation("红烧肉", "肥而不腻，入口即化，带着浓郁的酱香", 1));
	}

	@Tool(description = "获取一组美食推荐")
	public Flux<FoodRecommendation> getFoodList() {
		return Flux.just(new FoodRecommendation("火锅", "暖心暖胃的快乐源泉", 5), new FoodRecommendation("小笼包", "一口一个，汤汁四溢", 1),
				new FoodRecommendation("烤鸭", "外酥内嫩，香而不腻", 2));
	}

}

record FoodRecommendation(String name, String description, int spicyLevel) {
}
