package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.PropertyManager;
import io.goobi.workflow.api.connection.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.dl.Reference;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.UGHException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class MigrateVisualLibraryToGoobiStepPlugin implements IStepPluginVersion2 {

    private static final long serialVersionUID = 4361253140067822296L;

    @Getter
    private String title = "intranda_step_migrate_visual_library_to_goobi";

    @Getter
    private Step step;
    Process process;
    Prefs prefs;
    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    @Getter
    private String pagePath = null;

    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    private int interfaceVersion = 0;

    // just for JUnit tests
    @Setter
    private Element testResponse;

    private static final Namespace xlink = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");
    private static final Namespace mets = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
    private static final Namespace mods = Namespace.getNamespace("mods", "http://www.loc.gov/mods/v3");
    private static final Namespace oaiNamespace = Namespace.getNamespace("oai", "http://www.openarchives.org/OAI/2.0/");

    private Map<String, Element> dmdSecMap = new HashMap<>();
    private Element defaultFileGroup = null;
    private Element logicalStructMap = null;
    private Element physicalStructMap = null;
    private Element structLink = null;
    private transient List<ImageName> imageFiles = new ArrayList<>();

    private Map<String, DocStruct> pageMap = new HashMap<>();
    private Map<String, DocStruct> docstructMap = new HashMap<>();

    private DocStructType pageType;
    private DocStructType coverDocStructType;
    private DocStructType otherDocStructType;
    private DocStructType boundBookType;
    private MetadataType modsUrnType;
    private MetadataType phyPageNumberType;
    private MetadataType logPageNumberType;
    private MetadataType urnType;
    private MetadataType titleType;
    private MetadataType otherTitleType;
    private MetadataType subTitleType;
    private MetadataType partNumberType;
    private MetadataType partNameType;
    private MetadataType collectionType;
    private MetadataType sourceType;
    private MetadataType physicalLocationType;
    private MetadataType shelfLocatorType;
    private MetadataType placeOfElectronicOriginType;
    private MetadataType dateDigitizationType;
    private MetadataType electronicPublisherType;
    private MetadataType electronicEditionType;

    private MetadataType authorType;
    private MetadataType engraverType;
    private MetadataType honoreeType;
    private MetadataType editorType;
    private MetadataType otherPersonType;
    private MetadataType publicationYearType;
    private MetadataType placeOfPublicationType;
    private MetadataType publisherNameType;
    private MetadataType extentType;
    private MetadataType languageType;
    private MetadataType formatType;
    private MetadataType responsibilityType;
    private MetadataType titleMainSeriesType;
    private MetadataType currentNoMainSeriesType;
    private MetadataType seriesOrderType;
    private MetadataType catalogIDMainSeriesType;

    private String downloadUrl;
    private String identifier;
    private String anchorIdentifier;

    private Map<String, String> docStructRulesetNames = new HashMap<>();
    private DocStruct logical;
    private BeanHelper beanHelper;

    /**
     * Initialize the plugin with all needed defaults
     */
    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        this.process = step.getProzess();
        beanHelper = new BeanHelper();
        prefs = process.getRegelsatz().getPreferences();

        pageType = prefs.getDocStrctTypeByName("page");
        phyPageNumberType = prefs.getMetadataTypeByName("physPageNumber");
        logPageNumberType = prefs.getMetadataTypeByName("logicalPageNumber");
        urnType = prefs.getMetadataTypeByName("_urn");
        modsUrnType = prefs.getMetadataTypeByName("URN");

        boundBookType = prefs.getDocStrctTypeByName("BoundBook");
        otherDocStructType = prefs.getDocStrctTypeByName("OtherDocStrct");
        coverDocStructType = prefs.getDocStrctTypeByName("Cover");
        titleType = prefs.getMetadataTypeByName("TitleDocMain");
        otherTitleType = prefs.getMetadataTypeByName("OtherTitle");
        collectionType = prefs.getMetadataTypeByName("singleDigCollection");
        sourceType = prefs.getMetadataTypeByName("CatalogIDSource");
        physicalLocationType = prefs.getMetadataTypeByName("PhysicalLocation");
        shelfLocatorType = prefs.getMetadataTypeByName("shelfmarksource");
        languageType = prefs.getMetadataTypeByName("DocLanguage");
        publicationYearType = prefs.getMetadataTypeByName("PublicationYear");
        placeOfPublicationType = prefs.getMetadataTypeByName("PlaceOfPublication");
        publisherNameType = prefs.getMetadataTypeByName("PublisherName");
        placeOfElectronicOriginType = prefs.getMetadataTypeByName("_placeOfElectronicOrigin");
        dateDigitizationType = prefs.getMetadataTypeByName("_dateDigitization");
        electronicPublisherType = prefs.getMetadataTypeByName("_electronicPublisher");
        electronicEditionType = prefs.getMetadataTypeByName("_electronicEdition");
        extentType = prefs.getMetadataTypeByName("SizeSourcePrint");
        subTitleType = prefs.getMetadataTypeByName("TitleDocSub1");
        partNumberType = prefs.getMetadataTypeByName("VolumeNumber");
        partNameType = prefs.getMetadataTypeByName("VolumeName");
        formatType = prefs.getMetadataTypeByName("FormatSourcePrint");
        authorType = prefs.getMetadataTypeByName("Author");
        editorType = prefs.getMetadataTypeByName("Editor");
        honoreeType = prefs.getMetadataTypeByName("Honoree");
        engraverType = prefs.getMetadataTypeByName("Engraver");
        responsibilityType = prefs.getMetadataTypeByName("TitleDocMainResponsibility");
        otherPersonType = prefs.getMetadataTypeByName("OtherPerson");

        titleMainSeriesType = prefs.getMetadataTypeByName("TitleMainSeries");
        currentNoMainSeriesType = prefs.getMetadataTypeByName("CurrentNoMainSeries");
        seriesOrderType = prefs.getMetadataTypeByName("SeriesOrder");
        catalogIDMainSeriesType = prefs.getMetadataTypeByName("CatalogIDMainSeries");

        // just in case it is not a JUnit test
        if (testResponse == null) {
            SubnodeConfiguration config = ConfigPlugins.getProjectAndStepConfig(title, step);
            downloadUrl = getProcessProperty(step.getProzess(), config.getString("/vl-url"));
            identifier = getProcessProperty(step.getProzess(), config.getString("/vl-identifier"));
            anchorIdentifier = getProcessProperty(step.getProzess(), config.getString("/vl-identifier-volume"));
        }

        Path rulesetPath = Paths.get(ConfigurationHelper.getInstance().getRulesetFolder(), process.getRegelsatz().getDatei());
        Document rulesetDocument = XmlTools.readDocumentFromFile(rulesetPath);
        Element metsElement = rulesetDocument.getRootElement().getChild("Formats").getChild("METS");

        List<Element> docstructs = metsElement.getChildren("DocStruct");
        for (Element ds : docstructs) {
            docStructRulesetNames.put(ds.getChildText("MetsType").toLowerCase(), ds.getChildText("InternalName"));
        }

    }

    /**
     * This method is executed when the plugin is started
     */
    @Override
    public boolean execute() {

        try {
            // read mets file
            Fileformat fileformat = process.readMetadataFile();

            DigitalDocument digitalDocument = fileformat.getDigitalDocument();

            DocStruct anchor = null;
            logical = digitalDocument.getLogicalDocStruct();
            if (logical.getType().isAnchor()) {
                anchor = logical;
                logical = logical.getAllChildren().get(0);
            }
            DocStruct physical = digitalDocument.getPhysicalDocStruct();

            // cleanup metadata
            cleanupMetadata(logical);
            if (anchor != null) {
                cleanupMetadata(anchor);
            }

            if (identifier == null) {
                //  write error message to processlog
                Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, "No ID found.", "Visual Library Migration Plugin");
                return false;
            }
            log.info("Get data for record " + identifier);
            // clean current structure, remove all pages + structure elements
            // check if pagination was already written
            List<DocStruct> pages = physical.getAllChildren();
            if (pages != null && !pages.isEmpty()) {
                // process contains data, clear it
                for (DocStruct page : pages) {
                    digitalDocument.getFileSet().removeFile(page.getAllContentFiles().get(0));
                    List<Reference> refs = new ArrayList<>(page.getAllFromReferences());
                    for (ugh.dl.Reference ref : refs) {
                        ref.getSource().removeReferenceTo(page);
                    }
                }
                while (physical.getAllChildren() != null && !physical.getAllChildren().isEmpty()) {
                    physical.removeChild(physical.getAllChildren().get(0));
                }
                while (logical.getAllChildren() != null && !logical.getAllChildren().isEmpty()) {
                    logical.removeChild(logical.getAllChildren().get(0));
                }
            }

            // use id to search in and/or ddb
            // get mets record
            Element element = getRecord(identifier);
            if (element == null) {
                // no  record found
                Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, "No  record found for ID " + identifier,
                        "Visual Library Migration Plugin");
                return false;
            }

            if (StringUtils.isNotBlank(anchorIdentifier)) {
                Element anchorRecord = getRecord(anchorIdentifier);
                if (anchorRecord != null) {
                    String mdId = "md" + anchorIdentifier;
                    List<Element> dmdSections = anchorRecord.getChildren("dmdSec", mets);
                    for (Element dmdSec : dmdSections) {
                        if (mdId.equals(dmdSec.getAttributeValue("ID"))) {
                            parseModsElement(dmdSec, anchor);
                        }
                    }
                }
            }

            importRecord(digitalDocument, element);

            // assign all pages to top element
            assignPagesToUpperElement(logical);

            // save
            process.writeMetadataFile(fileformat);
            beanHelper.EigenschaftHinzufuegen(process, "VL Final URL", downloadUrl + identifier);

        } catch (ReadException | PreferencesException | WriteException | IOException | SwapException e) {
            // write error message to processlog
            Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, "Error during  import " + e.getMessage(),
                    "Visual Library Migration Plugin");
            return false;
        }

        //  download images
        return downloadImages();

    }

    /**
     * assign the pages of all sub elements to the given element as well
     *
     * @param in parent docstruct
     */
    private void assignPagesToUpperElement(DocStruct in) {
        for (DocStruct child : in.getAllChildrenAsFlatList()) {
            List<Reference> childRefs = child.getAllReferences("to");
            for (Reference toAdd : childRefs) {
                boolean existsAlready = false;
                for (Reference ref : in.getAllReferences("to")) {
                    if (ref.getTarget().equals(toAdd.getTarget())) {
                        existsAlready = true;
                        break;
                    }
                }
                if (!existsAlready) {
                    in.getAllReferences("to").add(toAdd);
                }
            }
        }

    }

    private void cleanupMetadata(DocStruct logical) {
        List<Metadata> existingMetadata = new ArrayList<>(logical.getAllMetadata());
        for (Metadata md : existingMetadata) {
            if (!"CatalogIDDigital".equals(md.getType().getName())) {
                logical.removeMetadata(md);
            }
        }
        if (logical.getAllPersons() != null) {
            List<Person> persons = new ArrayList<>(logical.getAllPersons());
            for (Person p : persons) {
                logical.removePerson(p);
            }
        }
    }

    /**
     * download all images into the media folder
     *
     * @return
     */
    private boolean downloadImages() {
        if (defaultFileGroup == null) {
            // no file group found, abort
            return true;
        }

        try {
            Path folder = Paths.get(process.getImagesTifDirectory(false));
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }

            if (testResponse != null) {
                // in case of a JUnit Test
                for (ImageName imageFile : imageFiles) {
                    Path path = Paths.get(folder.toString(), imageFile.getName());
                    Files.createFile(path);
                }
            } else {
                // in case of real live usage
                for (ImageName imageFile : imageFiles) {
                    String url = imageFile.getUrl();

                    String filename = imageFile.getName();
                    Path file = Paths.get(folder.toString(), filename);
                    try {
                        OutputStream out = Files.newOutputStream(file);
                        HttpUtils.getStreamFromUrl(out, url);
                        out.close();
                    } catch (Exception e) {
                        log.error("Error during image download from {}, retry in 5 sec", url);
                        Thread.sleep(5000l);
                        OutputStream out = Files.newOutputStream(Paths.get(folder.toString(), filename));
                        HttpUtils.getStreamFromUrl(out, url);
                        out.close();
                    }
                    // delete 0 byte files
                    if (Files.isRegularFile(file) && Files.size(file) == 0) {
                        Files.delete(file);
                        Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, "Image download failed.",
                                "Visual Library Migration Plugin");
                        return false;
                    }

                }
            }
        } catch (IOException | InterruptedException | SwapException e) {
            log.error(e);
            return false;
        }
        return true;
    }

    /**
     * main import method
     * 
     * @param digitalDocument
     * @param rec
     */
    private void importRecord(DigitalDocument digitalDocument, Element rec) {
        DocStruct docstruct = digitalDocument.getLogicalDocStruct();
        if (docstruct.getType().isAnchor()) {
            docstruct = docstruct.getAllChildren().get(0);
        }

        // get content of record
        for (Element metsElement : rec.getChildren()) {
            if ("dmdSec".equals(metsElement.getName())) {
                dmdSecMap.put(metsElement.getAttributeValue("ID"), metsElement);
            } else if ("amdSec".equals(metsElement.getName())) {
                // do we need anything from mets:amdSec?
            } else if ("fileSec".equals(metsElement.getName())) {
                for (Element fileGroup : metsElement.getChildren()) {
                    if ("DEFAULT".equals(fileGroup.getAttributeValue("USE"))) {
                        defaultFileGroup = fileGroup;
                        for (Element file : defaultFileGroup.getChildren()) {
                            Element flocat = file.getChild("FLocat", mets);

                            String mimeType = file.getAttributeValue("MIMETYPE");
                            String id = file.getAttributeValue("ID");
                            String url = flocat.getAttributeValue("href", xlink);
                            String filename;
                            if (StringUtils.isNotBlank(mimeType)) {
                                // get extension from mimetype
                                filename = id + "." + mimeType.substring(mimeType.indexOf("/") + 1);
                            } else {
                                // use jpeg as default
                                filename = id + ".jpg";
                            }
                            ImageName imageName = new ImageName(imageFiles.size() + 1, id, url, mimeType, filename);
                            imageFiles.add(imageName);

                        }
                    }
                }
            } else if ("structMap".equals(metsElement.getName()) && "LOGICAL".equals(metsElement.getAttributeValue("TYPE"))) {
                logicalStructMap = metsElement;
            } else if ("structMap".equals(metsElement.getName()) && "PHYSICAL".equals(metsElement.getAttributeValue("TYPE"))) {
                physicalStructMap = metsElement;
            } else if ("structLink".equals(metsElement.getName())) {
                structLink = metsElement;
            }
        }

        if (physicalStructMap == null) {
            // anchor or invalid record, abort
            return;
        }

        // parse physical physicalStructMap
        DocStruct physical = digitalDocument.getPhysicalDocStruct();
        if (physical == null) {
            try {
                physical = digitalDocument.createDocStruct(boundBookType);
                digitalDocument.setPhysicalDocStruct(physical);
            } catch (TypeNotAllowedForParentException e) {
                log.error(e);
            }
        }
        MetadataType mdt = prefs.getMetadataTypeByName("pathimagefiles");
        List<? extends ugh.dl.Metadata> alleImagepfade = physical.getAllMetadataByType(mdt);
        if (alleImagepfade == null || alleImagepfade.isEmpty()) {
            try {
                Metadata newmd = new Metadata(mdt);
                newmd.setValue("file://" + process.getImagesTifDirectory(false));
                physical.addMetadata(newmd);
            } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException | IOException | SwapException e) {
                log.error(e);
            }
        }

        Element physSequence = physicalStructMap.getChild("div", mets);

        for (Element pageDiv : physSequence.getChildren("div", mets)) {
            String contentIds = pageDiv.getAttributeValue("CONTENTIDS");
            String id = pageDiv.getAttributeValue("ID");
            String order = pageDiv.getAttributeValue("ORDER");
            String orderLabel = pageDiv.getAttributeValue("ORDERLABEL");
            String imageName = null;
            for (Element fptr : pageDiv.getChildren("fptr", mets)) {
                String fileid = fptr.getAttributeValue("FILEID");
                for (ImageName in : imageFiles) {
                    if (in.getId().equals(fileid)) {
                        imageName = in.getName();
                        break;
                    }
                }
            }
            try {
                DocStruct page = digitalDocument.createDocStruct(pageType);

                Metadata physPageNo = new Metadata(phyPageNumberType);
                physPageNo.setValue(order);
                page.addMetadata(physPageNo);

                if (StringUtils.isNotBlank(orderLabel)) {
                    Metadata logicalPageNo = new Metadata(logPageNumberType);
                    logicalPageNo.setValue(orderLabel);
                    page.addMetadata(logicalPageNo);
                }
                if (StringUtils.isNotBlank(contentIds)) {
                    Metadata urn = new Metadata(urnType);
                    urn.setValue(contentIds);
                    page.addMetadata(urn);
                }
                page.setImageName(imageName);
                physical.addChild(page);
                pageMap.put(id, page);
            } catch (UGHException e) {
                log.error(e);
            }
        }

        // parse logical structMap
        Element mainDiv = logicalStructMap.getChild("div", mets);
        Element mptr = mainDiv.getChild("mptr", mets);

        if (mptr != null) {
            // fix for visual library data
            String divType = mainDiv.getAttributeValue("TYPE");
            if ("multivolume_work".equalsIgnoreCase(divType) || "periodical".equalsIgnoreCase(divType)) {
                // multi volume document found, skip first element as it is handled in different file
                mainDiv = mainDiv.getChild("div", mets);
            } else {
                log.info("Found mptr for typ " + divType);
            }
        }
        String id = mainDiv.getAttributeValue("ID");
        String dmdid = mainDiv.getAttributeValue("DMDID");
        String urn = mainDiv.getAttributeValue("CONTENTIDS");
        if (StringUtils.isNotBlank(urn)) {
            try {
                Metadata md = new Metadata(urnType);
                md.setValue(urn);
                docstruct.addMetadata(md);
            } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                log.error(e);
            }
        }

        Element dmdSec = dmdSecMap.get(dmdid);
        if (dmdSec != null) {
            parseModsElement(dmdSec, docstruct);
        }
        docstructMap.put(id, docstruct);
        for (Element subDiv : mainDiv.getChildren("div", mets)) {
            createDocstruct(subDiv, docstruct, digitalDocument);
        }

        // link pages to docstructs
        for (Element smLink : structLink.getChildren()) {
            String fromId = smLink.getAttributeValue("from", xlink);
            String toId = smLink.getAttributeValue("to", xlink);

            DocStruct logicalElement = docstructMap.get(fromId);
            DocStruct page = pageMap.get(toId);
            if (logicalElement != null) {
                if (page != null) {
                    logicalElement.addReferenceTo(page, "logical_physical");
                }
            } else {
                log.debug("Ignore missing logical reference " + fromId);
            }
        }
    }

    /**
     * parse a MODS element to extract metadata from there
     *
     * @param dmdSec
     * @param docstruct
     */
    private void parseModsElement(Element dmdSec, DocStruct docstruct) {
        Element modsElement = dmdSec.getChild("mdWrap", mets).getChild("xmlData", mets).getChild("mods", mods);

        List<Element> classificationList = modsElement.getChildren("classification", mods);
        for (Element classification : classificationList) {
            addMetadata(classification, collectionType, docstruct);
        }

        List<Element> titleInfos = modsElement.getChildren("titleInfo", mods);
        if (titleInfos != null) {
            for (Element titleInfo : titleInfos) {
                if ("alternative".equals(titleInfo.getAttributeValue("type"))) {
                    // other title
                    Element alternativeTitle = titleInfo.getChild("title", mods);
                    addMetadata(alternativeTitle, otherTitleType, docstruct);
                } else {
                    // <mods:title>
                    // TODO: nonSort 10704
                    Element titleElement = titleInfo.getChild("title", mods);
                    addMetadata(titleElement, titleType, docstruct);
                    // <mods:subTitle>
                    Element subTitle = titleInfo.getChild("subTitle", mods);
                    addMetadata(subTitle, subTitleType, docstruct);

                    // <mods:partNumber>
                    Element partNumber = titleInfo.getChild("partNumber", mods);
                    addMetadata(partNumber, partNumberType, docstruct);
                    // <mods:partName>
                    Element partName = titleInfo.getChild("partName", mods);
                    addMetadata(partName, partNameType, docstruct);
                }
            }
        }

        List<Element> names = modsElement.getChildren("name", mods);
        if (names != null) {
            for (Element name : names) {
                if ("personal".equals(name.getAttributeValue("type"))) {
                    Person person = null;
                    Element role = name.getChild("role", mods);
                    String roleTerm = null;
                    if (role != null) {
                        Element roleTermElement = role.getChildren().get(0);
                        roleTerm = roleTermElement.getText();
                    }
                    try {

                        switch (roleTerm) {

                            // Author
                            case "aut":
                                person = new Person(authorType);
                                break;

                            // Engraver
                            case "egr":
                                person = new Person(engraverType);
                                break;

                            // Honoree
                            case "dte":
                                person = new Person(honoreeType);
                                break;

                            // Editor
                            case "edt":
                                person = new Person(editorType);
                                break;

                            // OtherPerson
                            default:
                                person = new Person(otherPersonType);
                        }

                    } catch (MetadataTypeNotAllowedException e) {
                        log.error(e);
                    }
                    String authority = name.getAttributeValue("authority");
                    String authorityURI = name.getAttributeValue("authorityURI");
                    String valueURI = name.getAttributeValue("valueURI");
                    person.setAuthorityFile(authority, authorityURI, valueURI);

                    List<Element> namePartList = name.getChildren("namePart", mods);
                    String firstName = null;
                    String lastName = null;
                    if (namePartList != null) {
                        for (Element namePart : namePartList) {
                            if ("family".equals(namePart.getAttributeValue("type"))) {
                                lastName = namePart.getText();
                            } else {
                                firstName = namePart.getText();
                            }
                        }
                    }

                    if (person != null) {
                        person.setFirstname(firstName);
                        person.setLastname(lastName);
                        try {
                            docstruct.addPerson(person);
                        } catch (UGHException e) {
                            log.error("Person of type '" + person.getType().getName() + "' cannot be added to structural element '"
                                    + docstruct.getType().getName() + "'.", e);
                        }
                    }
                }
            }

            List<Element> originInfoList = modsElement.getChildren("originInfo", mods);
            if (originInfoList != null) {
                for (Element originInfo : originInfoList) {
                    if ("publication".equals(originInfo.getAttributeValue("eventType"))) {
                        Element place = originInfo.getChild("place", mods);
                        addMetadata(place.getChild("placeTerm", mods), placeOfPublicationType, docstruct);
                        addMetadata(originInfo.getChild("publisher", mods), publisherNameType, docstruct);
                        addMetadata(originInfo.getChild("dateIssued", mods), publicationYearType, docstruct);
                    } else {
                        Element place = originInfo.getChild("place", mods);
                        if (place != null) {
                            Element placeTerm = place.getChild("placeTerm", mods);
                            addMetadata(placeTerm, placeOfElectronicOriginType, docstruct);
                        }
                        Element dateIssued = originInfo.getChild("dateIssued", mods);
                        addMetadata(dateIssued, dateDigitizationType, docstruct);

                        Element publisher = originInfo.getChild("publisher", mods);
                        addMetadata(publisher, electronicPublisherType, docstruct);

                        Element edition = originInfo.getChild("edition", mods);
                        addMetadata(edition, electronicEditionType, docstruct);

                    }
                }

                List<Element> languages = modsElement.getChildren("language", mods);
                if (languages != null) {
                    for (Element lang : languages) {
                        addMetadata(lang.getChild("languageTerm", mods), languageType, docstruct);
                    }
                }

                Element physicalDescription = modsElement.getChild("physicalDescription", mods);
                if (physicalDescription != null) {

                    addMetadata(physicalDescription.getChild("extent", mods), extentType, docstruct);
                    for (Element note : physicalDescription.getChildren("note", mods)) {
                        addMetadata(note, formatType, docstruct);
                    }
                }

                List<Element> identifierList = modsElement.getChildren("identifier", mods);
                if (identifierList != null) {
                    for (Element identifier : identifierList) {
                        switch (identifier.getAttributeValue("type")) {
                            case "gbv":
                                // CatalogIDSource
                                addMetadata(identifier, sourceType, docstruct);
                                break;
                            case "urn":
                                // URN
                                addMetadata(identifier, modsUrnType, docstruct);
                                break;
                            case "hbz-idn":
                                // ???
                                break;
                            default:
                                // ignore other identifier types
                        }
                    }
                }

                Element location = modsElement.getChild("location", mods);
                if (location != null) {
                    // mods:location/mods:physicalLocation
                    Element physicalLocation = location.getChild("physicalLocation", mods);
                    addMetadata(physicalLocation, physicalLocationType, docstruct);

                    // mods:location/mods:shelfLocator
                    Element shelfLocator = location.getChild("shelfLocator", mods);
                    addMetadata(shelfLocator, shelfLocatorType, docstruct);
                }

                List<Element> notesList = modsElement.getChildren("note", mods);
                for (Element note : notesList) {
                    if ("statement of responsibility".equals(note.getAttributeValue("type"))) {
                        addMetadata(note, responsibilityType, docstruct);
                    }
                }

                List<Element> relatedItems = modsElement.getChildren("relatedItem", mods);
                for (Element relatedItem : relatedItems) {
                    if ("series".equals(relatedItem.getAttributeValue("type"))) {
                        Element relatedItemTitleInfo = relatedItem.getChild("titleInfo", mods);
                        if (relatedItemTitleInfo != null) {
                            addMetadata(relatedItemTitleInfo.getChild("title", mods), titleMainSeriesType, docstruct);
                        }
                        Element id = relatedItem.getChild("recordInfo", mods);
                        if (id != null) {
                            addMetadata(id.getChild("recordIdentifier", mods), catalogIDMainSeriesType, docstruct);
                        }
                        Element part = relatedItem.getChild("part", mods);
                        if (part != null) {
                            Element detail = part.getChild("detail", mods);
                            Element number = detail.getChild("number", mods);
                            addMetadata(number, seriesOrderType, docstruct);
                        }
                    }
                }
            }
        }
    }

    /**
     * add new metadata based on given type and with the value from an XML-Element to existing docstruct
     * 
     * @param element
     * @param type
     * @param docstruct
     */
    private void addMetadata(Element element, MetadataType type, DocStruct docstruct) {
        if (element != null) {
            try {
                Metadata md = new Metadata(type);
                md.setValue(element.getText());
                docstruct.addMetadata(md);
            } catch (UGHException e) {
                log.error("The metadata type '" + type.getName() + "'is not allowed inside of the docstruct '" + docstruct.getType().getName() + "'.",
                        e);
            }
        }
    }

    /**
     * Create a structure element
     * 
     * @param currentDiv
     * @param parentDocstruct
     * @param digDoc
     */
    private void createDocstruct(Element currentDiv, DocStruct parentDocstruct, DigitalDocument digDoc) {
        String id = currentDiv.getAttributeValue("ID");
        String dmdid = currentDiv.getAttributeValue("DMDID");
        String docType = currentDiv.getAttributeValue("TYPE");
        String contentids = currentDiv.getAttributeValue("CONTENTIDS");
        String label = currentDiv.getAttributeValue("LABEL");
        DocStruct docStruct = null;

        try {
            DocStructType dst = null;
            String prefsName = docStructRulesetNames.get(docType.toLowerCase());
            if (StringUtils.isNotBlank(prefsName)) {
                dst = prefs.getDocStrctTypeByName(prefsName);
            }
            if (dst == null) {

                switch (docType) {

                    // Cover
                    case "cover_front":
                    case "cover_back":
                        dst = coverDocStructType;
                        break;

                    // Other
                    default:
                        log.debug("Docstruct type {} is unknown, use 'other'.", docType);
                        dst = otherDocStructType;
                }

            }

            docStruct = digDoc.createDocStruct(dst);
        } catch (UGHException e) {
            log.error(e);
        }
        try {
            parentDocstruct.addChild(docStruct);
            docstructMap.put(id, docStruct);

            if (StringUtils.isNotBlank(dmdid)) {
                // parse dmdSec
                Element dmdSec = dmdSecMap.get(dmdid);
                parseModsElement(dmdSec, docStruct);
            } else if (StringUtils.isNotBlank(label)) {
                Metadata metadataTitle = new Metadata(titleType);
                metadataTitle.setValue(label);
                docStruct.addMetadata(metadataTitle);
            }

            if (StringUtils.isNotBlank(contentids)) {
                Metadata urn = new Metadata(urnType);
                urn.setValue(contentids);
                docStruct.addMetadata(urn);
            }

        } catch (UGHException e) {
            log.error(e);
        }

        for (Element subDiv : currentDiv.getChildren("div", mets)) {
            createDocstruct(subDiv, docStruct, digDoc);
        }
    }

    /**
     * get the METS file from the OAI-Interface for a given identifier
     *
     * @param identifier
     * @return
     * @throws SwapException
     * @throws IOException
     */
    private Element getRecord(String identifier) throws IOException, SwapException {

        Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Try to analyze METS file from: " + downloadUrl + identifier,
                "Visual Library Migration Plugin");

        if (StringUtils.isNotBlank(identifier)) {
            log.info("Get record for pid " + identifier);
            Element oaiPmh = null;

            if (testResponse != null) {
                // in case of a JUnit Test
                oaiPmh = testResponse;
            } else {
                // in case of real live usage

                // get METS-File from VL and store it inside of the import folder
                String xmlDocument = HttpUtils.getStringFromUrl(downloadUrl + identifier);
                FileUtils.write(new File(process.getImportDirectory(), "/oai_mets_" + identifier + ".xml"), xmlDocument, "UTF-8");

                // parse METS-File now
                Document document = XmlTools.readDocumentFromString(xmlDocument);
                oaiPmh = document.getRootElement();
            }

            Element rec = null;
            Element getRecord = oaiPmh.getChild("GetRecord", oaiNamespace);
            if (getRecord != null) {
                List<Element> elements = getRecord.getChildren();
                for (Element element : elements) {
                    if ("record".equals(element.getName())) {
                        rec = element;
                    } else if ("error".equals(element.getName())) {
                        log.error(element.getText());

                    }
                }
            }

            if (rec != null) {
                Element metadata = rec.getChild("metadata", oaiNamespace);
                return metadata.getChild("mets", mets);
            }

        }

        return null;
    }

    //    /**
    //     * get CatalogIDDigital from given docstruct element
    //     *
    //     * @param docStruct
    //     * @return
    //     */
    //    private String getIdentifier(DocStruct docStruct) {
    //        String id = null;
    //        if (docStruct != null && docStruct.getAllMetadata() != null) {
    //            for (Metadata md : docStruct.getAllMetadata()) {
    //                if ("CatalogIDDigital".equals(md.getType().getName())) {
    //                    id = md.getValue();
    //                }
    //            }
    //        }
    //        return id;
    //    }

    /**
     * get process property
     * 
     * @param step
     * @return
     */
    private String getProcessProperty(Process process, String propertyName) {
        List<Processproperty> props = PropertyManager.getProcessPropertiesForProcess(process.getId());
        for (Processproperty p : props) {
            if (propertyName.equals(p.getTitel())) {
                return p.getWert();
            }
        }
        return null;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null; //NOSONAR
    }

    @Override
    public PluginReturnValue run() {
        return execute() ? PluginReturnValue.FINISH : PluginReturnValue.ERROR;
    }

    @Override
    public String cancel() {
        return null;
    }

    @Override
    public String finish() {
        return null;
    }

    @Getter
    @AllArgsConstructor
    class ImageName {
        private int order;
        private String id;
        private String url;
        private String mimetype;
        private String name;

    }
}
