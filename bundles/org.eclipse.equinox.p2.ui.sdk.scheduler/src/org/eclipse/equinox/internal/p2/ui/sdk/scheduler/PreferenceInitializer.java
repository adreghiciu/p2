/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.sdk.scheduler;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.*;
import org.eclipse.equinox.p2.core.IAgentLocation;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.engine.ProfileScope;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

	private static final String P_ENABLED = "enabled"; //$NON-NLS-1$
	private static final String UPDATE_PLUGIN_ID = "org.eclipse.update.scheduler"; //$NON-NLS-1$
	private static final String SDK_UI_PLUGIN_ID = "org.eclipse.equinox.p2.ui.sdk"; //$NON-NLS-1$

	public static void migratePreferences() {
		// Migrate preference values that were stored in alternate locations.
		// 1) migrate from instance scope (during 3.5 development) to profile
		// scope (final 3.5 format)
		// 2) if applicable, migrate from 3.4 prefs kept in a different bundle
		// 3) if applicable, migrate from 3.3 prefs known by Update Manager
		final IAgentLocation agentLocation = AutomaticUpdatePlugin.getDefault().getAgentLocation();
		if (agentLocation == null)
			return;
		Preferences pref = new ProfileScope(agentLocation, IProfileRegistry.SELF).getNode(AutomaticUpdatePlugin.PLUGIN_ID);
		try {
			if (pref.keys().length == 0) {
				// migrate preferences from instance scope to profile scope
				Preferences oldPref = new InstanceScope().getNode(AutomaticUpdatePlugin.PLUGIN_ID);
				String[] keys = oldPref.keys();
				for (int i = 0; i < keys.length; i++)
					pref.put(keys[i], oldPref.get(keys[i], "")); //$NON-NLS-1$

				if (keys.length > 0)
					pref.flush();
			}
		} catch (BackingStoreException e) {
			handleException(e, AutomaticUpdateMessages.ErrorLoadingPreferenceKeys);
		}

		// Have we migrated from 3.4 pref values?
		boolean migrated34 = pref.getBoolean(PreferenceConstants.PREF_MIGRATED_34, false);
		boolean node34exists = false;
		if (!migrated34) {
			// first look for the 3.4 automatic update preferences, which were
			// located in a different bundle than now, in the instance scope.
			Preferences instanceScope = Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE);
			try {
				node34exists = instanceScope.nodeExists(SDK_UI_PLUGIN_ID);
			} catch (BackingStoreException e1) {
				// nothing to report, assume node does not exist
			}
			if (node34exists) {
				Preferences node34 = instanceScope.node(SDK_UI_PLUGIN_ID);
				// We only migrate the preferences associated with auto update.
				// Other preferences still remain in that bundle and are handled
				// there. We don't migrate if the value was never set.
				// We use string literals rather than pref constants because we want to
				// ensure we match the 3.4 values.
				if (pref.get(PreferenceConstants.PREF_AUTO_UPDATE_ENABLED, null) == null && node34.get("enabled", null) != null) { //$NON-NLS-1$
					pref.putBoolean(PreferenceConstants.PREF_AUTO_UPDATE_ENABLED, node34.getBoolean("enabled", false)); //$NON-NLS-1$
				}
				if (pref.get(PreferenceConstants.PREF_AUTO_UPDATE_SCHEDULE, null) == null && node34.get("schedule", null) != null) { //$NON-NLS-1$
					pref.put(PreferenceConstants.PREF_AUTO_UPDATE_SCHEDULE, node34.get("schedule", //$NON-NLS-1$
							PreferenceConstants.PREF_UPDATE_ON_STARTUP));
				}
				if (pref.get(PreferenceConstants.PREF_DOWNLOAD_ONLY, null) == null && node34.get("download", null) != null) { //$NON-NLS-1$
					pref.putBoolean(PreferenceConstants.PREF_DOWNLOAD_ONLY, node34.getBoolean("download", false)); //$NON-NLS-1$
				}
				if (pref.get(PreferenceConstants.PREF_REMIND_SCHEDULE, null) == null && node34.get("remindOnSchedule", null) != null) { //$NON-NLS-1$
					pref.putBoolean(PreferenceConstants.PREF_REMIND_SCHEDULE, node34.getBoolean("remindOnSchedule", false)); //$NON-NLS-1$
				}
				if (pref.get(PreferenceConstants.PREF_REMIND_ELAPSED, null) == null && node34.get("remindElapsedTime", null) != null) { //$NON-NLS-1$
					pref.put(PreferenceConstants.PREF_REMIND_ELAPSED, node34.get("remindElapsedTime", //$NON-NLS-1$
							AutomaticUpdateMessages.AutomaticUpdateScheduler_30Minutes));
				}
			}
			// mark the pref that says we've migrated
			pref.putBoolean(PreferenceConstants.PREF_MIGRATED_34, true);
			try {
				pref.flush();
			} catch (BackingStoreException e) {
				handleException(e, AutomaticUpdateMessages.ErrorSavingPreferences);
			}
		}
		// pref used to track 3.3 migration
		// Have we initialized the auto update prefs from previous
		// releases?
		boolean autoUpdateInit = pref.getBoolean(PreferenceConstants.PREF_AUTO_UPDATE_INIT, false);

		if (!migrated34 && !autoUpdateInit) {
			// Look for the 3.3 UM automatic update preferences. We will
			// not migrate them if we already pulled values from 3.4.
			// However, we always want to turn off the UM automatic update
			// checker if it is found to be on.
			Preferences instanceScope = Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE);
			try {
				boolean updateNodeExists = instanceScope.nodeExists(UPDATE_PLUGIN_ID);
				Preferences prefUM = instanceScope.node(UPDATE_PLUGIN_ID);
				boolean enableUpdate = prefUM.getBoolean(P_ENABLED, false);
				// set p2 automatic update preference to match UM preference,
				// only if we haven't already set a value.
				if (pref.get(PreferenceConstants.PREF_AUTO_UPDATE_ENABLED, null) == null && updateNodeExists) {
					pref.putBoolean(PreferenceConstants.PREF_AUTO_UPDATE_ENABLED, enableUpdate);
				}
				// turn off UM automatic update preference if it exists
				if (updateNodeExists) {
					prefUM.putBoolean(P_ENABLED, false);
					prefUM.flush();
				}
				// mark the pref that says we migrated
				pref.putBoolean(PreferenceConstants.PREF_AUTO_UPDATE_INIT, true);
				pref.flush();
			} catch (BackingStoreException e) {
				handleException(e, AutomaticUpdateMessages.ErrorSavingClassicPreferences);
			}
		}
	}

	private static void handleException(Exception e, String message) {
		StatusManager.getManager().handle(new Status(IStatus.ERROR, AutomaticUpdatePlugin.PLUGIN_ID, 0, message, e), StatusManager.LOG);
	}

	public void initializeDefaultPreferences() {
		// initialize the default scope
		Preferences node = new DefaultScope().getNode(AutomaticUpdatePlugin.PLUGIN_ID);
		node.putBoolean(PreferenceConstants.PREF_AUTO_UPDATE_ENABLED, false);
		node.put(PreferenceConstants.PREF_AUTO_UPDATE_SCHEDULE, PreferenceConstants.PREF_UPDATE_ON_STARTUP);
		node.putBoolean(PreferenceConstants.PREF_DOWNLOAD_ONLY, false);
		node.putBoolean(PreferenceConstants.PREF_REMIND_SCHEDULE, false);
		node.put(PreferenceConstants.PREF_REMIND_ELAPSED, AutomaticUpdateMessages.AutomaticUpdateScheduler_30Minutes);
	}
}
