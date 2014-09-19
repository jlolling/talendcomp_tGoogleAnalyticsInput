package de.cimt.talendcomp.googleanalytics;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

public class Util {
	
	private final static Map<String, NumberFormat> numberformatMap = new HashMap<String, NumberFormat>();
	
	public static NumberFormat getNumberFormat(String localeStr) {
		String key = localeStr + "-" + Thread.currentThread().getName();
		NumberFormat nf = (NumberFormat) numberformatMap.get(key);
		if (nf == null) {
			Locale locale = new Locale(localeStr);
			nf = NumberFormat.getInstance(locale);
			numberformatMap.put(key, nf);
		}
		return nf;
	}
	
	/**
	 * converts a list of values into a collection
	 * @param valuesSeparated
	 * @param dataType String|date|Double|
	 * @param delimiter
	 * @return
	 */
	public static List<Object> convertToList(String valuesSeparated, String dataType, String delimiter, String pattern) throws Exception {
		List<Object> resultList = new ArrayList<Object>();
		StringTokenizer st = new StringTokenizer(valuesSeparated, delimiter);
		while (st.hasMoreElements()) {
			String value = st.nextToken();
			resultList.add(convertToDatatype(value, dataType, pattern));
		}
		return resultList;
	}
	
	public static Object convertToDatatype(String value, String dataType, String options) throws Exception {
		if (value != null && value.trim().isEmpty() == false) {
			if ("String".equalsIgnoreCase(dataType)) {
				return value;
			} else if ("BigDecimal".equalsIgnoreCase(dataType)) {
				return convertToBigDecimal(value);
			} else if ("Boolean".equalsIgnoreCase(dataType)) {
				return convertToBoolean(value);
			} else if ("Date".equalsIgnoreCase(dataType)) {
				return convertToDate(value, options);
			} else if ("Double".equalsIgnoreCase(dataType)) {
				return convertToDouble(value, options);
			} else if ("Float".equalsIgnoreCase(dataType)) {
				return convertToFloat(value, options);
			} else if ("Int".equalsIgnoreCase(dataType) || "Integer".equalsIgnoreCase(dataType)) {
				return convertToInteger(value, options);
			} else if ("Long".equalsIgnoreCase(dataType)) {
				return convertToLong(value, options);
			} else if ("Timestamp".equalsIgnoreCase(dataType)) {
				return convertToTimestamp(value, options);
			} else {
				throw new Exception("Unsupported dataType:" + dataType);
			}
		} else {
			return null;
		}
	}
	
	/**
	 * concerts the string format into a Date
	 * @param dateString
	 * @param pattern
	 * @return the resulting Date
	 */
	public static Date convertToDate(String dateString, String pattern) throws Exception {
		if (dateString == null || dateString.trim().isEmpty()) {
			return null;
		}
		if (pattern == null || pattern.isEmpty()) {
			throw new Exception("convertToDate failed: pattern cannot be null or empty");
		}
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(pattern);
			return sdf.parse(dateString.trim());
		} catch (Throwable t) {
			throw new Exception("Failed to convert string to date:" + t.getMessage(), t);
		}
	}
	
	public static Timestamp convertToTimestamp(String dateString, String pattern) throws Exception {
		Date date = convertToDate(dateString, pattern);
		if (date != null) {
			return new Timestamp(date.getTime());
		} else {
			return null;
		}
	}

	public static Boolean convertToBoolean(String value) throws Exception {
		if (value == null || value.trim().isEmpty()) {
			return null;
		} else {
			if ("true".equals(value.trim())) {
				return true;
			} else if ("false".equals(value.trim())) {
				return false;
			} else {
				throw new Exception("Value:" + value + " is not a boolean value!");
			}
		}
	}

	public static Double convertToDouble(String value, String locale) throws Exception {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return getNumberFormat(locale).parse(value.trim()).doubleValue();
	}

	public static Integer convertToInteger(String value, String locale) throws Exception {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return getNumberFormat(locale).parse(value.trim()).intValue();
	}
	
	public static Float convertToFloat(String value, String locale) throws Exception {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return getNumberFormat(locale).parse(value.trim()).floatValue();
	}

	public static Long convertToLong(String value, String locale) throws Exception {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return getNumberFormat(locale).parse(value.trim()).longValue();
	}

	public static BigDecimal convertToBigDecimal(String value) throws Exception {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(value.trim());
		} catch (RuntimeException e) {
			throw new Exception("convertToBigDecimal:" + value + " failed:" + e.getMessage(), e);
		}
	}

}
