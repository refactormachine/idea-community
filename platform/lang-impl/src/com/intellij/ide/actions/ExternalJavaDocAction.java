package com.intellij.ide.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.HashSet;

public class ExternalJavaDocAction extends AnAction {

  public ExternalJavaDocAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      Messages.showMessageDialog(
        project,
        IdeBundle.message("message.please.select.element.for.javadoc"),
        IdeBundle.message("title.no.element.selected"),
        Messages.getErrorIcon()
      );
      return;
    }


    PsiFile context = LangDataKeys.PSI_FILE.getData(dataContext);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiElement originalElement = getOriginalElement(context, editor);
    DocumentationManager.storeOriginalElement(project, originalElement, element);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    final List<String> urls = provider.getUrlFor(element, originalElement);
    assert urls != null;
    assert !urls.isEmpty();
    showExternalJavadoc(urls);
  }

  public static void showExternalJavadoc(List<String> urls) {
    final HashSet<String> set = new HashSet<String>(urls);
    if (set.size() > 1) {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>("Choose javadoc root", ArrayUtil.toStringArray(set)) {
        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          BrowserUtil.launchBrowser(selectedValue);
          return FINAL_CHOICE;
        }
      }).showInBestPositionFor(DataManager.getInstance().getDataContext());
    }
    else if (set.size() == 1) {
      BrowserUtil.launchBrowser(urls.get(0));
    }
  }

  @Nullable
  private static PsiElement getOriginalElement(final PsiFile context, final Editor editor) {
    return (context!=null && editor!=null)? context.findElementAt(editor.getCaretModel().getOffset()):null;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    final PsiElement originalElement = getOriginalElement(LangDataKeys.PSI_FILE.getData(dataContext), editor);
    DocumentationManager.storeOriginalElement(PlatformDataKeys.PROJECT.getData(dataContext), originalElement, element);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    final List<String> urls = provider.getUrlFor(element, originalElement);
    boolean enabled = urls != null && !urls.isEmpty();
    if (editor != null) {
      presentation.setEnabled(enabled);
      if (event.getPlace().equals(ActionPlaces.MAIN_MENU)) {
        presentation.setVisible(true);
      }
      else {
        presentation.setVisible(enabled);
      }
    }
    else{
      presentation.setEnabled(enabled);
      presentation.setVisible(true);
    }
  }
}