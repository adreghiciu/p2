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

package org.eclipse.equinox.internal.p2.ui;

import org.eclipse.osgi.util.NLS;

/**
 * Message class for provisioning UI messages.  
 * 
 * @since 3.4
 */
public class ProvUIMessages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.equinox.internal.p2.ui.messages"; //$NON-NLS-1$
	static {
		// load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, ProvUIMessages.class);
	}

	public static String AcceptLicensesWizardPage_AcceptMultiple;
	public static String AcceptLicensesWizardPage_AcceptSingle;
	public static String AcceptLicensesWizardPage_ItemsLabel;
	public static String AcceptLicensesWizardPage_LicenseTextLabel;
	public static String AcceptLicensesWizardPage_NoLicensesDescription;
	public static String AcceptLicensesWizardPage_RejectMultiple;
	public static String AcceptLicensesWizardPage_RejectSingle;
	public static String AcceptLicensesWizardPage_ReviewExtraLicensesDescription;
	public static String AcceptLicensesWizardPage_ReviewLicensesDescription;
	public static String AcceptLicensesWizardPage_SingleLicenseTextLabel;
	public static String AcceptLicensesWizardPage_Title;
	public static String ApplicationInRestartDialog;
	public static String ApplyProfileChangesDialog_ApplyChanges;
	public static String ApplyProfileChangesDialog_Restart;
	public static String ApplyProfileChangesDialog_NotYet;
	public static String ColocatedRepositoryManipulator_AddSiteOperationLabel;
	public static String ColocatedRepositoryTracker_PromptForSiteLocationEdit;
	public static String ColocatedRepositoryTracker_SiteNotFoundTitle;
	public static String RevertProfilePage_ConfirmDeleteMultipleConfigs;
	public static String RevertProfilePage_ConfirmDeleteSingleConfig;
	public static String RevertProfilePage_Delete;
	public static String RevertProfilePage_DeleteMultipleConfigurationsTitle;
	public static String RevertProfilePage_DeleteSingleConfigurationTitle;
	public static String RevertProfilePage_DeleteTooltip;
	public static String RevertProfilePage_NoProfile;
	public static String RevertProfilePage_RevertLabel;
	public static String RevertProfilePage_RevertTooltip;
	public static String RevertProfilePage_CompareLabel;
	public static String RevertProfilePage_CompareTooltip;
	public static String IUCopyrightPropertyPage_NoCopyright;
	public static String IUCopyrightPropertyPage_ViewLinkLabel;
	public static String IUDetailsLabelProvider_KB;
	public static String IUDetailsLabelProvider_Bytes;
	public static String IUDetailsLabelProvider_ComputingSize;
	public static String IUDetailsLabelProvider_Unknown;
	public static String IUGeneralInfoPropertyPage_ContactLabel;
	public static String IUGeneralInfoPropertyPage_CouldNotOpenBrowser;
	public static String IUGeneralInfoPropertyPage_DescriptionLabel;
	public static String IUGeneralInfoPropertyPage_DocumentationLink;
	public static String IUGeneralInfoPropertyPage_IdentifierLabel;
	public static String IUGeneralInfoPropertyPage_NameLabel;
	public static String IUGeneralInfoPropertyPage_ProviderLabel;
	public static String IUGeneralInfoPropertyPage_VersionLabel;
	public static String IULicensePropertyPage_NoLicense;
	public static String IULicensePropertyPage_ViewLicenseLabel;
	public static String ProfileModificationAction_InvalidSelections;
	public static String ProfileModificationWizardPage_DetailsLabel;
	public static String ProfileSnapshots_Label;

	// viewer support
	public static String ProvDropAdapter_InvalidDropTarget;
	public static String ProvDropAdapter_NoIUsToDrop;
	public static String ProvDropAdapter_UnsupportedDropOperation;
	public static String ProvElementContentProvider_FetchJobTitle;

	// Provisioning operations
	public static String ProvisioningOperationRunner_CannotApplyChanges;
	public static String ProvisioningOperationWizard_UnexpectedFailureToResolve;
	public static String InstalledSoftwarePage_NoProfile;
	public static String InstallIUOperationLabel;
	public static String InstallIUOperationTask;
	public static String InstallIUCommandLabel;
	public static String InstallIUCommandTooltip;
	public static String InstallWizardPage_NoCheckboxDescription;
	public static String InstallWizardPage_Title;
	public static String PreselectedIUInstallWizard_Title;
	public static String PreselectedIUInstallWizard_Description;
	public static String UninstallDialog_UninstallMessage;
	public static String UninstallIUOperationLabel;
	public static String UninstallIUOperationTask;
	public static String UninstallIUCommandLabel;
	public static String UninstallIUCommandTooltip;
	public static String UninstallIUProgress;
	public static String UninstallWizardPage_Description;
	public static String UninstallWizardPage_Title;
	public static String UpdateIUOperationLabel;
	public static String UpdateIUOperationTask;
	public static String UpdateIUCommandLabel;
	public static String UpdateIUCommandTooltip;
	public static String UpdateIUProgress;
	public static String RefreshAction_Label;
	public static String RefreshAction_Tooltip;
	public static String RemoveColocatedRepositoryAction_Label;
	public static String RemoveColocatedRepositoryAction_Tooltip;
	public static String RevertIUCommandLabel;
	public static String RevertIUCommandTooltip;

	// Property pages
	public static String IUPropertyPage_NoIUSelected;

	public static String RepositoryDetailsLabelProvider_Disabled;
	public static String RepositoryDetailsLabelProvider_Enabled;
	// Dialog groups
	public static String RepositoryGroup_LocalRepoBrowseButton;
	public static String RepositoryGroup_ArchivedRepoBrowseButton;
	public static String RepositoryGroup_RepositoryFile;
	public static String RepositoryGroup_SelectRepositoryDirectory;
	public static String RepositoryGroup_URLRequired;
	public static String RepositoryManipulationPage_Add;
	public static String RepositoryManipulationPage_ContactingSiteMessage;
	public static String RepositoryManipulationPage_DefaultFilterString;
	public static String RepositoryManipulationPage_Description;
	public static String RepositoryManipulationPage_DisableButton;
	public static String RepositoryManipulationPage_EnableButton;
	public static String RepositoryManipulationPage_EnabledColumnTitle;
	public static String RepositoryManipulationPage_Export;
	public static String RepositoryManipulationPage_Import;
	public static String RepositoryManipulationPage_LocationColumnTitle;
	public static String RepositoryManipulationPage_NameColumnTitle;
	public static String RepositoryManipulationPage_Edit;
	public static String RepositoryManipulationPage_RefreshConnection;
	public static String RepositoryManipulationPage_RefreshOperationCanceled;
	public static String RepositoryManipulationPage_Remove;
	public static String RepositoryManipulationPage_RemoveConfirmMessage;
	public static String RepositoryManipulationPage_RemoveConfirmSingleMessage;
	public static String RepositoryManipulationPage_RemoveConfirmTitle;
	public static String RepositoryManipulationPage_TestConnectionSuccess;
	public static String RepositoryManipulationPage_TestConnectionTitle;
	public static String RepositoryManipulationPage_Title;
	public static String RepositoryManipulatorDropTarget_DragAndDropJobLabel;
	public static String RepositoryManipulatorDropTarget_DragSourceNotValid;
	public static String RepositoryNameAndLocationDialog_Title;

	public static String RepositorySelectionGroup_GenericSiteLinkTitle;
	public static String RepositorySelectionGroup_PrefPageLink;
	public static String RepositorySelectionGroup_PrefPageName;
	public static String ResolutionWizardPage_Canceled;
	public static String ResolutionWizardPage_ErrorStatus;
	public static String ResolutionWizardPage_NoSelections;
	public static String ResolutionWizardPage_WarningInfoStatus;

	// Dialogs
	public static String AddRepositoryDialog_InvalidURL;
	public static String AddRepositoryDialog_LocationLabel;
	public static String AddRepositoryDialog_NameLabel;
	public static String AddRepositoryDialog_Title;
	public static String AvailableIUGroup_LoadingRepository;
	public static String AvailableIUGroup_NoSitesConfiguredDescription;
	public static String AvailableIUGroup_NoSitesConfiguredExplanation;
	public static String ColocatedRepositoryManipulator_NoContentExplanation;
	public static String AvailableIUGroup_NoSitesExplanation;
	public static String AvailableIUsPage_AddButton;
	public static String AvailableIUsPage_AllSites;
	public static String AvailableIUsPage_Description;
	public static String AvailableIUsPage_GotoInstallInfo;
	public static String AvailableIUsPage_GotoProperties;
	public static String AvailableIUsPage_GroupByCategory;
	public static String AvailableIUsPage_HideInstalledItems;
	public static String AvailableIUsPage_LocalSites;
	public static String AvailableIUsPage_NameWithLocation;
	public static String AvailableIUsPage_NoSites;
	public static String AvailableIUsPage_RepoFilterInstructions;
	public static String AvailableIUsPage_RepoFilterLabel;
	public static String AvailableIUsPage_ResolveAllCheckbox;
	public static String AvailableIUsPage_SelectASite;
	public static String AvailableIUsPage_ShowLatestVersions;
	public static String AvailableIUsPage_Title;
	public static String AvailableIUWrapper_AllAreInstalled;
	public static String IUViewQueryContext_AllAreInstalledDescription;
	public static String ElementUtils_UpdateJobTitle;
	public static String Label_Profiles;
	public static String Label_Repositories;
	public static String LaunchUpdateManagerButton;
	public static String LoadMetadataRepositoryJob_ContactSitesProgress;
	public static String LoadMetadataRepositoryJob_SitesMissingError;
	public static String RepositoryElement_NotFound;
	public static String MetadataRepositoryElement_RepositoryLoadError;
	public static String UpdateAction_UpdatesAvailableMessage;
	public static String UpdateAction_UpdatesAvailableTitle;
	public static String PlatformUpdateTitle;
	public static String PlatformRestartMessage;
	public static String Policy_RequiresUpdateManagerMessage;
	public static String Policy_RequiresUpdateManagerTitle;
	public static String ProvUI_ErrorDuringApplyConfig;
	public static String ProvUI_InformationTitle;
	public static String ProvUI_InstallDialogError;
	public static String ProvUI_NameColumnTitle;
	public static String ProvUI_IdColumnTitle;
	public static String ProvUI_VersionColumnTitle;
	public static String ProvUI_WarningTitle;
	public static String ProvUIMessages_NotAccepted_EnterFor_0;
	public static String ProvUIMessages_SavedNotAccepted_EnterFor_0;
	public static String OptionalPlatformRestartMessage;
	public static String IUViewQueryContext_NoCategorizedItemsDescription;
	public static String QueriedElementWrapper_NoCategorizedItemsExplanation;
	public static String QueriedElementWrapper_NoItemsExplanation;
	public static String QueriedElementWrapper_SiteNotFound;
	public static String QueryableMetadataRepositoryManager_LoadRepositoryProgress;
	public static String QueryableProfileRegistry_QueryProfileProgress;
	public static String QueryableUpdates_UpdateListProgress;
	public static String SizeComputingWizardPage_SizeJobTitle;
	public static String RevertDialog_ConfigContentsLabel;
	public static String RevertDialog_ConfigsLabel;
	public static String RevertDialog_ConfirmRestartMessage;
	public static String RevertDialog_RevertOperationLabel;
	public static String RevertDialog_Title;
	public static String RollbackProfileElement_CurrentInstallation;
	public static String SelectableIUsPage_Select_All;
	public static String SelectableIUsPage_Deselect_All;

	public static String TrustCertificateDialog_Details;
	public static String TrustCertificateDialog_Title;
	// Operations
	public static String UpdateManagerCompatibility_ExportSitesTitle;
	public static String UpdateManagerCompatibility_ImportSitesTitle;
	public static String UpdateManagerCompatibility_InvalidSiteFileMessage;
	public static String UpdateManagerCompatibility_InvalidSitesTitle;
	public static String UpdateManagerCompatibility_UnableToOpenFindAndInstall;
	public static String UpdateManagerCompatibility_UnableToOpenManageConfiguration;
	public static String ServiceUI_LoginDetails;
	public static String ServiceUI_LoginRequired;
	public static String ServiceUI_unsigned_message;
	public static String ServiceUI_warning_title;
	public static String UpdateOrInstallWizardPage_Size;
	public static String Updates_Label;
	public static String UpdateSingleIUPage_SingleUpdateDescription;
	public static String UpdateWizardPage_Description;
	public static String UpdateWizardPage_Title;
	public static String UserValidationDialog_PasswordLabel;
	public static String UserValidationDialog_SavePasswordButton;
	public static String UserValidationDialog_UsernameLabel;

}
