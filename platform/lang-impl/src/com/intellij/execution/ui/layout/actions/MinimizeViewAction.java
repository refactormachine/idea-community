package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MinimizeViewAction extends BaseViewAction {

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(context, content, e.getPlace()));
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    for (Content each : content) {
      context.findCellFor(each).minimize(each);
    }
  }

  public static boolean isEnabled(ViewContext context, Content[] content, String place) {
    if (!context.isMinimizeActionEnabled() || content.length == 0) {
      return false;
    }

    if (isDetached(context, content[0])) {
      return false;
    }

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(place) || ViewContext.TAB_POPUP_PLACE.equals(place)) {
      return false;
    } else {
      return getTabFor(context, content).isDefault();
    }
  }

}