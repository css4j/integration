/*

 Copyright (c) 2017-2019, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.w3c.dom.DOMException;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSValue;

import io.sf.carte.doc.style.css.CSSGradientValue;
import io.sf.carte.doc.style.css.CSSPrimitiveValue2;
import io.sf.carte.doc.style.css.ExtendedCSSPrimitiveValue;
import io.sf.carte.doc.style.css.ExtendedCSSValue;
import io.sf.carte.doc.style.css.ExtendedCSSValueList;
import io.sf.carte.doc.style.css.RGBAColor;
import io.sf.carte.doc.style.css.om.BaseCSSStyleDeclaration;
import io.sf.carte.doc.style.css.om.CSSOMBridge;
import io.sf.carte.doc.style.css.parser.ParseHelper;
import io.sf.carte.doc.style.css.property.AbstractCSSValue;
import io.sf.carte.doc.style.css.property.NumberValue;
import io.sf.carte.doc.style.css.property.PropertyDatabase;
import io.sf.carte.doc.style.css.property.URIValue;
import io.sf.carte.doc.style.css.property.ValueList;

class ValueComparator {

	private final BaseCSSStyleDeclaration style;

	ValueComparator(BaseCSSStyleDeclaration style) {
		super();
		this.style = style;
	}

	public boolean isNotDifferent(String property, ExtendedCSSValue value, ExtendedCSSValue minivalue) {
		if (value.equals(minivalue)) {
			// This is always going to evaluate to false, but just in case...
			return true;
		}
		String text = value.getCssText();
		if (text.equalsIgnoreCase("initial")) {
			AbstractCSSValue inivalue = CSSOMBridge.getInitialValue(property, style, PropertyDatabase.getInstance());
			if (minivalue.equals(inivalue)) {
				return true;
			}
		}
		String minitext = minivalue.getCssText();
		if (minitext.equalsIgnoreCase("initial")) {
			AbstractCSSValue inivalue = CSSOMBridge.getInitialValue(property, style, PropertyDatabase.getInstance());
			if (value.equals(inivalue)) {
				return true;
			}
		}
		if (text.equals(minitext) || isApproximateNumericValue(value, minivalue)) {
			return true;
		} else if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST
				&& minivalue.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			if (isApproximateNumericValue((ValueList) value, (ValueList) minivalue)) {
				return true;
			}
		}
		if (property.equals("background-position") && isSameBackgroundPosition(value, minivalue)) {
			return true;
		} else if (property.equals("background-repeat")) {
			int masterLen = masterPropertyLength("background-image");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
			if (minivalue.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
				ValueList mlist = (ValueList) minivalue;
				return mlist.getLength() == 2 && mlist.item(0).equals(mlist.item(1)) && mlist.item(0).equals(value);
			} else if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
				ValueList list = (ValueList) value;
				return list.getLength() == 2 && list.item(0).equals(list.item(1)) && list.item(0).equals(value);
			}
		} else if (property.equals("background-color") && text.equalsIgnoreCase("none")) {
			return true;
		} else if (property.equals("background-size")) {
			int masterLen = masterPropertyLength("background-image");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
			if (text.equalsIgnoreCase("auto") && minitext.equalsIgnoreCase("auto auto")) {
				return true;
			}
		} else if (property.startsWith("background-")) {
			int masterLen = masterPropertyLength("background-image");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
		} else if (property.startsWith("grid-")) {
			if (isSameLayeredPropertyItem(value, minivalue)) {
				return true;
			}
		}
		switch (testDifferentValue(value, minivalue)) {
		case 1:
			return true;
		case 2:
			return false;
		default:
		}
		try {
			text = ParseHelper.unescapeStringValue(text);
			minitext = ParseHelper.unescapeStringValue(minitext);
		} catch (DOMException e) {
		}
		if (text.equals(minitext)) {
			return true;
		}
		return false;
	}

	/**
	 * Test whether two values are different.
	 * 
	 * @return 1 if not different, 2 if different, 0 if inconclusive
	 */
	private int testDifferentValue(ExtendedCSSValue value, ExtendedCSSValue otherValue) {
		if (value.getCssValueType() == CSSValue.CSS_PRIMITIVE_VALUE
				&& otherValue.getCssValueType() == CSSValue.CSS_PRIMITIVE_VALUE) {
			ExtendedCSSPrimitiveValue pri = (ExtendedCSSPrimitiveValue) value;
			ExtendedCSSPrimitiveValue priOther = (ExtendedCSSPrimitiveValue) otherValue;
			if (pri.getPrimitiveType() == CSSPrimitiveValue.CSS_RGBCOLOR) {
				short otype = priOther.getPrimitiveType();
				if (otype == CSSPrimitiveValue.CSS_RGBCOLOR) {
					RGBAColor color = pri.getRGBColorValue();
					RGBAColor otherColor = priOther.getRGBColorValue();
					if (similarComponentValues(color.getRed(), otherColor.getRed())
							&& similarComponentValues(color.getGreen(), otherColor.getGreen())
							&& similarComponentValues(color.getBlue(), otherColor.getBlue())
							&& Math.abs(color.getAlpha().getFloatValue(CSSPrimitiveValue.CSS_NUMBER)
									- otherColor.getAlpha().getFloatValue(CSSPrimitiveValue.CSS_NUMBER)) < 0.01f) {
						return 1;
					} else {
						return 2;
					}
				} else if (otype == CSSPrimitiveValue.CSS_IDENT
						&& "transparent".equalsIgnoreCase(priOther.getStringValue())) {
					String cssText = pri.getMinifiedCssText("");
					if ("rgba(0,0,0,0)".equals(cssText) || "rgb(0 0 0/0)".equals(cssText)) {
						return 1;
					}
					return 2;
				}
			} else if (pri.getPrimitiveType() == CSSPrimitiveValue.CSS_URI
					&& priOther.getPrimitiveType() == CSSPrimitiveValue.CSS_URI) {
				URIValue uri = (URIValue) pri;
				URIValue uriOther = (URIValue) priOther;
				if (isSameURI(pri, priOther) || uri.isEquivalent(uriOther)) {
					return 1;
				} else {
					return 2;
				}
			} else if (pri.getPrimitiveType() == CSSPrimitiveValue.CSS_STRING
					&& priOther.getPrimitiveType() == CSSPrimitiveValue.CSS_STRING) {
				if (ParseHelper.unescapeStringValue(pri.getStringValue())
						.equals(ParseHelper.unescapeStringValue(priOther.getStringValue()))) {
					return 1;
				} else {
					return 2;
				}
			} else if (pri.getPrimitiveType() == CSSPrimitiveValue2.CSS_GRADIENT
					&& priOther.getPrimitiveType() == CSSPrimitiveValue2.CSS_GRADIENT) {
				CSSGradientValue gradient = (CSSGradientValue) pri;
				CSSGradientValue gradientOther = (CSSGradientValue) priOther;
				if (gradient.getGradientType() != gradientOther.getGradientType()) {
					return 2;
				}
				@SuppressWarnings("unchecked")
				ExtendedCSSValueList<AbstractCSSValue> args = (ExtendedCSSValueList<AbstractCSSValue>) gradient
						.getArguments();
				@SuppressWarnings("unchecked")
				ExtendedCSSValueList<AbstractCSSValue> argsOther = (ExtendedCSSValueList<AbstractCSSValue>) gradientOther
						.getArguments();
				if (args.getLength() != argsOther.getLength()) {
					return 2;
				}
				for (int i = 0; i < args.getLength(); i++) {
					AbstractCSSValue arg = args.item(i);
					AbstractCSSValue argOther = argsOther.item(i);
					if (!arg.equals(argOther)) {
						int result = testDifferentValue(arg, argOther);
						if (result != 1) {
							return 2;
						}
					}
				}
				return 1;
			} else if (pri.equals(priOther)) {
				return 1;
			}
		} else if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST
				&& otherValue.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList list = (ValueList) value;
			int len = list.getLength();
			ValueList otherList = (ValueList) otherValue;
			if (len == otherList.getLength() && list.isCommaSeparated() == otherList.isCommaSeparated()) {
				int listResult = 1;
				for (int i = 0; i < len; i++) {
					int result = testDifferentValue(list.item(i), otherList.item(i));
					if (result == 2) {
						return 2;
					} else if (result == 0) {
						listResult = 0;
					}
				}
				return listResult;
			} else {
				return 2;
			}
		}
		return 0;
	}

	private boolean isSameURI(CSSPrimitiveValue pri, CSSPrimitiveValue primini) {
		String uri = pri.getStringValue();
		String urimini = primini.getStringValue();
		URL baseurl;
		try {
			baseurl = new URL(style.getParentRule().getParentStyleSheet().getHref());
		} catch (MalformedURLException e) {
			return uri.equals(urimini);
		}
		try {
			URL url = new URL(baseurl, uri);
			URL urlmini = new URL(baseurl, urimini);
			return url.toURI().normalize().equals(urlmini.toURI().normalize());
		} catch (MalformedURLException | URISyntaxException e) {
		}
		return uri.equals(urimini);
	}

	private static boolean similarComponentValues(CSSPrimitiveValue comp1, CSSPrimitiveValue comp2) {
		return Math.abs(colorComponentPercent(comp1) - colorComponentPercent(comp2)) < 1f;
	}

	private static float colorComponentPercent(CSSPrimitiveValue comp) {
		float val;
		short type = comp.getPrimitiveType();
		if (type == CSSPrimitiveValue.CSS_PERCENTAGE) {
			val = comp.getFloatValue(CSSPrimitiveValue.CSS_PERCENTAGE);
		} else {
			val = comp.getFloatValue(CSSPrimitiveValue.CSS_NUMBER) / 2.55f;
		}
		return val;
	}

	private int masterPropertyLength(String propertyName) {
		int masterLen = 10;
		AbstractCSSValue bimage = style.getPropertyCSSValue(propertyName);
		if (bimage != null) {
			if (bimage.getCssValueType() == CSSValue.CSS_VALUE_LIST && ((ValueList) bimage).isCommaSeparated()) {
				masterLen = ((ValueList) bimage).getLength();
			} else {
				masterLen = 1;
			}
		}
		return masterLen;
	}

	boolean isSameLayeredProperty(ExtendedCSSValue value, ExtendedCSSValue minivalue, int masterLen) {
		if (masterLen == 1) {
			ValueList list, minilist;
			if (minivalue.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
				minilist = (ValueList) minivalue;
				if (minilist.isCommaSeparated()) {
					minivalue = minilist.item(0);
				}
			}
			if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST && (list = (ValueList) minivalue).isCommaSeparated()) {
				value = list.item(0);
			}
			return isSameLayeredPropertyItem(value, minivalue);
		} else if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList list = (ValueList) value;
			if (list.isCommaSeparated()) {
				int len = list.getLength();
				if (len > masterLen) {
					len = masterLen;
				}
				int minilen;
				ValueList minilist;
				if (minivalue.getCssValueType() != CSSValue.CSS_VALUE_LIST
						|| (minilen = (minilist = (ValueList) minivalue).getLength()) < len) {
					return false;
				}
				for (int i = 0; i < len; i++) {
					if (!isSameLayeredPropertyItem(list.item(i), minilist.item(i))) {
						return false;
					}
				}
				// Check for repeated values
				for (int i = len; i < minilen; i++) {
					if (!isSameLayeredPropertyItem(list.item(i - len), minilist.item(i))) {
						return false;
					}
				}
				return true;
			}
		}
		if (minivalue.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList minilist = (ValueList) minivalue;
			if (minilist.isCommaSeparated()) {
				int minilen = minilist.getLength();
				if (minilen > masterLen) {
					minilen = masterLen;
				}
				boolean islist = value.getCssValueType() == CSSValue.CSS_VALUE_LIST;
				if (!islist || (islist && !((ValueList) value).isCommaSeparated())) {
					for (int i = 0; i < minilen; i++) {
						if (!isSameLayeredPropertyItem(value, minilist.item(i))) {
							return false;
						}
					}
					return true;
				}
			}
		}
		return isSameLayeredPropertyItem(value, minivalue);
	}

	/*
	 * Here, value is not a comma-separated list
	 */
	private boolean isSameLayeredPropertyItem(ExtendedCSSValue value, ExtendedCSSValue minivalue) {
		if (value.equals(minivalue) || testDifferentValue(value, minivalue) == 1) {
			return true;
		}
		if (minivalue.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList list = (ValueList) minivalue;
			if (list.isCommaSeparated()) {
				for (int i = 0; i < list.getLength(); i++) {
					AbstractCSSValue item = list.item(i);
					if (!value.equals(item) && testDifferentValue(value, item) != 1) {
						return false;
					}
				}
				return true;
			} else {
				for (int i = 0; i < list.getLength(); i++) {
					AbstractCSSValue item = list.item(i);
					if (!value.equals(item) && testDifferentValue(value, item) != 1) {
						return false;
					}
				}
				return true;
			}
		} else if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList list = (ValueList) value;
			for (int i = 0; i < list.getLength(); i++) {
				AbstractCSSValue item = list.item(i);
				if (!minivalue.equals(item) && testDifferentValue(minivalue, item) != 1) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	boolean isSameBackgroundPosition(ExtendedCSSValue value, ExtendedCSSValue minivalue) {
		if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList list = (ValueList) value;
			if (list.isCommaSeparated()) {
				int len = list.getLength();
				int minilen = ((ValueList) minivalue).getLength();
				if (minivalue.getCssValueType() != CSSValue.CSS_VALUE_LIST || minilen < len) {
					return false;
				}
				ValueList minilist = (ValueList) minivalue;
				for (int i = 0; i < len; i++) {
					if (!isSameBackgroundPositionItem(list.item(i), minilist.item(i))) {
						return false;
					}
				}
				// Check for repeated values
				for (int i = len; i < minilen; i++) {
					if (!isSameBackgroundPositionItem(list.item(i - len), minilist.item(i))) {
						return false;
					}
				}
				return true;
			}
		}
		return isSameLayeredPropertyItem(value, minivalue) || isSameBackgroundPositionItem(value, minivalue);
	}

	/*
	 * Here, value is not a comma-separated list
	 */
	boolean isSameBackgroundPositionItem(ExtendedCSSValue value, ExtendedCSSValue minivalue) {
		if (value.equals(minivalue)) {
			return true;
		}
		if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			ValueList list = (ValueList) value;
			if (list.getLength() == 2) {
				String text1 = list.item(1).getCssText();
				if (text1.equalsIgnoreCase("center")) {
					return list.item(0).equals(minivalue);
				}
				if (minivalue.getCssValueType() != CSSValue.CSS_VALUE_LIST
						|| ((ValueList) minivalue).getLength() != 2) {
					return false;
				}
				String text0 = list.item(0).getCssText();
				String minitext = minivalue.getCssText();
				if (text0.equalsIgnoreCase("left") && text1.equalsIgnoreCase("top") && minitext.equals("0% 0%")) {
					return true;
				}
				if (text0.equalsIgnoreCase("top") && text1.equalsIgnoreCase("left") && minitext.equals("0% 0%")) {
					// top left is not totally conformant, but...
					return true;
				}
			}
		}
		return isNotDifferent("", value, minivalue);
	}

	private static boolean isApproximateNumericValue(ValueList list, ValueList mlist) {
		if (list.getLength() == mlist.getLength()) {
			for (int i = 0; i < list.getLength(); i++) {
				if (!isApproximateNumericValue(list.item(i), mlist.item(i))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private static boolean isApproximateNumericValue(ExtendedCSSValue value, ExtendedCSSValue minivalue) {
		if (value instanceof NumberValue && minivalue instanceof NumberValue) {
			NumberValue num = (NumberValue) value;
			NumberValue mininum = (NumberValue) minivalue;
			int val = Math.round(num.getFloatValue(num.getPrimitiveType()) * 1000f);
			int minival = Math.round(mininum.getFloatValue(mininum.getPrimitiveType()) * 1000f);
			if (val == 0 && minival == 0) {
				return true;
			}
			return val == minival && num.getPrimitiveType() == mininum.getPrimitiveType();
		}
		return false;
	}

}
