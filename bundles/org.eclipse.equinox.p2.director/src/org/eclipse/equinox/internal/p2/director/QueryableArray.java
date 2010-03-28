/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Cloudsmith Inc. - query indexes
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.director;

import java.util.Iterator;
import java.util.List;
import org.eclipse.equinox.internal.p2.core.helpers.CollectionUtils;
import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.internal.p2.metadata.TranslationSupport;
import org.eclipse.equinox.internal.p2.metadata.index.CapabilityIndex;
import org.eclipse.equinox.internal.p2.metadata.index.IndexProvider;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.KeyWithLocale;
import org.eclipse.equinox.p2.metadata.index.IIndex;

public class QueryableArray extends IndexProvider<IInstallableUnit> {
	private final List<IInstallableUnit> dataSet;
	private IIndex<IInstallableUnit> capabilityIndex;
	private TranslationSupport translationSupport;

	public QueryableArray(IInstallableUnit[] ius) {
		dataSet = CollectionUtils.unmodifiableList(ius);
	}

	public Iterator<IInstallableUnit> everything() {
		return dataSet.iterator();
	}

	public synchronized IIndex<IInstallableUnit> getIndex(String memberName) {
		if (InstallableUnit.MEMBER_PROVIDED_CAPABILITIES.equals(memberName)) {
			if (capabilityIndex == null)
				capabilityIndex = new CapabilityIndex(dataSet.iterator());
			return capabilityIndex;
		}
		return null;
	}

	public synchronized Object getManagedProperty(Object client, String memberName, Object key) {
		if (!(client instanceof IInstallableUnit))
			return null;
		IInstallableUnit iu = (IInstallableUnit) client;
		if (InstallableUnit.MEMBER_TRANSLATED_PROPERTIES.equals(memberName)) {
			if (translationSupport == null)
				translationSupport = new TranslationSupport(this);
			return key instanceof KeyWithLocale ? translationSupport.getIUProperty(iu, (KeyWithLocale) key) : translationSupport.getIUProperty(iu, key.toString());
		}
		return null;
	}
}
