/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *		compeople AG (Stefan Liebig) - various ongoing maintenance
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.artifact.repository.simple;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.eclipse.core.runtime.URIUtil;
import org.osgi.framework.*;

public class Mapper {
	private Filter[] filters;
	private String[] outputStrings;

	private static final String REPOURL = "repoUrl"; //$NON-NLS-1$
	private static final String CLASSIFIER = "classifier"; //$NON-NLS-1$
	private static final String FORMAT = "format"; //$NON-NLS-1$
	private static final String ID = "id"; //$NON-NLS-1$
	private static final String VERSION = "version"; //$NON-NLS-1$

	public Mapper() {
		filters = new Filter[0];
		outputStrings = new String[0];
	}

	/**
	 * mapping rule: LDAP filter --> output value
	 * the more specific filters should be given first.
	 */
	public void initialize(BundleContext ctx, String[][] mappingRules) {
		filters = new Filter[mappingRules.length];
		outputStrings = new String[mappingRules.length];
		for (int i = 0; i < mappingRules.length; i++) {
			try {
				filters[i] = ctx.createFilter(mappingRules[i][0]);
				outputStrings[i] = mappingRules[i][1];
			} catch (InvalidSyntaxException e) {
				//TODO Neeed to process this
				e.printStackTrace();
			}
		}
	}

	public URI map(URI repositoryLocation, String classifier, String id, String version, String format) {
		Map<String, String> properties = null;
		if (format != null) {
			properties = new HashMap<String, String>(1);
			properties.put(FORMAT, format);
		}
		return map(repositoryLocation, classifier, id, version, properties);
	}

	public URI map(URI repositoryLocation, String classifier, String id, String version, Map<String, String> properties) {
		String locationString = URIUtil.toUnencodedString(repositoryLocation);
		Hashtable<String, String> values = new Hashtable<String, String>(4 + (properties == null ? 0 : properties.size()));
		if (repositoryLocation != null) {
			// currently our mapping rules assume the repo URL is not "/" terminated. 
			// This may be the case for repoURLs in the root of a URL space e.g. root of a jar file or file:/c:/
			if (locationString.endsWith("/")) //$NON-NLS-1$
				locationString = locationString.substring(0, locationString.length() - 1);
			values.put(REPOURL, locationString);
		}

		if (classifier != null)
			values.put(CLASSIFIER, classifier);

		if (id != null)
			values.put(ID, id);

		if (version != null)
			values.put(VERSION, version);

		if (properties != null)
			values.putAll(properties);

		for (int i = 0; i < filters.length; i++) {
			if (filters[i].match(values))
				return doReplacement(outputStrings[i], values);
		}
		return null;
	}

	private URI doReplacement(String pattern, Map<String, String> properties) {
		try {
			// make a case insensitive map
			final Map<String, String> localProps = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
			localProps.putAll(properties);
			StringBuffer output = new StringBuffer(pattern);
			int index = 0;
			while (index < output.length()) {
				int beginning = output.indexOf("${", index); //$NON-NLS-1$
				if (beginning == -1)
					return URIUtil.fromString(output.toString());

				int end = output.indexOf("}", beginning); //$NON-NLS-1$
				if (end == -1)
					return URIUtil.fromString(pattern);

				String varName = output.substring(beginning + 2, end);
				String varValue = localProps.get(varName);
				if (varValue == null)
					varValue = ""; //$NON-NLS-1$

				output.replace(beginning, end + 1, varValue);
				index = beginning + varValue.length();
			}
			return URIUtil.fromString(output.toString());
		} catch (URISyntaxException e) {
			return null;
		}
	}

	public String toString() {
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < filters.length; i++) {
			result.append(filters[i]).append('-').append('>').append(outputStrings[i]).append('\n');
		}
		return result.toString();
	}

	public String[][] serialize() {
		String[][] result = new String[filters.length][2];
		for (int i = 0; i < filters.length; i++) {
			result[i][0] = filters[i].toString();
			result[i][1] = outputStrings[i];
		}
		return result;
	}
}
