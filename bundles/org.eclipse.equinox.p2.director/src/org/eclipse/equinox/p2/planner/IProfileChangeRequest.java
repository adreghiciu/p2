/*******************************************************************************
 *  Copyright (c) 2010 Sonatype, Inc and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     Sonatype, Inc. - initial API and implementation
 *     IBM Corporation - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.p2.planner;

import java.util.Collection;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;

/**
 *  A profile change request is a description of a set of changes that a client
 *  would like to perform on a profile. The request is provided as input to an
 *  {@link IPlanner}, which validates which of the requested changes can be
 *  performed, and what other changes are required in order to make the profile
 *  state consistent.
 *
 *  It is important to note that a change request can only be submitted once to the planner.
 *  
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @since 2.0
 */
public interface IProfileChangeRequest {

	/**
	 * Causes the installation of the mentioned IU.
	 * @param toInstall the entity to add to the profile
	 */
	public abstract void add(IInstallableUnit toInstall);

	/**
	 * Causes the installation of all the IUs mentioned
	 * @param toInstall the installable units to be added to the profile
	 */
	public abstract void addAll(Collection<IInstallableUnit> toInstall);

	/**
	 * Requests the removal of the specified installable unit
	 * 
	 * @param toUninstall the installable units to be remove from the profile
	 */
	public abstract void remove(IInstallableUnit toUninstall);

	/**
	 * Requests the removal of all installable units in the provided collection
	 * @param toUninstall the installable units to be remove from the profile
	 */
	public abstract void removeAll(Collection<IInstallableUnit> toUninstall);

	/**
	 * Add extra requirements that must be satisfied by the planner.
	 * 
	 * @param requirements the additional requirements
	 */
	public void addExtraRequirements(Collection<IRequirement> requirements);

	/**
	 * Associate an inclusion rule with the installable unit. An inclusion rule will dictate how
	 * the installable unit is treated when its dependencies are not satisfied.
	 * <p>
	 * The provided inclusion rule must be one of the values specified in {@link ProfileInclusionRules}.
	 * </p>
	 * @param iu the installable unit to set an inclusion rule for
	 * @param inclusionRule The inclusion rule.
	 */
	public abstract void setInstallableUnitInclusionRules(IInstallableUnit iu, String inclusionRule);

	/**
	 * Removes all inclusion rules associated with the given installable unit
	 * 
	 * @param iu the installable unit to remove inclusion rules for
	 */
	public abstract void removeInstallableUnitInclusionRules(IInstallableUnit iu);

	/** 
	 * Set a global property on the profile
	 * 
	 * @param key key of the property
	 * @param value value of the property
	 */
	public abstract void setProfileProperty(String key, String value);

	/** 
	 * Remove a global property on the profile
	 * 
	 * @param key key of the property
	 */
	public abstract void removeProfileProperty(String key);

	/** 
	 * Associate a property with a given installable unit.
	 * 
	 * @param key key of the property
	 * @param value value of the property
	 */
	public abstract void setInstallableUnitProfileProperty(IInstallableUnit iu, String key, String value);

	/** 
	 * Remove a property with a given installable unit.
	 * @param iu The installable until to remove a property for
	 * @param key key of the property
	 */
	public abstract void removeInstallableUnitProfileProperty(IInstallableUnit iu, String key);

	/**
	 *  Provide the set of installable units that have been requested for addition
	 * @return a collection of the installable units to add
	 */
	public abstract Collection<IInstallableUnit> getAdditions();

	/**
	 *  Provide the set of installable units that have been requested for removal
	 * @return a collection of the installable units to remove
	 */
	public abstract Collection<IInstallableUnit> getRemovals();

}