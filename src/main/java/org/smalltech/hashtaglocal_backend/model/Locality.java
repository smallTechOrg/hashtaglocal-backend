package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Locality {
	private List<String> hashtags;
}
