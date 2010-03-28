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
package org.eclipse.equinox.p2.internal.repository.tools;

import java.util.Hashtable;
import java.util.Map;

public class SlicingOptions {
	private boolean includeOptionalDependencies = true;
	private boolean everythingGreedy = true;
	private boolean forceFilterTo = true;
	private boolean considerStrictDependencyOnly = false;
	private boolean followOnlyFilteredRequirements = false;
	private boolean latestVersion = false;
	private Map<String, String> filter = null;

	public boolean includeOptionalDependencies() {
		return includeOptionalDependencies;
	}

	public void includeOptionalDependencies(boolean optional) {
		this.includeOptionalDependencies = optional;
	}

	public boolean isEverythingGreedy() {
		return everythingGreedy;
	}

	public void everythingGreedy(boolean greedy) {
		this.everythingGreedy = greedy;
	}

	public boolean forceFilterTo() {
		return forceFilterTo;
	}

	public void forceFilterTo(boolean forcedTo) {
		this.forceFilterTo = forcedTo;
	}

	public boolean considerStrictDependencyOnly() {
		return considerStrictDependencyOnly;
	}

	public void considerStrictDependencyOnly(boolean strict) {
		this.considerStrictDependencyOnly = strict;
	}

	public Map<String, String> getFilter() {
		if (filter == null)
			filter = new Hashtable<String, String>();
		return filter;
	}

	public void setFilter(Map<String, String> filter) {
		this.filter = filter;
	}

	public void followOnlyFilteredRequirements(boolean onlyFiltered) {
		this.followOnlyFilteredRequirements = onlyFiltered;
	}

	public boolean followOnlyFilteredRequirements() {
		return followOnlyFilteredRequirements;
	}

	public boolean latestVersionOnly() {
		return latestVersion;
	}

	public void latestVersionOnly(boolean latest) {
		this.latestVersion = latest;
	}
}
