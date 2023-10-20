/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.ev.temperature;/*
 * created by jbischoff, 15.08.2018
 */

import com.google.inject.Singleton;
import org.matsim.core.controler.AbstractModule;

public class TemperatureChangeModule extends AbstractModule {
    @Override
    public void install() {
	    addMobsimListenerBinding().to(TemperatureManager.class).in( Singleton.class );
	    bind(TemperatureServiceImpl.class).in( Singleton.class );
	    bind(TemperatureService.class).to(TemperatureServiceImpl.class);
	    addEventHandlerBinding().to(TemperatureServiceImpl.class);

	    // yyyy Shouldn't we think about just having a like

//	    bind(TemperatureService.class).to(TemperatureServiceImpl.class)(.in( Singleton.class ));

	    // and then make sure that the implementation registers itself?  kai, oct'23

	}
}
