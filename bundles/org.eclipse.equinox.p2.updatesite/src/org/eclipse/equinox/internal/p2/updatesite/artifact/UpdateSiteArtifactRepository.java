/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.updatesite.artifact;

import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.*;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.artifact.*;

public class UpdateSiteArtifactRepository implements IArtifactRepository {

	public static final String TYPE = "org.eclipse.equinox.p2.updatesite.artifactRepository"; //$NON-NLS-1$
	public static final String VERSION = Integer.toString(1);

	private URI location;
	private IArtifactRepository delegate;

	public UpdateSiteArtifactRepository(URI location, IArtifactRepository repository) {
		this.location = location;
		this.delegate = repository;
	}

	public void addDescriptor(IArtifactDescriptor descriptor) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public void addDescriptors(IArtifactDescriptor[] descriptors) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public boolean contains(IArtifactDescriptor descriptor) {
		return delegate.contains(descriptor);
	}

	public boolean contains(IArtifactKey key) {
		return delegate.contains(key);
	}

	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return delegate.getArtifact(descriptor, destination, monitor);
	}

	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		return delegate.getArtifactDescriptors(key);
	}

	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor) {
		return delegate.getArtifacts(requests, monitor);
	}

	public OutputStream getOutputStream(IArtifactDescriptor descriptor) throws ProvisionException {
		// TODO Auto-generated method stub
		return null;
	}

	public IStatus getRawArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return delegate.getRawArtifact(descriptor, destination, monitor);
	}

	public void removeAll() {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public void removeDescriptor(IArtifactDescriptor descriptor) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public void removeDescriptor(IArtifactKey key) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public String getDescription() {
		return delegate.getDescription();
	}

	public URI getLocation() {
		return this.location;
	}

	public String getName() {
		return delegate.getName();
	}

	public Map<String, String> getProperties() {
		return delegate.getProperties();
	}

	public String getProperty(String key) {
		return delegate.getProperty(key);
	}

	public String getProvider() {
		return delegate.getProvider();
	}

	public IProvisioningAgent getProvisioningAgent() {
		return delegate.getProvisioningAgent();
	}

	public String getType() {
		return TYPE;
	}

	public String getVersion() {
		return VERSION;
	}

	public boolean isModifiable() {
		return false;
	}

	public void setDescription(String description) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public void setName(String name) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public String setProperty(String key, String value) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	public void setProvider(String provider) {
		throw new UnsupportedOperationException("Repository not modifiable: " + location); //$NON-NLS-1$
	}

	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class adapter) {
		return delegate.getAdapter(adapter);
	}

	public IArtifactDescriptor createArtifactDescriptor(IArtifactKey key) {
		return delegate.createArtifactDescriptor(key);
	}

	public IArtifactKey createArtifactKey(String classifier, String id, Version version) {
		return delegate.createArtifactKey(classifier, id, version);
	}

	public IQueryable<IArtifactDescriptor> descriptorQueryable() {
		return delegate.descriptorQueryable();
	}

	public IQueryResult<IArtifactKey> query(IQuery<IArtifactKey> query, IProgressMonitor monitor) {
		return delegate.query(query, monitor);
	}

	public IStatus executeBatch(IRunnableWithProgress runnable, IProgressMonitor monitor) {
		return delegate.executeBatch(runnable, monitor);
	}
}
