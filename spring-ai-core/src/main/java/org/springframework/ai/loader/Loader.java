package org.springframework.ai.loader;

import org.springframework.ai.document.Document;
import org.springframework.ai.splitter.TextSplitter;

import java.util.List;

public interface Loader {

	List<Document> load();

	List<Document> load(TextSplitter textSplitter);

}
