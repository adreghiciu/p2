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
package org.eclipse.equinox.p2.internal.repository.comparator;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.internal.repository.comparator.java.*;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeatureEntry;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.tools.comparator.IArtifactComparator;
import org.eclipse.osgi.util.NLS;

public class JarComparator implements IArtifactComparator {

	private static final String LINE_SEPARATOR = "\n"; //$NON-NLS-1$
	private static final String CLASS_EXTENSION = ".class"; //$NON-NLS-1$
	private static final String JAR_EXTENSION = ".jar"; //$NON-NLS-1$
	private static final String PROPERTIES_EXTENSION = ".properties"; //$NON-NLS-1$
	private static final String MAPPINGS_EXTENSION = ".mappings"; //$NON-NLS-1$
	private static final String PLUGIN_ID = "org.eclipse.equinox.p2.repository.tools"; //$NON-NLS-1$
	private static final String DESTINATION_ARTIFACT_PREFIX = "destinationartifact"; //$NON-NLS-1$
	private static final String SUFFIX_JAR = ".jar"; //$NON-NLS-1$
	private static final String SOURCE_ARTIFACT_PREFIX = "sourceartifact"; //$NON-NLS-1$
	private static final String OSGI_BUNDLE_CLASSIFIER = "osgi.bundle"; //$NON-NLS-1$
	private static final String FEATURE_CLASSIFIER = "org.eclipse.update.feature"; //$NON-NLS-1$

	private static final String META_INF = "meta-inf/"; //$NON-NLS-1$
	private static final String DSA_EXT = ".dsa"; //$NON-NLS-1$
	private static final String RSA_EXT = ".rsa"; //$NON-NLS-1$
	private static final String SF_EXT = ".sf"; //$NON-NLS-1$

	private String sourceLocation, destinationLocation, descriptorString;

	public IStatus compare(IArtifactRepository source, IArtifactDescriptor sourceDescriptor, IArtifactRepository destination, IArtifactDescriptor destinationDescriptor) {
		// Cache information for potential error messages
		sourceLocation = URIUtil.toUnencodedString(sourceDescriptor.getRepository().getLocation());
		destinationLocation = URIUtil.toUnencodedString(destinationDescriptor.getRepository().getLocation());
		descriptorString = sourceDescriptor.toString();

		String classifier1 = sourceDescriptor.getArtifactKey().getClassifier();
		String classifier2 = destinationDescriptor.getArtifactKey().getClassifier();
		if (!classifier1.equals(classifier2) || (!OSGI_BUNDLE_CLASSIFIER.equals(classifier1) && !FEATURE_CLASSIFIER.equals(classifier1))) {
			return Status.OK_STATUS;
		}

		File firstTempFile = null;
		File secondTempFile = null;
		try {
			firstTempFile = getLocalJarFile(source, sourceDescriptor, SOURCE_ARTIFACT_PREFIX);
			secondTempFile = getLocalJarFile(destination, destinationDescriptor, DESTINATION_ARTIFACT_PREFIX);
			if (classifier1.equals(OSGI_BUNDLE_CLASSIFIER))
				return compare(firstTempFile, secondTempFile);
			else if (classifier1.equals(FEATURE_CLASSIFIER))
				return compareFeatures(firstTempFile, secondTempFile);
		} catch (CoreException e) {
			return e.getStatus();
		} finally {
			if (firstTempFile != null)
				firstTempFile.delete();
			if (secondTempFile != null)
				secondTempFile.delete();
		}
		return Status.OK_STATUS;
	}

	public IStatus compareFeatures(File sourceFile, File destinationFile) {
		FeatureParser parser = new FeatureParser();
		Feature feature1 = parser.parse(sourceFile);
		Feature feature2 = parser.parse(destinationFile);

		MultiStatus parent = new MultiStatus(PLUGIN_ID, 0, NLS.bind(Messages.differentEntry, new String[] {descriptorString, sourceLocation, destinationLocation}), null);

		if (!feature1.getId().equals(feature2.getId()))
			parent.add(newErrorStatus(NLS.bind(Messages.featureIdsDontMatch, feature1.getId(), feature2.getId())));
		if (!feature1.getVersion().equals(feature2.getVersion()))
			parent.add(newErrorStatus(NLS.bind(Messages.featureVersionsDontMatch, feature1.getVersion(), feature2.getVersion())));

		Map<FeatureEntry, FeatureEntry> entryMap = new HashMap<FeatureEntry, FeatureEntry>();
		FeatureEntry[] entries = feature1.getEntries();
		for (int i = 0; i < entries.length; i++)
			entryMap.put(entries[i], entries[i]);

		entries = feature2.getEntries();
		if (entries.length != entryMap.size())
			parent.add(newErrorStatus(Messages.featureSize));

		for (int i = 0; i < entries.length; i++) {
			FeatureEntry firstEntry = entryMap.get(entries[i]);
			if (firstEntry == null)
				parent.add(newErrorStatus(NLS.bind(Messages.featureEntry, entries[i])));
			else {
				if (firstEntry.isOptional() != entries[i].isOptional())
					parent.add(newErrorStatus(NLS.bind(Messages.featureEntryOptional, entries[i])));
				if (firstEntry.isUnpack() != entries[i].isUnpack())
					parent.add(newErrorStatus(NLS.bind(Messages.featureEntryUnpack, entries[i])));
				if (firstEntry.isRequires() && firstEntry.getMatch() != null && !firstEntry.getMatch().equals(entries[i].getMatch()))
					parent.add(newErrorStatus(NLS.bind(Messages.featureEntryMatch, entries[i])));
				if (firstEntry.getFilter() != null && !firstEntry.getFilter().equals(entries[i].getFilter()))
					parent.add(newErrorStatus(NLS.bind(Messages.featureEntryFilter, entries[i])));
			}
		}

		return parent.getChildren().length == 0 ? Status.OK_STATUS : parent;
	}

	public IStatus compare(File sourceFile, File destinationFile) {
		ZipFile firstFile = null;
		ZipFile secondFile = null;
		try {
			firstFile = new ZipFile(sourceFile);
			secondFile = new ZipFile(destinationFile);
			final int firstFileSize = firstFile.size();
			final int secondFileSize = secondFile.size();
			MultiStatus parent = new MultiStatus(PLUGIN_ID, 0, NLS.bind(Messages.differentEntry, new String[] {descriptorString, sourceLocation, destinationLocation}), null);

			if (firstFileSize != secondFileSize) {
				parent.add(newErrorStatus(NLS.bind(Messages.differentNumberOfEntries, new String[] {descriptorString, sourceLocation, Integer.toString(firstFileSize), destinationLocation, Integer.toString(secondFileSize)})));
				return parent;
			}
			for (Enumeration<? extends ZipEntry> enumeration = firstFile.entries(); enumeration.hasMoreElements();) {
				ZipEntry entry = enumeration.nextElement();
				String entryName = entry.getName();
				final ZipEntry entry2 = secondFile.getEntry(entryName);
				IStatus result = null;
				if (!entry.isDirectory() && entry2 != null) {
					String lowerCase = entryName.toLowerCase();
					if (isSigningEntry(lowerCase)) {
						continue;
					}

					InputStream firstStream = null;
					InputStream secondStream = null;
					try {
						firstStream = new BufferedInputStream(firstFile.getInputStream(entry));
						secondStream = new BufferedInputStream(secondFile.getInputStream(entry2));
						if (lowerCase.endsWith(CLASS_EXTENSION)) {
							try {
								result = compareClasses(entryName, firstStream, entry.getSize(), secondStream, entry2.getSize());
							} catch (ClassFormatException e) {
								result = newErrorStatus(NLS.bind(Messages.differentEntry, new String[] {entryName, descriptorString, sourceLocation}), e);
							}
						} else if (lowerCase.endsWith(JAR_EXTENSION)) {
							result = compareNestedJars(firstStream, entry.getSize(), secondStream, entry2.getSize(), entryName);
						} else if (lowerCase.endsWith(PROPERTIES_EXTENSION) || lowerCase.endsWith(MAPPINGS_EXTENSION)) {
							result = compareProperties(entryName, firstStream, secondStream);
						} else if (entryName.equalsIgnoreCase(JarFile.MANIFEST_NAME)) {
							result = compareManifest(firstStream, secondStream); //MANIFEST.MF file
						} else {
							long size1 = entry.getSize();
							long size2 = entry2.getSize();
							if (size1 != size2)
								result = newErrorStatus(NLS.bind(Messages.binaryDifferentLength, new String[] {entryName, String.valueOf(Math.abs(size1 - size2))}));
							else
								result = compareBytes(entryName, firstStream, entry.getSize(), secondStream, entry2.getSize());
						}
					} finally {
						Utility.close(firstStream);
						Utility.close(secondStream);
					}
				} else if (!entry.isDirectory()) {
					// missing entry, entry2 == null
					result = newErrorStatus(NLS.bind(Messages.missingEntry, new String[] {entryName, descriptorString, sourceLocation}));
				}

				if (result != null && !result.isOK()) {
					parent.add(result);
					return parent;
				}
			}
		} catch (IOException e) {
			// missing entry
			return newErrorStatus(NLS.bind(Messages.ioexception, new String[] {sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath()}), e);
		} finally {
			Utility.close(firstFile);
			Utility.close(secondFile);
		}
		return Status.OK_STATUS;
	}

	private IStatus compareManifest(InputStream firstStream, InputStream secondStream) throws IOException {
		Manifest manifest = new Manifest(firstStream);
		Manifest manifest2 = new Manifest(secondStream);

		if (manifest == null || manifest2 == null)
			return Status.OK_STATUS;

		Attributes attributes = manifest.getMainAttributes();
		Attributes attributes2 = manifest2.getMainAttributes();
		if (attributes.size() != attributes2.size())
			return newErrorStatus(NLS.bind(Messages.manifestDifferentSize, String.valueOf(Math.abs(attributes.size() - attributes2.size()))));
		for (Entry<Object, Object> entry : attributes.entrySet()) {
			Object value2 = attributes2.get(entry.getKey());
			if (value2 == null) {
				return newErrorStatus(NLS.bind(Messages.manifestMissingEntry, entry.getKey()));
			}
			if (!value2.equals(entry.getValue())) {
				return newErrorStatus(NLS.bind(Messages.manifestDifferentValue, entry.getKey()));
			}
		}
		return Status.OK_STATUS;
	}

	private IStatus compareClasses(String entryName, InputStream stream1, long size1, InputStream stream2, long size2) throws ClassFormatException, IOException {
		Disassembler disassembler = new Disassembler();
		byte[] firstEntryClassFileBytes = Utility.getInputStreamAsByteArray(stream1, (int) size1);
		byte[] secondEntryClassFileBytes = Utility.getInputStreamAsByteArray(stream2, (int) size2);

		String contentsFile1 = disassembler.disassemble(firstEntryClassFileBytes, LINE_SEPARATOR, Disassembler.DETAILED | Disassembler.COMPACT);
		String contentsFile2 = disassembler.disassemble(secondEntryClassFileBytes, LINE_SEPARATOR, Disassembler.DETAILED | Disassembler.COMPACT);
		if (!contentsFile1.equals(contentsFile2))
			return newErrorStatus(NLS.bind(Messages.classesDifferent, entryName));
		return Status.OK_STATUS;
	}

	private IStatus compareNestedJars(InputStream stream1, long size1, InputStream stream2, long size2, String entry) throws IOException {
		File firstTempFile = getLocalJarFile(stream1, entry, size1);
		File secondTempFile = getLocalJarFile(stream2, entry, size2);

		try {
			return compare(firstTempFile, secondTempFile);
		} finally {
			if (firstTempFile != null)
				firstTempFile.delete();
			if (secondTempFile != null)
				secondTempFile.delete();
		}
	}

	private IStatus compareProperties(String entryName, InputStream stream1, InputStream stream2) {
		Properties props1 = loadProperties(stream1);
		Properties props2 = loadProperties(stream2);
		if (props1.size() != props2.size())
			return newErrorStatus(NLS.bind(Messages.propertiesSizesDifferent, entryName, String.valueOf(Math.abs(props1.size() - props2.size()))));

		props1.keys();
		for (Iterator<Object> iterator = props1.keySet().iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			if (!props2.containsKey(key))
				return newErrorStatus(NLS.bind(Messages.missingProperty, key, entryName));
			String prop1 = props1.getProperty(key);
			String prop2 = props2.getProperty(key);
			if (!prop1.equals(prop2)) {
				if (prop1.length() < 10 && prop2.length() < 10)
					return newErrorStatus(NLS.bind(Messages.differentPropertyValueFull, new String[] {entryName, key, prop1, prop2}));
				return newErrorStatus(NLS.bind(Messages.differentPropertyValueFull, entryName, key));
			}

		}
		return Status.OK_STATUS;
	}

	private IStatus compareBytes(String entryName, InputStream firstStream, long size1, InputStream secondStream, long size2) throws IOException {
		byte[] firstBytes = Utility.getInputStreamAsByteArray(firstStream, (int) size1);
		byte[] secondBytes = Utility.getInputStreamAsByteArray(secondStream, (int) size2);
		if (!Arrays.equals(firstBytes, secondBytes))
			return newErrorStatus(NLS.bind(Messages.binaryFilesDifferent, entryName));
		return Status.OK_STATUS;
	}

	private Properties loadProperties(InputStream input) {
		Properties result = new Properties();
		try {
			result.load(input);
		} catch (IOException e) {
			//ignore
		}
		return result;
	}

	private String normalize(String entryName) {
		StringBuffer buffer = new StringBuffer();
		char[] chars = entryName.toCharArray();
		for (int i = 0, max = chars.length; i < max; i++) {
			char currentChar = chars[i];
			if (!Character.isJavaIdentifierPart(currentChar)) {
				buffer.append('_');
			} else {
				buffer.append(currentChar);
			}
		}
		return String.valueOf(buffer);
	}

	private IStatus newErrorStatus(String message, Exception e) {
		return new Status(IStatus.ERROR, PLUGIN_ID, message, e);
	}

	private IStatus newErrorStatus(String message) {
		return newErrorStatus(message, null);
	}

	private File getLocalJarFile(IArtifactRepository repository, IArtifactDescriptor descriptor, String prefix) throws CoreException {
		File file = null;
		BufferedOutputStream stream = null;
		try {
			file = File.createTempFile(prefix, SUFFIX_JAR);
			stream = new BufferedOutputStream(new FileOutputStream(file));
			IStatus status = repository.getArtifact(descriptor, stream, new NullProgressMonitor());
			if (!status.isOK())
				throw new CoreException(status);
			stream.flush();
		} catch (FileNotFoundException e) {
			throw new CoreException(newErrorStatus("FileNotFoundException", e)); //$NON-NLS-1$
		} catch (IOException e) {
			throw new CoreException(newErrorStatus("IOException", e)); //$NON-NLS-1$
		} finally {
			Utility.close(stream);
		}
		return file;
	}

	private File getLocalJarFile(InputStream inputStream, String entry, long size) throws IOException {
		byte[] firstEntryClassFileBytes = Utility.getInputStreamAsByteArray(inputStream, (int) size);

		File tempFile = null;
		BufferedOutputStream stream = null;
		try {
			tempFile = File.createTempFile(SOURCE_ARTIFACT_PREFIX + normalize(entry), SUFFIX_JAR);
			stream = new BufferedOutputStream(new FileOutputStream(tempFile));
			stream.write(firstEntryClassFileBytes);
			stream.flush();
		} finally {
			Utility.close(stream);
		}
		return tempFile;
	}

	private boolean isSigningEntry(String entry) {
		return (entry.startsWith(META_INF) && (entry.endsWith(SF_EXT) || entry.endsWith(RSA_EXT) || entry.endsWith(DSA_EXT)));
	}
}
