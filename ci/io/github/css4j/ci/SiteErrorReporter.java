/*

 Copyright (c) 2017-2020, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.stylesheets.StyleSheet;

import io.sf.carte.doc.dom.DOMElement;
import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.CSSMediaException;
import io.sf.carte.doc.style.css.CSSRule;
import io.sf.carte.doc.style.css.CSSStyleSheet;
import io.sf.carte.doc.style.css.SACErrorHandler;
import io.sf.carte.doc.style.css.SheetErrorHandler;
import io.sf.carte.doc.style.css.StyleDeclarationErrorHandler;
import io.sf.carte.doc.style.css.nsac.Selector;
import io.sf.carte.doc.style.css.nsac.SelectorList;
import io.sf.carte.doc.style.css.om.AbstractCSSStyleSheet;
import io.sf.carte.doc.style.css.om.CSSStyleDeclarationRule;
import io.sf.carte.doc.style.css.property.CSSPropertyValueException;

public interface SiteErrorReporter {

	void startSiteReport(URL url) throws IOException;

	void setSideDescriptions(String leftSide, String rightSide);

	void sideComparison(String message);

	void leftHasMoreSheets(List<CSSStyleSheet<? extends CSSRule>> missingSheets, int smallerCount);

	void rightHasMoreSheets(List<CSSStyleSheet<? extends CSSRule>> missingSheets, int smallerCount);

	void mediaQueryError(Node ownerNode, CSSMediaException exception);

	void linkedStyleError(Node ownerNode, String message);

	void linkedSheetError(Exception exception, CSSStyleSheet<? extends CSSRule> sheet);

	void inlineStyleError(CSSElement owner, Exception exception, String style);

	void inlineStyleError(CSSElement owner, StyleDeclarationErrorHandler styleHandler);

	void computedStyleError(CSSElement element, String propertyName, CSSPropertyValueException ex);

	void presentationalHintError(DOMElement element, DOMException ex);

	void minifiedMissingProperty(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String cssText,
			String miniCssText, String property, String propertyValue);

	void minifiedExtraProperty(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String cssText,
			String miniCssText, String property, String propertyValue);

	void minifiedDifferentValues(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String cssText,
			String miniCssText, String property, String propertyValueText, String miniValueText);

	void minifiedParseErrors(String cssText, String miniCssText,
			StyleDeclarationErrorHandler styleDeclarationErrorHandler);

	void reparsedMissingProperty(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String initialCssText,
			String reparsedCssText, String property, String propertyValue);

	void reparsedExtraProperty(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String initialCssText,
			String reparsedCssText, String property, String propertyValue);

	void reparsedDifferentValues(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String initialCssText,
			String reparsedCssText, String property, String propertyValueText, String reparsedValueText);

	void ruleReparseIssue(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String parsedText, String finalText);

	void ruleReparseError(CSSStyleSheet<? extends CSSRule> parent, int ruleIndex, String parsedText, DOMException ex);

	void ruleReparseErrors(String parsedText, String finalText,
			StyleDeclarationErrorHandler styleDeclarationErrorHandler);

	void ruleSelectorError(CSSStyleDeclarationRule stylerule, SelectorList selist, SelectorList oselist,
			String selectorText, int sheetIndex, int ruleIndex, AbstractCSSStyleSheet parent);

	void ioError(String href, IOException exception);

	void differentNodes(DOMElement parent, LinkedList<Node> nodediff);

	void unmatchedLeftSelector(StyleSheet sheet, int sheetIndex, DOMElement elm, String property, String propertyValue,
			LinkedList<Selector> selectorList, LinkedList<Selector> unmatched);

	void unmatchedRightSelector(StyleSheet sheet, int sheetIndex, DOMElement elm, String property, String propertyValue,
			LinkedList<Selector> selectorList, LinkedList<Selector> unmatched);

	void differentComputedValues(DOMElement elm, String property, String valueText, String rightValueText);

	void ruleErrors(CSSStyleSheet<? extends CSSRule> sheet, int sheetIndex, StyleDeclarationErrorHandler eh);

	void ruleWarnings(CSSStyleSheet<? extends CSSRule> sheet, int sheetIndex, StyleDeclarationErrorHandler eh);

	void omIssues(CSSStyleSheet<? extends CSSRule> sheet, int sheetIndex, SheetErrorHandler errHandler);

	void sacIssues(CSSStyleSheet<? extends CSSRule> sheet, int sheetIndex, SACErrorHandler errHandler);

	void fail(String message);

	void computedStyleExtraProperties(String message, DOMElement elm, String[] properties, String backendName);

	void fail(String message, DOMException exception);

	void close() throws IOException;

}
