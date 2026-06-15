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

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
