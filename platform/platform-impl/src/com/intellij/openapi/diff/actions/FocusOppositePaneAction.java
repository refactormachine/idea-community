package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.DumbAware;

/**
 * @author yole
 */
public class FocusOppositePaneAction extends AnAction implements DumbAware {
  public FocusOppositePaneAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(final AnActionEvent e) {
    final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    assert diffPanel != null;
    diffPanel.focusOppositeSide();
  }

  @Override
  public void update(final AnActionEvent e) {
    final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    e.getPresentation().setEnabled(diffPanel != null);
  }
}