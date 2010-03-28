/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.equinox.internal.p2.ui.viewers;

import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;

/**
 * Content provider for provisioning repositories. The repositories are the
 * elements and the repository children are retrieved asynchronously
 * using the IDeferredWorkbenchAdapter mechanism.
 * 
 * @since 3.4
 * 
 */
public class RepositoryContentProvider extends DeferredQueryContentProvider {

	public Object[] getChildren(final Object parent) {
		Object[] children = super.getChildren(parent);
		if (children != null)
			return children;
		if (parent instanceof IArtifactDescriptor) {
			return ((IArtifactDescriptor) parent).getProcessingSteps();
		}
		return null;
	}
}
