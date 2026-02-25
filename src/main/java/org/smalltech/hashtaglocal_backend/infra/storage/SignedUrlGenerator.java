package org.smalltech.hashtaglocal_backend.infra.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// generates signed URLs for uploading media to Google Cloud Storage, using PUT method and V4
// signatures, valid for 15 minutes
@Component
public class SignedUrlGenerator {

  private final Storage storage;

  public SignedUrlGenerator(Storage storage) {
    this.storage = storage;
  }

  public URL generatePutUrl(String bucket, String objectPath, String contentType) {
    BlobInfo blobInfo = BlobInfo.newBuilder(bucket, objectPath).setContentType(contentType).build();

    return storage.signUrl(
        blobInfo,
        15,
        TimeUnit.MINUTES,
        Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
        Storage.SignUrlOption.withV4Signature());
  }
}
