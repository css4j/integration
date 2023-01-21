/*

 Copyright (c) 2017-2023, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

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
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.css.sac.Selector;
import org.w3c.css.sac.SelectorList;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.MediaList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.sf.carte.doc.DocumentException;
import io.sf.carte.doc.agent.DeviceFactory;
import io.sf.carte.doc.agent.IllegalOriginException;
import io.sf.carte.doc.agent.net.DefaultOriginPolicy;
import io.sf.carte.doc.agent.net.DefaultUserAgent;
import io.sf.carte.doc.dom.CSSDOMImplementation;
import io.sf.carte.doc.dom.DOMElement;
import io.sf.carte.doc.dom.HTMLDocument;
import io.sf.carte.doc.dom.HTMLElement;
import io.sf.carte.doc.dom.XMLDocumentBuilder;
import io.sf.carte.doc.dom4j.DOM4JUserAgent;
import io.sf.carte.doc.dom4j.DOM4JUserAgent.AgentXHTMLDocumentFactory.AgentXHTMLDocument;
import io.sf.carte.doc.style.css.CSSComputedProperties;
import io.sf.carte.doc.style.css.CSSDeclarationRule;
import io.sf.carte.doc.style.css.CSSDocument;
import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.CSSMediaException;
import io.sf.carte.doc.style.css.CSSStyleSheetList;
import io.sf.carte.doc.style.css.ErrorHandler;
import io.sf.carte.doc.style.css.ExtendedCSSRule;
import io.sf.carte.doc.style.css.ExtendedCSSRuleList;
import io.sf.carte.doc.style.css.ExtendedCSSStyleSheet;
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
import io.sf.carte.doc.style.css.om.DummyDeviceFactory;
import io.sf.carte.doc.style.css.om.GroupingRule;
import io.sf.carte.doc.style.css.om.StylableDocumentWrapper;
import io.sf.carte.doc.style.css.om.StyleSheetList;
import io.sf.carte.doc.style.css.om.TestCSSStyleSheetFactory;
import io.sf.carte.doc.style.css.parser.ParseHelper;
import io.sf.carte.doc.style.css.property.CSSPropertyValueException;
import io.sf.carte.doc.style.css.property.PropertyDatabase;
import io.sf.carte.doc.style.css.property.StyleValue;
import io.sf.carte.net.NetCache;
import io.sf.carte.util.Diff;
import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.sax.HtmlParser;

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
 * You can put as many websites as you want in "samplesites.txt" (or what you
 * configured in the {@code sites.file} property), and they can be commented out
 * with the '#' character at the beginning of the line. There is a
 * "samplesites.properties" that can be used for configuration, supporting the
 * following options:
 * </p>
 * 
 * <pre>
 * parser=&lt;fully-qualified-class-name-of-SAC-parser&gt;
 * fail-on-warning=&lt;true|false&gt;
 * cache.dir=/path/to/cache/directory
 * reporter=log|tree
 * sites.file=&lt;samplesites.txt&gt;
 * dom.strict-error-checking=true|false
 * parser.&lt;flag&gt;=true|false
 * </pre>
 * <ul>
 * <li>'parser': the qualified class name of the SAC/NSAC parser to be used in
 * the test.</li>
 * <li>'fail-on-warning': if set to true, a test shall fail even if only
 * warnings were logged.</li>
 * <li>'cache.dir': the path to the directory where the network cache can store
 * its files.</li>
 * <li>'reporter': the type of site error reporter to be used. Default is
 * 'log'.</li>
 * <li>'cache.refresh': if set to 'true', refreshes the files in the cache.
 * Default is 'false'.</li>
 * <li>'sites.file': the filename of the list of URLs. Default is
 * 'samplesites.txt'. Beware that this is a filename to be read from the
 * classpath, and not a filesystem path.</li>
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
	private static final boolean forceCacheRefresh;
	private static final String urlsFilename;

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
		String s = config.getProperty("cache.dir");
		if (s != null) {
			cachedir = new File(s);
			if (cachedir.isDirectory()) {
				netcache = new NetCache(cachedir);
			}
		}
		urlsFilename = config.getProperty("sites.file", "samplesites.txt");
		//
		s = config.getProperty("reporter", "log");
		if (s.equalsIgnoreCase("tree")) {
			errorReporterType = 1;
			if (cachedir != null) {
				File failfile = TreeSiteErrorReporter.getGlobalFile(cachedir);
				if (failfile.exists()) {
					File oldfailfile = new File(failfile.getAbsolutePath() + ".old");
					try {
						Files.move(failfile.toPath(), oldfailfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
					}
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
		if ("true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.STARHACK);
		}
		s = config.getProperty("parser.ievalues");
		if ("true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.IEVALUES);
		}
		s = config.getProperty("parser.ieprio");
		if ("true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.IEPRIO);
		}
		s = config.getProperty("parser.iepriochar");
		if ("true".equalsIgnoreCase(s)) {
			parserFlags.add(Parser2.Flag.IEPRIOCHAR);
		}
		failOnWarning = "true".equalsIgnoreCase(config.getProperty("fail-on-warning", "false"));
		forceCacheRefresh = "true".equalsIgnoreCase(config.getProperty("cache.refresh", "false"));
	}

	HTMLDocument document;
	CSSDocument dom4jdoc;

	MyDOMUserAgent agent;
	DOM4JUserAgent dom4jAgent;

	SiteErrorReporter reporter;

	public SampleSitesIT(String uri) throws IOException {
		super();
		agent = new MyDOMUserAgent();
		if (!strictErrorChecking) {
			agent.getDOMImplementation().setStrictErrorChecking(false);
		}
		dom4jAgent = new MyDOM4JUserAgent();
		log.info("Testing URL: " + uri);
		URL url = new URL(uri);

		if (errorReporterType == 0) {
			reporter = new LogSiteErrorReporter();
		} else {
			if (netcache == null) {
				throw new IOException("Netcache is not available.");
			}
			reporter = new TreeSiteErrorReporter();
		}
		reporter.startSiteReport(url);

		try {
			document = (HTMLDocument) agent.readURL(url);
		} catch (DocumentException e) {
			e.printStackTrace();
			reporter.fail("Error parsing native DOM", e);
		} catch (IOException e) {
			e.printStackTrace();
			reporter.fail("Error retrieving document at " + url.toString(), e);
		}
		try {
			dom4jdoc = dom4jAgent.readURL(url);
		} catch (DocumentException e) {
			e.printStackTrace();
			reporter.fail("Error parsing to DOM4J", e);
		}
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
		BufferedReader re = new BufferedReader(loadFileFromClasspath(urlsFilename));
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
		if (is != null) {
			return new InputStreamReader(is, StandardCharsets.UTF_8);
		}
		return null;
	}

	@Test
	public void testSampleSites() throws IOException, DocumentException, ParserConfigurationException {
		/*
		 * First, make a native-to-dom4j sheet comparison
		 */
		reporter.setSideDescriptions("Native implementation", "DOM4J backend");
		boolean result = false;
		try {
			result = compareSheets(dom4jdoc);
		} catch (DOMException e) {
			reporter.fail("Failed preparation of style sheets", e);
		}
		if (!result) {
			reporter.fail("Different style sheets in backend: DOM4J.");
		}
		// Check rules (re-parse cssText serialization, including optimized serialization)
		short reparseResult = checkRuleSerialization();
		// Compare to DOM4J computed styles
		HTMLElement html = document.getDocumentElement();
		CSSElement dom4jHtml = dom4jdoc.getDocumentElement();
		//
		int count = checkTree(html, dom4jHtml, dom4jdoc, "DOM4J", false);
		/*
		 * Now compare native DOM to DOM Wrapper computed styles
		 */
		WrapperFactory factory = new WrapperFactory();
		factory.getUserAgent().setOriginPolicy(DefaultOriginPolicy.getInstance());
		factory.setDefaultHTMLUserAgentSheet();
		// If DOM wrapper comparison fails, do not stop
		CSSDocument wrappedHtml = factory.createCSSDocument(document);
		reporter.setSideDescriptions("Native implementation", "DOM wrapper");
		try {
			result = compareSheets(wrappedHtml);
		} catch (DOMException e) {
			reporter.fail("Failed preparation of style sheets.", e);
		}
		String failMessage = null;
		if (!result) {
			failMessage = "Different style sheets in backend: DOM wrapper.";
		} else {
			checkTree(html, wrappedHtml.getDocumentElement(), wrappedHtml, "DOM wrapper", true);
		}
		// Check the computed styles
		try {
			document.setTargetMedium("screen");
		} catch (CSSMediaException e) {
		}
		if (count < 1000) {
			if (!computeStyles(html)) {
				reporter.fail("Error(s) computing styles.");
			}
		}
		// Report style issues
		if (document.hasStyleIssues()) {
			StyleSheetList list = document.getStyleSheets();
			if (findSheetErrors(list) || checkDocumentHandler(document) || failOnWarning) {
				failMessage = "Sheet parsing had errors.";
				result = false;
			}
		}
		// Now it is time to fail on deferred reparse issues
		if (reparseResult == CSSRule.STYLE_RULE) {
			reporter.fail("Issues with style rules were detected. Check the logs for details.");
		} else if (reparseResult != -1) {
			reporter.fail("Serialization issues were detected (at least for rule type " + reparseResult
					+ "). Check the logs for details.");
		}
		// DOM wrapper issues?
		if (!result) {
			reporter.fail(failMessage);
		}
		//
		reporter.close();
	}

	private boolean findSheetErrors(StyleSheetList list) {
		boolean hasErrors = false;
		int sz = list.getLength();
		for (int i = 0; i < sz; i++) {
			AbstractCSSStyleSheet sheet = list.item(i);
			DefaultSheetErrorHandler errHandler = (DefaultSheetErrorHandler) sheet.getErrorHandler();
			if (errHandler.hasSacErrors() || errHandler.hasSacWarnings() || sheet.hasRuleErrorsOrWarnings()) {
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
				if (errHandler.hasSacErrors() || errHandler.hasSacWarnings()) {
					reporter.sacIssues(sheet, i, errHandler);
					if (errHandler.hasSacErrors()) {
						hasErrors = true;
					}
				}
			}
			if (errHandler.hasOMErrors() || errHandler.hasOMWarnings()) {
				reporter.omIssues(sheet, i, errHandler);
				if (errHandler.hasOMErrors()) {
					hasErrors = true;
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
			CSSStyleSheetList<? extends ExtendedCSSRule> larger, smaller;
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
				ExtendedCSSStyleSheet<? extends ExtendedCSSRule> csssheet = larger.item(i);
				String href = csssheet.getHref();
				for (int j = 0; j < minlen; j++) {
					ExtendedCSSStyleSheet<? extends ExtendedCSSRule> othercsssheet = smaller.item(j);
					if (isSameSheet(csssheet, href, othercsssheet)) {
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
		// Compare the two sheet lists
		boolean ret = true;
		for (int i = 0; i < sheetlen; i++) {
			AbstractCSSStyleSheet csssheet = sheets.item(i);
			ExtendedCSSStyleSheet<? extends ExtendedCSSRule> othercsssheet = otherSheets.item(i);
			String href = csssheet.getHref();
			String ohref = othercsssheet.getHref();
			if (!Objects.equals(href, ohref)) {
				reporter.sideComparison("Different href in sheet " + i + ", " + href + " vs " + ohref);
				ret = false;
			}
			MediaList media = csssheet.getMedia();
			MediaList omedia = othercsssheet.getMedia();
			if (!Objects.equals(media, omedia)) {
				reporter.sideComparison("Different media in sheet " + i + ", " + media + " vs " + omedia);
				ret = false;
			}
			String title = csssheet.getTitle();
			String otitle = othercsssheet.getTitle();
			if (title == null) {
				title = "";
			}
			if (otitle == null) {
				otitle = "";
			}
			if (!title.equals(otitle)) {
				reporter.sideComparison("Different title in sheet " + i + ", " + title + " vs " + otitle);
				ret = false;
			}
			// Rules
			CSSRuleArrayList rules = csssheet.getCssRules();
			ExtendedCSSRuleList<? extends ExtendedCSSRule> orules = othercsssheet.getCssRules();
			ret = compareRuleLists(i, rules, orules, ret);
			if (!ret) {
				break;
			}
		}
		// Compare merged style sheet
		ExtendedCSSStyleSheet<AbstractCSSRule> merged = document.getStyleSheet();
		ExtendedCSSStyleSheet<? extends ExtendedCSSRule> otherMerged = otherDoc.getStyleSheet();
		ret = compareRuleLists(-1, merged.getCssRules(), otherMerged.getCssRules(), ret);
		return ret;
	}

	private boolean isSameSheet(ExtendedCSSStyleSheet<? extends ExtendedCSSRule> csssheet, String href,
			ExtendedCSSStyleSheet<? extends ExtendedCSSRule> othercsssheet) {
		if (!href.equals(document.getDocumentURI())) {
			return href.equals(othercsssheet.getHref());
		}
		ExtendedCSSRuleList<? extends ExtendedCSSRule> rules = csssheet.getCssRules();
		ExtendedCSSRuleList<? extends ExtendedCSSRule> oRules = othercsssheet.getCssRules();
		int n = rules.getLength();
		if (n != oRules.getLength()) {
			return false;
		}
		for (int i = 0; i < n; i++) {
			ExtendedCSSRule rule = rules.item(i);
			ExtendedCSSRule orule = oRules.item(i);
			if (!rule.equals(orule)) {
				return false;
			}
		}
		return true;
	}

	private boolean compareRuleLists(int sheetIndex, ExtendedCSSRuleList<? extends ExtendedCSSRule> rules,
			ExtendedCSSRuleList<? extends ExtendedCSSRule> orules, boolean ret) {
		int n = rules.getLength();
		if (n != orules.getLength()) {
			reporter.sideComparison(
					"Different number of rules in sheet " + sheetIndex + ", " + n + " vs " + orules.getLength());
			return false;
		}
		for (int j = 0; j < n; j++) {
			ExtendedCSSRule rule = rules.item(j);
			AbstractCSSRule orule = (AbstractCSSRule) orules.item(j);
			if (!rule.equals(orule)) {
				reporter.sideComparison("Different rules in sheet " + sheetIndex + ", rule " + j + ": "
						+ rule.getCssText() + " vs " + orule.getCssText());
				ret = false;
			}
		}
		return ret;
	}

	private boolean checkDocumentHandler(HTMLDocument document) {
		DefaultErrorHandler eh = (DefaultErrorHandler) document.getErrorHandler();
		if (eh.hasErrors()) {
			HashMap<Node, CSSMediaException> me = eh.getMediaErrors();
			if (me != null) {
				Iterator<Entry<Node, CSSMediaException>> it = me.entrySet().iterator();
				while (it.hasNext()) {
					Entry<Node, CSSMediaException> entry = it.next();
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
			HashMap<String, IOException> ruleio = eh.getIOErrors();
			if (ruleio != null) {
				Iterator<Entry<String, IOException>> it = ruleio.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, IOException> entry = it.next();
					String href = entry.getKey();
					reporter.ioError(href, entry.getValue());
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
			if (!checkSelectors(stylerule, sheetIndex, ruleIndex, sheet)) {
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
		CSSStyleDeclarationRule stylerule = rule.getParentStyleSheet().createStyleRule();
		String mini;
		try {
			mini = CSSOMBridge.getOptimizedCssText(style);
		} catch (Exception e) {
			reporter.minifiedParseErrors(style.getCssText(), e.getMessage(),
				stylerule.getStyleDeclarationErrorHandler());
			e.printStackTrace();
			return false;
		}
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
		StyleValue value, minivalue;
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
		return result;
	}

	private boolean checkSelectors(CSSStyleDeclarationRule stylerule, int sheetIndex, int ruleIndex,
			AbstractCSSStyleSheet sheet) {
		SelectorList selist = CSSOMBridge.getSelectorList(stylerule);
		String cssText = stylerule.getCssText();
		CSSStyleDeclarationRule orule = sheet.createStyleRule();
		orule.setCssText(cssText);
		SelectorList oselist = CSSOMBridge.getSelectorList(orule);
		boolean result = ParseHelper.equalSelectorList(selist, oselist);
		if (!result) {
			reporter.ruleSelectorError(stylerule, selist, oselist, orule.getSelectorText(), sheetIndex, ruleIndex,
					sheet);
		}
		return result;
	}

	private short checkGroupingRule(GroupingRule rule, int sheetIndex, int ruleIndex, AbstractCSSStyleSheet sheet)
			throws DOMException, IOException {
		short result = -1;
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
		StyleValue value, reparsedValue;
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
		if (!rule.equals(other) && !rule.getCssText().equals(other.getCssText())) {
			reporter.ruleReparseIssue(rule.getParentStyleSheet(), ruleIndex, parsedText, other.getCssText());
			return false;
		}
		return true;
	}

	private int checkTree(DOMElement elm, CSSElement otherdocElm, CSSDocument docToCompare, String backendName,
			boolean ignoreNonCssHints) throws IOException {
		NodeList list = elm.getChildNodes();
		NodeList dom4jList = otherdocElm.getChildNodes();
		int sz = list.getLength();
		if (sz != dom4jList.getLength()) {
			compareChildList(list, dom4jList, elm, backendName);
			reporter.fail("Different number of child at element " + elm.getTagName() + " for " + backendName);
			return 0;
		}
		//
		int count = 0;
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
				count++;
				count += checkTree((DOMElement) node, (CSSElement) dom4jNode, docToCompare, backendName, ignoreNonCssHints);
			}
		}
		//
		if (!compareComputedStyles(elm, otherdocElm, docToCompare, backendName, ignoreNonCssHints)) {
			reporter.fail("Different computed styles found");
			return 0;
		}
		return count;
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
			String[] different = diff.getDifferent();
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
					if (right == null && different == null) {
						reporter.computedStyleExtraProperties(
								"Tree comparison failed, first document had more properties", elm, left, backendName);
						return false;
					} else if (failinfo == null) {
						failinfo = "Tree comparison failed, first document had more properties";
					}
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
					if (different == null) {
						reporter.computedStyleExtraProperties("Tree comparison failed: " + backendName + " has more properties.", elm, right,
								backendName);
						return false;
					} else if (failinfo == null) {
						failinfo = "Tree comparison failed: " + backendName + " has more properties.";
					}
				}
			}
			if (different != null) {
				diff = ((BaseCSSStyleDeclaration) style).diff((BaseCSSStyleDeclaration) otherStyle);
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

	private boolean computeStyles(DOMElement element) {
		boolean retval = true;
		ComputedCSSStyle style = element.getComputedStyle(null);
		int len = style.getLength();
		for (int i = 0; i < len; i++) {
			String propertyName = style.item(i);
			StyleValue value = style.getPropertyCSSValue(propertyName);
			if (value == null && PropertyDatabase.getInstance().isKnownProperty(propertyName)) {
				CSSPropertyValueException pve = new CSSPropertyValueException("Null value.");
				reporter.computedStyleError(element, propertyName, pve);
				retval = false;
			}
		}
		ErrorHandler eh = element.getOwnerDocument().getErrorHandler();
		if (eh.hasComputedStyleErrors(element)) {
			HashMap<String, CSSPropertyValueException> csemap = ((DefaultErrorHandler) eh)
					.getComputedStyleErrors(element);
			if (csemap != null) {
				Iterator<Entry<String, CSSPropertyValueException>> it = csemap.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, CSSPropertyValueException> entry = it.next();
					reporter.computedStyleError(element, entry.getKey(), entry.getValue());
					retval = false;
				}
			}
			List<DOMException> hintlist = ((DefaultErrorHandler) eh).getHintErrors(element);
			if (hintlist != null) {
				Iterator<DOMException> it = hintlist.iterator();
				while (it.hasNext()) {
					DOMException ex = it.next();
					reporter.presentationalHintError(element, ex);
					retval = false;
				}
			}
		}
		Iterator<DOMElement> it = element.elementIterator();
		while (it.hasNext()) {
			DOMElement elm = it.next();
			if (!computeStyles(elm)) {
				retval = false;
			}
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

	private void compareChildList(NodeList domlist1, NodeList domlist2, DOMElement parent, String backendName)
			throws IOException {
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
			if (node.getNodeType() != nodeo.getNodeType() || !node.getNodeName().equals(nodeo.getNodeName())
					|| !Objects.equals(node.getNodeValue(), nodeo.getNodeValue())) {
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
				short type = nodeo.getNodeType();
				final String nodeval;
				if (type == Node.ELEMENT_NODE || (type == Node.TEXT_NODE && (nodeval = nodeo.getNodeValue()) != null
						&& nodeval.trim().length() != 0)) {
					nodediff.add(nodeo);
				}
			}
		}
		if (!nodediff.isEmpty()) {
			reporter.differentNodes(parent, nodediff);
			reporter.fail(backendName + " comparison: found " + nodediff.size() + " different node(s) for parent: "
					+ parent.getStartTag());
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

	private void checkOrigin(URL url) throws IllegalOriginException {
		String prot = url.getProtocol();
		if (!"https".equals(prot) && !"http".equals(prot)) {
			throw new IllegalOriginException("Invalid origin: " + url.toExternalForm());
		}
	}

	class MyDOMUserAgent extends DefaultUserAgent {
		MyDOMUserAgent() {
			super(parserFlags, true);
			DeviceFactory deviceFactory = new DummyDeviceFactory();
			getDOMImplementation().setDeviceFactory(deviceFactory);
		}

		@Override
		protected URLConnection openConnection(URL url, long creationDate) throws IOException {
			checkOrigin(url);
			URLConnection ucon;
			if (netcache != null) {
				String hostname = url.getHost();
				String encUrl = encodeString(url.toExternalForm());
				if (forceCacheRefresh || !netcache.isCached(hostname, encUrl)) {
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
			checkOrigin(url);
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
			HtmlParser parser = new HtmlParser(XmlViolationPolicy.ALTER_INFOSET);
			parser.setReportingDoctype(true);
			parser.setCommentPolicy(XmlViolationPolicy.ALLOW);
			parser.setXmlnsPolicy(XmlViolationPolicy.ALLOW);
			XMLDocumentBuilder builder = new XMLDocumentBuilder(getXHTMLDocumentFactory());
			builder.setHTMLProcessing(true);
			builder.setXMLReader(parser);
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
				checkOrigin(url);
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
