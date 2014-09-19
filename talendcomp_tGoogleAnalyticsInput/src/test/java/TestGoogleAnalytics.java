import java.util.List;

import de.cimt.talendcomp.googleanalytics.GoogleAnalyticsInput;

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

		gi.setAccountEmail("503880615382@developer.gserviceaccount.com");
		gi.setKeyFile("config/2bc309bb904201fcc6a443ff50a3d8aca9c0a12c-privatekey.p12");
		//gi.setAccountEmail("jan.lolling@gmail.com");
		// gi.setProfileId("33360211"); // 01_main_profile
		// gi.setAccountEmail("422451649636@developer.gserviceaccount.com");
		// gi.setKeyFile("/home/jlolling/Documents/cimt/projects/mobile/GA_Service_Account/af21f07c84b14af09c18837c5a385f8252cc9439-privatekey.p12");
		System.out.println("asldkasdljkasdlkasdlkasd");
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
		gi.setStartDate("2012-12-15");
		gi.setEndDate("2013-01-01");

		gi.setDimensions("ga:date,ga:source,ga:keyword");
		gi.setMetrics("ga:visits,ga:exits");
		gi.setSorts("ga:date");
		try {
			fetchData(gi);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private static void fetchData(GoogleAnalyticsInput gi) throws Exception {
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
				if (gi.hasNextDataset() == false) {
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
			index = gi.getCurrentIndexOverAll();
			List<String> list = gi.getNextDataset();
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
