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

package org.eclipse.equinox.p2.ui;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.ui.*;
import org.eclipse.equinox.internal.p2.ui.actions.*;
import org.eclipse.equinox.internal.p2.ui.dialogs.*;
import org.eclipse.equinox.internal.p2.ui.viewers.IUColumnConfig;
import org.eclipse.equinox.internal.p2.ui.viewers.IUDetailsLabelProvider;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.about.InstallationPage;
import org.eclipse.ui.menus.AbstractContributionFactory;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * InstalledSoftwarePage displays a profile's IInstallableUnits in
 * an Installation Page.  Clients can use this class as the implementation
 * class for an installationPages extension.
 * 
 * @see InstallationPage
 * 
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @since 2.0
 *
 */
public class InstalledSoftwarePage extends InstallationPage implements ICopyable {

	private static final int UPDATE_ID = IDialogConstants.CLIENT_ID;
	private static final int UNINSTALL_ID = IDialogConstants.CLIENT_ID + 1;
	private static final int PROPERTIES_ID = IDialogConstants.CLIENT_ID + 2;
	private static final String BUTTON_ACTION = "org.eclipse.equinox.p2.ui.buttonAction"; //$NON-NLS-1$

	AbstractContributionFactory factory;
	Text detailsArea;
	InstalledIUGroup installedIUGroup;
	String profileId;
	Button updateButton, uninstallButton, propertiesButton;
	ProvisioningUI ui;

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IProvHelpContextIds.INSTALLED_SOFTWARE);

		ui = ProvisioningUI.getDefaultUI();
		profileId = ui.getProfileId();
		if (profileId == null) {
			IStatus status = ui.getPolicy().getNoProfileChosenStatus();
			if (status != null)
				ProvUI.reportStatus(status, StatusManager.LOG);
			Text text = new Text(parent, SWT.WRAP | SWT.READ_ONLY);
			text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			text.setText(ProvUIMessages.InstalledSoftwarePage_NoProfile);
			setControl(text);
			return;
		}

		Composite composite = new Composite(parent, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		int width = getDefaultWidth(composite);
		gd.widthHint = width;
		composite.setLayoutData(gd);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);

		// Table of installed IU's
		installedIUGroup = new InstalledIUGroup(ui, composite, JFaceResources.getDialogFont(), profileId, getColumnConfig());
		// we hook selection listeners on the viewer in createPageButtons because we
		// rely on the actions we create there getting selection events before we use
		// them to update button enablement.

		CopyUtils.activateCopy(this, installedIUGroup.getStructuredViewer().getControl());

		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.heightHint = convertHeightInCharsToPixels(ILayoutConstants.DEFAULT_DESCRIPTION_HEIGHT);
		gd.widthHint = width;

		detailsArea = new Text(composite, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY | SWT.WRAP);
		detailsArea.setBackground(detailsArea.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		detailsArea.setLayoutData(gd);

		setControl(composite);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.about.InstallationPage#createPageButtons(org.eclipse.swt.widgets.Composite)
	 */
	public void createPageButtons(Composite parent) {
		if (profileId == null)
			return;
		// For the update action, we create a custom selection provider that will interpret no
		// selection as checking for updates to everything.
		// We also override the run method to close the containing dialog
		// if we successfully try to resolve.  This is done to ensure that progress
		// is shown properly.
		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=236495
		UpdateAction updateAction = new UpdateAction(ui, new ISelectionProvider() {
			public void addSelectionChangedListener(ISelectionChangedListener listener) {
				installedIUGroup.getStructuredViewer().addSelectionChangedListener(listener);
			}

			public ISelection getSelection() {
				StructuredViewer viewer = installedIUGroup.getStructuredViewer();
				ISelection selection = viewer.getSelection();
				if (selection.isEmpty()) {
					final Object[] all = ((IStructuredContentProvider) viewer.getContentProvider()).getElements(viewer.getInput());
					return new StructuredSelection(all);
				}
				return selection;
			}

			public void removeSelectionChangedListener(ISelectionChangedListener listener) {
				installedIUGroup.getStructuredViewer().removeSelectionChangedListener(listener);
			}

			public void setSelection(ISelection selection) {
				installedIUGroup.getStructuredViewer().setSelection(selection);
			}
		}, profileId, true) {
			public void run() {
				super.run();
				if (getReturnCode() == Window.OK)
					getPageContainer().closeModalContainers();
			}
		};
		updateAction.setSkipSelectionPage(true);
		updateButton = createButton(parent, UPDATE_ID, updateAction.getText());
		updateButton.setData(BUTTON_ACTION, updateAction);
		// Uninstall action
		Action uninstallAction = new UninstallAction(ui, installedIUGroup.getStructuredViewer(), profileId) {
			public void run() {
				super.run();
				if (getReturnCode() == Window.OK)
					getPageContainer().closeModalContainers();
			}
		};
		uninstallButton = createButton(parent, UNINSTALL_ID, uninstallAction.getText());
		uninstallButton.setData(BUTTON_ACTION, uninstallAction);

		// Properties action
		PropertyDialogAction action = new PropertyDialogAction(new SameShellProvider(getShell()), installedIUGroup.getStructuredViewer());
		propertiesButton = createButton(parent, PROPERTIES_ID, action.getText());
		propertiesButton.setData(BUTTON_ACTION, action);

		// We rely on the actions getting selection events before we do, because
		// we rely on the enablement state of the action.  So we don't hook
		// the selection listener on our table until after actions are created.
		installedIUGroup.getStructuredViewer().addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateDetailsArea();
				updateEnablement();
			}

		});

		updateEnablement();
	}

	void updateDetailsArea() {
		java.util.List<IInstallableUnit> selected = installedIUGroup.getSelectedIUs();
		if (selected.size() == 1) {
			String description = selected.get(0).getProperty(IInstallableUnit.PROP_DESCRIPTION, null);
			if (description != null) {
				detailsArea.setText(description);
				return;
			}
		}
		detailsArea.setText(""); //$NON-NLS-1$
	}

	void updateEnablement() {
		if (updateButton == null || updateButton.isDisposed())
			return;
		Button[] buttons = {updateButton, uninstallButton, propertiesButton};
		for (int i = 0; i < buttons.length; i++) {
			Action action = (Action) buttons[i].getData(BUTTON_ACTION);
			if (action == null || !action.isEnabled())
				buttons[i].setEnabled(false);
			else
				buttons[i].setEnabled(true);
		}
	}

	private IUColumnConfig[] getColumnConfig() {
		return new IUColumnConfig[] {new IUColumnConfig(ProvUIMessages.ProvUI_NameColumnTitle, IUColumnConfig.COLUMN_NAME, ILayoutConstants.DEFAULT_PRIMARY_COLUMN_WIDTH), new IUColumnConfig(ProvUIMessages.ProvUI_VersionColumnTitle, IUColumnConfig.COLUMN_VERSION, ILayoutConstants.DEFAULT_SMALL_COLUMN_WIDTH), new IUColumnConfig(ProvUIMessages.ProvUI_IdColumnTitle, IUColumnConfig.COLUMN_ID, ILayoutConstants.DEFAULT_COLUMN_WIDTH)};
	}

	private int getDefaultWidth(Control control) {
		IUColumnConfig[] columns = getColumnConfig();
		int totalWidth = 0;
		for (int i = 0; i < columns.length; i++) {
			totalWidth += columns[i].getWidthInPixels(control);
		}
		return totalWidth + 20; // buffer for surrounding composites
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.p2.ui.ICopyable#copyToClipboard(org.eclipse.swt.widgets.Control)
	 */
	public void copyToClipboard(Control activeControl) {
		Object[] elements = installedIUGroup.getSelectedIUElements();
		if (elements.length == 0)
			return;
		String text = CopyUtils.getIndentedClipboardText(elements, new IUDetailsLabelProvider(null, getColumnConfig(), null));
		Clipboard clipboard = new Clipboard(PlatformUI.getWorkbench().getDisplay());
		clipboard.setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
		clipboard.dispose();
	}

	protected void buttonPressed(int buttonId) {
		switch (buttonId) {
			case UPDATE_ID :
				((Action) updateButton.getData(BUTTON_ACTION)).run();
				break;
			case UNINSTALL_ID :
				((Action) uninstallButton.getData(BUTTON_ACTION)).run();
				break;
			case PROPERTIES_ID :
				((Action) propertiesButton.getData(BUTTON_ACTION)).run();
				break;
			default :
				super.buttonPressed(buttonId);
				break;
		}
	}
}
