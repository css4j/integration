/*

 Copyright (c) 2017-2020, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ListIterator;

import org.dom4j.dom.DOMElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.StyleSheet;

import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.SACErrorHandler;
import io.sf.carte.doc.style.css.nsac.CSSParseException;
import io.sf.carte.doc.style.css.om.DefaultSheetErrorHandler;

public class LogSiteErrorReporter extends BaseSiteErrorReporter {

	final static Logger log = LoggerFactory.getLogger(LogSiteErrorReporter.class.getName());

	private int lastSheetIndex = -1;
	private int lastWarningSheetIndex = -1;

	@Override
	public void startSiteReport(URL url) throws IOException {
	}

	@Override
	void writeError(String message, Throwable exception) {
		log.error(message, exception);
	}

	@Override
	void writeError(String message) {
		log.error(message);
	}

	@Override
	void writeMinificationError(String message) {
		log.error(message);
	}

	@Override
	void writeSerializationError(String message) {
		log.error(message);
	}

	@Override
	void writeSerializationError(String message, DOMException exception) {
		log.error(message, exception);
	}

	@Override
	void writeWarning(String message) {
		log.warn(message);
	}

	@Override
	void selectErrorTargetSheet(StyleSheet sheet, int sheetIndex) {
		if (lastSheetIndex != sheetIndex) {
			selectTargetSheet(sheet, sheetIndex, false);
			lastSheetIndex = sheetIndex;
		}
	}

	@Override
	void selectWarningTargetSheet(StyleSheet sheet, int sheetIndex) {
		if (lastWarningSheetIndex != sheetIndex) {
			selectTargetSheet(sheet, sheetIndex, true);
			lastWarningSheetIndex = sheetIndex;
		}
	}

	private void selectTargetSheet(StyleSheet sheet, int sheetIndex, boolean warn) {
		CSSElement owner;
		if ((owner = (CSSElement) sheet.getOwnerNode()) != null && "style".equalsIgnoreCase(owner.getTagName())) {
			String text;
			if (owner instanceof DOMElement) {
				text = ((DOMElement) owner).getText();
			} else {
				text = owner.getTextContent();
			}
			writeWarning("Sheet:\n" + text);
		} else {
			String uri = sheet.getHref();
			if (uri != null) {
				writeWarning("Sheet at " + uri);
			}
		}
	}

	@Override
	public void sacIssues(CSSStyleSheet sheet, int sheetIndex, SACErrorHandler errHandler) {
		selectTargetSheet(sheet, sheetIndex, !errHandler.hasSacErrors());
		writeError(errHandler.toString());
		if (errHandler instanceof DefaultSheetErrorHandler) {
			DefaultSheetErrorHandler dseh = (DefaultSheetErrorHandler) errHandler;
			logSacErrors(dseh.getSacErrors());
			logSacWarnings(dseh.getSacWarnings());
		}
	}

	private void logSacErrors(List<CSSParseException> sacErrors) {
		if (sacErrors != null) {
			ListIterator<CSSParseException> it = sacErrors.listIterator();
			while (it.hasNext()) {
				CSSParseException ex = it.next();
				log.error(
						"SAC error at [" + ex.getLineNumber() + "," + ex.getColumnNumber() + "]: " + ex.getMessage());
			}
		}
	}

	private void logSacWarnings(List<CSSParseException> sacWarnings) {
		if (sacWarnings != null) {
			ListIterator<CSSParseException> it = sacWarnings.listIterator();
			while (it.hasNext()) {
				CSSParseException ex = it.next();
				log.warn(
						"SAC warning at [" + ex.getLineNumber() + "," + ex.getColumnNumber() + "]: " + ex.getMessage());
			}
		}
	}

	@Override
	public void close() throws IOException {
	}

}
