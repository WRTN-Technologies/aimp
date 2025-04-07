package io.wrtn.engine.lucene.index;

import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;

public final class IndexReaderBuilder {

    public static IndexReader build(Directory directory) throws IOException {
        return DirectoryReader.open(directory);
    }

    public static IndexReader build(IndexWriter writer) throws IOException {
        return DirectoryReader.open(writer);
    }

    public static IndexReader build(IndexReader reader, IndexWriter writer) throws IOException {
        IndexReader updatedReader = DirectoryReader.openIfChanged((DirectoryReader) reader, writer);
        if (updatedReader == null) {
            return reader;
        }
        return updatedReader;
    }

    public static IndexReader build(IndexReader reader) throws IOException {
        IndexReader updatedReader = DirectoryReader.openIfChanged((DirectoryReader) reader);
        if (updatedReader == null) {
            return reader;
        }
        return updatedReader;
    }
}
