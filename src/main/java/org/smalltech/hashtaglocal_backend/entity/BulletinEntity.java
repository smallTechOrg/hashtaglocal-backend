package org.smalltech.hashtaglocal_backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

/**
 * The integration row for one locality's daily bulletin: links the weather snapshot and the quiz,
 * and carries the AI summary. Both FKs are nullable because the row is upserted from two sides —
 * the admin attaches {@code quiz_id} when creating a quiz for a date, and the 8 AM weather job
 * attaches {@code periodic_data_id} + {@code summary}; whichever runs first creates the shell.
 */
@Entity
@Table(
    name = "bulletins",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_bulletins_locality_date",
            columnNames = {"locality_id", "date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulletinEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "locality_id", nullable = false)
  private Locality locality;

  @Column(nullable = false)
  private LocalDate date;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "periodic_data_id")
  private PeriodicDataEntity periodicData;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id")
  private QuizEntity quiz;

  /**
   * AI weather summary, e.g. {@code {"text": "Light showers this evening — ..."}}. Ops-editable.
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> summary;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
