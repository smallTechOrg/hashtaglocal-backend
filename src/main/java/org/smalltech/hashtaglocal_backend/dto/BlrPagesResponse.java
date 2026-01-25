package org.smalltech.hashtaglocal_backend.dto;

import java.util.List;
import lombok.Data;

@Data
public class BlrPagesResponse {

	private boolean success;

	private Result result;

	@Data
	public static class Result {

		private List<BlrPagesIssueDTO> data;

		private String city;

		private String name;
	}
}
