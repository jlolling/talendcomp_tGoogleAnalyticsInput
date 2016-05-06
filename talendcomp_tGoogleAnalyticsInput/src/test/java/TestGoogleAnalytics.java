import java.util.List;

import de.jlo.talendcomp.google.analytics.DimensionValue;
import de.jlo.talendcomp.google.analytics.MetricValue;
import de.jlo.talendcomp.google.analytics.v4.GoogleAnalyticsInput;

public class TestGoogleAnalytics {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		testGAData();
	}

	public static void testGAData() {

		GoogleAnalyticsInput gi = new GoogleAnalyticsInput();
		gi.setApplicationName("GATalendComp");
		gi.setDebug(true);
		gi.setAccountEmail("503880615382@developer.gserviceaccount.com");
		gi.setKeyFile("/Volumes/Data/Talend/testdata/ga/config/2bc309bb904201fcc6a443ff50a3d8aca9c0a12c-privatekey.p12");
		//gi.setAccountEmail("jan.lolling@gmail.com");
		// gi.setProfileId("33360211"); // 01_main_profile
		// gi.setAccountEmail("422451649636@developer.gserviceaccount.com");
		// gi.setKeyFile("/home/jlolling/Documents/cimt/projects/mobile/GA_Service_Account/af21f07c84b14af09c18837c5a385f8252cc9439-privatekey.p12");
		gi.setFetchSize(20000);
		
		gi.setTimeoutInSeconds(240);
		gi.deliverTotalsDataset(true);
		try {
			System.out.println("initialize client....");
			gi.initializeAnalyticsClient();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		// System.out.println("############################# " + i +
		// " ########################");
		gi.setProfileId("59815695");
		gi.setStartDate("2016-01-01");
		gi.setEndDate("2016-04-01");

		gi.setDimensions("ga:date,ga:source,ga:keyword,ga:segment");
		gi.setMetrics("ga:sessions,ga:pageviews");
		gi.setSegment("sessions::condition::ga:pagePath=~/sqlrunner/");
		try {
			fetchPlainData(gi);
//			fetchNormalizedData(gi);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private static void fetchNormalizedData(GoogleAnalyticsInput gi) throws Exception {
		System.out.println("Start fetching first data...");
		gi.executeQuery();
		System.out.println("total results:" + gi.getTotalAffectedRows());
		System.out.println("contains sampled data:" + gi.containsSampledData());
		System.out.println("results:");
		boolean firstLoop = true;
		List<String> columnNames = gi.getColumnNames();
		for (String name : columnNames) {
			if (firstLoop) {
				firstLoop = false;
			} else {
				System.out.print("\t|\t");
			}
			System.out.print(name);
		}
		System.out.println();
		System.out.println("---------------------------------------");
		List<String> columnTypes = gi.getColumnTypes();
		firstLoop = true;
		for (String type : columnTypes) {
			if (firstLoop) {
				firstLoop = false;
			} else {
				System.out.print("\t|\t");
			}
			System.out.print(type);
		}
		System.out.println();
		System.out.println("---------------------------------------");
		int index = 0;
		int indexDimensionValue = 0;
		int indexMetricValue = 0; 
		while (true) {
			try {
				// in hasNext we execute query if needed
				if (gi.nextNormalizedRecord() == false) {
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
			index++;
			DimensionValue dv = gi.getCurrentDimensionValue();
			if (dv != null) {
				indexDimensionValue++;
				System.out.println("DM: rowNum=" + dv.rowNum + " dimension=" + dv.name + " value=" + dv.value);
			}
			MetricValue mv = gi.getCurrentMetricValue();
			if (mv != null) {
				indexMetricValue++;
				System.out.println("MV: rowNum=" + mv.rowNum + " metric=" + mv.name + " value=" + mv.value);
			}
		}
	}

	private static void fetchPlainData(GoogleAnalyticsInput gi) throws Exception {
		System.out.println("Start fetching first data...");
		gi.executeQuery();
		System.out.println("total results:" + gi.getTotalAffectedRows());
		System.out.println("contains sampled data:" + gi.containsSampledData());
		System.out.println("results:");
		boolean firstLoop = true;
		List<String> columnNames = gi.getColumnNames();
		for (String name : columnNames) {
			if (firstLoop) {
				firstLoop = false;
			} else {
				System.out.print("\t|\t");
			}
			System.out.print(name);
		}
		System.out.println();
		System.out.println("---------------------------------------");
		List<String> columnTypes = gi.getColumnTypes();
		firstLoop = true;
		for (String type : columnTypes) {
			if (firstLoop) {
				firstLoop = false;
			} else {
				System.out.print("\t|\t");
			}
			System.out.print(type);
		}
		System.out.println();
		System.out.println("---------------------------------------");
		int index = 0;
		while (true) {
			try {
				// in hasNext we execute query if needed
				if (gi.hasNextPlainRecord() == false) {
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
			index = gi.getCurrentIndexOverAll();
			List<String> list = gi.getNextPlainRecord();
			System.out.print(index);
			System.out.print("#\t");
			firstLoop = true;
			for (String s : list) {
				if (firstLoop) {
					firstLoop = false;
				} else {
					System.out.print("\t|\t");
				}
				System.out.print(s);
			}
			System.out.println();
		}
		System.out.println("getOverallCount:" + gi.getOverAllCountRows()
				+ " index:" + index);
		System.out
				.println("#####################################################");
	}
	
}
