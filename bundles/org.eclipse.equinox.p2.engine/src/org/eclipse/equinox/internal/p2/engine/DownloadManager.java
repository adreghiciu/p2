/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     WindRiver - https://bugs.eclipse.org/bugs/show_bug.cgi?id=227372
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.engine;

import java.util.ArrayList;
import java.util.Iterator;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.engine.phases.Collect;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.metadata.expression.ExpressionUtil;
import org.eclipse.equinox.p2.query.*;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;

public class DownloadManager {
	private ProvisioningContext provContext = null;
	ArrayList<IArtifactRequest> requestsToProcess = new ArrayList<IArtifactRequest>();
	private IProvisioningAgent agent = null;

	public DownloadManager(ProvisioningContext context, IProvisioningAgent agent) {
		provContext = context;
		this.agent = agent;
	}

	/*
	 * Add the given artifact to the download queue. When it
	 * is downloaded, put it in the specified location.
	 */
	public void add(IArtifactRequest toAdd) {
		Assert.isNotNull(toAdd);
		requestsToProcess.add(toAdd);
	}

	public void add(IArtifactRequest[] toAdd) {
		Assert.isNotNull(toAdd);
		for (int i = 0; i < toAdd.length; i++) {
			add(toAdd[i]);
		}
	}

	private void filterUnfetched() {
		for (Iterator<IArtifactRequest> iterator = requestsToProcess.iterator(); iterator.hasNext();) {
			IArtifactRequest request = iterator.next();
			if (request.getResult() != null && request.getResult().isOK()) {
				iterator.remove();
			}
		}
	}

	/*
	 * Start the downloads. Return a status message indicating success or failure of the overall operation
	 */
	public IStatus start(IProgressMonitor monitor) {
		SubMonitor subMonitor = SubMonitor.convert(monitor, Messages.download_artifact, 1000);
		try {
			if (requestsToProcess.isEmpty())
				return Status.OK_STATUS;

			if (provContext == null)
				provContext = new ProvisioningContext(agent);

			IQueryable<IArtifactRepository> repoQueryable = provContext.getArtifactRepositories(subMonitor.newChild(250));
			IQuery<IArtifactRepository> all = new ExpressionMatchQuery<IArtifactRepository>(IArtifactRepository.class, ExpressionUtil.TRUE_EXPRESSION);
			IArtifactRepository[] repositories = repoQueryable.query(all, subMonitor.newChild(250)).toArray(IArtifactRepository.class);
			if (repositories.length == 0)
				return new Status(IStatus.ERROR, EngineActivator.ID, Messages.download_no_repository, new Exception(Collect.NO_ARTIFACT_REPOSITORIES_AVAILABLE));
			fetch(repositories, subMonitor.newChild(500));
			return overallStatus(monitor);
		} finally {
			subMonitor.done();
		}
	}

	private void fetch(IArtifactRepository[] repositories, IProgressMonitor mon) {
		SubMonitor monitor = SubMonitor.convert(mon, requestsToProcess.size());
		for (int i = 0; i < repositories.length && !requestsToProcess.isEmpty() && !monitor.isCanceled(); i++) {
			IArtifactRequest[] requests = getRequestsForRepository(repositories[i]);
			IStatus dlStatus = repositories[i].getArtifacts(requests, monitor.newChild(requests.length));
			if (dlStatus.getSeverity() == IStatus.CANCEL)
				return;
			filterUnfetched();
			monitor.setWorkRemaining(requestsToProcess.size());
		}
	}

	private IArtifactRequest[] getRequestsForRepository(IArtifactRepository repository) {
		ArrayList<IArtifactRequest> applicable = new ArrayList<IArtifactRequest>();
		for (IArtifactRequest request : requestsToProcess) {
			if (repository.contains(request.getArtifactKey()))
				applicable.add(request);
		}
		return applicable.toArray(new IArtifactRequest[applicable.size()]);
	}

	//	private void notifyFetched() {
	//		ProvisioningEventBus bus = (ProvisioningEventBus) ServiceHelper.getService(DownloadActivator.context, ProvisioningEventBus.class);
	//		bus.publishEvent();
	//	}

	private IStatus overallStatus(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled())
			return Status.CANCEL_STATUS;

		if (requestsToProcess.size() == 0)
			return Status.OK_STATUS;

		MultiStatus result = new MultiStatus(EngineActivator.ID, IStatus.OK, null, null);
		for (IArtifactRequest request : requestsToProcess) {
			IStatus failed = request.getResult();
			if (failed != null && !failed.isOK())
				result.add(failed);
		}
		return result;
	}
}
