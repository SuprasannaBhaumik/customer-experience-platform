package com.customer.profile.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import com.customer.profile.enums.CustomerSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity 
@Table(name="customer_preferences")
public class CustomerPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column (name="preference_id")
    @Setter(AccessLevel.NONE)
    private Long preferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name="customer_size")
    private CustomerSize size;

    @ManyToOne 
    @JoinColumn(name="customer_id")
    private CustomerProfile customer;

    public CustomerPreferences(CustomerSize size) {
        this.size = size;
    }

    public void setPreferenceId(Long preferenceId) {
        this.preferenceId = preferenceId;
    }

    public CustomerSize getCustomerSize() {
        return this.size;
    }

    public void setPreferenceId(CustomerSize customerSize) {
        this.size = customerSize;
    }

}
