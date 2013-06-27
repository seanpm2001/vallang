/*******************************************************************************
 * Copyright (c) 2009, 2012-2013 Centrum Wiskunde en Informatica (CWI)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Jurgen Vinju - interface and implementation
 *    Arnold Lankamp - implementation
 *    Anya Helene Bagge - rational support, labeled maps and tuples
 *    Davy Landman - added PI & E constants
 *    Michael Steindorfer - extracted factory for numeric data
 *******************************************************************************/
package org.eclipse.imp.pdb.facts.impl.fast;

import org.eclipse.imp.pdb.facts.*;
import org.eclipse.imp.pdb.facts.exceptions.FactParseError;
import org.eclipse.imp.pdb.facts.impl.BaseValueFactory;
import org.eclipse.imp.pdb.facts.impl.BoolValue;
import org.eclipse.imp.pdb.facts.impl.DateTimeValues;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.type.TypeFactory;
import org.eclipse.imp.pdb.facts.util.ShareableHashMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base value factory with optimized representations of primitive values.
 */
public abstract class FastBaseValueFactory implements IValueFactory {

	private final static int DEFAULT_PRECISION = 10;
	private final static String INTEGER_MAX_STRING = "2147483647";
	private final static String NEGATIVE_INTEGER_MAX_STRING = "-2147483648";
	private final AtomicInteger currentPrecision = new AtomicInteger(DEFAULT_PRECISION);

	public IInteger integer(BigInteger value) {
		if (value.bitLength() > 31) {
			return new BigIntegerValue(value);
		}
		return new IntegerValue(value.intValue());
	}

	public IReal real(BigDecimal value) {
		return new BigDecimalValue(value);
	}

	protected Type inferInstantiatedTypeOfConstructor(final Type constructorType, final IValue... children) {
		Type instantiatedType;
		if (!constructorType.getAbstractDataType().isParameterized()) {
			instantiatedType = constructorType;
		} else {
			ShareableHashMap<Type, Type> bindings = new ShareableHashMap<>();
			TypeFactory tf = TypeFactory.getInstance();
			Type params = constructorType.getAbstractDataType().getTypeParameters();
			for (Type p : params) {
				if (p.isOpen()) {
					bindings.put(p, tf.voidType());
				}
			}
			constructorType.getFieldTypes().match(tf.tupleType(children), bindings);
			instantiatedType = constructorType.instantiate(bindings);
		}

		return instantiatedType;
	}

	@Override
	public IInteger integer(String integerValue) {
		if (integerValue.startsWith("-")) {
			if (integerValue.length() < 11 || (integerValue.length() == 11 && integerValue.compareTo(NEGATIVE_INTEGER_MAX_STRING) <= 0)) {
				return new IntegerValue(Integer.parseInt(integerValue));
			}
			return new BigIntegerValue(new BigInteger(integerValue));
		}

		if (integerValue.length() < 10 || (integerValue.length() == 10 && integerValue.compareTo(INTEGER_MAX_STRING) <= 0)) {
			return new IntegerValue(Integer.parseInt(integerValue));
		}
		return new BigIntegerValue(new BigInteger(integerValue));
	}

	@Override
	public IInteger integer(int value) {
		return new IntegerValue(value);
	}

	@Override
	public IInteger integer(long value) {
		if (((value & 0x000000007fffffffL) == value) || ((value & 0xffffffff80000000L) == 0xffffffff80000000L)) {
			return integer((int) value);
		} else {
			byte[] valueData = new byte[8];
			valueData[0] = (byte) ((value >>> 56) & 0xff);
			valueData[1] = (byte) ((value >>> 48) & 0xff);
			valueData[2] = (byte) ((value >>> 40) & 0xff);
			valueData[3] = (byte) ((value >>> 32) & 0xff);
			valueData[4] = (byte) ((value >>> 24) & 0xff);
			valueData[5] = (byte) ((value >>> 16) & 0xff);
			valueData[6] = (byte) ((value >>> 8) & 0xff);
			valueData[7] = (byte) (value & 0xff);
			return integer(valueData);
		}
	}

	@Override
	public IInteger integer(byte[] integerData) {
		if (integerData.length <= 4) {
			int value = 0;
			for (int i = integerData.length - 1, j = 0; i >= 0; i--, j++) {
				value |= ((integerData[i] & 0xff) << (j * 8));
			}

			return new IntegerValue(value);
		}
		return new BigIntegerValue(new BigInteger(integerData));
	}

	@Override
	public IRational rational(int a, int b) {
		return rational(integer(a), integer(b));
	}

	@Override
	public IRational rational(long a, long b) {
		return rational(integer(a), integer(b));
	}

	@Override
	public IRational rational(IInteger a, IInteger b) {
		return new RationalValue(a, b);
	}

	@Override
	public IRational rational(String rat) throws NumberFormatException {
		if (rat.contains("r")) {
			String[] parts = rat.split("r");
			if (parts.length == 2) {
				return rational(integer(parts[0]), integer(parts[1]));
			}
			if (parts.length == 1) {
				return rational(integer(parts[0]), integer(1));
			}
			throw new NumberFormatException(rat);
		} else {
			return rational(integer(rat), integer(1));
		}
	}

	@Override
	public IReal real(String doubleValue) {
		return new BigDecimalValue(new BigDecimal(doubleValue));
	}

	@Override
	public IReal real(String s, int p) throws NumberFormatException {
		return new BigDecimalValue(new BigDecimal(s, new MathContext(p)));
	}

	@Override
	public IReal real(double value) {
		return new BigDecimalValue(BigDecimal.valueOf(value));
	}

	@Override
	public IReal real(double value, int p) {
		return new BigDecimalValue(new BigDecimal(value, new MathContext(p)));
	}

	@Override
	public int getPrecision() {
		return currentPrecision.get();
	}

	@Override
	public int setPrecision(int p) {
		return currentPrecision.getAndSet(p);
	}

	@Override
	public IReal pi(int precision) {
		return BigDecimalValue.pi(precision);
	}

	@Override
	public IReal e(int precision) {
		return BigDecimalValue.e(precision);
	}

	@Override
	public IString string(String value) {
		return new StringValue(value);
	}

	@Override
	public IString string(int[] chars) {
		StringBuilder b = new StringBuilder(chars.length);
		for (int ch : chars) {
			b.appendCodePoint(ch);
		}
		return string(b.toString());
	}

	@Override
	public IString string(int ch) {
		StringBuilder b = new StringBuilder(1);
		b.appendCodePoint(ch);
		return string(b.toString());
	}

	@Override
	public IBool bool(boolean value) {
		return BoolValue.getBoolValue(value);
	}

	@Override
	public IDateTime date(int year, int month, int day) {
		return new DateTimeValues.DateValue(year, month, day);
	}

	@Override
	public IDateTime time(int hour, int minute, int second, int millisecond) {
		return new DateTimeValues.TimeValue(hour, minute, second, millisecond);
	}

	@Override
	public IDateTime time(int hour, int minute, int second, int millisecond,
						  int hourOffset, int minuteOffset) {
		return new DateTimeValues.TimeValue(hour, minute, second, millisecond, hourOffset, minuteOffset);
	}

	@Override
	public IDateTime datetime(int year, int month, int day, int hour,
							  int minute, int second, int millisecond) {
		return new DateTimeValues.DateTimeValue(year, month, day, hour, minute, second, millisecond);
	}

	@Override
	public IDateTime datetime(int year, int month, int day, int hour,
							  int minute, int second, int millisecond, int hourOffset,
							  int minuteOffset) {
		return new DateTimeValues.DateTimeValue(year, month, day, hour, minute, second, millisecond, hourOffset, minuteOffset);
	}

	@Override
	public IDateTime datetime(long instant) {
		return new DateTimeValues.DateTimeValue(instant);
	}

	@Override
	public ISourceLocation sourceLocation(URI uri, int offset, int length) {
		if (offset < 0) throw new IllegalArgumentException("offset should be positive");
		if (length < 0) throw new IllegalArgumentException("length should be positive");

		if (offset < Byte.MAX_VALUE && length < Byte.MAX_VALUE) {
			return new SourceLocationValues.ByteByte(uri, (byte) offset, (byte) length);
		}

		if (offset < Character.MAX_VALUE && length < Character.MAX_VALUE) {
			return new SourceLocationValues.CharChar(uri, (char) offset, (char) length);
		}

		return new SourceLocationValues.IntInt(uri, offset, length);
	}

	@Override
	public ISourceLocation sourceLocation(URI uri, int offset, int length, int beginLine, int endLine, int beginCol, int endCol) {
		if (offset < 0) throw new IllegalArgumentException("offset should be positive");
		if (length < 0) throw new IllegalArgumentException("length should be positive");
		if (beginLine < 0) throw new IllegalArgumentException("beginLine should be positive");
		if (beginCol < 0) throw new IllegalArgumentException("beginCol should be positive");
		if (endCol < 0) throw new IllegalArgumentException("endCol should be positive");
		if (endLine < beginLine)
			throw new IllegalArgumentException("endLine should be larger than or equal to beginLine");
		if (endLine == beginLine && endCol < beginCol)
			throw new IllegalArgumentException("endCol should be larger than or equal to beginCol, if on the same line");

		if (offset < Character.MAX_VALUE
				&& length < Character.MAX_VALUE
				&& beginLine < Byte.MAX_VALUE
				&& endLine < Byte.MAX_VALUE
				&& beginCol < Byte.MAX_VALUE
				&& endCol < Byte.MAX_VALUE) {
			return new SourceLocationValues.CharCharByteByteByteByte(uri, (char) offset, (char) length, (byte) beginLine, (byte) endLine, (byte) beginCol, (byte) endCol);
		} else if (offset < Character.MAX_VALUE
				&& length < Character.MAX_VALUE
				&& beginLine < Character.MAX_VALUE
				&& endLine < Character.MAX_VALUE
				&& beginCol < Character.MAX_VALUE
				&& endCol < Character.MAX_VALUE) {
			return new SourceLocationValues.CharCharCharCharCharChar(uri, (char) offset, (char) length, (char) beginLine, (char) endLine, (char) beginCol, (char) endCol);
		} else if (beginLine < Character.MAX_VALUE
				&& endLine < Character.MAX_VALUE
				&& beginCol < Byte.MAX_VALUE
				&& endCol < Byte.MAX_VALUE) {
			return new SourceLocationValues.IntIntCharCharByteByte(uri, offset, length, (char) beginLine, (char) endLine, (byte) beginCol, (byte) endCol);
		} else if (beginCol < Byte.MAX_VALUE
				&& endCol < Byte.MAX_VALUE) {
			return new SourceLocationValues.IntIntIntIntByteByte(uri, offset, length, beginLine, endLine, (byte) beginCol, (byte) endCol);
		}

		return new SourceLocationValues.IntIntIntIntIntInt(uri, offset, length, beginLine, endLine, beginCol, endCol);
	}

	@Override
	public ISourceLocation sourceLocation(String path, int offset, int length, int beginLine, int endLine, int beginCol, int endCol) {
		try {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			return sourceLocation(new URI("file", "", path, null), offset, length, beginLine, endLine, beginCol, endCol);
		} catch (URISyntaxException e) {
			throw new FactParseError("Illegal path syntax: " + path, e);
		}
	}

	@Override
	public ISourceLocation sourceLocation(URI uri) {
		return new SourceLocationValues.OnlyURI(uri);
	}

	@Override
	public ISourceLocation sourceLocation(String path) {
		try {
			if (!path.startsWith("/"))
				path = "/" + path;
			return sourceLocation(new URI("file", "", path, null));
		} catch (URISyntaxException e) {
			throw new FactParseError("Illegal path syntax: " + path, e);
		}
	}

}
