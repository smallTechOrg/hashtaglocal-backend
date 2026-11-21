package org.smalltech.hashtaglocal_backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.smalltech.hashtaglocal_backend.model.QuizRejectReason;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;

/**
 * Daily quiz content for a locality, entered by ops through the admin portal (AI generation comes
 * later). The quiz↔date binding lives on {@code bulletins} ({@code quiz_id} + {@code date}); this
 * table holds only the content. Options are JSONB maps ({@code {"text": ...}}) so richer option
 * payloads (images, hints) need no schema change.
 */
@Entity
@Table(name = "quizzes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "locality_id", nullable = false)
  private Locality locality;

  @Column(nullable = false, columnDefinition = "text")
  private String question;

  @Type(JsonType.class)
  @Column(name = "option_1", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> option1;

  @Type(JsonType.class)
  @Column(name = "option_2", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> option2;

  @Type(JsonType.class)
  @Column(name = "option_3", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> option3;

  @Type(JsonType.class)
  @Column(name = "option_4", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> option4;

  /** 1-based index of the correct option (1..4). */
  @Column(name = "answer_option_index", nullable = false)
  private Integer answerOptionIndex;

  /** Engaging explanation shown after an attempt — Groq-generated at creation, ops-editable. */
  @Column(columnDefinition = "text")
  private String explanation;

  /**
   * Which AI wrote the quiz, as provider/model (e.g. "groq/some-model"), so admins can compare
   * quality. Null for hand-entered quizzes.
   */
  @Column(name = "generated_by", length = 100)
  private String generatedBy;

  /**
   * Review lifecycle. The column default backfills existing hand-entered quizzes as PUBLISHED,
   * because ddl-auto=update cannot add a NOT NULL column to a populated table without one.
   */
  @Enumerated(EnumType.STRING)
  @Column(
      nullable = false,
      length = 20,
      columnDefinition = "varchar(20) not null default 'PUBLISHED'")
  @Builder.Default
  private QuizStatus status = QuizStatus.PUBLISHED;

  /** Why an admin rejected the quiz; set only when {@code status} is REJECTED. */
  @Enumerated(EnumType.STRING)
  @Column(name = "rejection_reason", columnDefinition = "varchar(30)")
  private QuizRejectReason rejectionReason;

  /** Free-text note from the admin; required when the reason is OTHER. */
  @Column(name = "rejection_note", columnDefinition = "text")
  private String rejectionNote;

  /** Id of the admin user who approved or rejected the quiz. */
  @Column(name = "reviewed_by")
  private Long reviewedBy;

  /** When the admin approved or rejected the quiz. */
  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
