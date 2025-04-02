package io.wrtn.engine.lucene.index;

import io.wrtn.util.GlobalExceptionHandler;
import java.io.IOException;
import java.util.Map;

import io.wrtn.model.index.FieldConfig;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import io.wrtn.engine.lucene.analysis.AnalyzerBuilder;

public final class IndexWriterBuilder {

    public static IndexWriter build(Directory directory, Map<String, FieldConfig> mappings)
        throws IOException, GlobalExceptionHandler {

        Map<String, Analyzer> analyzerPerField = AnalyzerBuilder.build(mappings);
        PerFieldAnalyzerWrapper aWrapper = new PerFieldAnalyzerWrapper(new StandardAnalyzer(),
            analyzerPerField);

        // IndexWriterConfig
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(aWrapper);
        indexWriterConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);

        return new IndexWriter(directory, indexWriterConfig);
    }
}
