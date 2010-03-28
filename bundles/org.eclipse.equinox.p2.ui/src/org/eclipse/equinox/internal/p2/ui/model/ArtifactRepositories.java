/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.model;

import org.eclipse.equinox.internal.p2.ui.*;
import org.eclipse.equinox.p2.ui.ProvisioningUI;

/**
 * Element class that represents the root of an artifact
 * repository viewer.  Its children are the artifact repositories
 * obtained using the query installed in the content provider.
 * 
 * @since 3.4
 *
 */
public class ArtifactRepositories extends RootElement {

	public ArtifactRepositories(ProvisioningUI ui, QueryableArtifactRepositoryManager queryable) {
		super(ui);
		this.queryable = queryable;
	}

	protected int getDefaultQueryType() {
		return QueryProvider.ARTIFACT_REPOS;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getLabel(java.lang.Object)
	 */
	public String getLabel(Object o) {
		return ProvUIMessages.Label_Repositories;
	}
}
