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
package org.eclipse.equinox.p2.tests.metadata;

import java.util.*;
import junit.framework.AssertionFailedError;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class FragmentTest extends AbstractProvisioningTest {

	public void testAssociation() {
		String ID = "ui.test1";
		IInstallableUnit iu1 = createEclipseIU(ID);
		IInstallableUnit iu2 = createBundleFragment("iuFragment.test1");
		ProfileChangeRequest req = new ProfileChangeRequest(createProfile(getName()));
		createTestMetdataRepository(new IInstallableUnit[] {iu1, iu2});
		Iterator iterator = createPlanner().getProvisioningPlan(req, null, null).getAdditions().query(QueryUtil.createIUAnyQuery(), null).iterator();
		//		ResolutionHelper rh = new ResolutionHelper(new Hashtable(), null);
		//		HashSet set = new HashSet();
		//		set.add(iu1);
		//		set.add(iu2);
		//		Collection result = rh.attachCUs(set);
		for (; iterator.hasNext();) {
			IInstallableUnit iu = (IInstallableUnit) iterator.next();
			if (iu.getId().equals(ID)) {
				assertEquals(iu.getFragments().size(), 1);
				assertEquals(iu.getFragments().iterator().next().getId(), "iuFragment.test1");
			}
		}
	}

	public void testAssociation2() {
		String ID1 = "ui.test1";
		String ID3 = "ui.test3";
		IInstallableUnit iu1 = createEclipseIU(ID1);
		IInstallableUnit iu3 = createEclipseIU(ID3);
		IInstallableUnit iu2 = createBundleFragment("iuFragment.test1");
		ProfileChangeRequest req = new ProfileChangeRequest(createProfile(getName()));
		createTestMetdataRepository(new IInstallableUnit[] {iu1, iu2, iu3});
		Iterator iterator = createPlanner().getProvisioningPlan(req, null, null).getAdditions().query(QueryUtil.createIUAnyQuery(), null).iterator();
		for (; iterator.hasNext();) {
			IInstallableUnit iu = (IInstallableUnit) iterator.next();
			if (iu.getId().equals(ID1)) {
				assertEquals(iu.getFragments().size(), 1);
				assertEquals(iu.getFragments().iterator().next().getId(), "iuFragment.test1");
			}
			if (iu.getId().equals(ID3)) {
				assertEquals(iu.getFragments().size(), 1);
				assertEquals(iu.getFragments().iterator().next().getId(), "iuFragment.test1");
			}
		}
	}

	public void testTouchpointData() {
		assertEquals(createIUWithTouchpointData().getTouchpointData().size(), 1);
		assertEquals(createBundleFragment("iuFragment.test1").getTouchpointData().size(), 1);
		IInstallableUnit iu1 = createIUWithTouchpointData();
		IInstallableUnit iu2 = createBundleFragment("iuFragment.test1");
		ProfileChangeRequest req = new ProfileChangeRequest(createProfile(getName()));
		createTestMetdataRepository(new IInstallableUnit[] {iu1, iu2});
		Iterator iterator = createPlanner().getProvisioningPlan(req, null, null).getAdditions().query(QueryUtil.createIUAnyQuery(), null).iterator();
		for (; iterator.hasNext();) {
			IInstallableUnit iu = (IInstallableUnit) iterator.next();
			if (iu.getId().equals(iu1.getId()))
				assertEquals(2, iu.getTouchpointData().size());

		}
	}

	// test that host touchpoint type is the hosts one in case that there is no fragment 
	public void testTouchpointType1() {
		assertEquals("Touchpoint type", createTouchpointType("host"), getTouchpointType(createTouchpointType("host")));
	}

	// test that host touchpoint type is the hosts one regardless fragments types 
	public void testTouchpointType2() {
		assertEquals("Touchpoint type", createTouchpointType("host"), getTouchpointType(createTouchpointType("host"), createTouchpointType("fragment.1")));
	}

	// test that host touchpoint type is the one from the fragment in case of only one fragment
	public void testTouchpointType3() {
		assertEquals("Touchpoint type", createTouchpointType("fragment.1"), getTouchpointType(null, createTouchpointType("fragment.1")));
	}

	// test that host touchpoint type is the one from the fragment that has the touchpoint type set in case of more fragments 
	public void testTouchpointType4() {
		assertEquals("Touchpoint type", createTouchpointType("fragment.1"), getTouchpointType(null, createTouchpointType("fragment.1"), null));
	}

	private ITouchpointType getTouchpointType(ITouchpointType hostType, ITouchpointType... fragmentsTypes) {
		IInstallableUnit host = createIU("host", DEFAULT_VERSION, null, NO_REQUIRES, BUNDLE_CAPABILITY, NO_PROPERTIES, hostType, NO_TP_DATA, false);
		List<IInstallableUnit> ius = new ArrayList<IInstallableUnit>();
		ius.add(host);
		int i = 0;
		for (ITouchpointType fragmentType : fragmentsTypes) {
			IInstallableUnitFragment fragment = createIUFragment(host, "fragment." + ++i, DEFAULT_VERSION, NO_REQUIRES, fragmentType, NO_TP_DATA);
			ius.add(fragment);
		}
		ProfileChangeRequest req = new ProfileChangeRequest(createProfile(getName()));
		req.addAll(ius);
		createTestMetdataRepository(ius.toArray(new IInstallableUnit[ius.size()]));
		Iterator<IInstallableUnit> iterator = createPlanner().getProvisioningPlan(req, null, null).getAdditions().query(QueryUtil.createIUQuery("host"), null).iterator();
		return iterator.next().getTouchpointType();
	}

	private ITouchpointType createTouchpointType(String type) {
		return MetadataFactory.createTouchpointType(type, Version.createOSGi(1, 0, 0));
	}

	public void testFragmentCapability() {
		IInstallableUnit iu = createBundleFragment("iuFragment.test1");
		assertTrue(QueryUtil.isFragment(iu));
	}

	public void testDefaultIUCapability() {
		IInstallableUnit iu = createEclipseIU("ui.test1");
		Collection<IProvidedCapability> capabilities = iu.getProvidedCapabilities();
		for (IProvidedCapability c : capabilities) {
			if (c.getNamespace().equals(IInstallableUnit.NAMESPACE_IU_ID)) {
				assertEquals(c.getNamespace(), IInstallableUnit.NAMESPACE_IU_ID);
				assertEquals(c.getName(), iu.getId());
				return;
			}
		}
		throw new AssertionFailedError("No capability for the iu id");
	}

	public static void assertContains(Object[] objects, Object searched) {
		for (int i = 0; i < objects.length; i++) {
			if (objects[i] == searched)
				return;
		}
		throw new AssertionFailedError("The array does not contain the searched element");
	}

	public static void assertContainsWithEquals(Collection<? extends Object> objects, Object searched) {
		if (objects.contains(searched))
			return;

		throw new AssertionFailedError("The array does not contain the searched element");
	}

	private IInstallableUnit createIUWithTouchpointData() {
		ITouchpointData data = MetadataFactory.createTouchpointData(new HashMap());
		return createEclipseIU("ui.test1", DEFAULT_VERSION, NO_REQUIRES, data);
	}

	//	private IInstallableUnit createIUFragmentWithTouchpointData() {
	//		TouchpointData data = MetadataFactory.createTouchpointData(new HashMap());
	//		IInstallableUnitFragment unit = createBundleFragment("iuFragment.test1");
	//		return unit;
	//	}
}
