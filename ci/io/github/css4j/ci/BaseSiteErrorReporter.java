/*

 Copyright (c) 2017-2021, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.SACMediaList;
import org.w3c.css.sac.Selector;
import org.w3c.css.sac.SelectorList;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.StyleSheet;

import io.sf.carte.doc.dom.DOMElement;
import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.CSSMediaException;
import io.sf.carte.doc.style.css.ExtendedCSSRule;
import io.sf.carte.doc.style.css.SACErrorHandler;
import io.sf.carte.doc.style.css.SheetErrorHandler;
import io.sf.carte.doc.style.css.StyleDeclarationErrorHandler;
import io.sf.carte.doc.style.css.om.AbstractCSSStyleSheet;
import io.sf.carte.doc.style.css.om.CSSStyleDeclarationRule;
import io.sf.carte.doc.style.css.om.DefaultSheetErrorHandler;
import io.sf.carte.doc.style.css.om.DefaultStyleDeclarationErrorHandler;
import io.sf.carte.doc.style.css.om.RuleParseException;
import io.sf.carte.doc.style.css.property.CSSPropertyValueException;

abstract public class BaseSiteErrorReporter implements SiteErrorReporter {

	String leftSide;
	String rightSide;

	abstract void writeError(String message, Throwable exception);

	abstract void writeError(String message);

	abstract void writeMinificationError(String message);

	abstract void writeSerializationError(String message);

	abstract void writeSerializationError(String message, DOMException exception);

	abstract void writeWarning(String message);

	abstract void selectErrorTargetSheet(StyleSheet sheet, int sheetIndex);

	abstract void selectWarningTargetSheet(StyleSheet sheet, int sheetIndex);

	@Override
	public void setSideDescriptions(String leftSide, String rightSide) {
		this.leftSide = leftSide;
		this.rightSide = rightSide;
	}

	@Override
	public void computedStyleExtraProperties(String message, DOMElement elm, String[] properties, String backendName) {
		StringBuilder buf = new StringBuilder(message.length() + 128);
		buf.append('[').append(backendName).append(']').append(' ').append(message).append(" at: ");
		String nsuri = elm.getNamespaceURI();
		if (nsuri != null) {
			buf.append(nsuri).append(':');
		}
		buf.append(elm.getTagName());
		String id = elm.getId();
		if (id.length() != 0) {
			buf.append(" id='").append(id).append('\'');
		} else if (elm.getParentNode() != null) {
			buf.append(" parent=").append(elm.getParentNode().getNodeName());
		}
		buf.append(", properties:");
		for (String property : properties) {
			buf.append(' ').append(property);
		}
		writeError(buf.toString());
	}

	@Override
	public void fail(String message, DOMException ex) {
		writeError(message, ex);
		String exmsg = ex.getMessage();
		StringBuilder buf = new StringBuilder(message.length() + exmsg.length() + 2);
		buf.append(message).append(": ").append(exmsg);
		fail(buf.toString());
	}

	@Override
	public void fail(String message) {
		try {
			close();
		} catch (IOException e) {
		}
		org.junit.Assert.fail(message);
	}

	@Override
	public void sideComparison(String message) {
		writeError("Failed comparison to " + rightSide + '.');
		writeError(message);
	}

	@Override
	public void leftHasMoreSheets(List<CSSStyleSheet> missingSheets, int smallerCount) {
		writeError(leftSide + " has more style sheets, " + (missingSheets.size() + smallerCount) + " instead of "
				+ smallerCount);
		Iterator<CSSStyleSheet> it = missingSheets.iterator();
		while (it.hasNext()) {
			CSSStyleSheet sheet = it.next();
			StringBuilder sb = new StringBuilder(128);
			sb.append("The sheet is not in ").append(rightSide).append(", href ").append(sheet.getHref());
			Node owner = sheet.getOwnerNode();
			if (owner != null) {
				sb.append(", owner: ").append(owner.getNodeName());
			}
			selectErrorTargetSheet(sheet, -2);
			writeError(sb.toString());
		}
	}

	@Override
	public void rightHasMoreSheets(List<CSSStyleSheet> missingSheets, int smallerCount) {
		writeError(rightSide + " has more style sheets, " + (missingSheets.size() + smallerCount) + " instead of "
				+ smallerCount);
		Iterator<CSSStyleSheet> it = missingSheets.iterator();
		while (it.hasNext()) {
			CSSStyleSheet sheet = it.next();
			StringBuilder sb = new StringBuilder(128);
			sb.append("The sheet is not in ").append(leftSide).append(", href ").append(sheet.getHref());
			Node owner = sheet.getOwnerNode();
			if (owner != null) {
				sb.append(", owner: ").append(owner.getNodeName());
			}
			selectErrorTargetSheet(sheet, -3);
			writeError(sb.toString());
		}
	}

	@Override
	public void mediaQueryError(Node ownerNode, CSSMediaException exception) {
		writeError("Media query error [node=" + ownerNode.getNodeName() + "]:", exception);
	}

	@Override
	public void linkedStyleError(Node ownerNode, String message) {
		writeError("Linked style error [node=" + ownerNode.getNodeName() + "]: " + message);
	}

	@Override
	public void linkedSheetError(Exception exception, CSSStyleSheet sheet) {
		writeError("Linked sheet error [href=" + sheet.getHref() + "]:", exception);
	}

	@Override
	public void inlineStyleError(CSSElement owner, Exception exception, String style) {
		writeError("Inline style error [style=" + style + "]: ", exception);
	}

	@Override
	public void inlineStyleError(CSSElement owner, StyleDeclarationErrorHandler styleHandler) {
		writeError(styleHandler.toString());
	}

	@Override
	public void computedStyleError(CSSElement element, String propertyName, CSSPropertyValueException ex) {
		String message = "Computed style error (" + element.getTagName() + " / " + propertyName + ": "
				+ ex.getValueText() + ").";
		Throwable cause = ex.getCause();
		if (cause != null) {
			writeError(message, cause);
		} else {
			writeError(message);
		}
	}

	@Override
	public void presentationalHintError(DOMElement element, DOMException ex) {
		writeError("Presentational hint error (" + element.getTagName() + ").", ex);
	}

	@Override
	public void ruleSelectorError(CSSStyleDeclarationRule stylerule, SelectorList selist, SelectorList otherSelist,
			String selectorText, int sheetIndex, int ruleIndex, AbstractCSSStyleSheet parent) {
		writeSerializationError("Selector reparse error in rule: " + ruleIndex + " in sheet " + parent.getHref() + ":");
		writeSerializationError("List 1 (CSSOM): " + stylerule.getSelectorText());
		writeSerializationError("List 2 (CSSOM): " + selectorText);
		writeSerializationError("List 1  (NSAC): " + selist.toString());
		writeSerializationError("List 2  (NSAC): " + otherSelist.toString());
	}

	@Override
	public void ioError(String href, IOException exception) {
		writeError("@import or @font-face I/O error, URI: " + href, exception);
	}

	@Override
	public void minifiedMissingProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValue) {
		writeMinificationError("******** Minification issue:");
		writeMinificationError("Property " + property + " with value '" + propertyValue
				+ "' found only in non-minified style rule " + ruleIndex + " in style sheet " + parent.getHref() + ":\n"
				+ cssText + "\nMinified: " + miniCssText);
	}

	@Override
	public void minifiedExtraProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValue) {
		writeMinificationError("******** Minification issue:");
		writeMinificationError("Property " + property + " with value '" + propertyValue
				+ "' found only in minified style rule " + ruleIndex + " in style sheet " + parent.getHref() + ":\n"
				+ cssText + "\nMinified: " + miniCssText);
	}

	@Override
	public void minifiedDifferentValues(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValueText, String miniValueText) {
		String failinfo = property + " ('" + propertyValueText;
		failinfo += "' vs minified '" + miniValueText;
		failinfo += "').\nRule: " + ruleIndex + " in sheet " + parent.getHref() + ":\n" + cssText + "\nMinified: "
				+ miniCssText;
		writeMinificationError("******** Minification issue:");
		writeMinificationError("Different values found for property " + failinfo);
	}

	@Override
	public void minifiedParseErrors(String cssText, String miniCssText,
			StyleDeclarationErrorHandler styleDeclarationErrorHandler) {
		writeMinificationError("******** Minification issue:");
		writeMinificationError("Minified text has parse errors. Original: " + cssText);
		writeMinificationError("Problem: " + styleDeclarationErrorHandler.toString() + "\nMinified: " + miniCssText);
	}

	@Override
	public void reparsedMissingProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String reparsedCssText,
			String property, String propertyValue) {
		writeSerializationError("Re-parse check: property " + property + " with value '" + propertyValue
				+ "' found only in initial style rule " + ruleIndex + " in style sheet " + parent.getHref() + ":\n"
				+ cssText + "\nRe-parsed: " + reparsedCssText);
	}

	@Override
	public void reparsedExtraProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String reparsedCssText,
			String property, String propertyValue) {
		writeSerializationError("Re-parse check: property " + property + " with value '" + propertyValue
				+ "' found only in re-parsed style rule " + ruleIndex + " in style sheet " + parent.getHref() + ":\n"
				+ cssText + "\nRe-parsed: " + reparsedCssText);
	}

	@Override
	public void reparsedDifferentValues(CSSStyleSheet parent, int ruleIndex, String cssText, String reparsedCssText,
			String property, String propertyValueText, String reparsedValueText) {
		String failinfo = property + " ('" + propertyValueText;
		failinfo += "' vs re-parsed '" + reparsedValueText;
		failinfo += "').\nRule: " + ruleIndex + " in sheet " + parent.getHref() + ":\n" + cssText + "\nRe-parsed: "
				+ reparsedCssText;
		writeSerializationError("Re-parse check: different values found for property " + failinfo);
	}

	@Override
	public void ruleReparseIssue(CSSStyleSheet parent, int ruleIndex, String parsedText, String finalText) {
		writeSerializationError("Failed to re-parse rule [sheet=" + parent.getHref() + ", rule=" + ruleIndex + "]: " + parsedText
				+ "\nbecame: " + finalText);
	}

	@Override
	public void ruleReparseErrors(String parsedText, String finalText,
			StyleDeclarationErrorHandler styleDeclarationErrorHandler) {
		writeSerializationError("Reparsed text has errors. Original: " + parsedText);
		writeSerializationError("Problem: " + styleDeclarationErrorHandler.toString() + "\nResult: " + finalText);
	}

	@Override
	public void ruleReparseError(CSSStyleSheet parent, int ruleIndex, String parsedText, DOMException ex) {
		writeSerializationError("Failed to re-parse rule [sheet=" + parent.getHref() + ", rule=" + ruleIndex + "]: " + parsedText,
				ex);
	}

	@Override
	public void differentNodes(DOMElement parent, LinkedList<Node> nodediff) {
		StringBuilder buf = new StringBuilder(128);
		buf.append("Found ").append(nodediff.size()).append(" different nodes for parent: ").append(parent.toString())
				.append('\n');
		for (int i = 0; i < nodediff.size(); i++) {
			Node node = nodediff.get(i);
			buf.append("Node #").append(i).append(" (").append(node.getClass().getName()).append("): <")
					.append(node.getNodeName()).append('>');
			if (node.hasChildNodes()) {
				appendChildTextNodeValues(buf, node);
			}
		}
		writeError(buf.toString());
	}

	private void appendChildTextNodeValues(StringBuilder buf, Node parent) {
		NodeList list = parent.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if (node.getNodeType() == Node.TEXT_NODE) {
				buf.append(node.getNodeValue()).append('\n');
			}
		}
	}

	@Override
	public void unmatchedLeftSelector(StyleSheet sheet, int sheetIndex, DOMElement elm, String property,
			String propertyValue, LinkedList<Selector> selectorList, LinkedList<Selector> unmatched) {
		writeError("Failing due to issue in sheet:");
		selectErrorTargetSheet(sheet, sheetIndex);
		writeError("Failing due to issue with style on element: " + elm.getStartTag());
		writeError(rightSide + " comparison: on element <" + elm.getTagName() + ">, property " + property
				+ " with value '" + propertyValue + "' found only in first document's sheet " + sheetIndex);
		Iterator<Selector> it = unmatched.iterator();
		while (it.hasNext()) {
			writeError(rightSide + " does not match: " + it.next().toString());
		}
		writeError("Relevant selectors (" + leftSide + "): " + SampleSitesIT.printSelectorList(selectorList));
	}

	@Override
	public void unmatchedRightSelector(StyleSheet sheet, int sheetIndex, DOMElement elm, String property,
			String propertyValue, LinkedList<Selector> selectorList, LinkedList<Selector> unmatched) {
		writeError("Trouble with property specified in sheet (" + rightSide + "):");
		selectErrorTargetSheet(sheet, sheetIndex);
		writeError("Failing due to issue with style on element: " + elm.getStartTag());
		Iterator<Selector> it = unmatched.iterator();
		while (it.hasNext()) {
			writeError(leftSide + " does not match: " + it.next().toString());
		}
		writeError("Relevant selectors (" + rightSide + "): " + SampleSitesIT.printSelectorList(selectorList));
	}

	@Override
	public void differentComputedValues(DOMElement elm, String property, String valueText, String rightValueText) {
		writeError("Failing due to issue with computed style for element: " + elm.getStartTag());
		String failinfo = "Different values found for property " + property + " ('" + valueText + "' vs '"
				+ rightValueText + "')";
		writeError(failinfo);
	}

	@Override
	public void ruleErrors(CSSStyleSheet sheet, int sheetIndex, StyleDeclarationErrorHandler eh) {
		selectErrorTargetSheet(sheet, sheetIndex);
		if (eh instanceof DefaultStyleDeclarationErrorHandler) {
			StringBuilder buf = new StringBuilder(256);
			DefaultStyleDeclarationErrorHandler dseh = (DefaultStyleDeclarationErrorHandler) eh;
			dseh.errorSummary(buf);
			writeError(buf.toString());
		} else {
			writeError(eh.toString());
		}
	}

	@Override
	public void ruleWarnings(CSSStyleSheet sheet, int sheetIndex, StyleDeclarationErrorHandler eh) {
		if (eh instanceof DefaultStyleDeclarationErrorHandler) {
			selectWarningTargetSheet(sheet, sheetIndex);
			StringBuilder buf = new StringBuilder(200);
			DefaultStyleDeclarationErrorHandler dseh = (DefaultStyleDeclarationErrorHandler) eh;
			dseh.warningSummary(buf);
			if (buf.length() != 0) {
				writeWarning(buf.toString());
			}
		}
	}

	@Override
	public void omIssues(CSSStyleSheet sheet, int sheetIndex, SheetErrorHandler errHandler) {
		if (errHandler instanceof DefaultSheetErrorHandler) {
			DefaultSheetErrorHandler dseh = (DefaultSheetErrorHandler) errHandler;
			LinkedList<String> badAt = dseh.getBadAtRules();
			if (badAt != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				Iterator<String> it = badAt.iterator();
				while (it.hasNext()) {
					writeError("Error parsing at-rule: " + it.next());
				}
			}
			LinkedList<SACMediaList> badMedia = dseh.getBadMediaLists();
			if (badMedia != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				Iterator<SACMediaList> it = badMedia.iterator();
				while (it.hasNext()) {
					writeError("Error parsing media query: " + it.next().toString());
				}
			}
			LinkedList<String> badInline = dseh.getBadInlineStyles();
			if (badInline != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				Iterator<String> it = badInline.iterator();
				while (it.hasNext()) {
					writeError("Error parsing inline style: " + it.next());
				}
			}
			LinkedList<String> ignoredImports = dseh.getIgnoredImports();
			if (ignoredImports != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				Iterator<String> it = ignoredImports.iterator();
				while (it.hasNext()) {
					writeError("Ignored import rule for URI: " + it.next());
				}
			}
			LinkedList<String> unknownRules = dseh.getUnknownRules();
			if (unknownRules != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				Iterator<String> it = unknownRules.iterator();
				while (it.hasNext()) {
					writeError("Unknown rule: " + it.next());
				}
			}
			LinkedList<RuleParseException> rpe = dseh.getRuleParseErrors();
			if (rpe != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				Iterator<RuleParseException> it = rpe.iterator();
				while (it.hasNext()) {
					writeError("Rule parsing error: " + it.next().toString());
				}
			}
			LinkedList<String> emptyRules = dseh.getEmptyStyleRules();
			if (emptyRules != null) {
				selectWarningTargetSheet(sheet, sheetIndex);
				Iterator<String> it = emptyRules.iterator();
				while (it.hasNext()) {
					writeWarning("Empty style rule with selector: " + it.next());
				}
			}
		}
	}

	@Override
	public void sacIssues(CSSStyleSheet sheet, int sheetIndex, SACErrorHandler errHandler) {
		if (errHandler instanceof DefaultSheetErrorHandler) {
			DefaultSheetErrorHandler dseh = (DefaultSheetErrorHandler) errHandler;
			List<CSSParseException> sacErrors = dseh.getSacErrors();
			StringBuilder buf = new StringBuilder(512);
			if (sacErrors != null) {
				selectErrorTargetSheet(sheet, sheetIndex);
				for (int i = 0; i < sacErrors.size(); i++) {
					CSSParseException ex = sacErrors.get(i);
					ExtendedCSSRule rule = dseh.getRuleAtError(i);
					buf.append("SAC error at [").append(ex.getLineNumber()).append(':').append(ex.getColumnNumber()).append("] ")
							.append(ex.getMessage());
					if (rule != null) {
						buf.append(" --> ").append(rule.getCssText());
					}
					buf.append('\n');
					writeError(buf.toString());
					buf.setLength(0);
				}
			}
			List<CSSParseException> sacWarnings = dseh.getSacWarnings();
			if (sacWarnings != null) {
				selectWarningTargetSheet(sheet, sheetIndex);
				ListIterator<CSSParseException> it = sacWarnings.listIterator();
				while (it.hasNext()) {
					CSSParseException ex = it.next();
					buf.append("SAC warning at [").append(ex.getLineNumber()).append(':').append(ex.getColumnNumber())
							.append("] ").append(ex.getMessage());
					writeWarning(buf.toString());
					buf.setLength(0);
				}
			}
		}
	}

}
