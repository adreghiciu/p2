/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.internal.repository.mirroring;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;

public interface IArtifactMirrorLog {

	// Log a status associated with a descriptor 
	public void log(IArtifactDescriptor descriptor, IStatus status);

	// Log a status
	public void log(IStatus status);

	// Notify that logging is completed & cleanup resources 
	public void close();
}
