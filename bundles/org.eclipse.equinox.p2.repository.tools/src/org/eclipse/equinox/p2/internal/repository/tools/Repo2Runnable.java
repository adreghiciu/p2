/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sonatype, Inc. - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.p2.internal.repository.tools;

import java.net.URISyntaxException;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.engine.*;
import org.eclipse.equinox.internal.p2.engine.phases.Collect;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.engine.spi.ProvisioningAction;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;

/**
 * The transformer takes an existing p2 repository (local or remote), iterates over 
 * its list of IUs, and fetches all of the corresponding artifacts to a user-specified location. 
 * Once fetched, the artifacts will be in "runnable" form... that is directory-based bundles will be
 * extracted into folders and packed JAR files will be un-packed.
 * 
 * @since 1.0
 */
public class Repo2Runnable extends AbstractApplication implements IApplication {
	private static final String NATIVE_ARTIFACTS = "nativeArtifacts"; //$NON-NLS-1$
	private static final String NATIVE_TYPE = "org.eclipse.equinox.p2.native"; //$NON-NLS-1$
	private static final String PARM_OPERAND = "operand"; //$NON-NLS-1$

	protected class CollectNativesAction extends ProvisioningAction {
		public IStatus execute(Map<String, Object> parameters) {
			InstallableUnitOperand operand = (InstallableUnitOperand) parameters.get(PARM_OPERAND);
			IInstallableUnit installableUnit = operand.second();

			IArtifactRepositoryManager manager = getArtifactRepositoryManager();
			Collection<IArtifactKey> toDownload = installableUnit.getArtifacts();
			if (toDownload == null)
				return Status.OK_STATUS;

			@SuppressWarnings("unchecked")
			List<IArtifactRequest> artifactRequests = (List<IArtifactRequest>) parameters.get(NATIVE_ARTIFACTS);

			for (IArtifactKey keyToDownload : toDownload) {
				IArtifactRequest request = manager.createMirrorRequest(keyToDownload, destinationArtifactRepository, null, null);
				artifactRequests.add(request);
			}
			return Status.OK_STATUS;
		}

		public IStatus undo(Map<String, Object> parameters) {
			// nothing to do for now
			return Status.OK_STATUS;
		}
	}

	protected class CollectNativesPhase extends InstallableUnitPhase {
		public CollectNativesPhase(int weight) {
			super(NATIVE_ARTIFACTS, weight);
		}

		protected List<ProvisioningAction> getActions(InstallableUnitOperand operand) {
			IInstallableUnit unit = operand.second();
			if (unit.getTouchpointType().getId().equals(NATIVE_TYPE)) {
				return Collections.<ProvisioningAction> singletonList(new CollectNativesAction());
			}
			return null;
		}

		protected IStatus initializePhase(IProgressMonitor monitor, IProfile profile, Map<String, Object> parameters) {
			parameters.put(NATIVE_ARTIFACTS, new ArrayList<Object>());
			return null;
		}

		protected IStatus completePhase(IProgressMonitor monitor, IProfile profile, Map<String, Object> parameters) {
			@SuppressWarnings("unchecked")
			List<IArtifactRequest> artifactRequests = (List<IArtifactRequest>) parameters.get(NATIVE_ARTIFACTS);
			ProvisioningContext context = (ProvisioningContext) parameters.get(PARM_CONTEXT);
			IProvisioningAgent agent = (IProvisioningAgent) parameters.get(PARM_AGENT);
			DownloadManager dm = new DownloadManager(context, agent);
			for (IArtifactRequest request : artifactRequests) {
				dm.add(request);
			}
			return dm.start(monitor);
		}
	}

	// the list of IUs that we actually transformed... could have come from the repo 
	// or have been user-specified.
	private Collection<IInstallableUnit> processedIUs = new ArrayList<IInstallableUnit>();

	/*
	 * Perform the transformation.
	 */
	public IStatus run(IProgressMonitor monitor) throws ProvisionException {
		SubMonitor progress = SubMonitor.convert(monitor, 5);

		initializeRepos(progress);

		// ensure all the right parameters are set
		validate();

		// figure out which IUs we need to process
		collectIUs(progress.newChild(1));

		// call the engine with only the "collect" phase so all we do is download
		IProfile profile = createProfile();
		try {
			IEngine engine = (IEngine) agent.getService(IEngine.SERVICE_NAME);
			if (engine == null)
				throw new ProvisionException(Messages.exception_noEngineService);
			ProvisioningContext context = new ProvisioningContext(agent);
			IProvisioningPlan plan = engine.createPlan(profile, context);
			for (Iterator<IInstallableUnit> iterator = processedIUs.iterator(); iterator.hasNext();) {
				plan.addInstallableUnit(iterator.next());
			}
			IStatus result = engine.perform(plan, getPhaseSet(), progress.newChild(1));
			PhaseSet nativeSet = getNativePhase();
			if (nativeSet != null)
				engine.perform(plan, nativeSet, progress.newChild(1));

			// publish the metadata to a destination - if requested
			publishMetadata(progress.newChild(1));

			// return the resulting status
			return result;
		} finally {
			// cleanup by removing the temporary profile and unloading the repos which were new
			removeProfile(profile);
			finalizeRepositories();
		}
	}

	protected PhaseSet getPhaseSet() {
		return new PhaseSet(new Phase[] {new Collect(100)}) { /* nothing to override */};
	}

	protected PhaseSet getNativePhase() {
		return new PhaseSet(new Phase[] {new CollectNativesPhase(100)}) { /*nothing to override */};
	}

	/*
	 * Figure out exactly which IUs we have to process.
	 */
	private void collectIUs(IProgressMonitor monitor) throws ProvisionException {
		// if the user told us exactly which IUs to process, then just set it and return.
		if (sourceIUs != null && !sourceIUs.isEmpty()) {
			processedIUs = sourceIUs;
			return;
		}
		// get all IUs from the repos
		if (!hasMetadataSources())
			throw new ProvisionException(Messages.exception_needIUsOrNonEmptyRepo);

		Iterator<IInstallableUnit> itor = getAllIUs(getCompositeMetadataRepository(), monitor).iterator();
		while (itor.hasNext())
			processedIUs.add(itor.next());

		if (processedIUs.isEmpty())
			throw new ProvisionException(Messages.exception_needIUsOrNonEmptyRepo);
	}

	/*
	 * If there is a destination metadata repository set, then add all our transformed
	 * IUs to it. 
	 */
	private void publishMetadata(IProgressMonitor monitor) {
		// publishing the metadata is optional
		if (destinationMetadataRepository == null)
			return;
		destinationMetadataRepository.addInstallableUnits(processedIUs);
	}

	/*
	 * Return a collector over all the IUs contained in the given repository.
	 */
	private IQueryResult<IInstallableUnit> getAllIUs(IMetadataRepository repository, IProgressMonitor monitor) {
		SubMonitor progress = SubMonitor.convert(monitor, 2);
		try {
			return repository.query(QueryUtil.createIUAnyQuery(), progress.newChild(1));
		} finally {
			progress.done();
		}
	}

	/*
	 * Remove the given profile from the profile registry.
	 */
	private void removeProfile(IProfile profile) throws ProvisionException {
		IProfileRegistry registry = Activator.getProfileRegistry();
		registry.removeProfile(profile.getProfileId());
	}

	/*
	 * Create and return a new profile.
	 */
	private IProfile createProfile() throws ProvisionException {
		Map<String, String> properties = new HashMap<String, String>();
		properties.put(IProfile.PROP_CACHE, URIUtil.toFile(destinationArtifactRepository.getLocation()).getAbsolutePath());
		properties.put(IProfile.PROP_INSTALL_FOLDER, URIUtil.toFile(destinationArtifactRepository.getLocation()).getAbsolutePath());
		IProfileRegistry registry = Activator.getProfileRegistry();
		return registry.addProfile(System.currentTimeMillis() + "-" + Math.random(), properties); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext)
	 */
	public Object start(IApplicationContext context) throws Exception {
		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		processCommandLineArgs(args);
		// perform the transformation
		run(null);
		return IApplication.EXIT_OK;
	}

	/*
	 * Iterate over the command-line arguments and prepare the transformer for processing.
	 */
	private void processCommandLineArgs(String[] args) throws URISyntaxException {
		if (args == null)
			return;
		for (int i = 0; i < args.length; i++) {
			String option = args[i];
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;
			String arg = args[++i];

			if (option.equalsIgnoreCase("-source")) { //$NON-NLS-1$
				RepositoryDescriptor source = new RepositoryDescriptor();
				source.setLocation(URIUtil.fromString(arg));
				addSource(source);
			}

			if (option.equalsIgnoreCase("-destination")) { //$NON-NLS-1$
				RepositoryDescriptor destination = new RepositoryDescriptor();
				destination.setLocation(URIUtil.fromString(arg));
				addDestination(destination);
			}
		}
	}

	/*
	 * Ensure all mandatory parameters have been set. Throw an exception if there
	 * are any missing. We don't require the user to specify the artifact repository here,
	 * we will default to the ones already registered in the manager. (callers are free
	 * to add more if they wish)
	 */
	private void validate() throws ProvisionException {
		if (!hasMetadataSources() && sourceIUs == null)
			throw new ProvisionException(Messages.exception_needIUsOrNonEmptyRepo);
		if (destinationArtifactRepository == null)
			throw new ProvisionException(Messages.exception_needDestinationRepo);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	public void stop() {
		// nothing to do
	}
}
