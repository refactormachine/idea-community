/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.ProjectNameWithTypeStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class CreateFromScratchMode extends WizardMode {

  @NonNls private final Map<String, ModuleBuilder> myBuildersMap = new HashMap<String, ModuleBuilder>();

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.scratch.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.scratch.description", ApplicationNamesInfo.getInstance().getProductName(), context.getPresentationName());
  }

  @Nullable
  protected StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    final StepSequence sequence = new StepSequence(null);
    sequence.addCommonStep(new ProjectNameWithTypeStep(context, sequence, this));
    for (ModuleBuilder builder : ModuleBuilder.getAllBuilders()) {
      addModuleBuilder(builder, context, modulesProvider, sequence);
    }
    myBuildersMap.put(ModuleType.EMPTY.getId(), new EmptyModuleBuilder());
    return sequence;
  }

  private void addModuleBuilder(ModuleBuilder builder, WizardContext context, ModulesProvider modulesProvider, StepSequence myStepSequence) {
    final String id = builder.getBuilderId();
    final StepSequence sequence = new StepSequence(myStepSequence);
    myBuildersMap.put(id, builder);
    for (ModuleWizardStep step : builder.createWizardSteps(context, modulesProvider)) {
      sequence.addCommonStep(step);
    }
    if (!sequence.getCommonSteps().isEmpty()) {
      myStepSequence.addSpecificSteps(id, sequence);
    }
  }

  public boolean isAvailable(WizardContext context) {
    return true;
  }

  public ModuleBuilder getModuleBuilder() {
    return myBuildersMap.get(getSelectedType());
  }

  @Nullable
  public JComponent getAdditionalSettings() {
    return null;
  }

  public void onChosen(final boolean enabled) {
    
  }

  public void dispose() {
    super.dispose();
    myBuildersMap.clear();
  }
}