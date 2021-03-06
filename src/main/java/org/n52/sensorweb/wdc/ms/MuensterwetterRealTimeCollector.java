/**
 * ﻿Copyright (C) 2013
 * by 52 North Initiative for Geospatial Open Source Software GmbH
 *
 * Contact: Andreas Wytzisk
 * 52 North Initiative for Geospatial Open Source Software GmbH
 * Martin-Luther-King-Weg 24
 * 48155 Muenster, Germany
 * info@52north.org
 *
 * This program is free software; you can redistribute and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 *
 * This program is distributed WITHOUT ANY WARRANTY; even without the implied
 * WARRANTY OF MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program (see gnu-gpl v2.txt). If not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA or
 * visit the Free Software Foundation web page, http://www.fsf.org.
 */

package org.n52.sensorweb.wdc.ms;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.n52.sensorweb.wdc.DataCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO move all file CSV file handling code and constants to DataCollectionTask class and return only a new Dataset

public class MuensterwetterRealTimeCollector implements DataCollector {
	
	private static long run = 1;

	private static final long MILLIS_PER_MINUTE = 60 * 1000;

	private static final String DATE_FORMAT_data_file_extension = "DATE_FORMAT_data_file_extension";

	private static final String LAST_TIME_FILE = "lastTime.52n";

	private static final String DATA_FIELD_TIME = "DATA_FILE_time";

	private static final String DATA_FIELD_TIME_ZONE = "DATA_FILE_timeZone";
	
	private static final String DATA_FILE_TIME_ZONE_PARSE_PATTERN = "DATA_FILE_TIME_ZONE_PARSE_PATTERN";

    private static final String DATA_INTERVAL_MIN = "DATA_INTERVAL_MIN";

    protected static final String DATA_URL = "DATA_URL";

    private static final String DATA_FIELD_AIR_TEMP = "DATA_FILE_airTemperature";

    private static final String DATA_FIELD_REL_HUM = "DATA_FILE_relativeHumidity";

    private static final String DATA_FIELD_WIND_SPEED = "DATA_FILE_windspeed";

    private static final String DATA_FIELD_WIND_SPEED_KMH = "DATA_FILE_windspeedKmh";

    private static final String DATA_FIELD_WIND_SPEED_BFT = "DATA_FILE_windspeedBft";

    private static final String DATA_FIELD_WIND_SPEED_MAX_TEN_MIN = "DATA_FILE_windMax10min";

    private static final String DATA_FIELD_WIND_DIR_DEG = "DATA_FILE_windDirectionDeg";

    private static final String DATA_FIELD_WIND_DIR = "DATA_FILE_windDirection";

    private static final String DATA_FIELD_AIR_PRESSURE = "DATA_FILE_airPressure";

    private static final String DATA_FIELD_VISIBILITY = "DATA_FILE_visibility";

    private static final String DATA_FIELD_WEATHER_CODE = "DATA_FILE_weatherCode";

    private static final String DATA_FIELD_WEATHER_CODE_TEXT_DE = "DATA_FILE_weatherCodeTextDe";

    private static final String DATA_FIELD_GLOBAL_RADIATION = "DATA_FILE_globalRadiation";

    private static final String DATE_FORMAT_TIME_FILE = "DATE_FORMAT_time_file";

    private static final String OUTPUT_FILENAME = "OUTPUT_filename";

    private static final String DATA_LAST_TIME= "DATA_last_time";

	private static final String OUTPUT_FOLDER = "OUTPUT_folder";

	private static final String LAST_TIME_FILE_FOLDER = "LAST_TIME_FILE_folder";

    private static Logger LOG = LoggerFactory.getLogger(MuensterwetterRealTimeCollector.class);

    protected URL dataUrl;

    private long intervalMillis;

	private Properties props;

	private DateTimeFormatter dateTimeFormatter;

    @Override
	public void collectWeatherData() {
        LOG.info("** START parsing ** Run #" + getRun());

        final DateTime lastTimeOfMeasurement = getLastTimeOfMeasurement();
		final MuensterwetterDataset data = getData(lastTimeOfMeasurement);

        if (data.getTime().isAfter(lastTimeOfMeasurement)) {
        	if (appendData(data)) {
        		storeLastTime(data.getTime());
        	} else {
        		LOG.error("New data could not be stored to file. Please fix it!");
        	}
        } else {
        	LOG.info("No newer data available. Last timestamp: '{}'. Time now: '{}'",
        			lastTimeOfMeasurement.toString(),
        			DateTime.now(DateTimeZone.UTC));
        }

        LOG.info("** DONE  parsing ** Run #" + getAndIncrementRun());
    }

	private synchronized String getAndIncrementRun() {
		String tmp = Long.toString(run);
		run++;
		return tmp;
	}

	private synchronized String getRun() {
		return Long.toString(run);
	}

	private void storeLastTime(final DateTime time)	{
		try (
				FileWriter fw = new FileWriter(getLastTimeFile());
				BufferedWriter bw = new BufferedWriter(fw);
				){
			bw.write(time.toString());
		} catch (final IOException e) {
			LOG.error("Could not save timestamp '{}' of last data set to file '{}'. Switch log level to debug to see the exception.",
					time.toString(),
					getLastTimeFile());
			LOG.debug("Exception thrown!",e);
		}
	}

	private String getLastTimeFile() {
		return props.getProperty(LAST_TIME_FILE_FOLDER, "." + File.separatorChar) + LAST_TIME_FILE;
	}

	private DateTime getLastTimeOfMeasurement() {
		String lastTimestamp = "";
		// get last record from data file
		try (
				FileReader fr = new FileReader(getLastTimeFile());
				BufferedReader br = new BufferedReader(fr);
				){
			lastTimestamp = br.readLine();
		} catch (final FileNotFoundException e1) {
			LOG.info("File '{}' could not be found. A new file will be created after successful download of weather data.",getLastTimeFile());
		} catch (final IOException e1) {
			LOG.error("Exception thrown: {}", e1.getMessage(), e1);
		}
		if (lastTimestamp == null || lastTimestamp.isEmpty()) {
			 // this might result in a bug when dealing with data from before 1970-01-01
			return new DateTime(0).toDateTime(DateTimeZone.UTC);
		}
		// get date from last line
		DateTime lastTime;
		try {
			lastTime = new DateTime(lastTimestamp).toDateTime(DateTimeZone.UTC);
		} catch (final IllegalArgumentException|UnsupportedOperationException e) {
			LOG.error("LastTimestamp '{}' could not be parsed to a jodatime...DateTime. Current value '{}'. Error message: '{}' (enable debug level logging for more details). Default value 1970-01-01 will be used.",
					DATA_LAST_TIME,
					lastTimestamp,
					e.getMessage());
			LOG.debug("Exception", e);
			 // this might result in a bug when dealing with data from before 1970-01-01
			lastTime = new DateTime(0).toDateTime(DateTimeZone.UTC);
		}
		return lastTime;
	}

	private boolean appendData(final MuensterwetterDataset dataset) {
		// 1 create file name
		// 1.1 create file name
		final String fileName = getFileName(dataset.getTime());
		final File outputFile = new File(fileName);

		// 1.2 check if file exists -> if not => create new file
		if (!outputFile.exists()) {
			try {
				outputFile.createNewFile();
				writeCSVHeader(outputFile);
			} catch (final IOException e) {
				LOG.error("CSV output file '{}' could not be created. Aborting storing of parsed values: {}. Error Message: {} (enable debug level to see exception).",
						outputFile.getAbsolutePath(),
						dataset,
						e.getMessage());
				LOG.debug("Exception thrown!", e);
				return false;
			}
		}

		// 2 append new line
		try (final BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile,true))){
			bw.newLine();
			bw.write(dataset.toCSVString());
			bw.flush();
		}
		catch (final IOException e) {
			LOG.error("Could not append new line '{}' to CSV file '{}' in folder '{}'. Enable log level debug to see more details.",
					dataset.toCSVString(),
					outputFile.getName(),
					outputFile.getAbsolutePath());
			LOG.debug("Exception thrown!",e);
			return false;
		}
		return true;
	}

	private void writeCSVHeader(final File outputFile) {
		try (FileWriter fw = new FileWriter(outputFile);
				BufferedWriter bw = new BufferedWriter(fw);){
			bw.write(new MuensterwetterDataset().getCSVHeader());
			bw.flush();
		} catch (final IOException e) {
			// TODO Auto-generated catch block generated on 25.11.2013 around 11:34:15
			LOG.error("Exception thrown: {}", e.getMessage(), e);
		}
	}

	private String getFileName(final DateTime date)	{
		final String fileNameDatePart = DateTimeFormat.forPattern(props.getProperty(DATE_FORMAT_data_file_extension)).print(date);
		final String userDefinedPrefix = props.getProperty(OUTPUT_FILENAME, "prefix_not_defined");
		final String userDefinePath = props.getProperty(OUTPUT_FOLDER, "." + File.separatorChar);
		final String fileName = userDefinePath + userDefinedPrefix + "_" + fileNameDatePart + ".csv";
		return fileName;
	}

	private MuensterwetterDataset getData(final DateTime lastTime) {
        final MuensterwetterDataset data = new MuensterwetterDataset();

        getTimestamp(data, lastTime);
        // skip requesting if data is not new
        if (!data.getTime().isAfter(lastTime)) {
        	return data;
        }

        // 2m above ground
        final String airTempUrl = dataUrl + props.getProperty(DATA_FIELD_AIR_TEMP);
        try {
        	data.setTemperature(getDoubleValueFromUrl(airTempUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String relativeHumidityUrl = dataUrl + props.getProperty(DATA_FIELD_REL_HUM);
        try {
            data.setRelativeHumidity(getDoubleValueFromUrl(relativeHumidityUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        // 72m above NN
        final String airPressureUrl = dataUrl + props.getProperty(DATA_FIELD_AIR_PRESSURE);
        try {
            data.setAirPressure(getDoubleValueFromUrl(airPressureUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        // 7.7m above ground
        final String windSpeedMperSecUrl = dataUrl + props.getProperty(DATA_FIELD_WIND_SPEED);
        try {
            data.setWindSpeedMperSec(getDoubleValueFromUrl(windSpeedMperSecUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String windSpeedKmhUrl = dataUrl + props.getProperty(DATA_FIELD_WIND_SPEED_KMH);
        try {
            data.setWindSpeedKmh(getDoubleValueFromUrl(windSpeedKmhUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String windSpeedBeaufortUrl = dataUrl + props.getProperty(DATA_FIELD_WIND_SPEED_BFT);
        try {
            data.setWindSpeedBft(getDoubleValueFromUrl(windSpeedBeaufortUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String maxWindGustLast10minUrl = dataUrl + props.getProperty(DATA_FIELD_WIND_SPEED_MAX_TEN_MIN);
        try {
            data.setWindMaxGust(getDoubleValueFromUrl(maxWindGustLast10minUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String windDirectionUrl = dataUrl + props.getProperty(DATA_FIELD_WIND_DIR);
        try {
            data.setWindDirection(downloadFile(windDirectionUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String windDirectionDegreeUrl = dataUrl + props.getProperty(DATA_FIELD_WIND_DIR_DEG);
        try {
            data.setWindDirectionDegree(getDoubleValueFromUrl(windDirectionDegreeUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String globalRadiationUrl = dataUrl + props.getProperty(DATA_FIELD_GLOBAL_RADIATION);
        try {
            data.setGlobalRadiation(getDoubleValueFromUrl(globalRadiationUrl));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String visibilityUrl = dataUrl + props.getProperty(DATA_FIELD_VISIBILITY);
        try {
            final double d = Double.parseDouble(downloadFile(visibilityUrl).replaceAll("[^\\d]", ""));
            data.setVisibility(d);
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String weatherCodeUrl = dataUrl + props.getProperty(DATA_FIELD_WEATHER_CODE);
        try {
            final String s = downloadFile(weatherCodeUrl);
            data.setWeatherCode(s);
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        final String weatherCodeDescrUrl = dataUrl + props.getProperty(DATA_FIELD_WEATHER_CODE_TEXT_DE);
        try {
            final String s = downloadFile(weatherCodeDescrUrl);
            data.setWeatherCodeText(s);
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }
        LOG.debug("Created new Muensterwetter Dataset: {}", data.toString());
        return data;
    }

	private double getDoubleValueFromUrl(final String airTempUrl) throws MalformedURLException {
		String s = downloadFile(airTempUrl);
		s = s.replace(",", ".");
		return Double.parseDouble(s);
	}

	private void getTimestamp(final MuensterwetterDataset data, final DateTime lastTime) {
		final String timeUrl = dataUrl + props.getProperty(DATA_FIELD_TIME);
		final String timeZoneUrl = dataUrl + props.getProperty(DATA_FIELD_TIME_ZONE);
        try {
            final String t = downloadFile(timeUrl);

            String tzId = downloadFile(timeZoneUrl);
            if (!tzId.isEmpty()) {
            	tzId = String.format(props.getProperty(DATA_FILE_TIME_ZONE_PARSE_PATTERN),
            			tzId.replaceAll("[\\(\\)UTC]",""));
            	if (tzId.contains("+")) {
            		tzId = tzId.replace('+', '-');
            	} else {
            		tzId = tzId.replace('-', '+');
            	}
            }
            
            DateTimeZone localZone = DateTimeZone.UTC;

            if (DateTimeZone.getAvailableIDs().contains(tzId)) {
            	localZone = DateTimeZone.forID(tzId);
            } else {
            	LOG.info("Timezone id '{}' is not supported by Jodatime. Please check "
            			+ "'http://www.joda.org/joda-time/timezones.html'.",
            			tzId);
            }
            if (t == null || t.isEmpty()) {
            	LOG.error("Timestamp string '{}' could not be parsed. Stop this collection run",t);
            	// work around to skip processing because the timestamp of the dataset is not newer than last time
            	data.setTime(lastTime);
            }
            DateTime date = dateTimeFormatter.withZone(localZone).parseDateTime(t).toDateTime(DateTimeZone.UTC);
            data.setTime(date);

        } catch (final UnsupportedOperationException|IllegalArgumentException|MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }
	}

	protected String downloadFile(final String timeUrl) throws MalformedURLException {
		return HttpUtil.downloadFile(new URL(timeUrl));
	}

    @Override
	public long getParseIntervalMillis() {
        return intervalMillis;
    }

    @Override
	public String toString() {
        return "MuensterwetterRealTimeCollector [interval=" + intervalMillis + ", dataUrl=" + dataUrl + "]";
    }

	@Override
	public void setProperties(final Properties configuration) {
		props = configuration;
	}

	@Override
	public void init() {
		try {
            dataUrl = new URL(props.getProperty(DATA_URL));
        } catch (final MalformedURLException e) {
            LOG.error("Exception thrown: ",e);
        }

        intervalMillis = Long.parseLong(props.getProperty(DATA_INTERVAL_MIN)) * MILLIS_PER_MINUTE;
        dateTimeFormatter = DateTimeFormat.forPattern(props.getProperty(DATE_FORMAT_TIME_FILE));
	}

}
