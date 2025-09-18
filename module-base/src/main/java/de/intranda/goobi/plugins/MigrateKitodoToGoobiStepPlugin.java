package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.goobi.production.enums.LogType;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.Element;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.exceptions.SwapException;
import io.goobi.workflow.api.connection.HttpUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class MigrateKitodoToGoobiStepPlugin extends MigrateVisualLibraryToGoobiStepPlugin implements IStepPluginVersion2 {
    private static final long serialVersionUID = 5240181655559762265L;

    // klein: https://mets.sub.uni-hamburg.de/kitodo/PPN1870896998
    // Heidelberg: https://digi.ub.uni-heidelberg.de/diglitData/mets/buchstabieren1786.xml
    // https://content.staatsbibliothek-berlin.de/dc/PPN1757097163.mets.xml
    // https://collections.thulb.uni-jena.de/servlets/MCRMETSServlet/HisBest_derivate_00036644?XSL.Style=dfg

    // https://digital.iai.spk-berlin.de/viewer/sourcefile?id={identifier}
    // https://gdz.sub.uni-goettingen.de/mets/{identifier}.mets.xml

    // https://content.staatsbibliothek-berlin.de/dc/{identifier}.mets.xml
    // https://mets.sub.uni-hamburg.de/kitodo/{identifier}
    // https://digi.ub.uni-heidelberg.de/diglitData/mets/{identifier}.xml
    // https://collections.thulb.uni-jena.de/servlets/MCRMETSServlet/{identifier}?XSL.Style=dfg

    // Rostock mycore benötigt komplette url: https://rosdok.uni-rostock.de/file/rosdok_document_0000026530/rosdok_derivate_0000225270/rosdok_ppn1934918776.dv.mets.xml

    // https://api.digitale-sammlungen.de/dfg/mets/mods/v1/digitalobjects/identifier/mdz-obj:bsb00162433
    // https://api.digitale-sammlungen.de/dfg/mets/mods/v1/digitalobjects/identifier/mdz-obj:bsb00113155

    @Getter
    private String title = "intranda_step_migrate_kitodo_to_goobi";

    /**
     * get the METS file from the OAI-Interface for a given identifier
     *
     * @param identifier
     * @return
     * @throws SwapException
     * @throws IOException
     */
    @Override
    public Element getRecord(String identifier) throws IOException, SwapException {

        Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Try to analyze METS file from: " + identifier,
                "Visual Library Migration Plugin");

        if (StringUtils.isNotBlank(identifier)) {
            log.info("Get record for pid " + identifier);

            // get METS-File from VL and store it inside of the import folder
            String xmlDocument = HttpUtils.getStringFromUrl(getDownloadUrl(identifier));
            FileUtils.write(new File(process.getImportDirectory(), "/oai_mets_" + identifier + ".xml"), xmlDocument, "UTF-8");

            // parse METS-File now
            Document document = XmlTools.readDocumentFromString(xmlDocument);

            Element rec = document.getRootElement();

            return rec;

        }

        return null;
    }

    @Override
    public String getDownloadUrl(String identifier) {
        return downloadUrl.replace("{identifier}", identifier);

    }
}
