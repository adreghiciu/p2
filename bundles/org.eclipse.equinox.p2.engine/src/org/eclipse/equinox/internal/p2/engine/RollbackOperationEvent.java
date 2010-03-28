/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.engine;

import org.eclipse.equinox.p2.engine.IEngine;
import org.eclipse.equinox.p2.engine.IProfile;

import org.eclipse.core.runtime.IStatus;

/**
 * @since 2.0
 */
public class RollbackOperationEvent extends TransactionEvent {

	private static final long serialVersionUID = -2076492953949691215L;
	private IStatus cause;

	public RollbackOperationEvent(IProfile profile, PhaseSet phaseSet, Operand[] operands, IEngine engine, IStatus cause) {
		super(profile, phaseSet, operands, engine);
		this.cause = cause;
	}

	public IStatus getStatus() {
		return cause;
	}
}
