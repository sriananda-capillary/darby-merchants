package com.capillary.spar.integration.service;
import com.capillary.spar.integration.model.GetMemberForLMRPayload;
import com.capillary.spar.integration.model.LMREnrollmentRequest;
import com.capillary.spar.integration.model.LMREnrollmentResult;
import com.sellerworx.darby.annotation.NoObfuscation;
import org.springframework.retry.annotation.Retryable;

@NoObfuscation
@Retryable(interceptor = "nonIdempotentMJRetryInterceptor")
public interface SparLMRUserEnrolledService {
     @Retryable(interceptor = "nonIdempotentMJRetryInterceptor")
     LMREnrollmentResult enrollNewUserToLandmark(LMREnrollmentRequest payloadData);
     @Retryable(interceptor = "nonIdempotentMJRetryInterceptor")
     LMREnrollmentResult getMemberForLMR(GetMemberForLMRPayload payloadData);
}
