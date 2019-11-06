package entity.mapping.component;

import entity.mapping.component.model.SearchDefinition;
import entity.mapping.component.model.SearchToken;
import org.apache.commons.lang.CharUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class EntityMappingSearchComponent extends SearchComponent {

    private NamedList initParams;

    private static final int DEFAULT_RESULT_SIZE = 100;

    // could be refactored in a way that field names are provided as parameters or included into solrconfig.xml requesthandler props
    public static final String ID_FIELD = "id";
    public static final String ORIGINAL_FIELD = "original_s";
    public static final String VARIANT_FIELD = "variant_ws";
    public static final String GROUP_ID_FIELD = "group_id_s";
    public static final String TOKEN_COUNT_FIELD = "token_count_s";
    public static final String SEARCH_DEF_ID_FIELD = "search_def_id_ss";

    public static final String QUERY_PREFIX = "q.";
    public static final String TOKEN_PREFIX = ".t.";

    public static final String KEY_SEARCH_ID = "id";
    public static final String KEY_TEXT = "text";
    public static final String KEY_FUZZY = "fuzzy";
    public static final String KEY_VAR = "var";
    public static final String KEY_PREFIX = "prefix";


    private static final Logger logger = LoggerFactory.getLogger(EntityMappingSearchComponent.class);

    @Override
    @SuppressWarnings("unchecked")
    public void init(NamedList args) {
        super.init(args);
        this.initParams = args;
        /* parseInitialParams(this.initParams); */
    }

    /*
    private void parseInitialParams(NamedList args) {
        Object queryChainObj = args.get("queryChain");

        if (queryChainObj instanceof NamedList) {
            NamedList queryChain = (NamedList) queryChainObj;

            for (int i = 0; i < queryChain.size(); i++) {
                String queryDefName = queryChain.getName(i);

                Object entry = queryChain.getVal(i);
                if (entry instanceof NamedList) {
                    NamedList queryDefinition = (NamedList) entry;
                }
            }
        }

    }
    */

    private static final Set<String> excludedFields = new HashSet<>();
    static {
        excludedFields.add("_version_");
    }


    @Override
    public void prepare(ResponseBuilder responseBuilder) throws IOException {
        // nothing to do here
    }

    @Override
    public void process(ResponseBuilder responseBuilder) throws IOException {
        SolrIndexSearcher searcher = responseBuilder.req.getSearcher();
        SolrParams solrParams = responseBuilder.req.getParams();
        SearchDefinition searchDefinition = parseParams(solrParams);
        Query query = this.search(VARIANT_FIELD, searchDefinition.searchDefId, searchDefinition.searchTokenLists);
        Optional<Document> document = search(searcher, query);

        NamedList<Object> response = new SimpleOrderedMap<>();
        boolean hasMatch = document.isPresent();
        response.add("has_match", String.valueOf(hasMatch));

        if (hasMatch) {
            Document luceneDoc = document.get();
            NamedList<String> match = new SimpleOrderedMap<>();
            StreamSupport.stream(luceneDoc.spliterator(), false)
                    .map(IndexableField::name)
                    .filter(fieldName -> !excludedFields.contains(fieldName))
                    .forEach(fieldName -> match.add(fieldName, luceneDoc.get(fieldName)));

            response.add("doc", match);
        }
        responseBuilder.rsp.add("entity_mapping", response);
    }

    private Optional<Document> search(SolrIndexSearcher solrIndexSearcher, Query query) {
        try {
            return Arrays.stream(solrIndexSearcher.search(query, DEFAULT_RESULT_SIZE).scoreDocs)
                    .findFirst()
                    .map(scoreDoc -> getDoc(solrIndexSearcher, scoreDoc.doc));

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Document getDoc(SolrIndexSearcher solrIndexSearcher, int docId) {
        try {
            return solrIndexSearcher.doc(docId);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return new Document();
        }
    }

    private Query search(String searchField, String searchDefId, List<List<SearchToken>> searchTokenLists) {
        return new BooleanQuery.Builder()
                .add(
                        combineQueryByClause(searchTokenLists.stream()
                                .map(searchTokens -> searchTokens.stream()
                                        .map(searchToken -> mapTokenToQuery(searchField, searchToken))
                                        .collect(Collectors.toList()))
                                .map(queries -> new BooleanQuery.Builder()
                                        .add(combineQueryByClause(queries, BooleanClause.Occur.MUST), BooleanClause.Occur.MUST)
                                        .add(getFilterQuery(TOKEN_COUNT_FIELD, String.valueOf(queries.size())), BooleanClause.Occur.FILTER)
                                        .build())
                                .collect(Collectors.toList()), BooleanClause.Occur.SHOULD),
                        BooleanClause.Occur.MUST)

                .add(getFilterQuery(SEARCH_DEF_ID_FIELD, searchDefId), BooleanClause.Occur.FILTER)
                .build();
    }

    private Query combineQueryByClause(List<Query> queries, BooleanClause.Occur clause) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        queries.stream().map(query -> new BooleanClause(query, clause)).forEach(builder::add);
        return builder.build();
    }

    private Query mapTokenToQuery(String searchField, SearchToken searchToken) {
        if (searchToken.fuzzy) {
            return new FuzzyQuery(new Term(searchField, searchToken.text), searchToken.variants, searchToken.prefix);
        } else {
            return new TermQuery(new Term(searchField, searchToken.text));
        }
    }

    private Query getFilterQuery(String searchField, String match) {
        return new BooleanQuery.Builder()
                .add(new BooleanClause(new TermQuery(new Term(searchField, match)), BooleanClause.Occur.MUST))
                .build();
    }

    SearchDefinition parseParams(SolrParams params) throws IOException {
        List<List<SearchToken>> parsedQueries = new ArrayList<>();

        String searchId = params.get(QUERY_PREFIX + KEY_SEARCH_ID);
        if (searchId == null) {
            throw new IOException("No search id has been defined");
        }

        for (Map.Entry<Integer, Set<String>> entryQuery : groupParamKeysById(
                params.stream().map(Map.Entry::getKey), 2).entrySet()) {
            parsedQueries.add(parseQuery(entryQuery.getKey(), entryQuery.getValue(), params));
        }

        return new SearchDefinition(searchId, parsedQueries);
    }

    List<SearchToken> parseQuery(int queryId, Set<String> tokens, SolrParams params) throws IOException {

        List<SearchToken> searchTokens = new ArrayList<>();
        String tokenIdPrefix = QUERY_PREFIX + queryId + TOKEN_PREFIX;

        for (Map.Entry<Integer, Set<String>> entryToken :
                groupParamKeysById(tokens.stream(), 6).entrySet()) {

            String tokenIdPrefixExtended = tokenIdPrefix + entryToken.getKey() + ".";

            String tokenText = params.get(tokenIdPrefixExtended + KEY_TEXT);
            if (tokenText == null) {
                throw new IOException(String.format("No token text has been defined for query id %s", queryId));
            }

            String fuzzyStr = params.get(tokenIdPrefixExtended + KEY_FUZZY);
            if (fuzzyStr == null) {
                throw new IOException(String.format("No search type has been defined for query id %s and toke  id %s",
                        queryId, entryToken.getKey()));
            }

            boolean fuzzy = Boolean.parseBoolean(params.get(tokenIdPrefixExtended + KEY_FUZZY));
            if (fuzzy) {
                String prefixStr = params.get(tokenIdPrefixExtended + KEY_PREFIX);
                String varStr = params.get(tokenIdPrefixExtended + KEY_VAR);

                if (varStr == null || prefixStr == null) {
                    throw new IOException(String.format("Search for token \"%s\" has been defined as fuzzy, but no information " +
                            "about max variations and min prefix are provided", tokenText));
                }

                int prefix = Integer.parseInt(prefixStr);
                int var = Integer.parseInt(varStr);

                searchTokens.add(new SearchToken(tokenText, fuzzy, prefix, var));

            } else {
                searchTokens.add(new SearchToken(tokenText, fuzzy));
            }
        }

        return searchTokens;
    }

    private Map<Integer, Set<String>> groupParamKeysById(final Stream<String> keys, final int idPos) {
        return keys
                .filter(key -> key.startsWith(QUERY_PREFIX))
                .filter(key -> key.length() >= idPos + 1)
                .filter(key -> CharUtils.isAsciiNumeric(key.charAt(idPos)))
                .collect(Collectors.groupingBy(key -> CharUtils.toIntValue(key.charAt(idPos)), Collectors.toSet()));
    }

    @Override
    public String getDescription() {
        return "EntityMappingSearchComponent";
    }
}
