/*******************************************************************************
 *  Copyright (c) 2007, 2010 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.repository.artifact;

import java.io.OutputStream;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.artifact.spi.AbstractArtifactRepository;

/**
 * A repository containing artifacts.
 * <p>
 * This interface is not intended to be implemented by clients.  Artifact repository
 * implementations must subclass {@link AbstractArtifactRepository} rather than 
 * implementing this interface directly.
 * </p>
 * @noimplement This interface is not intended to be implemented by clients. Instead subclass {@link AbstractArtifactRepository}.
 * @noextend This interface is not intended to be extended by clients.
 * @since 2.0
 */
public interface IArtifactRepository extends IRepository<IArtifactKey> {

	/** 
	 * The return code to use when a client could/should retry a failed getArtifact() operation.
	 * For example, the repository may have additional mirrors that could be consulted.
	 */
	public static int CODE_RETRY = 13;

	/**
	 * Create an instance of {@link IArtifactDescriptor} based on the given key
	 * @param key {@link IArtifactKey}
	 * @return a new instanceof of IArtifactDescriptor
	 */
	public IArtifactDescriptor createArtifactDescriptor(IArtifactKey key);

	/**
	 * Create an instance of {@link IArtifactKey}
	 * @param classifier
	 * @param id
	 * @param version
	 * @return a new IArtifactKey
	 */
	public IArtifactKey createArtifactKey(String classifier, String id, Version version);

	/**
	 * Add the given descriptor to the set of descriptors in this repository.  This is 
	 * a relatively low-level operation that should be used only when the actual related 
	 * content is in this repository and the given descriptor accurately describes 
	 * that content.
	 * @param descriptor the descriptor to add.
	 */
	public void addDescriptor(IArtifactDescriptor descriptor);

	/**
	 * Add the given artifact descriptors to this repository
	 * @param descriptors the artifact descriptors to add
	 */
	public void addDescriptors(IArtifactDescriptor[] descriptors);

	/** 
	 * Returns true if this repository contains the given descriptor.
	 * @param descriptor the descriptor to query
	 * @return true if the given descriptor is already in this repository
	 */
	public boolean contains(IArtifactDescriptor descriptor);

	/** 
	 * Returns true if this repository contains the given artifact key.
	 * @param key the key to query
	 * @return true if the given key is already in this repository
	 */
	public boolean contains(IArtifactKey key);

	/**
	 * Writes to the given output stream the bytes represented by the artifact descriptor.
	 * Any processing steps defined by the descriptor will be applied to the artifact bytes
	 * before they are sent to the provided output stream.
	 * 
	 * @param descriptor the descriptor to transfer
	 * @param destination the stream to write the final artifact output to
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 *    reporting and cancellation are not desired
	 *  @return the result of the artifact transfer
	 */
	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor);

	/**
	 * Writes to the given output stream the bytes represented by the artifact descriptor.
	 * Any processing steps defined by the descriptor will <b>not</b> be applied to the artifact bytes.
	 * 
	 * @param descriptor the descriptor to transfer
	 * @param destination the stream to write the final artifact output to
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 *    reporting and cancellation are not desired
	 *  @return the result of the artifact transfer
	 */
	public IStatus getRawArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor);

	/**
	 * Return the set of artifact descriptors describing the ways that this repository
	 * can supply the artifact associated with the given artifact key
	 * @param key the artifact key to lookup
	 * @return the descriptors associated with the given key
	 */
	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key);

	/**
	 * Executes the given artifact requests on this byte server.
	 * @param requests The artifact requests
	 * @param monitor
	 * @return a status object that is <code>OK</code> if requests were
	 * processed successfully. Otherwise, a status indicating information,
	 * warnings, or errors that occurred while executing the artifact requests
	 */
	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor);

	/**
	 * Open an output stream to which a client can write the data for the given 
	 * artifact descriptor.
	 * @param descriptor the descriptor describing the artifact data to be written to the 
	 * resultant stream
	 * @return the stream to which the artifact content can be written. The returned output
	 *  stream may implement <code>IStateful</code>.
	 * @throws ProvisionException if the output stream could not be created.  Reasons include:
	 * <ul>
	 * <li>An I/O exception occurred (@link {@link ProvisionException#REPOSITORY_FAILED_WRITE}) .</li>
	 * <li>An artifact already exists at that location ({@link ProvisionException#ARTIFACT_EXISTS}).</li>
	 * </ul>
	 */
	public OutputStream getOutputStream(IArtifactDescriptor descriptor) throws ProvisionException;

	/**
	 * Returns a queryable that can be queried for artifact descriptors contained in this repository
	 * @return The queryable of artifact descriptors
	 */
	public IQueryable<IArtifactDescriptor> descriptorQueryable();

	/**
	 * Remove the all keys, descriptors, and contents from this repository.
	 */
	public void removeAll();

	/**
	 * Remove the given descriptor and its corresponding content in this repository.  
	 * @param descriptor the descriptor to remove.
	 */
	public void removeDescriptor(IArtifactDescriptor descriptor);

	/**
	 * Remove the given key and all related content and descriptors from this repository.  
	 * @param key the key to remove.
	 */
	public void removeDescriptor(IArtifactKey key);

	/**
	 * Executes a runnable against this repository. It is up to the repository
	 * implementor to determine what "batch process" means, for example, it may mean
	 * that the repository index is not stored until after the runnable completes.
	 * 
	 * The runnable should not execute anything in a separate thread.
	 *  
	 * @param runnable The runnable to execute
	 * @param monitor A progress monitor that will be passed to the runnable
	 * @return The result of running the runnable. Any exceptions thrown during
	 * the execution will be returned in the status.
	 */
	public IStatus executeBatch(IRunnableWithProgress runnable, IProgressMonitor monitor);

}