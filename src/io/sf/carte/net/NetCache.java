/*

 Copyright (c) 2017-2021, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.sf.carte.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetCache {

	static Logger log = LoggerFactory.getLogger(NetCache.class.getName());

	private static final String METADATA_FILENAME = "metadata.txt";

	private File cachedir = null;

	public NetCache(File cachedir) {
		super();
		if (cachedir == null) {
			throw new NullPointerException("Cache directory cannot be null");
		}
		this.cachedir = cachedir;
	}

	public boolean isCached(String hostname, String encodedUrl) {
		File hostdir = new File(cachedir, hostname);
		if (!hostdir.isDirectory()) {
			return false;
		}
		File cachedfile = new File(hostdir, encodedUrl);
		return cachedfile.canRead();
	}

	public File getCacheDirectory() {
		return cachedir;
	}

	public File getHostDirectory(URL url) {
		return new File(cachedir, url.getHost());
	}

	public void cacheFile(URL url, String encodedUrl, URLConnection ucon) throws IOException {
		File hostdir = getHostDirectory(url);
		if (!hostdir.isDirectory()) {
			if (!hostdir.mkdirs()) {
				throw new IOException("Could not create directory " + hostdir.getAbsolutePath());
			}
		}
		File cachedfile = new File(hostdir, encodedUrl);
		ucon.setConnectTimeout(100000);
		ucon.setAllowUserInteraction(false);
		FileOutputStream out = new FileOutputStream(cachedfile);
		InputStream is = null;
		long contentLen = 0;
		try {
			Charset charset = StandardCharsets.UTF_8;
			boolean contentEncodingGzip = false;
			ucon.connect();
			Map<String, List<String>> headers = ucon.getHeaderFields();
			Iterator<Entry<String, List<String>>> it = headers.entrySet().iterator();
			while(it.hasNext()) {
				Entry<String, List<String>> entry = it.next();
				String hdrname = entry.getKey();
				List<String> vlist = entry.getValue();
				if (hdrname != null && vlist.size() != 0) {
					if (hdrname.equalsIgnoreCase("Content-Encoding")) {
						Iterator<String> ecit = vlist.iterator();
						while (ecit.hasNext()) {
							if ("gzip".equalsIgnoreCase(ecit.next())) {
								contentEncodingGzip = true;
							}
						}
					}
					if (!contentEncodingGzip) {
						out.write(hdrname.getBytes(charset));
						out.write(58);
						out.write(vlist.get(vlist.size() - 1).getBytes(charset));
						out.write(10); // LF
					}
				}
			}
			out.write(10); // LF
			is = ucon.getInputStream();
			if (contentEncodingGzip) {
				is = new GZIPInputStream(is);
			}
			byte[] bbuf = new byte[4096];
			int numbytes;
			while ((numbytes = is.read(bbuf)) != -1) {
				out.write(bbuf, 0, numbytes);
				contentLen += numbytes;
			}
		} catch (IOException e) {
			out.close();
			cachedfile.delete();
			throw e;
		} finally {
			if (is != null) {
				is.close();
			}
		}
		out.close();
		// Update metadata
		File metadata = new File(hostdir, METADATA_FILENAME);
		PrintStream wri = new PrintStream(new FileOutputStream(metadata, true));
		wri.append(encodedUrl);
		wri.append(' ');
		wri.printf(Locale.ROOT, "%10d", contentLen);
		wri.append(' ');
		wri.append(url.getFile());
		String query = url.getQuery();
		if (query != null) {
			wri.append('?').append(query);
		}
		wri.println();
		wri.close();
	}

	public URLConnection openConnection(String hostname, String encodedUrl) throws IOException {
		File cachedfile = new File(new File(cachedir, hostname), encodedUrl);
		return new CacheConnection(cachedfile);
	}

	static class CacheConnection extends URLConnection {

		private final File cachedfile;
		private final LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
		long contentLength = -1;
		private FileInputStream inputStream = null;

		protected CacheConnection(File cachedfile) throws MalformedURLException {
			super(cachedfile.toURI().toURL());
			this.cachedfile = cachedfile;
		}

		@Override
		public void connect() throws IOException {
			inputStream = new FileInputStream(cachedfile);
			do {
				String line = readLine();
				if (line.length() == 0) {
					break;
				}
				int klen = line.indexOf(':');
				headers.put(line.substring(0, klen).toLowerCase(Locale.ROOT), line.substring(klen + 1));
			} while(true);
			contentLength = inputStream.available();
		}

		private String readLine() throws IOException {
			StringBuilder buf = new StringBuilder(256);
			while(true) {
				int cp = inputStream.read();
				if (cp == 10 || cp == -1) {
					break;
				}
				buf.append(Character.toChars(cp));
			}
			return buf.toString();
		}

		@Override
		public int getContentLength() {
			return (int) contentLength;
		}

		@Override
		public long getContentLengthLong() {
			return contentLength;
		}

		@Override
		public String getHeaderField(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

		@Override
		public String getHeaderFieldKey(int n) {
			String[] harray = headers.keySet().toArray(new String[0]);
			if (n < 0 || n >= harray.length) {
				return null;
			}
			return harray[n];
		}

		@Override
		public String getHeaderField(int n) {
			String[] harray = headers.values().toArray(new String[0]);
			if (n < 0 || n >= harray.length) {
				return null;
			}
			return harray[n];
		}

		@Override
		public InputStream getInputStream() throws IOException {
			if (inputStream == null) {
				connect();
			}
			return inputStream;
		}
	}

}
