/*

 Copyright (c) 2017-2025, Carlos Amengual.

 SPDX-License-Identifier: BSD-3-Clause

 Licensed under a BSD-style License. You can find the license here:
 https://css4j.github.io/LICENSE.txt

 */

package io.github.css4j.ci;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;

import org.w3c.dom.DOMException;

import io.sf.carte.doc.style.css.CSSGradientValue;
import io.sf.carte.doc.style.css.CSSPrimitiveValue;
import io.sf.carte.doc.style.css.CSSTypedValue;
import io.sf.carte.doc.style.css.CSSUnit;
import io.sf.carte.doc.style.css.CSSValue;
import io.sf.carte.doc.style.css.CSSValue.CssType;
import io.sf.carte.doc.style.css.CSSValue.Type;
import io.sf.carte.doc.style.css.CSSValueList;
import io.sf.carte.doc.style.css.RGBAColor;
import io.sf.carte.doc.style.css.nsac.LexicalUnit;
import io.sf.carte.doc.style.css.om.BaseCSSStyleDeclaration;
import io.sf.carte.doc.style.css.om.CSSOMBridge;
import io.sf.carte.doc.style.css.parser.ParseHelper;
import io.sf.carte.doc.style.css.property.NumberValue;
import io.sf.carte.doc.style.css.property.PropertyDatabase;
import io.sf.carte.doc.style.css.property.StyleValue;
import io.sf.carte.doc.style.css.property.URIValue;
import io.sf.carte.doc.style.css.property.ValueFactory;
import io.sf.carte.doc.style.css.property.ValueList;
import io.sf.carte.doc.style.css.property.VarValue;

class ValueComparator {

	private final BaseCSSStyleDeclaration style;

	ValueComparator(BaseCSSStyleDeclaration style) {
		super();
		this.style = style;
	}

	public boolean isNotDifferent(String property, CSSValue value, CSSValue minivalue) {
		if (value.equals(minivalue) || minivalue.getPrimitiveType() == Type.INTERNAL) {
			// The first is almost always going to evaluate to false
			return true;
		}
		String text = value.getCssText();
		if (value.getPrimitiveType() == CSSValue.Type.INITIAL || (value.getPrimitiveType() == CSSValue.Type.UNSET
				&& !PropertyDatabase.getInstance().isInherited(property))) {
			StyleValue inivalue = CSSOMBridge.getInitialValue(property, style);
			if (isNotDifferent(property, inivalue, minivalue)) {
				return true;
			}
		}
		String minitext = minivalue.getCssText();
		if (minivalue.getPrimitiveType() == CSSValue.Type.INITIAL
				|| (minivalue.getPrimitiveType() == CSSValue.Type.UNSET
						&& !PropertyDatabase.getInstance().isInherited(property))) {
			StyleValue inivalue = CSSOMBridge.getInitialValue(property, style);
			if (isNotDifferent(property, value, inivalue)) {
				return true;
			}
		}
		if (text.equals(minitext) || isApproximateNumericValue(value, minivalue)) {
			return true;
		} else if (value.getCssValueType() == CssType.LIST) {
			if ("font-family".equals(property) && minivalue.getPrimitiveType() == Type.STRING
					&& text.equals(((CSSTypedValue) minivalue).getStringValue())) {
				return true;
			}
			if (minivalue.getCssValueType() == CssType.LIST) {
				if (isApproximateNumericValue((ValueList) value, (ValueList) minivalue)) {
					return true;
				}
			}
		}
		if (property.equals("background-position")
				&& isSameBackgroundPosition(value, minivalue, masterPropertyLength("background-image"))) {
			return true;
		} else if (property.equals("background-repeat")) {
			int masterLen = masterPropertyLength("background-image");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
			if (isRepeatedList(value, minivalue)) {
				return true;
			}
		} else if (property.equals("background-color") && text.equalsIgnoreCase("none")) {
			return true;
		} else if (property.equals("background-size")) {
			int masterLen = masterPropertyLength("background-image");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
			if (isRepeatedList(value, minivalue)) {
				return true;
			}
		} else if (property.startsWith("background-")) {
			int masterLen = masterPropertyLength("background-image");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
		} else if (property.startsWith("animation-")) {
			int masterLen = masterPropertyLength("animation-name");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
		} else if (property.startsWith("transition-")) {
			int masterLen = masterPropertyLength("transition-property");
			if (isSameLayeredProperty(value, minivalue, masterLen)) {
				return true;
			}
		} else if (property.startsWith("grid-")) {
			if (isSameLayeredPropertyItem(value, minivalue)) {
				return true;
			}
		} else if (property.startsWith("border-image-")) {
			if (isRepeatedList(value, minivalue)) {
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

	private boolean isRepeatedList(CSSValue value, CSSValue minivalue) {
		if (minivalue.getCssValueType() == CssType.LIST) {
			ValueList mlist = (ValueList) minivalue;
			Iterator<StyleValue> it = mlist.iterator();
			minivalue = it.next();
			while (it.hasNext()) {
				if (!minivalue.equals(it.next())) {
					return false;
				}
			}
		}
		if (value.getCssValueType() == CssType.LIST) {
			Iterator<StyleValue> it = ((ValueList) value).iterator();
			value = it.next();
			while (it.hasNext()) {
				if (!value.equals(it.next())) {
					return false;
				}
			}
		}
		return minivalue.equals(value);
	}

	/**
	 * Test whether two values are different.
	 * 
	 * @return 1 if not different, 2 if different, 0 if inconclusive
	 */
	private int testDifferentValue(CSSValue value, CSSValue otherValue) {
		if (value.getCssValueType() == CssType.TYPED
				&& otherValue.getCssValueType() == CssType.TYPED) {
			CSSTypedValue pri = (CSSTypedValue) value;
			CSSTypedValue priOther = (CSSTypedValue) otherValue;
			Type ptype = pri.getPrimitiveType();
			Type otype = priOther.getPrimitiveType();
			if (ptype == Type.COLOR) {
				if (otype == Type.COLOR) {
					RGBAColor color = pri.toRGBColor();
					RGBAColor otherColor = priOther.toRGBColor();
					if (similarComponentValues(color.getRed(), otherColor.getRed())
							&& similarComponentValues(color.getGreen(), otherColor.getGreen())
							&& similarComponentValues(color.getBlue(), otherColor.getBlue())
							&& similarAlphaValue(color.getAlpha(), otherColor.getAlpha())) {
						return 1;
					} else {
						return 2;
					}
				} else if (otype == Type.IDENT
						&& "transparent".equalsIgnoreCase(priOther.getStringValue())) {
					String cssText = pri.getMinifiedCssText("");
					if ("rgba(0,0,0,0)".equals(cssText) || "rgb(0 0 0/0)".equals(cssText)) {
						return 1;
					}
					return 2;
				}
			} else if (ptype == Type.URI && otype == Type.URI) {
				URIValue uri = (URIValue) pri;
				URIValue uriOther = (URIValue) priOther;
				if (isSameURI(pri, priOther) || uri.isEquivalent(uriOther)) {
					return 1;
				} else {
					return 2;
				}
			} else if (ptype == Type.STRING && otype == Type.STRING) {
				if (ParseHelper.unescapeStringValue(pri.getStringValue())
						.equals(ParseHelper.unescapeStringValue(priOther.getStringValue()))) {
					return 1;
				} else {
					return 2;
				}
			} else if (ptype == Type.GRADIENT && otype == Type.GRADIENT) {
				CSSGradientValue gradient = (CSSGradientValue) pri;
				CSSGradientValue gradientOther = (CSSGradientValue) priOther;
				if (gradient.getGradientType() != gradientOther.getGradientType()) {
					return 2;
				}
				CSSValueList<? extends CSSValue> args = gradient.getArguments();
				CSSValueList<? extends CSSValue> argsOther = gradientOther.getArguments();
				if (args.getLength() != argsOther.getLength()) {
					return 2;
				}
				for (int i = 0; i < args.getLength(); i++) {
					CSSValue arg = args.item(i);
					CSSValue argOther = argsOther.item(i);
					if (!arg.equals(argOther)) {
						int result = testDifferentValue(arg, argOther);
						if (result != 1) {
							return 2;
						}
					}
				}
				return 1;
			} else if (ptype == Type.IDENT && otype == Type.IDENT) {
				String sv = pri.getStringValue();
				String osv = priOther.getStringValue();
				if (sv.equalsIgnoreCase(osv)) {
					return 1;
				}
			} else if (pri.equals(priOther)) {
				return 1;
			}
		} else if (value.getCssValueType() == CssType.PROXY
				&& otherValue.getCssValueType() == CssType.PROXY) {
			Type ptype = value.getPrimitiveType();
			Type otype = otherValue.getPrimitiveType();
			if (ptype == Type.VAR && otype == Type.VAR) {
				VarValue var = (VarValue) value;
				VarValue varOther = (VarValue) otherValue;
				if (var.getName().equals(varOther.getName())) {
					LexicalUnit fb = var.getFallback();
					LexicalUnit fbOther = varOther.getFallback();
					if (fb == null) {
						if (fbOther == null) {
							return 1;
						}
					} else if (fbOther != null) {
						if (fb.equals(fbOther)) {
							return 1;
						}
						ValueFactory vf = new ValueFactory();
						StyleValue fbOm, fbOtherOm;
						try {
							fbOm = vf.createCSSValue(fb);
							fbOtherOm = vf.createCSSValue(fbOther);
						} catch (DOMException e) {
							return 2;
						}
						return testDifferentValue(fbOm, fbOtherOm);
					}
				}
			}
			return 2;
		} else if (value.getCssValueType() == CssType.LIST
				&& otherValue.getCssValueType() == CssType.LIST) {
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

	private boolean isSameURI(CSSTypedValue pri, CSSTypedValue primini) {
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
		if (comp1.getPrimitiveType() != Type.NUMERIC || comp2.getPrimitiveType() != Type.NUMERIC) {
			return comp1.equals(comp2);
		}
		return Math
				.abs(colorComponentPercent((CSSTypedValue) comp1) - colorComponentPercent((CSSTypedValue) comp2)) < 1f;
	}

	private static float colorComponentPercent(CSSTypedValue comp) {
		float val;
		short type = comp.getUnitType();
		if (type == CSSUnit.CSS_PERCENTAGE) {
			val = comp.getFloatValue(CSSUnit.CSS_PERCENTAGE);
		} else {
			val = comp.getFloatValue(CSSUnit.CSS_NUMBER) / 2.55f;
		}
		return val;
	}

	private boolean similarAlphaValue(CSSPrimitiveValue alpha, CSSPrimitiveValue alpha2) {
		if (alpha.getPrimitiveType() != Type.NUMERIC || alpha2.getPrimitiveType() != Type.NUMERIC) {
			return alpha.equals(alpha2);
		}
		return Math.abs(((CSSTypedValue) alpha).getFloatValue(CSSUnit.CSS_NUMBER)
				- ((CSSTypedValue) alpha2).getFloatValue(CSSUnit.CSS_NUMBER)) < 0.01f;
	}

	private int masterPropertyLength(String propertyName) {
		int masterLen = 10;
		StyleValue bimage = style.getPropertyCSSValue(propertyName);
		if (bimage != null) {
			if (bimage.getCssValueType() == CssType.LIST && ((ValueList) bimage).isCommaSeparated()) {
				masterLen = ((ValueList) bimage).getLength();
			} else {
				masterLen = 1;
			}
		}
		return masterLen;
	}

	boolean isSameLayeredProperty(CSSValue value, CSSValue minivalue, int masterLen) {
		if (masterLen == 1) {
			ValueList list, minilist;
			if (minivalue.getCssValueType() == CssType.LIST) {
				minilist = (ValueList) minivalue;
				if (minilist.isCommaSeparated()) {
					minivalue = minilist.item(0);
				}
			}
			if (value.getCssValueType() == CssType.LIST && (list = (ValueList) value).isCommaSeparated()) {
				value = list.item(0);
			}
			return isSameLayeredPropertyItem(value, minivalue);
		} else if (value.getCssValueType() == CssType.LIST) {
			ValueList list = (ValueList) value;
			if (list.isCommaSeparated()) {
				int len = list.getLength();
				if (len > masterLen) {
					len = masterLen;
				}
				int minilen;
				ValueList minilist;
				if (minivalue.getCssValueType() != CssType.LIST
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
		if (minivalue.getCssValueType() == CssType.LIST) {
			ValueList minilist = (ValueList) minivalue;
			if (minilist.isCommaSeparated()) {
				int minilen = minilist.getLength();
				if (minilen > masterLen) {
					minilen = masterLen;
				}
				boolean islist = value.getCssValueType() == CssType.LIST;
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
	private boolean isSameLayeredPropertyItem(CSSValue value, CSSValue minivalue) {
		if (value.equals(minivalue) || testDifferentValue(value, minivalue) == 1) {
			return true;
		}
		if (minivalue.getCssValueType() == CssType.LIST) {
			ValueList list = (ValueList) minivalue;
			if (list.isCommaSeparated()) {
				for (int i = 0; i < list.getLength(); i++) {
					StyleValue item = list.item(i);
					if (!value.equals(item) && testDifferentValue(value, item) != 1) {
						return false;
					}
				}
				return true;
			} else {
				for (int i = 0; i < list.getLength(); i++) {
					StyleValue item = list.item(i);
					if (!value.equals(item) && testDifferentValue(value, item) != 1) {
						return false;
					}
				}
				return true;
			}
		} else if (value.getCssValueType() == CssType.LIST) {
			ValueList list = (ValueList) value;
			for (int i = 0; i < list.getLength(); i++) {
				StyleValue item = list.item(i);
				if (!minivalue.equals(item) && testDifferentValue(minivalue, item) != 1) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	boolean isSameBackgroundPosition(CSSValue value, CSSValue minivalue, int masterLen) {
		ValueList list;
		if (value.getCssValueType() == CssType.LIST && (list = (ValueList) value).isCommaSeparated()) {
			int len = list.getLength();
			if (len > masterLen) {
				len = masterLen;
			}
			if (masterLen > 1) {
				int minilen;
				ValueList minilist;
				if (minivalue.getCssValueType() != CssType.LIST
						|| (minilen = (minilist = (ValueList) minivalue).getLength()) < len) {
					return false;
				}
				if (minilen > masterLen) {
					minilen = masterLen;
				}
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
			} else {
				value = list.item(0);
				if (minivalue.getCssValueType() == CssType.LIST) {
					minivalue = ((ValueList) minivalue).item(0);
				}
			}
		} else if (minivalue.getCssValueType() == CssType.LIST) {
			ValueList minilist = (ValueList) minivalue;
			if (minilist.isCommaSeparated()) {
				int minilen = minilist.getLength();
				if (minilen > masterLen) {
					minilen = masterLen;
				}
				if (!isSameBackgroundPositionItem(value, minilist.item(0))) {
					return false;
				}
				// Check for repeated values
				for (int i = 1; i < minilen; i++) {
					if (!isSameBackgroundPositionItem(value, minilist.item(i))) {
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
	boolean isSameBackgroundPositionItem(CSSValue value, CSSValue minivalue) {
		if (value.equals(minivalue)) {
			return true;
		}
		if (value.getCssValueType() == CssType.LIST) {
			ValueList list = (ValueList) value;
			if (list.getLength() == 2) {
				String text1 = list.item(1).getCssText();
				if (text1.equalsIgnoreCase("center")) {
					return list.item(0).equals(minivalue);
				}
				if (minivalue.getCssValueType() != CssType.LIST
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

	private static boolean isApproximateNumericValue(CSSValue value, CSSValue minivalue) {
		if (value instanceof NumberValue && minivalue instanceof NumberValue) {
			NumberValue num = (NumberValue) value;
			NumberValue mininum = (NumberValue) minivalue;
			int val = Math.round(num.getFloatValue(num.getUnitType()) * 1000f);
			int minival = Math.round(mininum.getFloatValue(mininum.getUnitType()) * 1000f);
			if (val == 0 && minival == 0) {
				return true;
			}
			return val == minival && num.getPrimitiveType() == mininum.getPrimitiveType();
		}
		return false;
	}

}
