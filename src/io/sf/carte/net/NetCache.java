/*

 Copyright (c) 2017-2022, Carlos Amengual.

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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
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

public class NetCache {

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
		InputStream is = null;
		long contentLen = 0;
		try (FileOutputStream out = new FileOutputStream(cachedfile)) {
			Charset charset = StandardCharsets.UTF_8;
			boolean contentEncodingGzip = false;
			ucon.connect();
			// Response code
			if (ucon instanceof HttpURLConnection) {
				int code = ((HttpURLConnection) ucon).getResponseCode();
				String message = ((HttpURLConnection) ucon).getResponseMessage();
				out.write(Integer.toString(code).trim().getBytes(charset));
				if (message != null && message.length() > 0) {
					out.write(32);
					out.write(message.getBytes(charset));
				}
				out.write(10); // LF
			}
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
			cachedfile.delete();
			throw e;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
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

	static class CacheConnection extends HttpURLConnection {

		private final File cachedfile;

		private final LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>(32);

		long contentLength = -1;

		private FileInputStream inputStream = null;

		private int statusCode = -1;

		private String statusMessage = null;

		protected CacheConnection(File cachedfile) throws MalformedURLException {
			super(cachedfile.toURI().toURL());
			this.cachedfile = cachedfile;
		}

		@Override
		public void connect() throws IOException {
			connected = true;

			inputStream = new FileInputStream(cachedfile);
			String line = readLine();
			int iws = line.indexOf(' ');
			if (iws > 1) {
				statusMessage = line.substring(iws + 1);
				line = line.substring(0, iws);
			}
			try {
				statusCode = Integer.parseInt(line);
			} catch (NumberFormatException e) {
				statusCode = 200;
			}

			// Headers
			do {
				line = readLine();
				if (line.length() == 0) {
					break;
				}
				int klen = line.indexOf(':');
				headers.put(line.substring(0, klen).toLowerCase(Locale.ROOT), line.substring(klen + 1));
			} while(true);
			contentLength = inputStream.available();
		}

		@Override
		public void disconnect() {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
				}
			}
			connected = false;
		}

		private String readLine() throws IOException {
			StringBuilder buf = new StringBuilder(256);
			while(true) {
				int cp = inputStream.read();
				if (cp == 10 || cp == -1) {
					break;
				}
				buf.appendCodePoint(cp);
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
		public void setFixedLengthStreamingMode(int contentLength) {
			throw new IllegalArgumentException("streaming not supported");
		}

		@Override
		public void setFixedLengthStreamingMode(long contentLength) {
			throw new IllegalArgumentException("streaming not supported");
		}

		@Override
		public void setInstanceFollowRedirects(boolean followRedirects) {
			if (!followRedirects) {
				throw new IllegalArgumentException("Redirects are always followed");
			}
		}

		@Override
		public void setRequestMethod(String method) throws ProtocolException {
			if (!"get".equalsIgnoreCase(method) && "post".equalsIgnoreCase(method)) {
				throw new ProtocolException("Unsupported method: " + method);
			}
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

		@Override
		public int getResponseCode() throws IOException {
			return statusCode;
		}

		@Override
		public String getResponseMessage() throws IOException {
			return statusMessage;
		}

		@Override
		public boolean usingProxy() {
			return false;
		}

	}

}
