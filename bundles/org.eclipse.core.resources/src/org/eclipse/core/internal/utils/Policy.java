/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.utils;

import java.util.Date;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;

public class Policy {
	public static final long MAX_BUILD_DELAY = 1000;
	public static final long MIN_BUILD_DELAY = 100;

	public static final boolean buildOnCancel = false;
	public static int opWork = 99;
	public static int endOpWork = 1;
	public static final int totalWork = 100;

	//general debug flag for the plugin
	public static boolean DEBUG = false;
	//debug constants
	public static boolean DEBUG_BUILD_FAILURE = false;
	public static boolean DEBUG_NEEDS_BUILD = false;
	public static boolean DEBUG_BUILD_INVOKING = false;
	public static boolean DEBUG_BUILD_DELTA = false;
	public static boolean DEBUG_NATURES = false;
	public static boolean DEBUG_HISTORY = false;
	public static boolean DEBUG_PREFERENCES = false;
	public static boolean DEBUG_STRINGS = false;

	// Get timing information for restoring data
	public static boolean DEBUG_RESTORE = false;
	public static boolean DEBUG_RESTORE_MARKERS = false;
	public static boolean DEBUG_RESTORE_SYNCINFO = false;
	public static boolean DEBUG_RESTORE_TREE = false;
	public static boolean DEBUG_RESTORE_METAINFO = false;
	public static boolean DEBUG_RESTORE_SNAPSHOTS = false;
	public static boolean DEBUG_RESTORE_MASTERTABLE = false;

	// Get timing information for saving and snapshoting data
	public static boolean DEBUG_SAVE = false;
	public static boolean DEBUG_SAVE_MARKERS = false;
	public static boolean DEBUG_SAVE_SYNCINFO = false;
	public static boolean DEBUG_SAVE_TREE = false;
	public static boolean DEBUG_SAVE_METAINFO = false;
	public static boolean DEBUG_SAVE_MASTERTABLE = false;

	public static boolean DEBUG_AUTO_REFRESH = false;
	public static boolean DEBUG_CONTENT_TYPE = false;
	public static boolean DEBUG_CONTENT_TYPE_CACHE = false;
	
	static {
		//init debug options
		if (ResourcesPlugin.getPlugin().isDebugging()) {
			DEBUG = true;
			String sTrue = Boolean.TRUE.toString();
			DEBUG_BUILD_FAILURE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/build/failure")); //$NON-NLS-1$ 
			DEBUG_NEEDS_BUILD = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/build/needbuild")); //$NON-NLS-1$ 
			DEBUG_BUILD_INVOKING = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/build/invoking")); //$NON-NLS-1$ 
			DEBUG_BUILD_DELTA = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/build/delta")); //$NON-NLS-1$ 
			DEBUG_NATURES = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/natures")); //$NON-NLS-1$ 
			DEBUG_HISTORY = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/history")); //$NON-NLS-1$ 
			DEBUG_PREFERENCES = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/preferences")); //$NON-NLS-1$
			DEBUG_STRINGS = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/strings")); //$NON-NLS-1$

			DEBUG_RESTORE_MARKERS = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore/markers")); //$NON-NLS-1$ 
			DEBUG_RESTORE_SYNCINFO = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore/syncinfo")); //$NON-NLS-1$ 
			DEBUG_RESTORE_TREE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore/tree")); //$NON-NLS-1$ 
			DEBUG_RESTORE_METAINFO = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore/metainfo")); //$NON-NLS-1$ 
			DEBUG_RESTORE_SNAPSHOTS = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore/snapshots")); //$NON-NLS-1$ 
			DEBUG_RESTORE_MASTERTABLE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore/mastertable")); //$NON-NLS-1$ 
			DEBUG_RESTORE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/restore")); //$NON-NLS-1$ 

			DEBUG_SAVE_MARKERS = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/save/markers")); //$NON-NLS-1$ 
			DEBUG_SAVE_SYNCINFO = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/save/syncinfo")); //$NON-NLS-1$ 
			DEBUG_SAVE_TREE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/save/tree")); //$NON-NLS-1$ 
			DEBUG_SAVE_METAINFO = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/save/metainfo")); //$NON-NLS-1$ 
			DEBUG_SAVE_MASTERTABLE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/save/mastertable")); //$NON-NLS-1$ 
			DEBUG_SAVE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/save")); //$NON-NLS-1$ 

			DEBUG_AUTO_REFRESH = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/refresh")); //$NON-NLS-1$
			
			DEBUG_CONTENT_TYPE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/contenttype")); //$NON-NLS-1$
			DEBUG_CONTENT_TYPE_CACHE = sTrue.equalsIgnoreCase(Platform.getDebugOption(ResourcesPlugin.PI_RESOURCES + "/contenttype/cache")); //$NON-NLS-1$ 
		}
	}

	public static void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
	}

	public static IProgressMonitor monitorFor(IProgressMonitor monitor) {
		return monitor == null ? new NullProgressMonitor() : monitor;
	}

	public static IProgressMonitor subMonitorFor(IProgressMonitor monitor, int ticks) {
		if (monitor == null)
			return new NullProgressMonitor();
		if (monitor instanceof NullProgressMonitor)
			return monitor;
		return new SubProgressMonitor(monitor, ticks);
	}

	public static IProgressMonitor subMonitorFor(IProgressMonitor monitor, int ticks, int style) {
		if (monitor == null)
			return new NullProgressMonitor();
		if (monitor instanceof NullProgressMonitor)
			return monitor;
		return new SubProgressMonitor(monitor, ticks, style);
	}

	/**
	 * Print a debug message to the console. 
	 * Pre-pend the message with the current date and the name of the current thread.
	 */
	public static void debug(String message) {
		StringBuffer buffer = new StringBuffer();
		buffer.append(new Date(System.currentTimeMillis()));
		buffer.append(" - ["); //$NON-NLS-1$
		buffer.append(Thread.currentThread().getName());
		buffer.append("] "); //$NON-NLS-1$
		buffer.append(message);
		System.out.println(buffer.toString());
	}
}
