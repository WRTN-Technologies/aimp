package io.wrtn.model.storage;

public class File {

    private String name;
    private Long size;
    private String versionId;
    private Integer prefixId;
    private byte[] header;
    private byte[] footer;
    private byte[] fullBytes;

    public File() {
    }

    public File(String name, String versionId, Integer prefixId) {
        this.name = name;
        this.versionId = versionId;
        this.prefixId = prefixId;
    }

    public File(String name, Long size, String versionId, Integer prefixId) {
        this.name = name;
        this.size = size;
        this.versionId = versionId;
        this.prefixId = prefixId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public Integer getPrefixId() {
        return prefixId;
    }

    public void setPrefixId(Integer prefixId) {
        this.prefixId = prefixId;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

    public void setFooter(byte[] footer) {
        this.footer = footer;
    }

    public byte[] getHeader() {
        return header;
    }

    public byte[] getFooter() {
        return footer;
    }

    public void setFullBytes(byte[] fullBytes) {
        this.fullBytes = fullBytes;
    }

    public byte[] getFullBytes() {
        return fullBytes;
    }

    public String toString() {
        return "File(name=" + name + ", size=" + size + ", versionId=" + versionId + ", prefixId="
            + prefixId + ")";
    }
}
