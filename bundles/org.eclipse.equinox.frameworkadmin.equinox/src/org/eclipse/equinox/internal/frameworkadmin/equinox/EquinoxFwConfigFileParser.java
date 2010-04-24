/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.frameworkadmin.equinox;

import java.io.*;
import java.net.*;
import java.util.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.frameworkadmin.equinox.utils.FileUtils;
import org.eclipse.equinox.internal.frameworkadmin.utils.Utils;
import org.eclipse.equinox.internal.provisional.frameworkadmin.*;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;

public class EquinoxFwConfigFileParser {
	private static final Set KNOWN_PROPERTIES = new HashSet(Arrays.asList(new String[] {EquinoxConstants.PROP_BUNDLES, EquinoxConstants.PROP_FW_EXTENSIONS, EquinoxConstants.PROP_INITIAL_STARTLEVEL, EquinoxConstants.PROP_BUNDLES_STARTLEVEL}));
	private static final String CONFIG_DIR = "@config.dir/"; //$NON-NLS-1$
	private static final String KEY_ECLIPSE_PROV_DATA_AREA = "eclipse.p2.data.area"; //$NON-NLS-1$
	private static final String KEY_ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL = "org.eclipse.equinox.simpleconfigurator.configUrl"; //$NON-NLS-1$
	private static final String REFERENCE_SCHEME = "reference:"; //$NON-NLS-1$
	private static final String FILE_PROTOCOL = "file:"; //$NON-NLS-1$
	private static boolean DEBUG = false;

	public EquinoxFwConfigFileParser(BundleContext context) {
		//Empty
	}

	private static StringBuffer toOSGiBundleListForm(BundleInfo bundleInfo, URI location) {
		StringBuffer locationString = new StringBuffer(REFERENCE_SCHEME);
		if (URIUtil.isFileURI(location))
			locationString.append(URIUtil.toUnencodedString(location));
		else if (location.getScheme() == null)
			locationString.append(FILE_PROTOCOL).append(URIUtil.toUnencodedString(location));
		else
			locationString = new StringBuffer(URIUtil.toUnencodedString(location));

		int startLevel = bundleInfo.getStartLevel();
		boolean toBeStarted = bundleInfo.isMarkedAsStarted();

		StringBuffer sb = new StringBuffer();
		sb.append(locationString);
		if (startLevel == BundleInfo.NO_LEVEL && !toBeStarted)
			return sb;
		sb.append('@');
		if (startLevel != BundleInfo.NO_LEVEL)
			sb.append(startLevel);
		if (toBeStarted)
			sb.append(":start"); //$NON-NLS-1$
		return sb;
	}

	private static boolean getMarkedAsStartedFormat(String startInfo) {
		if (startInfo == null)
			return false;
		startInfo = startInfo.trim();
		int colon = startInfo.indexOf(':');
		if (colon > -1) {
			return startInfo.substring(colon + 1).equals("start"); //$NON-NLS-1$
		}
		return startInfo.equals("start"); //$NON-NLS-1$
	}

	private static int getStartLevel(String startInfo) {
		if (startInfo == null)
			return BundleInfo.NO_LEVEL;
		startInfo = startInfo.trim();
		int colon = startInfo.indexOf(":"); //$NON-NLS-1$
		if (colon > 0) {
			try {
				return Integer.parseInt(startInfo.substring(0, colon));
			} catch (NumberFormatException e) {
				return BundleInfo.NO_LEVEL;
			}
		}
		return BundleInfo.NO_LEVEL;
	}

	private void readBundlesList(Manipulator manipulator, URI osgiInstallArea, Properties props) throws NumberFormatException {
		ConfigData configData = manipulator.getConfigData();

		BundleInfo[] fwExtensions = parseBundleList(manipulator, props.getProperty(EquinoxConstants.PROP_FW_EXTENSIONS));
		if (fwExtensions != null) {
			for (int i = 0; i < fwExtensions.length; i++) {
				fwExtensions[i].setFragmentHost(Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
				configData.addBundle(fwExtensions[i]);
			}
		}

		BundleInfo[] bundles = parseBundleList(manipulator, props.getProperty(EquinoxConstants.PROP_BUNDLES));
		if (bundles != null) {
			for (int i = 0; i < bundles.length; i++) {
				configData.addBundle(bundles[i]);
			}
		}
	}

	private BundleInfo[] parseBundleList(Manipulator manipulator, String value) {
		if (value == null || value.length() == 0)
			return null;

		List bundles = new ArrayList();
		String[] bInfoStrings = Utils.getTokens(value, ","); //$NON-NLS-1$
		for (int i = 0; i < bInfoStrings.length; i++) {
			String entry = bInfoStrings[i].trim();
			entry = FileUtils.removeEquinoxSpecificProtocols(entry);

			int indexStartInfo = entry.indexOf('@');
			String location = (indexStartInfo == -1) ? entry : entry.substring(0, indexStartInfo);
			URI realLocation = null;
			if (manipulator.getLauncherData().getFwJar() != null) {
				File parentFile = manipulator.getLauncherData().getFwJar().getParentFile();
				try {
					realLocation = URIUtil.makeAbsolute(FileUtils.fromFileURL(location), parentFile.toURI());
				} catch (URISyntaxException e) {
					// try searching as a simple location
					realLocation = FileUtils.getEclipsePluginFullLocation(location, parentFile);
				}
			}
			String slAndFlag = (indexStartInfo > -1) ? entry.substring(indexStartInfo + 1) : null;

			boolean markedAsStarted = getMarkedAsStartedFormat(slAndFlag);
			int startLevel = getStartLevel(slAndFlag);

			if (realLocation != null) {
				bundles.add(new BundleInfo(realLocation, startLevel, markedAsStarted));
				continue;
			}
			if (location != null && location.startsWith(FILE_PROTOCOL))
				try {
					bundles.add(new BundleInfo(FileUtils.fromFileURL(location), startLevel, markedAsStarted));
					continue;
				} catch (URISyntaxException e) {
					//Ignore
				}

			//Fallback case, we use the location as a string
			bundles.add(new BundleInfo(location, null, null, startLevel, markedAsStarted));
		}
		return (BundleInfo[]) bundles.toArray(new BundleInfo[bundles.size()]);
	}

	private void writeBundlesList(File fwJar, Properties props, BundleInfo[] bundles) {
		StringBuffer osgiBundlesList = new StringBuffer();
		StringBuffer osgiFrameworkExtensionsList = new StringBuffer();
		for (int j = 0; j < bundles.length; j++) {
			BundleInfo bundle = bundles[j];

			//framework jar does not get stored on the bundle list, figure out who that is.
			if (fwJar != null) {
				if (URIUtil.sameURI(fwJar.toURI(), bundle.getLocation()))
					continue;
			} else if (EquinoxConstants.FW_SYMBOLIC_NAME.equals(bundle.getSymbolicName()))
				continue;

			URI location = fwJar != null ? URIUtil.makeRelative(bundle.getLocation(), fwJar.getParentFile().toURI()) : bundle.getLocation();

			String fragmentHost = bundle.getFragmentHost();
			boolean isFrameworkExtension = fragmentHost != null && (fragmentHost.startsWith(EquinoxConstants.FW_SYMBOLIC_NAME) || fragmentHost.startsWith(Constants.SYSTEM_BUNDLE_SYMBOLICNAME));

			if (isFrameworkExtension) {
				bundle.setStartLevel(BundleInfo.NO_LEVEL);
				bundle.setMarkedAsStarted(false);
				osgiFrameworkExtensionsList.append(toOSGiBundleListForm(bundle, location));
				osgiFrameworkExtensionsList.append(',');
			} else {
				osgiBundlesList.append(toOSGiBundleListForm(bundle, location));
				osgiBundlesList.append(',');
			}
		}
		if (osgiFrameworkExtensionsList.length() > 0)
			osgiFrameworkExtensionsList.deleteCharAt(osgiFrameworkExtensionsList.length() - 1);
		props.setProperty(EquinoxConstants.PROP_FW_EXTENSIONS, osgiFrameworkExtensionsList.toString());

		if (osgiBundlesList.length() > 0)
			osgiBundlesList.deleteCharAt(osgiBundlesList.length() - 1);
		props.setProperty(EquinoxConstants.PROP_BUNDLES, osgiBundlesList.toString());
	}

	/**
	 * inputFile must be not a directory but a file.
	 *
	 * @param manipulator
	 * @param inputFile
	 * @throws IOException
	 */
	public void readFwConfig(Manipulator manipulator, File inputFile) throws IOException, URISyntaxException {
		if (inputFile.isDirectory())
			throw new IllegalArgumentException(NLS.bind(Messages.exception_inputFileIsDirectory, inputFile));

		//Initialize data structures
		ConfigData configData = manipulator.getConfigData();
		LauncherData launcherData = manipulator.getLauncherData();
		configData.initialize();
		configData.setBundles(null);

		// load configuration properties
		Properties props = loadProperties(inputFile);

		// load shared configuration properties
		Properties sharedConfigProperties = getSharedConfiguration(props.getProperty(EquinoxConstants.PROP_SHARED_CONFIGURATION_AREA), ParserUtils.getOSGiInstallArea(Arrays.asList(manipulator.getLauncherData().getProgramArgs()), props, manipulator.getLauncherData()));
		if (sharedConfigProperties != null) {
			sharedConfigProperties.putAll(props);
			props = sharedConfigProperties;
		}

		//Start figuring out stuffs
		URI rootURI = launcherData.getLauncher() != null ? launcherData.getLauncher().getParentFile().toURI() : null;

		readFwJarLocation(configData, launcherData, props);
		URI configArea = inputFile.getParentFile().toURI();
		readLauncherPath(props, rootURI);
		readp2DataArea(props, configArea);
		readSimpleConfiguratorURL(props, configArea);
		readBundlesList(manipulator, ParserUtils.getOSGiInstallArea(Arrays.asList(launcherData.getProgramArgs()), props, launcherData).toURI(), props);
		readInitialStartLeve(configData, props);
		readDefaultStartLevel(configData, props);

		for (Enumeration enumeration = props.keys(); enumeration.hasMoreElements();) {
			String key = (String) enumeration.nextElement();
			if (KNOWN_PROPERTIES.contains(key))
				continue;
			String value = props.getProperty(key);
			configData.setProperty(key, value);
		}
		Log.log(LogService.LOG_INFO, NLS.bind(Messages.log_configFile, inputFile.getAbsolutePath()));
	}

	private void readDefaultStartLevel(ConfigData configData, Properties props) {
		if (props.getProperty(EquinoxConstants.PROP_BUNDLES_STARTLEVEL) != null)
			configData.setInitialBundleStartLevel(Integer.parseInt(props.getProperty(EquinoxConstants.PROP_BUNDLES_STARTLEVEL)));
	}

	private void writeDefaultStartLevel(ConfigData configData, Properties props) {
		if (configData.getInitialBundleStartLevel() != BundleInfo.NO_LEVEL)
			props.setProperty(EquinoxConstants.PROP_BUNDLES_STARTLEVEL, Integer.toString(configData.getInitialBundleStartLevel()));
	}

	private void readInitialStartLeve(ConfigData configData, Properties props) {
		if (props.getProperty(EquinoxConstants.PROP_INITIAL_STARTLEVEL) != null)
			configData.setBeginningFwStartLevel(Integer.parseInt(props.getProperty(EquinoxConstants.PROP_INITIAL_STARTLEVEL)));
	}

	private void writeInitialStartLevel(ConfigData configData, Properties props) {
		if (configData.getBeginingFwStartLevel() != BundleInfo.NO_LEVEL)
			props.setProperty(EquinoxConstants.PROP_INITIAL_STARTLEVEL, Integer.toString(configData.getBeginingFwStartLevel()));
	}

	private File readFwJarLocation(ConfigData configData, LauncherData launcherData, Properties props) throws URISyntaxException {
		File fwJar = null;
		if (props.getProperty(EquinoxConstants.PROP_OSGI_FW) != null) {
			URI absoluteFwJar = null;
			absoluteFwJar = URIUtil.makeAbsolute(FileUtils.fromFileURL(props.getProperty(EquinoxConstants.PROP_OSGI_FW)), ParserUtils.getOSGiInstallArea(Arrays.asList(launcherData.getProgramArgs()), props, launcherData).toURI());

			props.setProperty(EquinoxConstants.PROP_OSGI_FW, absoluteFwJar.toString());
			String fwJarString = props.getProperty(EquinoxConstants.PROP_OSGI_FW);
			if (fwJarString != null) {
				fwJar = URIUtil.toFile(absoluteFwJar);
				if (fwJar == null)
					throw new IllegalStateException(Messages.exception_noFrameworkLocation);
				//Here we overwrite the value read from eclipse.ini, because the value of osgi.framework always takes precedence.
				launcherData.setFwJar(fwJar);
			} else {
				throw new IllegalStateException(Messages.exception_noFrameworkLocation);
			}
		}
		if (launcherData.getFwJar() != null)
			configData.addBundle(new BundleInfo(launcherData.getFwJar().toURI()));
		return launcherData.getFwJar();
	}

	private void writeFwJarLocation(ConfigData configData, LauncherData launcherData, Properties props) {
		if (launcherData.getFwJar() == null)
			return;
		props.setProperty(EquinoxConstants.PROP_OSGI_FW, FileUtils.toFileURL(URIUtil.makeRelative(launcherData.getFwJar().toURI(), ParserUtils.getOSGiInstallArea(Arrays.asList(launcherData.getProgramArgs()), configData.getProperties(), launcherData).toURI())));
	}

	private static Properties loadProperties(File inputFile) throws FileNotFoundException, IOException {
		Properties props = new Properties();
		InputStream is = null;
		try {
			is = new FileInputStream(inputFile);
			props.load(is);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				Log.log(LogService.LOG_WARNING, NLS.bind(Messages.log_failed_reading_properties, inputFile));
			}
			is = null;
		}
		return props;
	}

	private File findSharedConfigIniFile(URL rootURL, String sharedConfigurationArea) {
		URL sharedConfigurationURL = null;
		try {
			sharedConfigurationURL = new URL(sharedConfigurationArea);
		} catch (MalformedURLException e) {
			// unexpected since this was written by the framework
			Log.log(LogService.LOG_WARNING, NLS.bind(Messages.log_shared_config_url, sharedConfigurationArea));
			return null;
		}

		// check for a relative URL
		if (!sharedConfigurationURL.getPath().startsWith("/")) { //$NON-NLS-1$
			if (!rootURL.getProtocol().equals(sharedConfigurationURL.getProtocol())) {
				Log.log(LogService.LOG_WARNING, NLS.bind(Messages.log_shared_config_relative_url, rootURL.toExternalForm(), sharedConfigurationArea));
				return null;
			}
			try {
				sharedConfigurationURL = new URL(rootURL, sharedConfigurationArea);
			} catch (MalformedURLException e) {
				// unexpected since this was written by the framework
				Log.log(LogService.LOG_WARNING, NLS.bind(Messages.log_shared_config_relative_url, rootURL.toExternalForm(), sharedConfigurationArea));
				return null;
			}
		}
		File sharedConfigurationFolder = new File(sharedConfigurationURL.getPath());
		if (!sharedConfigurationFolder.isDirectory()) {
			Log.log(LogService.LOG_WARNING, NLS.bind(Messages.log_shared_config_file_missing, sharedConfigurationFolder));
			return null;
		}

		File sharedConfigIni = new File(sharedConfigurationFolder, EquinoxConstants.CONFIG_INI);
		if (!sharedConfigIni.exists()) {
			Log.log(LogService.LOG_WARNING, NLS.bind(Messages.log_shared_config_file_missing, sharedConfigIni));
			return null;
		}

		return sharedConfigIni;
	}

	private void readp2DataArea(Properties props, URI configArea) throws URISyntaxException {
		if (props.getProperty(KEY_ECLIPSE_PROV_DATA_AREA) != null) {
			String url = props.getProperty(KEY_ECLIPSE_PROV_DATA_AREA);
			if (url != null) {
				if (url.startsWith(CONFIG_DIR))
					url = "file:" + url.substring(CONFIG_DIR.length()); //$NON-NLS-1$
				props.setProperty(KEY_ECLIPSE_PROV_DATA_AREA, URIUtil.makeAbsolute(FileUtils.fromFileURL(url), configArea).toString());
			}
		}
	}

	private void writep2DataArea(ConfigData configData, Properties props, URI configArea) throws URISyntaxException {
		String dataArea = getFwProperty(configData, KEY_ECLIPSE_PROV_DATA_AREA);
		if (dataArea != null) {
			if (dataArea.startsWith(CONFIG_DIR)) {
				props.setProperty(KEY_ECLIPSE_PROV_DATA_AREA, dataArea);
				return;
			}
			URI dataAreaURI = new URI(dataArea);
			URI relative = URIUtil.makeRelative(dataAreaURI, configArea);
			if (dataAreaURI.equals(relative)) {
				props.setProperty(KEY_ECLIPSE_PROV_DATA_AREA, FileUtils.toFileURL(dataAreaURI));
				return;
			}
			String result = URIUtil.toUnencodedString(relative);
			//We only relativize up to the level where the p2 and config folder are siblings (e.g. foo/p2 and foo/config)
			if (result.startsWith("../..")) //$NON-NLS-1$
				result = dataArea;
			else if (!result.equals(dataArea)) {
				props.setProperty(KEY_ECLIPSE_PROV_DATA_AREA, CONFIG_DIR + result);
				return;
			}
			props.setProperty(KEY_ECLIPSE_PROV_DATA_AREA, FileUtils.toFileURL(new URI(result)));
		}
	}

	private void readLauncherPath(Properties props, URI root) throws URISyntaxException {
		if (props.getProperty(EquinoxConstants.PROP_LAUNCHER_PATH) != null)
			props.setProperty(EquinoxConstants.PROP_LAUNCHER_PATH, URIUtil.makeAbsolute(URIUtil.fromString(props.getProperty(EquinoxConstants.PROP_LAUNCHER_PATH)), root).toString());
	}

	private void writeLauncherPath(ConfigData configData, Properties props, URI root) throws URISyntaxException {
		String value = getFwProperty(configData, EquinoxConstants.PROP_LAUNCHER_PATH);
		if (value != null) {
			URI launcherPathURI = FileUtils.fromPath(value);
			String launcherPath = URIUtil.toUnencodedString(URIUtil.makeRelative(launcherPathURI, root));
			if ("/".equals(launcherPath) || "".equals(launcherPath)) //$NON-NLS-1$ //$NON-NLS-2$
				launcherPath = "."; //$NON-NLS-1$
			props.setProperty(EquinoxConstants.PROP_LAUNCHER_PATH, launcherPath);
		}
	}

	private void readSimpleConfiguratorURL(Properties props, URI configArea) throws URISyntaxException {
		if (props.getProperty(KEY_ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL) != null)
			props.setProperty(KEY_ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL, URIUtil.makeAbsolute(FileUtils.fromFileURL(props.getProperty(KEY_ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL)), configArea).toString());
	}

	private void writeSimpleConfiguratorURL(ConfigData configData, Properties props, URI configArea) throws URISyntaxException {
		//FIXME How would someone set such a value.....
		String value = getFwProperty(configData, KEY_ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL);
		if (value != null)
			props.setProperty(KEY_ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL, FileUtils.toFileURL(URIUtil.makeRelative(URIUtil.fromString(value), configArea)));
	}

	private String getFwProperty(ConfigData data, String key) {
		return data.getProperty(key);
	}

	public void saveFwConfig(BundleInfo[] bInfos, Manipulator manipulator, boolean backup, boolean relative) throws IOException {//{
		ConfigData configData = manipulator.getConfigData();
		LauncherData launcherData = manipulator.getLauncherData();
		//Get the OSGi jar from the bundle.info
		File fwJar = EquinoxBundlesState.getSystemBundleFromBundleInfos(configData);
		launcherData.setFwJar(fwJar);

		File outputFile = launcherData.getFwConfigLocation();
		if (outputFile.exists()) {
			if (outputFile.isFile()) {
				if (!outputFile.getName().equals(EquinoxConstants.CONFIG_INI))
					throw new IllegalStateException(NLS.bind(Messages.exception_fwConfigLocationName, outputFile.getAbsolutePath(), EquinoxConstants.CONFIG_INI));
			} else { // Directory
				outputFile = new File(outputFile, EquinoxConstants.CONFIG_INI);
			}
		} else {
			if (!outputFile.getName().equals(EquinoxConstants.CONFIG_INI)) {
				if (!outputFile.mkdir())
					throw new IOException(NLS.bind(Messages.exception_failedToCreateDir, outputFile));
				outputFile = new File(outputFile, EquinoxConstants.CONFIG_INI);
			}
		}
		String header = "This configuration file was written by: " + this.getClass().getName(); //$NON-NLS-1$

		Properties configProps = new Properties();
		URI rootURI = launcherData.getLauncher() != null ? launcherData.getLauncher().getParentFile().toURI() : null;

		writeFwJarLocation(configData, launcherData, configProps);
		try {
			writeLauncherPath(configData, configProps, rootURI);
			URI configArea = manipulator.getLauncherData().getFwConfigLocation().toURI();
			writep2DataArea(configData, configProps, configArea);
			writeSimpleConfiguratorURL(configData, configProps, configArea);
			writeBundlesList(launcherData.getFwJar(), configProps, bInfos);
			writeInitialStartLevel(configData, configProps);
			writeDefaultStartLevel(configData, configProps);
		} catch (URISyntaxException e) {
			throw new FrameworkAdminRuntimeException(e, Messages.exception_errorSavingConfigIni);
		}

		Properties original = configData.getProperties();
		original.putAll(configProps);
		configProps = original;

		//Deal with the fw jar and ensure it is not set.
		//TODO This can't be done before because of the previous calls to appendProperties

		if (configProps == null || configProps.size() == 0) {
			Log.log(LogService.LOG_WARNING, this, "saveFwConfig() ", Messages.log_configProps); //$NON-NLS-1$
			return;
		}
		if (!Utils.createParentDir(outputFile)) {
			throw new IllegalStateException(NLS.bind(Messages.exception_failedToCreateDir, outputFile.getParent()));
		}

		if (DEBUG)
			Utils.printoutProperties(System.out, "configProps", configProps); //$NON-NLS-1$

		if (backup)
			if (outputFile.exists()) {
				File dest = Utils.getSimpleDataFormattedFile(outputFile);
				if (!outputFile.renameTo(dest))
					throw new IOException(NLS.bind(Messages.exception_failedToRename, outputFile, dest));
				Log.log(LogService.LOG_INFO, this, "saveFwConfig()", NLS.bind(Messages.log_renameSuccessful, outputFile, dest)); //$NON-NLS-1$
			}

		FileOutputStream out = null;
		try {
			out = new FileOutputStream(outputFile);
			//			configProps = makeRelative(configProps, launcherData.getLauncher().getParentFile().toURI(), fwJar, outputFile.getParentFile(), getOSGiInstallArea(manipulator.getLauncherData()));
			filterPropertiesFromSharedArea(configProps, manipulator);
			configProps.store(out, header);
			Log.log(LogService.LOG_INFO, NLS.bind(Messages.log_fwConfigSave, outputFile));
		} finally {
			try {
				out.flush();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			out = null;
		}
	}

	private void filterPropertiesFromSharedArea(Properties configProps, Manipulator manipulator) {
		LauncherData launcherData = manipulator.getLauncherData();
		//Remove from the config file that we are about to write the properties that are unchanged compared to what is in the base
		Properties sharedConfigProperties = getSharedConfiguration(configProps.getProperty(EquinoxConstants.PROP_SHARED_CONFIGURATION_AREA), ParserUtils.getOSGiInstallArea(Arrays.asList(launcherData.getProgramArgs()), configProps, launcherData));
		if (sharedConfigProperties == null)
			return;
		Enumeration keys = configProps.propertyNames();
		while (keys.hasMoreElements()) {
			String key = (String) keys.nextElement();
			String sharedValue = sharedConfigProperties.getProperty(key);
			if (sharedValue == null)
				continue;
			String value = configProps.getProperty(key);
			if (equalsIgnoringSeparators(sharedValue, value)) {
				configProps.remove(key);
				continue;
			}

			if (key.equals(EquinoxConstants.PROP_BUNDLES) && equalBundleLists(manipulator, value, sharedValue)) {
				configProps.remove(key);
				continue;
			}

			if (key.equals(EquinoxConstants.PROP_FW_EXTENSIONS) && equalBundleLists(manipulator, value, sharedValue)) {
				configProps.remove(key);
				continue;
			}
		}
	}

	private boolean equalBundleLists(Manipulator manipulator, String value, String sharedValue) {
		BundleInfo[] bundles = parseBundleList(manipulator, value);
		BundleInfo[] sharedBundles = parseBundleList(manipulator, sharedValue);
		if (bundles == null || sharedBundles == null || bundles.length != sharedBundles.length)
			return false;

		List compareList = new ArrayList(Arrays.asList(bundles));
		compareList.removeAll(Arrays.asList(sharedBundles));
		return compareList.isEmpty();
	}

	private boolean equalsIgnoringSeparators(String s1, String s2) {
		if (s1 == null && s2 == null)
			return true;
		if (s1 == null || s2 == null)
			return false;
		StringBuffer sb1 = new StringBuffer(s1);
		StringBuffer sb2 = new StringBuffer(s2);
		canonicalizePathsForComparison(sb1);
		canonicalizePathsForComparison(sb2);
		return sb1.toString().equals(sb2.toString());
	}

	private void canonicalizePathsForComparison(StringBuffer s) {
		final String[] tokens = new String[] {"\\\\", "\\", "//", "/"}; //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
		for (int t = 0; t < tokens.length; t++) {
			int idx = s.length();
			for (int i = s.length(); i != 0 && idx != -1; i--) {
				idx = s.toString().lastIndexOf(tokens[t], idx);
				if (idx != -1)
					s.replace(idx, idx + tokens[t].length(), "^"); //$NON-NLS-1$
			}
		}
	}

	private Properties getSharedConfiguration(String sharedConfigurationArea, File baseFile) {
		if (sharedConfigurationArea == null)
			return null;
		File sharedConfigIni;
		try {
			sharedConfigIni = findSharedConfigIniFile(baseFile.toURL(), sharedConfigurationArea);
		} catch (MalformedURLException e) {
			return null;
		}
		if (sharedConfigIni != null && sharedConfigIni.exists())
			try {
				return loadProperties(sharedConfigIni);
			} catch (FileNotFoundException e) {
				return null;
			} catch (IOException e) {
				return null;
			}
		return null;
	}
}
