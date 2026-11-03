package ci.assurzen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Profil, liens legaux, avis, notifications et support de l'application mobile (2026-11-03). */
public final class MobileExtraDtos {

    private MobileExtraDtos() {
    }

    // ---- profil
    public record ProfilePatchRequest(
            @Size(min = 2, max = 255, message = "Le nom doit contenir entre 2 et 255 caracteres") String fullName,
            @Email(message = "Adresse e-mail invalide") @Size(max = 150) String email
    ) {
    }

    public record ProfileView(String id, String fullName, String phone, String email) {
    }

    // ---- liens legaux
    public record LegalInfo(String termsUrl, String privacyUrl, String updatedAt) {
    }

    // ---- avis
    public record FeedbackRequest(
            @NotNull(message = "La note est obligatoire") @Min(value = 1, message = "La note va de 1 a 5") @Max(value = 5, message = "La note va de 1 a 5") Integer rating,
            @Size(max = 1000) String comment,
            String contractId
    ) {
    }

    public record FeedbackCreated(String reference) {
    }

    public record FeedbackAdminItem(Long id, Long userId, String phone, Long contractId, int rating, String comment, Instant createdAt) {
    }

    // ---- notifications
    public record NotificationItem(String id, String category, String title, String body, Instant createdAt, boolean read,
                                   String contractId, String claimId) {
    }

    public record NotificationPage(List<NotificationItem> items, long unreadCount, int page, int size, long totalElements) {
    }

    public record UnreadCount(long unreadCount) {
    }

    // ---- support
    public record Topic(String id, String label) {
    }

    public record TopicList(List<Topic> items) {
    }

    public record SupportCreateRequest(
            @NotBlank(message = "Le sujet est obligatoire") @Size(max = 40) String topic,
            @NotBlank(message = "Le message est obligatoire") @Size(min = 5, max = 2000, message = "Le message doit contenir entre 5 et 2000 caracteres") String message
    ) {
    }

    public record SupportCreated(String reference, Integer expectedWithinMinutes) {
    }

    public record SupportItem(String id, String reference, String topic, String topicLabel, String message, String status,
                              String response, Instant respondedAt, Instant createdAt) {
    }

    public record SupportList(List<SupportItem> items) {
    }

    public record FaqItem(String id, String question, String answer, int order) {
    }

    public record FaqList(List<FaqItem> items) {
    }

    // ---- appareils et preferences push
    public record DeviceRequest(
            @NotBlank(message = "Le jeton de l'appareil est obligatoire") @Size(max = 500) String token,
            @NotBlank(message = "La plateforme est obligatoire") @Pattern(regexp = "ANDROID|IOS", message = "platform doit valoir ANDROID ou IOS") String platform,
            @Size(max = 40) String appVersion
    ) {
    }

    public record DeviceView(String id, String platform, String appVersion) {
    }

    public record PreferenceItem(
            @NotBlank @Pattern(regexp = "EXPIRY|SUBSCRIPTION|PAYMENT|CLAIM|DOCUMENT|SERVICE", message = "category invalide") String category,
            @NotNull(message = "enabled est obligatoire") Boolean enabled
    ) {
    }

    public record PreferenceList(List<PreferenceItem> items) {
    }

    public record PreferenceUpdate(@NotNull @Size(min = 1, max = 6) List<@Valid PreferenceItem> items) {
    }

    /** Etat de la configuration push (administration). */
    public record PushStatus(boolean configured, boolean enabled, String projectId, boolean authenticated, String message) {
    }

    // ---- administration du support
    public record SupportAdminItem(Long id, String reference, Long userId, String phone, String topic, String message, String status,
                                   String response, Instant respondedAt, String respondedBy, Instant createdAt) {
    }

    public record SupportProcessRequest(
            @NotBlank @Pattern(regexp = "EN_COURS|REPONDU|CLOTURE", message = "statut doit valoir EN_COURS, REPONDU ou CLOTURE") String statut,
            @Size(max = 2000) String response
    ) {
    }

    public record FaqInput(
            @NotBlank(message = "La question est obligatoire") @Size(max = 300) String question,
            @NotBlank(message = "La reponse est obligatoire") @Size(max = 2000) String answer,
            @Min(0) @Max(9999) Integer order,
            Boolean visible
    ) {
    }

    /** Remplace TOUTE la FAQ (PUT). */
    public record FaqReplaceRequest(@NotNull @Size(max = 100, message = "100 questions au maximum") List<@Valid FaqInput> items) {
    }

    public record FaqAdminItem(Long id, String question, String answer, int order, boolean visible) {
    }

    public record FaqAdminList(List<FaqAdminItem> items) {
    }
}
