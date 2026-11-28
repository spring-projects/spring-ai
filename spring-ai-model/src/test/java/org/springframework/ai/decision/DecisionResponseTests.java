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

package org.springframework.ai.decision;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DecisionResponse}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class DecisionResponseTests {

	private final DecisionPrompt prompt = DecisionPrompt.builder()
		.text("Where is my order? I ordered it two weeks ago.")
		.question("defect", YesNoQuestion.of("Does the customer report a defect?"))
		.question("topic", ChoiceQuestion.of("What is the message about?", "delivery", "product"))
		.question("satisfaction", ScoreQuestion.of("How satisfied is the customer?", "unhappy", "neutral", "happy"))
		.question("secret", YesNoQuestion.of("Is it a secret?"))
		.build();

	private final DecisionResponse response = DecisionResponse.builder(this.prompt)
		.answer("defect", YesNoAnswer.builder().probability(0.9).build())
		.answer("topic",
				ChoiceAnswer.builder().value("product").probabilities(Map.of("product", 0.8, "delivery", 0.2)).build())
		.answer("satisfaction", ScoreAnswer.builder().value(1.5).build())
		.answer("secret", RefusalAnswer.builder().build())
		.metadata(metadata("dec_1", "jev-1", new DefaultUsage(10, 2)))
		.build();

	@Test
	void exposesResultsInOrder() {
		assertThat(this.response.getResults()).extracting(DecisionResult::getName)
			.containsExactly("defect", "topic", "satisfaction", "secret");
		assertThat(this.response.getResult()).isNotNull();
		assertThat(this.response.getResult().getName()).isEqualTo("defect");
		assertThat(this.response.getAnswers().keySet()).containsExactly("defect", "topic", "satisfaction", "secret");
	}

	@Test
	void typedAccessors() {
		assertThat(this.response.getYesNo("defect").isYes()).isTrue();
		assertThat(this.response.getChoice("topic").getValue()).isEqualTo("product");
		assertThat(this.response.getScore("satisfaction").getNearestLevel()).isEqualTo(2);
		assertThat(this.response.getAnswer("secret", RefusalAnswer.class)).isEqualTo(RefusalAnswer.builder().build());
		assertThat(this.response.isRefused("secret")).isTrue();
		assertThat(this.response.isRefused("defect")).isFalse();
		assertThat(this.response.hasAnswer("defect")).isTrue();
		assertThat(this.response.hasAnswer("missing")).isFalse();
	}

	@Test
	void accessorsFailOnUnknownNameWrongTypeAndRefusal() {
		assertThatThrownBy(() -> this.response.getAnswer("missing")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("no answer for question 'missing'");
		assertThatThrownBy(() -> this.response.getChoice("defect")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("YesNoAnswer")
			.hasMessageContaining("ChoiceAnswer");
		assertThatThrownBy(() -> this.response.getYesNo("secret")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("refused");
	}

	@Test
	void metadata() {
		assertThat(this.response.getMetadata().getId()).isEqualTo("dec_1");
		assertThat(this.response.getMetadata().getModel()).isEqualTo("jev-1");
		assertThat(this.response.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
	}

	@Test
	void metadataDefaultsToEmptyUsage() {
		var response = DecisionResponse.builder(this.prompt)
			.answers(Map.of("defect", RefusalAnswer.builder().build(), "topic", RefusalAnswer.builder().build(),
					"satisfaction", RefusalAnswer.builder().build(), "secret", RefusalAnswer.builder().build()))
			.build();

		assertThat(response.getMetadata().getId()).isNull();
		assertThat(response.getMetadata().getModel()).isNull();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isZero();
	}

	@Test
	void metadataEqualsOnIdModelAndUsage() {
		var metadata = metadata("dec_1", "jev-1", new DefaultUsage(10, 2));

		assertThat(metadata).isEqualTo(metadata("dec_1", "jev-1", new DefaultUsage(10, 2)))
			.hasSameHashCodeAs(metadata("dec_1", "jev-1", new DefaultUsage(10, 2)));
		assertThat(DecisionResponseMetadata.builder().build()).isEqualTo(DecisionResponseMetadata.builder().build())
			.hasSameHashCodeAs(DecisionResponseMetadata.builder().build());
		assertThat(metadata).isNotEqualTo(metadata("dec_2", "jev-1", new DefaultUsage(10, 2)));
		assertThat(metadata).isNotEqualTo(metadata("dec_1", "jev-2", new DefaultUsage(10, 2)));
		assertThat(metadata).isNotEqualTo(metadata("dec_1", "jev-1", new DefaultUsage(10, 3)));
		assertThat(metadata).isNotEqualTo(metadata("dec_1", "jev-1", new EmptyUsage()));
	}

	@Test
	void metadataEqualityIgnoresMapEntries() {
		var metadata = DecisionResponseMetadata.builder().id("dec_1").build();
		var same = DecisionResponseMetadata.builder().id("dec_1").build();
		same.put("extra", "value");

		assertThat(metadata).isEqualTo(same);
	}

	@Test
	void metadataEqualityRequiresTheSameClass() {
		var metadata = metadata("dec_1", "jev-1", new DefaultUsage(10, 2));
		var subclass = new DecisionResponseMetadata("dec_1", "jev-1", new DefaultUsage(10, 2)) {
		};

		assertThat(metadata).isNotEqualTo(subclass);
		assertThat(subclass).isNotEqualTo(metadata);
	}

	@Test
	void responsesWithEqualAnswersAndMetadataAreEqual() {
		var same = DecisionResponse.builder(this.prompt)
			.answers(this.response.getAnswers())
			.metadata(metadata("dec_1", "jev-1", new DefaultUsage(10, 2)))
			.build();
		var otherMetadata = DecisionResponse.builder(this.prompt)
			.answers(this.response.getAnswers())
			.metadata(metadata("dec_2", "jev-1", new DefaultUsage(10, 2)))
			.build();

		assertThat(this.response).isEqualTo(same).hasSameHashCodeAs(same);
		assertThat(this.response).isNotEqualTo(otherMetadata);
	}

	private static DecisionResponseMetadata metadata(String id, String model, Usage usage) {
		return DecisionResponseMetadata.builder().id(id).model(model).usage(usage).build();
	}

	@Nested
	class BuilderTests {

		private final DecisionPrompt prompt = DecisionPrompt.builder()
			.text("Where is my order? I ordered it two weeks ago.")
			.question("defect", YesNoQuestion.of("Does the customer report a defect?"))
			.question("topic", ChoiceQuestion.of("What is the message about?", "delivery", "product"))
			.question("satisfaction", ScoreQuestion.of("How satisfied is the customer?", "unhappy", "neutral", "happy"))
			.build();

		@Test
		void ordersAnswersByThePrompt() {
			var metadata = DecisionResponseMetadata.builder().model("m").build();

			var response = DecisionResponse.builder(this.prompt)
				.answer("satisfaction", ScoreAnswer.builder().value(1.2).probabilities(List.of(0.1, 0.6, 0.3)).build())
				.answer("defect", YesNoAnswer.builder().probability(0.9).build())
				.answer("topic",
						ChoiceAnswer.builder().value("delivery").probabilities(Map.of("delivery", 0.7)).build())
				.metadata(metadata)
				.build();

			assertThat(response.getAnswers().keySet()).containsExactly("defect", "topic", "satisfaction");
			assertThat(response.getMetadata()).isSameAs(metadata);
		}

		@Test
		void carriesResultMetadata() {
			DecisionResultMetadata metadata = new DecisionResultMetadata() {
			};

			var response = answered().build();
			var withMetadata = DecisionResponse.builder(this.prompt)
				.answer("defect", YesNoAnswer.builder().probability(0.9).build(), metadata)
				.answer("topic", ChoiceAnswer.builder().value("delivery").build())
				.answer("satisfaction", ScoreAnswer.builder().value(1).build())
				.build();

			assertThat(response.getResult()).isNotNull();
			assertThat(response.getResult().getMetadata()).isNotNull().isNotSameAs(metadata);
			assertThat(withMetadata.getResults().get(0).getMetadata()).isSameAs(metadata);
			assertThat(withMetadata.getResults().get(1).getMetadata())
				.isSameAs(response.getResults().get(1).getMetadata());
		}

		@Test
		void acceptsAnswersMapAndRefusals() {
			var response = DecisionResponse.builder(this.prompt)
				.answers(Map.of("defect", RefusalAnswer.builder().build(), "topic",
						RefusalAnswer.builder().reason("policy").build(), "satisfaction",
						RefusalAnswer.builder().build()))
				.build();

			assertThat(response.getResults()).hasSize(3);
			assertThat(response.isRefused("topic")).isTrue();
		}

		@Test
		void rejectsMissingAnswer() {
			var builder = DecisionResponse.builder(this.prompt)
				.answer("defect", YesNoAnswer.builder().probability(0.9).build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessage("missing answer for question 'topic'");
		}

		@Test
		void rejectsAnswerWithoutQuestion() {
			var builder = answered().answer("extra", YesNoAnswer.builder().probability(0.1).build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessage("the prompt has no question named 'extra'");
		}

		@Test
		void rejectsDuplicateAnswer() {
			var builder = DecisionResponse.builder(this.prompt)
				.answer("defect", YesNoAnswer.builder().probability(0.9).build());

			assertThatThrownBy(() -> builder.answer("defect", YesNoAnswer.builder().probability(0.1).build()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("duplicate answer for question 'defect'");
		}

		@Test
		void rejectsWrongAnswerType() {
			var builder = DecisionResponse.builder(this.prompt)
				.answer("defect", ChoiceAnswer.builder().value("delivery").build())
				.answer("topic", ChoiceAnswer.builder().value("delivery").build())
				.answer("satisfaction", ScoreAnswer.builder().value(1).build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("'defect'")
				.hasMessageContaining("ChoiceAnswer")
				.hasMessageContaining("YesNoAnswer was expected");
		}

		@Test
		void rejectsChoiceOutsideOptions() {
			var builder = answered("topic", ChoiceAnswer.builder().value("account").build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("'account'")
				.hasMessageContaining("[delivery, product]");
		}

		@Test
		void rejectsChoiceProbabilityForUnknownOption() {
			var builder = answered("topic",
					ChoiceAnswer.builder().value("delivery").probabilities(Map.of("account", 0.5)).build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("probability for 'account'");
		}

		@Test
		void rejectsScoreOutOfRange() {
			var builder = answered("satisfaction", ScoreAnswer.builder().value(2.5).build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("above the highest level 2");
		}

		@Test
		void rejectsScoreProbabilitiesNotMatchingLevels() {
			var builder = answered("satisfaction",
					ScoreAnswer.builder().value(1).probabilities(List.of(0.5, 0.5)).build());

			assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2 probabilities for 3 levels");
		}

		private DecisionResponse.Builder answered() {
			return DecisionResponse.builder(this.prompt)
				.answer("defect", YesNoAnswer.builder().probability(0.9).build())
				.answer("topic", ChoiceAnswer.builder().value("delivery").build())
				.answer("satisfaction", ScoreAnswer.builder().value(1).build());
		}

		/**
		 * All questions answered validly, except the given one.
		 */
		private DecisionResponse.Builder answered(String name, DecisionAnswer answer) {
			var builder = DecisionResponse.builder(this.prompt);
			if (!"defect".equals(name)) {
				builder.answer("defect", YesNoAnswer.builder().probability(0.9).build());
			}
			if (!"topic".equals(name)) {
				builder.answer("topic", ChoiceAnswer.builder().value("delivery").build());
			}
			if (!"satisfaction".equals(name)) {
				builder.answer("satisfaction", ScoreAnswer.builder().value(1).build());
			}
			return builder.answer(name, answer);
		}

	}

}
