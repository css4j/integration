# css4j - integration testing

This repository allows to test css4j against real-world websites, to verify that CSS issues are caught (and no bogus errors are reported). If a CSS problem is found, the test fails (also fails if a difference between the css4j backends is found).

The class `SampleSitesIT` (located in the `ci` tree) fetches a list of URLs with their style sheets, and performs a few tests with the computed styles, reporting any anomaly. The tool not only verifies whether a web page has style errors or warnings that the library detects (with the configured SAC parser), but also whether the native DOM, the DOM wrapper and the DOM4J backend see the same document nodes and style sheets. The comparison is possible because the same HTML5 parser is used.

For each URL in the `samplesites.txt` file (that has to be put under the same package `io.github.css4j.ci` in the classpath), it fetches the document and its style sheets, computing styles for each element with the native implementation, the DOM wrapper and the DOM4J backend. It looks for errors in the style sheets and also compares the results of the three backends. If there are errors or differences in the styles computed by the implementations, they are reported (reporting is configurable) and the test fails.

If you try the test with a high-volume website, beware that each site is retrieved twice (one by each backend), and some sites do send slightly different documents in these circumstances, so you should enable the use of the cache (`cachedir` configuration parameter).

You can put as many websites as you want in `samplesites.txt`, and they can be commented out with the '#' character at the beginning of the line. There is a `samplesites.properties` that can be used for configuration, with options like `fail-on-warning` in addition to the aforementioned `cachedir`:
```
fail-on-warning=<true|false>
cachedir=<path to site cache>
parser.<flag>=true
```
- `fail-on-warning`: if set to true, a test shall fail even if only style sheet warnings were logged.
- `cachedir`: that has to be set to the directory where the cache files can be stored.
- `parser.<flag>`: set a NSAC parser flag to use in the test.

You can use this tool to monitor a list of URLs that are important for you. The tool can be run from an IDE or with the Maven Failsafe plugin (`mvn verify`), provided that the CI environment is set up correctly.

To use it with Failsafe, perhaps you want to set up a classpath directory in the failsafe configuration, to put the configuration files so they can be read (and edited!), like:
```
 <plugin>
   <groupId>org.apache.maven.plugins</groupId>
   <artifactId>maven-failsafe-plugin</artifactId>
   <version>3.0.0-M4</version>
   <configuration>
     <additionalClasspathElements>
       <additionalClasspathElement>/my/path/to/ci</additionalClasspathElement>
     </additionalClasspathElements>
   </configuration>
   <executions>
     <execution>
       <goals>
         <goal>integration-test</goal>
         <goal>verify</goal>
       </goals>
     </execution>
   </executions>
 </plugin>
```
And then you can run your own integration tests with your chosen sites.
