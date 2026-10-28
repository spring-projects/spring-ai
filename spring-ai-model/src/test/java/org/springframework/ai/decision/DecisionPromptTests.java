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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.input.TextInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DecisionPrompt} and {@link DecisionInput}.
 *
 * @author Thomas Vitale
 */
class DecisionPromptTests {

	@Test
	void buildsPromptWithQuestionsInOrder() {
		var prompt = DecisionPrompt.builder()
			.text("Help! My payouts are late")
			.question("urgent", YesNoQuestion.of("Is it urgent?"))
			.question("team", ChoiceQuestion.of("Which team?", "billing", "technical"))
			.options(DecisionOptions.builder().model("jev-latest").build())
			.build();

		assertThat(prompt.getInstructions()).isInstanceOf(TextInput.class);
		assertThat(((TextInput) prompt.getInstructions()).getText()).isEqualTo("Help! My payouts are late");
		assertThat(prompt.getQuestions().keySet()).containsExactly("urgent", "team");
		assertThat(prompt.getOptions()).isNotNull();
		assertThat(prompt.getOptions().getModel()).isEqualTo("jev-latest");
	}

	@Test
	void rejectsDuplicateAndMissingQuestions() {
		assertThatThrownBy(() -> DecisionPrompt.builder()
			.text("x")
			.question("urgent", YesNoQuestion.of("Is it urgent?"))
			.question("urgent", YesNoQuestion.of("Is it urgent?"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("duplicate question 'urgent'");
		assertThatThrownBy(() -> DecisionPrompt.builder().text("x").build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least one question");
		assertThatThrownBy(() -> DecisionPrompt.builder().question("q", YesNoQuestion.of("?")).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("input");
	}

	@Test
	void mutateKeepsEverything() {
		var prompt = DecisionPrompt.builder()
			.text("x")
			.question("urgent", YesNoQuestion.of("Is it urgent?"))
			.options(DecisionOptions.builder().model("a").build())
			.build();

		var mutated = prompt.mutate().options(DecisionOptions.builder().model("b").build()).build();

		assertThat(mutated.getInstructions()).isEqualTo(prompt.getInstructions());
		assertThat(mutated.getQuestions()).isEqualTo(prompt.getQuestions());
		assertThat(mutated.getOptions()).isNotNull();
		assertThat(mutated.getOptions().getModel()).isEqualTo("b");
	}

	@Test
	void textInputRejectsBlankText() {
		assertThatThrownBy(() -> DecisionInput.text(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThat(DecisionInput.text("x").getParts()).containsExactly(TextPart.of("x"));
	}

	@Test
	void contentInputKeepsPartsInOrder() {
		var image = MediaPart.of(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(new byte[] { 1 })));

		var input = DecisionInput.content(TextPart.of("What is "), image, TextPart.of("this?"));

		assertThat(input.getParts()).containsExactly(TextPart.of("What is "), image, TextPart.of("this?"));
		assertThat(input.getText()).isEqualTo("What is this?");
		assertThat(input)
			.isEqualTo(DecisionInput.content(List.of(TextPart.of("What is "), image, TextPart.of("this?"))));
		assertThat(DecisionInput.content(image).getText()).isEmpty();
		assertThat(
				DecisionPrompt.builder().content(image).question("q", YesNoQuestion.of("?")).build().getInstructions())
			.isEqualTo(DecisionInput.content(List.of(image)));
	}

	@Test
	void contentInputRejectsMissingParts() {
		assertThatThrownBy(() -> DecisionInput.content(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DecisionInput.content(TextPart.of("x"), null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fieldsInputAcceptsJsonLikeValuesAndTopLevelParts() {
		var image = MediaPart.of(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(new byte[] { 1 })));
		Map<String, Object> fields = new HashMap<>();
		fields.put("text", "hello");
		fields.put("count", 3);
		fields.put("flag", true);
		fields.put("nothing", null);
		fields.put("nested", Map.of("list", List.of(1, "two")));
		fields.put("image", image);
		fields.put("attachment", List.of(TextPart.of("The receipt"), image));

		assertThat(DecisionInput.fields(fields).getFields()).containsAllEntriesOf(fields);
	}

	@Test
	void fieldsInputRejectsUnsupportedValues() {
		assertThatThrownBy(() -> DecisionInput.fields(Map.of("date", new Object())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unsupported value type");
		var media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(new byte[] { 1 }));
		assertThatThrownBy(() -> DecisionInput.fields(Map.of("image", media)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MediaPart");
		assertThatThrownBy(() -> DecisionInput.fields(Map.of("nested", Map.of("image", MediaPart.of(media)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("top level");
		assertThatThrownBy(() -> DecisionInput.fields(Map.of("nested", List.of(List.of(MediaPart.of(media))))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("top level");
		assertThatThrownBy(() -> DecisionInput.fields(Map.of("mixed", List.of("a", MediaPart.of(media)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cannot hold other values");
		assertThatThrownBy(() -> DecisionInput.fields(Map.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void conversationInput() {
		var messages = List.of(new UserMessage("Hi"), new AssistantMessage("Hello"));

		assertThat(DecisionInput.conversation(messages).getMessages()).containsExactlyElementsOf(messages);
		assertThatThrownBy(() -> DecisionInput.conversation(List.of())).isInstanceOf(IllegalArgumentException.class);
	}

}
