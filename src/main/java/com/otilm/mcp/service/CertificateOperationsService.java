package com.otilm.mcp.service;

public interface CertificateOperationsService {
    String getCertificateOperations(String authority, String profile);

    String getCertificateOperationAttributes(String authority, String profile, String operation, String values);

    String getCsrAttributes(String profile);

    String createCertificateRequest(String configuration);

    String issueCertificate(String authority, String profile, String configuration);

    String issueExistingCertificate(String authority, String profile, String certificate, String configuration);

    String registerCertificate(String authority, String profile, String configuration);

    String renewCertificate(String authority, String profile, String certificate, String configuration);

    String rekeyCertificate(String authority, String profile, String certificate, String configuration);

    String finalizeCertificate(String authority, String profile, String certificate, String configuration);

    String revokeCertificate(String authority, String profile, String certificate, String configuration);

    String confirmCertificateRevocation(String authority, String profile, String certificate);

    String cancelCertificateOperation(String authority, String profile, String certificate, String reason);

    String uploadCertificate(String certificate, String customAttributes, boolean asynchronous);

    String importCertificates(String configuration);

    String exportCertificate(String certificate, String format, String encoding, boolean chain,
            boolean includeEndCertificate);

    String exportCertificateKeystore(String certificate, String configuration);

    String updateCertificateMetadata(String certificate, String configuration);

    String setCertificateArchived(String certificate, boolean archived);

    String deleteCertificate(String certificate);

    String getCertificateRelations(String certificate);

    String setCertificateRelation(String certificate, String related, boolean associate);

    String getCertificateApprovals(String certificate, int page, int size);
}
