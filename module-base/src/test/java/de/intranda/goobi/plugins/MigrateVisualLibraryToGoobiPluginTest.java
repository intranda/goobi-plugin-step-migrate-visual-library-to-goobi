package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.beans.User;
import org.goobi.production.enums.PluginReturnValue;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetadatenHelper.class, VariableReplacer.class, ConfigurationHelper.class, Helper.class, ProcessManager.class,
        MetadataManager.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })

public class MigrateVisualLibraryToGoobiPluginTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private File processDirectory;
    private File metadataDirectory;
    private Process process;
    private Prefs prefs;
    private static String resourcesFolder;
    private BeanHelper beanHelper;

    @BeforeClass
    public static void setUpClass() {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Before
    public void setUp() throws Exception {

        metadataDirectory = folder.newFolder("metadata");

        processDirectory = new File(metadataDirectory + File.separator + "1");
        processDirectory.mkdirs();
        String metadataDirectoryName = metadataDirectory.getAbsolutePath() + File.separator;

        // copy meta.xml
        Path metaSource = Paths.get(resourcesFolder + "meta.xml");
        Path metaTarget = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Files.copy(metaSource, metaTarget);
        Path anchorSource = Paths.get(resourcesFolder + "meta_anchor.xml");
        Path anchorTarget = Paths.get(processDirectory.getAbsolutePath(), "meta_anchor.xml");
        Files.copy(anchorSource, anchorTarget);

        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getMaxDatabaseConnectionRetries()).andReturn(3).anyTimes();
        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();
        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectoryName).anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("00469418X_media").anyTimes();
        EasyMock.expect(configurationHelper.getProcessOcrAltoDirectoryName()).andReturn("00469418X_alto").anyTimes();
        EasyMock.expect(configurationHelper.isUseMasterDirectory()).andReturn(true).anyTimes();

        EasyMock.expect(configurationHelper.getNumberOfMetaBackups()).andReturn(0).anyTimes();
        EasyMock.replay(configurationHelper);

        beanHelper = EasyMock.createMock(BeanHelper.class);
        beanHelper.EigenschaftHinzufuegen((Process) EasyMock.anyObject(), EasyMock.anyString(), EasyMock.anyString());
        EasyMock.expectLastCall().anyTimes();
        EasyMock.replay(beanHelper);

        PowerMock.mockStatic(Helper.class);
        Helper.addMessageToProcessJournal(EasyMock.anyInt(), EasyMock.anyObject(), EasyMock.anyString(), EasyMock.anyString());
        PowerMock.expectLastCall().anyTimes();
        PowerMock.replayAll(Helper.class);

        PowerMock.mockStatic(ProcessManager.class);
        ProcessManager.saveProcess(EasyMock.anyObject());
        PowerMock.expectLastCall();
        PowerMock.replayAll(ProcessManager.class);

        PowerMock.mockStatic(VariableReplacer.class);
        EasyMock.expect(VariableReplacer.simpleReplace(EasyMock.anyString(), EasyMock.anyObject())).andReturn("00469418X_media").anyTimes();
        PowerMock.replay(VariableReplacer.class);
        prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        Fileformat ff = new MetsMods(prefs);
        ff.read(metaTarget.toString());

        PowerMock.mockStatic(MetadatenHelper.class);
        EasyMock.expect(MetadatenHelper.getMetaFileType(EasyMock.anyString())).andReturn("mets").anyTimes();
        EasyMock.expect(MetadatenHelper.getFileformatByName(EasyMock.anyString(), EasyMock.anyObject())).andReturn(ff).anyTimes();
        EasyMock.expect(MetadatenHelper.getMetadataOfFileformat(EasyMock.anyObject(), EasyMock.anyBoolean()))
                .andReturn(Collections.emptyMap())
                .anyTimes();
        PowerMock.replay(MetadatenHelper.class);

        PowerMock.mockStatic(MetadataManager.class);
        MetadataManager.updateMetadata(1, Collections.emptyMap());
        MetadataManager.updateJSONMetadata(1, Collections.emptyMap());
        PowerMock.replay(MetadataManager.class);
        PowerMock.replay(ConfigurationHelper.class);

        process = getProcess();

        Ruleset ruleset = EasyMock.createMock(Ruleset.class);
        EasyMock.expect(ruleset.getId()).andReturn(0).anyTimes();
        EasyMock.expect(ruleset.getDatei()).andReturn("ruleset.xml").anyTimes();
        EasyMock.expect(ruleset.getPreferences()).andReturn(prefs).anyTimes();
        EasyMock.replay(ruleset);

        process.setRegelsatz(ruleset);
    }

    @Test
    public void testConstructor() {
        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testValidate() {
        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        assertNull(plugin.validate());
    }

    @Test
    public void testCancel() {
        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        assertNull(plugin.cancel());
    }

    @Test
    public void testFinish() {
        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        assertNull(plugin.finish());
    }

    @Test
    public void testInit() {
        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        assertNotNull(plugin);
        Step step = process.getSchritte().get(0);
        plugin.setTestResponse(new Element("abc"));
        plugin.initialize(step, "");

        assertEquals("test step", plugin.getStep().getTitel());
        assertEquals("00469418X", plugin.process.getTitel());
        assertNotNull(plugin.prefs);

    }

    @Test
    public void testExecuteMultiVolume() throws Exception {
        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        assertNotNull(plugin);
        Step step = process.getSchritte().get(0);

        Fileformat mets = new MetsMods(prefs);
        mets.read(Paths.get(processDirectory.getAbsolutePath(), "meta.xml").toString());

        // check source file:
        DigitalDocument dd = mets.getDigitalDocument();
        DocStruct logical = dd.getLogicalDocStruct();
        DocStruct physical = dd.getPhysicalDocStruct();
        // no pages assigned
        assertNull(physical.getAllChildren());
        // logical is anchor, has only identifier metadata
        assertTrue(logical.getType().isAnchor());
        assertEquals(1, logical.getAllMetadata().size());
        // child has few metadata field and no further children
        DocStruct volume = logical.getAllChildren().get(0);
        assertEquals(2, volume.getAllMetadata().size());
        assertNull(volume.getAllChildren());

        SAXBuilder sb = new SAXBuilder();
        Document doc = sb.build(new File(resourcesFolder + "sample.xml"));
        Element root = doc.getRootElement();
        plugin.setTestResponse(root);
        plugin.initialize(step, "");

        // set bean helper mock
        plugin.setBeanHelper(beanHelper);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        // reload the metadata
        mets.read(Paths.get(processDirectory.getAbsolutePath(), "meta.xml").toString());

        dd = mets.getDigitalDocument();
        logical = dd.getLogicalDocStruct();
        physical = dd.getPhysicalDocStruct();
        volume = logical.getAllChildren().get(0);
        // now we have pages
        assertEquals(454, physical.getAllChildren().size());
        // and additional metadata
        assertEquals(15, logical.getAllMetadata().size());
        assertEquals(15, volume.getAllMetadata().size());
        // and sub elements
        assertEquals(21, volume.getAllChildren().size());

    }

    @Test
    public void testExecuteMonograph() throws Exception {
        Path metaSource = Paths.get(resourcesFolder + "monograph.xml");
        Path metaTarget = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Files.copy(metaSource, metaTarget, StandardCopyOption.REPLACE_EXISTING);

        MigrateVisualLibraryToGoobiStepPlugin plugin = new MigrateVisualLibraryToGoobiStepPlugin();
        SAXBuilder sb = new SAXBuilder();
        Document doc = sb.build(new File(resourcesFolder + "sample_monograph.xml"));
        Element root = doc.getRootElement();
        plugin.setTestResponse(root);
        Step step = process.getSchritte().get(0);
        plugin.initialize(step, "");

        // set bean helper mock
        plugin.setBeanHelper(beanHelper);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        Fileformat mets = new MetsMods(prefs);
        mets.read(Paths.get(processDirectory.getAbsolutePath(), "meta.xml").toString());

        DigitalDocument dd = mets.getDigitalDocument();
        DocStruct logical = dd.getLogicalDocStruct();
        DocStruct physical = dd.getPhysicalDocStruct();

        assertEquals(40, physical.getAllChildren().size());
        assertEquals(16, logical.getAllMetadata().size());
        assertEquals(7, logical.getAllChildren().size());

    }

    public Process getProcess() {
        Project project = new Project();
        project.setTitel("SampleProject");

        Process proc = new Process();
        proc.setTitel("00469418X");
        proc.setProjekt(project);
        proc.setId(1);
        List<Step> steps = new ArrayList<>();
        Step s1 = new Step();
        s1.setReihenfolge(1);
        s1.setProzess(proc);
        s1.setTitel("test step");
        s1.setBearbeitungsstatusEnum(StepStatus.OPEN);
        User user = new User();
        user.setVorname("Firstname");
        user.setNachname("Lastname");
        s1.setBearbeitungsbenutzer(user);
        steps.add(s1);

        proc.setSchritte(steps);

        createProcessDirectory(processDirectory);

        return proc;
    }

    private void createProcessDirectory(File processDirectory) {

        // image folder
        File imageDirectory = new File(processDirectory.getAbsolutePath(), "images");
        imageDirectory.mkdir();
        // master folder
        File masterDirectory = new File(imageDirectory.getAbsolutePath(), "00469418X_master");
        masterDirectory.mkdir();

        // media folder
        File mediaDirectory = new File(imageDirectory.getAbsolutePath(), "00469418X_media");
        mediaDirectory.mkdir();
    }
}
