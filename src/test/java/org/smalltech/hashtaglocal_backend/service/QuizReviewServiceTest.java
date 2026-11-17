package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.QuizRejectReason;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.model.response.QueuedQuizCountData;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class QuizReviewServiceTest {

  private static final Long ADMIN_ID = 7L;

  @Mock private QuizRepository quizRepository;
  @Mock private UserRepository userRepository;
  @Mock private QuizGenerationService quizGenerationService;

  @InjectMocks private QuizReviewService service;

  private final Locality portBlair =
      Locality.builder().id(5L).hashtag("portblair").name("Port Blair").build();
  private final Locality kargil =
      Locality.builder().id(3L).hashtag("kargil").name("Kargil").build();

  @Test
  void approveQueuesOnlyTheRequestedPendingQuizzes() {
    QuizEntity pending = quiz(1L, QuizStatus.PENDING);
    QuizEntity rejected = quiz(2L, QuizStatus.REJECTED);
    when(quizRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(pending, rejected));

    var approved = service.approve(List.of(1L, 2L), ADMIN_ID);

    assertEquals(1, approved.size());
    assertEquals(QuizStatus.QUEUED, pending.getStatus());
    assertEquals(ADMIN_ID, pending.getReviewedBy());
    assertEquals(QuizStatus.REJECTED, rejected.getStatus());
  }

  @Test
  void rejectStoresReasonNoteAndReviewerThenGeneratesReplacement() {
    QuizEntity pending = quiz(3L, QuizStatus.PENDING);
    when(quizRepository.findById(3L)).thenReturn(Optional.of(pending));

    var rejected =
        service.reject(3L, QuizRejectReason.WRONG_ANSWER, "  Check the river  ", ADMIN_ID);

    assertEquals(QuizStatus.REJECTED, pending.getStatus());
    assertEquals(QuizRejectReason.WRONG_ANSWER, pending.getRejectionReason());
    assertEquals("Check the river", pending.getRejectionNote());
    assertEquals(ADMIN_ID, pending.getReviewedBy());
    assertEquals(QuizStatus.REJECTED, rejected.getStatus());
    // A replacement is generated for the same locality.
    verify(quizGenerationService).generatePendingQuiz(portBlair);
  }

  @Test
  void rejectionIsKeptWhenReplacementGenerationFails() {
    QuizEntity pending = quiz(6L, QuizStatus.PENDING);
    when(quizRepository.findById(6L)).thenReturn(Optional.of(pending));
    when(quizGenerationService.generatePendingQuiz(portBlair)).thenReturn(Optional.empty());

    service.reject(6L, QuizRejectReason.POOR_OPTIONS, null, ADMIN_ID);

    assertEquals(QuizStatus.REJECTED, pending.getStatus());
    assertEquals(QuizRejectReason.POOR_OPTIONS, pending.getRejectionReason());
  }

  @Test
  void otherReasonWithoutNoteIsRejectedAsBadRequest() {
    DownstreamServiceException error =
        assertThrows(
            DownstreamServiceException.class,
            () -> service.reject(10L, QuizRejectReason.OTHER, "   ", ADMIN_ID));

    assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.getStatus());
    verify(quizRepository, never()).findById(any());
  }

  @Test
  void onlyPendingQuizzesCanBeRejected() {
    when(quizRepository.findById(4L)).thenReturn(Optional.of(quiz(4L, QuizStatus.QUEUED)));

    assertThrows(
        IllegalStateException.class,
        () -> service.reject(4L, QuizRejectReason.OTHER, "note", ADMIN_ID));
    verify(quizGenerationService, never()).generatePendingQuiz(any());
  }

  @Test
  void rejectedListIsFilteredByHashtagAndReason() {
    QuizEntity wrongAnswer = quiz(8L, QuizStatus.REJECTED);
    wrongAnswer.setRejectionReason(QuizRejectReason.WRONG_ANSWER);
    QuizEntity tooEasy = quiz(9L, QuizStatus.REJECTED);
    tooEasy.setRejectionReason(QuizRejectReason.TOO_EASY);
    when(quizRepository.findByStatusOrderByReviewedAtDesc(QuizStatus.REJECTED))
        .thenReturn(List.of(wrongAnswer, tooEasy));

    assertEquals(2, service.rejected(null, null).size());
    assertEquals(2, service.rejected("PortBlair", null).size());
    assertEquals(0, service.rejected("kargil", null).size());
    assertEquals(1, service.rejected(null, QuizRejectReason.WRONG_ANSWER).size());
  }

  @Test
  void queuedCountsIncludeLocalitiesWithNoneQueued() {
    when(quizRepository.countByLocalityForStatus(QuizStatus.QUEUED))
        .thenReturn(List.<Object[]>of(new Object[] {5L, 3L}));
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(portBlair, kargil));

    List<QueuedQuizCountData> counts = service.queuedCounts();

    assertEquals(2, counts.size());
    assertEquals("kargil", counts.get(0).getHashtag());
    assertEquals(0, counts.get(0).getQueued());
    assertEquals("portblair", counts.get(1).getHashtag());
    assertEquals(3, counts.get(1).getQueued());
  }

  private QuizEntity quiz(Long id, QuizStatus status) {
    return QuizEntity.builder()
        .id(id)
        .locality(portBlair)
        .question("Q?")
        .option1(Map.of("text", "a"))
        .option2(Map.of("text", "b"))
        .option3(Map.of("text", "c"))
        .option4(Map.of("text", "d"))
        .answerOptionIndex(1)
        .explanation("E.")
        .status(status)
        .build();
  }
}
