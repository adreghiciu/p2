/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.core;

import java.net.URI;
import java.util.*;
import org.eclipse.equinox.p2.core.IAgentLocation;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.spi.IAgentService;
import org.eclipse.equinox.p2.core.spi.IAgentServiceFactory;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Represents a p2 agent instance.
 */
public class ProvisioningAgent implements IProvisioningAgent, ServiceTrackerCustomizer {

	private final Map<String, Object> agentServices = Collections.synchronizedMap(new HashMap<String, Object>());
	private BundleContext context;
	private boolean stopped = false;
	private ServiceRegistration reg;
	private final Map<ServiceReference, ServiceTracker> trackers = Collections.synchronizedMap(new HashMap<ServiceReference, ServiceTracker>());

	private IProvisioningAgent installerAgent;
	private String installerProfileId;

	/**
	 * Instantiates a provisioning agent.
	 */
	public ProvisioningAgent() {
		super();
		installerAgent = this;
		installerProfileId = "_SELF_";
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.p2.core.IProvisioningAgent#getService(java.lang.String)
	 */
	public Object getService(String serviceName) {
		checkRunning();
		Object service = agentServices.get(serviceName);
		if (service != null)
			return service;
		//attempt to get factory service from service registry
		ServiceReference[] refs;
		try {
			refs = context.getServiceReferences(IAgentServiceFactory.SERVICE_NAME, "(" + IAgentServiceFactory.PROP_CREATED_SERVICE_NAME + '=' + serviceName + ')'); //$NON-NLS-1$
		} catch (InvalidSyntaxException e) {
			e.printStackTrace();
			return null;
		}
		if (refs == null || refs.length == 0)
			return null;
		//track the factory so that we can automatically remove the service when the factory goes away
		ServiceTracker tracker = new ServiceTracker(context, refs[0], this);
		tracker.open();
		IAgentServiceFactory factory = (IAgentServiceFactory) tracker.getService();
		if (factory == null) {
			tracker.close();
			return null;
		}
		service = factory.createService(this);
		if (service == null) {
			tracker.close();
			return null;
		}
		registerService(serviceName, service);
		trackers.put(refs[0], tracker);
		return service;
	}

	private synchronized void checkRunning() {
		if (stopped)
			throw new IllegalStateException("Attempt to access stopped agent: " + this); //$NON-NLS-1$
	}

	public void registerService(String serviceName, Object service) {
		checkRunning();
		agentServices.put(serviceName, service);
		if (service instanceof IAgentService)
			((IAgentService) service).start();
	}

	public void setBundleContext(BundleContext context) {
		this.context = context;
	}

	public void setLocation(URI location) {
		//treat a null location as using the currently running platform
		IAgentLocation agentLocation = null;
		if (location == null) {
			ServiceReference ref = context.getServiceReference(IAgentLocation.SERVICE_NAME);
			if (ref != null) {
				agentLocation = (IAgentLocation) context.getService(ref);
				context.ungetService(ref);
			}
		} else {
			agentLocation = new AgentLocation(location);
		}
		registerService(IAgentLocation.SERVICE_NAME, agentLocation);
	}

	public void unregisterService(String serviceName, Object service) {
		synchronized (this) {
			if (stopped)
				return;
		}
		synchronized (agentServices) {
			if (agentServices.get(serviceName) == service)
				agentServices.remove(serviceName);
		}
		if (service instanceof IAgentService)
			((IAgentService) service).stop();
	}

	public void stop() {
		//give services a chance to do their own shutdown
		for (Object service : agentServices.values()) {
			if (service instanceof IAgentService)
				((IAgentService) service).stop();
		}
		synchronized (this) {
			stopped = true;
		}
		//close all service trackers
		synchronized (trackers) {
			for (ServiceTracker t : trackers.values())
				t.close();
			trackers.clear();
		}
		if (reg != null) {
			reg.unregister();
			reg = null;
		}
	}

	public void setServiceRegistration(ServiceRegistration reg) {
		this.reg = reg;
	}

	/*(non-Javadoc)
	 * @see org.osgi.util.tracker.ServiceTrackerCustomizer#addingService(org.osgi.framework.ServiceReference)
	 */
	public Object addingService(ServiceReference reference) {
		if (stopped)
			return null;
		return context.getService(reference);
	}

	/*(non-Javadoc)
	 * @see org.osgi.util.tracker.ServiceTrackerCustomizer#modifiedService(org.osgi.framework.ServiceReference, java.lang.Object)
	 */
	public void modifiedService(ServiceReference reference, Object service) {
		//nothing to do
	}

	/*(non-Javadoc)
	 * @see org.osgi.util.tracker.ServiceTrackerCustomizer#removedService(org.osgi.framework.ServiceReference, java.lang.Object)
	 */
	public void removedService(ServiceReference reference, Object factoryService) {
		if (stopped)
			return;
		String serviceName = (String) reference.getProperty(IAgentServiceFactory.PROP_CREATED_SERVICE_NAME);
		if (serviceName == null)
			return;
		Object registered = agentServices.get(serviceName);
		if (registered == null)
			return;
		if (FrameworkUtil.getBundle(registered.getClass()) == FrameworkUtil.getBundle(factoryService.getClass())) {
			//the service we are holding is going away
			unregisterService(serviceName, registered);
			ServiceTracker toRemove = trackers.remove(reference);
			if (toRemove != null)
				toRemove.close();
		}
	}

	public void setParent(IProvisioningAgent installerAgent) {
		this.installerAgent = installerAgent;
	}

	public void setInstallerProfileId(String profileId) {
		this.installerProfileId = profileId;
	}

	public IProvisioningAgent getParent() {
		return installerAgent;
	}

	public String getInstallerProfileId() {
		return installerProfileId;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof IProvisioningAgent))
			return false;
		IAgentLocation thisLocation = (IAgentLocation) getService(IAgentLocation.SERVICE_NAME);
		IAgentLocation otherLocation = (IAgentLocation) ((IProvisioningAgent) obj).getService(IAgentLocation.SERVICE_NAME);
		if (thisLocation == null || otherLocation == null || (thisLocation == null && otherLocation == null))
			return false;
		return thisLocation.getRootLocation().equals(otherLocation.getRootLocation());
	}

	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return super.hashCode();
	}
}
