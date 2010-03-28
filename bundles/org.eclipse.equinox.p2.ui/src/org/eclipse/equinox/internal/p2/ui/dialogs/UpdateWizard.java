/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genuitec, LLC - added license support
 *     Sonatype, Inc. - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.dialogs;

import java.util.*;
import org.eclipse.core.runtime.Assert;
import org.eclipse.equinox.internal.p2.ui.ProvUIImages;
import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.internal.p2.ui.model.AvailableUpdateElement;
import org.eclipse.equinox.internal.p2.ui.model.IUElementListRoot;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.*;
import org.eclipse.equinox.p2.ui.LoadMetadataRepositoryJob;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.wizard.IWizardPage;

/**
 * @since 3.4
 */
public class UpdateWizard extends WizardWithLicenses {
	IInstallableUnit[] iusToReplace;
	boolean skipSelectionsPage = false;
	IUElementListRoot firstPageRoot;

	public static Collection<IInstallableUnit> getIUsToReplace(Object[] elements) {
		Set<IInstallableUnit> iusToReplace = new HashSet<IInstallableUnit>();
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] instanceof AvailableUpdateElement) {
				iusToReplace.add(((AvailableUpdateElement) elements[i]).getIUToBeUpdated());
			}
		}
		return iusToReplace;
	}

	public static IInstallableUnit[] getReplacementIUs(Object[] elements) {
		Set<IInstallableUnit> replacements = new HashSet<IInstallableUnit>();
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] instanceof AvailableUpdateElement) {
				replacements.add(((AvailableUpdateElement) elements[i]).getIU());
			}
		}
		return replacements.toArray(new IInstallableUnit[replacements.size()]);
	}

	public static Update[] makeUpdatesFromElements(Object[] elements) {
		Set<Update> updates = new HashSet<Update>();
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] instanceof AvailableUpdateElement) {
				updates.add(((AvailableUpdateElement) elements[i]).getUpdate());
			}
		}
		return updates.toArray(new Update[updates.size()]);
	}

	/**
	 * Open an update wizard.  For update wizards, the operation must have been resolved in advanced.
	 * This prevents searching for updates in the UI thread.
	 * 
	 * @param ui the provisioning UI
	 * @param operation the update operation.  Must already be resolved!
	 * @param initialSelections initial selections for the wizard (can be null)
	 * @param preloadJob a job that has been used to preload metadata repositories (can be null)
	 */
	public UpdateWizard(ProvisioningUI ui, UpdateOperation operation, Object[] initialSelections, LoadMetadataRepositoryJob preloadJob) {
		super(ui, operation, initialSelections, preloadJob);
		Assert.isLegal(operation.hasResolved(), "Cannot create an update wizard on an unresolved operation"); //$NON-NLS-1$
		setWindowTitle(ProvUIMessages.UpdateAction_UpdatesAvailableTitle);
		setDefaultPageImageDescriptor(ProvUIImages.getImageDescriptor(ProvUIImages.WIZARD_BANNER_UPDATE));
	}

	protected ISelectableIUsPage createMainPage(IUElementListRoot input, Object[] selections) {
		mainPage = new SelectableIUsPage(ui, this, getAllPossibleUpdatesRoot(), selections);
		mainPage.setTitle(ProvUIMessages.UpdateAction_UpdatesAvailableTitle);
		mainPage.setDescription(ProvUIMessages.UpdateAction_UpdatesAvailableMessage);
		((SelectableIUsPage) mainPage).updateStatus(getAllPossibleUpdatesRoot(), operation);
		return mainPage;
	}

	protected ResolutionResultsWizardPage createResolutionPage() {
		return new UpdateWizardPage(ui, this, root, (UpdateOperation) operation);
	}

	protected void initializeResolutionModelElements(Object[] selectedElements) {
		root = new IUElementListRoot();
		ArrayList<AvailableUpdateElement> list = new ArrayList<AvailableUpdateElement>(selectedElements.length);
		ArrayList<AvailableUpdateElement> selected = new ArrayList<AvailableUpdateElement>(selectedElements.length);
		for (int i = 0; i < selectedElements.length; i++) {
			if (selectedElements[i] instanceof AvailableUpdateElement) {
				AvailableUpdateElement element = (AvailableUpdateElement) selectedElements[i];
				AvailableUpdateElement newElement = new AvailableUpdateElement(root, element.getIU(), element.getIUToBeUpdated(), getProfileId(), shouldShowProvisioningPlanChildren());
				list.add(newElement);
				selected.add(newElement);
			} else if (selectedElements[i] instanceof Update) {
				Update update = (Update) selectedElements[i];
				AvailableUpdateElement newElement = new AvailableUpdateElement(root, update.replacement, update.toUpdate, getProfileId(), shouldShowProvisioningPlanChildren());
				list.add(newElement);
				selected.add(newElement);
			}
		}
		root.setChildren(list.toArray());
		planSelections = selected.toArray();
	}

	protected IResolutionErrorReportingPage createErrorReportingPage() {
		return (SelectableIUsPage) mainPage;
	}

	public void setSkipSelectionsPage(boolean skipSelectionsPage) {
		this.skipSelectionsPage = skipSelectionsPage;
	}

	public IWizardPage getStartingPage() {
		if (skipSelectionsPage) {
			// TODO see https://bugs.eclipse.org/bugs/show_bug.cgi?id=276963
			IWizardPage page = getNextPage(mainPage);
			if (page != null)
				return page;
		}
		return mainPage;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.p2.ui.dialogs.ProvisioningOperationWizard#getProfileChangeOperation(java.lang.Object[])
	 */
	protected ProfileChangeOperation getProfileChangeOperation(Object[] elements) {
		if (operation == null) {
			operation = new UpdateOperation(ui.getSession(), getIUsToReplace(elements));
			operation.setProfileId(getProfileId());
			//			operation.setRootMarkerKey(getRootMarkerKey());
			operation.setProvisioningContext(getProvisioningContext());

		} else {
			((UpdateOperation) operation).setSelectedUpdates(makeUpdatesFromElements(elements));
		}
		return operation;
	}

	private IUElementListRoot getAllPossibleUpdatesRoot() {
		if (firstPageRoot == null) {
			firstPageRoot = new IUElementListRoot();
			if (operation != null && operation instanceof UpdateOperation) {
				Update[] updates;
				if (getPolicy().getShowLatestVersionsOnly()) {
					updates = ((UpdateOperation) operation).getSelectedUpdates();
				} else {
					updates = ((UpdateOperation) operation).getPossibleUpdates();
				}
				ArrayList<AvailableUpdateElement> allPossible = new ArrayList<AvailableUpdateElement>(updates.length);
				for (int i = 0; i < updates.length; i++) {
					AvailableUpdateElement newElement = new AvailableUpdateElement(firstPageRoot, updates[i].replacement, updates[i].toUpdate, getProfileId(), shouldShowProvisioningPlanChildren());
					allPossible.add(newElement);
				}
				firstPageRoot.setChildren(allPossible.toArray());
			}
		}
		return firstPageRoot;
	}
}
