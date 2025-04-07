package io.wrtn.util;

import static io.wrtn.util.JsonParser.gson;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class DocumentIterator implements Iterator<Map<String, JsonElement>> {

    private long currentIndex = 0;
    private final JsonReader jsonReader;
    private final BufferedReader bufferedReader;
    private final InputStreamReader inputStreamReader;
    private final Type documentType = new TypeToken<Map<String, JsonElement>>() {
    }.getType();

    public DocumentIterator(InputStream inputStream) throws IOException {
        this.inputStreamReader = new InputStreamReader(inputStream);
        this.bufferedReader = new BufferedReader(inputStreamReader);
        this.jsonReader = new JsonReader(bufferedReader);

        boolean documentsArrayStarted = false;
        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            String name = jsonReader.nextName();
            if ("documents".equals(name)) {
                jsonReader.beginArray();
                documentsArrayStarted = true;
                break;
            }
        }

        if (!documentsArrayStarted) {
            throw new IOException("No documents array found in WAL record");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return jsonReader.hasNext();
        } catch (IOException e) {
            throw new RuntimeException("Error checking for next document", e);
        }
    }

    @Override
    public Map<String, JsonElement> next() {
        try {
            if (!jsonReader.hasNext()) {
                // End of file
                throw new NoSuchElementException();
            }
            currentIndex++;

            // Read single document
            return gson.fromJson(jsonReader,
                documentType);

        } catch (IOException e) {
            throw new RuntimeException("Error reading document", e);
        }
    }

    public long getCurrentIndex() {
        return currentIndex;
    }

    public void close() {
        try {
            if (jsonReader != null) {
                jsonReader.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (inputStreamReader != null) {
                inputStreamReader.close();
            }
        } catch (IOException e) {
            GlobalLogger.error("Error closing readers: " + e.getMessage());
        }
    }
}