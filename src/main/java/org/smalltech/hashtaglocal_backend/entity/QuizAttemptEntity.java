package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One user's single attempt at a quiz. The unique constraint enforces one attempt per user per
 * quiz; a {@code null} selectedOptionIndex records a missed attempt (the 15-second timer expired).
 */
@Entity
@Table(
    name = "quiz_attempts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_quiz_attempts_user_quiz",
            columnNames = {"user_id", "quiz_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private QuizEntity quiz;

  /** 1-based selected option; {@code null} = timed out without answering. */
  @Column(name = "selected_option_index")
  private Integer selectedOptionIndex;

  @Column(name = "is_correct", nullable = false)
  private boolean isCorrect;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
