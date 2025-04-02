package io.wrtn.model.document;

import java.util.Arrays;
import java.util.Set;

public class RefreshedDocs {

    private Document[] documents;
    private Set<String> deletedDocIdSet;

    public RefreshedDocs(Document[] documents, Set<String> deletedDocIdSet) {
        this.documents = documents;
        this.deletedDocIdSet = deletedDocIdSet;
    }

    public Document[] getDocuments() {
        return documents;
    }

    public void setDocuments(Document[] documents) {
        this.documents = documents;
    }

    public Set<String> getDeletedDocIdSet() {
        return deletedDocIdSet;
    }

    public void setDeletedDocIdSet(Set<String> deletedDocIdSet) {
        this.deletedDocIdSet = deletedDocIdSet;
    }

    @Override
    public String toString() {
        return "RefreshedDocs{" +
            "documents=" + Arrays.toString(documents) +
            ", deletedDocIds=" + deletedDocIdSet +
            '}';
    }
}
