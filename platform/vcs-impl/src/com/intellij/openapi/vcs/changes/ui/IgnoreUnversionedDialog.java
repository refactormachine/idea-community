/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author  yole
 */
public class IgnoreUnversionedDialog extends DialogWrapper {
  private JRadioButton myIgnoreSpecifiedFileRadioButton;
  private JRadioButton myIgnoreAllFilesUnderRadioButton;
  private TextFieldWithBrowseButton myIgnoreDirectoryTextField;
  private JRadioButton myIgnoreAllFilesMatchingRadioButton;
  private JTextField myIgnoreMaskTextField;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myIgnoreFileTextField;
  private List<VirtualFile> myFilesToIgnore;
  private final Project myProject;
  private boolean myInternalChange;
  private final IgnoredPathPresentation myPresentation;

  public IgnoreUnversionedDialog(final Project project) {
    super(project, false);
    myProject = project;
    myPresentation = new IgnoredPathPresentation(myProject);
    setTitle(VcsBundle.message("ignored.edit.title"));
    init();
    myIgnoreFileTextField.addBrowseFolderListener("Select File to Ignore",
                                                  "Select the file which will not be tracked for changes",
                                                  project,
                                                  new FileChooserDescriptor(true, false, false, true, false, false));
    myIgnoreFileTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        // on text change, clear remembered files to ignore
        if (!myInternalChange) {
          myFilesToIgnore = null;
        }
      }
    });
    myIgnoreDirectoryTextField.addBrowseFolderListener("Select Directory to Ignore",
                                                       "Select the directory which will not be tracked for changes",
                                                       project,
                                                       new FileChooserDescriptor(false, true, false, false, false, false));
    ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateControls();
      }
    };
    myIgnoreAllFilesUnderRadioButton.addActionListener(listener);
    myIgnoreAllFilesMatchingRadioButton.addActionListener(listener);
    myIgnoreSpecifiedFileRadioButton.addActionListener(listener);
    updateControls();
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("ignoreUnversionedFilesDialog");
  }

  private void updateControls() {
    myIgnoreDirectoryTextField.setEnabled(myIgnoreAllFilesUnderRadioButton.isSelected() && getDirectoriesToIgnore() <= 1);
    myIgnoreMaskTextField.setEnabled(myIgnoreAllFilesMatchingRadioButton.isSelected());
    myIgnoreFileTextField.setEnabled(myIgnoreSpecifiedFileRadioButton.isSelected() &&
                                     (myFilesToIgnore == null || (myFilesToIgnore.size() == 1 && !myFilesToIgnore.get(0).isDirectory())));
  }

  private int getDirectoriesToIgnore() {
    int result = 0;
    if (myFilesToIgnore != null) {
      for(VirtualFile f: myFilesToIgnore) {
        if (f.isDirectory()) {
          result++;
        }
      }
    }
    return result;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void setFilesToIgnore(List<VirtualFile> virtualFiles) {
    assert virtualFiles.size() > 0;
    myFilesToIgnore = virtualFiles;
    myInternalChange = true;
    try {
      if (virtualFiles.size() == 1) {
        VirtualFile projectDir = myProject.getBaseDir();
        String path = FileUtil.getRelativePath(new File(projectDir.getPresentableUrl()), new File(virtualFiles.get(0).getPresentableUrl()));
        myIgnoreFileTextField.setText(path);
      }
      else {
        myIgnoreFileTextField.setText(VcsBundle.message("ignored.edit.multiple.files", virtualFiles.size()));
      }
    }
    finally {
      myInternalChange = false;
    }

    int dirCount = 0;
    for(VirtualFile file: virtualFiles) {
      if (file.isDirectory()) {
        myIgnoreAllFilesUnderRadioButton.setSelected(true);
        myIgnoreSpecifiedFileRadioButton.setEnabled(false);
        myIgnoreFileTextField.setEnabled(false);
        dirCount++;
      }
    }
    updateControls();

    final VirtualFile[] ancestors = VfsUtil.getCommonAncestors(virtualFiles.toArray(new VirtualFile[virtualFiles.size()]));
    if (dirCount > 1) {
      myIgnoreDirectoryTextField.setText(VcsBundle.message("ignored.edit.multiple.directories", dirCount));
    }
    else if (ancestors.length > 0) {
      myIgnoreDirectoryTextField.setText(ancestors [0].getPresentableUrl());
    }
    else {
      myIgnoreDirectoryTextField.setText(virtualFiles.get(0).getParent().getPresentableUrl());
    }

    final Set<String> extensions = new HashSet<String>();
    for(VirtualFile vf: virtualFiles) {
      final String extension = vf.getExtension();
      if (extension != null) {
        extensions.add(extension);
      }
    }
    if (extensions.size() > 0) {
      final String[] extensionArray = extensions.toArray(new String[extensions.size()]);
      myIgnoreMaskTextField.setText("*." + extensionArray [0]);
    }
    else {
      myIgnoreMaskTextField.setText(virtualFiles.get(0).getPresentableName());
    }
  }

  public void setIgnoredFile(final IgnoredFileBean bean) {
    final String pathFromBean = bean.getPath();
    if (pathFromBean != null) {
      String path = pathFromBean.replace('/', File.separatorChar);
      if (path.endsWith(File.separator)) {
        myIgnoreAllFilesUnderRadioButton.setSelected(true);
        myIgnoreDirectoryTextField.setText(path);
      }
      else {
        myIgnoreSpecifiedFileRadioButton.setSelected(true);
        myIgnoreFileTextField.setText(path);
      }
    }
    else {
      myIgnoreAllFilesMatchingRadioButton.setSelected(true);
      myIgnoreMaskTextField.setText(bean.getMask());
    }
    updateControls();
  }

  public IgnoredFileBean[] getSelectedIgnoredFiles() {
    if (myIgnoreSpecifiedFileRadioButton.isSelected()) {
      if (myFilesToIgnore == null) {
        return new IgnoredFileBean[] { IgnoredBeanFactory.ignoreFile(myPresentation.alwaysRelative(myIgnoreFileTextField.getText()), myProject) };
      }
      return getBeansFromFilesToIgnore(false);
    }
    if (myIgnoreAllFilesUnderRadioButton.isSelected()) {
      if (getDirectoriesToIgnore() > 1) {
        return getBeansFromFilesToIgnore(true);
      }
      final String path = myIgnoreDirectoryTextField.getText();
      return new IgnoredFileBean[] { IgnoredBeanFactory.ignoreUnderDirectory(myPresentation.alwaysRelative(path), myProject) };
    }
    if (myIgnoreAllFilesMatchingRadioButton.isSelected()) {
      return new IgnoredFileBean[] { IgnoredBeanFactory.withMask(myIgnoreMaskTextField.getText()) };
    }
    return new IgnoredFileBean[0];
  }

  private IgnoredFileBean[] getBeansFromFilesToIgnore(boolean onlyDirs) {
    List<IgnoredFileBean> result = new ArrayList<IgnoredFileBean>();
    for (VirtualFile fileToIgnore : myFilesToIgnore) {
      String path = ChangesUtil.getProjectRelativePath(myProject, new File(fileToIgnore.getPath()));
      if (path != null) {
        path = FileUtil.toSystemIndependentName(path);
        if (fileToIgnore.isDirectory()) {
          result.add(IgnoredBeanFactory.ignoreUnderDirectory(path, myProject));
        }
        else if (!onlyDirs) {
          result.add(IgnoredBeanFactory.ignoreFile(path, myProject));
        }
      }
    }
    return result.toArray(new IgnoredFileBean[result.size()]);
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "IgnoreUnversionedDialog";
  }

  public static void ignoreSelectedFiles(final Project project, final List<VirtualFile> files) {
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(project);
    dlg.setFilesToIgnore(files);
    dlg.show();
    if (!dlg.isOK()) {
      return;
    }
    final IgnoredFileBean[] ignoredFiles = dlg.getSelectedIgnoredFiles();
    if (ignoredFiles.length > 0) {
      ChangeListManager.getInstance(project).addFilesToIgnore(ignoredFiles);
    }
  }
}