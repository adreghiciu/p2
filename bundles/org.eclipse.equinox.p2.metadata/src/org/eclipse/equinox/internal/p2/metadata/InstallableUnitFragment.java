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
package org.eclipse.equinox.internal.p2.metadata;

import java.util.Collection;
import java.util.List;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IRequirement;

public class InstallableUnitFragment extends InstallableUnit implements IInstallableUnitFragment {

	private Collection<IRequirement> hostRequirements;

	public InstallableUnitFragment() {
		super();
	}

	public void setHost(Collection<IRequirement> hostRequirements) {
		if (hostRequirements == null)
			return;
		this.hostRequirements = hostRequirements;
		addRequiredCapability(hostRequirements);
	}

	private void addRequiredCapability(Collection<IRequirement> toAdd) {
		List<IRequirement> current = super.getRequirements();
		int currSize = current.size();
		IRequirement[] result = new IRequirement[currSize + toAdd.size()];
		int i = 0;
		for (; i < currSize; ++i)
			result[i] = current.get(i);
		for (IRequirement requirement : toAdd)
			result[i++] = requirement;
		setRequiredCapabilities(result);
	}

	public Collection<IRequirement> getHost() {
		return hostRequirements;
	}

	public Object getMember(String memberName) {
		return "host".equals(memberName) ? hostRequirements : super.getMember(memberName); //$NON-NLS-1$
	}
}
