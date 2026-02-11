package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignedUrlResponse {
	private String signedUrl;
	private String path;
}
