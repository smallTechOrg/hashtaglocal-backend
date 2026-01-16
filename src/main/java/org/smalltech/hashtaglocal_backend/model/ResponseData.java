package org.smalltech.hashtaglocal_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseData {
	private Issue issue;
	private String signedUrl;
	private String path;
}
