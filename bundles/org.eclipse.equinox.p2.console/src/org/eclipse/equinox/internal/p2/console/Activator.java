/*******************************************************************************
 * Copyright (c) 2007, 2008, 2009, IBM Corporation and others. All rights reserved. This
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Composent, Inc. - additions
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.console;

import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class Activator implements BundleActivator, ServiceTrackerCustomizer {
	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.equinox.p2.console"; //$NON-NLS-1$
	private static final String PROVIDER_NAME = "org.eclipse.osgi.framework.console.CommandProvider"; //$NON-NLS-1$
	private static BundleContext context;

	private ServiceTracker agentTracker;
	private ProvCommandProvider provider;
	private ServiceRegistration providerRegistration = null;

	public static BundleContext getContext() {
		return context;
	}

	public Activator() {
		super();
	}

	public void start(BundleContext ctxt) throws Exception {
		Activator.context = ctxt;
		boolean registerCommands = true;
		try {
			Class.forName(PROVIDER_NAME);
		} catch (ClassNotFoundException e) {
			registerCommands = false;
		}

		if (registerCommands) {
			agentTracker = new ServiceTracker(context, IProvisioningAgent.SERVICE_NAME, this);
			agentTracker.open();
		}
	}

	public void stop(BundleContext ctxt) throws Exception {
		agentTracker.close();
		if (providerRegistration != null)
			providerRegistration.unregister();
		providerRegistration = null;
		Activator.context = null;
	}

	public Object addingService(ServiceReference reference) {
		BundleContext ctxt = Activator.getContext();
		IProvisioningAgent agent = (IProvisioningAgent) ctxt.getService(reference);
		provider = new ProvCommandProvider(ctxt.getProperty("eclipse.p2.profile"), agent); //$NON-NLS-1$
		providerRegistration = ctxt.registerService(PROVIDER_NAME, provider, null);
		return agent;
	}

	public void modifiedService(ServiceReference reference, Object service) {
		// nothing
	}

	public void removedService(ServiceReference reference, Object service) {
		if (providerRegistration != null)
			providerRegistration.unregister();
		providerRegistration = null;
	}

}
