/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 * 		IBM Corporation - initial API and implementation
 * 		Genuitec, LLC - added license support
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata;

import java.util.*;
import org.eclipse.equinox.internal.p2.core.helpers.CollectionUtils;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;

public class ResolvedInstallableUnit implements IInstallableUnit {
	private static IInstallableUnitFragment[] NO_IU = new IInstallableUnitFragment[0];

	private final IInstallableUnitFragment[] fragments;
	protected final IInstallableUnit original;

	public ResolvedInstallableUnit(IInstallableUnit resolved) {
		this(resolved, null);
	}

	public ResolvedInstallableUnit(IInstallableUnit resolved, IInstallableUnitFragment[] fragments) {
		this.original = resolved;
		this.fragments = fragments == null ? NO_IU : fragments;
	}

	public Collection<IInstallableUnitFragment> getFragments() {
		int fcount = fragments.length;
		if (fcount == 0)
			return CollectionUtils.emptyList();

		ArrayList<IInstallableUnitFragment> result = new ArrayList<IInstallableUnitFragment>(fcount);
		result.addAll(Arrays.asList(fragments));
		for (int i = 0; i < fcount; i++) {
			IInstallableUnit fragment = fragments[i];
			if (fragment.isResolved())
				result.addAll(fragment.getFragments());
		}
		return result;
	}

	public Collection<IArtifactKey> getArtifacts() {
		return original.getArtifacts();
	}

	public IMatchExpression<IInstallableUnit> getFilter() {
		return original.getFilter();
	}

	public String getId() {
		return original.getId();
	}

	public String getProperty(String key) {
		return original.getProperty(key);
	}

	public Map<String, String> getProperties() {
		return original.getProperties();
	}

	public String getProperty(String key, String locale) {
		return original.getProperty(key, locale);
	}

	public Collection<IProvidedCapability> getProvidedCapabilities() {
		Collection<IProvidedCapability> originalCapabilities = original.getProvidedCapabilities();
		if (fragments.length == 0)
			return originalCapabilities;

		ArrayList<IProvidedCapability> result = new ArrayList<IProvidedCapability>(originalCapabilities);
		for (int i = 0; i < fragments.length; i++)
			result.addAll(fragments[i].getProvidedCapabilities());
		return result;
	}

	public Collection<IRequirement> getRequirements() {
		Collection<IRequirement> originalCapabilities = original.getRequirements();
		if (fragments.length == 0)
			return originalCapabilities;

		ArrayList<IRequirement> result = new ArrayList<IRequirement>(originalCapabilities);
		for (int i = 0; i < fragments.length; i++)
			result.addAll(fragments[i].getRequirements());
		return result;
	}

	public Collection<IRequirement> getMetaRequirements() {
		Collection<IRequirement> originalCapabilities = original.getMetaRequirements();
		if (fragments.length == 0)
			return originalCapabilities;

		ArrayList<IRequirement> result = new ArrayList<IRequirement>(originalCapabilities);
		for (int i = 0; i < fragments.length; i++)
			result.addAll(fragments[i].getMetaRequirements());
		return result;
	}

	public Collection<ITouchpointData> getTouchpointData() {
		Collection<ITouchpointData> originalTouchpointData = original.getTouchpointData();
		if (fragments.length == 0)
			return originalTouchpointData;

		ArrayList<ITouchpointData> result = new ArrayList<ITouchpointData>(originalTouchpointData);
		for (int i = 0; i < fragments.length; i++)
			result.addAll(fragments[i].getTouchpointData());
		return result;
	}

	public ITouchpointType getTouchpointType() {
		return original.getTouchpointType();
	}

	public Version getVersion() {
		return original.getVersion();
	}

	public boolean isSingleton() {
		return original.isSingleton();
	}

	public boolean equals(Object obj) {
		//TODO This is pretty ugly....
		boolean result = original.equals(obj);
		if (result)
			return true;
		if (obj instanceof ResolvedInstallableUnit)
			return original.equals(((ResolvedInstallableUnit) obj).original);
		return false;
	}

	public int hashCode() {
		// TODO Auto-generated method stub
		return original.hashCode();
	}

	public String toString() {
		return "[R]" + original.toString(); //$NON-NLS-1$
	}

	public IInstallableUnit getOriginal() {
		return original;
	}

	public int compareTo(IInstallableUnit other) {
		int cmp = getId().compareTo(other.getId());
		if (cmp == 0)
			cmp = getVersion().compareTo(other.getVersion());
		return cmp;
	}

	public boolean isResolved() {
		return true;
	}

	public IInstallableUnit unresolved() {
		return original.unresolved();
	}

	public IUpdateDescriptor getUpdateDescriptor() {
		return original.getUpdateDescriptor();
	}

	public Collection<ILicense> getLicenses() {
		return original.getLicenses();
	}

	public Collection<ILicense> getLicenses(String locale) {
		return original.getLicenses(locale);
	}

	public ICopyright getCopyright() {
		return original.getCopyright();
	}

	public ICopyright getCopyright(String locale) {
		return original.getCopyright(locale);
	}

	public boolean satisfies(IRequirement candidate) {
		return candidate.isMatch(this);
	}

}
