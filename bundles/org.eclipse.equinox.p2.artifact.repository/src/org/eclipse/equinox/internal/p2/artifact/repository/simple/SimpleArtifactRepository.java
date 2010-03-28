/*******************************************************************************
 * Copyright (c) 2007, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	IBM Corporation - initial API and implementation
 * 	Genuitec, LLC - support for multi-threaded downloads
 *  Cloudsmith Inc. - query indexes
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.artifact.repository.simple;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.internal.p2.artifact.processors.md5.MD5Verifier;
import org.eclipse.equinox.internal.p2.artifact.repository.*;
import org.eclipse.equinox.internal.p2.artifact.repository.Messages;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.internal.p2.metadata.ArtifactKey;
import org.eclipse.equinox.internal.p2.metadata.expression.CompoundIterator;
import org.eclipse.equinox.internal.p2.metadata.index.IndexProvider;
import org.eclipse.equinox.internal.p2.repository.RepositoryTransport;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.processing.*;
import org.eclipse.equinox.internal.provisional.p2.repository.IStateful;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.index.IIndex;
import org.eclipse.equinox.p2.metadata.index.IIndexProvider;
import org.eclipse.equinox.p2.query.*;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.artifact.*;
import org.eclipse.equinox.p2.repository.artifact.spi.AbstractArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;
import org.eclipse.osgi.util.NLS;

public class SimpleArtifactRepository extends AbstractArtifactRepository implements IFileArtifactRepository, IIndexProvider<IArtifactKey> {
	/** 
	 * A boolean property controlling whether mirroring is enabled.
	 */
	public static final boolean MIRRORS_ENABLED = !"false".equals(Activator.getContext().getProperty("eclipse.p2.mirrors")); //$NON-NLS-1$//$NON-NLS-2$

	public static final boolean MD5_CHECK_ENABLED = !"false".equals(Activator.getContext().getProperty("eclipse.p2.MD5Check")); //$NON-NLS-1$//$NON-NLS-2$

	/** 
	 * The key for a integer property controls the maximum number
	 * of threads that should be used when optimizing downloads from a remote
	 * artifact repository.
	 */
	public static final String PROP_MAX_THREADS = "eclipse.p2.max.threads"; //$NON-NLS-1$

	/**
	 * Allows override of whether threading should be used.
	 */
	public static final String PROP_FORCE_THREADING = "eclipse.p2.force.threading"; //$NON-NLS-1$

	public class ArtifactOutputStream extends OutputStream implements IStateful {
		private boolean closed;
		private long count = 0;
		private IArtifactDescriptor descriptor;
		private OutputStream destination;
		private File file;
		private IStatus status = Status.OK_STATUS;
		private OutputStream firstLink;

		public ArtifactOutputStream(OutputStream os, IArtifactDescriptor descriptor) {
			this(os, descriptor, null);
		}

		public ArtifactOutputStream(OutputStream os, IArtifactDescriptor descriptor, File file) {
			this.destination = os;
			this.descriptor = descriptor;
			this.file = file;
		}

		public void close() throws IOException {
			if (closed)
				return;
			closed = true;

			try {
				destination.close();
			} catch (IOException e) {
				// cleanup if possible
				if (file != null)
					delete(file);
				if (getStatus().isOK())
					throw e;
				// if the stream has already been e.g. canceled, we can return - the status is already set correctly 
				return;
			}
			// if the steps ran ok and there was actual content, write the artifact descriptor
			// TODO the count check is a bit bogus but helps in some error cases (e.g., the optimizer)
			// where errors occurred in a processing step earlier in the chain.  We likely need a better
			// or more explicit way of handling this case.
			OutputStream testStream = firstLink == null ? this : firstLink;
			if (ProcessingStepHandler.checkStatus(testStream).isOK() && count > 0) {
				((ArtifactDescriptor) descriptor).setProperty(IArtifactDescriptor.DOWNLOAD_SIZE, Long.toString(count));
				addDescriptor(descriptor);
			} else if (file != null)
				// cleanup if possible
				delete(file);
		}

		public IStatus getStatus() {
			return status;
		}

		public OutputStream getDestination() {
			return destination;
		}

		public void setStatus(IStatus status) {
			this.status = status == null ? Status.OK_STATUS : status;
		}

		public void write(byte[] b) throws IOException {
			destination.write(b);
			count += b.length;
		}

		public void write(byte[] b, int off, int len) throws IOException {
			destination.write(b, off, len);
			count += len;
		}

		public void write(int b) throws IOException {
			destination.write(b);
			count++;
		}

		public void setFirstLink(OutputStream value) {
			firstLink = value;
		}
	}

	// TODO: optimize
	// we could stream right into the folder
	public static class ZippedFolderOutputStream extends OutputStream {

		private final File folder;
		private final FileOutputStream fos;
		private final File zipFile;

		public ZippedFolderOutputStream(File folder) throws IOException {
			this.folder = folder;
			zipFile = File.createTempFile(folder.getName(), JAR_EXTENSION, null);
			fos = new FileOutputStream(zipFile);
		}

		public void close() throws IOException {
			fos.close();
			try {
				FileUtils.unzipFile(zipFile, folder);
			} finally {
				zipFile.delete();
			}
		}

		public void flush() throws IOException {
			fos.flush();
		}

		public String toString() {
			return fos.toString();
		}

		public void write(byte[] b) throws IOException {
			fos.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			fos.write(b, off, len);
		}

		public void write(int b) throws IOException {
			fos.write(b);
		}
	}

	private static final String ARTIFACT_FOLDER = "artifact.folder"; //$NON-NLS-1$
	private static final String ARTIFACT_UUID = "artifact.uuid"; //$NON-NLS-1$
	static final private String BLOBSTORE = ".blobstore/"; //$NON-NLS-1$
	static final private String CONTENT_FILENAME = "artifacts"; //$NON-NLS-1$
	static final private String[][] PACKED_MAPPING_RULES = { {"(& (classifier=osgi.bundle) (format=packed))", "${repoUrl}/plugins/${id}_${version}.jar.pack.gz"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=osgi.bundle))", "${repoUrl}/plugins/${id}_${version}.jar"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=binary))", "${repoUrl}/binary/${id}_${version}"}, //$NON-NLS-1$ //$NON-NLS-2$
			{"(& (classifier=org.eclipse.update.feature) (format=packed))", "${repoUrl}/features/${id}_${version}.jar.pack.gz"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=org.eclipse.update.feature))", "${repoUrl}/features/${id}_${version}.jar"}}; //$NON-NLS-1$//$NON-NLS-2$

	static final private String[][] DEFAULT_MAPPING_RULES = { {"(& (classifier=osgi.bundle))", "${repoUrl}/plugins/${id}_${version}.jar"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=binary))", "${repoUrl}/binary/${id}_${version}"}, //$NON-NLS-1$ //$NON-NLS-2$
			{"(& (classifier=org.eclipse.update.feature))", "${repoUrl}/features/${id}_${version}.jar"}}; //$NON-NLS-1$//$NON-NLS-2$
	private static final String JAR_EXTENSION = ".jar"; //$NON-NLS-1$
	static final private String REPOSITORY_TYPE = IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY;

	static final private Integer REPOSITORY_VERSION = new Integer(1);
	private static final String XML_EXTENSION = ".xml"; //$NON-NLS-1$
	protected Set<SimpleArtifactDescriptor> artifactDescriptors = new HashSet<SimpleArtifactDescriptor>();
	/**
	 * Map<IArtifactKey,List<IArtifactDescriptor>> containing the index of artifacts in the repository.
	 */
	private Map<IArtifactKey, List<IArtifactDescriptor>> artifactMap = new HashMap<IArtifactKey, List<IArtifactDescriptor>>();
	private transient BlobStore blobStore;
	transient private Mapper mapper = new Mapper();
	private KeyIndex keyIndex;
	private boolean snapshotNeeded = false;

	static final private String PUBLISH_PACK_FILES_AS_SIBLINGS = "publishPackFilesAsSiblings"; //$NON-NLS-1$

	private static final int DEFAULT_MAX_THREADS = 4;

	protected String[][] mappingRules = DEFAULT_MAPPING_RULES;

	private MirrorSelector mirrors;

	private boolean disableSave = false;

	static void delete(File toDelete) {
		if (toDelete.isDirectory()) {
			File[] children = toDelete.listFiles();
			if (children != null) {
				for (int i = 0; i < children.length; i++) {
					delete(children[i]);
				}
			}
		}
		toDelete.delete();
	}

	public static URI getActualLocation(URI base, boolean compress) throws IOException {
		return getActualLocation(base, compress ? JAR_EXTENSION : XML_EXTENSION);
	}

	private static URI getActualLocation(URI base, String extension) throws IOException {
		final String name = CONTENT_FILENAME + extension;
		String spec = base.toString();
		if (spec.endsWith(name))
			return base;
		if (spec.endsWith("/")) //$NON-NLS-1$
			spec += name;
		else
			spec += "/" + name; //$NON-NLS-1$
		try {
			return new URI(spec);
		} catch (URISyntaxException e) {
			throw new IOException(NLS.bind(Messages.io_invalidLocation, spec));
		}
	}

	public static URI getBlobStoreLocation(URI base) {
		return URIUtil.append(base, BLOBSTORE);
	}

	/*
	 * This is only called by the parser when loading a repository.
	 */
	SimpleArtifactRepository(IProvisioningAgent agent, String name, String type, String version, String description, String provider, Set<SimpleArtifactDescriptor> artifacts, String[][] mappingRules, Map<String, String> properties) {
		super(agent, name, type, version, null, description, provider, properties);
		this.artifactDescriptors.addAll(artifacts);
		this.mappingRules = mappingRules;
		for (SimpleArtifactDescriptor desc : artifactDescriptors)
			mapDescriptor(desc);
	}

	private synchronized void mapDescriptor(IArtifactDescriptor descriptor) {
		IArtifactKey key = descriptor.getArtifactKey();
		if (snapshotNeeded) {
			cloneAritfactMap();
			snapshotNeeded = false;
		}
		List<IArtifactDescriptor> descriptors = artifactMap.get(key);
		if (descriptors == null) {
			descriptors = new ArrayList<IArtifactDescriptor>();
			artifactMap.put(key, descriptors);
		}
		descriptors.add(descriptor);
		keyIndex = null;
	}

	private synchronized void unmapDescriptor(IArtifactDescriptor descriptor) {
		IArtifactKey key = descriptor.getArtifactKey();
		List<IArtifactDescriptor> descriptors = artifactMap.get(key);
		if (descriptors == null)
			return;

		if (snapshotNeeded) {
			cloneAritfactMap();
			snapshotNeeded = false;
			descriptors = artifactMap.get(key);
		}
		descriptors.remove(descriptor);
		if (descriptors.isEmpty())
			artifactMap.remove(key);
		keyIndex = null;
	}

	private void cloneAritfactMap() {
		HashMap<IArtifactKey, List<IArtifactDescriptor>> clone = new HashMap<IArtifactKey, List<IArtifactDescriptor>>(artifactMap.size());
		for (Entry<IArtifactKey, List<IArtifactDescriptor>> entry : artifactMap.entrySet())
			clone.put(entry.getKey(), new ArrayList<IArtifactDescriptor>(entry.getValue()));
		artifactMap = clone;
	}

	public SimpleArtifactRepository(IProvisioningAgent agent, String repositoryName, URI location, Map<String, String> properties) {
		super(agent, repositoryName, REPOSITORY_TYPE, REPOSITORY_VERSION.toString(), location, null, null, properties);
		initializeAfterLoad(location);
		if (properties != null) {
			if (properties.containsKey(PUBLISH_PACK_FILES_AS_SIBLINGS)) {
				synchronized (this) {
					String newValue = properties.get(PUBLISH_PACK_FILES_AS_SIBLINGS);
					if (Boolean.TRUE.toString().equals(newValue)) {
						mappingRules = PACKED_MAPPING_RULES;
					} else {
						mappingRules = DEFAULT_MAPPING_RULES;
					}
					initializeMapper();
				}
			}
		}
		save();
	}

	public synchronized void addDescriptor(IArtifactDescriptor toAdd) {
		if (artifactDescriptors.contains(toAdd))
			return;

		SimpleArtifactDescriptor internalDescriptor = createInternalDescriptor(toAdd);
		artifactDescriptors.add(internalDescriptor);
		mapDescriptor(internalDescriptor);
		save();
	}

	public IArtifactDescriptor createArtifactDescriptor(IArtifactKey key) {
		return new SimpleArtifactDescriptor(key);
	}

	private SimpleArtifactDescriptor createInternalDescriptor(IArtifactDescriptor descriptor) {
		SimpleArtifactDescriptor internal = new SimpleArtifactDescriptor(descriptor);

		internal.setRepository(this);
		if (isFolderBased(descriptor))
			internal.setRepositoryProperty(ARTIFACT_FOLDER, Boolean.TRUE.toString());

		//clear out the UUID if we aren't using the blobstore.
		if (flatButPackedEnabled(descriptor) && internal.getProperty(ARTIFACT_UUID) != null) {
			internal.setProperty(ARTIFACT_UUID, null);
		}

		if (descriptor instanceof SimpleArtifactDescriptor) {
			Map<String, String> repoProperties = ((SimpleArtifactDescriptor) descriptor).getRepositoryProperties();
			for (Map.Entry<String, String> entry : repoProperties.entrySet()) {
				internal.setRepositoryProperty(entry.getKey(), entry.getValue());
			}
		}
		return internal;
	}

	public synchronized void addDescriptors(IArtifactDescriptor[] descriptors) {
		for (int i = 0; i < descriptors.length; i++) {
			if (artifactDescriptors.contains(descriptors[i]))
				continue;
			SimpleArtifactDescriptor internalDescriptor = createInternalDescriptor(descriptors[i]);
			artifactDescriptors.add(internalDescriptor);
			mapDescriptor(internalDescriptor);
		}
		save();
	}

	private synchronized OutputStream addPostSteps(ProcessingStepHandler handler, IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		ArrayList<ProcessingStep> steps = new ArrayList<ProcessingStep>();
		steps.add(new SignatureVerifier());
		if (steps.isEmpty())
			return destination;
		ProcessingStep[] stepArray = steps.toArray(new ProcessingStep[steps.size()]);
		// TODO should probably be using createAndLink here
		return handler.link(stepArray, destination, monitor);
	}

	private OutputStream addPreSteps(ProcessingStepHandler handler, IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		ArrayList<ProcessingStep> steps = new ArrayList<ProcessingStep>();
		if (IArtifactDescriptor.TYPE_ZIP.equals(descriptor.getProperty(IArtifactDescriptor.DOWNLOAD_CONTENTTYPE)))
			steps.add(new ZipVerifierStep());
		if (MD5_CHECK_ENABLED && descriptor.getProperty(IArtifactDescriptor.DOWNLOAD_MD5) != null)
			steps.add(new MD5Verifier(descriptor.getProperty(IArtifactDescriptor.DOWNLOAD_MD5)));
		// Add steps here if needed
		if (steps.isEmpty())
			return destination;
		ProcessingStep[] stepArray = steps.toArray(new ProcessingStep[steps.size()]);
		// TODO should probably be using createAndLink here
		return handler.link(stepArray, destination, monitor);
	}

	private byte[] bytesFromHexString(String string) {
		byte[] bytes = new byte[UniversalUniqueIdentifier.BYTES_SIZE];
		for (int i = 0; i < string.length(); i += 2) {
			String byteString = string.substring(i, i + 2);
			bytes[i / 2] = (byte) Integer.parseInt(byteString, 16);
		}
		return bytes;
	}

	private String bytesToHexString(byte[] bytes) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			String hexString;
			if (bytes[i] < 0)
				hexString = Integer.toHexString(256 + bytes[i]);
			else
				hexString = Integer.toHexString(bytes[i]);
			if (hexString.length() == 1)
				buffer.append("0"); //$NON-NLS-1$
			buffer.append(hexString);
		}
		return buffer.toString();
	}

	public synchronized boolean contains(IArtifactDescriptor descriptor) {
		SimpleArtifactDescriptor simpleDescriptor = createInternalDescriptor(descriptor);
		return artifactDescriptors.contains(simpleDescriptor);
	}

	public synchronized boolean contains(IArtifactKey key) {
		return artifactMap.containsKey(key);
	}

	public synchronized URI createLocation(ArtifactDescriptor descriptor) {
		if (flatButPackedEnabled(descriptor)) {
			return getLocationForPackedButFlatArtifacts(descriptor);
		}
		// if the descriptor is canonical, clear out any UUID that might be set and use the Mapper
		if (descriptor.getProcessingSteps().length == 0) {
			descriptor.setProperty(ARTIFACT_UUID, null);
			IArtifactKey key = descriptor.getArtifactKey();
			URI result = mapper.map(getLocation(), key.getClassifier(), key.getId(), key.getVersion().toString(), descriptor.getProperties());
			if (result != null) {
				if (isFolderBased(descriptor) && URIUtil.lastSegment(result).endsWith(JAR_EXTENSION)) {
					return URIUtil.removeFileExtension(result);
				}
				return result;
			}
		}

		// Otherwise generate a location by creating a UUID, remembering it in the properties 
		// and computing the location
		byte[] bytes = new UniversalUniqueIdentifier().toBytes();
		descriptor.setProperty(ARTIFACT_UUID, bytesToHexString(bytes));
		return blobStore.fileFor(bytes);
	}

	/**
	 * Removes the given descriptor, and the physical artifact corresponding
	 * to that descriptor. Returns <code>true</code> if and only if the
	 * descriptor existed in the repository, and was successfully removed.
	 */
	private boolean doRemoveArtifact(IArtifactDescriptor descriptor) {
		SimpleArtifactDescriptor simple = null;
		if (descriptor instanceof SimpleArtifactDescriptor)
			simple = (SimpleArtifactDescriptor) descriptor;
		else
			simple = createInternalDescriptor(descriptor);
		if (simple.getRepositoryProperty(SimpleArtifactDescriptor.ARTIFACT_REFERENCE) == null) {
			File file = getArtifactFile(descriptor);
			if (file != null) {
				// If the file != null remove it, otherwise just remove
				// the descriptor
				delete(file);
				if (file.exists())
					return false;
			}
		}
		boolean result = artifactDescriptors.remove(descriptor);
		if (result)
			unmapDescriptor(descriptor);

		return result;
	}

	protected IStatus downloadArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		if (isFolderBased(descriptor)) {
			File artifactFolder = getArtifactFile(descriptor);
			if (artifactFolder == null) {
				if (getLocation(descriptor) != null && !URIUtil.isFileURI(getLocation(descriptor)))
					return reportStatus(descriptor, destination, new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.folder_artifact_not_file_repo, descriptor.getArtifactKey())));
				return reportStatus(descriptor, destination, new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.artifact_not_found, descriptor.getArtifactKey())));
			}
			// TODO: optimize and ensure manifest is written first
			File zipFile = null;
			try {
				zipFile = File.createTempFile(artifactFolder.getName(), JAR_EXTENSION, null);
				FileUtils.zip(artifactFolder.listFiles(), null, zipFile, FileUtils.createRootPathComputer(artifactFolder));
				FileInputStream fis = new FileInputStream(zipFile);
				FileUtils.copyStream(fis, true, destination, false);
			} catch (IOException e) {
				return reportStatus(descriptor, destination, new Status(IStatus.ERROR, Activator.ID, e.getMessage()));
			} finally {
				if (zipFile != null)
					zipFile.delete();
			}
			return reportStatus(descriptor, destination, Status.OK_STATUS);
		}

		//download from the best available mirror
		URI baseLocation = getLocation(descriptor);
		if (baseLocation == null)
			return new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.no_location, descriptor));
		URI mirrorLocation = getMirror(baseLocation, monitor);
		IStatus status = downloadArtifact(descriptor, mirrorLocation, destination, monitor);
		IStatus result = reportStatus(descriptor, destination, status);
		// if the original download went reasonably but the reportStatus found some issues
		// (e..g, in the processing steps/validators) then mark the mirror as bad and return
		// a retry code (assuming we have more mirrors)
		if ((status.isOK() || status.matches(IStatus.INFO | IStatus.WARNING)) && result.getSeverity() == IStatus.ERROR) {
			if (mirrors != null) {
				mirrors.reportResult(mirrorLocation.toString(), result);
				if (mirrors.hasValidMirror())
					return new MultiStatus(Activator.ID, CODE_RETRY, new IStatus[] {result}, "Retry another mirror", null); //$NON-NLS-1$
			}
		}
		// if the original status was a retry, don't lose that.
		return status.getCode() == CODE_RETRY ? status : result;
	}

	private IStatus downloadArtifact(IArtifactDescriptor descriptor, URI mirrorLocation, OutputStream destination, IProgressMonitor monitor) {
		IStatus result = getTransport().download(mirrorLocation, destination, monitor);
		if (mirrors != null)
			mirrors.reportResult(mirrorLocation.toString(), result);
		if (result.isOK() || result.getSeverity() == IStatus.CANCEL)
			return result;
		if (monitor.isCanceled())
			return Status.CANCEL_STATUS;
		// If there are more valid mirrors then return an error with a special code that tells the caller
		// to keep trying.  Note that the message in the status is largely irrelevant but the child
		// status tells the story of why we failed on this try.
		// TODO find a better way of doing this.
		if (mirrors != null && mirrors.hasValidMirror())
			return new MultiStatus(Activator.ID, CODE_RETRY, new IStatus[] {result}, "Retry another mirror", null); //$NON-NLS-1$
		return result;
	}

	/**
	 * Returns an equivalent mirror location for the given artifact location.
	 * @param baseLocation The location of the artifact in this repository
	 * @return the Location of the artifact in this repository, or an equivalent mirror
	 */
	private synchronized URI getMirror(URI baseLocation, IProgressMonitor monitor) {
		if (!MIRRORS_ENABLED || (!isForceThreading() && isLocal()))
			return baseLocation;
		if (mirrors == null)
			mirrors = new MirrorSelector(this);
		return mirrors.getMirrorLocation(baseLocation, monitor);
	}

	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class adapter) {
		// if we are adapting to file or writable repositories then make sure we have a file location
		if (adapter == IFileArtifactRepository.class)
			if (!isLocal())
				return null;
		return super.getAdapter(adapter);
	}

	IStatus getArtifact(IArtifactRequest request, IProgressMonitor monitor) {
		request.perform(this, monitor);
		return request.getResult();
	}

	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		ProcessingStepHandler handler = new ProcessingStepHandler();
		destination = processDestination(handler, descriptor, destination, monitor);
		IStatus status = ProcessingStepHandler.checkStatus(destination);
		if (!status.isOK() && status.getSeverity() != IStatus.INFO)
			return status;

		return downloadArtifact(descriptor, destination, monitor);
	}

	public IStatus getRawArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return downloadArtifact(descriptor, destination, monitor);
	}

	public synchronized IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		List<IArtifactDescriptor> result = artifactMap.get(key);
		if (result == null)
			return new IArtifactDescriptor[0];

		return result.toArray(new IArtifactDescriptor[result.size()]);
	}

	public File getArtifactFile(IArtifactDescriptor descriptor) {
		URI result = getLocation(descriptor);
		if (result == null || !URIUtil.isFileURI(result))
			return null;
		return URIUtil.toFile(result);
	}

	public File getArtifactFile(IArtifactKey key) {
		IArtifactDescriptor descriptor = getCompleteArtifactDescriptor(key);
		if (descriptor == null)
			return null;
		return getArtifactFile(descriptor);
	}

	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor) {
		final MultiStatus overallStatus = new MultiStatus(Activator.ID, IStatus.OK, null, null);
		LinkedList<IArtifactRequest> requestsPending = new LinkedList<IArtifactRequest>(Arrays.asList(requests));

		int numberOfJobs = Math.min(requests.length, getMaximumThreads());
		if (numberOfJobs <= 1 || (!isForceThreading() && isLocal())) {
			SubMonitor subMonitor = SubMonitor.convert(monitor, requests.length);
			try {
				for (int i = 0; i < requests.length; i++) {
					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;
					IStatus result = getArtifact(requests[i], subMonitor.newChild(1));
					if (!result.isOK())
						overallStatus.add(result);
				}
			} finally {
				subMonitor.done();
			}
		} else {
			// initialize the various jobs needed to process the get artifact requests
			monitor.beginTask(NLS.bind(Messages.sar_downloading, Integer.toString(requests.length)), requests.length);
			try {
				DownloadJob jobs[] = new DownloadJob[numberOfJobs];
				for (int i = 0; i < numberOfJobs; i++) {
					jobs[i] = new DownloadJob(Messages.sar_downloadJobName + i);
					jobs[i].initialize(this, requestsPending, monitor, overallStatus);
					jobs[i].schedule();
				}
				// wait for all the jobs to complete
				try {
					Job.getJobManager().join(DownloadJob.FAMILY, null);
				} catch (InterruptedException e) {
					//ignore
				}
			} finally {
				monitor.done();
			}
		}
		return (monitor.isCanceled() ? Status.CANCEL_STATUS : overallStatus);
	}

	public synchronized IArtifactDescriptor getCompleteArtifactDescriptor(IArtifactKey key) {
		List<IArtifactDescriptor> descriptors = artifactMap.get(key);
		if (descriptors == null)
			return null;

		for (IArtifactDescriptor desc : descriptors) {
			// look for a descriptor that matches the key and is "complete"
			if (desc.getArtifactKey().equals(key) && desc.getProcessingSteps().length == 0)
				return desc;
		}
		return null;
	}

	public synchronized Set<SimpleArtifactDescriptor> getDescriptors() {
		return artifactDescriptors;
	}

	/**
	 * Typically non-canonical forms of the artifact are stored in the blob store.
	 * However, we support having the pack200 files alongside the canonical artifact
	 * for compatibility with the format used in optimized update sites.  We call
	 * this arrangement "flat but packed".
	 */
	private boolean flatButPackedEnabled(IArtifactDescriptor descriptor) {
		return Boolean.TRUE.toString().equals(getProperties().get(PUBLISH_PACK_FILES_AS_SIBLINGS)) && IArtifactDescriptor.FORMAT_PACKED.equals(descriptor.getProperty(IArtifactDescriptor.FORMAT));
	}

	/**
	 * @see #flatButPackedEnabled(IArtifactDescriptor)
	 */
	private URI getLocationForPackedButFlatArtifacts(IArtifactDescriptor descriptor) {
		IArtifactKey key = descriptor.getArtifactKey();
		return mapper.map(getLocation(), key.getClassifier(), key.getId(), key.getVersion().toString(), descriptor.getProperties());
	}

	public synchronized URI getLocation(IArtifactDescriptor descriptor) {
		if (flatButPackedEnabled(descriptor)) {
			return getLocationForPackedButFlatArtifacts(descriptor);
		}

		// if the artifact has a uuid then use it
		String uuid = descriptor.getProperty(ARTIFACT_UUID);
		if (uuid != null)
			return blobStore.fileFor(bytesFromHexString(uuid));

		try {
			// if the artifact is just a reference then return the reference location
			if (descriptor instanceof SimpleArtifactDescriptor) {
				String artifactReference = ((SimpleArtifactDescriptor) descriptor).getRepositoryProperty(SimpleArtifactDescriptor.ARTIFACT_REFERENCE);
				if (artifactReference != null) {
					try {
						return new URI(artifactReference);
					} catch (URISyntaxException e) {
						return URIUtil.fromString(artifactReference);
					}
				}
			}

			// if the descriptor is complete then use the mapping rules...
			if (descriptor.getProcessingSteps().length == 0) {
				IArtifactKey key = descriptor.getArtifactKey();
				URI result = mapper.map(getLocation(), key.getClassifier(), key.getId(), key.getVersion().toString(), descriptor.getProperties());
				if (result != null) {
					if (isFolderBased(descriptor) && URIUtil.lastSegment(result).endsWith(JAR_EXTENSION))
						return URIUtil.removeFileExtension(result);

					return result;
				}
			}
		} catch (URISyntaxException e) {
			return null;
		}
		// in the end there is not enough information so return null 
		return null;
	}

	/**
	 * Returns the maximum number of concurrent download threads.
	 */
	private int getMaximumThreads() {
		try {
			String maxThreadString = getProperties().get(PROP_MAX_THREADS);
			if (maxThreadString != null)
				return Math.max(1, Integer.parseInt(maxThreadString));
		} catch (NumberFormatException nfe) {
			// return default number of threads
		}
		return DEFAULT_MAX_THREADS;
	}

	public OutputStream getOutputStream(IArtifactDescriptor descriptor) throws ProvisionException {
		assertModifiable();

		// Create a copy of the original descriptor that we can manipulate and add to our repo.
		ArtifactDescriptor newDescriptor = createInternalDescriptor(descriptor);

		// Check if the artifact is already in this repository, check the newDescriptor instead of the original
		// since the implementation of hash/equals on the descriptor matters here.
		if (contains(newDescriptor)) {
			String msg = NLS.bind(Messages.available_already_in, getLocation().toString());
			throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.ARTIFACT_EXISTS, msg, null));
		}

		// Determine writing location
		URI newLocation = createLocation(newDescriptor);
		if (newLocation == null)
			throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.no_location, newDescriptor)));
		String file = URIUtil.toFile(newLocation).getAbsolutePath();

		// TODO at this point we have to assume that the repository is file-based.  Eventually 
		// we should end up with writeable URLs...
		// Make sure that the file does not exist and that the parents do
		File outputFile = new File(file);
		if (outputFile.exists()) {
			System.err.println("Artifact repository out of sync. Overwriting " + outputFile.getAbsoluteFile()); //$NON-NLS-1$
			delete(outputFile);
		}

		OutputStream target = null;
		try {
			if (isFolderBased(newDescriptor)) {
				mkdirs(outputFile);
				if (!outputFile.isDirectory())
					throw failedWrite(new IOException(NLS.bind(Messages.sar_failedMkdir, outputFile.toString())));
				target = new ZippedFolderOutputStream(outputFile);
			} else {
				// file based
				File parent = outputFile.getParentFile();
				parent.mkdirs();
				if (!parent.isDirectory())
					throw failedWrite(new IOException(NLS.bind(Messages.sar_failedMkdir, parent.toString())));
				target = new FileOutputStream(file);
			}

			// finally create and return an output stream suitably wrapped so that when it is 
			// closed the repository is updated with the descriptor
			return new ArtifactOutputStream(new BufferedOutputStream(target), newDescriptor, outputFile);
		} catch (IOException e) {
			throw failedWrite(e);
		}

	}

	/**
	 * We implement mkdirs ourselves because this code is known to run in
	 * highly concurrent scenarios, and there is a race condition in the JRE implementation
	 * of mkdirs (see bug 265654).
	 */
	private void mkdirs(File dir) {
		if (dir == null || dir.exists())
			return;
		if (dir.mkdir())
			return;
		mkdirs(dir.getParentFile());
		dir.mkdir();
	}

	private ProvisionException failedWrite(Exception e) throws ProvisionException {
		String msg = NLS.bind(Messages.repoFailedWrite, getLocation());
		throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_FAILED_WRITE, msg, e));
	}

	public synchronized String[][] getRules() {
		return mappingRules;
	}

	private Transport getTransport() {
		return RepositoryTransport.getInstance();
	}

	// use this method to setup any transient fields etc after the object has been restored from a stream
	public synchronized void initializeAfterLoad(URI repoLocation) {
		setLocation(repoLocation);
		blobStore = new BlobStore(getBlobStoreLocation(repoLocation), 128);
		initializeMapper();
		for (SimpleArtifactDescriptor desc : artifactDescriptors)
			desc.setRepository(this);
	}

	private synchronized void initializeMapper() {
		mapper = new Mapper();
		mapper.initialize(Activator.getContext(), mappingRules);
	}

	private boolean isFolderBased(IArtifactDescriptor descriptor) {
		// This is called from createInternalDescriptor, so if we aren't a
		// SimpleArtifactDescriptor then just check the descriptor properties instead 
		// of creating the internal descriptor.
		SimpleArtifactDescriptor internalDescriptor = null;
		if (descriptor instanceof SimpleArtifactDescriptor)
			internalDescriptor = (SimpleArtifactDescriptor) descriptor;
		if (internalDescriptor != null) {
			String useArtifactFolder = internalDescriptor.getRepositoryProperty(ARTIFACT_FOLDER);
			if (useArtifactFolder != null)
				return Boolean.valueOf(useArtifactFolder).booleanValue();
		}
		return Boolean.valueOf(descriptor.getProperty(ARTIFACT_FOLDER)).booleanValue();
	}

	private boolean isForceThreading() {
		return "true".equals(getProperties().get(PROP_FORCE_THREADING)); //$NON-NLS-1$
	}

	private boolean isLocal() {
		return "file".equalsIgnoreCase(getLocation().getScheme()); //$NON-NLS-1$
	}

	public boolean isModifiable() {
		return isLocal();
	}

	public OutputStream processDestination(ProcessingStepHandler handler, IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		destination = addPostSteps(handler, descriptor, destination, monitor);
		destination = handler.createAndLink(getProvisioningAgent(), descriptor.getProcessingSteps(), descriptor, destination, monitor);
		destination = addPreSteps(handler, descriptor, destination, monitor);
		return destination;
	}

	public synchronized void removeAll() {
		IArtifactDescriptor[] toRemove = artifactDescriptors.toArray(new IArtifactDescriptor[artifactDescriptors.size()]);
		boolean changed = false;
		for (int i = 0; i < toRemove.length; i++)
			changed |= doRemoveArtifact(toRemove[i]);
		if (changed)
			save();
	}

	public synchronized void removeDescriptor(IArtifactDescriptor descriptor) {
		if (doRemoveArtifact(descriptor))
			save();
	}

	public synchronized void removeDescriptor(IArtifactKey key) {
		IArtifactDescriptor[] toRemove = getArtifactDescriptors(key);
		boolean changed = false;
		for (int i = 0; i < toRemove.length; i++)
			changed |= doRemoveArtifact(toRemove[i]);
		if (changed)
			save();
	}

	private IStatus reportStatus(IArtifactDescriptor descriptor, OutputStream destination, IStatus status) {
		// If the destination is just a normal stream then the status is simple.  Just return
		// it and do not close the destination
		if (!(destination instanceof ProcessingStep))
			return status;

		// If the destination is a processing step then close the stream to flush the data through all
		// the steps.  then collect up the status from all the steps and return
		try {
			destination.close();
		} catch (IOException e) {
			return new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.sar_reportStatus, descriptor.getArtifactKey().toExternalForm()), e);
		}

		// An error occurred obtaining the artifact, ProcessingStep errors aren't important
		if (status.matches(IStatus.ERROR))
			return status;

		IStatus stepStatus = ProcessingStepHandler.getErrorStatus(destination);
		// if the steps all ran ok and there is no interesting information, return the status from this method
		if (!stepStatus.isMultiStatus() && stepStatus.isOK())
			return status;
		// else gather up the status from the steps
		MultiStatus result = new MultiStatus(Activator.ID, IStatus.OK, new IStatus[0], NLS.bind(Messages.sar_reportStatus, descriptor.getArtifactKey().toExternalForm()), null);

		if (!status.isOK()) {
			// Transport pushes its status onto the output stream if the stream implements IStateful, to prevent
			// duplication determine if the Status is present in the ProcessingStep status. 
			boolean found = false;
			IStatus[] stepStatusChildren = stepStatus.getChildren();
			for (int i = 0; i < stepStatusChildren.length && !found; i++)
				if (stepStatusChildren[i] == status)
					found = true;
			if (!found)
				result.merge(status);
		}

		result.merge(stepStatus);
		return result;
	}

	public void save() {
		if (disableSave)
			return;
		boolean compress = "true".equalsIgnoreCase(getProperty(PROP_COMPRESSED)); //$NON-NLS-1$
		save(compress);
	}

	private void save(boolean compress) {
		assertModifiable();
		OutputStream os = null;
		try {
			try {
				URI actualLocation = getActualLocation(getLocation(), false);
				File artifactsFile = URIUtil.toFile(actualLocation);
				File jarFile = URIUtil.toFile(getActualLocation(getLocation(), true));
				if (!compress) {
					if (jarFile.exists()) {
						jarFile.delete();
					}
					if (!artifactsFile.exists()) {
						// create parent folders
						mkdirs(artifactsFile.getParentFile());
					}
					os = new FileOutputStream(artifactsFile);
				} else {
					if (artifactsFile.exists()) {
						artifactsFile.delete();
					}
					if (!jarFile.exists()) {
						mkdirs(jarFile.getParentFile());
						jarFile.createNewFile();
					}
					JarOutputStream jOs = new JarOutputStream(new FileOutputStream(jarFile));
					jOs.putNextEntry(new JarEntry(new Path(artifactsFile.getAbsolutePath()).lastSegment()));
					os = jOs;
				}
				super.setProperty(IRepository.PROP_TIMESTAMP, Long.toString(System.currentTimeMillis()));
				new SimpleArtifactRepositoryIO(getProvisioningAgent()).write(this, os);
			} catch (IOException e) {
				// TODO proper exception handling
				e.printStackTrace();
			} finally {
				if (os != null)
					os.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String setProperty(String key, String newValue) {
		String oldValue = super.setProperty(key, newValue);
		if (oldValue == newValue || (oldValue != null && oldValue.equals(newValue)))
			return oldValue;
		if (PUBLISH_PACK_FILES_AS_SIBLINGS.equals(key)) {
			synchronized (this) {
				if (Boolean.TRUE.toString().equals(newValue)) {
					setRules(PACKED_MAPPING_RULES);
				} else {
					setRules(DEFAULT_MAPPING_RULES);
				}
			}
		}
		save();
		//force repository manager to reload this repository because it caches properties
		ArtifactRepositoryManager manager = (ArtifactRepositoryManager) getProvisioningAgent().getService(IArtifactRepositoryManager.SERVICE_NAME);
		if (manager.removeRepository(getLocation()))
			manager.addRepository(this);
		return oldValue;
	}

	public synchronized void setRules(String[][] rules) {
		mappingRules = rules;
		initializeMapper();
	}

	public String toString() {
		return getLocation().toString();
	}

	public IQueryable<IArtifactDescriptor> descriptorQueryable() {
		return new IQueryable<IArtifactDescriptor>() {
			public IQueryResult<IArtifactDescriptor> query(IQuery<IArtifactDescriptor> query, IProgressMonitor monitor) {
				synchronized (SimpleArtifactRepository.this) {
					snapshotNeeded = true;
					Collection<List<IArtifactDescriptor>> descs = SimpleArtifactRepository.this.artifactMap.values();
					return query.perform(new CompoundIterator<IArtifactDescriptor>(descs.iterator()));
				}
			}
		};
	}

	public IQueryResult<IArtifactKey> query(IQuery<IArtifactKey> query, IProgressMonitor monitor) {
		return IndexProvider.query(this, query, monitor);
	}

	public synchronized Iterator<IArtifactKey> everything() {
		snapshotNeeded = true;
		return artifactMap.keySet().iterator();
	}

	public IStatus executeBatch(IRunnableWithProgress runnable, IProgressMonitor monitor) {
		IStatus result = null;
		synchronized (this) {
			try {
				disableSave = true;
				runnable.run(monitor);
			} catch (Throwable e) {
				result = new Status(IStatus.ERROR, Activator.ID, e.getMessage(), e);
			} finally {
				disableSave = false;
				try {
					save();
				} catch (Exception e) {
					if (result != null)
						result = new MultiStatus(Activator.ID, IStatus.ERROR, new IStatus[] {result}, e.getMessage(), e);
					else
						result = new Status(IStatus.ERROR, Activator.ID, e.getMessage(), e);
				}
			}
		}
		if (result == null)
			result = Status.OK_STATUS;
		return result;
	}

	public synchronized IIndex<IArtifactKey> getIndex(String memberName) {
		if (ArtifactKey.MEMBER_ID.equals(memberName)) {
			snapshotNeeded = true;
			if (keyIndex == null)
				keyIndex = new KeyIndex(artifactMap.keySet());
			return keyIndex;
		}
		return null;
	}

	public Object getManagedProperty(Object client, String memberName, Object key) {
		return null;
	}
}
