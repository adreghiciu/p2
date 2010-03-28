/*******************************************************************************
 * Copyright (c) 2007, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource - ongoing development
 *     Sonatype, Inc. - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.dialogs;

import java.net.URI;
import org.eclipse.equinox.internal.p2.ui.*;
import org.eclipse.equinox.internal.p2.ui.model.EmptyElementExplanation;
import org.eclipse.equinox.internal.p2.ui.query.IUViewQueryContext;
import org.eclipse.equinox.internal.p2.ui.viewers.*;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class AvailableIUsPage extends ProvisioningWizardPage implements ISelectableIUsPage {

	private static final String DIALOG_SETTINGS_SECTION = "AvailableIUsPage"; //$NON-NLS-1$
	private static final String AVAILABLE_VIEW_TYPE = "AvailableViewType"; //$NON-NLS-1$
	private static final String SHOW_LATEST_VERSIONS_ONLY = "ShowLatestVersionsOnly"; //$NON-NLS-1$
	private static final String HIDE_INSTALLED_IUS = "HideInstalledContent"; //$NON-NLS-1$
	private static final String RESOLVE_ALL = "ResolveInstallWithAllSites"; //$NON-NLS-1$
	private static final String NAME_COLUMN_WIDTH = "AvailableNameColumnWidth"; //$NON-NLS-1$
	private static final String VERSION_COLUMN_WIDTH = "AvailableVersionColumnWidth"; //$NON-NLS-1$
	private static final String LIST_WEIGHT = "AvailableListSashWeight"; //$NON-NLS-1$
	private static final String DETAILS_WEIGHT = "AvailableDetailsSashWeight"; //$NON-NLS-1$
	private static final String LINKACTION = "linkAction"; //$NON-NLS-1$

	Object[] initialSelections;
	IUViewQueryContext queryContext;
	AvailableIUGroup availableIUGroup;
	Composite availableIUButtonBar;
	Link installLink;
	Button useCategoriesCheckbox, hideInstalledCheckbox, showLatestVersionsCheckbox, resolveAllCheckbox;
	SashForm sashForm;
	IUColumnConfig nameColumn, versionColumn;
	StructuredViewerProvisioningListener profileListener;
	Display display;
	int batchCount = 0;
	RepositorySelectionGroup repoSelector;
	IUDetailsGroup iuDetailsGroup;

	public AvailableIUsPage(ProvisioningUI ui, ProvisioningOperationWizard wizard) {
		super("AvailableSoftwarePage", ui, wizard); //$NON-NLS-1$
		makeQueryContext();
		setTitle(ProvUIMessages.AvailableIUsPage_Title);
		setDescription(ProvUIMessages.AvailableIUsPage_Description);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		this.display = parent.getDisplay();

		Composite composite = new Composite(parent, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		composite.setLayoutData(gd);
		setDropTarget(composite);

		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;

		composite.setLayout(layout);
		// Repo manipulation 
		createRepoArea(composite);

		sashForm = new SashForm(composite, SWT.VERTICAL);
		FillLayout fill = new FillLayout();
		sashForm.setLayout(fill);
		GridData data = new GridData(GridData.FILL_BOTH);
		sashForm.setLayoutData(data);

		// Now the available group 
		// If repositories are visible, we want to default to showing no repos.  Otherwise all.
		int filterConstant = AvailableIUGroup.AVAILABLE_NONE;
		if (!getPolicy().getRepositoriesVisible())
			filterConstant = AvailableIUGroup.AVAILABLE_ALL;
		nameColumn = new IUColumnConfig(ProvUIMessages.ProvUI_NameColumnTitle, IUColumnConfig.COLUMN_NAME, ILayoutConstants.DEFAULT_PRIMARY_COLUMN_WIDTH + 15);
		versionColumn = new IUColumnConfig(ProvUIMessages.ProvUI_VersionColumnTitle, IUColumnConfig.COLUMN_VERSION, ILayoutConstants.DEFAULT_COLUMN_WIDTH);

		getColumnWidthsFromSettings();
		availableIUGroup = new AvailableIUGroup(getProvisioningUI(), sashForm, JFaceResources.getDialogFont(), queryContext, new IUColumnConfig[] {nameColumn, versionColumn}, filterConstant);

		// Selection listeners must be registered on both the normal selection
		// events and the check mark events.  Must be done after buttons 
		// are created so that the buttons can register and receive their selection notifications before us.
		availableIUGroup.getStructuredViewer().addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateDetails();
				iuDetailsGroup.enablePropertyLink(availableIUGroup.getSelectedIUElements().length == 1);
			}
		});

		availableIUGroup.getCheckboxTreeViewer().addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				updateSelection();
			}
		});

		addViewerProvisioningListeners();

		availableIUGroup.setUseBoldFontForFilteredItems(queryContext.getViewType() != IUViewQueryContext.AVAILABLE_VIEW_FLAT);
		setDropTarget(availableIUGroup.getStructuredViewer().getControl());
		activateCopy(availableIUGroup.getStructuredViewer().getControl());

		// Details area
		iuDetailsGroup = new IUDetailsGroup(sashForm, availableIUGroup.getStructuredViewer(), SWT.DEFAULT, true);

		sashForm.setWeights(getSashWeights());

		// Controls for filtering/presentation/site selection
		Composite controlsComposite = new Composite(composite, SWT.NONE);
		layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.numColumns = 2;
		layout.makeColumnsEqualWidth = true;
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		controlsComposite.setLayout(layout);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		controlsComposite.setLayoutData(gd);

		createViewControlsArea(controlsComposite);

		initializeWidgetState();
		setControl(composite);
		composite.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				removeProvisioningListeners();
			}

		});
		Dialog.applyDialogFont(composite);
	}

	private void createViewControlsArea(Composite parent) {
		showLatestVersionsCheckbox = new Button(parent, SWT.CHECK);
		showLatestVersionsCheckbox.setText(ProvUIMessages.AvailableIUsPage_ShowLatestVersions);
		showLatestVersionsCheckbox.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				updateQueryContext();
				availableIUGroup.updateAvailableViewState();
			}

			public void widgetSelected(SelectionEvent e) {
				updateQueryContext();
				availableIUGroup.updateAvailableViewState();
			}
		});

		hideInstalledCheckbox = new Button(parent, SWT.CHECK);
		hideInstalledCheckbox.setText(ProvUIMessages.AvailableIUsPage_HideInstalledItems);
		hideInstalledCheckbox.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				updateQueryContext();
				availableIUGroup.updateAvailableViewState();
			}

			public void widgetSelected(SelectionEvent e) {
				updateQueryContext();
				availableIUGroup.updateAvailableViewState();
			}
		});

		useCategoriesCheckbox = new Button(parent, SWT.CHECK);
		useCategoriesCheckbox.setText(ProvUIMessages.AvailableIUsPage_GroupByCategory);
		useCategoriesCheckbox.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				updateQueryContext();
				availableIUGroup.updateAvailableViewState();
			}

			public void widgetSelected(SelectionEvent e) {
				updateQueryContext();
				availableIUGroup.updateAvailableViewState();
			}
		});

		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.horizontalIndent = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		installLink = createLink(parent, new Action() {
			public void runWithEvent(Event event) {
				ProvUI.openInstallationDialog(event);
			}
		}, ProvUIMessages.AvailableIUsPage_GotoInstallInfo);
		installLink.setLayoutData(gd);

		if (getPolicy().getRepositoriesVisible()) {
			// Checkbox
			resolveAllCheckbox = new Button(parent, SWT.CHECK);
			resolveAllCheckbox.setText(ProvUIMessages.AvailableIUsPage_ResolveAllCheckbox);
			gd = new GridData(SWT.FILL, SWT.FILL, true, false);
			gd.horizontalSpan = 2;
			resolveAllCheckbox.setLayoutData(gd);
		}
	}

	private void createRepoArea(Composite parent) {
		// Site controls are only available if a repository manipulator
		// is specified.
		if (getPolicy().getRepositoriesVisible()) {
			repoSelector = new RepositorySelectionGroup(getProvisioningUI(), getContainer(), parent, queryContext);
			repoSelector.addRepositorySelectionListener(new IRepositorySelectionListener() {
				public void repositorySelectionChanged(int repoChoice, URI repoLocation) {
					repoComboSelectionChanged(repoChoice, repoLocation);
				}
			});
		}
	}

	void repoComboSelectionChanged(int repoChoice, URI repoLocation) {
		if (repoChoice == AvailableIUGroup.AVAILABLE_NONE) {
			setDescription(ProvUIMessages.AvailableIUsPage_SelectASite);
		} else {
			setDescription(ProvUIMessages.AvailableIUsPage_Description);
		}
		availableIUGroup.setRepositoryFilter(repoChoice, repoLocation);
		updateSelection();
	}

	void updateSelection() {
		setPageComplete(availableIUGroup.getCheckedLeafIUs().length > 0);
		getProvisioningWizard().mainPageSelectionsChanged();
	}

	void updateQueryContext() {
		queryContext.setShowLatestVersionsOnly(showLatestVersionsCheckbox.getSelection());
		if (hideInstalledCheckbox.getSelection())
			queryContext.hideAlreadyInstalled(getProfileId());
		else {
			queryContext.showAlreadyInstalled();
			queryContext.setInstalledProfileId(getProfileId());
		}
		if (useCategoriesCheckbox.getSelection())
			queryContext.setViewType(IUViewQueryContext.AVAILABLE_VIEW_BY_CATEGORY);
		else
			queryContext.setViewType(IUViewQueryContext.AVAILABLE_VIEW_FLAT);
	}

	private Link createLink(Composite parent, IAction action, String text) {
		Link link = new Link(parent, SWT.PUSH);
		link.setText(text);

		link.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				IAction linkAction = getLinkAction(event.widget);
				if (linkAction != null) {
					linkAction.runWithEvent(event);
				}
			}
		});
		link.setToolTipText(action.getToolTipText());
		link.setData(LINKACTION, action);
		return link;
	}

	IAction getLinkAction(Widget widget) {
		Object data = widget.getData(LINKACTION);
		if (data == null || !(data instanceof IAction)) {
			return null;
		}
		return (IAction) data;
	}

	private void setDropTarget(Control control) {
		if (getPolicy().getRepositoriesVisible()) {
			DropTarget target = new DropTarget(control, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
			target.setTransfer(new Transfer[] {URLTransfer.getInstance(), FileTransfer.getInstance()});
			target.addDropListener(new RepositoryManipulatorDropTarget(getProvisioningUI(), control));
		}
	}

	private void initializeWidgetState() {
		// Set widgets according to query context
		hideInstalledCheckbox.setSelection(queryContext.getHideAlreadyInstalled());
		showLatestVersionsCheckbox.setSelection(queryContext.getShowLatestVersionsOnly());
		useCategoriesCheckbox.setSelection(queryContext.shouldGroupByCategories());
		availableIUGroup.updateAvailableViewState();
		if (initialSelections != null)
			availableIUGroup.setChecked(initialSelections);

		// Focus should go on site combo unless it's not there.  In that case, go to the filter text.
		Control focusControl = null;
		if (repoSelector != null)
			focusControl = repoSelector.getDefaultFocusControl();
		else
			focusControl = availableIUGroup.getDefaultFocusControl();
		if (focusControl != null)
			focusControl.setFocus();
		updateDetails();
		iuDetailsGroup.enablePropertyLink(availableIUGroup.getSelectedIUElements().length == 1);
		updateSelection();

		if (repoSelector != null) {
			repoSelector.setRepositorySelection(AvailableIUGroup.AVAILABLE_NONE, null);
			setDescription(ProvUIMessages.AvailableIUsPage_SelectASite);
		}

		if (resolveAllCheckbox != null) {
			IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
			IDialogSettings section = settings.getSection(DIALOG_SETTINGS_SECTION);
			String value = null;
			if (section != null)
				value = section.get(RESOLVE_ALL);
			// no section or no value in the section
			if (value == null)
				resolveAllCheckbox.setSelection(true);
			else
				resolveAllCheckbox.setSelection(section.getBoolean(RESOLVE_ALL));
		}
	}

	private void makeQueryContext() {
		// Make a local query context that is based on the default.
		IUViewQueryContext defaultQueryContext = ProvUI.getQueryContext(getPolicy());
		queryContext = new IUViewQueryContext(defaultQueryContext.getViewType());
		if (defaultQueryContext.getHideAlreadyInstalled()) {
			queryContext.hideAlreadyInstalled(getProfileId());
		} else {
			queryContext.setInstalledProfileId(getProfileId());
		}
		queryContext.setShowLatestVersionsOnly(defaultQueryContext.getShowLatestVersionsOnly());
		// Now check for saved away dialog settings
		IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(DIALOG_SETTINGS_SECTION);
		if (section != null) {
			// View by...
			try {
				if (section.get(AVAILABLE_VIEW_TYPE) != null)
					queryContext.setViewType(section.getInt(AVAILABLE_VIEW_TYPE));
			} catch (NumberFormatException e) {
				// Ignore if there actually was a value that didn't parse.  
			}
			// We no longer (in 3.5) show a view by site, so ignore any older dialog setting that
			// instructs us to do this.
			if (queryContext.getViewType() == IUViewQueryContext.AVAILABLE_VIEW_BY_REPO)
				queryContext.setViewType(IUViewQueryContext.AVAILABLE_VIEW_BY_CATEGORY);

			// Show latest versions
			if (section.get(SHOW_LATEST_VERSIONS_ONLY) != null)
				queryContext.setShowLatestVersionsOnly(section.getBoolean(SHOW_LATEST_VERSIONS_ONLY));

			// Hide installed content
			boolean hideContent = section.getBoolean(HIDE_INSTALLED_IUS);
			if (hideContent)
				queryContext.hideAlreadyInstalled(getProfileId());
			else {
				queryContext.setInstalledProfileId(getProfileId());
				queryContext.showAlreadyInstalled();
			}
		}
	}

	private void getColumnWidthsFromSettings() {
		IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(DIALOG_SETTINGS_SECTION);
		if (section != null) {
			try {
				if (section.get(NAME_COLUMN_WIDTH) != null)
					nameColumn.setWidthInPixels(section.getInt(NAME_COLUMN_WIDTH));
				if (section.get(VERSION_COLUMN_WIDTH) != null)
					versionColumn.setWidthInPixels(section.getInt(VERSION_COLUMN_WIDTH));
			} catch (NumberFormatException e) {
				// Ignore if there actually was a value that didn't parse.  
			}
		}
	}

	private int[] getSashWeights() {
		IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(DIALOG_SETTINGS_SECTION);
		if (section != null) {
			try {
				int[] weights = new int[2];
				if (section.get(LIST_WEIGHT) != null) {
					weights[0] = section.getInt(LIST_WEIGHT);
					if (section.get(DETAILS_WEIGHT) != null) {
						weights[1] = section.getInt(DETAILS_WEIGHT);
						return weights;
					}
				}
			} catch (NumberFormatException e) {
				// Ignore if there actually was a value that didn't parse.  
			}
		}
		return ILayoutConstants.IUS_TO_DETAILS_WEIGHTS;
	}

	public void saveBoundsRelatedSettings() {
		if (getShell().isDisposed())
			return;
		IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(DIALOG_SETTINGS_SECTION);
		if (section == null) {
			section = settings.addNewSection(DIALOG_SETTINGS_SECTION);
		}
		section.put(AVAILABLE_VIEW_TYPE, queryContext.getViewType());
		section.put(SHOW_LATEST_VERSIONS_ONLY, showLatestVersionsCheckbox.getSelection());
		section.put(HIDE_INSTALLED_IUS, hideInstalledCheckbox.getSelection());
		if (resolveAllCheckbox != null)
			section.put(RESOLVE_ALL, resolveAllCheckbox.getSelection());

		TreeColumn col = availableIUGroup.getCheckboxTreeViewer().getTree().getColumn(0);
		section.put(NAME_COLUMN_WIDTH, col.getWidth());
		col = availableIUGroup.getCheckboxTreeViewer().getTree().getColumn(1);
		section.put(VERSION_COLUMN_WIDTH, col.getWidth());

		int[] weights = sashForm.getWeights();
		section.put(LIST_WEIGHT, weights[0]);
		section.put(DETAILS_WEIGHT, weights[1]);
	}

	void updateDetails() {
		// First look for an empty explanation.
		Object[] elements = ((IStructuredSelection) availableIUGroup.getStructuredViewer().getSelection()).toArray();
		if (elements.length == 1 && elements[0] instanceof EmptyElementExplanation) {
			String description = ((EmptyElementExplanation) elements[0]).getDescription();
			if (description != null) {
				iuDetailsGroup.setDetailText(description);
				return;
			}
		}

		// Now look for IU's
		java.util.List<IInstallableUnit> selected = getSelectedIUs();
		if (selected.size() == 1) {
			StringBuffer result = new StringBuffer();
			String description = selected.get(0).getProperty(IInstallableUnit.PROP_DESCRIPTION, null);
			if (description != null) {
				result.append(description);
			} else {
				String name = selected.get(0).getProperty(IInstallableUnit.PROP_NAME, null);
				if (name != null)
					result.append(name);
				else
					result.append(selected.get(0).getId());
				result.append(" "); //$NON-NLS-1$
				result.append(selected.get(0).getVersion().toString());
			}

			iuDetailsGroup.setDetailText(result.toString());
			return;
		}
		iuDetailsGroup.setDetailText(""); //$NON-NLS-1$
	}

	public java.util.List<IInstallableUnit> getSelectedIUs() {
		return availableIUGroup.getSelectedIUs();
	}

	/*
	 * This method is provided only for automated testing.
	 */
	public AvailableIUGroup testGetAvailableIUGroup() {
		return availableIUGroup;
	}

	public IInstallableUnit[] getCheckedIUs() {
		return availableIUGroup.getCheckedLeafIUs();
	}

	/*
	 * Overridden so that we don't call getNextPage().
	 * We use getNextPage() to start resolving the install so
	 * we only want to do that when the next button is pressed.
	 * 
	 * (non-Javadoc)
	 * @see org.eclipse.jface.wizard.WizardPage#canFlipToNextPage()
	 */
	public boolean canFlipToNextPage() {
		return isPageComplete();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.p2.ui.dialogs.ISelectableIUsPage#getCheckedIUElements()
	 */
	public Object[] getCheckedIUElements() {
		return availableIUGroup.getCheckedLeafIUs();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.p2.ui.dialogs.ISelectableIUsPage#getSelectedIUElements()
	 */
	public Object[] getSelectedIUElements() {
		return availableIUGroup.getSelectedIUElements();
	}

	/**
	 * Set the selections to be used in this page.  This method only changes the 
	 * selections of items that are already visible.  It does not expand items
	 * or change the repository elements in order to make the selections valid.
	 * 
	 * @param elements
	 */
	public void setCheckedElements(Object[] elements) {
		if (availableIUGroup == null)
			initialSelections = elements;
		else
			availableIUGroup.setChecked(elements);
	}

	void addViewerProvisioningListeners() {
		// We might need to adjust the content of the available IU group's viewer
		// according to installation changes.  We want to be very selective about refreshing,
		// because the viewer has its own listeners installed.
		profileListener = new StructuredViewerProvisioningListener(availableIUGroup.getStructuredViewer(), ProvUIProvisioningListener.PROV_EVENT_PROFILE) {
			protected void profileAdded(String id) {
				// do nothing
			}

			protected void profileRemoved(String id) {
				// do nothing
			}

			protected void profileChanged(String id) {
				if (id.equals(getProfileId())) {
					safeRefresh();
				}
			}
		};

		ProvUI.addProvisioningListener(profileListener);
	}

	void removeProvisioningListeners() {
		if (profileListener != null) {
			ProvUI.removeProvisioningListener(profileListener);
			profileListener = null;
		}
	}

	protected String getClipboardText(Control control) {
		// The default label provider constructor uses the default column config.
		// since we passed the default column config to the available iu group,
		// we know that this label provider matches the one used there.
		return CopyUtils.getIndentedClipboardText(getSelectedIUElements(), new IUDetailsLabelProvider());
	}

	public ProvisioningContext getProvisioningContext() {
		// If the user can't manipulate repos, always resolve against everything
		if (!getPolicy().getRepositoriesVisible() || repoSelector == null) {
			return new ProvisioningContext(getProvisioningUI().getSession().getProvisioningAgent());
		}
		// Consult the checkbox to see if we should resolve against everything,
		// or use the combo to determine what to do.
		if (resolveAllCheckbox.getSelection())
			return new ProvisioningContext(getProvisioningUI().getSession().getProvisioningAgent());
		// Use the contents of the combo to determine the context
		return repoSelector.getProvisioningContext();
	}
}
