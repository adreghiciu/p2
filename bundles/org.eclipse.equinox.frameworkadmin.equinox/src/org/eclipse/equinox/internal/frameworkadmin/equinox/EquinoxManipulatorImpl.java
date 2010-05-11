/*******************************************************************************
 * Copyright (c) 2007, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.frameworkadmin.equinox;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.frameworkadmin.utils.SimpleBundlesState;
import org.eclipse.equinox.internal.frameworkadmin.utils.Utils;
import org.eclipse.equinox.internal.provisional.configuratormanipulator.ConfiguratorManipulator;
import org.eclipse.equinox.internal.provisional.frameworkadmin.*;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;
import org.osgi.service.log.LogService;
import org.osgi.service.startlevel.StartLevel;
import org.osgi.util.tracker.ServiceTracker;

/**
 *
 */
public class EquinoxManipulatorImpl implements Manipulator {
	private static final long DEFAULT_LASTMODIFIED = 0L;
	private static final boolean LOG_ILLEGALSTATEEXCEPTION = false;
	private static final String COMMA = ","; //$NON-NLS-1$
	private static final String FILE_PROTOCOL = "file:"; //$NON-NLS-1$
	private static final String IGNORED = "ignored"; //$NON-NLS-1$

	/**
	 * If the fwConfigLocation is a file and its name does not equal "config.ini",
	 * throw an IllegaStateException.
	 * If the fwConfigLocation is a file and its name equals "config.ini",
	 * fwConfigLocation will be updated by its parent directory.
	 *
	 * Then, reset fwConfigLocation and fwPersistentDataLocation to be matched.
	 *
	 * @param launcherData
	 */
	static void checkConsistencyOfFwConfigLocAndFwPersistentDataLoc(LauncherData launcherData) {
		File fwConfigLocation = launcherData.getFwConfigLocation();
		File fwPersistentDataLocation = launcherData.getFwPersistentDataLocation();

		if (fwConfigLocation != null) {
			if (fwConfigLocation.isFile()) {
				if (fwConfigLocation.getName().equals(EquinoxConstants.CONFIG_INI))
					fwConfigLocation = fwConfigLocation.getParentFile();
				else
					throw new IllegalStateException(NLS.bind(Messages.exception_unexpectedfwConfigLocation, fwConfigLocation.getAbsolutePath(), EquinoxConstants.CONFIG_INI));
				launcherData.setFwConfigLocation(fwConfigLocation);
			}
			if (fwPersistentDataLocation != null) {
				if (!fwConfigLocation.equals(fwPersistentDataLocation))
					throw new IllegalStateException(NLS.bind(Messages.exception_persistantLocationNotEqualConfigLocation, fwPersistentDataLocation.getAbsolutePath(), fwConfigLocation.getAbsolutePath()));
			} else
				launcherData.setFwPersistentDataLocation(fwConfigLocation, launcherData.isClean());
		} else {
			if (fwPersistentDataLocation != null) {
				launcherData.setFwConfigLocation(fwPersistentDataLocation);
			} else {
				File home = launcherData.getHome();
				if (home == null)
					throw new IllegalStateException(Messages.exception_noLocations);
				fwConfigLocation = new File(home, "configuration"); //$NON-NLS-1$
				launcherData.setFwPersistentDataLocation(fwConfigLocation, launcherData.isClean());
				launcherData.setFwConfigLocation(fwConfigLocation);
			}
		}
	}

	//This returns the location of the <eclipse>.ini file
	static File getLauncherConfigLocation(LauncherData launcherData) {
		File launcherIni = launcherData.getLauncherConfigLocation();
		if (launcherIni != null)
			return launcherIni;

		File launcher = launcherData.getLauncher();
		if (launcher == null)
			return null;
		String launcherName = launcher.getName();
		int dotLocation = launcherName.lastIndexOf('.');
		if (dotLocation != -1)
			launcherName = launcherName.substring(0, dotLocation);
		File result = new File(launcher.getParentFile(), launcherName + EquinoxConstants.INI_EXTENSION);
		return result;
	}

	ConfigData configData = new ConfigData(EquinoxConstants.FW_NAME, EquinoxConstants.FW_VERSION, EquinoxConstants.LAUNCHER_NAME, EquinoxConstants.LAUNCHER_VERSION);
	EquinoxLauncherData launcherData = new EquinoxLauncherData(EquinoxConstants.FW_NAME, EquinoxConstants.FW_VERSION, EquinoxConstants.LAUNCHER_NAME, EquinoxConstants.LAUNCHER_VERSION);
	BundleContext context = null;
	private Properties platformProperties = new Properties();

	ServiceTracker cmTracker;
	int trackingCount = -1;
	private final PlatformAdmin platformAdmin;
	private final StartLevel startLevelService;

	//	private final boolean runtime;

	ConfiguratorManipulator configuratorManipulator;

	EquinoxFwAdminImpl fwAdmin = null;

	EquinoxManipulatorImpl(BundleContext context, EquinoxFwAdminImpl fwAdmin, PlatformAdmin admin, StartLevel slService, boolean runtime) {
		this.context = context;
		this.fwAdmin = fwAdmin;
		this.platformAdmin = admin;
		this.startLevelService = slService;
		if (context != null) {
			cmTracker = new ServiceTracker(context, ConfiguratorManipulator.class.getName(), null);
			cmTracker.open();
		}
		//		this.runtime = runtime;
		if (runtime)
			initializeRuntime();
		// XXX For Equinox, default value of Initial Bundle Start Level is 4.
		// Precisely speaking, it's not correct.
		// Equinox doesn't support setting initial bundle start level as an OSGi terminology.
		// Only bundles installed by config.ini and updateconfigurator will have that start level(4).
		// Others has a start level of 1.
		configData.setInitialBundleStartLevel(4);
	}

	public BundlesState getBundlesState() throws FrameworkAdminRuntimeException {
		if (context == null)
			return new SimpleBundlesState(fwAdmin, this, EquinoxConstants.FW_SYMBOLIC_NAME);

		if (!EquinoxBundlesState.checkFullySupported())
			return new SimpleBundlesState(fwAdmin, this, EquinoxConstants.FW_SYMBOLIC_NAME);

		if (platformProperties.isEmpty())
			return new EquinoxBundlesState(context, fwAdmin, this, platformAdmin, false);
		// XXX checking if fwDependent or fwIndependent platformProperties are updated after the platformProperties was created might be required for better implementation.
		return new EquinoxBundlesState(context, fwAdmin, this, platformAdmin, platformProperties);
	}

	public ConfigData getConfigData() throws FrameworkAdminRuntimeException {
		return configData;
	}

	public BundleInfo[] getExpectedState() throws IllegalArgumentException, FrameworkAdminRuntimeException {
		//Log.log(LogService.LOG_DEBUG, this, "getExpectedState()", "BEGIN");
		SimpleBundlesState.checkAvailability(fwAdmin);

		BundlesState bundleState = this.getBundlesState();
		if (bundleState instanceof SimpleBundlesState)
			return new BundleInfo[0];
		bundleState.resolve(true);

		return bundleState.getExpectedState();
	}

	public LauncherData getLauncherData() throws FrameworkAdminRuntimeException {
		return launcherData;
	}

	/**
	 * Return the configuration location.
	 *
	 * @see Location
	 */
	private File getRunningConfigurationLocation() {
		ServiceTracker tracker = null;
		Filter filter = null;
		try {
			filter = context.createFilter(Location.CONFIGURATION_FILTER);
		} catch (InvalidSyntaxException e) {
			// ignore this. It should never happen as we have tested the above format.
		}
		tracker = new ServiceTracker(context, filter, null);
		tracker.open();
		Location location = (Location) tracker.getService();
		URL url = location.getURL();
		if (!url.getProtocol().equals("file")) //$NON-NLS-1$
			return null;
		return new File(url.getFile());
	}

	private File getRunningLauncherFile() {
		File launcherFile = null;
		String eclipseCommandsSt = context.getProperty(EquinoxConstants.PROP_ECLIPSE_COMMANDS);
		if (eclipseCommandsSt == null)
			return null;

		StringTokenizer tokenizer = new StringTokenizer(eclipseCommandsSt, "\n"); //$NON-NLS-1$
		boolean found = false;
		String launcherSt = null;
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (found) {
				launcherSt = token;
				break;
			}
			if (token.equals("-launcher")) //$NON-NLS-1$
				found = true;
		}
		if (launcherSt != null)
			launcherFile = new File(launcherSt);
		return launcherFile;
	}

	private Properties getRunningPlatformProperties() {
		Properties props = new Properties();
		for (int i = 0; i < EquinoxBundlesState.PROPS.length; i++) {
			String value = context.getProperty(EquinoxBundlesState.PROPS[i]);
			if (value != null)
				props.setProperty(EquinoxBundlesState.PROPS[i], value);
		}
		return props;
	}

	public long getTimeStamp() {
		long ret = this.getTimeStampWithoutFwPersistentData();
		if (this.launcherData.isClean())
			return ret;
		long lastModifiedFwPersistent = EquinoxBundlesState.getTimeStamp(launcherData.getFwPersistentDataLocation());
		return Math.max(ret, lastModifiedFwPersistent);
	}

	private long getTimeStampWithoutFwPersistentData() {
		SimpleBundlesState.checkAvailability(fwAdmin);
		File launcherConfigFile = getLauncherConfigLocation(launcherData);
		long lastModifiedLauncherConfigFile = DEFAULT_LASTMODIFIED;
		long lastModifiedFwConfigFile = DEFAULT_LASTMODIFIED;
		if (launcherConfigFile != null) {
			// use launcher. -- > load from LaucnherConfig file.
			lastModifiedLauncherConfigFile = launcherConfigFile.lastModified();
		}
		checkConsistencyOfFwConfigLocAndFwPersistentDataLoc(launcherData);

		if (launcherData.getFwConfigLocation() != null) {
			File fwConfigFile = new File(launcherData.getFwConfigLocation(), EquinoxConstants.CONFIG_INI);
			lastModifiedFwConfigFile = fwConfigFile.lastModified();
		}
		long ret = Math.max(lastModifiedLauncherConfigFile, lastModifiedFwConfigFile);
		return ret;
	}

	public void initialize() {
		Log.log(LogService.LOG_DEBUG, this, "initialize()", "BEGIN"); //$NON-NLS-1$ //$NON-NLS-2$
		configData.initialize();
		launcherData.initialize();
	}

	private void initializeRuntime() {
		//TODO refine the implementation. using some MAGIC dependent on Eclipse.exe and Equinox implementation,
		// set parameters according to the current running fw.

		// 1. retrieve location data from Location services registered by equinox fw.
		String fwJarLocation = context.getProperty(EquinoxConstants.PROP_OSGI_FW);
		if (!fwJarLocation.startsWith("file:")) //$NON-NLS-1$
			throw new IllegalStateException(NLS.bind(Messages.exception_fileURLExpected, EquinoxConstants.PROP_OSGI_FW, fwJarLocation));
		File fwJar = new File(fwJarLocation.substring("file:".length())); //$NON-NLS-1$
		File fwConfigLocation = getRunningConfigurationLocation();
		File launcherFile = getRunningLauncherFile();
		launcherData.setFwJar(fwJar);
		launcherData.setFwPersistentDataLocation(fwConfigLocation, false);
		launcherData.setLauncher(launcherFile);
		launcherData.setOS(context.getProperty("osgi.os")); //$NON-NLS-1$
		try {
			this.loadWithoutFwPersistentData();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// 2. Create a Manipulator object fully initialized to the current running fw.

		ServiceReference reference = context.getServiceReference(StartLevel.class.getName());
		StartLevel startLevel = (StartLevel) context.getService(reference);
		Bundle[] bundles = context.getBundles();
		BundleInfo[] bInfos = new BundleInfo[bundles.length];
		for (int i = 0; i < bundles.length; i++) {
			//			System.out.println("bundles[" + i + "]=" + bundles[i]);
			try {
				if (bundles[i].getBundleId() == 0) // SystemBundle
					bInfos[i] = new BundleInfo(bundles[i].getSymbolicName(), (String) bundles[i].getHeaders().get(Constants.BUNDLE_VERSION), FileLocator.getBundleFile(bundles[i]).getAbsoluteFile().toURI(), -1, true);
				else {
					bInfos[i] = new BundleInfo(bundles[i].getSymbolicName(), (String) bundles[i].getHeaders().get(Constants.BUNDLE_VERSION), FileLocator.getBundleFile(bundles[i]).getAbsoluteFile().toURI(), startLevel.getBundleStartLevel(bundles[i]), startLevel.isBundlePersistentlyStarted(bundles[i]));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		configData.setBundles(bInfos);
		platformProperties = this.getRunningPlatformProperties();

		// copy system properties to ConfigData
		Properties props = System.getProperties();
		for (Enumeration enumeration = props.keys(); enumeration.hasMoreElements();) {
			String key = (String) enumeration.nextElement();
			String value = props.getProperty(key);
			if (toBeEliminated(key))
				continue;
			configData.setProperty(key, value);
		}

		// update initialBundleStartLevel
		int initialBSL = configData.getInitialBundleStartLevel();
		if (initialBSL != startLevelService.getInitialBundleStartLevel())
			configData.setInitialBundleStartLevel(startLevelService.getInitialBundleStartLevel());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.frameworkadmin.Manipulator#load()
	 */
	public void load() throws IllegalStateException, IOException, FrameworkAdminRuntimeException {
		Log.log(LogService.LOG_DEBUG, this, "load()", "BEGIN"); //$NON-NLS-1$//$NON-NLS-2$
		loadWithoutFwPersistentData();

		BundlesState bundlesState = null;
		if (EquinoxBundlesState.checkFullySupported()) {
			bundlesState = new EquinoxBundlesState(context, fwAdmin, this, platformAdmin, !launcherData.isClean());
			platformProperties = ((EquinoxBundlesState) bundlesState).getPlatformProperties();
		} else {
			bundlesState = new SimpleBundlesState(fwAdmin, this, EquinoxConstants.FW_SYMBOLIC_NAME);
			platformProperties.clear();
		}
		updateAccordingToExpectedState(bundlesState);
		//		if (!useConfigurator)
		//			return;
		setConfiguratorManipulator();
		if (this.configuratorManipulator == null)
			return;
		configuratorManipulator.updateBundles(this);
		return;
	}

	private void loadWithoutFwPersistentData() throws IOException {
		SimpleBundlesState.checkAvailability(fwAdmin);
		File launcherConfigFile = getLauncherConfigLocation(launcherData);
		if (launcherConfigFile != null && !launcherConfigFile.getName().endsWith(IGNORED)) {
			// use launcher. -- > load from LaucnherConfig file.
			// the parameters in memory will be updated.
			EclipseLauncherParser parser = new EclipseLauncherParser();
			parser.read(launcherConfigFile, launcherData);
		}
		checkConsistencyOfFwConfigLocAndFwPersistentDataLoc(launcherData);

		File fwConfigFile = new File(launcherData.getFwConfigLocation(), EquinoxConstants.CONFIG_INI);
		EquinoxFwConfigFileParser parser = new EquinoxFwConfigFileParser(context);
		if (fwConfigFile.exists())
			try {
				parser.readFwConfig(this, fwConfigFile);
			} catch (URISyntaxException e) {
				throw new FrameworkAdminRuntimeException(e, NLS.bind(Messages.exception_errorReadingFile, fwConfigFile.getAbsolutePath()));
			}
	}

	// Save all parameter in memory into proper config files.
	public void save(boolean backup) throws IOException, FrameworkAdminRuntimeException {
		Log.log(LogService.LOG_DEBUG, this, "save()", "BEGIN"); //$NON-NLS-1$//$NON-NLS-2$
		SimpleBundlesState.checkAvailability(fwAdmin);

		try {
			updateAccordingToExpectedState(this.getBundlesState());
		} catch (IllegalStateException e) {
			// ignore.
		}

		boolean stateIsEmpty = configData.getBundles().length == 0;

		File launcherConfigFile = getLauncherConfigLocation(launcherData);
		if (launcherConfigFile != null) {
			if (!stateIsEmpty) {
				// Use launcher. -- > save LauncherConfig file.
				EclipseLauncherParser launcherParser = new EclipseLauncherParser();
				launcherParser.save(launcherData, backup);
			} else {
				// No bundles in configuration, so delete the launcher config file
				launcherConfigFile.delete();
			}
		}

		checkConsistencyOfFwConfigLocAndFwPersistentDataLoc(launcherData);

		ConfiguratorManipulator previousConfigurator = setConfiguratorManipulator();
		if (previousConfigurator != null)
			previousConfigurator.cleanup(this);

		BundleInfo[] newBInfos = null;
		if (configuratorManipulator != null) { // Optimize BundleInfo[]
			try {
				newBInfos = configuratorManipulator.save(this, backup);
			} catch (IllegalStateException e) {
				if (LOG_ILLEGALSTATEEXCEPTION)
					Log.log(LogService.LOG_WARNING, this, "save()", e); //$NON-NLS-1$
				newBInfos = configData.getBundles();
			}
		} else {
			newBInfos = configData.getBundles();
		}

		if (!stateIsEmpty) {
			// Save FwConfigFile
			EquinoxFwConfigFileParser parser = new EquinoxFwConfigFileParser(context);
			parser.saveFwConfig(newBInfos.length != 0 ? newBInfos : getConfigData().getBundles(), this, backup, false);
		} else {
			File configDir = launcherData.getFwConfigLocation();
			File outputFile = new File(configDir, EquinoxConstants.CONFIG_INI);
			if (outputFile != null && outputFile.exists()) {
				outputFile.delete();
			}
			if (configDir != null && configDir.exists()) {
				configDir.delete();
			}
		}
	}

	public void setConfigData(ConfigData configData) {
		this.configData.initialize();
		this.configData.setInitialBundleStartLevel(configData.getInitialBundleStartLevel());
		this.configData.setBeginningFwStartLevel(configData.getBeginingFwStartLevel());
		BundleInfo[] bInfos = configData.getBundles();
		for (int i = 0; i < bInfos.length; i++)
			this.configData.addBundle(bInfos[i]);
		this.configData.setProperties(configData.getProperties());
		if (this.configData.getFwName().equals(configData.getFwName()))
			if (this.configData.getFwVersion().equals(configData.getFwVersion())) {
				// TODO refine the algorithm to copying fw dependent props.
				//  configData.getFwName()/getFwVersion()/
				//	getLauncherName()/getLauncherVersion() might be taken into consideration.
				this.configData.setProperties(configData.getProperties());
			}
	}

	/**
	 * 1. get all ServiceReferences of ConfiguratorManipulator.
	 * 2. Check if there any ConfiguratorBundle in the Bundles list that can be manipulated by
	 * 	the available ConfiguratorManipulators.
	 * 3. Choose the one that will be firstly started among them.
	 * 4. set the object that corresponds to the chosen ConfiguratorBundle.
	 *
	 */
	private ConfiguratorManipulator setConfiguratorManipulator() {
		if (context == null) {
			this.configuratorManipulator = this.fwAdmin.getConfiguratorManipulator();
			return null;
		}
		ServiceReference[] references = cmTracker.getServiceReferences();
		if (references == null)
			return null;

		//		int count = cmTracker.getTrackingCount();
		//		if (count == this.trackingCount)
		//			return;
		//		this.trackingCount = count;

		BundleInfo[] bInfos = configData.getBundles();
		int initialBSL = configData.getInitialBundleStartLevel();
		bInfos = Utils.sortBundleInfos(bInfos, initialBSL);
		//int index = -1;
		ConfiguratorManipulator previousConfiguratorManipulator = configuratorManipulator;
		configuratorManipulator = null;
		for (int i = 0; i < bInfos.length; i++) {
			URI location = bInfos[i].getLocation();
			if (!bInfos[i].isMarkedAsStarted())
				continue;
			for (int j = 0; j < references.length; j++)
				if (references[j].getProperty(ConfiguratorManipulator.SERVICE_PROP_KEY_CONFIGURATOR_BUNDLESYMBOLICNAME).equals(Utils.getPathFromClause(Utils.getManifestMainAttributes(location, Constants.BUNDLE_SYMBOLICNAME)))) {
					configuratorManipulator = (ConfiguratorManipulator) cmTracker.getService(references[j]);
					break;
				}
			if (configuratorManipulator != null)
				break;
		}
		if (configuratorManipulator != previousConfiguratorManipulator)
			return previousConfiguratorManipulator;
		return null;
	}

	public void setLauncherData(LauncherData value) {
		launcherData.initialize();
		launcherData.setFwConfigLocation(value.getFwConfigLocation());
		launcherData.setFwPersistentDataLocation(value.getFwPersistentDataLocation(), value.isClean());
		launcherData.setJvm(value.getJvm());
		launcherData.setJvmArgs(value.getJvmArgs());
		launcherData.setOS(value.getOS());
		if (launcherData.getFwName().equals(value.getFwName()))
			if (launcherData.getFwVersion().equals(value.getFwVersion())) {
				// TODO launcherData.getFwName()/getFwVersion()/
				//	getLauncherName()/getLauncherVersion() might be taken into consideration
				//  for copying .
				launcherData.setFwJar(value.getFwJar());
				launcherData.setHome(value.getHome());
				launcherData.setLauncher(value.getLauncher());
				launcherData.setLauncherConfigLocation(value.getLauncherConfigLocation());
			}
	}

	/**
	 * Temporal implementation.
	 *
	 * If a property of the given key should be eliminated
	 *  from FwDependentProperties and FwIndependentProperties,
	 *  return true. Otherwise false.
	 *
	 * @param key
	 * @return true if it should be elimineted from FwDependentProperties and FwIndependentProperties,
	 */
	private boolean toBeEliminated(String key) {
		if (key.startsWith("java.")) //$NON-NLS-1$
			return true;
		return false;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("++++++++++++++++++++++++++++++++++++++++++\n" + "Class:" + this.getClass().getName() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		sb.append("------------- LauncherData -----------\n"); //$NON-NLS-1$
		sb.append(launcherData.toString());
		sb.append("------------- ConfigData -----------\n"); //$NON-NLS-1$
		sb.append(configData.toString());
		sb.append("\n" + Utils.toStringProperties("platformProperties", this.platformProperties)); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("++++++++++++++++++++++++++++++++++++++++++\n"); //$NON-NLS-1$
		return sb.toString();
	}

	private void updateAccordingToExpectedState(BundlesState bundlesState) {
		//		File newFwJar = EquinoxBundlesState.getFwJar(launcherData, configData);
		//		if (bundlesState instanceof EquinoxBundlesState)
		//			((EquinoxBundlesState) bundlesState).setFwJar(newFwJar);
		//
		//		if (launcherData.getFwJar() == null && newFwJar != null)
		//			launcherData.setFwJar(newFwJar);
		BundleInfo[] newBundleInfos = bundlesState.getExpectedState();
		configData.setBundles(newBundleInfos);
	}

	public static String makeRelative(String original, String rootPath) {
		IPath path = new Path(original);
		// ensure we have an absolute path to start with
		if (!path.isAbsolute())
			return original;

		//Returns the original string if no relativization has been done
		IPath result = path.makeRelativeTo(new Path(rootPath));
		return path.equals(result) ? original : result.toString();
	}

	public static String makeRelative(String urlString, URL rootURL) {
		// we only traffic in file: URLs
		int index = urlString.indexOf(FILE_PROTOCOL);
		if (index == -1)
			return urlString;
		index = index + 5;

		// ensure we have an absolute path to start with
		boolean done = false;
		URL url = null;
		String file = urlString;
		while (!done) {
			try {
				url = new URL(file);
				file = url.getFile();
			} catch (java.net.MalformedURLException e) {
				done = true;
			}
		}
		if (url == null || !new File(url.getFile()).isAbsolute())
			return urlString;

		String rootString = rootURL.toExternalForm();
		IPath one = new Path(urlString.substring(index));
		IPath two = new Path(rootString.substring(rootString.indexOf(FILE_PROTOCOL) + 5));
		String deviceOne = one.getDevice();
		String deviceTwo = two.getDevice();
		// do checking here because we want to return the exact string we got initially if
		// we are unable to make it relative.
		if (deviceOne != deviceTwo && (deviceOne == null || !deviceOne.equalsIgnoreCase(two.getDevice())))
			return urlString;

		return urlString.substring(0, index) + one.makeRelativeTo(two);
	}

	public static String makeArrayRelative(String array, URL rootURL) {
		StringBuffer buffer = new StringBuffer();
		for (StringTokenizer tokenizer = new StringTokenizer(array, COMMA); tokenizer.hasMoreTokens();) {
			String token = tokenizer.nextToken();
			String absolute = makeRelative(token, rootURL);
			buffer.append(absolute);
			if (tokenizer.hasMoreTokens())
				buffer.append(',');
		}
		return buffer.toString();
	}

	public static String makeArrayAbsolute(String array, URL rootURL) {
		StringBuffer buffer = new StringBuffer();
		for (StringTokenizer tokenizer = new StringTokenizer(array, COMMA); tokenizer.hasMoreTokens();) {
			String token = tokenizer.nextToken();
			String absolute = makeAbsolute(token, rootURL);
			buffer.append(absolute);
			if (tokenizer.hasMoreTokens())
				buffer.append(',');
		}
		return buffer.toString();
	}

	/*
	 * Make the given path absolute to the specified root, if applicable. If not, then
	 * return the path as-is.
	 *
	 * TODO: can we use URIUtil in these #make* methods?
	 */
	public static String makeAbsolute(String original, String rootPath) {
		IPath path = new Path(original);
		// ensure we have a relative path to start with
		if (path.isAbsolute())
			return original;
		IPath root = new Path(rootPath);
		return root.addTrailingSeparator().append(original.replace(':', '}')).toOSString().replace('}', ':');
	}

	public static String makeAbsolute(String urlString, URL rootURL) {
		// we only traffic in file: URLs
		int index = urlString.indexOf(FILE_PROTOCOL);
		if (index == -1)
			return urlString;
		index = index + 5;

		// ensure we have a relative path to start with
		boolean done = false;
		URL url = null;
		String file = urlString;
		while (!done) {
			try {
				url = new URL(file);
				file = url.getFile();
			} catch (java.net.MalformedURLException e) {
				done = true;
			}
		}
		if (url == null || new File(url.getFile()).isAbsolute())
			return urlString;

		return urlString.substring(0, index - 5) + makeAbsolute(urlString.substring(index), rootURL.toExternalForm());
	}
}
