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

package org.springframework.ai.model.oci.genai.autoconfigure;

import com.oracle.bmc.generativeaiinference.model.EmbedTextDetails;

import org.springframework.ai.oci.OCIEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OCI embedding model.
 *
 * @author Anders Swanson
 */
@ConfigurationProperties(OCIEmbeddingModelProperties.CONFIG_PREFIX)
public class OCIEmbeddingModelProperties {

	public static final String CONFIG_PREFIX = "spring.ai.oci.genai.embedding";

	private ServingMode servingMode = ServingMode.ON_DEMAND;

	private EmbedTextDetails.Truncate truncate = EmbedTextDetails.Truncate.End;

	private String compartment;

	private String model;

	private boolean enabled;

	public OCIEmbeddingOptions getEmbeddingOptions() {
		return OCIEmbeddingOptions.builder()
			.compartment(this.compartment)
			.model(this.model)
			.servingMode(this.servingMode.getMode())
			.truncate(this.truncate)
			.build();
	}

	public ServingMode getServingMode() {
		return this.servingMode;
	}

	public void setServingMode(ServingMode servingMode) {
		this.servingMode = servingMode;
	}

	public String getCompartment() {
		return this.compartment;
	}

	public void setCompartment(String compartment) {
		this.compartment = compartment;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public EmbedTextDetails.Truncate getTruncate() {
		return this.truncate;
	}

	public void setTruncate(EmbedTextDetails.Truncate truncate) {
		this.truncate = truncate;
	}

}
