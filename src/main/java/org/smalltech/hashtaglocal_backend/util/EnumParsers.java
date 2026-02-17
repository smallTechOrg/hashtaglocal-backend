package org.smalltech.hashtaglocal_backend.util;

import java.util.Locale;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EnumParsers {

	private EnumParsers() {
	}

	public static MediaTypeModel parseMediaType(String type) {
		try {
			return MediaTypeModel.valueOf(type.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media type: " + type, ex);
		}
	}

	public static IssueStatusModel parseStatus(String status) {
		try {
			return IssueStatusModel.valueOf(status.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value: " + status, ex);
		}
	}

	public static IssueTypeModel parseType(String type) {
		try {
			return IssueTypeModel.valueOf(type.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type value: " + type, ex);
		}
	}
}
