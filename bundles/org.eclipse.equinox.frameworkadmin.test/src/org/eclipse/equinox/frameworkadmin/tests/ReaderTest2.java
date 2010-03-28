/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.frameworkadmin.tests;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;
import org.eclipse.equinox.internal.provisional.frameworkadmin.*;
import org.osgi.framework.BundleException;

public class ReaderTest2 extends AbstractFwkAdminTest {
	private File installFolder = null;
	private File configurationFolder = null;
	private String launcherName = "eclipse";

	public ReaderTest2(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		startSimpleConfiguratorManipulator();

		installFolder = Activator.getContext().getDataFile(ReaderTest2.class.getName());
		configurationFolder = new File(installFolder, "conf");
		writeEclipseIni(new File(installFolder, "eclipse.ini"), new String[] { "-configuration", configurationFolder.getAbsolutePath() });
		Properties properties = new Properties();
		properties.setProperty("foo", "bar");
		writeConfigIni(new File(configurationFolder, "config.ini"), properties);
	}

	public void testConfigContent() throws IllegalStateException, FrameworkAdminRuntimeException, IOException, BundleException, URISyntaxException {
		FrameworkAdmin fwkAdmin = getEquinoxFrameworkAdmin();
		Manipulator manipulator = fwkAdmin.getManipulator();
		LauncherData launcherData = manipulator.getLauncherData();
		launcherData.setLauncher(new File(installFolder, launcherName));
		try {
			manipulator.load();
		} catch (IllegalStateException e) {
			//TODO We ignore the framework JAR location not set exception
		}
		
		assertEquals(new File(installFolder, "conf"), manipulator.getLauncherData().getFwConfigLocation());  
		assertEquals("bar", manipulator.getConfigData().getProperty("foo"));
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

}
