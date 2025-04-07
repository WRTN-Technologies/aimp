package io.wrtn.engine.lucene.query;

import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

public class FilteredKnnFloatVectorQuery extends KnnFloatVectorQuery {

    private final Integer topK;
    private final Integer numCandidates;

    public FilteredKnnFloatVectorQuery(String field, float[] target, Integer topK, Query filter) {
        super(field, target, topK, filter);
        this.topK = topK;
        this.numCandidates = null;
    }

    public FilteredKnnFloatVectorQuery(String field, float[] target, Integer topK,
        Integer numCandidates, Query filter) {
        super(field, target, numCandidates, filter);
        this.topK = topK;
        this.numCandidates = numCandidates;
    }

    @Override
    protected TopDocs mergeLeafResults(TopDocs[] perLeafResults) {
        // if topK is set, return top topK results from numCandidates
        return numCandidates == null ? super.mergeLeafResults(perLeafResults)
            : TopDocs.merge(topK, perLeafResults);
    }
}
