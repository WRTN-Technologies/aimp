package io.wrtn.engine.lucene.analysis;

import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;

public class TokenizedTermExtractor {

    public static Map<String, List<Term>> extract(final Map<String, Analyzer> perFieldAnalyzers,
        final String fieldName, final String text) throws IOException {

        HashMap<String, List<Term>> fieldTermsMap = new HashMap<>();
        for (String field : perFieldAnalyzers.keySet()) {
            if (field.startsWith(fieldName + NESTED_FIELD_DELIMITER)) {
                List<Term> terms = new ArrayList<>();
                TokenStream tokenStream = perFieldAnalyzers.get(field).tokenStream(field,
                    new StringReader(text));

                try (tokenStream) {
                    CharTermAttribute termAttr = tokenStream.addAttribute(CharTermAttribute.class);
                    tokenStream.reset();
                    while (tokenStream.incrementToken()) {
                        terms.add(new Term(field, termAttr.toString()));
                    }
                    tokenStream.end();
                }

                fieldTermsMap.put(field, terms);
            }
        }

        return fieldTermsMap;
    }

}
