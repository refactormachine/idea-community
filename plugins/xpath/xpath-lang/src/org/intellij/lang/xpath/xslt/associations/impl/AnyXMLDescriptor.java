/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.associations.impl;

import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;

public class AnyXMLDescriptor extends FileChooserDescriptor {
    public static final AnyXMLDescriptor INSTANCE = new AnyXMLDescriptor();

    final FileTypeManager myFileTypeManager;

    public AnyXMLDescriptor() {
        super(true, false, false, false, false, true);
        myFileTypeManager = FileTypeManager.getInstance();
    }

    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        final FileType fileType = myFileTypeManager.getFileTypeByFile(file);
        return file.isDirectory() || (super.isFileVisible(file, showHiddenFiles)
                && FileAssociationsManager.XML_FILES_LIST.contains(fileType));
    }
}
