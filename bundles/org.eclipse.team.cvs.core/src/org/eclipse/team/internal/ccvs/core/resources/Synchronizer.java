package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.ResourceDeltaVisitor;
import org.eclipse.team.internal.ccvs.core.util.SyncFileUtil;

/**
 * A singleton that provides clients access to CVS synchronization information 
 * about local files and folder. In addition, when sync information is modified
 * via this class, the Team plugin is notified of a state change and the resource 
 * is refreshed within the workbench.
 * <p>
 * All set and delete methods on the synchronizer do not persist the sync information
 * to disk, instead clients modifying the sync info must call <code>save()</code>
 * to persist the sync information.</p>
 * <p>
 * A client may set/get resource sync info for files that don't exist on the file
 * system yet. This is mainly to support having sync info for deleted files. However,
 * to set/get folder sync info the underlying folder in the file system must exist.</p>
 * 
 * [Note: it may be interesting to implement a mechanism for invalidating cache items
 * based on a LRU - least recently used, algorithm. This could potentially save a lot
 * of memory for very large workspaces.]
 * 
 * @see ResourceSyncInfo
 * @see FolderSyncInfo
 */
public class Synchronizer {
		
	// the one and only instance.
	private static Synchronizer instance;
	private static SyncResourceDeletionListener deletedListener;			
	
	// cache containing ResourceSyncInfo that are created from the entry file and permission file 
	// {keys = Entry File files values = SyncFile}
	// {Syncfile is  keys = file name values = ResourceSyncInfo}
	private Map entriesCache = new HashMap(10);
	
	// cache containing FolderSyncInfo that are created from the CVS subdirectory folder config
	// files
	// {keys = Folder values = FolderSyncInfo}
	private Map folderConfigCache = new HashMap(10);
	
	// used to remember entry files and folders that are modified in memory but have 
	// not been saved.
	private Set invalidatedEntryFiles = new HashSet(10);
	private Set invalidatedFolderConfigs = new HashSet(10);
	
	// Fields for progress monitoring algorithm. Initially, give progress for every 4 resources, double
	// this value at halfway point, then reset halfway point to be half of remaining work.  (this gives an infinite
	// series that converges at total work after an infinite number of resources).
	public static final int TOTAL_WORK = 250;
	private int halfWay = TOTAL_WORK / 2;
	private int currentIncrement = 4;
	private int nextProgress = currentIncrement;
	private int worked = 0;
	
	/** 
	 * Data structured used in the cache tables to save information about file contents that
	 * are read from disk.
	 */
	private class SyncFile {
		long timestamp = 0;
		Map config = new HashMap(10);
	}
	
	/**
	 * When a container resource in the workbench is deleted the Synchronizer must clear the cache so that 
	 * stale sync infos are removed.
	 */
	public class SyncResourceDeletionListener extends ResourceDeltaVisitor {
		protected void handleAdded(IProject project, IResource resource) {
		}
		protected void handleRemoved(IProject project, IResource resource) {
			try {
				if(resource.getType()!=IResource.FILE && !resource.getName().equals("CVS")) {
					String location = new Path(project.getWorkspace().getRoot().getLocation().toString()).append(resource.getFullPath()).toString();
					Synchronizer.getInstance().deleteFolderSync(new File(location), new NullProgressMonitor());
					Synchronizer.getInstance().save(new NullProgressMonitor());
				}
			} catch(CVSException e) {
				CVSProviderPlugin.log(new Status(IStatus.WARNING, CVSProviderPlugin.ID, 0, "Could not delete CVS folder sync info", null));
			}
		}
		protected void handleChanged(IProject project, IResource resource) {
		}
	}
	
	/**
	 * Debug method that displays the contents of the cache.
	 */
	public void dump() {
		System.out.println("Synchronizer:");
		System.out.println("\tEntries");
		for (Iterator it = entriesCache.keySet().iterator(); it.hasNext();) {
			File entriesFile = (File)it.next();
			SyncFile contents = (SyncFile)entriesCache.get(entriesFile);
			System.out.println(entriesFile.getAbsolutePath() + " (" + contents.config.size() +")");
			for (Iterator it2	= contents.config.keySet().iterator(); it2.hasNext();) {
				System.out.println(((String)it2.next()));				
			}
		}
		
		System.out.println("\tFolders");
		for (Iterator it = folderConfigCache.keySet().iterator(); it.hasNext();) {
			File folder = (File)it.next();
			System.out.println(folder.getAbsolutePath());
		}
	}
		
	/**
	 * Singleton constructor
	 */
	private Synchronizer() {
	}
	
	/**
	 * Answer the singleton instance of the Synchronizer.
	 */
	public static Synchronizer getInstance() {
		if(instance==null) {
			instance = new Synchronizer();
			deletedListener = instance.new SyncResourceDeletionListener();
			deletedListener.register();
		}
		return instance;
	}		
	
	/**
	 * Associates the provided sync information with the given file or folder. The resource
	 * may or may not exist on the file system however the parent folder must have a CVS
	 * subdirectory.
	 * <p>
	 * The workbench and team plugins are notified that the state of this resources has 
	 * changed.</p>
	 * 
	 * @param file the file or folder for which to associate the sync info.
	 * @param info to set. The name in the resource info must match the file or folder name.
	 * 
 	 * @throws CVSException if there was a problem adding sync info or broadcasting
	 * the changes. If the parent folder does not have a CVS subdirectory.
	 */
	public synchronized void setResourceSync(File file, ResourceSyncInfo info) throws CVSException {
		
		Assert.isNotNull(file);
		Assert.isNotNull(info);
		Assert.isTrue(info.getName().equals(file.getName()));
		
		File parentCVSDir = SyncFileUtil.getCVSSubdirectory(file.getParentFile());
		File entriesFile = new File(parentCVSDir, SyncFileUtil.ENTRIES);
		
		if(!parentCVSDir.exists()) {
			throw new CVSException("Parent folder does not have a CVS directory");
		}
								
		SyncFile entriesSync = (SyncFile)entriesCache.get(entriesFile);
		if(entriesSync==null) {
			entriesSync = new SyncFile();
			entriesCache.put(entriesFile, entriesSync);
		}			
		entriesSync.config.put(info.getName(), info);						
		invalidatedEntryFiles.add(entriesFile);
	}
	
	/**
	 * Answers the sync information associated with this file of folder or <code>null</code>
	 * if none is available. A resource cannot have sync information if its parent folder
	 * does not exist.
	 * 
	 * @param file the file or folder for which to return sync info.
 	 * @throws CVSException if there was a problem adding sync info or broadcasting
	 * the changes.
	 */
	public synchronized ResourceSyncInfo getResourceSync(File file) throws CVSException {
		return getResourceSync(file, false);		
	}
	
	/**
	 * Remove the sync information associated with this file.
	 * <p>
	 * The workbench and team plugins are notified that the state of this resources has 
	 * changed.</p>
	 * 
	 * @param file the file or folder for which to delete its resource sync info.
	 * 
	 * @throws CVSException if there was a problem removing sync info or broadcasting
	 * the changes.
	 */
	public synchronized void deleteResourceSync(File file) throws CVSException {
		File entriesFile = new File(SyncFileUtil.getCVSSubdirectory(file.getParentFile()), SyncFileUtil.ENTRIES);
		SyncFile entriesSync = (SyncFile)entriesCache.get(entriesFile);
		if(entriesSync!=null) {
			entriesSync.config.remove(file.getName());
			invalidatedEntryFiles.add(entriesFile);
			broadcastSyncChange(new File[] {file}, true);
		}			
	}
	
	/**
	 * Answers the folder sync information associated with this folder or <code>null</code>
	 * if none is available.
	 * 
	 * @param folder the folder for which to return folder sync info.
 	 * @throws CVSException if there was a problem adding folder sync info or broadcasting
	 * the changes. If the folder does not exist on the file system.
	 */
	public synchronized FolderSyncInfo getFolderSync(File folder) throws CVSException {
		return getFolderSync(folder, false);
	}
	
	/**
	 * Associates the provided folder sync information with the given folder. The folder
	 * must exist on the file system.
	 * <p>
	 * The workbench and team plugins are notified that the state of this resources has 
	 * changed.</p>
	 * 
	 * @param file the file or folder for which to associate the sync info.
	 * @param info to set. The name in the resource info must match the file or folder name.
	 * 
 	 * @throws CVSException if there was a problem adding sync info or broadcasting
	 * the changes. If the folder does not exist on the file system.
	 */
	public synchronized void setFolderSync(File folder, FolderSyncInfo info) throws CVSException {
		
		Assert.isNotNull(info);
		
		if(!folder.exists()) {
			throw new CVSException("Folder must exist to set sync info");
		}
		
		File cvsDirectory = SyncFileUtil.getCVSSubdirectory(folder);
		if(!cvsDirectory.exists()) {
			cvsDirectory.mkdir();
		}
					
		// if parent has the sync folder (e.g. CVS) then ensure that the directory
		// entry for this folder is added.
		if(getFolderSync(folder.getParentFile())!=null) {
			ResourceSyncInfo resourceInfo = new ResourceSyncInfo(folder.getName());
			setResourceSync(folder, resourceInfo);
		}
		
		// there can only be one sync entry for folders, create a new one
		folderConfigCache.remove(folder);
		SyncFile folderSync = new SyncFile();
		folderSync.config.put(folder.getName(), info);
		folderConfigCache.put(folder, folderSync);
		invalidatedFolderConfigs.add(folder);			
	}
	
	/**
	 * Remove the folder sync information associated with this folder and all its
	 * children. If a parent folder does not have folder sync (e.g. is not managed
	 * by CVS) then all children must also not have sync information.
	 * <p>
	 * The workbench and team plugins are notified that the state of this resources has 
	 * changed.</p>
	 * 
	 * @param folder the root folder at which to start deleting.
	 * @param monitor the progress monitor, cannot be <code>null</code>.
	 * 
	 * @throws CVSException if there was a problem removing sync info or broadcasting
	 * the changes.
	 */	
	public synchronized void deleteFolderSync(File folder, IProgressMonitor monitor) throws CVSException {
		ResourceSyncInfo[] children = members(folder);
		
		for (int i = 0; i < children.length; i++) {
			if(children[i].isDirectory()) {
				deleteFolderSync(new File(folder, children[i].getName()), monitor);			
			}
		}
		
		deleteFolderAndChildEntries(folder, true);
	}
	
	/**
	 * The set and delete methods on the synchronizer do not persist the sync information 
	 * to disk, instead clients modifying the sync info must call this method to 
	 * persist the sync information.
	 * 
	 * @param monitor the progress monitor, cannot be <code>null</code>.
	 * 
	 * @throws CVSException if there was a problem persisting the changes to disk.
	 */
	public synchronized void save(IProgressMonitor monitor) throws CVSException {
		List filesToUpdate = new ArrayList();
		for (Iterator it = invalidatedEntryFiles.iterator(); it.hasNext();) {
			File entryFile = (File) it.next();
			SyncFile info = (SyncFile)entriesCache.get(entryFile);
			
			// entry file may of been deleted from cache by a client calling
			// deleteFolderSync (e.g. pruning on update).
			if(info!=null) {
							
				// collect all sync infos for this entries file
				List syncInfos = new ArrayList();
				for (Iterator it2 = info.config.values().iterator(); it2.hasNext();) {										
					ResourceSyncInfo element = (ResourceSyncInfo) it2.next();
					syncInfos.add(element);
					filesToUpdate.add(new File(entryFile.getParentFile().getParentFile(), element.getName()));
				}
				
				if(!entryFile.exists()) {
					try {
						entryFile.getParentFile().mkdirs();
						entryFile.createNewFile();
					} catch(IOException e) {
						throw new CVSException("Error creating " + entryFile.getAbsolutePath(), e);
					}
				}				
				
				SyncFileUtil.writeEntriesFile(entryFile, (ResourceSyncInfo[]) syncInfos.toArray(new ResourceSyncInfo[syncInfos.size()]));
				
				// ensure that the external sync files are kept in sync with the workbench
				File[] entrySyncFiles = SyncFileUtil.getEntrySyncFiles(entryFile.getParentFile().getParentFile());
				broadcastSyncChange(entrySyncFiles, false);			
			}
		}
		
		for (Iterator it = invalidatedFolderConfigs.iterator(); it.hasNext();) {
			File folder = (File) it.next();
			SyncFile info = (SyncFile)folderConfigCache.get(folder);
			
			// folder config may of been deleted from cache by a client calling
			// deleteFolderSync (e.g. pruning on update).
			if(info!=null) {	
				SyncFileUtil.writeFolderConfig(folder, (FolderSyncInfo)info.config.get(folder.getName()));
				File rootFile = new File(SyncFileUtil.getCVSSubdirectory(folder), SyncFileUtil.ROOT);
				info.timestamp = rootFile.lastModified();
				
				// ensure that the external sync files are kept in sync with the workbench.
				File[] folderSyncFiles = SyncFileUtil.getFolderSyncFiles(folder);
				broadcastSyncChange(folderSyncFiles, false);			
			}
		}
		
		// broadcast state changes for non-metadata files that have changed
		if(!filesToUpdate.isEmpty()) {
			broadcastSyncChange((File[]) filesToUpdate.toArray(new File[filesToUpdate.size()]), true);
		}
		
		// clear invalidated lists
		invalidatedEntryFiles.clear();
		invalidatedFolderConfigs.clear();
	}
	
	/**
	 * Answers the sync information for child resources of this folder. Note that the
	 * returned sync information may be for resources that no longer exist (e.g. in the
	 * case of a pending deletion.)
	 * 
	 * @param folder the folder for which to return the children resource sync infos.
	 */
	public ResourceSyncInfo[] members(File folder) throws CVSException {
		File entriesFile = new File(SyncFileUtil.getCVSSubdirectory(folder), SyncFileUtil.ENTRIES);
		SyncFile entriesSync = (SyncFile)entriesCache.get(entriesFile);		
		
		if(entriesSync==null) {
			return new ResourceSyncInfo[0];
		}
		Collection entries = entriesSync.config.values();
		return (ResourceSyncInfo[])entries.toArray(new ResourceSyncInfo[entries.size()]);		
	}
	
	/**
	 * Reload the sync information from disk for this folder and all its children depth first.
	 * 
	 * @param folder the root folder at which to start reloading.
	 * @param monitor the progress monitor, cannot be <code>null</code>.
	 */
	public void reload(ICVSResource resource, IProgressMonitor monitor) throws CVSException {
		try {
			monitor = Policy.monitorFor(monitor);
			monitor.beginTask("", TOTAL_WORK);
			doReload(resource, monitor);
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * Clear the entire synchronizer cache. This will force a reload of sync information from
	 * disk on subsequent get calls. There must not be any invalidated sync infos.
	 */
	public void clear() {
		Assert.isTrue(invalidatedEntryFiles.isEmpty() && invalidatedFolderConfigs.isEmpty());
		entriesCache.clear();
		folderConfigCache.clear();
	}
	
	/**
	 * Answers if the cache is empty and there are no pending saves.
	 */
	public boolean isEmpty() {
		return  entriesCache.isEmpty() && folderConfigCache.isEmpty();
	}
	
	/**
	 * Internal helping for returning the folder sync info. If reload is <code>true</code>
	 * then load entries from disk.
	 * 
	 * @param folder the folder for which to retrieve folder sync info.
	 * @param reload if <code>true</code> then reload config from disk, if <code>false</code>
	 * config is taken from cache and reloaded only if it is not cached.
	 */
	protected FolderSyncInfo getFolderSync(File folder, boolean reload) throws CVSException {
		SyncFile folderSync = (SyncFile)folderConfigCache.get(folder);
		if(folderSync==null || reload) {
			
			if(!folder.exists()) {				
				return null;
			}
			
			folderSync = new SyncFile();
			FolderSyncInfo info = SyncFileUtil.readFolderConfig(folder);
			
			// no CVS sub-directory
			if(info==null) {
				return null;
			}
			folderSync.config.put(folder.getName(), info);
			folderConfigCache.put(folder, folderSync);
		}
		return (FolderSyncInfo)folderSync.config.get(folder.getName());			
	}
	
	/**
	 * Internal helping for returning the folder sync info. If reload is <code>true</code>
	 * then load entries from disk.
	 * 
	 * @param folder the folder for which to retrieve folder sync info.
	 * @param reload if <code>true</code> then reload config from disk, if <code>false</code>
	 * config is taken from cache and reloaded only if it is not cached.
	 */
	protected ResourceSyncInfo getResourceSync(File file, boolean reload) throws CVSException {
		File entriesFile = new File(SyncFileUtil.getCVSSubdirectory(file.getParentFile()), SyncFileUtil.ENTRIES);
		SyncFile entriesSync = (SyncFile)entriesCache.get(entriesFile);
		
		// read entries file
		if(entriesSync==null || reload) {
			entriesSync = new SyncFile();
			ResourceSyncInfo infos[] = SyncFileUtil.readEntriesFile(file.getParentFile());
			// entries file does not exist
			if(infos==null) {
				entriesCache.remove(entriesFile);
				return null;
			}
			for (int i = 0; i < infos.length; i++) {				
				entriesSync.config.put(infos[i].getName(), infos[i]);
			}
			entriesCache.put(entriesFile, entriesSync);
		}
		return (ResourceSyncInfo)entriesSync.config.get(file.getName());
	}

	/**
	 * Notifies the worbench and the team plugin that changes to a resource have
	 * occured. This allows components interested in the CVS state of a resource to
	 * update when state changes, and also to keep resources in sync with the 
	 * workspace by refreshing the contents. 
	 * 
	 * @param file a file that has its CVS state changed. If the file does not exist
	 * then the parent if notified instead.
	 */
	protected void broadcastSyncChange(File[] files, boolean notifyOfStateChange) throws CVSException {	
		List resourcesToUpdate = new ArrayList();
		int depth = IResource.DEPTH_ZERO;
		
		for(int i=0; i < files.length; i++) {
			File file = files[i];
			if(!file.exists()) {
				file = file.getParentFile();
				depth = IResource.DEPTH_ONE;
			}
			
			if(file.equals(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile()) || !file.exists()) {
				return;
			}
			
			IResource resource;
			if(file.isDirectory()) {
				resource = ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(new Path(file.getAbsolutePath()));
			} else {
				resource = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(new Path(file.getAbsolutePath()));
			}
				
			if(resource!=null) {			
				try {
					resourcesToUpdate.add(resource);
					resource.refreshLocal(depth, null);
				} catch(CoreException e) {
					// throw new CVSException("Error refreshing file from local contents " + file.getAbsolutePath(), e);
				}
			}
		}
		if(!resourcesToUpdate.isEmpty() && notifyOfStateChange) {
			TeamPlugin.getManager().broadcastResourceStateChanges((IResource[]) resourcesToUpdate.toArray(new IResource[resourcesToUpdate.size()]));
		}
	}
	
	/**
	 * Deletes a folder and all its children's sync information from the cache. 
	 * <p>
	 * The workbench and team plugins are notified that the state of this resources has 
	 * changed.</p>
	 * 
	 * @param folder the root folder at which to start deleting sync information.
	 */
	protected void deleteFolderAndChildEntries(File folder, boolean deleteResourceSync) throws CVSException {
		
		// remove resource sync entries file from the cache
		File entriesFile = new File(SyncFileUtil.getCVSSubdirectory(folder), SyncFileUtil.ENTRIES);
		entriesCache.remove(entriesFile);
		
		// remove from parent
		if(deleteResourceSync) {
			deleteResourceSync(folder);
		}
		
		// remove folder sync
		folderConfigCache.remove(folder);
		
		// notify of state change to this folder
		broadcastSyncChange(new File[] {folder}, true);
	}
	
	/**
	 * Perform the reload.
	 */
	private void doReload(ICVSResource resource, IProgressMonitor monitor) throws CVSException {		
		if (resource instanceof LocalFolder) {
			LocalFolder fsFolder = (LocalFolder) resource;
			File file = fsFolder.getLocalFile();
			
			String message = Policy.bind("Synchronizer.reload", fsFolder.getName());
			monitor.subTask(message);
									
			ICVSFolder[] folders = fsFolder.getFolders();
			for (int i = 0; i < folders.length; i++) {
				ICVSFolder iCVSFolder = folders[i];
				reload(iCVSFolder, monitor);
			}
			
			FolderSyncInfo info = getFolderSync(file, true);
			
			// info will be null if the CVS subdirectory or folder does not exist, then we can safely
			// clear this folder from the cache. Otherwise, reload the entries file for this folder as
			// well.
			if(info==null) {
				deleteFolderAndChildEntries(file, false);
			} else {			
				getResourceSync(new File(file, "dummy"), true);
			}
		
			// Update monitor work amount
			if (--nextProgress <= 0) {
				monitor.worked(1);
				worked++;
				if (worked >= halfWay) {
					//we have passed the current halfway point, so double the
					//increment and reset the halfway point.
					currentIncrement *= 2;
					halfWay += (TOTAL_WORK - halfWay) / 2;				
				}
				//reset the progress counter to another full increment
				nextProgress = currentIncrement;
			}	
		}
		if(resource instanceof LocalFile) {
			// force a reload of entries file of parent
			getResourceSync(((LocalFile)resource).getLocalFile(), true);
		}		
	}
}