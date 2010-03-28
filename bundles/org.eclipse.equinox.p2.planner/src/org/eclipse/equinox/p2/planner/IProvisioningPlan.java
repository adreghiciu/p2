/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.planner;

import java.util.Collection;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryable;

/**
 * A provisioning plan describes a proposed set of changes to a profile. The
 * proposed changes may represent a valid and consistent set of changes, or it
 * may represent a set of changes that would cause errors if executed. In this
 * case the plan contains information about the severity and explanation for the
 * problems.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @since 2.0
 */
public interface IProvisioningPlan {

	/**
	 * Returns the proposed set of installable units to be added to the profile.
	 * 
	 * @return The proposed profile additions
	 */
	public IQueryable<IInstallableUnit> getAdditions();

	/**
	 * Returns the provisioning context in which this plan was created.
	 * 
	 * @return The plan's provisioning context
	 */
	public ProvisioningContext getContext();

	/**
	 * Returns the proposed set of installable units to be removed from this profile.
	 * 
	 * @return The proposed profile removals.
	 */
	public IQueryable<IInstallableUnit> getRemovals();

	/**
	 * Returns the overall plan status. The severity of this status indicates
	 * whether the plan can be successfully executed or not:
	 * <ul>
	 * <li>A status of {@link IStatus#OK} indicates that the plan can be executed successfully.</li>
	 * <li>A status of {@link IStatus#INFO} or {@link IStatus#WARNING} indicates
	 * that the plan can be executed but may cause problems.</li>
	 * <li>A status of {@link IStatus#ERROR} indicates that the plan cannot be executed
	 * successfully.</li>
	 * <li>A status of {@link IStatus#CANCEL} indicates that the plan computation was
	 * canceled and is incomplete. A canceled plan cannot be executed.</li>
	 * </ul>
	 * 
	 * @return The overall plan status.
	 */
	public IStatus getStatus();

	/**
	 * Return the complete set of installable units representing the installation where the profile change request has been satisfied.
	 * @return the set of installable unit representing the future state of the system
	 */
	public Collection<IInstallableUnit> getFutureState();
}