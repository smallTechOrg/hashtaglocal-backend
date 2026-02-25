package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueQueryService {

  private final IssueRepository issueRepository;

  public IssueEntity get(Long issueId) {
    return issueRepository
        .findById(issueId)
        .orElseGet(
            () ->
                issueRepository
                    .findById(1L)
                    .orElseThrow(() -> new RuntimeException("No issue available")));
  }
}
