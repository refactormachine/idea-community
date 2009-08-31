package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class HideSideWindowsAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id == null) {
      id = toolWindowManager.getLastActiveToolWindowId();
    }
    toolWindowManager.hideToolWindow(id, true);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id != null) {
      presentation.setEnabled(true);
      return;
    }

    id = toolWindowManager.getLastActiveToolWindowId();
    if (id == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowEx toolWindow = (ToolWindowEx)toolWindowManager.getToolWindow(id);
    presentation.setEnabled(toolWindow.isVisible());
  }
}