/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.query;

import java.net.URI;
import org.eclipse.equinox.internal.p2.ui.ProvUI;
import org.eclipse.equinox.internal.p2.ui.model.ArtifactRepositoryElement;
import org.eclipse.equinox.internal.p2.ui.model.QueriedElementWrapper;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.ui.ProvisioningUI;

/**
 * ElementWrapper that wraps a URI with an ArtifactRepositoryElement.
 * 
 * @since 3.4
 */
public class ArtifactRepositoryElementWrapper extends QueriedElementWrapper {

	public ArtifactRepositoryElementWrapper(IQueryable<URI> queryable, Object parent) {
		super(queryable, parent);
	}

	/**
	 * Accepts a result that matches the query criteria.
	 * 
	 * @param match an object matching the query
	 * @return <code>true</code> if the query should continue,
	 * or <code>false</code> to indicate the query should stop.
	 */
	protected boolean shouldWrap(Object match) {
		if ((match instanceof URI))
			return true;
		return false;
	}

	/**
	 * Transforms the item to a UI element
	 */
	protected Object wrap(Object item) {
		return super.wrap(new ArtifactRepositoryElement(parent, (URI) item, ProvUI.getArtifactRepositoryManager(ProvisioningUI.getDefaultUI().getSession()).isEnabled((URI) item)));
	}
}
