package com.amdocs.telecom.dto;
import javax.validation.constraints.*;
public class FeedbackForm {
    @NotNull @Min(0) private Long rowVersion;
    @NotNull @Min(1) @Max(5) private Integer rating;
    @Size(max=2000) private String comments;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion=value; }
    public Integer getRating() { return rating; }
    public void setRating(Integer value) { rating=value; }
    public String getComments() { return comments; }
    public void setComments(String value) { comments=value; }
}
