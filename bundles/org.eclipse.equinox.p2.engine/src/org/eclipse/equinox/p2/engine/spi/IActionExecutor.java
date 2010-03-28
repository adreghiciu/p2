package org.eclipse.equinox.p2.engine.spi;

import org.eclipse.core.runtime.IStatus;

public interface IActionExecutor {

	static final String PARM_ACTION_EXECUTOR = "actionExecutor"; //$NON-NLS-1$

	Executable action(String action);

	public static interface Executable {
		IStatus execute();

		Executable withParam(String name, String value);
	}

}
