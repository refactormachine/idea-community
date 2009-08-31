package com.intellij.xdebugger.impl.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.DebuggerLogConsoleManager;
import com.intellij.diagnostic.logging.LogConsoleImpl;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class DebuggerLogConsoleManagerBase implements DebuggerLogConsoleManager, Disposable {
  private final Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private final Map<AdditionalTabComponent, ContentManagerListener> myContentListeners =
    new HashMap<AdditionalTabComponent, ContentManagerListener>();
  private final Project myProject;
  private final LogFilesManager myManager;
  protected ExecutionEnvironment myEnvironment;
  private final Icon DEFAULT_TAB_COMPONENT_ICON = IconLoader.getIcon("/fileTypes/text.png");

  public DebuggerLogConsoleManagerBase(Project project) {
    myProject = project;
    myManager = new LogFilesManager(project, this);
  }

  public abstract RunContentDescriptor getRunContentDescriptor();

  public abstract RunnerLayoutUi getUi();

  protected void registerFileMatcher(final RunProfile runConfiguration) {
    if (runConfiguration instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)runConfiguration);
    }
  }

  protected void initLogConsoles(final RunProfile runConfiguration, final ProcessHandler processHandler) {
    if (runConfiguration instanceof RunConfigurationBase && ((RunConfigurationBase)runConfiguration).needAdditionalConsole()) {
      myManager.initLogConsoles((RunConfigurationBase)runConfiguration, processHandler);
    }
  }

  // TODO[oleg]: talk to nick
  public void setEnvironment(@NotNull final ExecutionEnvironment env) {
    myEnvironment = env;
  }

  public void addLogConsole(final String name, final String path, final long skippedContent, Icon icon) {
    final Ref<Content> content = new Ref<Content>();

    final LogConsoleImpl log = new LogConsoleImpl(myProject, new File(path), skippedContent, name, false) {
      public boolean isActive() {
        final Content logContent = content.get();
        return logContent != null && logContent.isSelected();
      }
    };
    log.attachStopLogConsoleTrackingListener(getRunContentDescriptor().getProcessHandler());
    // Attach custom log handlers
    if (myEnvironment != null && myEnvironment.getRunProfile() instanceof RunConfigurationBase) {
      ((RunConfigurationBase)myEnvironment.getRunProfile()).customizeLogConsole(log);
    }

    content.set(addLogComponent(log, icon));
    final ContentManagerAdapter l = new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        log.activate();
      }
    };
    myContentListeners.put(log, l);
    getUi().addListener(l, this);
  }

  public void addLogConsole(String name, String path, long skippedContent) {
    addLogConsole(name, path, skippedContent, DEFAULT_TAB_COMPONENT_ICON);
  }

  public void removeLogConsole(final String path) {
    LogConsoleImpl componentToRemove = null;
    for (AdditionalTabComponent tabComponent : myAdditionalContent.keySet()) {
      if (tabComponent instanceof LogConsoleImpl) {
        final LogConsoleImpl console = (LogConsoleImpl)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          componentToRemove = console;
          break;
        }
      }
    }
    if (componentToRemove != null) {
      getUi().removeListener(myContentListeners.remove(componentToRemove));
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  public void addAdditionalTabComponent(AdditionalTabComponent tabComponent, String id, Icon icon) {
    addLogComponent(tabComponent, id, icon);
  }

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent, final String id) {
    addLogComponent(tabComponent, id);
  }

  private void addLogComponent(AdditionalTabComponent component, String id) {
    addLogComponent(component, id, DEFAULT_TAB_COMPONENT_ICON);
  }

  private Content addLogComponent(AdditionalTabComponent tabComponent, Icon icon) {
    @NonNls final String id = "Log-" + tabComponent.getTabTitle();
    return addLogComponent(tabComponent, id, icon);
  }

  private Content addLogComponent(final AdditionalTabComponent tabComponent, String id, Icon icon) {
    final Content logContent = getUi().createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(), icon,
                                                     tabComponent.getPreferredFocusableComponent());
    logContent.setCloseable(false);
    logContent.setDescription(tabComponent.getTooltip());
    myAdditionalContent.put(tabComponent, logContent);
    getUi().addContent(logContent);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });

    return logContent;
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    component.dispose();
    final Content content = myAdditionalContent.remove(component);
    getUi().removeContent(content, true);
  }

  protected Project getProject() {
    return myProject;
  }

  public void dispose() {
    myManager.unregisterFileMatcher();
  }
}