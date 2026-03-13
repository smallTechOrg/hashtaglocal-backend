package org.smalltech.hashtaglocal_backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.smalltech.hashtaglocal_backend.model.PortalEnum;

@Entity
@Table(name = "portal_issues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GovPortalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "issue_id", nullable = false)
  private IssueEntity issueEntity;

  private String trackingId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PortalEnum portal;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private String status;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metaData;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
