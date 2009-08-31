/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class EditorColorsManagerImpl extends EditorColorsManager
    implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl");

  private final Collection<EditorColorsListener> myListeners = new ArrayList<EditorColorsListener>();


  @NonNls private static final String NODE_NAME = "global_color_scheme";
  @NonNls private static final String SCHEME_NODE_NAME = "scheme";

  private String myGlobalSchemeName;
  public boolean USE_ONLY_MONOSPACED_FONTS = true;
  private final DefaultColorSchemesManager myDefaultColorSchemesManager;
  private final SchemesManager<EditorColorsScheme, EditorColorsSchemeImpl> mySchemesManager;
  @NonNls private static final String NAME_ATTR = "name";
  private static final String FILE_SPEC = "$ROOT_CONFIG$/colors";

  public EditorColorsManagerImpl(DefaultColorSchemesManager defaultColorSchemesManager, SchemesManagerFactory schemesManagerFactory) {
    myDefaultColorSchemesManager = defaultColorSchemesManager;

        mySchemesManager = schemesManagerFactory.createSchemesManager(
        FILE_SPEC,
        new SchemeProcessor<EditorColorsSchemeImpl>() {
          public EditorColorsSchemeImpl readScheme(final Document document)
              throws InvalidDataException, IOException, JDOMException {

            return loadSchemeFromDocument(document, true);
          }

          public Document writeScheme(final EditorColorsSchemeImpl scheme) throws WriteExternalException {
            Element root = new Element(SCHEME_NODE_NAME);
            try {
              scheme.writeExternal(root);
            }
            catch (WriteExternalException e) {
              LOG.error(e);
              return null;
            }

            return new Document(root);
          }

          public void renameScheme(final String name, final EditorColorsScheme scheme) {
            scheme.setName(name);
          }

          public boolean shouldBeSaved(final EditorColorsSchemeImpl scheme) {
            return !(scheme instanceof ReadOnlyColorsScheme);
          }

          public void initScheme(final EditorColorsSchemeImpl scheme) {
            
          }

          public void onSchemeAdded(final EditorColorsSchemeImpl scheme) {

          }

          public void onSchemeDeleted(final EditorColorsSchemeImpl scheme) {
          }

          public void onCurrentSchemeChanged(final Scheme newCurrentScheme) {
            fireChanges(mySchemesManager.getCurrentScheme());
          }
        }, RoamingType.PER_USER);


    addDefaultSchemes();
    // Load default schemes from providers
    loadAdditionalDefaultSchemes();
    loadAllSchemes();

    setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
  }

  private void loadAdditionalDefaultSchemes() {
    //Get color schemes from EPs
    for (BundledColorSchemesProvider provider : BundledColorSchemesProvider.EP_NAME.getExtensions()) {
      final String[] schemesPaths = provider.getBundledSchemesRelativePaths();

      for (final String schemePath : schemesPaths) {
        try {
          final InputStream inputStream = DecodeDefaultsUtil.getDefaultsInputStream(provider, schemePath);
          if (inputStream == null) {
            // Error shouldn't occur during this operation
            // thus we report error instead of info
            LOG.error("Cannot read scheme from " +  schemePath);
            continue;
          }

          final Document document;
          try {
            document = JDOMUtil.loadDocument(inputStream);
          }
          catch (JDOMException e) {
            LOG.info("Error reading scheme from  " + schemePath + ": " + e.getLocalizedMessage());
            throw e;
          }
          final EditorColorsSchemeImpl scheme = loadSchemeFromDocument(document, false);
          mySchemesManager.addNewScheme(scheme, false);
        }
        catch (final Exception e) {
          ApplicationManager.getApplication().invokeLater(
            new Runnable(){
              public void run() {
                // Error shouldn't occur during this operation
                // thus we report error instead of info
                LOG.error("Cannot read scheme from " + schemePath + ": " + e.getLocalizedMessage(), e);
              }
            }
          );
        }
      }
    }
  }

  private EditorColorsSchemeImpl loadSchemeFromDocument(final Document document,
                                                        final boolean isEditable)
    throws InvalidDataException {

    final Element root = document.getRootElement();

    if (root == null || !SCHEME_NODE_NAME.equals(root.getName())) {
      throw new InvalidDataException();
    }

    final EditorColorsSchemeImpl scheme = isEditable
       // editable scheme
       ? new EditorColorsSchemeImpl(null, DefaultColorSchemesManager.getInstance())
       //not editable scheme
       : new ReadOnlyColorsSchemeImpl(null, DefaultColorSchemesManager.getInstance());
    scheme.readExternal(root);
    return scheme;
  }

  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  // -------------------------------------------------------------------------
  // Schemes manipulation routines
  // -------------------------------------------------------------------------

  public void addColorsScheme(EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && scheme.getName().trim().length() > 0) {
      mySchemesManager.addNewScheme(scheme, true);
    }
  }

  public void removeAllSchemes() {
    mySchemesManager.clearAllSchemes();
    addDefaultSchemes();
  }

  private void addDefaultSchemes() {
    DefaultColorsScheme[] allDefaultSchemes = myDefaultColorSchemesManager.getAllSchemes();
    for (DefaultColorsScheme defaultScheme : allDefaultSchemes) {
      mySchemesManager.addNewScheme(defaultScheme, true);
    }
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public EditorColorsScheme[] getAllSchemes() {
    ArrayList<EditorColorsScheme> schemes = new ArrayList<EditorColorsScheme>(mySchemesManager.getAllSchemes());
    Collections.sort(schemes, new Comparator<EditorColorsScheme>() {
      public int compare(EditorColorsScheme s1, EditorColorsScheme s2) {
        if (isDefaultScheme(s1) && !isDefaultScheme(s2)) return -1;
        if (!isDefaultScheme(s1) && isDefaultScheme(s2)) return 1;

        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });

    return schemes.toArray(new EditorColorsScheme[schemes.size()]);
  }

  public void setGlobalScheme(EditorColorsScheme scheme) {
    mySchemesManager.setCurrentSchemeName(scheme == null ? DefaultColorSchemesManager.getInstance().getAllSchemes()[0].getName() : scheme.getName());
    fireChanges(scheme);
  }

  public EditorColorsScheme getGlobalScheme() {
    return mySchemesManager.getCurrentScheme();
  }

  public EditorColorsScheme getScheme(String schemeName) {
    return mySchemesManager.findSchemeByName(schemeName);
  }

  private void fireChanges(EditorColorsScheme scheme) {
    EditorColorsListener[] colorsListeners = myListeners.toArray(new EditorColorsListener[myListeners.size()]);
    for (EditorColorsListener colorsListener : colorsListeners) {
      colorsListener.globalSchemeChange(scheme);
    }
  }

  // -------------------------------------------------------------------------
  // Routines responsible for loading & saving colors schemes.
  // -------------------------------------------------------------------------

  private void loadAllSchemes() {
    mySchemesManager.loadSchemes();
  }

  private static File getColorsDir(boolean create) {
    @NonNls String directoryPath = PathManager.getConfigPath() + File.separator + "colors";
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        LOG.error("Cannot create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }


  public void addEditorColorsListener(EditorColorsListener listener) {
    myListeners.add(listener);
  }

  public void removeEditorColorsListener(EditorColorsListener listener) {
    myListeners.remove(listener);
  }

  public void setUseOnlyMonospacedFonts(boolean b) {
    USE_ONLY_MONOSPACED_FONTS = b;
  }

  public boolean isUseOnlyMonospacedFonts() {
    return USE_ONLY_MONOSPACED_FONTS;
  }

  public String getExternalFileName() {
    return "colors.scheme";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getColorsDir(true), PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return OptionsBundle.message("options.color.schemes.presentable.name");
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    Element element = parentNode.getChild(NODE_NAME);
    if (element != null) {
      String name = element.getAttributeValue(NAME_ATTR);
      if (name != null && !"".equals(name.trim())) {
        myGlobalSchemeName = name;
      }
    }

    initGlobalScheme();
  }

  private void initGlobalScheme() {
    if (myGlobalSchemeName != null) {
      setGlobalSchemeByName(myGlobalSchemeName);
    }
    else {
      setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
    }
  }

  private void setGlobalSchemeByName(String schemeName) {
    setGlobalScheme(mySchemesManager.findSchemeByName(schemeName));
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    if (mySchemesManager.getCurrentScheme() != null) {
      Element element = new Element(NODE_NAME);
      element.setAttribute(NAME_ATTR, mySchemesManager.getCurrentScheme().getName());
      parentNode.addContent(element);
    }
  }

  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  public String getComponentName() {
    return "EditorColorsManagerImpl";
  }

  public SchemesManager<EditorColorsScheme, EditorColorsSchemeImpl> getSchemesManager() {
    return mySchemesManager;
  }
}