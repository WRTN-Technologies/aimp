package io.wrtn.dto;

public class ProjectUpdateRequest {

    private Double rateLimit;  // Request per second

    public Double getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Double rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public String toString() {
        return "ProjectCreateRequest{" +
            ", rateLimit=" + rateLimit +
            '}';
    }
}
