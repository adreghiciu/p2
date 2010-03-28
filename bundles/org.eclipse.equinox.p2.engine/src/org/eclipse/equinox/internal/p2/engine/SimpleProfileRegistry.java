/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.internal.p2.engine;

import java.io.*;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.internal.p2.core.helpers.*;
import org.eclipse.equinox.internal.p2.metadata.TranslationSupport;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus;
import org.eclipse.equinox.p2.core.*;
import org.eclipse.equinox.p2.core.spi.IAgentService;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class SimpleProfileRegistry implements IProfileRegistry, IAgentService {

	private static final String PROFILE_REGISTRY = "profile registry"; //$NON-NLS-1$

	private static final String PROFILE_EXT = ".profile"; //$NON-NLS-1$
	private static final String PROFILE_GZ_EXT = ".profile.gz"; //$NON-NLS-1$
	public static final String DEFAULT_STORAGE_DIR = "profileRegistry"; //$NON-NLS-1$
	private static final String DATA_EXT = ".data"; //$NON-NLS-1$

	protected final IProvisioningAgent agent;

	/**
	 * Reference to Map of String(Profile id)->Profile. 
	 */
	private SoftReference<Map<String, Profile>> profiles;
	private Map<String, ProfileLock> profileLocks = new HashMap<String, ProfileLock>();

	private String self;

	//Whether the registry should update the self profile when the registry is restored
	private boolean updateSelfProfile;

	private File store;

	ISurrogateProfileHandler surrogateProfileHandler;

	private IProvisioningEventBus eventBus;

	public SimpleProfileRegistry(IProvisioningAgent agent, File registryDirectory) {
		this(agent, registryDirectory, new SurrogateProfileHandler(agent), true);
	}

	public SimpleProfileRegistry(IProvisioningAgent agent, File registryDirectory, ISurrogateProfileHandler handler, boolean updateSelfProfile) {
		this.agent = agent;
		store = registryDirectory;
		surrogateProfileHandler = handler;
		Assert.isNotNull(store, "Profile registry requires a directory"); //$NON-NLS-1$
		findSelf();
		this.updateSelfProfile = updateSelfProfile;
	}

	/**
	 * Determine the id of the "self" profile. This is only applicable for the registry
	 * of the currently running system.
	 */
	private void findSelf() {
		//the location for the currently running system is registered as a service
		ServiceReference ref = EngineActivator.getContext().getServiceReference(IAgentLocation.SERVICE_NAME);
		if (ref == null)
			return;
		IAgentLocation location = (IAgentLocation) EngineActivator.getContext().getService(ref);
		if (location == null)
			return;
		if (store.equals(getDefaultRegistryDirectory(location))) {
			//we are the registry for the currently running system
			self = EngineActivator.getContext().getProperty("eclipse.p2.profile"); //$NON-NLS-1$
		}
		EngineActivator.getContext().ungetService(ref);
	}

	public static File getDefaultRegistryDirectory(IAgentLocation agent) {
		File registryDirectory = null;
		if (agent == null)
			throw new IllegalStateException("Profile Registry inialization failed: Agent Location is not available"); //$NON-NLS-1$
		final URI engineDataArea = agent.getDataArea(EngineActivator.ID);
		URI registryURL = URIUtil.append(engineDataArea, DEFAULT_STORAGE_DIR);
		registryDirectory = new File(registryURL);
		registryDirectory.mkdirs();
		return registryDirectory;
	}

	/**
	 * If the current profile for self is marked as a roaming profile, we need
	 * to update its install and bundle pool locations.
	 */
	private void updateSelfProfile(Map<String, Profile> profileMap) {
		if (profileMap == null)
			return;
		Profile selfProfile = profileMap.get(self);
		if (selfProfile == null)
			return;

		//register default locale provider where metadata translations are found
		//TODO ideally this should not be hard-coded to the current profile
		TranslationSupport.getInstance().setTranslationSource(selfProfile);

		if (DebugHelper.DEBUG_PROFILE_REGISTRY)
			DebugHelper.debug(PROFILE_REGISTRY, "SimpleProfileRegistry.updateSelfProfile"); //$NON-NLS-1$
		boolean changed = false;
		//only update if self is a roaming profile
		if (Boolean.valueOf(selfProfile.getProperty(IProfile.PROP_ROAMING)).booleanValue())
			changed = updateRoamingProfile(selfProfile);

		if (surrogateProfileHandler != null && surrogateProfileHandler.isSurrogate(selfProfile))
			changed = changed || surrogateProfileHandler.updateProfile(selfProfile);

		if (changed)
			saveProfile(selfProfile);
	}

	private boolean updateRoamingProfile(Profile selfProfile) {
		if (DebugHelper.DEBUG_PROFILE_REGISTRY)
			DebugHelper.debug(PROFILE_REGISTRY, "SimpleProfileRegistry.updateRoamingProfile"); //$NON-NLS-1$
		Location installLocation = (Location) ServiceHelper.getService(EngineActivator.getContext(), Location.class.getName(), Location.INSTALL_FILTER);
		File location = new File(installLocation.getURL().getPath());
		boolean changed = false;
		if (!location.equals(new File(selfProfile.getProperty(IProfile.PROP_INSTALL_FOLDER)))) {
			selfProfile.setProperty(IProfile.PROP_INSTALL_FOLDER, location.getAbsolutePath());
			changed = true;
		}
		String propCache = selfProfile.getProperty(IProfile.PROP_CACHE);
		if (propCache != null && !location.equals(new File(propCache))) {
			selfProfile.setProperty(IProfile.PROP_CACHE, location.getAbsolutePath());
			changed = true;
		}
		if (DebugHelper.DEBUG_PROFILE_REGISTRY)
			DebugHelper.debug(PROFILE_REGISTRY, "SimpleProfileRegistry.updateRoamingProfile(changed=" + changed + ')'); //$NON-NLS-1$
		return changed;
	}

	public synchronized String toString() {
		return getProfileMap().toString();
	}

	public synchronized IProfile getProfile(String id) {
		Profile profile = internalGetProfile(id);
		if (profile == null)
			return null;
		return profile.snapshot();
	}

	public synchronized IProfile getProfile(String id, long timestamp) {
		if (SELF.equals(id))
			id = self;

		if (profiles != null) {
			IProfile profile = getProfile(id);
			if (profile != null && profile.getTimestamp() == timestamp)
				return profile;
		}

		File profileDirectory = new File(store, escape(id) + PROFILE_EXT);
		if (!profileDirectory.isDirectory())
			return null;

		File profileFile = new File(profileDirectory, Long.toString(timestamp) + PROFILE_GZ_EXT);
		if (!profileFile.exists()) {
			profileFile = new File(profileDirectory, Long.toString(timestamp) + PROFILE_EXT);
			if (!profileFile.exists())
				return null;
		}

		Parser parser = new Parser(EngineActivator.getContext(), EngineActivator.ID);
		try {
			parser.parse(profileFile);
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, EngineActivator.ID, NLS.bind(Messages.error_parsing_profile, profileFile), e));
		}
		return parser.getProfileMap().get(id);
	}

	public synchronized long[] listProfileTimestamps(String id) {
		if (SELF.equals(id))
			id = self;
		//guard against null self profile
		if (id == null)
			return new long[0];

		File profileDirectory = new File(store, escape(id) + PROFILE_EXT);
		if (!profileDirectory.isDirectory())
			return new long[0];

		File[] profileFiles = profileDirectory.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return (pathname.getName().endsWith(PROFILE_EXT) || pathname.getName().endsWith(PROFILE_GZ_EXT)) && pathname.isFile();
			}
		});

		long[] timestamps = new long[profileFiles.length];
		for (int i = 0; i < profileFiles.length; i++) {
			String filename = profileFiles[i].getName();
			int extensionIndex = filename.lastIndexOf(PROFILE_EXT);
			try {
				timestamps[i] = Long.parseLong(filename.substring(0, extensionIndex));
			} catch (NumberFormatException e) {
				throw new IllegalStateException("Incompatible profile file name. Expected format is {timestamp}" + PROFILE_GZ_EXT + " (or {timestamp}" + PROFILE_EXT + ") but was " + filename + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
		}
		Arrays.sort(timestamps);
		return timestamps;
	}

	private Profile internalGetProfile(String id) {
		if (SELF.equals(id))
			id = self;
		Profile profile = getProfileMap().get(id);
		if (profile == null && self != null && self.equals(id))
			profile = createSurrogateProfile(id);

		return profile;
	}

	private Profile createSurrogateProfile(String id) {
		if (surrogateProfileHandler == null)
			return null;

		Profile profile = (Profile) surrogateProfileHandler.createProfile(id);
		if (profile == null)
			return null;

		saveProfile(profile);
		resetProfiles();
		return getProfileMap().get(id);
	}

	public synchronized IProfile[] getProfiles() {
		Map<String, Profile> profileMap = getProfileMap();
		Profile[] result = new Profile[profileMap.size()];
		int i = 0;
		for (Profile profile : profileMap.values()) {
			result[i++] = profile.snapshot();
		}
		return result;
	}

	/**
	 * Returns an initialized map of String(Profile id)->Profile. 
	 */
	protected Map<String, Profile> getProfileMap() {
		if (profiles != null) {
			Map<String, Profile> result = profiles.get();
			if (result != null)
				return result;
		}
		Map<String, Profile> result = restore();
		if (result == null)
			result = new LinkedHashMap<String, Profile>(8);
		profiles = new SoftReference<Map<String, Profile>>(result);
		if (updateSelfProfile) {
			//update self profile on first load
			updateSelfProfile(result);
		}
		return result;
	}

	public synchronized void updateProfile(Profile profile) {
		String id = profile.getProfileId();
		Profile current = internalGetProfile(id);
		if (current == null)
			throw new IllegalArgumentException(NLS.bind(Messages.profile_does_not_exist, id));

		ProfileLock lock = profileLocks.get(id);
		lock.checkLocked();

		current.clearLocalProperties();
		current.clearInstallableUnits();

		current.addProperties(profile.getLocalProperties());
		IQueryResult<IInstallableUnit> queryResult = profile.query(QueryUtil.createIUAnyQuery(), null);
		for (Iterator<IInstallableUnit> queryResultIt = queryResult.iterator(); queryResultIt.hasNext();) {
			IInstallableUnit iu = queryResultIt.next();
			current.addInstallableUnit(iu);
			Map<String, String> iuProperties = profile.getInstallableUnitProperties(iu);
			if (iuProperties != null)
				current.addInstallableUnitProperties(iu, iuProperties);
		}
		saveProfile(current);
		profile.clearOrphanedInstallableUnitProperties();
		profile.setTimestamp(current.getTimestamp());
		broadcastChangeEvent(id, IProfileEvent.CHANGED);
	}

	public IProfile addProfile(String id) throws ProvisionException {
		return addProfile(id, null, null);
	}

	public IProfile addProfile(String id, Map<String, String> profileProperties) throws ProvisionException {
		return addProfile(id, profileProperties, null);
	}

	public synchronized IProfile addProfile(String id, Map<String, String> profileProperties, String parentId) throws ProvisionException {
		if (SELF.equals(id))
			id = self;
		Map<String, Profile> profileMap = getProfileMap();
		if (profileMap.get(id) != null)
			throw new ProvisionException(NLS.bind(Messages.Profile_Duplicate_Root_Profile_Id, id));

		Profile parent = null;
		if (parentId != null) {
			if (SELF.equals(parentId))
				parentId = self;
			parent = profileMap.get(parentId);
			if (parent == null)
				throw new ProvisionException(NLS.bind(Messages.Profile_Parent_Not_Found, parentId));
		}

		Profile profile = new Profile(agent, id, parent, profileProperties);
		if (surrogateProfileHandler != null && surrogateProfileHandler.isSurrogate(profile))
			profile.setSurrogateProfileHandler(surrogateProfileHandler);
		profileMap.put(id, profile);
		saveProfile(profile);
		broadcastChangeEvent(id, IProfileEvent.ADDED);
		return profile.snapshot();
	}

	public synchronized void removeProfile(String profileId) {
		if (SELF.equals(profileId))
			profileId = self;
		//note we need to maintain a reference to the profile map until it is persisted to prevent gc
		Map<String, Profile> profileMap = getProfileMap();
		Profile profile = profileMap.get(profileId);
		if (profile == null)
			return;

		List<String> subProfileIds = profile.getSubProfileIds();
		for (int i = 0; i < subProfileIds.size(); i++) {
			removeProfile(subProfileIds.get(i));
		}
		internalLockProfile(profile);
		// The above call recursively locked the parent(s). So save it away to rewind the locking process.
		IProfile savedParent = profile.getParentProfile();
		try {
			profile.setParent(null);
		} finally {
			internalUnlockProfile(profile);
			// The above call will not recurse since parent is now null. So do it explicitly.
			if (savedParent != null) {
				internalUnlockProfile(savedParent);
			}
		}
		profileMap.remove(profileId);
		profileLocks.remove(profileId);
		deleteProfile(profileId);
		broadcastChangeEvent(profileId, IProfileEvent.REMOVED);
	}

	public synchronized void removeProfile(String id, long timestamp) throws ProvisionException {
		if (SELF.equals(id))
			id = self;

		if (profiles != null) {
			IProfile profile = getProfile(id);
			if (profile != null && profile.getTimestamp() == timestamp)
				throw new ProvisionException(Messages.SimpleProfileRegistry_CannotRemoveCurrentSnapshot);
		}

		File profileDirectory = new File(store, escape(id) + PROFILE_EXT);
		if (!profileDirectory.isDirectory())
			return;

		File profileFile = new File(profileDirectory, Long.toString(timestamp) + PROFILE_GZ_EXT);
		if (!profileFile.exists()) {
			profileFile = new File(profileDirectory, Long.toString(timestamp) + PROFILE_EXT);
			if (!profileFile.exists())
				return;
		}
		FileUtils.deleteAll(profileFile);
	}

	private void broadcastChangeEvent(String profileId, int reason) {
		if (eventBus != null)
			eventBus.publishEvent(new ProfileEvent(profileId, reason));
	}

	/**
	 * Restores the profile registry from disk, and returns the loaded profile map.
	 * Returns <code>null</code> if unable to read the registry.
	 */
	private Map<String, Profile> restore() {
		if (store == null || !store.isDirectory())
			throw new IllegalStateException(NLS.bind(Messages.reg_dir_not_available, store));

		Parser parser = new Parser(EngineActivator.getContext(), EngineActivator.ID);
		File[] profileDirectories = store.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(PROFILE_EXT) && pathname.isDirectory();
			}
		});
		for (int i = 0; i < profileDirectories.length; i++) {
			String directoryName = profileDirectories[i].getName();
			String profileId = unescape(directoryName.substring(0, directoryName.lastIndexOf(PROFILE_EXT)));
			ProfileLock lock = profileLocks.get(profileId);
			if (lock == null) {
				lock = new ProfileLock(this, profileDirectories[i]);
				profileLocks.put(profileId, lock);
			}

			boolean locked = false;
			if (lock.processHoldsLock() || (locked = lock.lock())) {
				try {
					File profileFile = findLatestProfileFile(profileDirectories[i]);
					if (profileFile != null) {
						try {
							parser.parse(profileFile);
						} catch (IOException e) {
							LogHelper.log(new Status(IStatus.ERROR, EngineActivator.ID, NLS.bind(Messages.error_parsing_profile, profileFile), e));
						}
					}
				} finally {
					if (locked)
						lock.unlock();
				}
			} else {
				// could not lock the profile, so add a place holder
				parser.addProfilePlaceHolder(profileId);
			}
		}
		return parser.getProfileMap();
	}

	private File findLatestProfileFile(File profileDirectory) {
		File latest = null;
		long latestTimestamp = 0;
		File[] profileFiles = profileDirectory.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return (pathname.getName().endsWith(PROFILE_GZ_EXT) || pathname.getName().endsWith(PROFILE_EXT)) && !pathname.isDirectory();
			}
		});
		for (int i = 0; i < profileFiles.length; i++) {
			File profileFile = profileFiles[i];
			String fileName = profileFile.getName();
			try {
				long timestamp = Long.parseLong(fileName.substring(0, fileName.indexOf(PROFILE_EXT)));
				if (timestamp > latestTimestamp) {
					latestTimestamp = timestamp;
					latest = profileFile;
				}
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return latest;
	}

	private void saveProfile(Profile profile) {
		File profileDirectory = new File(store, escape(profile.getProfileId()) + PROFILE_EXT);
		profileDirectory.mkdir();

		long previousTimestamp = profile.getTimestamp();
		long currentTimestamp = System.currentTimeMillis();
		if (currentTimestamp <= previousTimestamp)
			currentTimestamp = previousTimestamp + 1;
		boolean shouldGzipFile = shouldGzipFile(profile);
		File profileFile = new File(profileDirectory, Long.toString(currentTimestamp) + (shouldGzipFile ? PROFILE_GZ_EXT : PROFILE_EXT));

		// Log a stack trace to see who is writing the profile.
		if (DebugHelper.DEBUG_PROFILE_REGISTRY)
			DebugHelper.debug(PROFILE_REGISTRY, "Saving profile to: " + profileFile.getAbsolutePath()); //$NON-NLS-1$

		profile.setTimestamp(currentTimestamp);
		profile.setChanged(false);
		OutputStream os = null;
		try {
			if (shouldGzipFile)
				os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(profileFile)));
			else
				os = new BufferedOutputStream(new FileOutputStream(profileFile));
			Writer writer = new Writer(os);
			writer.writeProfile(profile);
		} catch (IOException e) {
			profile.setTimestamp(previousTimestamp);
			profileFile.delete();
			LogHelper.log(new Status(IStatus.ERROR, EngineActivator.ID, NLS.bind(Messages.error_persisting_profile, profile.getProfileId()), e));
		} finally {
			try {
				if (os != null)
					os.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	public void setEventBus(IProvisioningEventBus bus) {
		this.eventBus = bus;
	}

	/**
	 * Returns whether the profile file for the given profile should be written in gzip format.
	 */
	private boolean shouldGzipFile(Profile profile) {
		//check system property controlling compression
		String format = EngineActivator.getContext().getProperty(EngineActivator.PROP_PROFILE_FORMAT);
		if (format != null && format.equals(EngineActivator.PROFILE_FORMAT_UNCOMPRESSED))
			return false;

		//check whether the profile contains the p2 engine from 3.5.0 or earlier
		return profile.available(QueryUtil.createIUQuery("org.eclipse.equinox.p2.engine", new VersionRange("[0.0.0, 1.0.101)")), null).isEmpty(); //$NON-NLS-1$//$NON-NLS-2$
	}

	private void deleteProfile(String profileId) {
		File profileDirectory = new File(store, escape(profileId) + PROFILE_EXT);
		FileUtils.deleteAll(profileDirectory);
	}

	/**
	 * Converts a profile id into a string that can be used as a file name in any file system.
	 */
	public static String escape(String toEscape) {
		StringBuffer buffer = new StringBuffer();
		int length = toEscape.length();
		for (int i = 0; i < length; ++i) {
			char ch = toEscape.charAt(i);
			switch (ch) {
				case '\\' :
				case '/' :
				case ':' :
				case '*' :
				case '?' :
				case '"' :
				case '<' :
				case '>' :
				case '|' :
				case '%' :
					buffer.append("%" + (int) ch + ";"); //$NON-NLS-1$ //$NON-NLS-2$
					break;
				default :
					buffer.append(ch);
			}
		}
		return buffer.toString();
	}

	public static String unescape(String text) {
		if (text.indexOf('%') == -1)
			return text;

		StringBuffer buffer = new StringBuffer();
		int length = text.length();
		for (int i = 0; i < length; ++i) {
			char ch = text.charAt(i);
			if (ch == '%') {
				int colon = text.indexOf(';', i);
				if (colon == -1)
					throw new IllegalStateException("error unescaping the sequence at character (" + i + ") for " + text + ". Expected %{int};."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				ch = (char) Integer.parseInt(text.substring(i + 1, colon));
				i = colon;
			}
			buffer.append(ch);
		}
		return buffer.toString();
	}

	static class Writer extends ProfileWriter {

		public Writer(OutputStream output) throws IOException {
			super(output, new ProcessingInstruction[] {ProcessingInstruction.makeTargetVersionInstruction(PROFILE_TARGET, ProfileXMLConstants.CURRENT_VERSION)});
		}
	}

	/*
	 * 	Parser for the contents of a SimpleProfileRegistry,
	 * 	as written by the Writer class.
	 */
	class Parser extends ProfileParser {
		private final Map<String, ProfileHandler> profileHandlers = new HashMap<String, ProfileHandler>();

		public Parser(BundleContext context, String bundleId) {
			super(context, bundleId);
		}

		public void addProfilePlaceHolder(String profileId) {
			profileHandlers.put(profileId, new ProfileHandler(profileId));
		}

		public void parse(File file) throws IOException {
			InputStream is;
			if (file.getName().endsWith(PROFILE_GZ_EXT)) {
				is = new BufferedInputStream(new GZIPInputStream(new FileInputStream(file)));
			} else { // backward compatibility. SimpleProfileRegistry doesn't write non-gzipped profiles any more. 
				is = new BufferedInputStream(new FileInputStream(file));
			}
			parse(is);
		}

		public synchronized void parse(InputStream stream) throws IOException {
			this.status = null;
			try {
				// TODO: currently not caching the parser since we make no assumptions
				//		 or restrictions on concurrent parsing
				getParser();
				ProfileHandler profileHandler = new ProfileHandler();
				xmlReader.setContentHandler(new ProfileDocHandler(PROFILE_ELEMENT, profileHandler));
				xmlReader.parse(new InputSource(stream));
				profileHandlers.put(profileHandler.getProfileId(), profileHandler);
			} catch (SAXException e) {
				throw new IOException(e.getMessage());
			} catch (ParserConfigurationException e) {
				throw new IOException(e.getMessage());
			} finally {
				stream.close();
			}
		}

		protected Object getRootObject() {
			return this;
		}

		public Map<String, Profile> getProfileMap() {
			Map<String, Profile> profileMap = new HashMap<String, Profile>();
			for (String profileId : profileHandlers.keySet()) {
				addProfile(profileId, profileMap);
			}
			return profileMap;
		}

		private void addProfile(String profileId, Map<String, Profile> profileMap) {
			if (profileMap.containsKey(profileId))
				return;

			ProfileHandler profileHandler = profileHandlers.get(profileId);
			Profile parentProfile = null;

			String parentId = profileHandler.getParentId();
			if (parentId != null) {
				addProfile(parentId, profileMap);
				parentProfile = profileMap.get(parentId);
			}

			Profile profile = new Profile(agent, profileId, parentProfile, profileHandler.getProperties());
			if (surrogateProfileHandler != null && surrogateProfileHandler.isSurrogate(profile))
				profile.setSurrogateProfileHandler(surrogateProfileHandler);

			profile.setTimestamp(profileHandler.getTimestamp());

			IInstallableUnit[] ius = profileHandler.getInstallableUnits();
			if (ius != null) {
				for (int i = 0; i < ius.length; i++) {
					IInstallableUnit iu = ius[i];
					profile.addInstallableUnit(iu);
					Map<String, String> iuProperties = profileHandler.getIUProperties(iu);
					if (iuProperties != null) {
						for (Entry<String, String> entry : iuProperties.entrySet()) {
							profile.setInstallableUnitProperty(iu, entry.getKey(), entry.getValue());
						}
					}
				}
			}
			profile.setChanged(false);
			profileMap.put(profileId, profile);
		}

		private final class ProfileDocHandler extends DocHandler {

			public ProfileDocHandler(String rootName, RootHandler rootHandler) {
				super(rootName, rootHandler);
			}

			public void processingInstruction(String target, String data) throws SAXException {
				if (ProfileXMLConstants.PROFILE_TARGET.equals(target)) {
					Version repositoryVersion = extractPIVersion(target, data);
					if (!ProfileXMLConstants.XML_TOLERANCE.isIncluded(repositoryVersion)) {
						throw new SAXException(NLS.bind(Messages.SimpleProfileRegistry_Parser_Has_Incompatible_Version, repositoryVersion, ProfileXMLConstants.XML_TOLERANCE));
					}
				}
			}
		}

		protected String getErrorMessage() {
			return Messages.SimpleProfileRegistry_Parser_Error_Parsing_Registry;
		}

		public String toString() {
			// TODO:
			return null;
		}

	}

	public synchronized boolean isCurrent(IProfile profile) {
		Profile internalProfile = internalGetProfile(profile.getProfileId());
		if (internalProfile == null)
			throw new IllegalArgumentException(NLS.bind(Messages.profile_not_registered, profile.getProfileId()));

		if (!internalLockProfile(internalProfile))
			throw new IllegalStateException(Messages.SimpleProfileRegistry_Profile_in_use);

		try {
			return (!((Profile) profile).isChanged() && checkTimestamps(profile, internalProfile));
		} finally {
			internalUnlockProfile(internalProfile);
		}
	}

	public synchronized void lockProfile(Profile profile) {
		Profile internalProfile = internalGetProfile(profile.getProfileId());
		if (internalProfile == null)
			throw new IllegalArgumentException(NLS.bind(Messages.profile_not_registered, profile.getProfileId()));

		if (!internalLockProfile(internalProfile))
			throw new IllegalStateException(Messages.SimpleProfileRegistry_Profile_in_use);

		boolean isCurrent = false;
		try {
			if (profile.isChanged()) {
				if (DebugHelper.DEBUG_PROFILE_REGISTRY)
					DebugHelper.debug(PROFILE_REGISTRY, "Profile is marked as changed."); //$NON-NLS-1$
				throw new IllegalStateException(NLS.bind(Messages.profile_not_current, profile.getProfileId()));
			}
			if (!checkTimestamps(profile, internalProfile)) {
				if (DebugHelper.DEBUG_PROFILE_REGISTRY)
					DebugHelper.debug(PROFILE_REGISTRY, "Unexpected timestamp difference in profile."); //$NON-NLS-1$
				throw new IllegalStateException(NLS.bind(Messages.profile_not_current, profile.getProfileId()));
			}
			isCurrent = true;
		} finally {
			// this check is done here to ensure we unlock even if a runtime exception is thrown
			if (!isCurrent)
				internalUnlockProfile(internalProfile);
		}
	}

	private boolean internalLockProfile(IProfile profile) {
		ProfileLock lock = profileLocks.get(profile.getProfileId());
		if (lock == null) {
			lock = new ProfileLock(this, new File(store, escape(profile.getProfileId()) + PROFILE_EXT));
			profileLocks.put(profile.getProfileId(), lock);
		}
		return lock.lock();
	}

	private boolean checkTimestamps(IProfile profile, IProfile internalProfile) {
		long[] timestamps = listProfileTimestamps(profile.getProfileId());
		if (timestamps.length == 0) {
			if (DebugHelper.DEBUG_PROFILE_REGISTRY)
				DebugHelper.debug(PROFILE_REGISTRY, "check timestamp: expected " + profile.getTimestamp() + " but no profiles were found"); //$NON-NLS-1$ //$NON-NLS-2$
			resetProfiles();
			return false;
		}

		long currentTimestamp = (timestamps.length == 0) ? -1 : timestamps[timestamps.length - 1];
		if (profile.getTimestamp() != currentTimestamp) {
			if (DebugHelper.DEBUG_PROFILE_REGISTRY)
				DebugHelper.debug(PROFILE_REGISTRY, "check timestamp: expected " + profile.getTimestamp() + " but was " + currentTimestamp); //$NON-NLS-1$ //$NON-NLS-2$
			if (internalProfile.getTimestamp() != currentTimestamp)
				resetProfiles();
			return false;
		}

		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.engine.IProfileRegistry#containsProfile(java.lang.String)
	 */
	public synchronized boolean containsProfile(String id) {
		if (SELF.equals(id))
			id = self;
		//null check done after self check, because self can be null
		if (id == null)
			return false;

		// check profiles to avoid restoring the profile registry
		if (profiles != null)
			if (getProfile(id) != null)
				return true;

		File profileDirectory = new File(store, escape(id) + PROFILE_EXT);
		if (!profileDirectory.isDirectory())
			return false;
		File[] profileFiles = profileDirectory.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return (pathname.getName().endsWith(PROFILE_GZ_EXT) || pathname.getName().endsWith(PROFILE_EXT)) && pathname.isFile();
			}
		});
		return profileFiles.length > 0;
	}

	public synchronized void resetProfiles() {
		profiles = null;
	}

	public synchronized void unlockProfile(IProfile profile) {
		Profile internalProfile = internalGetProfile(profile.getProfileId());
		if (internalProfile == null)
			throw new IllegalArgumentException(NLS.bind(Messages.profile_not_registered, profile.getProfileId()));
		internalUnlockProfile(internalProfile);
	}

	private void internalUnlockProfile(IProfile profile) {
		ProfileLock lock = profileLocks.get(profile.getProfileId());
		lock.unlock();
	}

	public Profile validate(IProfile candidate) {
		if (candidate instanceof Profile)
			return (Profile) candidate;

		throw new IllegalArgumentException("Profile incompatible: expected " + Profile.class.getName() + " but was " + ((candidate != null) ? candidate.getClass().getName() : "null") + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	public synchronized File getProfileDataDirectory(String id) {
		if (SELF.equals(id))
			id = self;
		File profileDirectory = new File(store, escape(id) + PROFILE_EXT);
		File profileDataArea = new File(profileDirectory, DATA_EXT);
		if (!profileDataArea.isDirectory() && !profileDataArea.mkdir())
			throw new IllegalStateException("Could not create profile data area " + profileDataArea.getAbsolutePath() + "for: " + id); //$NON-NLS-1$ //$NON-NLS-2$
		return profileDataArea;
	}

	/*(non-Javadoc)
	 * @see org.eclipse.equinox.p2.core.spi.IAgentService#start()
	 */
	public void start() {
		//nothing to do
	}

	/*(non-Javadoc)
	 * @see org.eclipse.equinox.p2.core.spi.IAgentService#stop()
	 */
	public void stop() {
		try {
			//ensure there are no more profile preference save jobs running
			Job.getJobManager().join(ProfilePreferences.PROFILE_SAVE_JOB_FAMILY, null);
		} catch (InterruptedException e) {
			//ignore
		}
	}
}
