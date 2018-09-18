/**
 *  Copyright (c) 2018 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.lsp4xml.dom;

import static java.lang.System.lineSeparator;

import org.eclipse.lsp4xml.commons.BadLocationException;
import org.eclipse.lsp4xml.utils.StringUtils;

/**
 * A CharacterData node.
 *
 */
public class CharacterData extends Node {

	private String data;

	private String normalizedData;

	public CharacterData(int start, int end, XMLDocument ownerDocument) {
		super(start, end, ownerDocument);
	}

	public String getData() {
		if (data == null) {
			data = getOwnerDocument().getText().substring(getStartContent(), getEndContent());
		}
		return data;
	}

	public boolean hasMultiLine() {
		try {
			String delimiter = getOwnerDocument().getTextDocument().lineDelimiter(0);
			return getData().contains(delimiter);
		} catch (BadLocationException e) {
			return getData().contains(lineSeparator());
		}
	}

	public String getNormalizedData() {
		if (normalizedData == null) {
			normalizedData = StringUtils.normalizeSpace(getData());
		}
		return normalizedData;
	}

	public boolean hasData() {
		return !getData().isEmpty();
	}

	public int getStartContent() {
		return start;
	}

	public int getEndContent() {
		return end;
	}
}
