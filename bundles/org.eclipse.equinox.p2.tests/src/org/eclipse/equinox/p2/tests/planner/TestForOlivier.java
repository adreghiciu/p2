package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.query.IUProfilePropertyQuery;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.*;

public class TestForOlivier {

	public void foo(IProfile prof) {
		final String INCLUSION_RULES = "org.eclipse.equinox.p2.internal.inclusion.rules"; //$NON-NLS-1$
		final String INCLUSION_OPTIONAL = "OPTIONAL"; //$NON-NLS-1$
		final String INCLUSION_STRICT = "STRICT"; //$NON-NLS-1$

		IQueryResult<IInstallableUnit> strictRoots = prof.query(new IUProfilePropertyQuery(INCLUSION_RULES, INCLUSION_STRICT), null);
		IQueryResult<IInstallableUnit> optionalRoots = prof.query(new IUProfilePropertyQuery(INCLUSION_RULES, INCLUSION_OPTIONAL), null);
		IQueryable<IInstallableUnit> allRoots = new CompoundQueryable<IInstallableUnit>(new IQueryable[] {strictRoots, optionalRoots});
		System.out.println(allRoots);
	}

}
