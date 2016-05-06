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
package de.jlo.talendcomp.google.analytics;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.analytics.AnalyticsScopes;

public class GoogleAnalyticsBase {

	private Logger logger = null;
	private static final Map<String, GoogleAnalyticsBase> clientCache = new HashMap<String, GoogleAnalyticsBase>();
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private final JsonFactory JSON_FACTORY = new JacksonFactory();
	private File keyFile; // *.p12 key file is needed
	private String clientSecretFile = null;
	private String accountEmail;
	private String applicationName = null;
	private int timeoutInSeconds = 120;
	private Date currentDate;
	private long timeMillisOffsetToPast = 10000;
	private boolean useServiceAccount = true;
	private String credentialDataStoreDir = null;
	private int errorCode = 0;
	private boolean success = true;

	public static void putIntoCache(String key, GoogleAnalyticsBase gai) {
		clientCache.put(key, gai);
	}
	
	public static GoogleAnalyticsBase getFromCache(String key) {
		return clientCache.get(key);
	}
	
	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public void setKeyFile(String file) {
		if (file == null || file.trim().isEmpty()) {
			throw new IllegalArgumentException("Key file path cannot be null or empty");
		}
		keyFile = new File(file.trim());
	}

	public void setAccountEmail(String email) {
		if (email == null || email.trim().isEmpty()) {
			throw new IllegalArgumentException("Email cannot be null or empty");
		}
		accountEmail = email.trim();
	}

	public void setTimeoutInSeconds(int timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}
	
	protected Credential authorize() throws Exception {
		final Credential credential;
		if (isUseServiceAccount()) {
			credential = authorizeWithServiceAccount();
		} else {
			credential = authorizeWithClientSecret();
		}
		return credential;
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
			throw new Exception("The client secret file does not contains the credentials. At first you have to pass the web based authorization process!");
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

	public void setClientSecretFile(String clientSecretFile) {
		if (clientSecretFile != null && clientSecretFile.trim().isEmpty() == false) {
			this.clientSecretFile = clientSecretFile;
		}
	}

	public String getCredentialDataStoreDir() {
		return credentialDataStoreDir;
	}

	public boolean isUseServiceAccount() {
		return useServiceAccount;
	}

	public void setUseServiceAccount(boolean useServiceAccount) {
		this.useServiceAccount = useServiceAccount;
	}

	public Date getCurrentDate() throws ParseException {
		return currentDate;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public boolean isSuccess() {
		return success;
	}
	
	public void info(String message) {
		if (logger != null) {
			logger.info(message);
		} else {
			System.out.println("INFO:" + message);
		}
	}
	
	public void debug(String message) {
		if (logger != null) {
			logger.debug(message);
		} else {
			System.out.println("DEBUG:" + message);
		}
	}

	public void warn(String message) {
		if (logger != null) {
			logger.warn(message);
		} else {
			System.err.println("WARN:" + message);
		}
	}

	public void error(String message, Exception e) {
		if (logger != null) {
			if (e != null) {
				logger.error(message, e);
			} else {
				logger.error(message);
			}
		} else {
			System.err.println("ERROR:" + message);
		}
	}

	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	private boolean debug = false;

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
		if (logger != null) {
			logger.setLevel(Level.DEBUG);
		}
	}

	public String getApplicationName() {
		return applicationName;
	}

	public int getTimeoutInSeconds() {
		return timeoutInSeconds;
	}

	public long getTimeMillisOffsetToPast() {
		return timeMillisOffsetToPast;
	}

	public void setTimeMillisOffsetToPast(Long timeMillisOffsetToPast) {
		if (timeMillisOffsetToPast != null) {
			this.timeMillisOffsetToPast = timeMillisOffsetToPast.longValue();
		}
	}

	public void setTimeMillisOffsetToPast(Integer timeMillisOffsetToPast) {
		if (timeMillisOffsetToPast != null) {
			this.timeMillisOffsetToPast = timeMillisOffsetToPast.longValue();
		}
	}

}
