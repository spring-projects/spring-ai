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

package org.springframework.ai.reader.pdf.layout;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.TextPosition;

import org.springframework.util.Assert;

/**
 * Re-implement the PDFLayoutTextStripperByArea on top of the PDFLayoutTextStripper
 * instead the original PDFTextStripper.
 *
 * This class allows cropping pages (e.g., removing headers, footers, and between-page
 * empty spaces) while extracting layout text, preserving the PDF's internal text
 * formatting.
 *
 * @author Christian Tzolov
 */
public class PDFLayoutTextStripperByArea extends ForkPDFLayoutTextStripper {

	private final List<String> regions = new ArrayList<>();

	private final Map<String, Rectangle2D> regionArea = new HashMap<>();

	private final Map<String, ArrayList<List<TextPosition>>> regionCharacterList = new HashMap<>();

	private final Map<String, StringWriter> regionText = new HashMap<>();

	/**
	 * Constructor.
	 * @throws IOException If there is an error loading properties.
	 */
	public PDFLayoutTextStripperByArea() throws IOException {
		super.setShouldSeparateByBeads(false);
	}

	/**
	 * This method does nothing in this derived class, because beads and regions are
	 * incompatible. Beads are ignored when stripping by area.
	 * @param aShouldSeparateByBeads The new grouping of beads.
	 */
	@Override
	public final void setShouldSeparateByBeads(boolean aShouldSeparateByBeads) {
	}

	/**
	 * Add a new region to group text by.
	 * @param regionName The name of the region.
	 * @param rect The rectangle area to retrieve the text from. The y-coordinates are
	 * java coordinates (y == 0 is top), not PDF coordinates (y == 0 is bottom).
	 */
	public void addRegion(String regionName, Rectangle2D rect) {
		this.regions.add(regionName);
		this.regionArea.put(regionName, rect);
	}

	/**
	 * Delete a region to group text by. If the region does not exist, this method does
	 * nothing.
	 * @param regionName The name of the region to delete.
	 */
	public void removeRegion(String regionName) {
		this.regions.remove(regionName);
		this.regionArea.remove(regionName);
	}

	/**
	 * Get the list of regions that have been setup.
	 * @return A list of java.lang.String objects to identify the region names.
	 */
	public List<String> getRegions() {
		return this.regions;
	}

	/**
	 * Get the text for the region, this should be called after extractRegions().
	 * @param regionName The name of the region to get the text from.
	 * @return The text that was identified in that region.
	 */
	public String getTextForRegion(String regionName) {
		StringWriter text = this.regionText.get(regionName);
		Assert.state(text != null, "Text for region " + regionName + " not found");
		return text.toString();
	}

	/**
	 * Process the page to extract the region text.
	 * @param page The page to extract the regions from.
	 * @throws IOException If there is an error while extracting text.
	 */
	public void extractRegions(PDPage page) throws IOException {
		for (String regionName : this.regions) {
			setStartPage(getCurrentPageNo());
			setEndPage(getCurrentPageNo());
			// reset the stored text for the region so this class can be reused.
			ArrayList<List<TextPosition>> regionCharactersByArticle = new ArrayList<>();
			regionCharactersByArticle.add(new ArrayList<>());
			this.regionCharacterList.put(regionName, regionCharactersByArticle);
			this.regionText.put(regionName, new StringWriter());
		}

		if (page.hasContents()) {
			processPage(page);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTextPosition(TextPosition text) {
		for (Map.Entry<String, Rectangle2D> regionAreaEntry : this.regionArea.entrySet()) {
			Rectangle2D rect = regionAreaEntry.getValue();
			if (rect.contains(text.getX(), text.getY())) {
				this.charactersByArticle = this.regionCharacterList.get(regionAreaEntry.getKey());
				super.processTextPosition(text);
			}
		}
	}

	/**
	 * This will print the processed page text to the output stream.
	 * @throws IOException If there is an error writing the text.
	 */
	@Override
	protected void writePage() throws IOException {
		for (String region : this.regionArea.keySet()) {
			this.charactersByArticle = this.regionCharacterList.get(region);
			this.output = this.regionText.get(region);
			super.writePage();
		}
	}

}
