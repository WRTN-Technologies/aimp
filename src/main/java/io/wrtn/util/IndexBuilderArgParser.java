package io.wrtn.util;

import static io.wrtn.util.JsonParser.gson;

import com.beust.jcommander.Parameter;
import io.wrtn.model.event.IndexBuildEvent;

public class IndexBuilderArgParser {

    public static final String HELP = "--help";
    @Parameter(names = HELP, description = "Display usage information", help = true)
    private boolean help;

    public boolean getHelp() {
        return help;
    }

    public static final String EVENT = "--event";
    @Parameter(names = EVENT, description = "indexBuildEvent", required = true)
    private String event;

    public IndexBuildEvent getEvent() {
        return gson.fromJson(event, IndexBuildEvent.class);
    }

}
