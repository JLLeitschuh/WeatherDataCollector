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

package org.n52.sensorweb.wdc;

import java.util.Properties;

public interface DataCollector {
    
    /**
     * Collects the weather data
     */
	// TODO this should return a Dataset object
    void collectWeatherData();

    long getParseIntervalMillis();
    
    /**
     * Sets the Configuration properties for this {@link DataCollector} for 
     * providing the required configuration settings contained in the global
     * config.properties file of this application.
     * 
     * @param configuration
     */
    void setProperties(Properties configuration);
    
    /**
     * Initializes the {@link DataCollector}. Is called after 
     * {@link #setProperties(Properties)} in {@link WeatherDataCollector#init()}.
     */
    void init();

}
