package ci.assurzen.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Notification in-app de l'espace partenaire (2026-09-23) - doublee d'un SMS au moment de sa creation
 * (smsEnvoye trace le resultat de cet envoi). Sert aussi d'historique des messages envoyes au
 * partenaire : GET /partenaires/{partenaireId}/notifications.
 */
@Entity
@Table(
        name = "t_assur_zen_partenaire_notification",
        indexes = {
                @Index(name = "idx_partenaire_notification_partenaire_lu", columnList = "partenaire_id, lu"),
                @Index(name = "idx_partenaire_notification_created_at", columnList = "created_at"),
                @Index(name = "idx_partenaire_notification_user_lu", columnList = "user_id, lu")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartenaireNotification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", foreignKey = @ForeignKey(name = "fk_partenaire_notification_partenaire"))
    private Partenaire partenaire;

    /** Destinataire quand la notification ne concerne pas un partenaire (commercial interne, 2026-10-03) ;
     * NULL pour une notification de partenaire (destinataire = partenaire). */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private PartenaireNotificationType type;

    @Column(name = "titre", nullable = false, length = 150)
    private String titre;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** Montant concerne (FCFA) : total disponible, commission gagnee ou versee selon le type. */
    @Column(name = "montant")
    private Long montant;

    /** Contrat a l'origine de la notification (COMMISSION_GAGNEE/COMMISSION_VERSEE), NULL pour un rappel. */
    @Column(name = "contract_id")
    private Long contractId;

    @Builder.Default
    @Column(name = "sms_envoye", nullable = false)
    private boolean smsEnvoye = false;

    @Builder.Default
    @Column(name = "lu", nullable = false)
    private boolean lu = false;

    @Column(name = "lu_at")
    private Instant luAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Notification push navigateur de l'application EP (2026-09-26) - NULL pour les notifications
     * anterieures. */
    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", length = 10)
    private PushStatus pushStatus;

    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    /** Motif technique d'echec (jamais de donnee sensible). */
    @Column(name = "push_error", length = 255)
    private String pushError;

    /** Objet a ouvrir depuis la notification (2026-10-03) : CONTRAT, DEMANDE_PAIEMENT,
     * PERFORMANCE_COMMERCIALE... NULL pour les notifications anterieures (deduit de contractId). */
    @Column(name = "objet_type", length = 30)
    private String objetType;

    @Column(name = "objet_id")
    private Long objetId;

    /** Supprimee par son destinataire (DELETE /notifications/{id}) : masquee de son centre de
     * notifications, conservee dans l'historique consulte par les administrateurs. NULL = false. */
    @Column(name = "supprimee")
    private Boolean supprimee;

    @Column(name = "supprimee_at")
    private Instant supprimeeAt;
}
