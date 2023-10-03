package org.springframework.ai.loader;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.ai.document.Document;

public interface DocumentReader extends Supplier<List<Document>> {

	List<Document> get();

}
