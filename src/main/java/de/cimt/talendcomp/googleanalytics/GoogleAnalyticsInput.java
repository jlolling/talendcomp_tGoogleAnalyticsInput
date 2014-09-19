package de.cimt.talendcomp.googleanalytics;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.Analytics.Data.Ga.Get;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.GaData;
import com.google.api.services.analytics.model.GaData.ColumnHeaders;

public class GoogleAnalyticsInput {

	private static final Map<String, GoogleAnalyticsInput> clientCache = new HashMap<String, GoogleAnalyticsInput>();
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private final JsonFactory JSON_FACTORY = new JacksonFactory();
	private File keyFile; // *.p12 key file is needed
	private String clientSecretFile = null;
	private String accountEmail;
	private String applicationName = null;
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	private int countDimensions = 0;
	private String startDate = null;
	private String endDate = null;
	private String metrics = null;
	private String dimensions = null;
	private String sorts = null;
	private String filters = null;
	private String segmentId = null;
	private String profileId;
	private int fetchSize = 0;
	private int timeoutInSeconds = 120;
	private GaData gaData;
	private int lastFetchedRowCount = 0;
	private int overallRowCount = 0;
	private int currentIndex = 0;
	private int startIndex = 1;
	private List<List<String>> lastResultSet;
	private List<String> requestedColumnNames = new ArrayList<String>();
	private Analytics analyticsClient;
	private Get getRequest;
	private boolean addTotalsDataSet = false;
	private boolean totalsDelivered = false;
	private long timeMillisOffsetToPast = 10000;
	private String proxyHost;
	private String proxyPort;
	private String proxyUser;
	private String proxyPassword;
	public static final String SAMPLING_LEVEL_DEFAULT = "DEFAULT";
	public static final String SAMPLING_LEVEL_FASTER = "FASTER";
	public static final String SAMPLING_LEVEL_HIGHER_PRECISION = "HIGHER_PRECISION";
	private String samplingLevel = SAMPLING_LEVEL_DEFAULT;
	private boolean useServiceAccount = true;
	private String credentialDataStoreDir = null;

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
		this.profileId = profileId;
	}

	public void setProfileId(int profileId) {
		this.profileId = String.valueOf(profileId);
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public void setStartDate(String yyyyMMdd) {
		this.startDate = yyyyMMdd;
	}

	public void setStartDate(Date startDate) {
		this.startDate = sdf.format(startDate);
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * 
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
		this.metrics = metrics;
	}

	public void setDimensions(String dimensions) {
		this.dimensions = dimensions;
	}

	public void setSorts(String sorts) {
		this.sorts = sorts;
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
		this.filters = filters;
	}

	public void setSegmentId(String segmentId) {
		this.segmentId = segmentId;
	}

	public void setKeyFile(String file) {
		keyFile = new File(file);
	}

	public void setAccountEmail(String email) {
		accountEmail = email;
	}

	public void setTimeoutInSeconds(int timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}
	
	/**
	 * Authorizes the installed application to access user's protected YouTube
	 * data.
	 * 
	 * @param scopes
	 *            list of scopes needed to access general and analytic YouTube
	 *            info.
	 */
	private Credential authorizeWithClientSecret() throws Exception {
		if (clientSecretFile == null) {
			throw new IllegalStateException("client secret file is not set");
		}
		File secretFile = new File(clientSecretFile);
		if (secretFile.exists() == false) {
			throw new Exception("Client secret file:" + secretFile.getAbsolutePath() + " does not exists or is not readable.");
		}
		Reader reader = new FileReader(secretFile);
		// Load client secrets.
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
		try {
			reader.close();
		} catch (Throwable e) {}
		// Checks that the defaults have been replaced (Default =
		// "Enter X here").
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret()
						.startsWith("Enter ")) {
			throw new Exception("The client secret file does not contains the credentials!");
		}
		credentialDataStoreDir= secretFile.getParent() + "/" + clientSecrets.getDetails().getClientId() + "/";
		File credentialDataStoreDirFile = new File(credentialDataStoreDir);             
		if (credentialDataStoreDirFile.exists() == false && credentialDataStoreDirFile.mkdirs() == false) {
			throw new Exception("Credentedial data dir does not exists or cannot created:" + credentialDataStoreDir);
		}
		FileDataStoreFactory fdsf = new FileDataStoreFactory(credentialDataStoreDirFile);
		// Set up authorization code flow.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
					HTTP_TRANSPORT, 
					JSON_FACTORY, 
					clientSecrets, 
					Arrays.asList(AnalyticsScopes.ANALYTICS_READONLY))
				.setDataStoreFactory(fdsf)
				.setClock(new Clock() {
					@Override
					public long currentTimeMillis() {
						// we must be sure, that we are always in the past from Googles point of view
						// otherwise we get an "invalid_grant" error
						return System.currentTimeMillis() - timeMillisOffsetToPast;
					}
				})
				.build();

		// Authorize.
		return new AuthorizationCodeInstalledApp(
				flow,
				new LocalServerReceiver()).authorize(accountEmail);
	}
	
	private Credential authorizeWithServiceAccount() throws Exception {
		if (keyFile == null) {
			throw new Exception("KeyFile not set!");
		}
		if (keyFile.canRead() == false) {
			throw new IOException("keyFile:" + keyFile.getAbsolutePath()
					+ " is not readable");
		}
		if (accountEmail == null || accountEmail.isEmpty()) {
			throw new Exception("account email cannot be null or empty");
		}
		// Authorization.
		return new GoogleCredential.Builder()
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setServiceAccountId(accountEmail)
				.setServiceAccountScopes(Arrays.asList(AnalyticsScopes.ANALYTICS_READONLY))
				.setServiceAccountPrivateKeyFromP12File(keyFile)
				.setClock(new Clock() {
					@Override
					public long currentTimeMillis() {
						// we must be sure, that we are always in the past from Googles point of view
						// otherwise we get an "invalid_grant" error
						return System.currentTimeMillis() - timeMillisOffsetToPast;
					}
				})
				.build();
	}

	public void initializeAnalyticsClient() throws Exception {
		// Authorization.
		final Credential credential;
		if (useServiceAccount) {
			credential = authorizeWithServiceAccount();
		} else {
			credential = authorizeWithClientSecret();
		}
		// Set up and return Google Analytics API client.
		analyticsClient = new Analytics.Builder(
				HTTP_TRANSPORT, 
				JSON_FACTORY, 
				new HttpRequestInitializer() {
					@Override
					public void initialize(final HttpRequest httpRequest) throws IOException {
						credential.initialize(httpRequest);
						httpRequest.setConnectTimeout(timeoutInSeconds * 1000);
						httpRequest.setReadTimeout(timeoutInSeconds * 1000);
					}
				})
				.setApplicationName(applicationName)
				.build();
	}

	private void executeDataQuery() throws Exception {
		gaData = null;
		if (profileId == null || profileId.length() < 5) {
			throw new Exception("profileId is missing or not long enough");
		}
		if (metrics == null || metrics.startsWith("ga:") == false) {
			throw new Exception("Missing or invalid metrics:" + metrics);
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
		if (dimensions != null && dimensions.startsWith("ga:")) {
			getRequest.setDimensions(dimensions.trim());
		}
		requestedColumnNames = new ArrayList<String>(); // reset
		addRequestedColumns(dimensions); // must added at first!
		countDimensions = requestedColumnNames.size();
		addRequestedColumns(metrics);
		if (filters != null && filters.trim().isEmpty() == false) {
			getRequest.setFilters(filters.trim());
		}
		if (sorts != null && sorts.trim().isEmpty() == false) {
			getRequest.setSort(sorts.trim());
		}
		if (fetchSize > 0) {
			getRequest.setMaxResults(fetchSize);
		}
		if (segmentId != null && segmentId.trim().isEmpty() == false) {
			getRequest.setSegment(segmentId.trim());
		}
		if (samplingLevel != null) {
			getRequest.setSamplingLevel(samplingLevel);
		}
		gaData = getRequest.execute();
		lastResultSet = gaData.getRows();
		if (lastResultSet == null) {
			// fake an empty result set to avoid breaking further processing
			lastResultSet = new ArrayList<List<String>>();
		}
		lastFetchedRowCount = lastResultSet.size();
		currentIndex = 0;
		overallRowCount = 0;
		totalsDelivered = false;
		startIndex = 1;
	}

	private void addRequestedColumns(String columnStr) {
		if (columnStr != null) {
			StringTokenizer st = new StringTokenizer(columnStr, ",");
			while (st.hasMoreElements()) {
				requestedColumnNames.add(st.nextToken());
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

	/**
	 * checks if more result set available
	 * @return true if more data sets available
	 * @throws Exception
	 */
	public boolean hasNextDataset() throws Exception {
		if (gaData == null) {
			throw new IllegalStateException("No query executed before");
		}
		if (getRequest == null) {
			throw new IllegalStateException("No request object available");
		}
		if (addTotalsDataSet && totalsDelivered == false) {
			return true;
		}
		// check if we are at the end of previously fetched data
		// we need fetch data if
		// current index reached the max of last fetched data
		// and count of last fetched data == maxResult what indicated that there
		// is more than we have currently fetched
		if (currentIndex == lastFetchedRowCount
				&& (fetchSize == 0 || lastFetchedRowCount == fetchSize)) {
			startIndex = startIndex + lastFetchedRowCount;
			getRequest.setStartIndex(startIndex);
			gaData = getRequest.execute();
			lastResultSet = gaData.getRows();
			if (lastResultSet == null) {
				lastResultSet = new ArrayList<List<String>>();
			}
			lastFetchedRowCount = lastResultSet.size();
			currentIndex = 0;
		}
		if (lastFetchedRowCount > 0 && currentIndex < lastFetchedRowCount) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * if true, add the totals data set at the end of the 
	 * @param addTotals
	 */
	public void deliverTotalsDataset(boolean addTotals) {
		this.addTotalsDataSet = addTotals;
	}

	public List<String> getTotalsDataset() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed before");
		}
		Map<String, String> totalsMap = gaData.getTotalsForAllResults();
		List<String> totalResult = new ArrayList<String>();
		// find correct field index
		List<String> columnsFormResult = getColumnNames();
		for (int i = 0; i < columnsFormResult.size(); i++) {
			if (i < countDimensions) {
				totalResult.add("total");
			} else {
				totalResult.add(totalsMap.get(columnsFormResult.get(i)));
			}
		}
		return totalResult;
	}

	public List<String> getNextDataset() {
		if (gaData == null) {
			throw new IllegalStateException("No query executed before");
		}
		if (addTotalsDataSet && totalsDelivered == false) {
			totalsDelivered = true;
			overallRowCount++;
			return getTotalsDataset();
		} else {
			overallRowCount++;
			return lastResultSet.get(currentIndex++);
		}
	}
	
	public int getCurrentIndexOverAll() {
		if (addTotalsDataSet && totalsDelivered == false) {
			return 0;
		} else {
			return startIndex + currentIndex;
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
		return overallRowCount;
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

	public void setTimeOffsetMillisToPast(long timeMillisOffsetToPast) {
		this.timeMillisOffsetToPast = timeMillisOffsetToPast;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		if (proxyHost != null && proxyHost.trim().isEmpty() == false) {
			this.proxyHost = proxyHost.trim();
		} else {
			this.proxyHost = null;
		}
	}

	public String getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(String proxyPort) {
		if (proxyPort != null && proxyPort.trim().isEmpty() == false) {
			this.proxyPort = proxyPort.trim();
		} else {
			this.proxyPort = null;
		}
	}

	public String getProxyUser() {
		return proxyUser;
	}

	public void setProxyUser(String proxyUser) {
		if (proxyUser != null && proxyUser.trim().isEmpty() == false) {
			this.proxyUser = proxyUser.trim();
		} else {
			this.proxyUser = null;
		}
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		if (proxyPassword != null && proxyPassword.trim().isEmpty() == false) {
			this.proxyPassword = proxyPassword.trim();
		} else {
			this.proxyPassword = null;
		}
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

	public void setClientSecretFile(String clientSecretFile) {
		if (clientSecretFile != null && clientSecretFile.trim().isEmpty() == false) {
			this.clientSecretFile = clientSecretFile;
			useServiceAccount = false;
		}
	}

	public String getCredentialDataStoreDir() {
		return credentialDataStoreDir;
	}

}