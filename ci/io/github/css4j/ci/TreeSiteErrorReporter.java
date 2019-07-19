/*

 Copyright (c) 2017-2019, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.List;
import java.util.ListIterator;

import org.dom4j.dom.DOMElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.css.sac.CSSParseException;
import org.w3c.dom.DOMException;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.StyleSheet;

import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.SACErrorHandler;
import io.sf.carte.doc.style.css.SheetErrorHandler;
import io.sf.carte.doc.style.css.om.DefaultSheetErrorHandler;

public class TreeSiteErrorReporter extends BaseSiteErrorReporter {

	final static Logger log = LoggerFactory.getLogger(TreeSiteErrorReporter.class.getName());

	private URL testedurl;
	private File hostdir;
	private String filename;
	private FileChannel mainchannel;
	private FileChannel minichannel;
	private PrintWriter mainwriter = null;
	private PrintWriter miniwriter = null;
	private PrintWriter serialwriter = null;
	private FileDescriptor mainfd;
	private FileDescriptor minifd;
	private int lastSheetIndex = -1;

	@Override
	public void startSiteReport(URL url) throws IOException {
		testedurl = url;
		hostdir = SampleSitesIT.getHostDirectory(url);
		filename = SampleSitesIT.encodeString(url.toExternalForm()).substring(0, 10);
		File mainfile = getMainFile();
		File minifile = getMinificationFile();
		File serialfile = getSerializationFile();
		if (mainfile.exists()) {
			Files.delete(mainfile.toPath());
		}
		if (minifile.exists()) {
			Files.delete(minifile.toPath());
		}
		if (serialfile.exists()) {
			Files.delete(serialfile.toPath());
		}
	}

	static File getGlobalFile(File cachedir) {
		return new File(cachedir, "fail.log");
	}

	File getMainFile() {
		return new File(hostdir, filename + ".log");
	}

	File getMinificationFile() {
		return new File(hostdir, filename + "-mini.err");
	}

	File getSerializationFile() {
		return new File(hostdir, filename + "-rule.err");
	}

	@Override
	public void fail(String message) {
		PrintWriter pw;
		try {
			pw = new PrintWriter(new FileOutputStream(getGlobalFile(hostdir.getParentFile()), true));
			pw.println(testedurl.toExternalForm());
			pw.println(message);
			pw.println();
			pw.close();
		} catch (FileNotFoundException e) {
			log.error("Unable to write to " + getGlobalFile(hostdir.getParentFile()).getAbsolutePath(), e);
		}
		super.fail(message);
	}

	@Override
	public void minifiedMissingProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValue) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeMinificationError("Minification issue in sheet at " + uri + "\npath: " + path);
		super.minifiedMissingProperty(parent, ruleIndex, cssText, miniCssText, property, propertyValue);
	}

	@Override
	public void minifiedExtraProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String miniCssText,
			String property, String propertyValue) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeMinificationError("Minification issue in sheet at " + uri + "\npath: " + path);
		super.minifiedExtraProperty(parent, ruleIndex, cssText, miniCssText, property, propertyValue);
	}

	@Override
	public void reparsedMissingProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String reparsedCssText,
			String property, String propertyValue) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeSerializationError("Reparse issue in sheet at " + uri + "\npath: " + path);
		super.reparsedMissingProperty(parent, ruleIndex, cssText, reparsedCssText, property, propertyValue);
	}

	@Override
	public void reparsedExtraProperty(CSSStyleSheet parent, int ruleIndex, String cssText, String reparsedCssText,
			String property, String propertyValue) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeSerializationError("Reparse issue in sheet at " + uri + "\npath: " + path);
		super.reparsedExtraProperty(parent, ruleIndex, cssText, reparsedCssText, property, propertyValue);
	}

	@Override
	public void reparsedDifferentValues(CSSStyleSheet parent, int ruleIndex, String cssText, String reparsedCssText,
			String property, String propertyValueText, String reparsedValueText) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeSerializationError("Reparse issue in sheet at " + uri + "\npath: " + path);
		super.reparsedDifferentValues(parent, ruleIndex, cssText, reparsedCssText, property, propertyValueText,
				reparsedValueText);
	}

	@Override
	public void ruleReparseIssue(CSSStyleSheet parent, int ruleIndex, String parsedText, String finalText) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeSerializationError("Reparse issue in sheet at " + uri + "\npath: " + path);
		super.ruleReparseIssue(parent, ruleIndex, parsedText, finalText);
	}

	@Override
	public void ruleReparseError(CSSStyleSheet parent, int ruleIndex, String parsedText, DOMException ex) {
		String uri = parent.getHref();
		String path = getLinkedSheetInternalPath(parent, uri);
		writeSerializationError("Reparse error in sheet at " + uri + "\npath: " + path);
		super.ruleReparseError(parent, ruleIndex, parsedText, ex);
	}

	@Override
	public void omIssues(CSSStyleSheet sheet, int sheetIndex, SheetErrorHandler errHandler) {
		String uri = sheet.getHref();
		String path = getLinkedSheetInternalPath(sheet, uri);
		writeError("OM issue(s) in sheet at " + uri + "\npath: " + path);
		super.omIssues(sheet, sheetIndex, errHandler);
	}

	@Override
	void writeError(String message, Exception exception) {
		enableMainWriter();
		mainwriter.println(message);
		exception.printStackTrace(mainwriter);
	}

	@Override
	void writeError(String message) {
		enableMainWriter();
		mainwriter.println(message);
	}

	@Override
	void writeWarning(String message) {
		enableMainWriter();
		mainwriter.println("WARNING: " + message);
	}

	private void enableMainWriter() {
		if (mainwriter == null) {
			try {
				FileOutputStream out = new FileOutputStream(getMainFile());
				mainchannel = out.getChannel();
				mainfd = out.getFD();
				mainwriter = new PrintWriter(new OutputStreamWriter(out, "utf-8"));
			} catch (IOException e) {
				log.error("Unable to write to " + getMainFile().getAbsolutePath(), e);
			}
		}
	}

	@Override
	void writeMinificationError(String message) {
		if (miniwriter == null) {
			try {
				FileOutputStream out = new FileOutputStream(getMinificationFile());
				minichannel = out.getChannel();
				minifd = out.getFD();
				miniwriter = new PrintWriter(new OutputStreamWriter(out, "utf-8"));
			} catch (IOException e) {
				log.error("Unable to write to " + getMinificationFile().getAbsolutePath(), e);
			}
		}
		miniwriter.println(message);
	}

	@Override
	void writeSerializationError(String message) {
		enableSerializationWriter();
		serialwriter.println(message);
	}

	@Override
	void writeSerializationError(String message, DOMException exception) {
		enableSerializationWriter();
		serialwriter.println(message);
		exception.printStackTrace(serialwriter);
	}

	private void enableSerializationWriter() {
		if (serialwriter == null) {
			try {
				FileOutputStream out = new FileOutputStream(getSerializationFile());
				serialwriter = new PrintWriter(new OutputStreamWriter(out, "utf-8"));
			} catch (IOException e) {
				log.error("Unable to write to " + getSerializationFile().getAbsolutePath(), e);
			}
		}
	}

	@Override
	void selectTargetSheet(StyleSheet sheet, int sheetIndex, boolean warn) {
		if (lastSheetIndex != sheetIndex) {
			CSSElement owner;
			if ((owner = (CSSElement) sheet.getOwnerNode()) != null && "style".equalsIgnoreCase(owner.getTagName())) {
				String text;
				if (owner instanceof DOMElement) {
					text = ((DOMElement) owner).getText();
				} else {
					text = owner.getTextContent();
				}
				text = text.trim();
				if (text.length() != 0) {
					String filename = SampleSitesIT.encodeString(text).substring(0, 8);
					File sheetfile = new File(hostdir, filename + ".css");
					FileOutputStream out = null;
					try {
						out = new FileOutputStream(sheetfile);
						out.write(text.getBytes("utf-8"));
					} catch (IOException e) {
					} finally {
						if (out != null) {
							try {
								out.close();
							} catch (IOException e) {
							}
						}
					}
					writeWarning("Sheet: " + sheetfile.getAbsolutePath());
				}
			} else {
				String uri = sheet.getHref();
				String path = getLinkedSheetInternalPath(sheet, uri);
				writeWarning("Sheet at " + uri + "\npath: " + path);
			}
			lastSheetIndex = sheetIndex;
		}
	}

	private String getLinkedSheetInternalPath(StyleSheet sheet, String uri) {
		String path;
		try {
			URL url = new URL(uri);
			path = SampleSitesIT.encodeString(url.toExternalForm());
			path = url.getHost() + '/' + path;
		} catch (MalformedURLException e) {
			path = "-";
		}
		return path;
	}

	@Override
	public void sacIssues(CSSStyleSheet sheet, int sheetIndex, SACErrorHandler errHandler) {
		selectTargetSheet(sheet, sheetIndex, !errHandler.hasSacErrors());
		writeError(errHandler.toString());
		if (errHandler instanceof DefaultSheetErrorHandler) {
			DefaultSheetErrorHandler dseh = (DefaultSheetErrorHandler) errHandler;
			List<CSSParseException> sacErrors = dseh.getSacErrors();
			if (sacErrors != null) {
				ListIterator<CSSParseException> it = sacErrors.listIterator();
				while (it.hasNext()) {
					CSSParseException ex = it.next();
					writeError("SAC error at [" + ex.getLineNumber() + "," + ex.getColumnNumber() + "]: "
							+ ex.getMessage());
				}
			}
			List<CSSParseException> sacWarnings = dseh.getSacWarnings();
			if (sacWarnings != null) {
				ListIterator<CSSParseException> it = sacWarnings.listIterator();
				while (it.hasNext()) {
					CSSParseException ex = it.next();
					writeWarning("SAC warning at [" + ex.getLineNumber() + "," + ex.getColumnNumber() + "]: "
							+ ex.getMessage());
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (serialwriter != null) {
			serialwriter.flush();
			serialwriter.close();
		}
		if (mainwriter != null) {
			if (mainwriter.checkError()) {
				log.error("Problems writing to " + getMainFile().getAbsolutePath());
			}
			mainfd.sync();
			mainchannel.force(true);
			mainwriter.close();
			mainwriter = null;
		}
		if (miniwriter != null) {
			if (miniwriter.checkError()) {
				log.error("Problems writing to " + getMinificationFile().getAbsolutePath());
			}
			minifd.sync();
			minichannel.force(true);
			miniwriter.close();
			miniwriter = null;
		}
	}

}
