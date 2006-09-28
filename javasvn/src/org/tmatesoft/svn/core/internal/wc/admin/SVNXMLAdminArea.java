/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNXMLAdminArea extends SVNAdminArea {
    public static final int WC_FORMAT = 4;
    private static final String THIS_DIR = "";
    private static final Set BOOLEAN_PROPERTIES = new HashSet();
    static {
        BOOLEAN_PROPERTIES.add(SVNProperty.COPIED);
        BOOLEAN_PROPERTIES.add(SVNProperty.DELETED);
        BOOLEAN_PROPERTIES.add(SVNProperty.ABSENT);
        BOOLEAN_PROPERTIES.add(SVNProperty.INCOMPLETE);
    }

    private File myLockFile;
    private File myEntriesFile;

    public SVNXMLAdminArea(File dir) {
        super(dir);
        myLockFile = new File(getAdminDirectory(), "lock");
        myEntriesFile = new File(getAdminDirectory(), "entries");
    }

    private void saveProperties(ISVNLog log) throws SVNException {
        Map propsCache = getPropertiesStorage(false);
        if (propsCache == null || propsCache.isEmpty()) {
            return;
        }

        Map command = new HashMap();
        for(Iterator entries = propsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)propsCache.get(name);
            if (props.isModified()) {
                String dstPath = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                dstPath = getAdminDirectory().getName() + "/" + dstPath;
                
                if (props.isEmpty()) {
                    command.put(ISVNLog.NAME_ATTR, dstPath);
                    log.addCommand(SVNLog.DELETE, command, false);
                } else {
                    String tmpPath = "tmp/";
                    tmpPath += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                    File tmpFile = getAdminFile(tmpPath);
                    String srcPath = getAdminDirectory().getName() + "/" + tmpPath;
                    SVNProperties tmpProps = new SVNProperties(tmpFile, srcPath);
                    tmpProps.setProperties(props.asMap());
                    command.put(ISVNLog.NAME_ATTR, srcPath);
                    command.put(ISVNLog.DEST_ATTR, dstPath);
                    log.addCommand(SVNLog.MOVE, command, false);
                    command.clear();
                    command.put(SVNLog.NAME_ATTR, dstPath);
                    log.addCommand(SVNLog.READONLY, command, false);
                }
                props.setModified(false);
                command.clear();
            }
        }
    }
    
    private void saveBaseProperties(ISVNLog log) throws SVNException {
        Map basePropsCache = getBasePropertiesStorage(false);
        if (basePropsCache == null || basePropsCache.isEmpty()) { 
            return;
        }

        Map command = new HashMap();
        for(Iterator entries = basePropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)basePropsCache.get(name);
            if (props.isModified()) {
                String dstPath = getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                dstPath = getAdminDirectory().getName() + "/" + dstPath;
                
                if (props.isEmpty()) {
                    command.put(ISVNLog.NAME_ATTR, dstPath);
                    log.addCommand(SVNLog.DELETE, command, false);
                } else {
                    String tmpPath = "tmp/";
                    tmpPath += getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                    File tmpFile = getAdminFile(tmpPath);
                    String srcPath = getAdminDirectory().getName() + "/" + tmpPath;
                    SVNProperties tmpProps = new SVNProperties(tmpFile, srcPath);
                    tmpProps.setProperties(props.asMap());
                    command.put(ISVNLog.NAME_ATTR, srcPath);
                    command.put(ISVNLog.DEST_ATTR, dstPath);
                    log.addCommand(SVNLog.MOVE, command, false);
                    command.clear();
                    command.put(SVNLog.NAME_ATTR, dstPath);
                    log.addCommand(SVNLog.READONLY, command, false);
                }
                props.setModified(false);
                command.clear();
            }
        }
    }
    
    public void saveWCProperties(boolean close) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(false);
        if (wcPropsCache == null) {
            return;
        }
        
        for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)wcPropsCache.get(name);
            if (props.isModified()) {
                String dstPath = getThisDirName().equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
                File dstFile = getAdminFile(dstPath);
                if (props.isEmpty()) {
                    SVNFileUtil.deleteFile(dstFile);
                } else {
                    String tmpPath = "tmp/";
                    tmpPath += getThisDirName().equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
                    File tmpFile = getAdminFile(tmpPath);
                    SVNProperties.setProperties(props.asMap(), dstFile, tmpFile, SVNProperties.SVN_HASH_TERMINATOR);
                }
                props.setModified(false);
            }
        }
        if (close) {
            closeWCProperties();
        }
    }
    
    public ISVNProperties getBaseProperties(String name) throws SVNException {
        Map basePropsCache = getBasePropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)basePropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map baseProps = null;
        try {
            baseProps = readBaseProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }

        props = new SVNProperties13(baseProps);
        basePropsCache.put(name, props);
        return props;
    }

    public ISVNProperties getProperties(String name) throws SVNException {
        Map propsCache = getPropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)propsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map properties = null;
        try {
            properties = readProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }
        
        props = new SVNProperties13(properties);
        propsCache.put(name, props);
        return props;
    }

    public ISVNProperties getWCProperties(String name) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)wcPropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map properties = null;
        try {
            properties = readWCProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }

        props = new SVNProperties13(properties);
        wcPropsCache.put(name, props);
        return props;
    }
    
    private Map readProperties(String name) throws SVNException {
        File propertiesFile = getPropertiesFile(name, false);
        SVNProperties props = new SVNProperties(propertiesFile, null);
        return props.asMap();
    }

    private Map readBaseProperties(String name) throws SVNException {
        File propertiesFile = getBasePropertiesFile(name, false);
        SVNProperties props = new SVNProperties(propertiesFile, null);
        return props.asMap();
    }

    private Map readWCProperties(String name) throws SVNException {
        String path = getThisDirName().equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        SVNProperties props = new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
        return props.asMap();
    }

    public void saveEntries(boolean close) throws SVNException {
        if (myEntries != null) {
            SVNEntry2 rootEntry = (SVNEntry2) myEntries.get(getThisDirName());
            if (rootEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No default entry in directory ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
            
            String reposURL = rootEntry.getRepositoryRoot();
            String url = rootEntry.getURL();
            if (reposURL != null && !SVNPathUtil.isAncestor(reposURL, url)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry ''{0}'' has inconsistent repository root and url", getThisDirName());
                SVNErrorManager.error(err);
            }
    
            File tmpFile = new File(getAdminDirectory(), "tmp/entries");
            Writer os = null;
            try {
                os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(tmpFile), "UTF-8");
                writeEntries(os);
            } catch (IOException e) {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.deleteFile(tmpFile);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot wrtie entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            
            SVNFileUtil.rename(tmpFile, myEntriesFile);
            SVNFileUtil.setReadonly(myEntriesFile, true);
            if (close) {
                myEntries = null;
            }
        }
    
    }

    public void saveVersionedProperties(ISVNLog log, boolean close) throws SVNException {
        saveProperties(log);
        saveBaseProperties(log);
        if (close) {
            myBaseProperties = null;
            myProperties = null;
        }
    }

    protected Map fetchEntries() throws SVNException {
        if (!myEntriesFile.exists()) {
            return null;
        }
        Map entries = new TreeMap();
        
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myEntriesFile), "UTF-8"));
            String line;
            Map entry = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equals("<entry")) {
                    entry = new HashMap();
                    continue;
                }
                if (entry != null) {
                    if (line.indexOf('=') <= 0 || line.indexOf('\"') <= 0 || 
                            line.indexOf('\"') == line.lastIndexOf('\"')) {
                        continue;
                    }
                    String name = line.substring(0, line.indexOf('='));
                    String value = line.substring(line.indexOf('\"') + 1, 
                            line.lastIndexOf('\"'));
                    value = SVNEncodingUtil.xmlDecode(value);
                    entry.put(SVNProperty.SVN_ENTRY_PREFIX + name, value);
                    if (line.charAt(line.length() - 1) == '>') {
                        String entryName = (String) entry.get(SVNProperty.NAME);
                        if (entryName == null) {
                            return entries;
                        }
                        entries.put(entryName, new SVNEntry2(entry, this, entryName));
                        if (!getThisDirName().equals(entryName)) {
                            SVNEntry2 rootEntry = (SVNEntry2)entries.get(getThisDirName());
                            if (rootEntry != null) {
                                Map rootEntryAttrs = rootEntry.asMap();

                                if (entry.get(SVNProperty.REVISION) == null) {
                                    entry.put(SVNProperty.REVISION, rootEntryAttrs.get(SVNProperty.REVISION));
                                }
                                if (entry.get(SVNProperty.URL) == null) {
                                    String url = (String) rootEntryAttrs.get(SVNProperty.URL);
                                    if (url != null) {
                                        url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(entryName));
                                    }
                                    entry.put(SVNProperty.URL, url);
                                }
                                if (entry.get(SVNProperty.UUID) == null) {
                                    entry.put(SVNProperty.UUID, rootEntryAttrs.get(SVNProperty.UUID));
                                }
                                if (entry.get(SVNProperty.REPOS) == null && rootEntryAttrs.get(SVNProperty.REPOS) != null) {
                                    entry.put(SVNProperty.REPOS, rootEntryAttrs.get(SVNProperty.REPOS));
                                }
                            }
                        }
                        entry = null;
                    }
                }
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        return entries;
    }

    public String getThisDirName() {
        return THIS_DIR;
    }

    protected void writeEntries(Writer writer) throws IOException {
        SVNEntry2 rootEntry = (SVNEntry2)myEntries.get(getThisDirName());
        Map rootEntryAttrs = rootEntry.asMap();
        
        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        writer.write("<wc-entries\n");
        writer.write("   xmlns=\"svn:\">\n");

        for (Iterator entriesIter = myEntries.keySet().iterator(); entriesIter.hasNext();) {
            String name = (String)entriesIter.next();
            SVNEntry2 entry = (SVNEntry2)myEntries.get(name);
            Map entryAttrs = entry.asMap();
            writer.write("<entry");
            for (Iterator names = entryAttrs.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) entryAttrs.get(propName);
                if (propValue == null) {
                    continue;
                }
                if (BOOLEAN_PROPERTIES.contains(propName) && !Boolean.TRUE.toString().equals(propValue)) {
                    continue;
                }
                if (!getThisDirName().equals(name)) {
                    Object expectedValue;
                    if (SVNProperty.KIND_DIR.equals(entryAttrs.get(SVNProperty.KIND))) {
                        if (SVNProperty.UUID.equals(propName)
                                || SVNProperty.REVISION.equals(propName)
                                || SVNProperty.URL.equals(propName) 
                                || SVNProperty.REPOS.equals(propName)) {
                            continue;
                        }
                    } else {
                        if (SVNProperty.URL.equals(propName)) {
                            expectedValue = SVNPathUtil.append((String) rootEntryAttrs.get(propName), SVNEncodingUtil.uriEncode(name));
                        } else if (SVNProperty.UUID.equals(propName) || SVNProperty.REVISION.equals(propName)) {
                            expectedValue = rootEntryAttrs.get(propName);
                        } else if (SVNProperty.REPOS.equals(propName)) {
                            expectedValue = rootEntryAttrs.get(propName);
                        } else {
                            expectedValue = null;
                        }
                        if (propValue.equals(expectedValue)) {
                            continue;
                        }
                    }
                }
                propName = propName.substring(SVNProperty.SVN_ENTRY_PREFIX.length());
                propValue = SVNEncodingUtil.xmlEncodeAttr(propValue);
                writer.write("\n   ");
                writer.write(propName);
                writer.write("=\"");
                writer.write(propValue);
                writer.write("\"");
            }
            writer.write("/>\n");
            writer.flush();
        }
        writer.write("</wc-entries>\n");
        writer.flush();
    }

    public boolean hasPropModifications(String name) throws SVNException {
        File propFile;
        File baseFile;
        if ("".equals(name)) {
            propFile = getAdminFile("dir-props");
            baseFile = getAdminFile("dir-prop-base");
        } else {
            propFile = getAdminFile("props/" + name + ".svn-work");
            baseFile = getAdminFile("prop-base/" + name + ".svn-base");
        }
        SVNEntry2 entry = getEntry(name, true);
        long propLength = propFile.length();
        boolean propEmtpy = propLength <= 4;
        if (entry.isScheduledForReplacement()) {
            return !propEmtpy;
        }
        if (propEmtpy) {
            boolean baseEmtpy = baseFile.length() <= 4;
            if (baseEmtpy) {
                return !propEmtpy;
            }
            return true;
        }
        if (propLength != baseFile.length()) {
            return true;
        }
        String realTimestamp = SVNTimeUtil.formatDate(new Date(propFile.lastModified()));
        String fullRealTimestamp = realTimestamp;
        realTimestamp = realTimestamp.substring(0, 23);
        String timeStamp = entry.getPropTime();
        if (timeStamp != null) {
            timeStamp = timeStamp.substring(0, 23);
            if (realTimestamp.equals(timeStamp)) {
                return false;
            }
        }
        ISVNProperties m1 = getProperties(name);
        ISVNProperties m2 = getBaseProperties(name);
        if (m1.equals(m2)) {
            if (isLocked()) {
                entry.setPropTime(fullRealTimestamp);
                saveEntries(false);
            }
            return false;
        }
        return true;
    }
    
    public boolean hasTextModifications(String name, boolean forceComparison) throws SVNException {
        SVNFileType fType = SVNFileType.getType(getFile(name));
        if (fType == SVNFileType.DIRECTORY || fType == SVNFileType.NONE) {
            return false;
        }
        SVNEntry2 entry = getEntry(name, true);
        if (entry.isDirectory()) {
            return false;
        }
        if (!forceComparison) {
            String textTime = entry.getTextTime();
            long textTimeAsLong = SVNFileUtil.roundTimeStamp(SVNTimeUtil.parseDateAsLong(textTime));
            long tstamp = SVNFileUtil.roundTimeStamp(getFile(name).lastModified());
            if (textTimeAsLong == tstamp ) {
                return false;
            }
        }
        File baseFile = getBaseFile(name, false);
        if (!baseFile.isFile()) {
            return true;
        }
        // translate versioned file.
        File baseTmpFile = SVNFileUtil.createUniqueFile(getRoot(), 
                SVNFileUtil.getBasePath(getBaseFile(name, true)), ".tmp");
        if (!baseTmpFile.getParentFile().exists()) {
            baseTmpFile.getParentFile().mkdirs();
        }
        File versionedFile = getFile(name);
        SVNTranslator2.translate(this, name, name, SVNFileUtil.getBasePath(baseTmpFile), false, false);

        // now compare file and get base file checksum (when forced)
        MessageDigest digest;
        boolean equals = true;
        try {
            digest = forceComparison ? MessageDigest.getInstance("MD5") : null;
            equals = SVNFileUtil.compareFiles(baseFile, baseTmpFile, digest);
            if (forceComparison) {
                // if checksum differs from expected - throw exception
                String checksum = SVNFileUtil.toHexDigest(digest);
                if (!checksum.equals(entry.getChecksum())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base: ''{0}''\n" +
                            "   expected: {1}\n" +
                            "     actual: {2}\n", new Object[] {baseFile, entry.getChecksum(), checksum});
                    SVNErrorManager.error(err);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {1}", e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            baseTmpFile.delete();
        }

        if (equals && isLocked()) {
            entry.setTextTime(SVNTimeUtil.formatDate(new Date(versionedFile.lastModified())));
            saveEntries(false);
        }
        return !equals;
    }

    public boolean hasProperties(String entryName) throws SVNException {
        File propFile;
        File baseFile;
        if (getThisDirName().equals(entryName)) {
            propFile = getAdminFile("dir-props");
            baseFile = getAdminFile("dir-prop-base");
        } else {
            propFile = getAdminFile("props/" + entryName + ".svn-work");
            baseFile = getAdminFile("prop-base/" + entryName + ".svn-base");
        }
        SVNProperties baseProps = new SVNProperties(baseFile, null);
        if (baseProps.isEmpty()) {
            SVNProperties props = new SVNProperties(propFile, null);
            return !props.isEmpty();
        }
        return true;
    }

    public boolean lock() throws SVNException {
        if (!isVersioned()) {
            return false;
        }
        return innerLock();
    }

    boolean innerLock() throws SVNException {
        if (myLockFile.isFile()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked; try performing ''cleanup''", getRoot());
            SVNErrorManager.error(err);
        }
        boolean created = false;
        try {
            created = myLockFile.createNewFile();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}'': {1}", 
                    new Object[] {getRoot(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        }
        if (!created) {
            if (myLockFile.isFile()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' is locked; try performing 'cleanup'", getRoot());
                SVNErrorManager.error(err);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
        }
        return created;
    }

    public boolean unlock() throws SVNException {
        if (!myLockFile.exists()) {
            return true;
        }
        // only if there are not locks or killme files.
        boolean killMe = getAdminFile("KILLME").exists();
        if (killMe) {
            return false;
        }
        File[] logs = getAdminDirectory().listFiles();
        for (int i = 0; logs != null && i < logs.length; i++) {
            File log = logs[i];
            if ("log".equals(log.getName()) || log.getName().startsWith("log.")) {
                return false;
            }

        }
        boolean deleted = myLockFile.delete();
        if (!deleted) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Failed to unlock working copy ''{0}''", getRoot());
            SVNErrorManager.error(err);
        }
        return deleted;
    }

    public SVNAdminArea upgradeFormat(SVNAdminArea adminArea) throws SVNException {
        return this;
    }

    public boolean isVersioned() {
        if (getAdminDirectory().isDirectory() && myEntriesFile.canRead()) {
            try {
                if (getEntry("", false) != null) {
                    return true;
                }
            } catch (SVNException e) {
                //
            }
        }
        return false;
    }
    
    public SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, long revNumber, boolean createMyself) throws SVNException {
        dir = createMyself ? getRoot() : dir;
        dir.mkdirs();
        File adminDir = createMyself ? getAdminDirectory() : new File(dir, SVNFileUtil.getAdminDirectoryName());
        adminDir.mkdir();
        SVNFileUtil.setHidden(adminDir, true);
        // lock dir.
        File lockFile = createMyself ? myLockFile : new File(adminDir, "lock");
        SVNFileUtil.createEmptyFile(lockFile);
        SVNAdminUtil.createReadmeFile(adminDir);
        SVNFileUtil.createEmptyFile(createMyself ? getAdminFile("empty-file") : new File(adminDir, "empty-file"));
        File[] tmp = {
                createMyself ? getAdminFile("tmp") : new File(adminDir, "tmp"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "props") : new File(adminDir, "tmp" + File.separatorChar + "props"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "prop-base") : new File(adminDir, "tmp" + File.separatorChar + "prop-base"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "text-base") : new File(adminDir, "tmp" + File.separatorChar + "text-base"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "wcprops") : new File(adminDir, "tmp" + File.separatorChar + "wcprops"),
                createMyself ? getAdminFile("props") : new File(adminDir, "props"), 
                createMyself ? getAdminFile("prop-base") : new File(adminDir, "prop-base"),
                createMyself ? getAdminFile("text-base") : new File(adminDir, "text-base"), 
                createMyself ? getAdminFile("wcprops") : new File(adminDir, "wcprops")};
        for (int i = 0; i < tmp.length; i++) {
            tmp[i].mkdir();
        }
        SVNAdminUtil.createFormatFile(adminDir);

        SVNAdminArea adminArea = createMyself ? this : new SVNXMLAdminArea(dir);
        SVNEntry2 rootEntry = adminArea.getEntry(adminArea.getThisDirName(), true);
        if (rootEntry == null) {
            rootEntry = adminArea.addEntry(adminArea.getThisDirName());
        }
        if (url != null) {
            rootEntry.setURL(url);
        }
        rootEntry.setRepositoryRoot(rootURL);
        rootEntry.setRevision(revNumber);
        rootEntry.setKind(SVNNodeKind.DIR);
        if (uuid != null) {
            rootEntry.setUUID(uuid);
        }
        adminArea.saveEntries(true);
        
        // unlock dir.
        SVNFileUtil.deleteFile(lockFile);
        return adminArea;
    }

    public boolean isLocked() {
        return myLockFile.isFile();
    }

    protected int getFormatVersion() {
        return WC_FORMAT;
    }
    
    public void postUpgradeFormat(int format) throws SVNException {
    }

}
