/**
 *  Copyright (c) 2018 Angelo ZERR
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.lsp4xml.services;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4xml.commons.BadLocationException;
import org.eclipse.lsp4xml.commons.TextDocument;
import org.eclipse.lsp4xml.dom.Attr;
import org.eclipse.lsp4xml.dom.CDataSection;
import org.eclipse.lsp4xml.dom.CharacterData;
import org.eclipse.lsp4xml.dom.Comment;
import org.eclipse.lsp4xml.dom.DocumentType;
import org.eclipse.lsp4xml.dom.Node;
import org.eclipse.lsp4xml.dom.ProcessingInstruction;
import org.eclipse.lsp4xml.dom.Text;
import org.eclipse.lsp4xml.dom.XMLDocument;
import org.eclipse.lsp4xml.dom.XMLParser;
import org.eclipse.lsp4xml.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lsp4xml.settings.XMLFormattingOptions;
import org.eclipse.lsp4xml.utils.XMLBuilder;

/**
 * XML formatter support.
 *
 */
class XMLFormatter {

	private static final Logger LOGGER = Logger.getLogger(XMLFormatter.class.getName());

	private final XMLExtensionsRegistry extensionsRegistry;

	public XMLFormatter(XMLExtensionsRegistry extensionsRegistry) {
		this.extensionsRegistry = extensionsRegistry;
	}

	public List<? extends TextEdit> format(TextDocument document, Range range, XMLFormattingOptions formattingOptions) {
		try {
			// Compute start/end offset range
			int start = -1;
			int end = -1;
			if (range == null) {
				start = 0;
				end = document.getText().length();
			} else {
				start = document.offsetAt(range.getStart());
				end = document.offsetAt(range.getEnd());
			}
			Position startPosition = document.positionAt(start);
			Position endPosition = document.positionAt(end);

			// Parse the content to format to create an XML document with full data (CData,
			// comments, etc)
			String text = document.getText().substring(start, end);
			XMLDocument doc = XMLParser.getInstance().parse(text, null);

			// Format the content
			XMLBuilder xml = new XMLBuilder(formattingOptions, "", document.lineDelimiter(startPosition.getLine()));
			format(doc, 0, end, xml);

			// Returns LSP list of TextEdits
			Range r = new Range(startPosition, endPosition);
			List<TextEdit> edits = new ArrayList<>();
			edits.add(new TextEdit(r, xml.toString()));
			return edits;

		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, "Formatting failed due to BadLocation from 'range' parameter", e);
		}
		return null;
	}

	private void format(Node node, int level, int end, XMLBuilder xml) {
		if (node.tag != null) {

			// element to format

			boolean doLineFeed = !(node.isComment() && ((Comment) node).isCommentSameLineEndTag()) && !node.isText();
			if (level > 0 && doLineFeed) {
				// add new line + indent
				xml.linefeed();
				xml.indent(level);
			}
			// generate start element
			if (node.isCDATA()) {
				CDataSection cdata = (CDataSection) node;
				xml.startCDATA();
				xml.addContentCDATA(cdata.getData());
				xml.endCDATA();
			} else if (node.isComment()) {
				Comment comment = (Comment) node;
				xml.startComment(comment);
				xml.addContentComment(comment.getData());
				xml.endComment();
				if (level == 0) {
					xml.linefeed();
				}
			} else if (node.isProcessingInstruction()) {
				ProcessingInstruction processingInstruction = (ProcessingInstruction) node;
				xml.startPrologOrPI(processingInstruction.tag);
				xml.addContentPI(processingInstruction.getData());
				xml.endPrologOrPI();
				if (level == 0) {
					xml.linefeed();
				}
			} else if (node.isProlog()) {
				xml.startPrologOrPI(node.tag);
				if (node.hasAttributes()) {
					// generate attributes
					String[] attributes = new String[3];
					attributes[0] = "version";
					attributes[1] = "encoding";
					attributes[2] = "standalone";

					for (int i = 0; i < attributes.length; i++) {
						String name = attributes[i];
						String value = node.getAttributeValue(attributes[i]);
						if (value == null) {
							continue;
						}
						xml.addPrologAttribute(name, value, level);
					}
				}
				xml.endPrologOrPI();
				xml.linefeed();
			} else if (node.isDoctype()) {
				DocumentType documentType = (DocumentType) node;
				xml.startDoctype();
				xml.addContentDoctype(documentType.getContent());
				xml.endDoctype();
				xml.linefeed();
			} else if (node.isText()) {
				Text text = (Text) node;
				if (text.hasData()) {
					// Generate content
					if (text.hasMultiLine() && !xml.isJoinContentLines()) {
						xml.linefeed();
					}
					String content = text.getData();
					if (!content.isEmpty()) {
						xml.addContent(content);
					}
				}
				return;
			} else {
				xml.startElement(node.tag, false);
				if (node.hasAttributes()) {
					// generate attributes
					int attributeIndex = 0;
					for (Attr attr : node.getAttributeNodes()) {
						String attributeName = attr.getName();
						xml.addAttribute(attributeName, attr.getValue(), attributeIndex, level, node.tag);
						attributeIndex++;
					}
				}
				boolean hasElements = false;
				boolean startElementClosed = false;
				if (node.hasChildren()) {
					// element has body
					xml.closeStartElement();
					startElementClosed = true;
					level++;
					for (Node child : node.getChildren()) {
						boolean textElement = !child.isText()
								|| (child.isCharacterData() && ((CharacterData) child).hasMultiLine())
								|| node.getChildren().size() > 1;
						if (child.isCharacterData()) {
							if (xml.isJoinContentLines()) {
								textElement = false;
							}
						}

						hasElements = hasElements | textElement;

						format(child, level, end, xml);
					}
					level--;
				}
				if (node.isClosed()) {
					if (hasElements) {
						xml.linefeed();
						xml.indent(level);
					}
					// end tag element is done, only if the element is closed
					// the format, doesn't fix the close tag
					if (node.endTagStart != null && node.endTagStart.intValue() <= end) {
						if (!startElementClosed) {
							xml.closeStartElement();
						}
						xml.endElement(node.tag);
					} else {
						xml.endElement();
					}
				} else if (node.isStartTagClose()) {
					if (!startElementClosed) {
						xml.closeStartElement();
					}
				}
				return;
			}
		} else if (node.hasChildren()) {
			// Other nodes kind like root
			for (Node child : node.getChildren()) {
				format(child, level, end, xml);
			}
		}
	}

}
