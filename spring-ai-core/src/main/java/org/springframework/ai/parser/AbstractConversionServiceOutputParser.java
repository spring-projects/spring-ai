package org.springframework.ai.parser;

import org.springframework.core.convert.support.DefaultConversionService;

public abstract class AbstractConversionServiceOutputParser<T> implements OutputParser<T> {

	private final DefaultConversionService conversionService;

	public AbstractConversionServiceOutputParser(DefaultConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public DefaultConversionService getConversionService() {
		return conversionService;
	}

}
