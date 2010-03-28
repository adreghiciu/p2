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
package org.eclipse.equinox.internal.p2.core;

import java.util.EventObject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.*;
import org.eclipse.equinox.p2.core.spi.IAgentService;
import org.eclipse.osgi.framework.eventmgr.*;

/**
 * Default implementation of the {@link IProvisioningEventBus} service.
 */
public class ProvisioningEventBus implements EventDispatcher, IProvisioningEventBus, IAgentService {
	private final CopyOnWriteIdentityMap syncListeners = new CopyOnWriteIdentityMap();
	private final CopyOnWriteIdentityMap asyncListeners = new CopyOnWriteIdentityMap();
	private EventManager eventManager = new EventManager("Provisioning Event Dispatcher"); //$NON-NLS-1$

	private Object dispatchEventLock = new Object();
	/* @GuardedBy("dispatchEventLock") */
	private boolean closed = false;
	/* @GuardedBy("dispatchEventLock") */
	private int dispatchingEvents = 0;

	public ProvisioningEventBus() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus#addListener(org.eclipse.equinox.internal.provisional.p2.core.eventbus.ProvisioningListener)
	 */
	public void addListener(ProvisioningListener toAdd) {
		if (toAdd instanceof SynchronousProvisioningListener) {
			synchronized (syncListeners) {
				syncListeners.put(toAdd, toAdd);
			}
		} else {
			synchronized (asyncListeners) {
				asyncListeners.put(toAdd, toAdd);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus#removeListener(org.eclipse.equinox.internal.provisional.p2.core.eventbus.ProvisioningListener)
	 */
	public void removeListener(ProvisioningListener toRemove) {
		if (toRemove instanceof SynchronousProvisioningListener) {
			synchronized (syncListeners) {
				syncListeners.remove(toRemove);
			}
		} else {
			synchronized (asyncListeners) {
				asyncListeners.remove(toRemove);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus#publishEvent(java.util.EventObject)
	 */
	public void publishEvent(EventObject event) {
		synchronized (dispatchEventLock) {
			if (closed)
				return;
		}
		/* queue to hold set of listeners */
		ListenerQueue listeners = new ListenerQueue(eventManager);

		/* synchronize while building the listener list */
		synchronized (syncListeners) {
			/* add set of BundleContexts w/ listeners to queue */
			listeners.queueListeners(syncListeners.entrySet(), this);
			/* synchronously dispatch to populate listeners queue */
			listeners.dispatchEventSynchronous(0, event);
		}

		listeners = new ListenerQueue(eventManager);
		synchronized (asyncListeners) {
			listeners.queueListeners(asyncListeners.entrySet(), this);
			synchronized (dispatchEventLock) {
				if (!closed)
					listeners.dispatchEventAsynchronous(0, event);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus#dispatchEvent(java.lang.Object, java.lang.Object, int, java.lang.Object)
	 */
	public void dispatchEvent(Object eventListener, Object listenerObject, int eventAction, Object eventObject) {
		synchronized (dispatchEventLock) {
			if (closed)
				return;
			dispatchingEvents++;
		}
		try {
			((ProvisioningListener) eventListener).notify((EventObject) eventObject);
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "Exception during event notification", e)); //$NON-NLS-1$
		} finally {
			synchronized (dispatchEventLock) {
				dispatchingEvents--;
				if (dispatchingEvents == 0)
					dispatchEventLock.notifyAll();
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus#close()
	 */
	public void close() {
		boolean interrupted = false;
		synchronized (dispatchEventLock) {
			eventManager.close();
			closed = true;
			while (dispatchingEvents != 0) {
				try {
					dispatchEventLock.wait(30000); // we're going to cap waiting time at 30s
					break;
				} catch (InterruptedException e) {
					// keep waiting but flag interrupted
					interrupted = true;
				}
			}
		}
		if (interrupted)
			Thread.currentThread().interrupt();
	}

	/*(non-Javadoc)
	 * @see org.eclipse.equinox.p2.core.spi.IAgentService#start()
	 */
	public void start() {
		//nothing to do
	}

	/*(non-Javadoc)
	 * @see org.eclipse.equinox.p2.core.spi.IAgentService#stop()
	 */
	public void stop() {
		close();
	}
}
