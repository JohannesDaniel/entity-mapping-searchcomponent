package entity.mapping.component.model;

import java.util.List;

public class SearchDefinition {
    public final String searchDefId;
    public final List<List<SearchToken>> searchTokenLists;

    public SearchDefinition(String searchDefId, List<List<SearchToken>> searchTokenLists) {
        this.searchDefId = searchDefId;
        this.searchTokenLists = searchTokenLists;
    }
}
