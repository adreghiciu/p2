package org.eclipse.equinox.p2.tests.planner;

import java.util.*;
import org.eclipse.equinox.internal.p2.metadata.query.UpdateQuery;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.engine.query.IUProfilePropertyQuery;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.planner.*;
import org.eclipse.equinox.p2.query.*;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

//TODO Add test to deal with preserving the optional and strict roots
public class TestOptimalUpdate extends AbstractProvisioningTest {
	private IInstallableUnit a1, a2;
	private IInstallableUnit b1, b2, b3;
	private IInstallableUnit c1;
	private IProfile profile;
	private IPlanner planner;
	private IEngine engine;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		a1 = createEclipseIU("A", Version.create("1.0.0"), new IRequirement[] {MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "B", new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true)}, null);
		b1 = createEclipseIUSingleton("B", Version.create("1.0.0"));
		c1 = createEclipseIU("C", Version.create("1.0.0"), new IRequirement[] {MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "B", new VersionRange("[1.0.0, 2.1.0)"), null, false, false, true)}, null);

		profile = createProfile(getName());
		planner = createPlanner();
		engine = createEngine();

		install(profile, new IInstallableUnit[] {a1, b1, c1}, true, planner, engine);

	}

	public void testNoUpdateAvailable() {
		a2 = createEclipseIU("A", Version.create("2.0.0"), new IRequirement[] {MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "B", new VersionRange("[4.0.0, 5.0.0]"), null, false, false, true)}, null);
		b2 = createEclipseIUSingleton("B", Version.create("2.0.0"));
		b3 = createEclipseIUSingleton("B", Version.create("3.0.0"));
		createTestMetdataRepository(new IInstallableUnit[] {a2, b2, b3});

		IProvisioningPlan plan = buildOptimalUpdateRequest(profile, planner, new ProvisioningContext(getAgent()));
		assertNull(plan);
	}

	public void testUpdateGood() {
		a2 = createEclipseIU("A", Version.create("2.0.0"), new IRequirement[] {MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "B", new VersionRange("[2.0.0, 2.0.0]"), null, false, false, true)}, null);
		b2 = createEclipseIUSingleton("B", Version.create("2.0.0"));
		b3 = createEclipseIUSingleton("B", Version.create("3.0.0"));
		createTestMetdataRepository(new IInstallableUnit[] {a2, b2, b3});

		IProvisioningPlan plan = buildOptimalUpdateRequest(profile, planner, new ProvisioningContext(getAgent()));
		assertOK("stauts ok", plan.getStatus());
		assertFalse(plan.getAdditions().query(QueryUtil.createIUQuery(a2), null).isEmpty());
		assertFalse(plan.getAdditions().query(QueryUtil.createIUQuery(b2), null).isEmpty());
	}

	public IProvisioningPlan buildOptimalUpdateRequest(IProfile prof, IPlanner plan, ProvisioningContext context) {

		final String INCLUSION_RULES = "org.eclipse.equinox.p2.internal.inclusion.rules"; //$NON-NLS-1$
		final String INCLUSION_OPTIONAL = "OPTIONAL"; //$NON-NLS-1$
		final String INCLUSION_STRICT = "STRICT"; //$NON-NLS-1$

		IQueryResult<IInstallableUnit> strictRoots = prof.query(new IUProfilePropertyQuery(INCLUSION_RULES, INCLUSION_STRICT), null);
		IQueryResult<IInstallableUnit> optionalRoots = prof.query(new IUProfilePropertyQuery(INCLUSION_RULES, INCLUSION_OPTIONAL), null);
		Set<IInstallableUnit> tmpRoots = new HashSet<IInstallableUnit>(strictRoots.toUnmodifiableSet());
		tmpRoots.addAll(optionalRoots.toUnmodifiableSet());
		CollectionResult<IInstallableUnit> allRoots = new CollectionResult<IInstallableUnit>(tmpRoots);

		IProfileChangeRequest newRequest = plan.createChangeRequest(prof);
		Collection<IRequirement> limitingRequirements = new ArrayList<IRequirement>();

		for (Iterator<IInstallableUnit> iterator = allRoots.query(QueryUtil.ALL_UNITS, null).iterator(); iterator.hasNext();) {
			IInstallableUnit currentlyInstalled = iterator.next();

			//find all the potential updates for the currentlyInstalled iu
			IQueryResult<IInstallableUnit> updatesAvailable = plan.updatesFor(currentlyInstalled, context, null);
			for (Iterator<IInstallableUnit> iterator2 = updatesAvailable.iterator(); iterator2.hasNext();) {
				IInstallableUnit update = iterator2.next();
				newRequest.add(update);
				newRequest.setInstallableUnitInclusionRules(update, ProfileInclusionRules.createOptionalInclusionRule(update));
			}
			if (!updatesAvailable.isEmpty()) {
				//force the original IU to optional, but make sure that the solution at least includes it
				newRequest.setInstallableUnitInclusionRules(currentlyInstalled, ProfileInclusionRules.createOptionalInclusionRule(currentlyInstalled));
				limitingRequirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, currentlyInstalled.getId(), new VersionRange(currentlyInstalled.getVersion(), true, Version.MAX_VERSION, true), null, false, false));
			}
		}

		IProvisioningPlan updateFinderPlan = planner.getProvisioningPlan(newRequest, context, null);
		if (updateFinderPlan.getAdditions().query(QueryUtil.ALL_UNITS, null).isEmpty())
			return null;

		//Take into account all the removals
		IProfileChangeRequest finalChangeRequest = plan.createChangeRequest(prof);
		IQueryResult<IInstallableUnit> removals = updateFinderPlan.getRemovals().query(QueryUtil.ALL_UNITS, null);
		for (Iterator<IInstallableUnit> iterator = removals.iterator(); iterator.hasNext();) {
			IInstallableUnit iu = iterator.next();
			if (!allRoots.query(QueryUtil.createIUQuery(iu), null).isEmpty()) {
				finalChangeRequest.remove(iu);
			}
		}

		//Take into account the additions for stricts
		for (Iterator<IInstallableUnit> iterator = strictRoots.iterator(); iterator.hasNext();) {
			IInstallableUnit formerRoot = iterator.next();
			if (!updateFinderPlan.getAdditions().query(new UpdateQuery(formerRoot), null).isEmpty())
				finalChangeRequest.add(formerRoot);
		}

		//Take into account the additions for optionals
		for (Iterator<IInstallableUnit> iterator = optionalRoots.iterator(); iterator.hasNext();) {
			IInstallableUnit formerRoot = iterator.next();
			if (!updateFinderPlan.getAdditions().query(new UpdateQuery(formerRoot), null).isEmpty()) {
				finalChangeRequest.add(formerRoot);
				finalChangeRequest.setInstallableUnitInclusionRules(formerRoot, ProfileInclusionRules.createOptionalInclusionRule(formerRoot));
			}
		}
		return planner.getProvisioningPlan(finalChangeRequest, context, null);
	}
}
