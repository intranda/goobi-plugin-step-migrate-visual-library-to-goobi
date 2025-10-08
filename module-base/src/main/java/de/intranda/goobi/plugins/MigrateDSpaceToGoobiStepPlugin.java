package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class MigrateDSpaceToGoobiStepPlugin extends MigrateVisualLibraryToGoobiStepPlugin implements IStepPluginVersion2 {
    private static final long serialVersionUID = 5240181655559762265L;

    // Halle: https://opendata.uni-halle.de/oai/dd?verb=GetRecord&metadataPrefix=mets&identifier=oai:opendata.uni-halle.de:1981185920/122454

    @Getter
    private String title = "intranda_step_migrate_dspace_to_goobi";

    @Override
    public String getDownloadUrl(String identifier) {
        return downloadUrl.replace("{identifier}", identifier);

    }
}
