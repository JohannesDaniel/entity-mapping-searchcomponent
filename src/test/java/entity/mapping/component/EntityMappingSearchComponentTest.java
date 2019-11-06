package entity.mapping.component;

import entity.mapping.component.model.SearchDefinition;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static entity.mapping.component.EntityMappingSearchComponent.GROUP_ID_FIELD;
import static entity.mapping.component.EntityMappingSearchComponent.ID_FIELD;
import static entity.mapping.component.EntityMappingSearchComponent.ORIGINAL_FIELD;
import static entity.mapping.component.EntityMappingSearchComponent.SEARCH_DEF_ID_FIELD;
import static entity.mapping.component.EntityMappingSearchComponent.TOKEN_COUNT_FIELD;
import static entity.mapping.component.EntityMappingSearchComponent.VARIANT_FIELD;

@SolrTestCaseJ4.SuppressSSL
public class EntityMappingSearchComponentTest extends SolrTestCaseJ4 {

    @BeforeClass
    public static void before() throws Exception {
        SolrTestCaseJ4.initCore("solrconfig.xml", "schema.xml");
        addDocuments();
    }

    private static void addDocuments() {
        assertU(adoc(ID_FIELD, "1",
                TOKEN_COUNT_FIELD, "1",
                SEARCH_DEF_ID_FIELD, "1",
                ORIGINAL_FIELD, "i phone",
                GROUP_ID_FIELD, "iphone",
                VARIANT_FIELD, "iphone"));
        assertU(adoc(ID_FIELD, "2",
                TOKEN_COUNT_FIELD, "1",
                SEARCH_DEF_ID_FIELD, "1",
                ORIGINAL_FIELD, "i phone",
                GROUP_ID_FIELD, "iphone",
                VARIANT_FIELD, "i phone"));
        assertU(adoc(ID_FIELD, "3",
                TOKEN_COUNT_FIELD, "1",
                SEARCH_DEF_ID_FIELD, "1",
                ORIGINAL_FIELD, "ihphone",
                GROUP_ID_FIELD, "iphone",
                VARIANT_FIELD, "ihphone"));
        assertU(commit());
    }

    @Test
    public void testBasicMatching() {

        SolrQueryRequest req = req("q.id", "1",
                "q.0.t.0.text", "iphone",
                "q.0.t.0.fuzzy", "false");

        assertQ("Something",
                req,
                "/response/lst[@name='entity_mapping']/str[@name='has_match'][contains(.,'true')]",
                "/response/lst[@name='entity_mapping']/lst[@name='doc']/str[@name='id'][contains(.,'1')]"
        );

        req.close();
    }

    @Test
    public void testFuzzyMatching() {

        SolrQueryRequest req = req("q.id", "1",
                "q.0.t.0.text", "ipone",
                "q.0.t.0.fuzzy", "true",
                "q.0.t.0.var", "1",
                "q.0.t.0.prefix", "2");

        assertQ("Something",
                req,
                "/response/lst[@name='entity_mapping']/str[@name='has_match'][contains(.,'true')]",
                "/response/lst[@name='entity_mapping']/lst[@name='doc']/str[@name='id'][contains(.,'1')]"
        );

        req.close();
    }

    @Test
    public void testParseParams() throws IOException {

        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add("q.id", "1");
        solrParams.add("q.0.t.0.text", "samsung");
        solrParams.add("q.0.t.0.fuzzy", "false");
        solrParams.add("q.0.t.1.text", "galaxy");
        solrParams.add("q.0.t.1.fuzzy", "true");
        solrParams.add("q.0.t.1.var", "1");
        solrParams.add("q.0.t.1.prefix", "3");
        solrParams.add("q.1.t.0.text", "samsung");
        solrParams.add("q.1.t.0.fuzzy", "false");
        solrParams.add("12", "false");
        solrParams.add("something", "false");

        EntityMappingSearchComponent searchComponent = new EntityMappingSearchComponent();
        SearchDefinition searchDefinition = searchComponent.parseParams(solrParams);

        assertEquals(searchDefinition.searchDefId, "1");
        assertEquals("samsung", searchDefinition.searchTokenLists.get(0).get(0).text);
        assertFalse(searchDefinition.searchTokenLists.get(0).get(0).fuzzy);

        assertEquals("galaxy", searchDefinition.searchTokenLists.get(0).get(1).text);
        assertTrue(searchDefinition.searchTokenLists.get(0).get(1).fuzzy);
        assertEquals(3, searchDefinition.searchTokenLists.get(0).get(1).prefix);
        assertEquals(1, searchDefinition.searchTokenLists.get(0).get(1).variants);

        assertEquals("samsung", searchDefinition.searchTokenLists.get(1).get(0).text);
        assertFalse(searchDefinition.searchTokenLists.get(1).get(0).fuzzy);
    }

}
