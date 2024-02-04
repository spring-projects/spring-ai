package org.springframework.ai.openai.transcription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.chat.ActorsFilms;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.ai.transcription.TranscriptionRequest;
import org.springframework.ai.transcription.TranscriptionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiTranscriptionClientIT extends AbstractIT {

	@Value("classpath:/speech/jfk.flac")
	private Resource audioFile;

	@Test
	void transcriptionTest() {
		TranscriptionRequest transcriptionRequest = new TranscriptionRequest(audioFile);
		TranscriptionResponse response = openAiTranscriptionClient.call(transcriptionRequest);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo("And so my fellow Americans, ask not what your country can do for you, ask what you can do for your country.");
	}
}
