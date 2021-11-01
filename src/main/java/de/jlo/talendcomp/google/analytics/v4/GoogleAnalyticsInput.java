/**
 * Copyright 2015 Jan Lolling jan.lolling@gmail.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jlo.talendcomp.google.analytics.v4;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.analyticsreporting.v4.AnalyticsReporting;
import com.google.api.services.analyticsreporting.v4.AnalyticsReporting.Reports.BatchGet;
import com.google.api.services.analyticsreporting.v4.model.ColumnHeader;
import com.google.api.services.analyticsreporting.v4.model.DateRange;
import com.google.api.services.analyticsreporting.v4.model.DateRangeValues;
import com.google.api.services.analyticsreporting.v4.model.Dimension;
import com.google.api.services.analyticsreporting.v4.model.GetReportsRequest;
import com.google.api.services.analyticsreporting.v4.model.GetReportsResponse;
import com.google.api.services.analyticsreporting.v4.model.Metric;
import com.google.api.services.analyticsreporting.v4.model.MetricHeaderEntry;
import com.google.api.services.analyticsreporting.v4.model.OrderBy;
import com.google.api.services.analyticsreporting.v4.model.Report;
import com.google.api.services.analyticsreporting.v4.model.ReportRequest;
import com.google.api.services.analyticsreporting.v4.model.ReportRow;
import com.google.api.services.analyticsreporting.v4.model.Segment;

import de.jlo.talendcomp.google.analytics.DimensionValue;
import de.jlo.talendcomp.google.analytics.GoogleAnalyticsBase;
import de.jlo.talendcomp.google.analytics.MetricValue;
import de.jlo.talendcomp.google.analytics.Util;

public class GoogleAnalyticsInput extends GoogleAnalyticsBase {

	private static final Map<String, GoogleAnalyticsInput> clientCache = new HashMap<String, GoogleAnalyticsInput>();
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private final JsonFactory JSON_FACTORY = new JacksonFactory();
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	private String startDate = null;
	private String endDate = null;
	private String metrics = null;
	private String dimensions = null;
	private String sorts = null;
	private String filters = null;
	private String segment = null;
	private String profileId;
	private int fetchSize = 0;
	private String pageToken = null;
	private int lastFetchedRowCount = 0;
	private int overallPlainRowCount = 0;
	private int currentPlainRowIndex = 0;
	private int startIndex = 1;
	private List<DimensionValue> currentResultRowDimensionValues;
	private List<MetricValue> currentResultRowMetricValues;
	private Date currentDate;
	private static final String DATE_DIM = "ga:date";
	private boolean excludeDate = false;
	private static final String SEGMENT_DIM = "ga:segment";
	private boolean excludeSegmentDimension = false;
	private int segmentDimensionIndex = -1;
	private List<Dimension> listDimensions = null;
	private List<Metric> listMetrics = null;
	private List<OrderBy> listOrderBys = null;
	private AnalyticsReporting analyticsClient;
	private ReportRequest reportRequest;
	private String reportRequestJSON = null;
	private boolean useJsonTemplateToBuildRequest = false;
	private BatchGet request;
	private GetReportsResponse response;
	private Report report = null;
	private List<ReportRow> lastResultSet = null;
	private boolean addTotalsRecord = false;
	private boolean totalsDelivered = false;
	public static final String SAMPLING_LEVEL_DEFAULT = "DEFAULT";
	public static final String V3_SAMPLING_LEVEL_FASTER = "FASTER";
	public static final String V3_SAMPLING_LEVEL_HIGHER_PRECISION = "HIGHER_PRECISION";
	public static final String V4_SAMPLING_LEVEL_SMALL = "SMALL";
	public static final String V4_SAMPLING_LEVEL_LARGE = "LARGE";
	private String samplingLevel = SAMPLING_LEVEL_DEFAULT;
	private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
	private int errorCode = 0;
	private boolean success = true;

	public static void putIntoCache(String key, GoogleAnalyticsInput gai) {
		clientCache.put(key, gai);
	}
	
	public static GoogleAnalyticsInput getFromCache(String key) {
		return clientCache.get(key);
	}
	
	/**
	 * set the maximum rows per fetch
	 * 
	 * @param fetchSize
	 */
	public void setFetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
	}

	public void setProfileId(String profileId) {
		if (profileId == null || profileId.trim().isEmpty()) {
			throw new IllegalArgumentException("Profile-ID (View-ID) cannot be null or empty");
		}
		this.profileId = profileId;
	}

	public void setProfileId(int profileId) {
		if (profileId == 0) {
			throw new IllegalArgumentException("Profile-ID (View-ID) must be greater than 0");
		}
		this.profileId = String.valueOf(profileId);
	}

	public void setProfileId(Long profileId) {
		if (profileId == null) {
			throw new IllegalArgumentException("profileId cannot be null.");
		}
		this.profileId = Long.toString(profileId);
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * Format: yyyy-MM-dd
	 * @param yyyyMMdd
	 */
	public void setStartDate(String yyyyMMdd) {
		this.startDate = yyyyMMdd;
	}

	public void setStartDate(Date startDate) {
		this.startDate = sdf.format(startDate);
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * Format: yyyy-MM-dd
	 * @param yyyyMMdd
	 */
	public void setEndDate(String yyyyMMdd) {
		this.endDate = yyyyMMdd;
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * 
	 * @param yyyyMMdd
	 */
	public void setEndDate(Date endDate) {
		this.endDate = sdf.format(endDate);
	}

	public void setMetrics(String metrics) {
		if (metrics == null || metrics.trim().isEmpty()) {
			throw new IllegalArgumentException("Metrics cannot be null or empty");
		}
		this.metrics = metrics.trim();
	}

	public void setDimensions(String dimensions) {
		if (dimensions != null && dimensions.trim().isEmpty() == false) {
			this.dimensions = dimensions.trim();
		} else {
			this.dimensions = null;
		}
	}

	public void setSorts(String sorts) {
		if (sorts != null && sorts.trim().isEmpty() == false) {
			this.sorts = sorts;
		} else {
			this.sorts = null;
		}
	}

	/**
	 * use operators like:
	 * == exact match
	 * =@ contains
	 * =~ matches regular expression
	 * != does not contains
	 * separate with , for OR and ; for AND
	 * @param filters
	 */
	public void setFilters(String filters) {
		if (filters != null && filters.trim().isEmpty() == false) {
			this.filters = filters.trim();
		} else {
			this.filters = null;
		}
	}

	public void setSegment(String segment) {
		if (segment != null && segment.trim().isEmpty() == false) {
			this.segment = segment;
		} else {
			this.segment = null;
		}
	}

	public void initializeAnalyticsClient() throws Exception {
		// Authorization.
		final Credential credential = authorize();
        // Set up and return Google Analytics API client.
		analyticsClient = new AnalyticsReporting.Builder(
			HTTP_TRANSPORT, 
			JSON_FACTORY, 
			new HttpRequestInitializer() {
				@Override
				public void initialize(final HttpRequest httpRequest) throws IOException {
					credential.initialize(httpRequest);
					httpRequest.setConnectTimeout(getTimeoutInSeconds() * 1000);
					httpRequest.setReadTimeout(getTimeoutInSeconds() * 1000);
				}
			})
			.setApplicationName(getApplicationName())
			.build();
	}
	
	private void executeDataQuery() throws Exception {
		response = null;
		if (profileId == null || profileId.length() < 5) {
			throw new Exception("profileId is missing or not long enough");
		}
		if (startDate == null || startDate.trim().isEmpty()) {
			throw new Exception("Missing start date!");
		}
		if (endDate == null || endDate.trim().isEmpty()) {
			throw new Exception("Missing end date!");
		}
		if (useJsonTemplateToBuildRequest) {
			reportRequest = buildReportRequestFromJsonTemplate();
			reportRequest.setViewId(profileId);
			reportRequest.setDateRanges(getDateRanges());
			reportRequest.setHideTotals(addTotalsRecord == false);
			if (samplingLevel != null) {
				reportRequest.setSamplingLevel(samplingLevel);
			}
		} else {
			reportRequest = new ReportRequest();
			reportRequest.setViewId(profileId);
			reportRequest.setDateRanges(getDateRanges());
			// we have to add segments before dimensions because we have to check dimensions for the ga:dimension dim!
			setupSegments();
			reportRequest.setDimensions(buildDimensions());
			reportRequest.setMetrics(buildMetrics());
			if (filters != null && filters.trim().isEmpty() == false) {
				reportRequest.setFiltersExpression(filters);
			}
			reportRequest.setOrderBys(buildOrderBys());
			reportRequest.setHideTotals(addTotalsRecord == false);
			if (samplingLevel != null) {
				reportRequest.setSamplingLevel(samplingLevel);
			}
		}
		setupGetRequest();
		doExecute();
		overallPlainRowCount = 0;
		totalsDelivered = false;
		startIndex = 1;
		maxCountNormalizedValues = 0;
		currentNormalizedValueIndex = 0;
	}
	
	private void setupGetRequest() throws IOException {
		if (fetchSize > 0) {
			reportRequest.setPageSize(fetchSize);
		}
		reportRequest.setPageToken(pageToken);
		GetReportsRequest getRequest = new GetReportsRequest();
		getRequest.setReportRequests(Arrays.asList(reportRequest));
		request = analyticsClient
				.reports()
				.batchGet(getRequest);
	}
	
	private List<DateRange> getDateRanges() {
		DateRange range = new DateRange();
		range.setStartDate(startDate);
		range.setEndDate(endDate);
		return Arrays.asList(range);
	}
	
	public void executeQuery() throws Exception {
		executeDataQuery();
	}
	
	private int maxRetriesInCaseOfErrors = 5;
	private int currentAttempt = 0;
	private String errorMessage = null;
	
	private void doExecute() throws Exception {
		int waitTime = 1000;
		for (currentAttempt = 0; currentAttempt < maxRetriesInCaseOfErrors; currentAttempt++) {
			errorCode = 0;
			try {
				response = request.execute();
				success = true;
				break;
			} catch (IOException ioe) {
				success = false;
				warn("Got error:" + ioe.getMessage());
				errorMessage = ioe.getMessage();
				if (ioe instanceof HttpResponseException) {
					errorCode = ((HttpResponseException) ioe).getStatusCode();
				}
				if (Util.canBeIgnored(ioe) == false) {
					error("Stop processing because of this error does not allow a retry.", null);
					throw ioe;
				}
				if (currentAttempt == (maxRetriesInCaseOfErrors - 1)) {
					error("All repetition of requests failed:" + ioe.getMessage(), ioe);
					throw ioe;
				} else {
					// wait
					try {
						info("Retry request in " + waitTime + "ms");
						Thread.sleep(waitTime);
					} catch (InterruptedException ie) {}
					int random = (int) Math.random() * 500;
					waitTime = (waitTime * 2) + random;
				}
			}
		}
		report = response.getReports().get(0);
		// get the page token for the next request
		pageToken = report.getNextPageToken();
		if (report != null) {
			lastResultSet = report.getData().getRows();
		} else {
			lastResultSet = null;
		}
		if (lastResultSet == null) {
			// fake an empty result set to avoid breaking further processing
			lastResultSet = new ArrayList<ReportRow>();
		}
		lastFetchedRowCount = lastResultSet.size();
		currentPlainRowIndex = 0;
	}

	/**
	 * checks if more result set available
	 * @return true if more data sets available
	 * @throws Exception if the necessary next request fails 
	 */
	public boolean hasNextPlainRecord() throws Exception {
		if (response == null) {
			throw new IllegalStateException("No query executed before");
		}
		if (request == null) {
			throw new IllegalStateException("No request object available");
		}
		if (addTotalsRecord && totalsDelivered == false) {
			return true;
		}
		// check if we are at the end of previously fetched data
		// we need fetch data if
		// current index reached the max of last fetched data
		// and count of last fetched data == maxResult what indicated that there
		// is more than we have currently fetched
		if (currentPlainRowIndex == lastFetchedRowCount
				&& lastFetchedRowCount > 0
				&& (fetchSize == 0 || (lastFetchedRowCount == fetchSize && pageToken != null))) {
			startIndex = startIndex + lastFetchedRowCount;
			setupGetRequest();
			doExecute();
		}
		if (lastFetchedRowCount > 0 && currentPlainRowIndex < lastFetchedRowCount) {
			return true;
		} else {
			return false;
		}
	}
	
	public List<String> getNextPlainRecord() {
		if (response == null) {
			throw new IllegalStateException("No query executed before");
		}
		overallPlainRowCount++;
		if (addTotalsRecord && totalsDelivered == false) {
			totalsDelivered = true;
			return getTotalsDataset();
		} else {
			ReportRow row = lastResultSet.get(currentPlainRowIndex++); 
			return buildRecord(row);
		}
	}

	private List<String> buildRecord(ReportRow row) {
		List<String> record = new ArrayList<String>();
		List<String> dimValues = row.getDimensions();
		for (int i = 0; i < dimValues.size(); i++) {
			if (excludeSegmentDimension && i == segmentDimensionIndex) {
				// add nothing!
			} else {
				String value = dimValues.get(i);
				record.add(value);
			}
		}
		List<DateRangeValues> listDateRangeValues = row.getMetrics();
		// we currently allow only one date range
		DateRangeValues drv = listDateRangeValues.get(0);
		for (String value : drv.getValues()) {
			record.add(value);
		}
		return record;
	}
	
	public boolean nextNormalizedRecord() throws Exception {
		if (maxCountNormalizedValues == 0) {
			// at start we do not have any records
			if (hasNextPlainRecord()) {
				buildNormalizedRecords(getNextPlainRecord());
			}
		}
		if (maxCountNormalizedValues > 0) {
			if (currentNormalizedValueIndex < maxCountNormalizedValues) {
				currentNormalizedValueIndex++;
				return true;
			} else if (currentNormalizedValueIndex == maxCountNormalizedValues) {
				// the end of the normalized rows reached, fetch the next data row
				if (hasNextPlainRecord()) {
					if (buildNormalizedRecords(getNextPlainRecord())) {
						currentNormalizedValueIndex++;
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public DimensionValue getCurrentDimensionValue() {
		if (currentNormalizedValueIndex == 0) {
			throw new IllegalStateException("Call nextNormalizedRecord() at first!");
		}
		if (currentNormalizedValueIndex <= currentResultRowDimensionValues.size()) {
			return currentResultRowDimensionValues.get(currentNormalizedValueIndex - 1);
		} else {
			return null;
		}
	}
	
	public MetricValue getCurrentMetricValue() {
		if (currentNormalizedValueIndex == 0) {
			throw new IllegalStateException("Call nextNormalizedRecord() at first!");
		}
		if (currentNormalizedValueIndex <= currentResultRowMetricValues.size()) {
			return currentResultRowMetricValues.get(currentNormalizedValueIndex - 1);
		} else {
			return null;
		}
	}

	private int maxCountNormalizedValues = 0;
	private int currentNormalizedValueIndex = 0;
	
	private void setMaxCountNormalizedValues(int count) {
		if (count > maxCountNormalizedValues) {
			maxCountNormalizedValues = count;
		}
	}
	
	private boolean buildNormalizedRecords(List<String> oneRow) {
		maxCountNormalizedValues = 0;
		currentNormalizedValueIndex = 0;
		buildDimensionValues(oneRow);
		buildMetricValues(oneRow);
		return maxCountNormalizedValues > 0;
	}
	
	private List<DimensionValue> buildDimensionValues(List<String> oneRow) {
		int index = 0;
		currentDate = null;
		final List<DimensionValue> oneRowDimensionValues = new ArrayList<DimensionValue>();
		for (; index < listDimensions.size(); index++) {
			String dimName = listDimensions.get(index).getName();
			if (excludeSegmentDimension && dimName.toLowerCase().trim().equals(SEGMENT_DIM)) {
				continue; // skip over the segment dimension if automatically added
			}
			DimensionValue dm = new DimensionValue();
			dm.name = dimName.trim();
			dm.value = oneRow.get(index);
			dm.rowNum = overallPlainRowCount;
        	if (excludeDate && DATE_DIM.equals(dm.name.trim().toLowerCase())) {
        		try {
        			if (dm.value != null) {
    					currentDate = dateFormatter.parse(dm.value);
        			}
				} catch (ParseException e) {
					currentDate = null;
				}
        	} else {
    			oneRowDimensionValues.add(dm);
        	}
		}
		currentResultRowDimensionValues = oneRowDimensionValues;
		setMaxCountNormalizedValues(currentResultRowDimensionValues.size());
		return oneRowDimensionValues;
	}

	private List<MetricValue> buildMetricValues(List<String> oneRow) {
		int index = 0;
		final List<MetricValue> oneRowMetricValues = new ArrayList<MetricValue>();
		for (; index < listMetrics.size(); index++) {
			MetricValue mv = new MetricValue();
			Metric metric = listMetrics.get(index);
			mv.name = metric.getAlias();
			if (mv.name == null) {
				mv.name = metric.getExpression();
			}
			mv.rowNum = overallPlainRowCount;
			int countDimensions = listDimensions.size();
			if (excludeSegmentDimension) {
				countDimensions--;
			}
			String valueStr = oneRow.get(index + countDimensions);
			try {
				mv.value = Util.convertToDouble(valueStr, Locale.ENGLISH.toString());
				oneRowMetricValues.add(mv);
			} catch (Exception e) {
				throw new IllegalStateException("Failed to build a double value for the metric:" + mv.name + " and value String:" + valueStr);
			}
		}
		currentResultRowMetricValues = oneRowMetricValues;
		setMaxCountNormalizedValues(currentResultRowMetricValues.size());
		return oneRowMetricValues;
	}

	/**
	 * if true, add the totals data set at the end of the 
	 * @param addTotals
	 */
	public void deliverTotalsDataset(boolean addTotals) {
		this.addTotalsRecord = addTotals;
	}

	public List<String> getTotalsDataset() {
		if (report == null) {
			throw new IllegalStateException("No query executed before");
		}
		List<DateRangeValues> listDateRangeValues = report.getData().getTotals();
		List<String> totalResult = new ArrayList<String>();
		// find correct field index
		List<String> columnsFromResult = getColumnNames();
		// listDimensions potentially contains the segment dimension but not columnsFromResult
		int countDimensions = listDimensions.size();
		if (excludeSegmentDimension) {
			countDimensions--;
		}
		for (int i = 0; i < columnsFromResult.size(); i++) {
			if (i < countDimensions) {
				if (excludeSegmentDimension) {
					if (columnsFromResult.get(i).equals(SEGMENT_DIM)) {
						continue;
					}
				}
				totalResult.add("total");
			} else {
				totalResult.add(listDateRangeValues
						.get(0)
						.getValues()
						.get(i - countDimensions));
			}
		}
		return totalResult;
	}

	public int getCurrentIndexOverAll() {
		if (addTotalsRecord && totalsDelivered == false) {
			return 0;
		} else {
			return startIndex + currentPlainRowIndex;
		}
	}

	public List<String> getColumnNames() {
		ColumnHeader columnHeader = report.getColumnHeader();
		List<String> listDimensions = columnHeader.getDimensions();
		List<String> names = new ArrayList<String>();
		for (String name : listDimensions) {
			if (excludeSegmentDimension && name.equals(SEGMENT_DIM)) {
				continue;
			}
			names.add(name);
		}
		List<MetricHeaderEntry> metricHeaders = columnHeader.getMetricHeader().getMetricHeaderEntries();
		for (MetricHeaderEntry metricHeaderEntry : metricHeaders) {
			names.add(metricHeaderEntry.getName());
		}
		return names;
	}

	public List<String> getColumnTypes() {
		ColumnHeader columnHeader = report.getColumnHeader();
		List<String> listDimensions = columnHeader.getDimensions();
		List<String> types = new ArrayList<String>();
		for (String name : listDimensions) {
			if (excludeSegmentDimension && name.equals(SEGMENT_DIM)) {
				continue;
			}
			types.add("STRING");
		}
		List<MetricHeaderEntry> metricHeaders = columnHeader.getMetricHeader().getMetricHeaderEntries();
		for (MetricHeaderEntry metricHeaderEntry : metricHeaders) {
			types.add(metricHeaderEntry.getType());
		}
		return types;
	}
	
	public int getOverAllCountRows() {
		return overallPlainRowCount;
	}

	public boolean containsSampledData() {
		if (report == null) {
			throw new IllegalStateException("No query executed");
		}
		Boolean isGolden = report.getData().getIsDataGolden();
		if (isGolden != null) {
			return isGolden == false;
		} else {
			return true;
		}
	}

	public Long getSampleSize() {
		if (report == null) {
			throw new IllegalStateException("No query executed");
		}
		List<Long> counts = report.getData().getSamplesReadCounts();
		if (counts != null && counts.size() > 0) {
			return counts.get(0);
		} else {
			return 0l;
		}
	}
	
	public Long getSampleSpace() {
		if (report == null) {
			throw new IllegalStateException("No query executed");
		}
		List<Long> sizes = report.getData().getSamplingSpaceSizes();
		if (sizes != null && sizes.size() > 0) {
			return sizes.get(0);
		} else {
			return 0l;
		}
	}

	public Integer getTotalAffectedRows() {
		if (report == null) {
			throw new IllegalStateException("No query executed");
		}
		return startIndex + lastFetchedRowCount;
	}

	public String getSamplingLevel() {
		return samplingLevel;
	}

	public void setSamplingLevel(String samplingLevel) {
		if (SAMPLING_LEVEL_DEFAULT.equals(samplingLevel)) {
			this.samplingLevel = samplingLevel;
		} else if (V3_SAMPLING_LEVEL_FASTER.equals(samplingLevel)) {
			this.samplingLevel = "SMALL";
		} else if (V3_SAMPLING_LEVEL_HIGHER_PRECISION.equals(samplingLevel)) {
			this.samplingLevel = "LARGE";
		} else if (V4_SAMPLING_LEVEL_SMALL.equals(samplingLevel) || V4_SAMPLING_LEVEL_LARGE.equals(samplingLevel)) {
			this.samplingLevel = samplingLevel;
		} else if (samplingLevel == null || samplingLevel.isEmpty()) {
			this.samplingLevel = null;
		} else {
			throw new IllegalArgumentException("Only these sampling levels: " + 
					SAMPLING_LEVEL_DEFAULT + "," + 
					V4_SAMPLING_LEVEL_SMALL + "," + 
					V4_SAMPLING_LEVEL_LARGE + "," + 
					V3_SAMPLING_LEVEL_FASTER + "," + 
					V3_SAMPLING_LEVEL_HIGHER_PRECISION + " are allowed!");
		}
	}

	@Override
	public Date getCurrentDate() throws ParseException {
		return currentDate;
	}

	public void setExcludeDate(boolean excludeDate) {
		this.excludeDate = excludeDate;
	}

	@Override
	public int getErrorCode() {
		return errorCode;
	}

	@Override
	public boolean isSuccess() {
		return success;
	}
	
	public void setMaxRetriesInCaseOfErrors(Integer maxRetriesInCaseOfErrors) {
		if (maxRetriesInCaseOfErrors != null) {
			this.maxRetriesInCaseOfErrors = maxRetriesInCaseOfErrors;
		}
	}

	public String getErrorMessage() {
		return errorMessage;
	}
	
	private List<Dimension> buildDimensions() {
		if (dimensions != null) {
			listDimensions = new ArrayList<Dimension>();
			String[] dimArray = dimensions.split(",");
			for (String dimName : dimArray) {
				Dimension dimension = new Dimension();
				dimension.setName(dimName);
				listDimensions.add(dimension);
				debug("add dimension: " + dimension.toString());
			}
		} else {
			listDimensions = new ArrayList<Dimension>();
		}
		excludeSegmentDimension = false; // reset from a previous run
		if (reportRequest.getSegments() != null && reportRequest.getSegments().size() > 0) {
			// we have segments, let us check if the ga:segment dimension is present
			if (dimensions.toLowerCase().contains(SEGMENT_DIM) == false) {
				excludeSegmentDimension = true;
				Dimension dimension = new Dimension();
				dimension.setName(SEGMENT_DIM);
				listDimensions.add(dimension);
				segmentDimensionIndex = listDimensions.size() - 1;
				debug("add dimension: " + dimension.toString());
				info("Dimension " + SEGMENT_DIM + " added because it is mandatory in case of using segments. It will be excluded in the output.");
			}
		}
		return listDimensions;
	}
	
	private Metric buildMetric(String metricStr) {
		if (metricStr != null && metricStr.trim().isEmpty() == false) {
			metricStr = metricStr.trim();
			Metric metric = new Metric();
			int eqPos = metricStr.indexOf("=");
			if (eqPos != -1) {
				String aliasAndType = metricStr.substring(0, eqPos).trim();
				if (aliasAndType != null && aliasAndType.isEmpty() == false) {
					int posAt = aliasAndType.indexOf("@");
					if (posAt != -1) {
						String alias = aliasAndType.substring(0, posAt).trim();
						if (alias.isEmpty() == false) {
							metric.setAlias(alias);
						}
						if (posAt < aliasAndType.length() - 2) {
							String type = aliasAndType.substring(posAt).trim();
							if (type.isEmpty() == false) {
								metric.setFormattingType(type);
							}
						}
					} else {
						metric.setAlias(aliasAndType);
					}
				}
				if (eqPos < metricStr.length() - 2) {
					String expression = metricStr.substring(eqPos + 1);
					metric.setExpression(expression);
				} else {
					throw new IllegalStateException("Invalid metric (without expression) found: " + metricStr);
				}
			} else {
				metric.setExpression(metricStr);
			}
			return metric;
		} else {
			return null;
		}
	}

	private List<Metric> buildMetrics() {
		if (metrics == null) {
			throw new IllegalStateException("Metrics are not set!");
		}
		listMetrics = new ArrayList<Metric>();
		String[] metricArray = metrics.split(",");
		// a metric can consists of an alias and the expression separated by =
		for (String metricStr : metricArray) {
			Metric metric = buildMetric(metricStr);
			if (metric != null) {
				listMetrics.add(metric);
				debug("add metric: " + metric.toString());
			}
		}
		if (listMetrics.isEmpty()) {
			throw new IllegalStateException("No metrics found in metric string: " + metrics);
		}
		return listMetrics;
	}
	
	private List<OrderBy> buildOrderBys() {
		if (sorts != null) {
			String[] sortArray = sorts.split(",");
			listOrderBys = new ArrayList<OrderBy>();
			for (String sort : sortArray) {
				if (sort != null && sort.trim().isEmpty() == false) {
					sort = sort.trim();
					OrderBy orderBy = new OrderBy();
					if (sort.startsWith("-")) {
						orderBy.setSortOrder("DESCENDING");
						orderBy.setFieldName(sort.substring(1));
					} else {
						orderBy.setSortOrder("ASCENDING");
						orderBy.setFieldName(sort);
					}
					listOrderBys.add(orderBy);
				}
			}
			if (listOrderBys.isEmpty()) {
				listOrderBys = null;
			}
		} else {
			listOrderBys = null;
		}
		return listOrderBys;
	}
	
	private void setupSegments() {
		if (segment != null) {
			Segment seg = new Segment();
			seg.setSegmentId(segment);
			reportRequest.setSegments(Arrays.asList(seg));
		}
	}

	public void setJsonReportTemplate(String reportRequestJSON) {
		if (reportRequestJSON != null && reportRequestJSON.trim().isEmpty() == false) {
			this.reportRequestJSON = reportRequestJSON.trim();
		} else {
			throw new IllegalArgumentException("Json report template cannot be empty or null!");
		}
	}
	
	private ReportRequest buildReportRequestFromJsonTemplate() throws Exception {
		if (reportRequestJSON == null) {
			throw new IllegalStateException("No json template for report provided!");
		}
		JsonObjectParser parser = new JsonObjectParser(JSON_FACTORY);
		StringReader sr = new StringReader(reportRequestJSON);
		GetReportsRequest requestArray = parser.parseAndClose(sr, GetReportsRequest.class);
		if (requestArray.getReportRequests() == null || requestArray.getReportRequests().isEmpty()) {
			throw new Exception("Got empty request array!");
		} else if (requestArray.getReportRequests().size() > 1) {
			warn("The given report request json contains: " + requestArray.getReportRequests().size() + " reports. Only the first one will be executed!");
		}
		return requestArray.getReportRequests().get(0);
	}

	public boolean isUseJsonTemplateToBuildRequest() {
		return useJsonTemplateToBuildRequest;
	}

	public void setUseJsonTemplateToBuildRequest(boolean useJsonTemplateToBuildRequest) {
		this.useJsonTemplateToBuildRequest = useJsonTemplateToBuildRequest;
	}

}
