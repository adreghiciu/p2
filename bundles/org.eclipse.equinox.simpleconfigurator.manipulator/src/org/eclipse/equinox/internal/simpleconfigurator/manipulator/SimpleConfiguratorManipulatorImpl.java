/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.simpleconfigurator.manipulator;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.frameworkadmin.equinox.ParserUtils;
import org.eclipse.equinox.internal.frameworkadmin.utils.Utils;
import org.eclipse.equinox.internal.provisional.configuratormanipulator.ConfiguratorManipulator;
import org.eclipse.equinox.internal.provisional.frameworkadmin.*;
import org.eclipse.equinox.internal.simpleconfigurator.SimpleConfiguratorImpl;
import org.eclipse.equinox.internal.simpleconfigurator.utils.EquinoxUtils;
import org.eclipse.equinox.internal.simpleconfigurator.utils.SimpleConfiguratorUtils;
import org.eclipse.equinox.simpleconfigurator.manipulator.SimpleConfiguratorManipulator;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

/**
 * 
 */
public class SimpleConfiguratorManipulatorImpl implements SimpleConfiguratorManipulator, ConfiguratorManipulator {
	class LocationInfo {
		URI[] prerequisiteLocations = null;
		URI systemBundleLocation = null;
		URI[] systemFragmentedBundleLocations = null;
	}

	private final static boolean DEBUG = false;

	private static final BundleInfo[] NULL_BUNDLEINFOS = new BundleInfo[0];

	public static final String PROP_KEY_EXCLUSIVE_INSTALLATION = "org.eclipse.equinox.simpleconfigurator.exclusiveInstallation"; //$NON-NLS-1$
	public static final String CONFIG_LIST = "bundles.info"; //$NON-NLS-1$
	public static final String CONFIGURATOR_FOLDER = "org.eclipse.equinox.simpleconfigurator"; //$NON-NLS-1$
	public static final String PROP_KEY_CONFIGURL = "org.eclipse.equinox.simpleconfigurator.configUrl"; //$NON-NLS-1$

	private Set manipulators = new HashSet();

	/**	
	 * Return the ConfiguratorConfigFile which is determined 
	 * by the parameters set in Manipulator. 
	 * 
	 * @param manipulator
	 * @return File
	 */
	private static File getConfigFile(Manipulator manipulator) throws IllegalStateException {
		File fwConfigLoc = manipulator.getLauncherData().getFwConfigLocation();
		File baseDir = null;
		if (fwConfigLoc == null) {
			baseDir = manipulator.getLauncherData().getHome();
			if (baseDir == null) {
				if (manipulator.getLauncherData().getLauncher() != null) {
					baseDir = manipulator.getLauncherData().getLauncher().getParentFile();
				} else {
					throw new IllegalStateException("All of fwConfigFile, home, launcher are not set.");
				}
			}
		} else {
			if (fwConfigLoc.exists())
				if (fwConfigLoc.isDirectory())
					baseDir = fwConfigLoc;
				else
					baseDir = fwConfigLoc.getParentFile();
			else {
				// TODO We need to decide whether launcher data configLocation is the location of a file or a directory 
				if (fwConfigLoc.getName().endsWith(".ini")) //$NON-NLS-1$
					baseDir = fwConfigLoc.getParentFile();
				else
					baseDir = fwConfigLoc;
			}
		}
		File configuratorFolder = new File(baseDir, SimpleConfiguratorManipulatorImpl.CONFIGURATOR_FOLDER);
		File targetFile = new File(configuratorFolder, SimpleConfiguratorManipulatorImpl.CONFIG_LIST);
		if (!Utils.createParentDir(targetFile))
			return null;
		return targetFile;
	}

	static boolean isPrerequisiteBundles(URI location, LocationInfo info) {
		boolean ret = false;

		if (info.prerequisiteLocations == null)
			return false;
		for (int i = 0; i < info.prerequisiteLocations.length; i++)
			if (location.equals(info.prerequisiteLocations[i])) {
				ret = true;
				break;
			}

		return ret;
	}

	static boolean isSystemBundle(URI location, LocationInfo info) {
		if (info.systemBundleLocation == null)
			return false;
		if (location.equals(info.systemBundleLocation))
			return true;
		return false;
	}

	static boolean isSystemFragmentBundle(URI location, LocationInfo info) {
		boolean ret = false;
		if (info.systemFragmentedBundleLocations == null)
			return false;
		for (int i = 0; i < info.systemFragmentedBundleLocations.length; i++)
			if (location.equals(info.systemFragmentedBundleLocations[i])) {
				ret = true;
				break;
			}
		return ret;
	}

	private static boolean isTargetConfiguratorBundle(BundleInfo[] bInfos) {
		for (int i = 0; i < bInfos.length; i++) {
			if (isTargetConfiguratorBundle(bInfos[i].getLocation())) {
				return true;
				//TODO confirm that startlevel of configurator bundle must be no larger than beginning start level of fw. However, there is no way to know the start level of cached ones.
			}
		}
		return false;
	}

	private static boolean isTargetConfiguratorBundle(URI location) {
		final String symbolic = Utils.getPathFromClause(Utils.getManifestMainAttributes(location, Constants.BUNDLE_SYMBOLICNAME));
		return (SimpleConfiguratorManipulator.SERVICE_PROP_VALUE_CONFIGURATOR_SYMBOLICNAME.equals(symbolic));
	}

	private void algorithm(int initialSl, SortedMap bslToList, BundleInfo configuratorBInfo, List setToInitialConfig, List setToSimpleConfig, LocationInfo info) {
		int configuratorSL = configuratorBInfo.getStartLevel();

		Integer sL0 = (Integer) bslToList.keySet().iterator().next();// StartLevel == 0;
		List list0 = (List) bslToList.get(sL0);
		if (sL0.intValue() == 0)
			for (Iterator ite2 = list0.iterator(); ite2.hasNext();) {
				BundleInfo bInfo = (BundleInfo) ite2.next();
				if (isSystemBundle(bInfo.getLocation(), info)) {
					setToSimpleConfig.add(bInfo);
					break;
				}
			}

		for (Iterator ite = bslToList.keySet().iterator(); ite.hasNext();) {
			Integer sL = (Integer) ite.next();
			List list = (List) bslToList.get(sL);

			if (sL.intValue() < configuratorSL) {
				for (Iterator ite2 = list.iterator(); ite2.hasNext();) {
					BundleInfo bInfo = (BundleInfo) ite2.next();
					if (!isSystemBundle(bInfo.getLocation(), info))
						setToInitialConfig.add(bInfo);
				}
			} else if (sL.intValue() > configuratorSL) {
				for (Iterator ite2 = list.iterator(); ite2.hasNext();) {
					BundleInfo bInfo = (BundleInfo) ite2.next();
					if (isPrerequisiteBundles(bInfo.getLocation(), info) || isSystemFragmentBundle(bInfo.getLocation(), info))
						if (!isSystemBundle(bInfo.getLocation(), info))
							setToInitialConfig.add(bInfo);
					setToSimpleConfig.add(bInfo);
				}
			} else {
				boolean found = false;
				for (Iterator ite2 = list.iterator(); ite2.hasNext();) {
					BundleInfo bInfo = (BundleInfo) ite2.next();
					if (found) {
						if (!isSystemBundle(bInfo.getLocation(), info))
							if (isPrerequisiteBundles(bInfo.getLocation(), info) || isSystemFragmentBundle(bInfo.getLocation(), info))
								setToInitialConfig.add(bInfo);
						setToSimpleConfig.add(bInfo);
						continue;
					}
					if (isTargetConfiguratorBundle(bInfo.getLocation()))
						found = true;
					else if (!isSystemBundle(bInfo.getLocation(), info))
						setToInitialConfig.add(bInfo);
					setToSimpleConfig.add(bInfo);
				}
			}
		}

		setToInitialConfig.add(configuratorBInfo);
	}

	private boolean checkResolve(BundleInfo bInfo, BundlesState state) {//throws ManipulatorException {
		if (bInfo == null)
			throw new IllegalArgumentException("bInfo is null.");

		if (!state.isResolved())
			state.resolve(false);
		//		if (DEBUG)
		//			System.out.println(state.toString());

		if (!state.isResolved(bInfo)) {
			printoutUnsatisfiedConstraints(bInfo, state);
			return false;
		}
		return true;
	}

	private boolean divideBundleInfos(Manipulator manipulator, List setToInitialConfig, List setToSimpleConfig, final int initialBSL) {
		BundlesState state = manipulator.getBundlesState();
		BundleInfo[] targetBundleInfos = null;
		if (state.isFullySupported()) {
			targetBundleInfos = state.getExpectedState();
		} else {
			targetBundleInfos = manipulator.getConfigData().getBundles();
		}
		BundleInfo configuratorBInfo = null;
		for (int i = 0; i < targetBundleInfos.length; i++) {
			if (isTargetConfiguratorBundle(targetBundleInfos[i].getLocation())) {
				if (targetBundleInfos[i].isMarkedAsStarted()) {
					configuratorBInfo = targetBundleInfos[i];
					break;
				}
			}
		}
		if (configuratorBInfo == null && !manipulators.contains(manipulator)) {
			return false;
		} else if (manipulators.contains(manipulator) && targetBundleInfos.length == 0) {
			// Resulting state will have no bundles - so is an uninstall, including
			// uninstall of the configurator. However, we have seen this manipulator
			// before with a target configurator bundle, so allow uninstall to proceed,
			// but only get one chance.
			manipulators.remove(manipulator);
		} else if (!manipulators.contains(manipulator)) {
			manipulators.add(manipulator);
		}

		if (state.isFullySupported()) {
			state.resolve(false);
		}

		LocationInfo info = new LocationInfo();
		setSystemBundles(state, info);
		if (configuratorBInfo != null) {
			setPrerequisiteBundles(configuratorBInfo, state, info);
			SortedMap bslToList = getSortedMap(initialBSL, targetBundleInfos);
			algorithm(initialBSL, bslToList, configuratorBInfo, setToInitialConfig, setToSimpleConfig, info);
		}
		return true;
	}

	private SortedMap getSortedMap(int initialSl, BundleInfo[] bInfos) {
		SortedMap bslToList = new TreeMap();
		for (int i = 0; i < bInfos.length; i++) {
			Integer sL = new Integer(bInfos[i].getStartLevel());
			if (sL.intValue() == BundleInfo.NO_LEVEL)
				sL = new Integer(initialSl);
			List list = (List) bslToList.get(sL);
			if (list == null) {
				list = new LinkedList();
				bslToList.put(sL, list);
			}
			list.add(bInfos[i]);
		}
		return bslToList;
	}

	private BundleInfo[] orderingInitialConfig(List setToInitialConfig) {
		List notToBeStarted = new LinkedList();
		List toBeStarted = new LinkedList();
		for (Iterator ite2 = setToInitialConfig.iterator(); ite2.hasNext();) {
			BundleInfo bInfo = (BundleInfo) ite2.next();
			if (bInfo.isMarkedAsStarted())
				toBeStarted.add(bInfo);
			else
				notToBeStarted.add(bInfo);
		}
		setToInitialConfig.clear();
		setToInitialConfig.addAll(notToBeStarted);
		setToInitialConfig.addAll(toBeStarted);
		return Utils.getBundleInfosFromList(setToInitialConfig);

	}

	private void printoutUnsatisfiedConstraints(BundleInfo bInfo, BundlesState state) {
		if (DEBUG) {
			StringBuffer sb = new StringBuffer();
			sb.append("Missing constraints:\n"); //$NON-NLS-1$
			String[] missings = state.getUnsatisfiedConstraints(bInfo);
			for (int i = 0; i < missings.length; i++)
				sb.append(" " + missings[i] + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println(sb.toString());
		}
	}

	public BundleInfo[] loadConfiguration(BundleContext context, String infoPath) throws IOException {
		URI installArea = EquinoxUtils.getInstallLocationURI(context);

		URL configURL = null;
		InputStream stream = null;

		if (infoPath == null) {
			SimpleConfiguratorImpl simpleImpl = new SimpleConfiguratorImpl(context, null);
			configURL = simpleImpl.getConfigurationURL();
		} else {
			// == (not .equals) use the default source info, currently SOURCE_INFO_PATH
			boolean defaultSource = (infoPath == SOURCE_INFO);
			if (defaultSource)
				infoPath = SOURCE_INFO_PATH;

			Location configLocation = EquinoxUtils.getConfigLocation(context);
			configURL = configLocation.getDataArea(infoPath);
			try {
				stream = configURL.openStream();
			} catch (FileNotFoundException e) {
				if (defaultSource && configLocation.getParentLocation() != null) {
					configURL = configLocation.getParentLocation().getDataArea(infoPath);
				} else {
					return new BundleInfo[0];
				}
			}
		}
		if (configURL == null)
			return new BundleInfo[0];
		else if (stream == null) {
			try {
				stream = configURL.openStream();
			} catch (FileNotFoundException e) {
				return new BundleInfo[0];
			}
		}

		//stream will be closed
		return loadConfiguration(stream, installArea);
	}

	/*
	 * InputStream must be closed
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.simpleconfigurator.manipulator.SimpleConfiguratorManipulator#loadConfiguration(java.io.InputStream, java.net.URI)
	 */
	public BundleInfo[] loadConfiguration(InputStream stream, URI installArea) throws IOException {
		if (stream == null)
			return NULL_BUNDLEINFOS;

		List simpleBundles = SimpleConfiguratorUtils.readConfiguration(stream, installArea);

		// convert to FrameworkAdmin BundleInfo Type
		BundleInfo[] result = new BundleInfo[simpleBundles.size()];
		int i = 0;
		for (Iterator iterator = simpleBundles.iterator(); iterator.hasNext();) {
			org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo simpleInfo = (org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo) iterator.next();
			URI location = simpleInfo.getLocation();
			if (!location.isAbsolute() && simpleInfo.getBaseLocation() != null)
				location = URIUtil.makeAbsolute(location, simpleInfo.getBaseLocation());

			BundleInfo bundleInfo = new BundleInfo(simpleInfo.getSymbolicName(), simpleInfo.getVersion(), location, simpleInfo.getStartLevel(), simpleInfo.isMarkedAsStarted());
			bundleInfo.setBaseLocation(simpleInfo.getBaseLocation());
			result[i++] = bundleInfo;
		}
		return result;
	}

	public void saveConfiguration(BundleInfo[] configuration, OutputStream stream, URI installArea) throws IOException {
		org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo[] simpleInfos = convertBundleInfos(configuration, installArea);
		SimpleConfiguratorManipulatorUtils.writeConfiguration(simpleInfos, stream);
	}

	public void saveConfiguration(BundleInfo[] configuration, File outputFile, URI installArea) throws IOException {
		saveConfiguration(configuration, outputFile, installArea, false);
	}

	private void saveConfiguration(BundleInfo[] configuration, File outputFile, URI installArea, boolean backup) throws IOException {
		if (backup && outputFile.exists()) {
			File backupFile = Utils.getSimpleDataFormattedFile(outputFile);
			if (!outputFile.renameTo(backupFile)) {
				throw new IOException("Fail to rename from (" + outputFile + ") to (" + backupFile + ")");
			}
		}

		org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo[] simpleInfos = convertBundleInfos(configuration, installArea);

		// if empty remove the configuration file
		if (simpleInfos == null || simpleInfos.length == 0) {
			if (outputFile.exists()) {
				outputFile.delete();
			}
			File parentDir = outputFile.getParentFile();
			if (parentDir.exists()) {
				parentDir.delete();
			}
			return;
		}
		SimpleConfiguratorManipulatorUtils.writeConfiguration(simpleInfos, outputFile);
	}

	private org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo[] convertBundleInfos(BundleInfo[] configuration, URI installArea) {
		// convert to SimpleConfigurator BundleInfo Type
		org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo[] simpleInfos = new org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo[configuration.length];
		for (int i = 0; i < configuration.length; i++) {
			BundleInfo bundleInfo = configuration[i];
			URI location = bundleInfo.getLocation();
			if (bundleInfo.getSymbolicName() == null || bundleInfo.getVersion() == null || location == null)
				throw new IllegalArgumentException("Cannot persist bundleinfo: " + bundleInfo.toString());
			//only need to make a new BundleInfo if we are changing it.
			if (installArea != null)
				location = URIUtil.makeRelative(location, installArea);
			simpleInfos[i] = new org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo(bundleInfo.getSymbolicName(), bundleInfo.getVersion(), location, bundleInfo.getStartLevel(), bundleInfo.isMarkedAsStarted());
			simpleInfos[i].setBaseLocation(bundleInfo.getBaseLocation());
		}
		return simpleInfos;
	}

	public BundleInfo[] save(Manipulator manipulator, boolean backup) throws IOException {
		List setToInitialConfig = new LinkedList();
		List setToSimpleConfig = new LinkedList();
		ConfigData configData = manipulator.getConfigData();

		if (!divideBundleInfos(manipulator, setToInitialConfig, setToSimpleConfig, configData.getInitialBundleStartLevel()))
			return configData.getBundles();

		File outputFile = getConfigFile(manipulator);
		URI installArea = ParserUtils.getOSGiInstallArea(Arrays.asList(manipulator.getLauncherData().getProgramArgs()), manipulator.getConfigData().getProperties(), manipulator.getLauncherData()).toURI();
		saveConfiguration((BundleInfo[]) setToSimpleConfig.toArray(new BundleInfo[setToSimpleConfig.size()]), outputFile, installArea, backup);
		configData.setProperty(SimpleConfiguratorManipulatorImpl.PROP_KEY_CONFIGURL, outputFile.toURL().toExternalForm());
		return orderingInitialConfig(setToInitialConfig);
	}

	void setPrerequisiteBundles(BundleInfo configuratorBundleInfo, BundlesState state, LocationInfo info) {
		if (state.isFullySupported())
			if (!this.checkResolve(configuratorBundleInfo, state)) {
				printoutUnsatisfiedConstraints(configuratorBundleInfo, state);
				return;
			}
		BundleInfo[] prerequisites = state.getPrerequisteBundles(configuratorBundleInfo);
		info.prerequisiteLocations = new URI[prerequisites.length];
		for (int i = 0; i < prerequisites.length; i++)
			info.prerequisiteLocations[i] = prerequisites[i].getLocation();
		return;

	}

	void setSystemBundles(BundlesState state, LocationInfo info) {
		BundleInfo systemBundleInfo = state.getSystemBundle();
		if (systemBundleInfo == null) {
			// TODO Log
			//throw new IllegalStateException("There is no systemBundle.\n");
			return;
		}
		if (state.isFullySupported())
			if (!this.checkResolve(systemBundleInfo, state)) {
				printoutUnsatisfiedConstraints(systemBundleInfo, state);
				return;
			}
		info.systemBundleLocation = systemBundleInfo.getLocation();
		BundleInfo[] fragments = state.getSystemFragmentedBundles();
		info.systemFragmentedBundleLocations = new URI[fragments.length];
		for (int i = 0; i < fragments.length; i++)
			info.systemFragmentedBundleLocations[i] = fragments[i].getLocation();
	}

	public void updateBundles(Manipulator manipulator) throws IOException {
		if (DEBUG)
			System.out.println("SimpleConfiguratorManipulatorImpl#updateBundles()"); //$NON-NLS-1$

		BundlesState bundleState = manipulator.getBundlesState();

		if (bundleState == null)
			return;
		if (bundleState.isFullySupported())
			bundleState.resolve(true);

		BundleInfo[] currentBInfos = bundleState.getExpectedState();
		if (!isTargetConfiguratorBundle(currentBInfos))
			return;
		Properties properties = new Properties();
		String[] jvmArgs = manipulator.getLauncherData().getJvmArgs();
		for (int i = 0; i < jvmArgs.length; i++) {
			if (jvmArgs[i].startsWith("-D")) { //$NON-NLS-1$
				int index = jvmArgs[i].indexOf("="); //$NON-NLS-1$
				if (index > 0 && jvmArgs[i].length() > 2) {
					String key = jvmArgs[i].substring(2, index);
					String value = jvmArgs[i].substring(index + 1);
					properties.setProperty(key, value);
				}
			}
		}

		Utils.appendProperties(properties, manipulator.getConfigData().getProperties());
		boolean exclusiveInstallation = Boolean.valueOf(properties.getProperty(SimpleConfiguratorManipulatorImpl.PROP_KEY_EXCLUSIVE_INSTALLATION)).booleanValue();
		File configFile = getConfigFile(manipulator);

		File installArea = ParserUtils.getOSGiInstallArea(Arrays.asList(manipulator.getLauncherData().getProgramArgs()), manipulator.getConfigData().getProperties(), manipulator.getLauncherData());
		BundleInfo[] toInstall = null;
		try {
			//input stream will be closed for us
			toInstall = loadConfiguration(new FileInputStream(configFile), installArea.toURI());
		} catch (FileNotFoundException e) {
			//no file, just return an empty list
			toInstall = new BundleInfo[0];
		}

		List toUninstall = new LinkedList();
		if (exclusiveInstallation)
			for (int i = 0; i < currentBInfos.length; i++) {
				boolean install = false;
				for (int j = 0; j < toInstall.length; j++)
					if (currentBInfos[i].getLocation().equals(toInstall[j].getLocation())) {
						install = true;
						break;
					}
				if (!install)
					toUninstall.add(currentBInfos[i]);
			}

		for (int i = 0; i < toInstall.length; i++) {
			try {
				bundleState.installBundle(toInstall[i]);
			} catch (RuntimeException e) {
				//Ignore
			}
		}
		if (exclusiveInstallation)
			for (Iterator ite = toUninstall.iterator(); ite.hasNext();) {
				BundleInfo bInfo = (BundleInfo) ite.next();
				bundleState.uninstallBundle(bInfo);
			}

		bundleState.resolve(true);
		manipulator.getConfigData().setBundles(bundleState.getExpectedState());
	}

	public void cleanup(Manipulator manipulator) {
		File outputFile = getConfigFile(manipulator);
		outputFile.delete();

		if (outputFile.getParentFile().isDirectory())
			outputFile.getParentFile().delete();
	}
}
