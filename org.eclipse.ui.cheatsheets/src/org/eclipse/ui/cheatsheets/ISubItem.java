/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2000, 2003.
 * All Rights Reserved. 
 * Note to U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP  schedule Contract with IBM Corp. 
*/

package org.eclipse.ui.cheatsheets;

/**
 *  <p>An ISubItem represents a sub step of a step in the cheat sheet that has sub steps.  
 *  A step in the cheat sheet that contains sub steps is represented by an IItemWithSubItems.
 *  By calling getItem on ICheatSheetManager and passing the id of a step in the cheat sheet 
 *  with sub steps, you get an IAbstractItem that may be casted to an IItemWithSubItems.  </p>
 *
 * <p>This IItemWithSubItems can be used to  access info about the sub steps for that step in the cheat sheet.
 * ISubItem can be implemented to add sub steps to a step in the cheat sheet.</p>
 * 
 * <p>Each sub step in the cheat sheet has a label, as well as the same buttons and actions that a retular
 * step in the cheat sheet (represented by IItem) has.</p>
 *  
  */
public interface ISubItem extends IItem {
	
	/**
	 * This method sets the label that will be shown for the sub item.
	 * @param label the label to be shown
	 */
	public void setLabel(String label);
	/**
	 * This method returns the label to be shown for the sub item.
	 * @return the label
	 */
	public String getLabel();
	/**
	 * This method sets the id of this sub item.  This id is used to access this sub item
	 * later and must be unique from the id of the other sub items added to the same base item.
	 * @param id the unique id to assign to this sub item
	 */
	public void setID(String id);
}
