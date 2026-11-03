package ci.assurzen.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Contenu d'un produit affiche dans l'application mobile, parametre depuis le back-office (2026-11-03). Une ligne par
 * produit (t_assur_zen_produit). Un produit SANS ligne, ou avec visibleMobile = false, n'apparait pas dans le catalogue
 * mobile. Les listes (avantages, garanties) sont stockees en JSON.
 */
@Entity
@Table(
        name = "t_assur_zen_mobile_product_content",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_product_content_product", columnNames = "product_id")
)
@Getter
@Setter
@NoArgsConstructor
public class MobileProductContent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "visible_mobile", nullable = false)
    private boolean visibleMobile = false;

    @Column(name = "short_name", length = 100)
    private String shortName;

    /** VEHICLE, PERSON, PROPERTY ou PROFESSIONAL. */
    @Column(name = "family", length = 20)
    private String family;

    @Column(name = "description", length = 2000)
    private String description;

    /** Tableau JSON de textes. */
    @Column(name = "benefits_json", columnDefinition = "TEXT")
    private String benefitsJson;

    @Column(name = "how_it_works", columnDefinition = "TEXT")
    private String howItWorks;

    @Column(name = "eligibility", length = 2000)
    private String eligibility;

    /** Tableau JSON de {name, kind}. */
    @Column(name = "guarantees_json", columnDefinition = "TEXT")
    private String guaranteesJson;

    /** Tableau JSON de {id, name, kind, description, limit, formulaIds, displayOrder, visible}. */
    // (guarantees_json ci-dessus : meme colonne, structure enrichie le 2026-11-03)

    /** Tableau JSON de {id, name, description, displayOrder, visible}. */
    @Column(name = "formulas_json", columnDefinition = "TEXT")
    private String formulasJson;

    /** Tableau JSON de textes. */
    @Column(name = "exclusions_json", columnDefinition = "TEXT")
    private String exclusionsJson;

    /** Tableau JSON de textes : pieces a fournir pour souscrire. */
    @Column(name = "required_documents_json", columnDefinition = "TEXT")
    private String requiredDocumentsJson;

    /** Tableau JSON de {question, answer}. */
    @Column(name = "faq_json", columnDefinition = "TEXT")
    private String faqJson;

    /** Code d'icone choisi par le back-office (ex. car, moto, health). */
    @Column(name = "icon", length = 40)
    private String icon;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(name = "online_subscription", nullable = false)
    private boolean onlineSubscription = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
