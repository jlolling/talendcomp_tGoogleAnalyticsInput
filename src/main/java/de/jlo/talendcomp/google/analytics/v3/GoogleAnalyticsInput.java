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
package de.jlo.talendcomp.google.analytics.v3;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.Analytics.Data.Ga.Get;
import com.google.api.services.analytics.model.GaData;
import com.google.api.services.analytics.model.GaData.ColumnHeaders;

import de.jlo.talendcomp.google.analytics.DimensionValue;
import de.jlo.talendcomp.google.analytics.GoogleAnalyticsBase;
import de.jlo.talendcomp.google.analytics.MetricValue;
import de.jlo.talendcomp.google.analytics.Util;

public class GoogleAnalyticsInput extends GoogleAnalyticsBase {

	private static final Map<String, GoogleAnalyticsInput> clientCache = new HashMap<String, GoogleAnalyticsInput>();
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private final JsonFactory JSON_FACTORY = new JacksonFactory();
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	private int countDimensions = 0;
	private String startDate = null;
	private String endDate = null;
	private String metrics = null;
	private String dimensions = null;
	private String sorts = null;
	private String filters = null;
	private String segment = null;
	private String profileId;
	private int fetchSize = 0;
	private GaData gaData;
	private int lastFetchedRowCount = 0;
	private int overallPlainRowCount = 0;
	private int currentPlainRowIndex = 0;
	private int startIndex = 1;
	private List<List<String>> lastResultSet;
	private List<DimensionValue> currentResultRowDimensionValues;
	private List<MetricValue> currentResultRowMetricValues;
	private Date currentDate;
	private static final String DATE_DIME = "ga:date";
	private boolean excludeDate = false;
	private List<String> requestedColumnNames = new ArrayList<String>();
	private List<String> requestedDimensionNames = new ArrayList<String>();
	private List<String> requestedMetricNames = new ArrayList<String>();
	private Analytics analyticsClient;
	private Get getRequest;
	private boolean addTotalsRecord = false;
	private boolean totalsDelivered = false;
	public static final String SAMPLING_LEVEL_DEFAULT = "DEFAULT";
	public static final String SAMPLING_LEVEL_FASTER = "FASTER";
	public static final String SAMPLING_LEVEL_HIGHER_PRECISION = "HIGHER_PRECISION";
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
		analyticsClient = new Analytics.Builder(
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
		gaData = null;
		if (profileId == null || profileId.length() < 5) {
			throw new Exception("profileId is missing or not long enough");
		}
		if (metrics == null) {
			throw new Exception("Missing metrics");
		}
		if (startDate == null || startDate.trim().isEmpty()) {
			throw new Exception("Missing start date!");
		}
		if (endDate == null || endDate.trim().isEmpty()) {
			throw new Exception("Missing end date!");
		}
		getRequest = analyticsClient
				.data()
				.ga()
				.get("ga:" + profileId, startDate, endDate, metrics);
		if (dimensions != null) {
			getRequest.setDimensions(dimensions.trim());
		}
		requestedColumnNames = new ArrayList<String>(); // reset
		requestedDimensionNames = new ArrayList<String>();
		requestedMetricNames = new ArrayList<String>();
		addRequestedDimensionColumns(dimensions); // must added at first!
		countDimensions = requestedDimensionNames.size();
		addRequestedMetricColumns(metrics);
		if (filters != null && filters.trim().isEmpty() == false) {
			getRequest.setFilters(filters.trim());
		}
		if (sorts != null && sorts.trim().isEmpty() == false) {
			getRequest.setSort(sorts.trim());
		}
		if (fetchSize > 0) {
			getRequest.setMaxResults(fetchSize);
		}
		if (segment != null && segment.trim().isEmpty() == false) {
			getRequest.setSegment(segment.trim());
		}
		if (samplingLevel != null) {
			getRequest.setSamplingLevel(samplingLevel);
		}
		doExecute();
		overallPlainRowCount = 0;
		totalsDelivered = false;
		startIndex = 1;
		maxCountNormalizedValues = 0;
		currentNormalizedValueIndex = 0;
	}
	
	private void addRequestedDimensionColumns(String columnStr) {
		if (columnStr != null) {
			StringTokenizer st = new StringTokenizer(columnStr, ",");
			while (st.hasMoreElements()) {
				String name = st.nextToken().trim();
				requestedColumnNames.add(name);
				requestedDimensionNames.add(name);
			}
		}
	}

	private void addRequestedMetricColumns(String columnStr) {
		if (columnStr != null) {
			StringTokenizer st = new StringTokenizer(columnStr, ",");
			while (st.hasMoreElements()) {
				String name = st.nextToken().trim();
				requestedColumnNames.add(name);
				requestedMetricNames.add(name);
			}
		}
	}

	private void checkColumns() throws Exception {
		List<String> columnsFromData = getColumnNames();
		if (columnsFromData.size() != requestedColumnNames.size()) {
			throw new Exception("Requested column names="
					+ requestedColumnNames.size() + " columnsFromData="
					+ columnsFromData.size());
		} else {
			for (int i = 0; i < columnsFromData.size(); i++) {
				String colNameFromData = columnsFromData.get(i);
				String colNameFromRequest = requestedColumnNames.get(i);
				if (colNameFromData.equalsIgnoreCase(colNameFromRequest) == false) {
					throw new Exception("At position:" + i
							+ " column missmatch: colNameFromRequest="
							+ colNameFromRequest + " colNameFromData="
							+ colNameFromData);
				}
			}
		}
	}
	
	public void executeQuery() throws Exception {
		executeDataQuery();
		checkColumns();
	}
	
	private int maxRetriesInCaseOfErrors = 5;
	private int currentAttempt = 0;
	private String errorMessage = null;
	
	private void doExecute() throws Exception {
		lastFetchedRowCount = 0;
		int waitTime = 1000;
		for (currentAttempt = 0; currentAttempt < maxRetriesInCaseOfErrors; currentAttempt++) {
			errorCode = 0;
			try {
				gaData = getRequest.execute();
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
		if (gaData != null) {
			lastResultSet = gaData.getRows();
		} else {
			lastResultSet = null;
		}
		if (lastResultSet == null) {
			// fake an empty result set to avoid breaking further processing
			lastResultSet = new ArrayList<List<String>>();
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
		if (gaData == null) {
			throw new IllegalStateException("No query executed before");
		}
		if (getRequest == null) {
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
				&& (fetchSize == 0 || lastFetchedRowCount == fetchSize)) {
			startIndex = startIndex + lastFetchedRowCount;
			getRequest.setStartIndex(startIndex);
			doExecute();
		}
		if (lastFetchedRowCount > 0 && currentPlainRowIndex < lastFetchedRowCount) {
			return true;
		} else {
			return false;
		}
	}
	
	public List<String> getNextPlainRecord() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed before");
		}
		overallPlainRowCount++;
		if (addTotalsRecord && totalsDelivered == false) {
			totalsDelivered = true;
			return getTotalsDataset();
		} else {
			return lastResultSet.get(currentPlainRowIndex++);
		}
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
		for (; index < requestedDimensionNames.size(); index++) {
			DimensionValue dm = new DimensionValue();
			dm.name = requestedDimensionNames.get(index);
			dm.value = oneRow.get(index);
			dm.rowNum = overallPlainRowCount;
        	if (excludeDate && DATE_DIME.equalsIgnoreCase(dm.name.trim().toLowerCase())) {
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
		for (; index < requestedMetricNames.size(); index++) {
			MetricValue mv = new MetricValue();
			mv.name = requestedMetricNames.get(index);
			mv.rowNum = overallPlainRowCount;
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
		if (gaData == null) {
			throw new IllegalStateException("No query executed before");
		}
		Map<String, String> totalsMap = gaData.getTotalsForAllResults();
		List<String> totalResult = new ArrayList<String>();
		// find correct field index
		List<String> columnsFromResult = getColumnNames();
		for (int i = 0; i < columnsFromResult.size(); i++) {
			if (i < countDimensions) {
				totalResult.add("total");
			} else {
				totalResult.add(totalsMap.get(columnsFromResult.get(i)));
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

	public List<String> getRequestedColumns() {
		return requestedColumnNames;
	}

	public List<String> getColumnNames() {
		List<ColumnHeaders> listHeaders = gaData.getColumnHeaders();
		List<String> names = new ArrayList<String>();
		for (ColumnHeaders ch : listHeaders) {
			names.add(ch.getName());
		}
		return names;
	}

	public List<String> getColumnTypes() {
		List<ColumnHeaders> listHeaders = gaData.getColumnHeaders();
		List<String> types = new ArrayList<String>();
		for (ColumnHeaders ch : listHeaders) {
			types.add(ch.getDataType());
		}
		return types;
	}

	public int getOverAllCountRows() {
		return overallPlainRowCount;
	}

	public boolean containsSampledData() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed");
		}
		return gaData.getContainsSampledData();
	}

	public Long getSampleSize() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed");
		}
		return gaData.getSampleSize();
	}
	
	public Long getSampleSpace() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed");
		}
		return gaData.getSampleSpace();
	}

	public Integer getTotalAffectedRows() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed");
		}
		return gaData.getTotalResults();
	}

	public String getSamplingLevel() {
		return samplingLevel;
	}

	public void setSamplingLevel(String samplingLevel) {
		if (SAMPLING_LEVEL_DEFAULT.equals(samplingLevel) || SAMPLING_LEVEL_FASTER.equals(samplingLevel) || SAMPLING_LEVEL_HIGHER_PRECISION.equals(samplingLevel)) {
			this.samplingLevel = samplingLevel;
		} else if (samplingLevel == null || samplingLevel.isEmpty()) {
			this.samplingLevel = null;
		} else {
			throw new IllegalArgumentException("Only these sampling levels:" + SAMPLING_LEVEL_DEFAULT + "," + SAMPLING_LEVEL_FASTER + "," + SAMPLING_LEVEL_HIGHER_PRECISION + " are allowed!");
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
	
}
