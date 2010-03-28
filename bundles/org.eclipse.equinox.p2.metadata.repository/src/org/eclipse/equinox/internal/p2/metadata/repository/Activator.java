/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Cloudsmith Inc - additional implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata.repository;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	public static final String ID = "org.eclipse.equinox.p2.metadata.repository"; //$NON-NLS-1$
	public static final String REPO_PROVIDER_XPT = ID + '.' + "metadataRepositories"; //$NON-NLS-1$

	private static BundleContext bundleContext;

	public static BundleContext getContext() {
		return bundleContext;
	}

	public void start(BundleContext aContext) throws Exception {
		bundleContext = aContext;
	}

	public void stop(BundleContext aContext) throws Exception {
		bundleContext = null;
	}
}
