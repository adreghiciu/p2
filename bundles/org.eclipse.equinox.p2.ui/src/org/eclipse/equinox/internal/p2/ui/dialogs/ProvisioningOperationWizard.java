/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sonatype, Inc. - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.dialogs;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.equinox.internal.p2.ui.*;
import org.eclipse.equinox.internal.p2.ui.model.ElementUtils;
import org.eclipse.equinox.internal.p2.ui.model.IUElementListRoot;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.ProfileChangeOperation;
import org.eclipse.equinox.p2.ui.*;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * Common superclass for a wizard that performs a provisioning
 * operation.
 * 
 * @since 3.5
 */
public abstract class ProvisioningOperationWizard extends Wizard {

	private static final String WIZARD_SETTINGS_SECTION = "WizardSettings"; //$NON-NLS-1$

	protected ProvisioningUI ui;
	protected IUElementListRoot root;
	protected ProfileChangeOperation operation;
	protected Object[] planSelections;
	protected ISelectableIUsPage mainPage;
	protected IResolutionErrorReportingPage errorPage;
	protected ResolutionResultsWizardPage resolutionPage;
	private ProvisioningContext provisioningContext;
	protected LoadMetadataRepositoryJob repoPreloadJob;
	IStatus couldNotResolveStatus = Status.OK_STATUS; // we haven't tried and failed

	boolean waitingForOtherJobs = false;

	public ProvisioningOperationWizard(ProvisioningUI ui, ProfileChangeOperation operation, Object[] initialSelections, LoadMetadataRepositoryJob job) {
		super();
		this.ui = ui;
		initializeResolutionModelElements(initialSelections);
		this.operation = operation;
		this.repoPreloadJob = job;
		setForcePreviousAndNextButtons(true);
		setNeedsProgressMonitor(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	public void addPages() {
		mainPage = createMainPage(root, planSelections);
		addPage(mainPage);
		errorPage = createErrorReportingPage();
		if (errorPage != mainPage)
			addPage(errorPage);
		resolutionPage = createResolutionPage();
		addPage(resolutionPage);
	}

	protected abstract IResolutionErrorReportingPage createErrorReportingPage();

	protected abstract ISelectableIUsPage createMainPage(IUElementListRoot input, Object[] selections);

	protected abstract ResolutionResultsWizardPage createResolutionPage();

	public boolean performFinish() {
		return resolutionPage.performFinish();
	}

	protected LoadMetadataRepositoryJob getRepositoryPreloadJob() {
		return repoPreloadJob;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#getNextPage(org.eclipse.jface.wizard.IWizardPage)
	 */
	public IWizardPage getNextPage(IWizardPage page) {
		// If we are moving from the main page or error page, we may need to resolve before
		// advancing.
		if (page == mainPage || page == errorPage) {
			ISelectableIUsPage currentPage = (ISelectableIUsPage) page;
			// Do we need to resolve?
			if (operation == null || (operation != null && shouldRecomputePlan(currentPage))) {
				recomputePlan(getContainer());
			} else {
				// the selections have not changed from an IU point of view, but we want
				// to reinitialize the resolution model elements to ensure they are up to
				// date.
				initializeResolutionModelElements(planSelections);
			}
			IStatus status = operation.getResolutionResult();
			if (status == null || status.getSeverity() == IStatus.ERROR) {
				return errorPage;
			} else if (status.getSeverity() == IStatus.CANCEL) {
				return page;
			} else {
				return resolutionPage;
			}
		}
		return super.getNextPage(page);
	}

	/**
	 * The selections that drive the provisioning operation have changed.  We might need to
	 * change the completion state of the resolution page.
	 */
	public void operationSelectionsChanged(ISelectableIUsPage page) {
		if (resolutionPage != null) {
			// If the page selections are different than what we may have resolved
			// against, then this page is not complete.
			boolean old = resolutionPage.isPageComplete();
			if (pageSelectionsHaveChanged(page))
				resolutionPage.setPageComplete(false);
			// If the state has truly changed, update the buttons.  
			if (old != resolutionPage.isPageComplete()) {
				IWizardContainer container = getContainer();
				if (container != null && container.getCurrentPage() != null)
					getContainer().updateButtons();
			}
		}
	}

	private boolean shouldRecomputePlan(ISelectableIUsPage page) {
		boolean previouslyWaiting = waitingForOtherJobs;
		boolean previouslyCanceled = getCurrentStatus().getSeverity() == IStatus.CANCEL;
		waitingForOtherJobs = ui.hasScheduledOperations();
		return waitingForOtherJobs || previouslyWaiting || previouslyCanceled || pageSelectionsHaveChanged(page) || provisioningContextChanged();
	}

	protected boolean pageSelectionsHaveChanged(ISelectableIUsPage page) {
		HashSet<IInstallableUnit> selectedIUs = new HashSet<IInstallableUnit>();
		Object[] currentSelections = page.getCheckedIUElements();
		selectedIUs.addAll(ElementUtils.elementsToIUs(currentSelections));
		HashSet<IInstallableUnit> lastIUSelections = new HashSet<IInstallableUnit>();
		if (planSelections != null)
			lastIUSelections.addAll(ElementUtils.elementsToIUs(planSelections));
		return !(selectedIUs.equals(lastIUSelections));
	}

	private boolean provisioningContextChanged() {
		ProvisioningContext currentProvisioningContext = getProvisioningContext();
		if (currentProvisioningContext == null && provisioningContext == null)
			return false;
		if (currentProvisioningContext != null && provisioningContext != null)
			return !currentProvisioningContext.equals(provisioningContext);
		// One is null and the other is not
		return true;
	}

	protected void planChanged() {
		resolutionPage.updateStatus(root, operation);
		if (errorPage != resolutionPage) {
			IUElementListRoot newRoot = shouldUpdateErrorPageModelOnPlanChange() ? root : null;
			errorPage.updateStatus(newRoot, operation);
		}
	}

	protected boolean shouldUpdateErrorPageModelOnPlanChange() {
		return errorPage != mainPage;
	}

	protected abstract void initializeResolutionModelElements(Object[] selectedElements);

	protected ProvisioningContext getProvisioningContext() {
		return new ProvisioningContext(ui.getSession().getProvisioningAgent());
	}

	/**
	 * Recompute the provisioning plan based on the items in the IUElementListRoot and the given provisioning context.
	 * Report progress using the specified runnable context.  This method may be called before the page is created.
	 * 
	 * @param runnableContext
	 */
	public void recomputePlan(IRunnableContext runnableContext) {
		couldNotResolveStatus = Status.OK_STATUS;
		provisioningContext = getProvisioningContext();
		initializeResolutionModelElements(getOperationSelections());
		if (planSelections.length == 0) {
			operation = null;
			couldNotResolve(ProvUIMessages.ResolutionWizardPage_NoSelections);
		} else {
			operation = getProfileChangeOperation(planSelections);
			operation.setProvisioningContext(provisioningContext);
			try {
				runnableContext.run(true, true, new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) {
						operation.resolveModal(monitor);
					}
				});

			} catch (InterruptedException e) {
				// Nothing to report if thread was interrupted
			} catch (InvocationTargetException e) {
				ProvUI.handleException(e.getCause(), null, StatusManager.SHOW | StatusManager.LOG);
				couldNotResolve(null);
			}
		}
		planChanged();
	}

	/*
	 * Get the selections that drive the provisioning operation.
	 */
	protected Object[] getOperationSelections() {
		return mainPage.getCheckedIUElements();
	}

	protected abstract ProfileChangeOperation getProfileChangeOperation(Object[] elements);

	void couldNotResolve(String message) {
		if (message != null) {
			couldNotResolveStatus = new Status(IStatus.ERROR, ProvUIActivator.PLUGIN_ID, message, null);
		} else {
			couldNotResolveStatus = new Status(IStatus.ERROR, ProvUIActivator.PLUGIN_ID, ProvUIMessages.ProvisioningOperationWizard_UnexpectedFailureToResolve, null);
		}
		StatusManager.getManager().handle(couldNotResolveStatus, StatusManager.LOG);
	}

	public IStatus getCurrentStatus() {
		if (operation != null && operation.getResolutionResult() != null)
			return operation.getResolutionResult();
		return couldNotResolveStatus;
	}

	public String getDialogSettingsSectionName() {
		return getClass().getName() + "." + WIZARD_SETTINGS_SECTION; //$NON-NLS-1$
	}

	public void saveBoundsRelatedSettings() {
		IWizardPage[] pages = getPages();
		for (int i = 0; i < pages.length; i++) {
			if (pages[i] instanceof ProvisioningWizardPage)
				((ProvisioningWizardPage) pages[i]).saveBoundsRelatedSettings();
		}
	}

	protected Policy getPolicy() {
		return ui.getPolicy();
	}

	protected String getProfileId() {
		return ui.getProfileId();
	}

	protected boolean shouldShowProvisioningPlanChildren() {
		return ProvUI.getQueryContext(getPolicy()).getShowProvisioningPlanChildren();
	}

	/*
	 * Overridden to start the preload job after page control creation.
	 * This allows any listeners on repo events to be set up before a
	 * batch load occurs.  The job creator uses a property to indicate if
	 * the job needs scheduling (the client may have already completed the job
	 * before the UI was opened).
	 * (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#createPageControls(org.eclipse.swt.widgets.Composite)
	 */
	public void createPageControls(Composite pageContainer) {
		// We call this so that wizards ignore all repository eventing that occurs while the wizard is
		// open.  Otherwise, we can get an add event when a repository loads its references that we
		// don't want to respond to.  Since repo discovery events can be received asynchronously by the
		// manager, the subsequent add events generated by the manager aren't guaranteed to be synchronous,
		// even if our listener is synchronous.  Thus, we can't fine-tune
		// the "ignore" window to a specific operation.
		// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=277265#c38
		ui.signalRepositoryOperationStart();
		super.createPageControls(pageContainer);
		if (repoPreloadJob != null) {
			if (repoPreloadJob.getProperty(LoadMetadataRepositoryJob.WIZARD_CLIENT_SHOULD_SCHEDULE) != null) {
				// job has not been scheduled.  Set a listener so we can report accumulated errors and
				// schedule it.
				repoPreloadJob.addJobChangeListener(new JobChangeAdapter() {
					public void done(IJobChangeEvent e) {
						asyncReportLoadFailures();
					}
				});
				repoPreloadJob.schedule();
			} else {
				// job has been scheduled, might already be done.
				if (repoPreloadJob.getState() == Job.NONE) {
					// job is done, report failures found so far
					// do it asynchronously since we are in the middle of creation
					asyncReportLoadFailures();
				} else {
					// job is waiting, sleeping, running, report failures when
					// it's done
					repoPreloadJob.addJobChangeListener(new JobChangeAdapter() {
						public void done(IJobChangeEvent e) {
							asyncReportLoadFailures();
						}
					});
				}

			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#dispose()
	 */
	public void dispose() {
		ui.signalRepositoryOperationComplete(null, false);
		super.dispose();
	}

	void asyncReportLoadFailures() {
		if (repoPreloadJob != null && getShell() != null && !getShell().isDisposed()) {
			getShell().getDisplay().asyncExec(new Runnable() {
				public void run() {
					if (PlatformUI.isWorkbenchRunning() && getShell() != null && !getShell().isDisposed())
						repoPreloadJob.reportAccumulatedStatus();
				}
			});
		}
	}
}
