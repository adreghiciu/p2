/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * IBM Corporation - initial implementation and ideas 
 ******************************************************************************/
package org.eclipse.equinox.internal.p2.reconciler.dropins;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.artifact.repository.ArtifactRepositoryManager;
import org.eclipse.equinox.internal.p2.core.helpers.*;
import org.eclipse.equinox.internal.p2.extensionlocation.*;
import org.eclipse.equinox.internal.p2.metadata.repository.MetadataRepositoryManager;
import org.eclipse.equinox.internal.p2.update.Configuration;
import org.eclipse.equinox.internal.p2.update.PathUtil;
import org.eclipse.equinox.internal.provisional.p2.directorywatcher.DirectoryWatcher;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	static final String PROP_APPLICATION_STATUS = "org.eclipse.equinox.p2.reconciler.application.status"; //$NON-NLS-1$
	public static final String ID = "org.eclipse.equinox.p2.reconciler.dropins"; //$NON-NLS-1$
	private static final String DROPINS_DIRECTORY = "org.eclipse.equinox.p2.reconciler.dropins.directory"; //$NON-NLS-1$
	private static final String DROPINS = "dropins"; //$NON-NLS-1$
	private static final String LINKS = "links"; //$NON-NLS-1$
	private static final String CONFIG_INI = "config.ini"; //$NON-NLS-1$
	private static final String PLATFORM_CFG = "org.eclipse.update/platform.xml"; //$NON-NLS-1$
	private static final String CACHE_FILENAME = "cache.timestamps"; //$NON-NLS-1$
	private static final String DIR_ECLIPSE = "eclipse"; //$NON-NLS-1$
	private static final String DIR_PLUGINS = "plugins"; //$NON-NLS-1$
	private static final String DIR_FEATURES = "features"; //$NON-NLS-1$
	private static final String EXT_LINK = ".link"; //$NON-NLS-1$
	private static BundleContext bundleContext;
	private final static Set<IMetadataRepository> repositories = new HashSet<IMetadataRepository>();
	private Collection<File> filesToCheck = null;

	/**
	 * Helper method to create an extension location metadata repository at the given URI. 
	 * If one already exists at that location then an exception will be thrown.
	 * 
	 * This method never returns <code>null</code>.
	 * 
	 * @throws IllegalStateException
	 * @throws ProvisionException 
	 */
	public static IMetadataRepository createExtensionLocationMetadataRepository(URI location, String name, Map<String, String> properties) throws ProvisionException {
		IProvisioningAgent agent = getAgent();
		IMetadataRepositoryManager manager = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
		if (manager == null)
			throw new IllegalStateException("MetadataRepositoryManager not registered."); //$NON-NLS-1$
		ExtensionLocationMetadataRepositoryFactory factory = new ExtensionLocationMetadataRepositoryFactory();
		factory.setAgent(agent);
		IMetadataRepository repository = factory.create(location, name, ExtensionLocationMetadataRepository.TYPE, properties);
		//we need to add the concrete repository to the repository manager, or its properties will not be correct
		((MetadataRepositoryManager) manager).addRepository(repository);
		manager.setRepositoryProperty(location, IRepository.PROP_SYSTEM, String.valueOf(true));
		return repository;
	}

	private static IProvisioningAgent getAgent() {
		return (IProvisioningAgent) ServiceHelper.getService(getContext(), IProvisioningAgent.SERVICE_NAME);
	}

	/**
	 * Helper method to load an extension location metadata repository from the given URL.
	 * 
	 * @throws IllegalStateException
	 * @throws ProvisionException
	 */
	public static IMetadataRepository loadMetadataRepository(URI location, IProgressMonitor monitor) throws ProvisionException {
		IMetadataRepositoryManager manager = (IMetadataRepositoryManager) getAgent().getService(IMetadataRepositoryManager.SERVICE_NAME);
		if (manager == null)
			throw new IllegalStateException("MetadataRepositoryManager not registered."); //$NON-NLS-1$
		IMetadataRepository repository = manager.loadRepository(location, monitor);
		manager.setRepositoryProperty(location, IRepository.PROP_SYSTEM, String.valueOf(true));
		return repository;
	}

	/**
	 * Helper method to create an extension location artifact repository at the given URL. 
	 * If one already exists at that location then an exception will be thrown.
	 * 
	 * This method never returns <code>null</code>.
	 * 
	 * @throws IllegalStateException
	 * @throws ProvisionException 
	 */
	public static IArtifactRepository createExtensionLocationArtifactRepository(URI location, String name, Map<String, String> properties) throws ProvisionException {
		IProvisioningAgent agent = getAgent();
		IArtifactRepositoryManager manager = (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
		if (manager == null)
			throw new IllegalStateException("ArtifactRepositoryManager not registered."); //$NON-NLS-1$
		ExtensionLocationArtifactRepositoryFactory factory = new ExtensionLocationArtifactRepositoryFactory();
		factory.setAgent(agent);
		IArtifactRepository repository = factory.create(location, name, ExtensionLocationArtifactRepository.TYPE, properties);
		//we need to add the concrete repository to the repository manager, or its properties will not be correct
		((ArtifactRepositoryManager) manager).addRepository(repository);
		manager.setRepositoryProperty(location, IRepository.PROP_SYSTEM, String.valueOf(true));
		return repository;
	}

	/**
	 * Helper method to load an extension location metadata repository from the given URL.
	 * 
	 * @throws IllegalStateException
	 * @throws ProvisionException
	 */
	public static IArtifactRepository loadArtifactRepository(URI location, IProgressMonitor monitor) throws ProvisionException {
		IArtifactRepositoryManager manager = (IArtifactRepositoryManager) getAgent().getService(IArtifactRepositoryManager.SERVICE_NAME);
		if (manager == null)
			throw new IllegalStateException("ArtifactRepositoryManager not registered."); //$NON-NLS-1$
		IArtifactRepository repository = manager.loadRepository(location, monitor);
		manager.setRepositoryProperty(location, IRepository.PROP_SYSTEM, String.valueOf(true));
		return repository;
	}

	/*
	 * Return the set of metadata repositories known to this bundle. It is constructed from the repos
	 * for the drop-ins as well as the ones in the configuration.
	 */
	public static Set<IMetadataRepository> getRepositories() {
		return repositories;
	}

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		bundleContext = context;

		// check to see if there is really any work to do. Do this after setting the context, and
		// doing other initialization in case others call our public methods later.
		if (isUpToDate()) {
			// clear the cache
			filesToCheck = null;
			return;
		}

		checkConfigIni();

		// create the watcher for the "drop-ins" folder
		watchDropins();
		// keep an eye on the platform.xml
		watchConfiguration();

		synchronize(null);
		writeTimestamps();

		// we should probably be holding on to these repos by URL
		// see Bug 223422
		// for now explicitly nulling out these repos to allow GC to occur
		repositories.clear();
		filesToCheck = null;
	}

	private void checkConfigIni() {
		File configuration = getConfigurationLocation();
		if (configuration == null) {
			LogHelper.log(new Status(IStatus.ERROR, ID, "Unable to determine configuration location.")); //$NON-NLS-1$
			return;
		}

		File configIni = new File(configuration, CONFIG_INI);
		if (!configIni.exists()) {
			// try parent configuration
			File parentConfiguration = getParentConfigurationLocation();
			if (parentConfiguration == null)
				return;

			// write shared configuration
			Properties props = new Properties();
			try {
				OutputStream os = null;
				try {
					os = new BufferedOutputStream(new FileOutputStream(configIni));
					String externalForm = PathUtil.makeRelative(parentConfiguration.toURL().toExternalForm(), getOSGiInstallArea()).replace('\\', '/');
					props.put("osgi.sharedConfiguration.area", externalForm); //$NON-NLS-1$
					props.store(os, "Linked configuration"); //$NON-NLS-1$
				} finally {
					if (os != null)
						os.close();
				}
			} catch (IOException e) {
				LogHelper.log(new Status(IStatus.ERROR, ID, "Unable to create linked configuration location.", e)); //$NON-NLS-1$
			}
		}
	}

	/*
	 * Return a boolean value indicating whether or not we need to run
	 * the reconciler due to changes in the file-system.
	 */
	private boolean isUpToDate() {
		// the user might want to force a reconciliation
		if ("true".equals(getContext().getProperty("osgi.checkConfiguration"))) //$NON-NLS-1$//$NON-NLS-2$
			return false;
		// read timestamps
		Properties timestamps = readTimestamps();
		if (timestamps.isEmpty())
			return false;

		// gather the list of files/folders that we need to check
		Collection<File> files = getFilesToCheck();
		for (Iterator<File> iter = files.iterator(); iter.hasNext();) {
			File file = iter.next();
			String key = file.getAbsolutePath();
			String timestamp = timestamps.getProperty(key);
			if (timestamp == null)
				return false;
			if (!Long.toString(file.lastModified()).equals(timestamp))
				return false;
			timestamps.remove(key);
		}

		// if we had some extra timestamps in the file, then signal that something has
		// changed and we need to reconcile
		return timestamps.isEmpty();
	}

	/*
	 * Restore the cached timestamp values.
	 */
	private Properties readTimestamps() {
		Properties result = new Properties();
		File file = Activator.getContext().getDataFile(CACHE_FILENAME);
		if (!file.exists())
			return result;
		InputStream input = null;
		try {
			input = new BufferedInputStream(new FileInputStream(file));
			result.load(input);
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "Error occurred while reading cached timestamps for reconciliation.", e)); //$NON-NLS-1$
		} finally {
			try {
				if (input != null)
					input.close();
			} catch (IOException e) {
				// ignore
			}
		}
		return result;
	}

	/*
	 * Return a collection of files which are interesting to us when we want to record timestamps
	 * to figure out if something has changed and perhaps avoid an unnecessary reconcilation.
	 */
	private Collection<File> getFilesToCheck() {
		if (filesToCheck != null)
			return filesToCheck;

		Set<File> result = new HashSet<File>();

		// configuration/org.eclipse.update/platform.xml, configuration/../plugins, configuration/../features
		File configuration = getConfigurationLocation();
		if (configuration != null) {
			result.add(new File(configuration, PLATFORM_CFG));
			File parent = configuration.getParentFile();
			if (parent != null) {
				File plugins = new File(parent, "plugins"); //$NON-NLS-1$
				result.add(plugins);
				File features = new File(parent, "features"); //$NON-NLS-1$
				result.add(features);
			}
		}

		// if we are in shared mode then record the same files for the parent configuration
		File parentConfiguration = getParentConfigurationLocation();
		if (parentConfiguration != null) {
			result.add(new File(parentConfiguration, PLATFORM_CFG));
			File parent = parentConfiguration.getParentFile();
			if (parent != null) {
				File plugins = new File(parent, "plugins"); //$NON-NLS-1$
				result.add(plugins);
				File features = new File(parent, "features"); //$NON-NLS-1$
				result.add(features);
			}
		}

		// dropins folders
		File[] dropins = getDropinsDirectories();
		result.addAll(getDropinsToCheck(dropins));

		// links folders
		File[] links = getLinksDirectories();
		result.addAll(getDropinsToCheck(links));

		filesToCheck = result;
		return filesToCheck;
	}

	/*
	 * Iterate over the given collection of files (could be dropins or links folders) and 
	 * return a collection of files that might be interesting to check the timestamps of.
	 */
	private Collection<File> getDropinsToCheck(File[] files) {
		Collection<File> result = new HashSet<File>();
		for (int outer = 0; outer < files.length; outer++) {
			// add top-level file/folder
			result.add(files[outer]);

			File[] children = files[outer].listFiles();
			for (int inner = 0; children != null && inner < children.length; inner++) {
				File child = children[inner];
				if (child.isFile() && child.getName().toLowerCase().endsWith(EXT_LINK)) {
					// if we have a link file then add the link file and its target
					File target = DropinsRepositoryListener.getLinkedFile(child);
					if (target == null || !target.exists())
						continue;
					result.add(child);
					result.add(target);
					File eclipse = new File(target, DIR_ECLIPSE);
					result.add(eclipse);
					result.add(new File(eclipse, DIR_PLUGINS));
					result.add(new File(eclipse, DIR_FEATURES));

				} else if (child.getName().equalsIgnoreCase(DIR_ECLIPSE)) {
					// if it is an "eclipse" dir then add it as well as "plugins" and "features"
					result.add(child);
					result.add(new File(child, DIR_PLUGINS));
					result.add(new File(child, DIR_FEATURES));

				} else if (child.isDirectory()) {
					// look for "dropins/foo/plugins" (and "features") and
					// "dropins/foo/eclipse/plugins" (and "features")
					// Note: we could have a directory-based bundle here but we 
					// will still add it since it won't hurt anything (one extra timestamp check)
					result.add(child);
					File parent;
					File eclipse = new File(child, DIR_ECLIPSE);
					if (eclipse.exists()) {
						result.add(eclipse);
						parent = eclipse;
					} else {
						parent = child;
					}
					File plugins = new File(parent, DIR_PLUGINS);
					if (plugins.exists())
						result.add(plugins);
					File features = new File(parent, DIR_FEATURES);
					if (features.exists())
						result.add(features);
				}
			}
		}
		return result;
	}

	/*
	 * Persist the cache timestamp values.
	 */
	private void writeTimestamps() {
		Properties timestamps = new Properties();
		Collection<File> files = getFilesToCheck();
		for (Iterator<File> iter = files.iterator(); iter.hasNext();) {
			File file = iter.next();
			timestamps.put(file.getAbsolutePath(), Long.toString(file.lastModified()));
		}

		// write out the file
		File file = Activator.getContext().getDataFile(CACHE_FILENAME);
		OutputStream output = null;
		try {
			file.delete();
			output = new BufferedOutputStream(new FileOutputStream(file));
			timestamps.store(output, null);
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, ID, "Error occurred while writing cache timestamps for reconciliation.", e)); //$NON-NLS-1$
		} finally {
			if (output != null)
				try {
					output.close();
				} catch (IOException e) {
					// ignore
				}
		}
	}

	/*
	 * Synchronize the profile.
	 */
	public static synchronized void synchronize(IProgressMonitor monitor) {
		IProfile profile = getCurrentProfile(bundleContext);
		if (profile == null)
			return;
		// create the profile synchronizer on all available repositories
		ProfileSynchronizer synchronizer = new ProfileSynchronizer(getAgent(), profile, repositories);
		IStatus result = synchronizer.synchronize(monitor);
		if (ProfileSynchronizer.isReconciliationApplicationRunning()) {
			System.getProperties().put(PROP_APPLICATION_STATUS, result);
		}
		if (!result.isOK() && !(result.getSeverity() == IStatus.CANCEL))
			LogHelper.log(result);
	}

	/*
	 * Watch the platform.xml file.
	 */
	private void watchConfiguration() {
		File configFile = getConfigurationLocation();
		if (configFile == null) {
			LogHelper.log(new Status(IStatus.ERROR, ID, "Unable to determine configuration location.")); //$NON-NLS-1$
			return;
		}

		configFile = new File(configFile, PLATFORM_CFG);
		if (!configFile.exists()) {
			// try parent configuration
			File parentConfiguration = getParentConfigurationLocation();
			if (parentConfiguration == null)
				return;

			File shareConfigFile = new File(parentConfiguration, PLATFORM_CFG);
			if (!shareConfigFile.exists())
				return;

			Configuration config = new Configuration();
			config.setDate(Long.toString(new Date().getTime()));
			config.setVersion("3.0"); //$NON-NLS-1$
			try {
				String sharedUR = PathUtil.makeRelative(shareConfigFile.toURL().toExternalForm(), getOSGiInstallArea()).replace('\\', '/');
				config.setSharedUR(sharedUR);
				// ensure that org.eclipse.update directory that holds platform.xml is pre-created.
				configFile.getParentFile().mkdirs();
				config.save(configFile, getOSGiInstallArea());
			} catch (IOException e) {
				LogHelper.log(new Status(IStatus.ERROR, ID, "Unable to create linked platform.xml.", e)); //$NON-NLS-1$
				return;
			} catch (ProvisionException e) {
				LogHelper.log(new Status(IStatus.ERROR, ID, "Unable to create linked platform.xml.", e)); //$NON-NLS-1$
				return;
			}

		}
		DirectoryWatcher watcher = new DirectoryWatcher(configFile.getParentFile());
		PlatformXmlListener listener = new PlatformXmlListener(configFile);
		watcher.addListener(listener);
		watcher.poll();
		repositories.addAll(listener.getMetadataRepositories());
	}

	/*
	 * Create a new directory watcher with a repository listener on the drop-ins folder. 
	 */
	private void watchDropins() {
		List<File> directories = new ArrayList<File>();
		File[] dropinsDirectories = getDropinsDirectories();
		directories.addAll(Arrays.asList(dropinsDirectories));
		File[] linksDirectories = getLinksDirectories();
		directories.addAll(Arrays.asList(linksDirectories));
		if (directories.isEmpty())
			return;

		DropinsRepositoryListener listener = new DropinsRepositoryListener(getAgent(), DROPINS);
		DirectoryWatcher watcher = new DirectoryWatcher(directories.toArray(new File[directories.size()]));
		watcher.addListener(listener);
		watcher.poll();
		repositories.addAll(listener.getMetadataRepositories());
	}

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		bundleContext = null;
	}

	/*
	 * Return the bundle context for this bundle.
	 */
	public static BundleContext getContext() {
		return bundleContext;
	}

	/*
	 * Helper method to get the configuration location. Return null if
	 * it is unavailable.
	 */
	public static File getConfigurationLocation() {
		Location configurationLocation = (Location) ServiceHelper.getService(getContext(), Location.class.getName(), Location.CONFIGURATION_FILTER);
		if (configurationLocation == null || !configurationLocation.isSet())
			return null;
		URL url = configurationLocation.getURL();
		if (url == null)
			return null;
		return URLUtil.toFile(url);
	}

	/*
	 * Helper method to get the shared configuration location. Return null if
	 * it is unavailable.
	 */
	public static File getParentConfigurationLocation() {
		Location configurationLocation = (Location) ServiceHelper.getService(getContext(), Location.class.getName(), Location.CONFIGURATION_FILTER);
		if (configurationLocation == null || !configurationLocation.isSet())
			return null;

		Location sharedConfigurationLocation = configurationLocation.getParentLocation();
		if (sharedConfigurationLocation == null)
			return null;

		URL url = sharedConfigurationLocation.getURL();
		if (url == null)
			return null;
		return URLUtil.toFile(url);
	}

	/*
	 * Do a look-up and return the OSGi install area if it is set.
	 */
	public static URL getOSGiInstallArea() {
		Location location = (Location) ServiceHelper.getService(Activator.getContext(), Location.class.getName(), Location.INSTALL_FILTER);
		if (location == null)
			return null;
		if (!location.isSet())
			return null;
		return location.getURL();
	}

	/*
	 * Helper method to return the eclipse.home location. Return
	 * null if it is unavailable.
	 */
	public static File getEclipseHome() {
		Location eclipseHome = (Location) ServiceHelper.getService(getContext(), Location.class.getName(), Location.ECLIPSE_HOME_FILTER);
		if (eclipseHome == null || !eclipseHome.isSet())
			return null;
		URL url = eclipseHome.getURL();
		if (url == null)
			return null;
		return URLUtil.toFile(url);
	}

	/*
	 * Return the locations of the links directories. There is a potential for
	 * more than one to be returned here if we are running in shared mode.
	 */
	private static File[] getLinksDirectories() {
		List<File> linksDirectories = new ArrayList<File>();
		File root = getEclipseHome();
		if (root != null)
			linksDirectories.add(new File(root, LINKS));

		// check to see if we are in shared mode. if so, then add the user's local
		// links directory. (the shared one will have been added above with the
		// reference to Eclipse home)
		if (getParentConfigurationLocation() != null) {
			File configuration = getConfigurationLocation();
			if (configuration != null && configuration.getParentFile() != null)
				linksDirectories.add(new File(configuration.getParentFile(), LINKS));
		}
		return linksDirectories.toArray(new File[linksDirectories.size()]);
	}

	/*
	 * Return the location of the dropins directories. These include the one specified by
	 * the "org.eclipse.equinox.p2.reconciler.dropins.directory" System property and the one
	 * in the Eclipse home directory. If we are in shared mode, then also add the user's
	 * local dropins directory.
	 */
	private static File[] getDropinsDirectories() {
		List<File> dropinsDirectories = new ArrayList<File>();
		// did the user specify one via System properties?
		String watchedDirectoryProperty = bundleContext.getProperty(DROPINS_DIRECTORY);
		if (watchedDirectoryProperty != null)
			dropinsDirectories.add(new File(watchedDirectoryProperty));

		// always add the one in the Eclipse home directory
		File root = getEclipseHome();
		if (root != null)
			dropinsDirectories.add(new File(root, DROPINS));

		// check to see if we are in shared mode. if so, then add the user's local
		// dropins directory. (the shared one will have been added above with the
		// reference to Eclipse home)
		if (getParentConfigurationLocation() != null) {
			File configuration = getConfigurationLocation();
			if (configuration != null && configuration.getParentFile() != null)
				dropinsDirectories.add(new File(configuration.getParentFile(), DROPINS));
		}
		return dropinsDirectories.toArray(new File[dropinsDirectories.size()]);
	}

	/*
	 * Return the current profile or null if it cannot be retrieved.
	 */
	public static IProfile getCurrentProfile(BundleContext context) {
		IProvisioningAgent agent = getAgent();
		IProfileRegistry profileRegistry = (IProfileRegistry) agent.getService(IProfileRegistry.SERVICE_NAME);
		if (profileRegistry == null)
			return null;
		return profileRegistry.getProfile(IProfileRegistry.SELF);
	}
}
