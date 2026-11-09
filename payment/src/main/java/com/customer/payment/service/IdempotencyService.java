package com.customer.payment.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.payment.domain.IdempotencyRecordEntity;
import com.customer.payment.domain.IdempotencyStatus;
import com.customer.payment.domain.PaymentStatus;
import com.customer.payment.dto.IdempotencyDecision;
import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.exception.IdempotencyKeyConflictException;
import com.customer.payment.exception.PaymentAlreadyInProgressException;
import com.customer.payment.exception.PreviousPaymentAttemptFailedException;
import com.customer.payment.repository.IdempotencyRecordRepository;

import tools.jackson.databind.ObjectMapper;

@Service 
public class IdempotencyService {

    @Autowired 
    private IdempotencyRecordRepository repository;

    @Autowired 
    private ObjectMapper mapper;

    public IdempotencyDecision checkAndRegisterPayment(String subject, String key, String requestHash) {
        
        Optional<IdempotencyRecordEntity> existingPayment = repository.findBySubjectAndIdempotencyKey(subject, key);

        if(existingPayment.isPresent()) {
            return evaluateExisting(existingPayment.get(), requestHash);
        }

        IdempotencyRecordEntity newPayment = 
            new IdempotencyRecordEntity(
                subject,
                key,
                requestHash,
                IdempotencyStatus.IN_PROGRESS,
                Instant.now()
            );
        
        try {

            repository.saveAndFlush(newPayment);
            return IdempotencyDecision.execute();

        } catch (DataIntegrityViolationException dive) {

            IdempotencyRecordEntity winner =
                repository
                    .findBySubjectAndIdempotencyKey(subject, key)
                    .orElseThrow(() -> dive);
            return evaluateExisting(winner, requestHash);
        }   
    }

    //as the method suggests this is evaluating an existing payment, so different payments are not compared here
    private IdempotencyDecision evaluateExisting(IdempotencyRecordEntity record, String requestHash) {

        //if the request hashes dont match - they are different payments, throw exception
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(record.getIdempotencyKey());
        }

        if(record.getStatus() == IdempotencyStatus.COMPLETED) {
            return IdempotencyDecision.cached(mapper.readValue(record.getResponseBody(), PaymentResponse.class));
        }

        if(record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new PaymentAlreadyInProgressException(record.getIdempotencyKey());
        }
        throw new PreviousPaymentAttemptFailedException(record.getIdempotencyKey());
    }

    @Transactional 
    public void fail(String subject, String idempotencyKey) {
        repository
            .findBySubjectAndIdempotencyKey(subject, idempotencyKey)
            .ifPresent(record -> {
                record.setStatus(IdempotencyStatus.FAILED);
                record.setUpdatedAt(Instant.now());
            });
    }

    @Transactional 
    public void complete(String subject, String idempotencyKey, PaymentResponse paymentResponse) {
        
        IdempotencyRecordEntity record = repository.findBySubjectAndIdempotencyKey(subject, idempotencyKey).orElseThrow();

        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setPaymentId(paymentResponse.paymentId());
        record.setResponseCode(paymentResponse.status() == PaymentStatus.SUCCEEDED ? HttpStatus.CREATED.value(): HttpStatus.UNPROCESSABLE_CONTENT.value());
        record.setResponseBody(mapper.writeValueAsString(paymentResponse));
        record.setUpdatedAt(Instant.now());
        
    }

}
