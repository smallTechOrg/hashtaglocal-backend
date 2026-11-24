package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;

@ExtendWith(MockitoExtension.class)
class QuizQueueServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 17);

  @Mock private QuizRepository quizRepository;
  @Mock private BulletinRepository bulletinRepository;

  @InjectMocks private QuizQueueService service;

  private final Locality portBlair =
      Locality.builder().id(5L).hashtag("portblair").name("Port Blair").build();

  @Test
  void queuedQuizIsAttachedToTodaysBulletinAndPublished() {
    QuizEntity queued = queuedQuiz(11L);
    when(bulletinRepository.findByLocalityIdAndDate(5L, DATE)).thenReturn(Optional.empty());
    when(quizRepository.lockRandomQueuedQuiz(5L)).thenReturn(Optional.of(queued));

    Optional<QuizEntity> picked = service.publishForDate(portBlair, DATE);

    assertTrue(picked.isPresent());
    assertSame(queued, picked.get());
    assertEquals(QuizStatus.PUBLISHED, queued.getStatus());

    ArgumentCaptor<BulletinEntity> saved = ArgumentCaptor.forClass(BulletinEntity.class);
    verify(bulletinRepository).save(saved.capture());
    assertSame(queued, saved.getValue().getQuiz());
    assertEquals(DATE, saved.getValue().getDate());
  }

  @Test
  void bulletinThatAlreadyHasAQuizIsLeftAlone() {
    QuizEntity existing = queuedQuiz(20L);
    BulletinEntity bulletin =
        BulletinEntity.builder().locality(portBlair).date(DATE).quiz(existing).build();
    when(bulletinRepository.findByLocalityIdAndDate(5L, DATE)).thenReturn(Optional.of(bulletin));

    Optional<QuizEntity> result = service.publishForDate(portBlair, DATE);

    assertSame(existing, result.orElseThrow());
    verify(quizRepository, never()).lockRandomQueuedQuiz(any());
    verify(bulletinRepository, never()).save(any());
  }

  @Test
  void emptyQueueSavesNoBulletin() {
    when(bulletinRepository.findByLocalityIdAndDate(5L, DATE)).thenReturn(Optional.empty());
    when(quizRepository.lockRandomQueuedQuiz(5L)).thenReturn(Optional.empty());

    assertFalse(service.publishForDate(portBlair, DATE).isPresent());
    verify(bulletinRepository, never()).save(any());
  }

  private QuizEntity queuedQuiz(Long id) {
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
        .status(QuizStatus.QUEUED)
        .build();
  }
}
