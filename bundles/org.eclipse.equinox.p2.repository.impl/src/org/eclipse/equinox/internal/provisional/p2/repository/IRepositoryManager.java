/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.repository;

import java.net.URI;

/**
 * The common base class for metadata and artifact repository managers.
 * <p>
 * A repository manager keeps track of a set of known repositories, and provides 
 * caching of these known repositories to avoid unnecessary loading of repositories 
 * from the disk or network.  The manager fires {@link RepositoryEvent}s when the 
 * set of known repositories changes.
 * </p>
 * <p>
 * All {@link URI} instances provided to a repository manager must be absolute.
 * </p>
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface IRepositoryManager {
	/**
	 * Constant used to indicate that all enabled repositories are of interest.
	 */
	public static final int REPOSITORIES_ALL = 0;

	/**
	 * Constant used to indicate that disabled repositories are of interest.
	 * @see #getKnownRepositories(int)
	 */
	public static final int REPOSITORIES_DISABLED = 1 << 3;

	/**
	 * Constant used to indicate that local repositories are of interest.
	 * @see #getKnownRepositories(int)
	 */
	public static final int REPOSITORIES_LOCAL = 1 << 2;

	/**
	 * Constant used to indicate that non-system repositories are of interest.
	 * @see IRepository#PROP_SYSTEM
	 * @see #getKnownRepositories(int)
	 */
	public static final int REPOSITORIES_NON_SYSTEM = 1 << 1;

	/**
	 * Constant used to indicate that system repositories are of interest.
	 * @see IRepository#PROP_SYSTEM
	 * @see #getKnownRepositories(int)
	 */
	public static final int REPOSITORIES_SYSTEM = 1 << 0;

	/**
	 * Constant used to indicate that a repository manager should only load the
	 * repository if the repository is modifiable.
	 * @see IRepository#isModifiable()
	 */
	public static final int REPOSITORY_HINT_MODIFIABLE = 1 << 0;

	/**
	 * Adds the repository at the given location to the list of repositories tracked by 
	 * this repository manager.
	 * <p>
	 * If there is a known disabled repository at the given location, it will become
	 * enabled as a result of this method. Thus the caller can be guaranteed that
	 * there is a known, enabled repository at the given location when this method returns.
	 * 
	 * @param location The absolute location of the repository to add
	 * @see #isEnabled(URI)
	 */
	public void addRepository(URI location);

	/**
	 * Returns whether a repository at the given location is in the list of repositories
	 * tracked by this repository manager.
	 * 
	 * @param location The absolute location of the repository to look for
	 * @return <code>true</code> if the repository is known to this manager,
	 * and <code>false</code> otherwise
	 */
	public boolean contains(URI location);

	/**
	 * Returns the artifact repository locations known to the repository manager.
	 * <p>
	 * Note that the repository manager does not guarantee that a valid repository
	 * exists at any of the returned locations at any particular moment in time.
	 * A subsequent attempt to load a repository at any of the given locations may
	 * or may not succeed.
	 * 
	 * @param flags an integer bit-mask indicating which repositories should be
	 * returned.  <code>REPOSITORIES_ALL</code> can be used as the mask when
	 * all enabled repositories should be returned.
	 * @return the locations of the repositories managed by this repository manager.
	 * 
	 * @see #REPOSITORIES_ALL
	 * @see #REPOSITORIES_SYSTEM
	 * @see #REPOSITORIES_NON_SYSTEM
	 * @see #REPOSITORIES_LOCAL
	 * @see #REPOSITORIES_DISABLED
	 */
	public URI[] getKnownRepositories(int flags);

	/**
	 * Returns the property associated with the repository at the given URI, 
	 * without loading the repository.
	 * <p>
	 * Note that some properties for a repository can only be
	 * determined when that repository is loaded.  This method will return <code>null</code>
	 * for such properties.  Only values for the properties that are already
	 * known by a repository manager will be returned. 
	 * <p>
	 * If a client wishes to retrieve a property value from a repository 
	 * regardless of the cost of retrieving it, the client should load the 
	 * repository and then retrieve the property from the repository itself.
	 * 
	 * @param location the absolute URI of the repository in question
	 * @param key the String key of the property desired
	 * @return the value of the property, or <code>null</code> if the repository
	 * does not exist, the value does not exist, or the property value 
	 * could not be determined without loading the repository.
	 * 
	 * @see IRepository#getProperties()
	 * @see #setRepositoryProperty(URI, String, String)
	 */
	public String getRepositoryProperty(URI location, String key);

	/**
	 * Sets the property associated with the repository at the given URI, 
	 * without loading the repository.
	 * <p>
	 * This method stores properties in a cache in the repository manager and does
	 * not write the property to the backing repository. This is useful for making
	 * repository properties available without incurring the cost of loading the repository.
	 * When the repository is loaded, it will overwrite any conflicting properties that
	 * have been set using this method.
	 * </p>
	 * <p>
	 * To persistently set a property on a repository, clients must load
	 * the repository and call {@link IRepository#setProperty(String, String)}.
	 * </p>
	 * 
	 * @param location the absolute URI of the repository in question
	 * @param key the String key of the property desired
	 * @param value the value to set the property to
	 * @see #getRepositoryProperty(URI, String)
	 * @see IRepository#setProperty(String, String)
	 */
	public void setRepositoryProperty(URI location, String key, String value);

	/**
	 * Returns the enablement value of a repository.  Disabled repositories are known
	 * to the repository manager, but are never used in the context of provisioning
	 * operations. Disabled repositories are useful as a form of bookmark to indicate that a 
	 * repository location is of interest, but not currently used.
	 * <p>
	 * Note that enablement is a property of the repository manager and not a property
	 * of the affected repository. The enablement of the repository is discarded when 
	 * a repository is removed from the repository manager.
	 * 
	 * @param location The absolute location of the repository whose enablement is requested
	 * @return <code>true</code> if the repository is enabled, and
	 * <code>false</code> if it is not enabled, or if the repository location 
	 * is not known to the repository manager.
	 * @see #REPOSITORIES_DISABLED
	 * @see #setEnabled(URI, boolean)
	 */
	public boolean isEnabled(URI location);

	/**
	 * Removes the repository at the given location from the list of
	 * repositories known to this repository manager.  The underlying
	 * repository is not deleted. This method has no effect if the given
	 * repository is not already known to this repository manager.
	 * 
	 * @param location The absolute location of the repository to remove
	 * @return <code>true</code> if a repository was removed, and 
	 * <code>false</code> otherwise.
	 */
	public boolean removeRepository(URI location);

	/**
	 * Sets the enablement of a repository. Disabled repositories are known
	 * to the repository manager, but are never used in the context of provisioning
	 * operation. Disabled repositories are useful as a form of bookmark to indicate that a 
	 * repository location is of interest, but not currently used.
	 * <p>
	 * Note that enablement is a property of the repository manager and not a property
	 * of the affected repository. The enablement of the repository is discarded when 
	 * a repository is removed from the repository manager.
	 * <p>
	 * This method has no effect if the given repository location is not known to the
	 * repository manager.
	 * 
	 * @param location The absolute location of the repository to enable or disable
	 * @param enablement <code>true</code>to enable the repository, and
	 * <code>false</code> to disable the repository
	 * @see #REPOSITORIES_DISABLED
	 * @see #isEnabled(URI)
	 */
	public void setEnabled(URI location, boolean enablement);

}
