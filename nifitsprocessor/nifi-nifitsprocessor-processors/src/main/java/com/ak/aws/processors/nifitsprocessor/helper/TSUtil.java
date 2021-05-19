package com.ak.aws.processors.nifitsprocessor.helper;

import java.util.List;

import org.apache.nifi.logging.ComponentLog;

import com.amazonaws.services.timestreamwrite.model.DimensionValueType;
import com.amazonaws.services.timestreamwrite.model.MeasureValueType;
import com.amazonaws.services.timestreamwrite.model.TimeUnit;

public class TSUtil {

	private ComponentLog logger;

	public TSUtil(ComponentLog componentLog) {
		this.logger = componentLog;

	}

	public MeasureValueType getMeasureValueType(String type) {
		try {
			return MeasureValueType.fromValue(type.trim().toUpperCase());

		} catch (Exception e) {
			logger.warn("TimestreamProcessorUtil : unrecognized type, casting to VARCHAR");
			return MeasureValueType.VARCHAR;
		}
	}

	public DimensionValueType getDimensionValueType(String type) {
		try {
			return DimensionValueType.fromValue(type.trim().toUpperCase());
		} catch (Exception e) {
			logger.warn("TimestreamProcessorUtil : unrecognized type, casting to VARCHAR");
			return DimensionValueType.VARCHAR;
		}
	}
	
	public boolean isListNullOrEmpty(List<?> list) {
		try {
			if (list == null || list.size() == 0) {
				return true;
			}
		} catch (Exception e) {

		}
		return false;
	}
	
	public boolean isValueNullOrEmpty(String x) {
		try {
			if (x == null || x.trim().equalsIgnoreCase("")) {
				return true;
			}
		} catch (Exception e) {
		}
		return false;
	}
	
	
	public boolean isNumericPositive(String x) {
		try{
			return Long.valueOf(x.trim()) > 0;
		}catch (Exception e ) {
		}
		return false;
	}
	
	public TimeUnit getTimeUnit(String x) {
		try {
			return TimeUnit.fromValue(x.trim().toUpperCase());
		}catch(Exception e) {
			logger.warn("TimestreamProcessorUtil : unrecognized timeunit, defaulting to MILLISECONDS");
		}
		
		return TimeUnit.MILLISECONDS;
	}

}
