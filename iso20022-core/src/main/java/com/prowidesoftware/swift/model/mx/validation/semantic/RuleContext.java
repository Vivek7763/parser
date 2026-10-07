package com.prowidesoftware.swift.model.mx.validation.semantic;

public class RuleContext {
    private String activeProfile;

    public RuleContext(String activeProfile) {
        this.activeProfile = activeProfile;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }
}
