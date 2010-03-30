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
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
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

	/**
	 * Test that fragments does not affect the solution when the fragments do not have requirements.
	 * This mean that having a fragment that has a host requirement that cannot be satisfied it will not determine solver to fail to find a solution.
	 * This case is valid only in the context that bug #222157 is solved, meaning that host requirements are not supposed to be expressed also 
	 * as requirements (which was the case described also in #222158) 
	 * 
	 * This test does not include the host IU in request or metadata repository.
	 */
	public void testFragmentsOptionality1() {
		testFragmentsOptionality(false);
	}

	/**
	 * Test that fragments does not affect the solution when the fragments do not have requirements.
	 * This mean that having a fragment that has a host requirement that cannot be satisfied it will not determine solver to fail to find a solution.
	 * This case is valid only in the context that bug #222157 is solved, meaning that host requirements are not supposed to be expressed also 
	 * as requirements (which was the case described also in #222158) 
	 * 
	 * This test does not include the host IU in request but it includes it in metadata repository.
	 */
	public void testFragmentsOptionality2() {
		testFragmentsOptionality(true);
	}

	private void testFragmentsOptionality(boolean includeHostIUInRepository) {
		String ID1 = "iu.test1";
		String ID2 = "iu.test2";
		String ID3 = "iuFragment.test3";
		IInstallableUnit iu1 = createEclipseIU(ID1);
		IInstallableUnit iu2 = createEclipseIU(ID2);
		IInstallableUnit iu3 = createIUFragment(iu1, ID3, DEFAULT_VERSION);

		ProfileChangeRequest req = new ProfileChangeRequest(createProfile(getName()));
		req.addAll(Arrays.asList(iu2, iu3));

		if (includeHostIUInRepository) {
			createTestMetdataRepository(new IInstallableUnit[] {iu1, iu2, iu3});
		} else {
			createTestMetdataRepository(new IInstallableUnit[] {iu2, iu3});
		}

		IProvisioningPlan provisioningPlan = createPlanner().getProvisioningPlan(req, null, null);

		{
			Iterator<IInstallableUnit> iterator = provisioningPlan.getAdditions().query(QueryUtil.createIUQuery(ID1), null).iterator();
			assertTrue("IU " + ID1 + " not expected as part of solution", !iterator.hasNext());
		}
		{
			Iterator<IInstallableUnit> iterator = provisioningPlan.getAdditions().query(QueryUtil.createIUQuery(ID2), null).iterator();
			assertTrue("IU " + ID2 + " expected as part of solution", iterator.hasNext());
			assertEquals("Number of fragments", 0, iterator.next().getFragments().size());
		}
		{
			Iterator<IInstallableUnit> iterator = provisioningPlan.getAdditions().query(QueryUtil.createIUQuery(ID3), null).iterator();
			assertTrue("IU " + ID3 + " expected as part of solution", iterator.hasNext());
			assertEquals("Number of fragments", 0, iterator.next().getFragments().size());
		}
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
