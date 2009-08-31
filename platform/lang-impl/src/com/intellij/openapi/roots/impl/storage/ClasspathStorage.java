package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Mar 9, 2007
 * Time: 1:42:06 PM
 */
public class ClasspathStorage implements StateStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.storage.ClasspathStorage");

  @NonNls public static final String DEFAULT_STORAGE = "default";
  @NonNls public static final String SPECIAL_STORAGE = "special";

  public static final String DEFAULT_STORAGE_DESCR = ProjectBundle.message("project.roots.classpath.format.default.descr");

  @NonNls public static final String CLASSPATH_OPTION = "classpath";
  @NonNls public static final String CLASSPATH_DIR_OPTION = "classpath-dir";

  @NonNls private static final String COMPONENT_TAG = "component";
  private Object mySession;
  private final ClasspathStorageProvider.ClasspathConverter myConverter;


  public ClasspathStorage(Module module) {
    myConverter = getProvider(getStorageType(module)).createConverter(module);
    final MessageBus messageBus = module.getMessageBus();
    final VirtualFileTracker virtualFileTracker = (VirtualFileTracker)module.getPicoContainer().getComponentInstanceOfType(VirtualFileTracker.class);
    if (virtualFileTracker != null && messageBus != null) {
      final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      try {
        myConverter.getFileSet().listFiles(files);
        for (VirtualFile file : files) {
          final Listener listener = messageBus.syncPublisher(STORAGE_TOPIC);
          virtualFileTracker.addTracker(file.getUrl(), new VirtualFileAdapter() {
            public void contentsChanged(final VirtualFileEvent event) {
              listener.storageFileChanged(event, ClasspathStorage.this);
            }
          }, true, module);
        }
      }
      catch (UnsupportedOperationException e) {
        //UnsupportedStorageProvider doesn't mean any files
      }
    }
  }

  private FileSet getFileSet() {
    return myConverter.getFileSet();
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert stateClass == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      final Module module = ((ModuleRootManagerImpl)component).getModule();
      final Element element = new Element(COMPONENT_TAG);
      final Set<String> macros;
      ModifiableRootModel model = null;
      try {
        model = ((ModuleRootManagerImpl)component).getModifiableModel();
        macros = myConverter.getClasspath(model, element);
      }
      finally {
        if (model != null) {
          model.dispose();
        }
      }
      final HashMap<String, String> map = new HashMap<String, String>();
      for (String v : macros) {
        map.put(v, null);
      }
      final boolean macrosOk = ProjectMacrosUtil.checkMacros(module.getProject(), map);
      PathMacroManager.getInstance(module).expandPaths(element);
      ModuleRootManagerImpl.ModuleRootManagerState moduleRootManagerState = new ModuleRootManagerImpl.ModuleRootManagerState();
      moduleRootManagerState.readExternal(element);
      if (!macrosOk) {
        throw new StateStorageException(ProjectBundle.message("project.load.undefined.path.variables.error"));
      }
      //noinspection unchecked
      return (T)moduleRootManagerState;
    }
    catch (InvalidDataException e) {
      throw new StateStorageException(e.getMessage());
    }
    catch (IOException e) {
      throw new StateStorageException(e.getMessage());
    }
  }

  public boolean hasState(final Object component, final String componentName, final Class<?> aClass)
    throws StateStorageException {
    return true;
  }

  public void setState(Object component, final String componentName, Object state) throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert state.getClass() == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      final ModifiableRootModel model = ((ModuleRootManagerImpl)component).getModifiableModel();
      myConverter.setClasspath(model);
      model.dispose();
    }
    catch (WriteExternalException e) {
      throw new StateStorageException(e.getMessage());
    }
    catch (IOException e) {
      throw new StateStorageException(e.getMessage());
    }
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    final ExternalizationSession session = new ExternalizationSession() {
      public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec)
        throws StateStorageException {
        assert mySession == this;
        ClasspathStorage.this.setState(component, componentName, state);
      }
    };

    mySession = session;
    return session;
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final SaveSession session = new SaveSession() {
      public boolean needsSave() throws StateStorageException {
        assert mySession == this;
        return ClasspathStorage.this.needsSave();
      }

      public void save() throws StateStorageException {
        assert mySession == this;
        ClasspathStorage.this.save();
      }

      public Set<String> getUsedMacros() {
        assert mySession == this;
        return Collections.EMPTY_SET;
      }

      @Nullable
      public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
        return null;
      }

      public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
        return needsSave() ? getAllStorageFiles() : Collections.<IFile>emptyList();
      }

      public List<IFile> getAllStorageFiles() {
        final List<IFile> list = new ArrayList<IFile>();
        final ArrayList<VirtualFile> virtualFiles = new ArrayList<VirtualFile>();
        getFileSet().listFiles(virtualFiles);

        for (VirtualFile virtualFile : virtualFiles) {
          final File ioFile = VfsUtil.virtualToIoFile(virtualFile);
          if (ioFile == null) continue;
          list.add(FileSystem.FILE_SYSTEM.createFile(ioFile.getAbsolutePath()));
        }

        return list;
      }
    };

    mySession = session;
    return session;
  }

  public void finishSave(final SaveSession saveSession) {
    try {
      LOG.assertTrue(mySession == saveSession);
    } finally {
      mySession = null;
    }
  }

  public void reload(final Set<String> changedComponents) throws StateStorageException {
  }

  public boolean needsSave() throws StateStorageException {
    return getFileSet().hasChanged();
  }

  public void save() throws StateStorageException {
    final Ref<IOException> ref = new Ref<IOException>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          getFileSet().commit();
        }
        catch (IOException e) {
          ref.set(e);
        }
      }
    });

    if (!ref.isNull()) {
      throw new StateStorageException(ref.get());
    }
  }

  public static ClasspathStorageProvider getProvider(final String type) {
    for (ClasspathStorageProvider provider : getProviders()) {
      if (type.equals(provider.getID())) {
        return provider;
      }
    }
    return new UnsupportedStorageProvider(type);
  }

  public static List<ClasspathStorageProvider> getProviders() {
    final List<ClasspathStorageProvider> list = new ArrayList<ClasspathStorageProvider>();
    list.add(new DefaultStorageProvider());
    list.addAll(Arrays.asList(Extensions.getExtensions(ClasspathStorageProvider.EXTENSION_POINT_NAME)));
    return list;
  }

  @NotNull
  public static String getStorageType(final Module module) {
    final String id = module.getOptionValue(CLASSPATH_OPTION);
    return id != null ? id : DEFAULT_STORAGE;
  }

  public static String getModuleDir(final Module module) {
    return new File(module.getModuleFilePath()).getParent();
  }

  public static String getStorageRootFromOptions(final Module module) {
    final String moduleRoot = getModuleDir(module);
    final String storageRef = module.getOptionValue(CLASSPATH_DIR_OPTION);
    if (storageRef == null) {
      return moduleRoot;
    }
    else if (new File(storageRef).isAbsolute()) {
      return storageRef;
    }
    else {
      return FileUtil.toSystemIndependentName(new File(moduleRoot, storageRef).getPath());
    }
  }

  public static void setStorageType(final ModifiableRootModel model, final String storageID) {
    final Module module = model.getModule();
    final String oldStorageType = getStorageType(module);
    if (oldStorageType.equals(storageID)) {
      return;
    }

    getProvider(oldStorageType).detach(module);

    if (storageID.equals(DEFAULT_STORAGE)) {
      module.clearOption(CLASSPATH_OPTION);
      module.clearOption(CLASSPATH_DIR_OPTION);
    }
    else {
      module.setOption(CLASSPATH_OPTION, storageID);
      final VirtualFile[] contentRoots = model.getContentRoots();
      assert contentRoots.length == 1;
      module.setOption(CLASSPATH_DIR_OPTION, contentRoots[0].getPath());
    }
  }

  public static void moduleRenamed(Module module, String newName) {
    getProvider(getStorageType(module)).moduleRenamed(module, newName);
  }

  private static class DefaultStorageProvider implements ClasspathStorageProvider {
    @NonNls
    public String getID() {
      return DEFAULT_STORAGE;
    }

    @Nls
    public String getDescription() {
      return DEFAULT_STORAGE_DESCR;
    }

    public void assertCompatible(final ModifiableRootModel model) throws ConfigurationException {
    }

    public void detach(Module module) {
    }

    public void moduleRenamed(Module module, String newName) {
      //do nothing
    }

    public ClasspathConverter createConverter(Module module) {
      throw new UnsupportedOperationException(getDescription());
    }
  }

  public static class UnsupportedStorageProvider implements ClasspathStorageProvider {
    private final String myType;

    public UnsupportedStorageProvider(final String type) {
      myType = type;
    }

    @NonNls
    public String getID() {
      return myType;
    }

    @Nls
    public String getDescription() {
      return "Unsupported classpath format " + myType;
    }

    public void assertCompatible(final ModifiableRootModel model) throws ConfigurationException {
      throw new UnsupportedOperationException(getDescription());
    }

    public void detach(final Module module) {
      throw new UnsupportedOperationException(getDescription());
    }

    public void moduleRenamed(Module module, String newName) {
      throw new UnsupportedOperationException(getDescription());
    }

    public ClasspathConverter createConverter(final Module module) {
      return new ClasspathConverter() {
        public FileSet getFileSet() {
          throw new StateStorageException(getDescription());
        }

        public Set<String> getClasspath(final ModifiableRootModel model, final Element element) throws IOException, InvalidDataException {
          throw new InvalidDataException(getDescription());
        }

        public void setClasspath(final ModifiableRootModel model) throws IOException, WriteExternalException {
          throw new WriteExternalException(getDescription());
        }
      };
    }
  }
}