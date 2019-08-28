/*

 Copyright (c) 2017-2019, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.sf.carte.doc.dom.DOMBridge;
import io.sf.carte.doc.style.css.om.AbstractCSSStyleSheet;
import io.sf.carte.doc.style.css.om.BaseCSSStyleDeclaration;
import io.sf.carte.doc.style.css.om.CSSStyleDeclarationRule;
import io.sf.carte.doc.style.css.property.AbstractCSSValue;
import io.sf.carte.doc.style.css.property.ValueFactory;

public class SampleSitesTest {

	private SampleSitesIT sitetest;
	private BaseCSSStyleDeclaration styleDecl;
	private ValueComparator comparator;

	@Before
	public void setUp() {
		sitetest = new SampleSitesIT();
		styleDecl = createCSSStyleDeclaration();
		comparator = new ValueComparator(styleDecl);
	}

	private BaseCSSStyleDeclaration createCSSStyleDeclaration() {
		AbstractCSSStyleSheet sheet = DOMBridge.createLinkedStyleSheet(sitetest.document.getImplementation(),
				sitetest.document.getDocumentElement());
		CSSStyleDeclarationRule styleRule = sheet.createCSSStyleRule();
		return (BaseCSSStyleDeclaration) styleRule.getStyle();
	}

	@Test
	public void testIsSameLayeredProperty() {
		// background-size
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("5px 5px, 5px 5px, 1px 1.5em");
		AbstractCSSValue other = factory.parseProperty("5px 5px, 5px 5px");
		assertTrue(comparator.isSameLayeredProperty(value, other, 2));
	}

	@Test
	public void testIsSameLayeredProperty2() {
		// background-attachment
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("scroll");
		AbstractCSSValue other = factory.parseProperty("scroll, scroll");
		assertTrue(comparator.isSameLayeredProperty(value, other, 2));
	}

	@Test
	public void testIsSameLayeredProperty3() {
		// background-attachment
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("center center");
		AbstractCSSValue other = factory.parseProperty("center,center");
		assertTrue(comparator.isSameLayeredProperty(value, other, 2));
	}

	@Test
	public void testIsSameLayeredProperty4() {
		// background-size
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("5px 5px");
		AbstractCSSValue other = factory.parseProperty("5px 5px, 5px 5px");
		assertTrue(comparator.isSameLayeredProperty(value, other, 2));
	}

	@Test
	public void testIsSameLayeredProperty5() {
		// background-size
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("5px 5px");
		AbstractCSSValue other = factory.parseProperty("5px 5px, 100%");
		assertTrue(comparator.isSameLayeredProperty(value, other, 1));
	}

	@Test
	public void testIsSameLayeredPropertyAuto() {
		// background-size
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("auto");
		AbstractCSSValue other = factory.parseProperty("auto auto, auto auto");
		assertTrue(comparator.isSameLayeredProperty(value, other, 2));
	}

	@Test
	public void testIsSameLayeredPropertyAuto2() {
		// background-size
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("auto auto");
		AbstractCSSValue other = factory.parseProperty("auto auto, auto auto");
		assertTrue(comparator.isSameLayeredProperty(value, other, 2));
	}

	@Test
	public void testIsSameBackgroundPosition() {
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("left top, center right");
		AbstractCSSValue other = factory.parseProperty("0% 0%, center right");
		assertTrue(comparator.isSameBackgroundPosition(value, other));
	}

	@Test
	public void testIsSameBackgroundPosition2() {
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("center 10%");
		AbstractCSSValue other = factory.parseProperty("center 10%, center 10%");
		assertTrue(comparator.isSameBackgroundPosition(value, other));
	}

	@Test
	public void testIsSameBackgroundPosition3() {
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("left 0px");
		AbstractCSSValue other = factory.parseProperty("left 0");
		assertTrue(comparator.isSameBackgroundPosition(value, other));
	}

	@Test
	public void testIsSameBackgroundPosition4() {
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue value = factory.parseProperty("left 0px");
		AbstractCSSValue other = factory.parseProperty("left -0px");
		assertTrue(comparator.isSameBackgroundPosition(value, other));
	}

	@Test
	public void testIsNotDifferentGridTemplateRows() {
		ValueFactory factory = new ValueFactory();
		styleDecl.setCssText("grid-template-rows: auto auto auto");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("grid-template-rows");
		AbstractCSSValue other = factory.parseProperty("auto");
		assertTrue(comparator.isNotDifferent("background-color", value, other));
	}

	@Test
	public void testIsNotDifferentColor() {
		styleDecl.setCssText("background-color: hsl(207 6% 61% / 0.6);");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-color");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("rgb(59% 61% 63% / 0.6)");
		assertTrue(comparator.isNotDifferent("background-color", value, other));
	}

	@Test
	public void testIsNotDifferentColor2() {
		styleDecl.setCssText("background-color: hsl(24 20% 50% / 0);");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-color");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("rgb(60% 48% 40% / 0)");
		assertTrue(comparator.isNotDifferent("background-color", value, other));
	}

	@Test
	public void testIsNotDifferentColor3() {
		styleDecl.setCssText("background-color: hsl(0, 0%, 95%);");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-color");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("#f2f2f2");
		assertTrue(comparator.isNotDifferent("background-color", value, other));
	}

	@Test
	public void testIsNotDifferentColor4() {
		styleDecl.setCssText("background-color: rgba(0,0,0,0);");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-color");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("transparent");
		assertTrue(comparator.isNotDifferent("background-color", value, other));
	}

	@Test
	public void testIsNotDifferentURL() {
		styleDecl.setCssText("background-image: url('/foo.png');");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-image");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("url('../foo.png')");
		assertTrue(comparator.isNotDifferent("background-image", value, other));
		assertTrue(comparator.isNotDifferent("background-image", other, value));
	}

	@Test
	public void testIsNotDifferentURL2() {
		styleDecl.setCssText("background-image: url('http://www.example.com/dir/file.png');");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-image");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("url('../dir/file.png')");
		assertTrue(comparator.isNotDifferent("background-image", value, other));
		assertTrue(comparator.isNotDifferent("background-image", other, value));
	}

	@Test
	public void testIsNotDifferentURL3() {
		styleDecl.setCssText("background-image: url('http://www.example.com/dir/file.png');");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-image");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("url('/dir/file.png')");
		assertTrue(comparator.isNotDifferent("background-image", value, other));
		assertTrue(comparator.isNotDifferent("background-image", other, value));
	}

	@Test
	public void testIsNotDifferentURL4() {
		styleDecl.setCssText("background-image: url('http://www.example.com/dir/file.png');");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-image");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("url('//www.example.com/dir/file.png')");
		assertTrue(comparator.isNotDifferent("background-image", value, other));
		assertTrue(comparator.isNotDifferent("background-image", other, value));
	}

	@Test
	public void testIsNotDifferentGradient() {
		styleDecl.setCssText("background-image: linear-gradient(left, hsl(24 20% 50% / 0.1) 70%, hsl(24 20% 50% / 0))");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-image");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("linear-gradient(left, hsl(24 20% 50% / 0.1) 70%, rgb(60% 48% 40% / 0))");
		assertTrue(comparator.isNotDifferent("background-image", value, other));
	}

	@Test
	public void testIsNotDifferentGradient2() {
		styleDecl.setCssText("background-image: linear-gradient(131deg, #fff 0%, hsl(0, 0%, 95%) 100%)");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("background-image");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("linear-gradient(131deg,#fff 0%,#f2f2f2 100%)");
		assertTrue(comparator.isNotDifferent("background-image", value, other));
	}

	@Test
	public void testIsNotDifferentTransform() {
		styleDecl.setCssText("transform: translateX(0%);");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("transform");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("translateX(0%)");
		assertTrue(comparator.isNotDifferent("transform", value, other));
	}

	@Test
	public void testIsNotDifferentList() {
		styleDecl.setCssText("border-image-slice: 10;");
		AbstractCSSValue value = styleDecl.getPropertyCSSValue("border-image-slice");
		ValueFactory factory = new ValueFactory();
		AbstractCSSValue other = factory.parseProperty("10 10");
		assertTrue(comparator.isNotDifferent("border-image-slice", value, other));
	}

}
