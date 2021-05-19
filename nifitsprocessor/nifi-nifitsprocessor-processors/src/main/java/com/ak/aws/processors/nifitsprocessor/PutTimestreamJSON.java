package com.ak.aws.processors.nifitsprocessor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.stream.io.StreamUtils;

import com.ak.aws.processors.nifitsprocessor.exceptions.InvalidTimestreamInputException;
import com.ak.aws.processors.nifitsprocessor.helper.TSData;
import com.ak.aws.processors.nifitsprocessor.helper.TSDimension;
import com.ak.aws.processors.nifitsprocessor.helper.TSMeasure;
import com.ak.aws.processors.nifitsprocessor.helper.TSUtil;
import com.amazonaws.services.timestreamwrite.model.Dimension;
import com.amazonaws.services.timestreamwrite.model.DimensionValueType;
import com.amazonaws.services.timestreamwrite.model.MeasureValueType;
import com.amazonaws.services.timestreamwrite.model.Record;
import com.amazonaws.services.timestreamwrite.model.TimeUnit;
import com.amazonaws.services.timestreamwrite.model.WriteRecordsRequest;
import com.amazonaws.services.timestreamwrite.model.WriteRecordsResult;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PutTimestreamJSON extends AbstractTimestreamWriteProcessor {

	private Set<Relationship> relationships;
	WriteRecordsResult writeRecordsResult;
	String dbName;
	String tblName;
	Map<String, String> attributes;
	StringWriter sw;
	Record r;

	public static final List<PropertyDescriptor> properties = Collections
			.unmodifiableList(Arrays.asList(AWS_CREDENTIALS_PROVIDER_SERVICE, REGION, ACCESS_KEY, SECRET_KEY,
					CREDENTIALS_FILE, TIMEOUT, DB_NAME, TBL_NAME));

	@Override
	protected void init(ProcessorInitializationContext context) {
		final Set<Relationship> relationships = new HashSet<Relationship>();
		relationships.add(REL_SUCCESS);
		relationships.add(REL_FAILURE);
		this.relationships = Collections.unmodifiableSet(relationships);
	}

	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}

	private List<Record> getTSRecord(String jsonInput, ComponentLog componentLog)
			throws IOException, JsonParseException, JsonMappingException, InvalidTimestreamInputException {
		ObjectMapper jsonMapper = new ObjectMapper();
		jsonMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
		jsonMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
		jsonMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

		TSUtil util = new TSUtil(componentLog);
		String dimName;
		String dimValue;
		DimensionValueType dimType;

		String parentTime;
		String parentTimeUnit; // TODO unused

		String mName;
		String mValue;
		String mTime;
		String mTimeUnit; // TODO unused
		MeasureValueType mType;
		String processingStartTime = String.valueOf(System.currentTimeMillis());
		String timeVal;
		com.amazonaws.services.timestreamwrite.model.TimeUnit timeUnit =null;

		List<TSDimension> dims;
		List<TSMeasure> measures;

		List<Dimension> dimensions;
		List<Record> records = new ArrayList<Record>();

		TSData[] tsDataArray = jsonMapper.readValue(jsonInput, TSData[].class);

		for (TSData tsData : tsDataArray) {
			// One record in the Stream
			dimensions = new ArrayList<Dimension>();
			dims = tsData.getDimensions();
			if (util.isListNullOrEmpty(dims)) {
				throw new InvalidTimestreamInputException("No timestream dimensions found. Please check your input");
			}
			for (TSDimension dim : dims) {
				// dimension start
				dimName = dim.getDimensionName();
				dimValue = dim.getDimensionValue();
				if (util.isValueNullOrEmpty(dimName)) {
					throw new InvalidTimestreamInputException("Dimension should have a name. Please check your input");
				}
				if (util.isValueNullOrEmpty(dimValue)) {
					throw new InvalidTimestreamInputException("Dimension should have a value. Please check your input");
				}
				dimType = util.getDimensionValueType(dim.getDimensionType());
				dimensions.add(new Dimension().withName(dimName).withValue(dimValue).withDimensionValueType(dimType));

				// dimension end
			}
			/* TIME */
			parentTime = tsData.getTime(); // lets default to long
			parentTimeUnit=tsData.getTimeUnit();// this is not used

			measures = tsData.getMeasures();
			if (util.isListNullOrEmpty(dims)) {
				throw new InvalidTimestreamInputException("No timestream measure found. Please check your input");
			}
			for (TSMeasure m : measures) {
				
				
				// each measure start

				r= new Record();
				
				timeVal = null;

				mName = m.getMeasureName();
				mValue = m.getMeasureValue();

				mTime = m.getTime();
				mTimeUnit = m.getTimeUnit();// this is not used

				if (util.isValueNullOrEmpty(mName)) {
					throw new InvalidTimestreamInputException("Measure should have a name. Please check your input");
				}

				if (util.isValueNullOrEmpty(mValue)) {
					throw new InvalidTimestreamInputException("Measure should have a value. Please check your input");
				}
				
				mType = util.getMeasureValueType(m.getMeasureType());
				
				r=r.withDimensions(dimensions).withMeasureName(mName).withMeasureValue(mValue).withMeasureValueType(mType);

				if (!util.isValueNullOrEmpty(mTime)) {
					timeVal = mTime;
					timeUnit=util.getTimeUnit(mTimeUnit);
				} else {
					if (!util.isValueNullOrEmpty(parentTime)) {
						timeVal = parentTime;
						timeUnit = util.getTimeUnit(parentTimeUnit);
					}
				}

				if (timeVal == null) {
					timeVal = processingStartTime;
					timeUnit=TimeUnit.MILLISECONDS;
				}
				
				r=r.withTime(timeVal).withTimeUnit(timeUnit);
				
				
				if(util.isNumeric(m.getVersion())) {
					r = r.withVersion(Long.valueOf(m.getVersion().trim()));
					
				}
				
				records.add(r);

				
				// each measure end
			}
			// END processing one record
		}
		return records;
	}

	private Map<String, String> getAttributes(FlowFile flowFile, WriteRecordsResult writeRecordsResult) {
		attributes = new HashMap<String, String>();
		attributes.putAll(flowFile.getAttributes());
		attributes.put(CoreAttributes.MIME_TYPE.key(), "application/json");
		attributes.put("timestream.insert.status",
				String.valueOf(writeRecordsResult.getSdkHttpMetadata().getHttpStatusCode()));
		return attributes;

	}

	@Override
	public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {

		FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}

		final long startNanos = System.nanoTime();

		dbName = context.getProperty(DB_NAME).evaluateAttributeExpressions(flowFile).getValue();
		tblName = context.getProperty(TBL_NAME).evaluateAttributeExpressions(flowFile).getValue();

		final List<Record> records;
		try {

			final byte[] content = new byte[(int) flowFile.getSize()];
			session.read(flowFile, in -> StreamUtils.fillBuffer(in, content, true));
			records = getTSRecord(new String(content), getLogger());
			WriteRecordsRequest writeRecordsRequest = new WriteRecordsRequest().withDatabaseName(dbName)
					.withTableName(tblName).withRecords(records);

			writeRecordsResult = client.writeRecords(writeRecordsRequest);
			flowFile = session.putAllAttributes(flowFile, getAttributes(flowFile, writeRecordsResult));
			session.transfer(flowFile, REL_SUCCESS);

			
			long transmissionMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
			session.getProvenanceReporter().send(flowFile, tblName, transmissionMillis);

		} catch (Exception e) {
			sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			getLogger().info("Error processing timestream record. Check stacktrace " + sw.toString());
			session.transfer(flowFile, REL_FAILURE);
		}

	}

}
