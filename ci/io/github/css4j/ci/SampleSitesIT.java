/*

 Copyright (c) 2017-2019, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://carte.sourceforge.io/LICENSE.txt

 */

package io.github.css4j.ci;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.css.sac.Selector;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSStyleSheet;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.sf.carte.doc.DocumentException;
import io.sf.carte.doc.agent.net.DefaultOriginPolicy;
import io.sf.carte.doc.agent.net.DefaultUserAgent;
import io.sf.carte.doc.dom.CSSDOMImplementation;
import io.sf.carte.doc.dom.DOMElement;
import io.sf.carte.doc.dom.HTMLDocument;
import io.sf.carte.doc.dom.HTMLElement;
import io.sf.carte.doc.dom4j.DOM4JUserAgent;
import io.sf.carte.doc.dom4j.DOM4JUserAgent.AgentXHTMLDocumentFactory.AgentXHTMLDocument;
import io.sf.carte.doc.style.css.CSSComputedProperties;
import io.sf.carte.doc.style.css.CSSDeclarationRule;
import io.sf.carte.doc.style.css.CSSDocument;
import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.CSSStyleSheetList;
import io.sf.carte.doc.style.css.ExtendedCSSRule;
import io.sf.carte.doc.style.css.StyleDeclarationErrorHandler;
import io.sf.carte.doc.style.css.nsac.Parser2;
import io.sf.carte.doc.style.css.om.AbstractCSSRule;
import io.sf.carte.doc.style.css.om.AbstractCSSStyleSheet;
import io.sf.carte.doc.style.css.om.BaseCSSDeclarationRule;
import io.sf.carte.doc.style.css.om.BaseCSSStyleDeclaration;
import io.sf.carte.doc.style.css.om.BaseCSSStyleSheet;
import io.sf.carte.doc.style.css.om.CSSOMBridge;
import io.sf.carte.doc.style.css.om.CSSRuleArrayList;
import io.sf.carte.doc.style.css.om.CSSStyleDeclarationRule;
import io.sf.carte.doc.style.css.om.ComputedCSSStyle;
import io.sf.carte.doc.style.css.om.DOMCSSStyleSheetFactory;
import io.sf.carte.doc.style.css.om.DefaultErrorHandler;
import io.sf.carte.doc.style.css.om.DefaultSheetErrorHandler;
import io.sf.carte.doc.style.css.om.GroupingRule;
import io.sf.carte.doc.style.css.om.StylableDocumentWrapper;
import io.sf.carte.doc.style.css.om.StyleSheetList;
import io.sf.carte.doc.style.css.om.TestCSSStyleSheetFactory;
import io.sf.carte.doc.style.css.property.AbstractCSSValue;
import io.sf.carte.doc.xml.dtd.DefaultEntityResolver;
import io.sf.carte.net.NetCache;
import io.sf.carte.util.Diff;
import nu.validator.htmlparser.dom.HtmlDocumentBuilder;

/**
 * 
 * This test is intended for integration testing purposes. For each URL in the
 * "samplesites.txt" file, it fetches the document and its style sheets,
 * computing styles for each element with the native implementation, the DOM
 * wrapper and the DOM4J backend, looking for errors and comparing the results.
 * If there are errors or differences in the styles computed by the two
 * implementations, they are reported and the test fails. Also verifies that the
 * <code>cssText</code> serializations for rules can be re-parsed to an
 * identical rule.
 * <p>
 * You can put as many websites as you want in "samplesites.txt", and they can
 * be commented out with the '#' character at the beginning of the line. There
 * is a "samplesites.properties" that can be used for configuration, supporting
 * the following options:
 * </p>
 * 
 * <pre>
 * parser=&lt;fully-qualified-class-name-of-SAC-parser&gt;
 * fail-on-warning=&lt;true|false&gt;
 * cachedir=/path/to/cache/directory
 * reporter=log|tree
 * dom.strict-error-checking=true|false
 * parser.&lt;flag&gt;=true|false
 * </pre>
 * <ul>
 * <li>'parser': the qualified class name of the SAC/NSAC parser to be used in
 * the test.</li>
 * <li>'fail-on-warning': if set to true, a test shall fail even if only
 * warnings were logged.</li>
 * <li>'cachedir': the path to the directory where the network cache can store
 * its files.</li>
 * <li>'reporter': the type of site error reporter to be used. Default is
 * 'log'.</li>
 * <li>'dom.strict-error-checking': set strict error checking at the DOM
 * implementation. Default is 'true'.</li>
 * <li>'parser.&lt;flag&gt;': to set the relevant NSAC parser flags.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class SampleSitesIT {

	final static Logger log = LoggerFactory.getLogger(SampleSitesIT.class.getName());

	private static NetCache netcache = null;
	private static final boolean strictErrorChecking;
	private static final boolean failOnWarning;
	private static final int errorReporterType;

	private static final EnumSet<Parser2.Flag> parserFlags = EnumSet.noneOf(Parser2.Flag.class);

	static {
		Properties config = new Properties();
		Reader re = loadFileFromClasspath("samplesites.properties");
		try {
			config.load(re);
			re.close();
			System.setProperty("org.w3c.css.sac.parser",
					config.getProperty("parser", "io.sf.carte.doc.style.css.parser.CSSParser"));
		} catch (IOException e) {
			TestCSSStyleSheetFactory.setTestSACParser();
		}
		File cachedir = null;
		String s = config.getProperty("cachedir");
		if (s != null) {
			cachedir = new File(s);
			if (cachedir.isDirectory()) {
				netcache = new NetCache(cachedir);
			}
		}
		s = config.getProperty("reporter", "log");
		if (s.equalsIgnoreCase("tree")) {
			errorReporterType = 1;
			if (cachedir != null) {
				File failfile = TreeSiteErrorReporter.getGlobalFile(cachedir);
				if (failfile.exists()) {
					File oldfailfile = new File(failfile.getAbsolutePath() + ".old");
					try {
						Files.copy(failfile.toPath(), oldfailfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
					}
					failfile.delete();
				}
			}
		} else {
			errorReporterType = 0;
		}
		// DOM error checking
		s = config.getProperty("dom.strict-error-checking");
		strictErrorChecking = s == null || "true".equalsIgnoreCase(s);
		// NSAC flags
		s = config.getProperty("parser.starhack");
		if (s != null && "true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.STARHACK);
		}
		s = config.getProperty("parser.ievalues");
		if (s != null && "true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.IEVALUES);
		}
		s = config.getProperty("parser.ieprio");
		if (s != null && "true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.IEPRIO);
		}
		s = config.getProperty("parser.iepriochar");
		if (s != null && "true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.IEPRIOCHAR);
		}
		failOnWarning = "true".equalsIgnoreCase(config.getProperty("fail-on-warning", "false"));
	}

	HTMLDocument document;
	CSSDocument dom4jdoc;

	MyDOMUserAgent agent;
	DOM4JUserAgent dom4jAgent;

	SiteErrorReporter reporter;

	public SampleSitesIT(String uri) throws IOException, DocumentException {
		super();
		agent = new MyDOMUserAgent();
		if (!strictErrorChecking) {
			agent.getDOMImplementation().setStrictErrorChecking(false);
		}
		dom4jAgent = new MyDOM4JUserAgent();
		log.info("Testing URL: " + uri);
		URL url = new URL(uri);
		document = (HTMLDocument) agent.readURL(url);
		dom4jdoc = dom4jAgent.readURL(url);
		if (errorReporterType == 0) {
			reporter = new LogSiteErrorReporter();
		} else {
			reporter = new TreeSiteErrorReporter();
		}
		reporter.startSiteReport(url);
	}

	/**
	 * Constructor intended for unit testing of this class.
	 */
	SampleSitesIT() {
		super();
		CSSDOMImplementation domimpl = new CSSDOMImplementation();
		document = (HTMLDocument) domimpl.createDocument(null, "html", null);
		document.setDocumentURI("http://www.example.com/dir/");
		agent = null;
		dom4jAgent = null;
		dom4jdoc = null;
		reporter = null;
	}

	@Parameters
	public static Collection<Object[]> data() throws IOException {
		List<Object[]> sites = new LinkedList<Object[]>();
		BufferedReader re = new BufferedReader(loadFileFromClasspath("samplesites.txt"));
		String site;
		while ((site = re.readLine()) != null) {
			if (site.length() != 0 && site.charAt(0) != '#') {
				sites.add(new Object[] { site });
			}
		}
		re.close();
		return sites;
	}

	private static Reader loadFileFromClasspath(final String filename) {
		InputStream is = java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<InputStream>() {
			@Override
			public InputStream run() {
				return this.getClass().getResourceAsStream(filename);
			}
		});
		Reader re = null;
		if (is != null) {
			try {
				re = new InputStreamReader(is, "utf-8");
			} catch (UnsupportedEncodingException e) {
				// Should not happen
			}
		}
		return re;
	}

	@Test
	public void testSampleSites() throws IOException, DocumentException, ParserConfigurationException {
		/*
		 * First, make a native-to-dom4j sheet comparison
		 */
		reporter.setSideDescriptions("Native implementation", "DOM4J backend");
		boolean compResult = false;
		try {
			compResult = compareSheets(dom4jdoc);
		} catch (DOMException e) {
			reporter.fail("Failed preparation of style sheets", e);
		}
		if (!compResult) {
			reporter.fail("Different number of style sheets in backend: DOM4J");
		}
		// Check rules (re-parse cssText serialization, including optimized serialization)
		short reparseResult = checkRuleSerialization();
		// Compare to DOM4J computed styles
		HTMLElement html = document.getDocumentElement();
		CSSElement dom4jHtml = dom4jdoc.getDocumentElement();
		checkTree(html, dom4jHtml, dom4jdoc, "DOM4J", false);
		/*
		 * Now compare native DOM to DOM Wrapper computed styles
		 */
		DocumentBuilder docbuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		docbuilder.setEntityResolver(new DefaultEntityResolver());
		WrapperFactory factory = new WrapperFactory();
		factory.getUserAgent().setOriginPolicy(DefaultOriginPolicy.getInstance());
		factory.setDefaultHTMLUserAgentSheet();
		CSSDocument wrappedHtml = factory.createCSSDocument(document);
		reporter.setSideDescriptions("Native implementation", "DOM wrapper");
		checkTree(html, wrappedHtml.getDocumentElement(), wrappedHtml, "DOM wrapper", true);
		// Report style issues
		if (document.hasStyleIssues()) {
			StyleSheetList list = document.getStyleSheets();
			if (findSheetErrors(list) || checkDocumentHandler(document) || failOnWarning) {
				reporter.fail("Sheet parsing had errors");
			}
		}
		if (reparseResult == CSSRule.STYLE_RULE) {
			reporter.fail("Issues with style rules were detected. Check the logs for details.");
		} else if (reparseResult != -1) {
			reporter.fail("Serialization issues were detected (at least for rule type " + reparseResult
					+ "). Check the logs for details.");
		}
		reporter.close();
	}

	private boolean findSheetErrors(StyleSheetList list) {
		boolean hasErrors = false;
		int sz = list.getLength();
		for (int i = 0; i < sz; i++) {
			AbstractCSSStyleSheet sheet = list.item(i);
			DefaultSheetErrorHandler errHandler = (DefaultSheetErrorHandler) sheet.getErrorHandler();
			hasErrors = errHandler.hasSacErrors();
			if (hasErrors || errHandler.hasSacWarnings() || sheet.hasRuleErrorsOrWarnings()) {
				if (sheet.hasRuleErrorsOrWarnings()) {
					CSSRuleArrayList rules = sheet.getCssRules();
					for (ExtendedCSSRule rule : rules) {
						if (rule.getType() == CSSRule.STYLE_RULE) {
							StyleDeclarationErrorHandler eh = ((CSSStyleDeclarationRule) rule)
									.getStyleDeclarationErrorHandler();
							if (eh.hasErrors()) {
								reporter.ruleErrors(sheet, i, eh);
								hasErrors = true;
							} else if (eh.hasWarnings()) {
								reporter.ruleWarnings(sheet, i, eh);
							}
						}
					}
				}
				if (errHandler.hasOMErrors() || errHandler.hasOMWarnings()) {
					reporter.omIssues(sheet, i, errHandler);
					if (errHandler.hasOMErrors()) {
						hasErrors = true;
					}
				}
				if (errHandler.hasSacErrors() || errHandler.hasSacWarnings()) {
					reporter.sacIssues(sheet, i, errHandler);
					if (errHandler.hasSacErrors()) {
						hasErrors = true;
					}
				}
			}
		}
		return hasErrors;
	}

	private boolean compareSheets(CSSDocument otherDoc) {
		StyleSheetList sheets = document.getStyleSheets();
		CSSStyleSheetList<? extends ExtendedCSSRule> otherSheets = otherDoc.getStyleSheets();
		int sheetlen = sheets.getLength();
		int othersheetlen = otherSheets.getLength();
		if (sheetlen != othersheetlen) {
			int maxlen, minlen;
			CSSStyleSheetList<?> larger, smaller;
			boolean leftHasMore;
			if (sheetlen > othersheetlen) {
				larger = sheets;
				smaller = otherSheets;
				maxlen = sheetlen;
				minlen = othersheetlen;
				leftHasMore = true;
			} else {
				larger = otherSheets;
				smaller = sheets;
				maxlen = othersheetlen;
				minlen = sheetlen;
				leftHasMore = false;
			}
			List<CSSStyleSheet> missingSheets = new LinkedList<CSSStyleSheet>();
			outerloop: for (int i = 0; i < maxlen; i++) {
				CSSStyleSheet csssheet = larger.item(i);
				String href = csssheet.getHref();
				for (int j = 0; j < minlen; j++) {
					CSSStyleSheet othercsssheet = smaller.item(j);
					if (href.equals(othercsssheet.getHref())) {
						continue outerloop;
					}
				}
				missingSheets.add(csssheet);
			}
			if (leftHasMore) {
				reporter.leftHasMoreSheets(missingSheets, smaller.getLength());
			} else {
				reporter.rightHasMoreSheets(missingSheets, smaller.getLength());
			}
			return false;
		}
		return true;
	}

	private boolean checkDocumentHandler(HTMLDocument document) {
		DefaultErrorHandler eh = (DefaultErrorHandler) document.getErrorHandler();
		if (eh.hasErrors()) {
			LinkedHashMap<Node, String> me = eh.getMediaErrors();
			if (me != null) {
				Iterator<Entry<Node, String>> it = me.entrySet().iterator();
				while (it.hasNext()) {
					Entry<Node, String> entry = it.next();
					reporter.mediaQueryError(entry.getKey(), entry.getValue());
				}
			}
			LinkedHashMap<Node, String> linkedErrors = eh.getLinkedStyleErrors();
			if (linkedErrors != null) {
				Iterator<Entry<Node, String>> it = linkedErrors.entrySet().iterator();
				while (it.hasNext()) {
					Entry<Node, String> esEntry = it.next();
					reporter.linkedStyleError(esEntry.getKey(), esEntry.getValue());
				}
			}
			LinkedHashMap<Exception, String> inlineErrors = eh.getInlineStyleErrors();
			if (inlineErrors != null) {
				Iterator<Entry<Exception, String>> it = inlineErrors.entrySet().iterator();
				while (it.hasNext()) {
					Entry<Exception, String> eoEntry = it.next();
					reporter.inlineStyleError(null, eoEntry.getKey(), eoEntry.getValue());
				}
			}
			LinkedHashMap<Exception, CSSStyleSheet> linkedSheetErrs = eh.getLinkedSheetErrors();
			if (linkedSheetErrs != null) {
				Iterator<Entry<Exception, CSSStyleSheet>> it = linkedSheetErrs.entrySet().iterator();
				while (it.hasNext()) {
					Entry<Exception, CSSStyleSheet> eoEntry = it.next();
					reporter.linkedSheetError(eoEntry.getKey(), eoEntry.getValue());
				}
			}
			Set<CSSElement> owners = eh.getInlineStyleOwners();
			if (owners != null) {
				Iterator<CSSElement> it = owners.iterator();
				while (it.hasNext()) {
					CSSElement owner = it.next();
					StyleDeclarationErrorHandler styleHandler = eh.getInlineStyleErrorHandler(owner);
					if (styleHandler.hasErrors()) {
						reporter.inlineStyleError(owner, styleHandler);
					}
				}
			}
		}
		return false;
	}

	private short checkRuleSerialization() throws DOMException, IOException {
		short result = -1;
		StyleSheetList sheets = document.getStyleSheets();
		int len = sheets.getLength();
		for (int i = 0; i < len; i++) {
			AbstractCSSStyleSheet sheet = sheets.item(i);
			CSSRuleArrayList rules = sheet.getCssRules();
			short ruleListResult = checkRuleListSerialization(rules, i, sheet);
			if (ruleListResult != -1) {
				result = ruleListResult;
			}
		}
		return result;
	}

	private short checkRuleListSerialization(CSSRuleArrayList rules, int sheetIndex, AbstractCSSStyleSheet sheet)
			throws DOMException, IOException {
		short result = -1;
		int rulen = rules.getLength();
		for (int j = 0; j < rulen; j++) {
			AbstractCSSRule rule = rules.item(j);
			short ruleResult = checkRuleSerialization(rule, sheetIndex, j, sheet);
			if (ruleResult != -1) {
				result = ruleResult;
			}
		}
		return result;
	}

	private short checkRuleSerialization(AbstractCSSRule rule, int sheetIndex, int ruleIndex,
			AbstractCSSStyleSheet sheet) throws DOMException, IOException {
		short result = -1;
		short ruleType = rule.getType();
		if (ruleType == CSSRule.STYLE_RULE) {
			CSSStyleDeclarationRule stylerule = (CSSStyleDeclarationRule) rule;
			if (!checkMinification(stylerule, sheetIndex, ruleIndex)) {
				result = ruleType;
			}
			if (!checkDeclarationRule(stylerule, sheetIndex, ruleIndex, sheet, rule.getCssText())) {
				result = ruleType;
			}
			if (!checkDeclarationRule(stylerule, sheetIndex, ruleIndex, sheet, rule.getMinifiedCssText())) {
				result = ruleType;
			}
		} else if (rule instanceof BaseCSSDeclarationRule) {
			BaseCSSDeclarationRule declrule = (BaseCSSDeclarationRule) rule;
			if (!checkDeclarationRule(declrule, sheetIndex, ruleIndex, sheet, rule.getCssText())) {
				result = ruleType;
			}
			if (!checkDeclarationRule(declrule, sheetIndex, ruleIndex, sheet, rule.getMinifiedCssText())) {
				result = ruleType;
			}
		} else if (rule instanceof GroupingRule) {
			short groupResult = checkGroupingRule((GroupingRule) rule, sheetIndex, ruleIndex, sheet);
			if (groupResult != -1) {
				result = groupResult;
			}
		} else if (ruleType != ExtendedCSSRule.NAMESPACE_RULE) {
			if (!checkRule(rule, sheetIndex, ruleIndex, sheet)) {
				result = ruleType;
			}
		}
		return result;
	}

	private boolean checkMinification(CSSStyleDeclarationRule rule, int sheetIndex, int ruleIndex) {
		boolean result = true;
		BaseCSSStyleDeclaration style = (BaseCSSStyleDeclaration) rule.getStyle();
		String mini = CSSOMBridge.getOptimizedCssText(style);
		CSSStyleDeclarationRule stylerule = rule.getParentStyleSheet().createCSSStyleRule();
		BaseCSSStyleDeclaration ministyle = (BaseCSSStyleDeclaration) stylerule.getStyle();
		try {
			ministyle.setCssText(mini);
		} catch (DOMException e) {
			reporter.ruleReparseIssue(rule.getParentStyleSheet(), ruleIndex, mini, e.getMessage());
			return false;
		}
		if (!style.equals(ministyle)
				&& reportMinifiedStyleDiff(rule.getParentStyleSheet(), ruleIndex, style, ministyle, mini)) {
			result = false;
		}
		if (stylerule.getStyleDeclarationErrorHandler().hasErrors() && mini.indexOf('\ufffd') == -1) {
			reporter.minifiedParseErrors(style.getCssText(), mini, stylerule.getStyleDeclarationErrorHandler());
			result = false;
		}
		return result;
	}

	private boolean reportMinifiedStyleDiff(CSSStyleSheet parent, int ruleIndex, BaseCSSStyleDeclaration style,
			BaseCSSStyleDeclaration otherstyle, String serializedText) {
		boolean foundDiff = false;
		Diff<String> diff = style.diff(otherstyle);
		String[] left = diff.getLeftSide();
		String[] right = diff.getRightSide();
		String[] different = diff.getDifferent();
		if (left != null) {
			for (int k = 0; k < left.length; k++) {
				String property = left[k];
				if (property.charAt(0) != '*' && property.charAt(property.length() - 1) != 0xfffd) {
					reporter.minifiedMissingProperty(parent, ruleIndex, style.getCssText(), serializedText, property,
							style.getPropertyValue(property));
					foundDiff = true;
				}
			}
		}
		if (right != null) {
			for (int k = 0; k < right.length; k++) {
				reporter.minifiedExtraProperty(parent, ruleIndex, style.getCssText(), serializedText, right[k],
						style.getPropertyValue(right[k]));
			}
			foundDiff = true;
		}
		ValueComparator comp = new ValueComparator(style);
		AbstractCSSValue value, minivalue;
		if (different != null) {
			for (int k = 0; k < different.length; k++) {
				String property = different[k];
				if (!comp.isNotDifferent(property, value = style.getPropertyCSSValue(property),
						minivalue = otherstyle.getPropertyCSSValue(property))) {
					String valueText = value.getCssText();
					String prio = style.getPropertyPriority(property);
					if (prio.length() != 0) {
						valueText += "!" + prio;
					}
					String miniValueText = minivalue.getCssText();
					prio = otherstyle.getPropertyPriority(property);
					if (prio.length() != 0) {
						miniValueText += "!" + prio;
					}
					reporter.minifiedDifferentValues(parent, ruleIndex, style.getCssText(), serializedText, property,
							valueText, miniValueText);
					foundDiff = true;
				}
			}
		}
		return foundDiff;
	}

	private boolean checkDeclarationRule(BaseCSSDeclarationRule rule, int sheetIndex, int ruleIndex,
			AbstractCSSStyleSheet sheet, String serializedText) {
		boolean result = true;
		BaseCSSStyleDeclaration style = (BaseCSSStyleDeclaration) rule.getStyle();
		CSSDeclarationRule other = rule.clone(sheet);
		other.getStyle().setCssText("");
		try {
			other.setCssText(serializedText);
		} catch (DOMException e) {
			reporter.ruleReparseIssue(rule.getParentStyleSheet(), ruleIndex, serializedText, e.getMessage());
			return false;
		}
		BaseCSSStyleDeclaration otherStyle = (BaseCSSStyleDeclaration) other.getStyle();
		if (!style.equals(otherStyle)
				&& reportStyleDiff(rule.getParentStyleSheet(), ruleIndex, style, otherStyle, serializedText)) {
			result = false;
		}
		if (!rule.getStyleDeclarationErrorHandler().hasErrors()
				&& other.getStyleDeclarationErrorHandler().hasErrors()) {
			String original = style.getCssText();
			if (original.indexOf('\ufffd') == -1) {
				reporter.ruleReparseErrors(original, otherStyle.getCssText(), rule.getStyleDeclarationErrorHandler());
				result = false;
			}
		}
		if (rule.getType() != CSSRule.STYLE_RULE && result && serializedText.indexOf('\ufffd') == -1) {
			String reparsed = other.getCssText();
			if (!checkRulePreamble(sheet, sheetIndex, ruleIndex, serializedText, reparsed)) {
				result = false;
			}
		}
		return result;
	}

	private boolean checkRulePreamble(AbstractCSSStyleSheet sheet, int sheetIndex, int ruleIndex, String serializedText,
			String reparsed) {
		boolean result = true;
		int lbi = serializedText.indexOf('{');
		int lbirep = reparsed.indexOf('{');
		if (lbi != -1) {
			if (lbirep == -1) {
				result = false;
			} else {
				String preamble = serializedText.substring(0, lbi).trim();
				String repPreamble = reparsed.substring(0, lbirep).trim();
				if (!preamble.equalsIgnoreCase(repPreamble)) {
					reporter.ruleReparseIssue(sheet, ruleIndex, serializedText, reparsed);
					result = false;
				}
			}
		}
		return result;
	}

	private short checkGroupingRule(GroupingRule rule, int sheetIndex, int ruleIndex, AbstractCSSStyleSheet sheet)
			throws DOMException, IOException {
		short result = -1;
		String serializedText = rule.getCssText();
		AbstractCSSRule other = rule.clone(sheet);
		if (!checkRulePreamble(sheet, sheetIndex, ruleIndex, serializedText, other.getCssText())) {
			result = rule.getType();
		}
		short ruleListResult = checkRuleListSerialization(rule.getCssRules(), sheetIndex, sheet);
		if (ruleListResult != -1) {
			result = ruleListResult;
		}
		return result;
	}

	private boolean reportStyleDiff(CSSStyleSheet parent, int ruleIndex, BaseCSSStyleDeclaration style,
			BaseCSSStyleDeclaration otherStyle, String parsedText) {
		Diff<String> diff = style.diff(otherStyle);
		if (!diff.hasDifferences()) {
			return false;
		}
		boolean result = false;
		String[] left = diff.getLeftSide();
		String[] right = diff.getRightSide();
		String[] different = diff.getDifferent();
		if (left != null) {
			for (int k = 0; k < left.length; k++) {
				String property = left[k];
				if (property.charAt(0) != '*' && property.charAt(property.length() - 1) != 0xfffd) {
					reporter.reparsedMissingProperty(parent, ruleIndex, parsedText, otherStyle.getCssText(), left[k],
							style.getPropertyValue(left[k]));
					result = true;
				}
			}
		}
		if (right != null) {
			for (int k = 0; k < right.length; k++) {
				reporter.reparsedExtraProperty(parent, ruleIndex, parsedText, otherStyle.getCssText(), right[k],
						style.getPropertyValue(right[k]));
			}
			result = true;
		}
		AbstractCSSValue value, reparsedValue;
		if (different != null) {
			ValueComparator comp = new ValueComparator(style);
			for (int k = 0; k < different.length; k++) {
				String property = different[k];
				if (!comp.isNotDifferent(property, value = style.getPropertyCSSValue(property),
						reparsedValue = otherStyle.getPropertyCSSValue(property))) {
					String valueText = value.getCssText();
					String prio = style.getPropertyPriority(property);
					if (prio.length() != 0) {
						valueText += "!" + prio;
					}
					String reparsedValueText = reparsedValue.getCssText();
					prio = otherStyle.getPropertyPriority(property);
					if (prio.length() != 0) {
						reparsedValueText += "!" + prio;
					}
					reporter.reparsedDifferentValues(parent, ruleIndex, parsedText, otherStyle.getCssText(), property,
							valueText, reparsedValueText);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean checkRule(AbstractCSSRule rule, int sheetIndex, int ruleIndex, AbstractCSSStyleSheet sheet) {
		AbstractCSSRule other = rule.clone(sheet);
		String parsedText = rule.getCssText();
		try {
			other.setCssText(parsedText);
		} catch (DOMException e) {
			reporter.ruleReparseIssue(rule.getParentStyleSheet(), ruleIndex, parsedText, e.getMessage());
			return false;
		}
		if (!rule.equals(other)) {
			reporter.ruleReparseIssue(rule.getParentStyleSheet(), ruleIndex, parsedText, other.getCssText());
			return false;
		}
		return true;
	}

	private void checkTree(DOMElement elm, CSSElement otherdocElm, CSSDocument docToCompare, String backendName,
			boolean ignoreNonCssHints) throws IOException {
		if (!compareComputedStyles(elm, otherdocElm, docToCompare, backendName, ignoreNonCssHints)) {
			reporter.fail("Different computed styles found");
		}
		NodeList list = elm.getChildNodes();
		NodeList dom4jList = otherdocElm.getChildNodes();
		int sz = list.getLength();
		if (sz != dom4jList.getLength()) {
			compareChildList(list, dom4jList, elm);
			reporter.fail("Different number of child at element " + elm.getTagName() + " for " + backendName);
		}
		int delta = 0;
		for (int i = 0; i < sz; i++) {
			Node node = list.item(i);
			Node dom4jNode = dom4jList.item(i + delta);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				while (dom4jNode != null && dom4jNode.getNodeType() != Node.ELEMENT_NODE) {
					delta++;
					dom4jNode = dom4jList.item(i + delta);
				}
				assertNotNull(dom4jNode);
				if (!node.getLocalName().equalsIgnoreCase(dom4jNode.getLocalName())) {
					assertEquals(node.getLocalName(), dom4jNode.getLocalName());
				}
				checkTree((DOMElement) node, (CSSElement) dom4jNode, docToCompare, backendName, ignoreNonCssHints);
			}
		}
	}

	private boolean compareComputedStyles(DOMElement elm, CSSElement otherdocElm, CSSDocument docToCompare,
			String backendName, boolean ignoreNonCssHints) {
		ComputedCSSStyle style = elm.getComputedStyle(null);
		CSSComputedProperties otherStyle = otherdocElm.getComputedStyle(null);
		boolean retval = true;
		String failinfo = null;
		if (!style.equals(otherStyle)) {
			Diff<String> diff = ((BaseCSSStyleDeclaration) style).diff((BaseCSSStyleDeclaration) otherStyle);
			String[] left = diff.getLeftSide();
			String[] right = diff.getRightSide();
			CSSStyleSheetList<?> sheets;
			if (left != null) {
				// Report only if non-CSS presentational hints cannot be the reason
				if (!ignoreNonCssHints || elm.hasPresentationalHints() == otherdocElm.hasPresentationalHints()) {
					sheets = document.getStyleSheets();
					for (int i = 0; i < left.length; i++) {
						String property = left[i];
						if (property.charAt(0) != '*' && property.charAt(property.length() - 1) != 0xfffd) {
							for (int j = 0; j < sheets.getLength(); j++) {
								String value = style.getPropertyValue(property);
								CSSStyleSheet sheet = sheets.item(j);
								Selector[] sel = ((BaseCSSStyleSheet) sheet).getSelectorsForPropertyValue(property,
										value);
								if (sel != null) {
									LinkedList<Selector> selectorList = new LinkedList<Selector>();
									LinkedList<Selector> unmatched = unmatchedSelectors(sel, elm, otherdocElm,
											selectorList);
									reporter.unmatchedLeftSelector(sheet, j, elm, property, value, selectorList,
											unmatched);
									failinfo = backendName + " comparison: on element <" + elm.getTagName()
											+ ">, property " + property + " with value '" + value
											+ "' found only in first document's sheet " + j + ", selectors "
											+ printSelectorList(selectorList);
									retval = false;
								} else {
									sel = ((BaseCSSStyleSheet) sheet).getSelectorsForProperty(property);
									if (sel != null) {
										LinkedList<Selector> selectorList = new LinkedList<Selector>();
										LinkedList<Selector> unmatched = unmatchedSelectors(sel, elm, otherdocElm,
												selectorList);
										if (!unmatched.isEmpty()) {
											reporter.unmatchedRightSelector(sheet, j, elm, property, value,
													selectorList, unmatched);
											retval = false;
										}
									}
								}
							}
						}
					}
				}
				if (!retval) {
					reporter.fail("Tree comparison failed, first document had more properties", elm, left, backendName);
				}
			}
			if (right != null) {
				sheets = docToCompare.getStyleSheets();
				for (int i = 0; i < right.length; i++) {
					for (int j = 0; j < sheets.getLength(); j++) {
						CSSStyleSheet sheet = sheets.item(j);
						String value = otherStyle.getPropertyValue(right[i]);
						Selector[] sel = ((BaseCSSStyleSheet) sheet).getSelectorsForPropertyValue(right[i], value);
						if (sel != null) {
							LinkedList<Selector> selectorList = new LinkedList<Selector>();
							LinkedList<Selector> unmatched = unmatchedSelectors(sel, otherdocElm, elm, selectorList);
							reporter.unmatchedRightSelector(sheet, j, elm, right[i], value, selectorList, unmatched);
							failinfo = backendName + " comparison: on element <" + otherdocElm.getTagName()
									+ ">, property " + right[i] + " with value '" + value
									+ "' found only in second document's sheet " + j + ", selectors "
									+ printSelectorList(selectorList);
							retval = false;
						} else {
							sel = ((BaseCSSStyleSheet) sheet).getSelectorsForProperty(right[i]);
							if (sel != null) {
								LinkedList<Selector> selectorList = new LinkedList<Selector>();
								LinkedList<Selector> unmatched = unmatchedSelectors(sel, otherdocElm, elm,
										selectorList);
								if (!unmatched.isEmpty()) {
									reporter.unmatchedRightSelector(sheet, j, elm, right[i], value, selectorList,
											unmatched);
									retval = false;
								}
							}
						}
					}
				}
				if (!retval) {
					reporter.fail("Tree comparison failed: " + backendName + " has more properties.", elm, right,
							backendName);
				}
			}
			String[] different = diff.getDifferent();
			if (different != null) {
				sheets = document.getStyleSheets();
				CSSStyleSheetList<? extends ExtendedCSSRule> otherSheets = docToCompare.getStyleSheets();
				for (int i = 0; i < different.length; i++) {
					String property = different[i];
					String value = style.getPropertyValue(property);
					for (int j = 0; j < sheets.getLength(); j++) {
						CSSStyleSheet sheet = sheets.item(j);
						Selector[] sel = ((BaseCSSStyleSheet) sheet).getSelectorsForPropertyValue(property, value);
						if (sel != null) {
							LinkedList<Selector> selectorList = new LinkedList<Selector>();
							LinkedList<Selector> unmatched = unmatchedSelectors(sel, elm, otherdocElm, selectorList);
							reporter.unmatchedLeftSelector(sheet, j, elm, property, value, selectorList, unmatched);
							retval = false;
						}
					}
					String othervalue = otherStyle.getPropertyValue(property);
					for (int j = 0; j < otherSheets.getLength(); j++) {
						CSSStyleSheet sheet = otherSheets.item(j);
						Selector[] sel = ((BaseCSSStyleSheet) sheet).getSelectorsForPropertyValue(property, othervalue);
						if (sel != null) {
							LinkedList<Selector> selectorList = new LinkedList<Selector>();
							LinkedList<Selector> unmatched = unmatchedSelectors(sel, otherdocElm, elm, selectorList);
							reporter.unmatchedRightSelector(sheet, j, elm, property, othervalue, selectorList,
									unmatched);
							retval = false;
						}
					}
					if (retval) {
						ValueComparator comp = new ValueComparator(style);
						if (!comp.isNotDifferent(property, style.getPropertyCSSValue(property),
								otherStyle.getPropertyCSSValue(property))) {
							String prio = style.getPropertyPriority(property);
							if (prio.length() != 0) {
								value += "!" + prio;
							}
							prio = otherStyle.getPropertyPriority(property);
							if (prio.length() != 0) {
								othervalue += "!" + prio;
							}
							reporter.differentComputedValues(elm, property, value, othervalue);
							if (failinfo != null) {
								failinfo = "Different values found for property " + property + " ('" + value + "' vs '"
										+ othervalue + "')";
							}
							retval = false;
						}
					}
				}
			}
		}
		if (failinfo != null) {
			reporter.fail(failinfo);
		}
		return retval;
	}

	private LinkedList<Selector> unmatchedSelectors(Selector[] sel, CSSElement elm, CSSElement otherdocElm,
			LinkedList<Selector> selectorList) {
		LinkedList<Selector> unmatched = new LinkedList<Selector>();
		for (int k = 0; k < sel.length; k++) {
			if (elm.getSelectorMatcher().matches(sel[k])) {
				selectorList.add(sel[k]);
				if (!otherdocElm.getSelectorMatcher().matches(sel[k])) {
					unmatched.add(sel[k]);
				}
			}
		}
		return unmatched;
	}

	private void compareChildList(NodeList domlist1, NodeList domlist2, DOMElement parent) throws IOException {
		int sz1 = domlist1.getLength();
		int sz2 = domlist2.getLength();
		NodeList list, other;
		int sz;
		if (sz1 > sz2) {
			list = domlist2;
			other = domlist1;
			sz = sz2;
		} else {
			list = domlist1;
			other = domlist2;
			sz = sz1;
		}
		int countdiff = Math.abs(sz2 - sz1);
		LinkedList<Node> nodediff = new LinkedList<Node>();
		int delta = 0;
		for (int i = 0; i < sz; i++) {
			Node node = list.item(i + delta);
			Node nodeo = other.item(i);
			if (node.getNodeType() != nodeo.getNodeType() || !node.getNodeName().equals(nodeo.getNodeName())) {
				nodediff.add(nodeo);
				if (countdiff != 0) {
					delta--;
				}
			}
		}
		countdiff += delta;
		if (countdiff != 0) {
			for (int i = 0; i < countdiff; i++) {
				Node nodeo = other.item(i + sz);
				if (nodeo.getNodeType() == Node.ELEMENT_NODE) {
					nodediff.add(nodeo);
				}
			}
		}
		if (!nodediff.isEmpty()) {
			reporter.differentNodes(parent, nodediff);
			reporter.fail("Found " + nodediff.size() + " different node(s) for parent: " + parent.getStartTag());
		}
	}

	static String printSelectorList(List<Selector> list) {
		if (list.size() == 0) {
			return "";
		}
		StringBuilder buf = new StringBuilder(list.size() * 16 + 32);
		buf.append(list.get(0).toString());
		for (int i = 1; i < list.size(); i++) {
			buf.append(',').append(list.get(i).toString());
		}
		return buf.toString();
	}

	public static File getHostDirectory(URL url) {
		return netcache.getHostDirectory(url);
	}

	static String encodeString(String s) {
		return DigestUtils.md5Hex(s);
	}

	class MyDOMUserAgent extends DefaultUserAgent {
		MyDOMUserAgent() {
			super(parserFlags, true);
		}

		@Override
		protected URLConnection openConnection(URL url, long creationDate) throws IOException {
			URLConnection ucon;
			if (netcache != null) {
				String hostname = url.getHost();
				String encUrl = encodeString(url.toExternalForm());
				if (!netcache.isCached(hostname, encUrl)) {
					ucon = super.openConnection(url, creationDate);
					netcache.cacheFile(url, encUrl, ucon);
				}
				return netcache.openConnection(hostname, encUrl);
			}
			return super.openConnection(url, creationDate);
		}

		@Override
		protected InputStream openInputStream(URLConnection con) throws IOException {
			return new BufferedInputStream(super.openInputStream(con), 65536);
		}

	}

	class MyDOM4JUserAgent extends DOM4JUserAgent {
		MyDOM4JUserAgent() {
			super(parserFlags, false);
			setOriginPolicy(DefaultOriginPolicy.getInstance());
		}

		@Override
		protected URLConnection openConnection(URL url, long creationDate) throws IOException {
			URLConnection ucon;
			if (netcache != null) {
				String hostname = url.getHost();
				String encUrl = encodeString(url.toExternalForm());
				if (!netcache.isCached(hostname, encUrl)) {
					ucon = super.openConnection(url, creationDate);
					netcache.cacheFile(url, encUrl, ucon);
				}
				return netcache.openConnection(hostname, encUrl);
			}
			return super.openConnection(url, creationDate);
		}

		@Override
		protected InputStream openInputStream(URLConnection con) throws IOException {
			return new BufferedInputStream(super.openInputStream(con), 65536);
		}

		@Override
		protected AgentXHTMLDocument parseDocument(Reader re) throws DocumentException, IOException {
			InputSource source = new InputSource(re);
			HtmlDocumentBuilder builder = new HtmlDocumentBuilder(getXHTMLDocumentFactory());
			try {
				return (AgentXHTMLDocument) builder.parse(source);
			} catch (SAXException e) {
				throw new DocumentException("Unable to parse document", e);
			}
		}
	}

	class WrapperFactory extends DOMCSSStyleSheetFactory {
		WrapperFactory() {
			super(parserFlags);
		}

		@Override
		public StylableDocumentWrapper createCSSDocument(Document document) {
			return new CIStylableDocumentWrapper(document);
		}

		private class CIStylableDocumentWrapper extends StylableDocumentWrapper {

			private long creationDate;

			public CIStylableDocumentWrapper(Document document) {
				super(document);
			}

			@Override
			protected DOMCSSStyleSheetFactory getStyleSheetFactory() {
				return WrapperFactory.this;
			}

			/**
			 * Opens a connection for the given URL.
			 * 
			 * @param url
			 *            the URL to open a connection to.
			 * @return the URL connection.
			 * @throws IOException
			 *             if the connection could not be opened.
			 */
			@Override
			public URLConnection openConnection(URL url) throws IOException {
				URLConnection ucon;
				if (netcache != null) {
					String hostname = url.getHost();
					String encUrl = encodeString(url.toExternalForm());
					if (!netcache.isCached(hostname, encUrl)) {
						ucon = getUserAgent().openConnection(url, creationDate);
						netcache.cacheFile(url, encUrl, ucon);
					}
					return netcache.openConnection(hostname, encUrl);
				}
				return getUserAgent().openConnection(url, creationDate);
			}

			/**
			 * Set the time at which this document was loaded from origin.
			 * 
			 * @param time
			 *            the time of loading, in milliseconds.
			 */
			@Override
			public void setLoadingTime(long time) {
				this.creationDate = time;
			}

		}

	}

}
