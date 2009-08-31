package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.text.CharArrayUtil;

public class EndHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EndHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(final Editor editor, DataContext dataContext) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.SMART_END_ACTION){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    if (project == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    final CaretModel caretModel = editor.getCaretModel();
    final int caretOffset = caretModel.getOffset();
    CharSequence chars = editor.getDocument().getCharsSequence();
    int length = editor.getDocument().getTextLength();
    if (caretOffset < length){
      final int offset1 = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t");
      if (offset1 < 0 || chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r'){
        int offset2 = CharArrayUtil.shiftForward(chars, offset1 + 1, " \t");
        boolean isEmptyLine = offset2 >= length || chars.charAt(offset2) == '\n' || chars.charAt(offset2) == '\r';
        if (isEmptyLine){
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
              final String lineIndent = styleManager.getLineIndent(file, caretOffset);
              if (lineIndent != null) {
                int col = calcColumnNumber(lineIndent, editor.getSettings().getTabSize(project));
                int line = caretModel.getLogicalPosition().line;
                caretModel.moveToLogicalPosition(new LogicalPosition(line, col));

                if (caretModel.getLogicalPosition().column != col){
                  if (!document.isWritable() && !FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
                    return;
                  }
                  editor.getSelectionModel().removeSelection();
                  EditorModificationUtil.insertStringAtCaret(editor, lineIndent);
                }
              }

              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              editor.getSelectionModel().removeSelection();
            }

            private int calcColumnNumber(final String lineIndent, final int tabSize) {
              int result = 0;
              for (char c : lineIndent.toCharArray()) {
                if (c == ' ') result++;
                if (c == '\t') result += tabSize;
              }
              return result;
            }
          });
          return;
        }
      }
    }

    if (myOriginalHandler != null){
      myOriginalHandler.execute(editor, dataContext);
    }
  }
}