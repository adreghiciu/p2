/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.touchpoint.natives;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.p2.core.*;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.*;
import org.eclipse.osgi.util.NLS;

public class Util {

	public static void log(String message) {
		LogHelper.log(createError(message));
	}

	public static IStatus createError(String message) {
		return new Status(IStatus.ERROR, Activator.ID, message);
	}

	public static String getInstallFolder(IProfile profile) {
		return profile.getProperty(IProfile.PROP_INSTALL_FOLDER);
	}

	private static IAgentLocation getAgentLocation(IProvisioningAgent agent) {
		return (IAgentLocation) agent.getService(IAgentLocation.SERVICE_NAME);
	}

	public static IArtifactRepositoryManager getArtifactRepositoryManager(IProvisioningAgent agent) {
		return (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
	}

	public static IFileArtifactRepository getDownloadCacheRepo(IProvisioningAgent agent) throws ProvisionException {
		URI location = getDownloadCacheLocation(agent);
		if (location == null)
			throw new IllegalStateException(Messages.could_not_obtain_download_cache);
		IArtifactRepositoryManager manager = getArtifactRepositoryManager(agent);
		if (manager == null)
			throw new IllegalStateException(Messages.artifact_repo_not_found);
		IArtifactRepository repository;
		try {
			repository = manager.loadRepository(location, null);
		} catch (ProvisionException e) {
			// the download cache doesn't exist or couldn't be read. Create new cache.
			String repositoryName = location + " - Agent download cache"; //$NON-NLS-1$
			Map<String, String> properties = new HashMap<String, String>(1);
			properties.put(IRepository.PROP_SYSTEM, Boolean.TRUE.toString());
			repository = manager.createRepository(location, repositoryName, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);
		}

		IFileArtifactRepository downloadCache = (IFileArtifactRepository) repository.getAdapter(IFileArtifactRepository.class);
		if (downloadCache == null)
			throw new ProvisionException(NLS.bind(Messages.download_cache_not_writeable, location));
		return downloadCache;
	}

	static private URI getDownloadCacheLocation(IProvisioningAgent agent) {
		IAgentLocation location = getAgentLocation(agent);
		if (location == null)
			return null;
		return URIUtil.append(location.getDataArea("org.eclipse.equinox.p2.core"), "cache/"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Unzip from a File to an output directory, with progress indication and backup.
	 * monitor and backup store may be null.
	 */
	public static File[] unzipFile(File zipFile, File outputDir, IBackupStore store, String taskName, IProgressMonitor monitor) throws IOException {
		InputStream in = new FileInputStream(zipFile);
		try {
			return unzipStream(in, zipFile.length(), outputDir, store, taskName, monitor);
		} catch (IOException e) {
			// add the file name to the message
			throw new IOException(NLS.bind(Messages.Util_Error_Unzipping, zipFile, e.getMessage()));
		} finally {
			in.close();
		}
	}

	/**
	 * Unzip from an InputStream to an output directory using backup of overwritten files
	 * if backup store is not null.
	 */
	public static File[] unzipStream(InputStream stream, long size, File outputDir, IBackupStore store, String taskName, IProgressMonitor monitor) throws IOException {
		InputStream is = monitor == null ? stream : stream; // new ProgressMonitorInputStream(stream, size, size, taskName, monitor); TODO Commented code
		ZipInputStream in = new ZipInputStream(new BufferedInputStream(is));
		ZipEntry ze = in.getNextEntry();
		if (ze == null) {
			// There must be at least one entry in a zip file.
			// When there isn't getNextEntry returns null.
			in.close();
			throw new IOException(Messages.Util_Invalid_Zip_File_Format);
		}
		ArrayList<File> unzippedFiles = new ArrayList<File>();
		do {
			File outFile = new File(outputDir, ze.getName());
			unzippedFiles.add(outFile);
			if (ze.isDirectory()) {
				outFile.mkdirs();
			} else {
				if (outFile.exists()) {
					if (store != null)
						store.backup(outFile);
					else
						outFile.delete();
				} else {
					outFile.getParentFile().mkdirs();
				}
				try {
					copyStream(in, false, new FileOutputStream(outFile), true);
				} catch (FileNotFoundException e) {
					// TEMP: ignore this for now in case we're trying to replace
					// a running eclipse.exe
				}
				outFile.setLastModified(ze.getTime());
			}
			in.closeEntry();
		} while ((ze = in.getNextEntry()) != null);
		in.close();

		return unzippedFiles.toArray(new File[unzippedFiles.size()]);
	}

	/**
	 * Copy an input stream to an output stream.
	 * Optionally close the streams when done.
	 * Return the number of bytes written.
	 */
	public static int copyStream(InputStream in, boolean closeIn, OutputStream out, boolean closeOut) throws IOException {
		try {
			int written = 0;
			byte[] buffer = new byte[16 * 1024];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
				written += len;
			}
			return written;
		} finally {
			try {
				if (closeIn) {
					in.close();
				}
			} finally {
				if (closeOut) {
					out.close();
				}
			}
		}
	}
}
