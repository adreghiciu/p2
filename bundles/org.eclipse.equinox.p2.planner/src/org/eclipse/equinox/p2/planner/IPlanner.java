package org.eclipse.equinox.p2.planner;

public interface IPlanner {
	public IProvisioningPlan plan(IProfileChangeRequest request, ProvisioningContext context);
}
