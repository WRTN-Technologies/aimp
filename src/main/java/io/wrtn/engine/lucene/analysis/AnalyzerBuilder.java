package io.wrtn.engine.lucene.analysis;

import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.wrtn.model.index.FieldConfig;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import static io.wrtn.engine.lucene.Constants.*;

public final class AnalyzerBuilder {

    private static final KeywordAnalyzer KEYWORD_ANALYZER = new KeywordAnalyzer();
    private static final EnglishAnalyzer ENGLISH_ANALYZER = new EnglishAnalyzer();
    private static final JapaneseAnalyzer JAPANESE_ANALYZER = new JapaneseAnalyzer();
    private static final KoreanAnalyzer KOREAN_ANALYZER = new KoreanAnalyzer();
    private static final StandardAnalyzer STANDARD_ANALYZER = new StandardAnalyzer();

    public static Map<String, Analyzer> build(final Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        Map<String, Map<String, List<String>>> fieldsConfig = getFieldsConfig(mappings, "");

        for (Map.Entry<String, Map<String, List<String>>> entry : fieldsConfig.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, List<String>> fieldConfig = entry.getValue();

            for (Map.Entry<String, List<String>> fieldEntry : fieldConfig.entrySet()) {
                String fieldType = fieldEntry.getKey();
                List<String> analyzers = fieldEntry.getValue();

                if (fieldType.equals(DATA_TYPE_KEYWORD)) {
                    analyzerPerField.put(fieldName, createAnalyzer(DATA_TYPE_KEYWORD));
                }

                if (analyzers != null) {
                    for (String analyzerName : analyzers) {
                        Analyzer analyzer = createAnalyzer(analyzerName);
                        if (analyzer != null) {
                            analyzerPerField.put(fieldName + NESTED_FIELD_DELIMITER + analyzerName,
                                analyzer);
                        }
                    }
                }
            }
        }

        return analyzerPerField;
    }

    private static Map<String, Map<String, List<String>>> getFieldsConfig(
        final Map<String, FieldConfig> mappings,
        String parentKey) {
        Map<String, Map<String, List<String>>> fields = new HashMap<>();
        for (Map.Entry<String, FieldConfig> entry : mappings.entrySet()) {
            String key = entry.getKey();
            FieldConfig value = entry.getValue();
            String fullFieldName =
                parentKey.isEmpty() ? key : parentKey + NESTED_FIELD_DELIMITER + key;

            if (value.getType().equals(DATA_TYPE_OBJECT) && value.getObjectMapping() != null) {
                fields.putAll(getFieldsConfig(value.getObjectMapping(), fullFieldName));
            } else {
                Map<String, List<String>> typeAnalyerMap = new HashMap<>();
                typeAnalyerMap.put(value.getType(), value.getAnalyzers());
                fields.put(fullFieldName, typeAnalyerMap);
            }
        }
        return fields;
    }

    private static Analyzer createAnalyzer(final String analyzerName)
        throws GlobalExceptionHandler {
        switch (analyzerName) {
            case ANALYZER_TYPE_KOREAN -> {
                return KOREAN_ANALYZER;
            }
            case ANALYZER_TYPE_JAPANESE -> {
                return JAPANESE_ANALYZER;
            }
            case ANALYZER_TYPE_ENGLISH -> {
                return ENGLISH_ANALYZER;
            }
            case ANALYZER_TYPE_STANDARD -> {
                return STANDARD_ANALYZER;
            }
            case DATA_TYPE_KEYWORD -> {
                return KEYWORD_ANALYZER;
            }
            default -> throw new GlobalExceptionHandler("Invalid analyzer type: " + analyzerName,
                StatusCode.INVALID_INPUT_VALUE);
        }
    }
}