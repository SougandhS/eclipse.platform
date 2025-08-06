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
package org.eclipse.debug.internal.ui.actions.variables;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IViewPart;

public class VariableClipboardCompareAction implements IViewActionDelegate {

	private IViewPart fView;

	private IViewPart getView() {
		return fView;
	}

	protected void setView(IViewPart view) {
		fView = view;
	}

	@Override
	public void run(IAction action) {
		IStructuredSelection selection = getSelection();
		if (selection.getFirstElement() instanceof IVariable variable) {
			try {
				String variableName = variable.getName();
				String variableValue = variable.getValue().getValueString();
				String cb = getClipboard().toString();
				compareVariable(variableValue, variableName, cb);
			} catch (DebugException e) {
				DebugUIPlugin.log(e);
			}
		}
	}

	private void compareVariable(String variableValue, String variableName, String clipboardContent) {

		class StringTypedElement implements ITypedElement, IStreamContentAccessor {
		    private final String name;
		    private final String content;

		    public StringTypedElement(String name, String content) {
		        this.name = name;
		        this.content = content;
		    }

		    @Override
		    public String getName() {
				return name;
		    }

		    @Override
		    public Image getImage() {
				return null;
		    }

		    @Override
		    public String getType() {
				return "variable"; //$NON-NLS-1$
		    }

			@Override
			public InputStream getContents() {
		        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		    }
		}
		StringTypedElement variable = new StringTypedElement(variableName, variableValue);
		StringTypedElement clipboard = new StringTypedElement("Clipboard", clipboardContent); //$NON-NLS-1$

		CompareConfiguration config = new CompareConfiguration();
		config.setLeftLabel(variableName);
		config.setRightLabel("Contents in clipboard"); //$NON-NLS-1$
		config.setLeftEditable(false);
		config.setLeftEditable(false);
		CompareEditorInput compareInput = new CompareEditorInput(config) {
			@Override
			protected Object prepareInput(IProgressMonitor monitor)
					throws InvocationTargetException, InterruptedException {
				return new DiffNode(variable, clipboard);

			}
		};

		CompareUI.openCompareEditor(compareInput);

	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {

	}

	@Override
	public void init(IViewPart view) {
		setView(view);
	}

	protected IStructuredSelection getSelection() {
		return (IStructuredSelection) getView().getViewSite().getSelectionProvider().getSelection();
	}

	private Object getClipboard() {
		Clipboard clip = new Clipboard(Display.getDefault());
		return clip.getContents(TextTransfer.getInstance());
	}

}
