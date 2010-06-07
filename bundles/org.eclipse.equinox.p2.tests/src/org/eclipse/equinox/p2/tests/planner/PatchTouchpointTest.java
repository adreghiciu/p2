/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.planner.IPlanner;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class PatchTouchpointTest extends AbstractProvisioningTest {

	IRequirementChange[] NO_CHANGES = new IRequirementChange[0];
	IRequirement NO_LIFECYCLE = null;

	IInstallableUnit a1;
	IInstallableUnitPatch p1;
	ITouchpointType pTT;

	IProfile profile1;
	IPlanner planner;
	IEngine engine;

	protected void setUp() throws Exception {
		super.setUp();

		a1 = createIU("A", Version.createOSGi(1, 2, 0), null, NO_REQUIRES, NO_PROVIDES, NO_PROPERTIES, ITouchpointType.NONE, NO_TP_DATA, true);
		MetadataFactory.createTouchpointType("a1.touchpoint", DEFAULT_VERSION);

		IRequirement[][] scope = new IRequirement[][] {{MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "A", VersionRange.emptyRange, null, false, false)}};
		pTT = MetadataFactory.createTouchpointType("p1.touchpoint", DEFAULT_VERSION);
		p1 = createIUPatch("P", Version.create("1.0.0"), null, NO_REQUIRES, NO_PROVIDES, NO_PROPERTIES, pTT, NO_TP_DATA, true, null, NO_CHANGES, scope, NO_LIFECYCLE, NO_REQUIRES);

		createTestMetdataRepository(new IInstallableUnit[] {a1, p1});

		profile1 = createProfile("TestProfile." + getName());
		planner = createPlanner();
		engine = createEngine();
	}

	public void testInstall() {
		ProfileChangeRequest req1 = new ProfileChangeRequest(profile1);
		req1.addInstallableUnits(new IInstallableUnit[] {a1, p1});
		IProvisioningPlan plan1 = planner.getProvisioningPlan(req1, null, null);
		assertEquals(IStatus.OK, plan1.getStatus().getSeverity());
		IQueryResult<IInstallableUnit> result = plan1.getAdditions().query(QueryUtil.createIUQuery("A"), new NullProgressMonitor());
		assertNotNull(result);
		assertFalse(result.isEmpty());
		IInstallableUnit aResult = result.iterator().next();
		assertEquals(pTT, aResult.getTouchpointType());
	}
}
