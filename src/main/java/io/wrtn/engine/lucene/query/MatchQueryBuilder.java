package io.wrtn.engine.lucene.query;

import static io.wrtn.engine.lucene.Constants.DEFAULT_MAX_CLAUSE_COUNT;

import io.wrtn.engine.lucene.analysis.TokenizedTermExtractor;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher.TooManyClauses;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class MatchQueryBuilder {

    private static final BooleanClause.Occur DEFAULT_OCCUR = BooleanClause.Occur.SHOULD;

    public static Query build(final Map<String, Analyzer> perFieldAnalyzers,
        final String fieldName, final String text) throws IOException, GlobalExceptionHandler {

        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
        Map<String, List<Term>> fieldTermsMap = TokenizedTermExtractor.extract(perFieldAnalyzers,
            fieldName, text);

        for (Map.Entry<String, List<Term>> entry : fieldTermsMap.entrySet()) {

            List<Term> terms = entry.getValue();
            for (Term term : terms) {
                try {
                    booleanQueryBuilder.add(new TermQuery(term), DEFAULT_OCCUR);
                } catch (TooManyClauses e) {
                    throw new GlobalExceptionHandler(
                        "Too many boolean clauses, the maximum is "
                            + DEFAULT_MAX_CLAUSE_COUNT,
                        StatusCode.BAD_REQUEST);
                }
            }
        }

        BooleanQuery query = booleanQueryBuilder.build();
        if (query.clauses().isEmpty()) {
            return new MatchNoDocsQuery(
                "No terms found for field: " + fieldName + " with text: " + text);
        }

        return query;
    }

}
