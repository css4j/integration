/*

 Copyright (c) 2017-2019, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.w3c.css.sac.Selector;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.StyleSheet;

import io.sf.carte.doc.dom.DOMElement;
import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.SACErrorHandler;
import io.sf.carte.doc.style.css.SheetErrorHandler;
import io.sf.carte.doc.style.css.StyleDeclarationErrorHandler;
import io.sf.carte.doc.style.css.property.CSSPropertyValueException;

public interface SiteErrorReporter {

	void startSiteReport(URL url) throws IOException;

	void setSideDescriptions(String leftSide, String rightSide);

	void leftHasMoreSheets(List<CSSStyleSheet> missingSheets, int smallerCount);

	void rightHasMoreSheets(List<CSSStyleSheet> missingSheets, int smallerCount);

	void mediaQueryError(Node ownerNode, String mediaQuery);

	void linkedStyleError(Node ownerNode, String message);

	void linkedSheetError(Exception exception, CSSStyleSheet sheet);

	void inlineStyleError(CSSElement owner, Exception exception, String style);

	void inlineStyleError(CSSElement owner, StyleDeclarationErrorHandler styleHandler);

	void computedStyleError(CSSElement element, String propertyName, CSSPropertyValueException ex);

	void minifiedMissingProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValue);

	void minifiedExtraProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText, String property,
			String propertyValue);

	void minifiedDifferentValues(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValueText, String miniValueText);

	void minifiedParseErrors(String cssText, String miniCssText,
			StyleDeclarationErrorHandler styleDeclarationErrorHandler);

	void reparsedMissingProperty(CSSStyleSheet parent, int ruleIndex, String initialCssText, String reparsedCssText,
			String property, String propertyValue);

	void reparsedExtraProperty(CSSStyleSheet parent, int ruleIndex, String initialCssText, String reparsedCssText,
			String property, String propertyValue);

	void reparsedDifferentValues(CSSStyleSheet parent, int ruleIndex, String initialCssText, String reparsedCssText,
			String property, String propertyValueText, String reparsedValueText);

	void ruleReparseIssue(CSSStyleSheet parent, int ruleIndex, String parsedText, String finalText);

	void ruleReparseError(CSSStyleSheet parent, int ruleIndex, String parsedText, DOMException ex);

	void ruleReparseErrors(String parsedText, String finalText,
			StyleDeclarationErrorHandler styleDeclarationErrorHandler);

	void differentNodes(DOMElement parent, LinkedList<Node> nodediff);

	void unmatchedLeftSelector(StyleSheet sheet, int sheetIndex, DOMElement elm, String property, String propertyValue,
			LinkedList<Selector> selectorList, LinkedList<Selector> unmatched);

	void unmatchedRightSelector(StyleSheet sheet, int sheetIndex, DOMElement elm, String property, String propertyValue,
			LinkedList<Selector> selectorList, LinkedList<Selector> unmatched);

	void differentComputedValues(DOMElement elm, String property, String valueText, String rightValueText);

	void ruleErrors(CSSStyleSheet sheet, int sheetIndex, StyleDeclarationErrorHandler eh);

	void ruleWarnings(CSSStyleSheet sheet, int sheetIndex, StyleDeclarationErrorHandler eh);

	void omIssues(CSSStyleSheet sheet, int sheetIndex, SheetErrorHandler errHandler);

	void sacIssues(CSSStyleSheet sheet, int sheetIndex, SACErrorHandler errHandler);

	void fail(String message);

	void fail(String message, DOMElement elm, String[] properties, String backendName);

	void fail(String message, DOMException exception);

	void close() throws IOException;

}
