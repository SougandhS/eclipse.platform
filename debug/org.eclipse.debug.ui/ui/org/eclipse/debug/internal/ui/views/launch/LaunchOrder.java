/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.launch;

import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.PlatformUI;

/**
 * Action that controls launch order in view.
 *
 */
class LaunchOrder extends Action {

	private final LaunchView fLaunchView;

	private final String fLaunchPref = IDebugPreferenceConstants.LAUNCH_ORDER_BY_LATEST;

	/**
	 * Creates a new action to set the launch order.
	 *
	 * @param view Reference to the debug view.
	 */
	public LaunchOrder(LaunchView view) {
		super(IInternalDebugCoreConstants.EMPTY_STRING, AS_CHECK_BOX);
		fLaunchView = view;

		setText(LaunchViewMessages.DebugToolBarSortBylatestLabel);
		setToolTipText(LaunchViewMessages.DebugToolBarSortBylatestToolTip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IDebugHelpContextIds.DEBUG_LAUNCH_ORDER_ACTION);
		setChecked(DebugUIPlugin.getDefault().getPreferenceStore()
				.getBoolean(fLaunchPref));
	}


	@Override
	public void run() {
		IPreferenceStore prefStore = DebugUIPlugin.getDefault().getPreferenceStore();
		boolean currentState = prefStore.getBoolean(fLaunchPref);
		prefStore.setValue(fLaunchPref, !currentState);
		fLaunchView.getViewer().refresh();
	}
}

