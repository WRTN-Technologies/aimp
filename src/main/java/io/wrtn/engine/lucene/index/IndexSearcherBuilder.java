package io.wrtn.engine.lucene.index;

import static io.wrtn.engine.lucene.Constants.DEFAULT_MAX_CLAUSE_COUNT;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.Similarity;
import io.wrtn.util.Threads;

public final class IndexSearcherBuilder {

    static {
        IndexSearcher.setMaxClauseCount(DEFAULT_MAX_CLAUSE_COUNT);
    }

    public static IndexSearcher build(IndexReader reader) {
        return new IndexSearcher(reader);
    }

    public static IndexSearcher build(IndexReader reader, Similarity similarity) {
        IndexSearcher searcher = new IndexSearcher(reader, Threads.getIOExecutor());
        searcher.setSimilarity(similarity);
        return searcher;
    }
}
