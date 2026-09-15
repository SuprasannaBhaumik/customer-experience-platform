package com.customer.profile.entity;

import com.customer.profile.enums.CustomerLanguage;
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

@Entity 
@Table(name="customer_preferences")
public class CustomerPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column (name="preference_id")
    private int preferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name="customer_size")
    private CustomerSize size;

    @ManyToOne 
    @JoinColumn(name="customer_id")
    private CustomerProfile customer;

    protected CustomerPreferences() {
        //no arg constructor for the JPA
    }

    public CustomerPreferences(CustomerSize size) {
        this.size = size;
    }

    public int getPreferenceId() {
        return this.preferenceId;
    }

    public void setPreferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
    }

    public CustomerSize getCustomerSize() {
        return this.size;
    }

    public void setPreferenceId(CustomerSize customerSize) {
        this.size = customerSize;
    }

    public CustomerProfile getCustomer() {
        return this.customer;
    }

    public void setCustomer(CustomerProfile customer) {
        this.customer = customer;
    }

}
