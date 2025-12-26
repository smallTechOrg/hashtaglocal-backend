package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Locality {
	private List<String> hashtags;
}
