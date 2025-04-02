package io.wrtn.model.wal;

import java.util.Map;

public class PendingDeletes {

    private Map<String, Long> walIdMap;

    public PendingDeletes(Map<String, Long> walIdMap) {
        this.walIdMap = walIdMap;
    }

    public Map<String, Long> getWalIdMap() {
        return walIdMap;
    }

    public void setWalIdMap(Map<String, Long> walIdMap) {
        this.walIdMap = walIdMap;
    }

    @Override
    public String toString() {
        return "PendingDeletes{" +
            "walIdMap=" + walIdMap +
            '}';
    }
}
