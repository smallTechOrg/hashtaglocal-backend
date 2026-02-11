package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponse {
	private String value;
	private long expiry;
}
