package org.springframework.ai.transcription;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.model.ResponseMetadata;
import org.springframework.ai.transcription.metadata.TranscriptionResponseMetadata;

import java.util.Arrays;
import java.util.List;

public class TranscriptionResponse implements ModelResponse<Transcript> {

    private Transcript transcript;

    private TranscriptionResponseMetadata transcriptionResponseMetadata;

    public TranscriptionResponse(Transcript transcript) {
        this(transcript, TranscriptionResponseMetadata.NULL);
    }

    public TranscriptionResponse(Transcript transcript, TranscriptionResponseMetadata transcriptionResponseMetadata) {
        this.transcript = transcript;
        this.transcriptionResponseMetadata = transcriptionResponseMetadata;
    }

    @Override
    public Transcript getResult() {
        return transcript;
    }

    @Override
    public List<Transcript> getResults() {
        return Arrays.asList(transcript);
    }

    @Override
    public TranscriptionResponseMetadata getMetadata() {
        return transcriptionResponseMetadata;
    }
}
