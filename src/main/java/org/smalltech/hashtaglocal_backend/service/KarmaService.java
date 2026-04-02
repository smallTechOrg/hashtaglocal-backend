package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.KarmaTransactionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.KarmaPointsConfig;
import org.smalltech.hashtaglocal_backend.model.KarmaTransactionStatus;
import org.smalltech.hashtaglocal_backend.model.KarmaTransactionType;
import org.smalltech.hashtaglocal_backend.repository.KarmaTransactionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KarmaService {

  private final KarmaTransactionRepository karmaTransactionRepository;
  private final UserRepository userRepository;

  /**
   * Award pending karma points for an action that requires admin approval.
   *
   * @return the number of points awarded
   */
  @Transactional
  public int awardPendingKarma(
      UserEntity user, KarmaTransactionType type, IssueActionEntity referenceAction) {
    int points = KarmaPointsConfig.pointsFor(type);

    KarmaTransactionEntity txn =
        KarmaTransactionEntity.builder()
            .userEntity(user)
            .points(points)
            .type(type)
            .status(KarmaTransactionStatus.PENDING)
            .referenceAction(referenceAction)
            .build();
    karmaTransactionRepository.save(txn);

    user.setKarmaPending(user.getKarmaPending() + points);
    userRepository.save(user);

    log.info(
        "Awarded {} pending karma ({}) to user {} for action {}",
        points,
        type,
        user.getId(),
        referenceAction.getId());
    return points;
  }

  /**
   * Award earned karma directly (no admin approval needed, e.g. daily login).
   *
   * @return the number of points awarded
   */
  @Transactional
  public int awardEarnedKarma(UserEntity user, KarmaTransactionType type) {
    int points = KarmaPointsConfig.pointsFor(type);

    KarmaTransactionEntity txn =
        KarmaTransactionEntity.builder()
            .userEntity(user)
            .points(points)
            .type(type)
            .status(KarmaTransactionStatus.EARNED)
            .build();
    karmaTransactionRepository.save(txn);

    user.setKarmaEarned(user.getKarmaEarned() + points);
    userRepository.save(user);

    log.info("Awarded {} earned karma ({}) to user {}", points, type, user.getId());
    return points;
  }

  /**
   * Confirm pending karma (move from PENDING → EARNED) when an admin approves an action. All
   * PENDING transactions tied to the given action are confirmed.
   */
  @Transactional
  public void confirmKarma(IssueActionEntity action) {
    List<KarmaTransactionEntity> pendingTxns =
        karmaTransactionRepository.findByReferenceActionAndStatus(
            action, KarmaTransactionStatus.PENDING);

    for (KarmaTransactionEntity txn : pendingTxns) {
      txn.setStatus(KarmaTransactionStatus.EARNED);
      karmaTransactionRepository.save(txn);

      UserEntity user = txn.getUserEntity();
      user.setKarmaPending(user.getKarmaPending() - txn.getPoints());
      user.setKarmaEarned(user.getKarmaEarned() + txn.getPoints());
      userRepository.save(user);

      log.info(
          "Confirmed {} karma ({}) for user {} on action {}",
          txn.getPoints(),
          txn.getType(),
          user.getId(),
          action.getId());
    }
  }

  /**
   * Revoke pending karma (move from PENDING → REVOKED) when an admin rejects an action. All PENDING
   * transactions tied to the given action are revoked.
   */
  @Transactional
  public void revokeKarma(IssueActionEntity action) {
    List<KarmaTransactionEntity> pendingTxns =
        karmaTransactionRepository.findByReferenceActionAndStatus(
            action, KarmaTransactionStatus.PENDING);

    for (KarmaTransactionEntity txn : pendingTxns) {
      txn.setStatus(KarmaTransactionStatus.REVOKED);
      karmaTransactionRepository.save(txn);

      UserEntity user = txn.getUserEntity();
      user.setKarmaPending(user.getKarmaPending() - txn.getPoints());
      userRepository.save(user);

      log.info(
          "Revoked {} karma ({}) for user {} on action {}",
          txn.getPoints(),
          txn.getType(),
          user.getId(),
          action.getId());
    }
  }

  /**
   * Award 1 earned karma to the issue reporter when someone else's VERIFY action on their issue is
   * approved.
   */
  @Transactional
  public void awardReportedIssueVerifiedKarma(
      UserEntity issueReporter, IssueActionEntity verifyAction) {
    int points = KarmaPointsConfig.REPORTED_ISSUE_VERIFIED;

    KarmaTransactionEntity txn =
        KarmaTransactionEntity.builder()
            .userEntity(issueReporter)
            .points(points)
            .type(KarmaTransactionType.REPORTED_ISSUE_VERIFIED)
            .status(KarmaTransactionStatus.EARNED)
            .referenceAction(verifyAction)
            .build();
    karmaTransactionRepository.save(txn);

    issueReporter.setKarmaEarned(issueReporter.getKarmaEarned() + points);
    userRepository.save(issueReporter);

    log.info(
        "Awarded {} REPORTED_ISSUE_VERIFIED karma to user {} for verify action {}",
        points,
        issueReporter.getId(),
        verifyAction.getId());
  }

  /**
   * Check and award daily login karma. Idempotent — only awards once per calendar day (UTC).
   *
   * @return true if karma was awarded (first login today), false if already awarded
   */
  @Transactional
  public boolean tryAwardDailyLoginKarma(UserEntity user) {
    LocalDateTime startOfToday = LocalDate.now().atTime(LocalTime.MIN);

    boolean alreadyAwarded =
        karmaTransactionRepository.existsByUserEntityIdAndTypeAndCreatedAtAfter(
            user.getId(), KarmaTransactionType.DAILY_LOGIN, startOfToday);

    if (alreadyAwarded) {
      return false;
    }

    awardEarnedKarma(user, KarmaTransactionType.DAILY_LOGIN);
    return true;
  }
}
