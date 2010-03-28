/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.internal.p2.core.helpers;

import org.osgi.framework.*;

public class ServiceHelper {
	/**
	 * Returns the service described by the given arguments.  Note that this is a helper class
	 * that <b>immediately</b> ungets the service reference.  This results in a window where the
	 * system thinks the service is not in use but indeed the caller is about to use the returned 
	 * service object.  
	 * @param context
	 * @param name
	 * @return The requested service
	 */
	public static Object getService(BundleContext context, String name) {
		if (context == null)
			return null;
		ServiceReference reference = context.getServiceReference(name);
		if (reference == null)
			return null;
		Object result = context.getService(reference);
		context.ungetService(reference);
		return result;
	}

	public static Object getService(BundleContext context, String name, String filter) {
		ServiceReference[] references;
		try {
			references = context.getServiceReferences(name, filter);
		} catch (InvalidSyntaxException e) {
			// TODO Auto-generated catch block
			return null;
		}
		if (references == null || references.length == 0)
			return null;
		Object result = context.getService(references[0]);
		context.ungetService(references[0]);
		return result;
	}
}
