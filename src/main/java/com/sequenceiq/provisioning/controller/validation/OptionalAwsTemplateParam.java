package com.sequenceiq.provisioning.controller.validation;

public enum OptionalAwsTemplateParam implements TemplateParam {

    SSH_LOCATION("sshLocation", String.class);

    private final String paramName;
    private final Class clazz;

    private OptionalAwsTemplateParam(String paramName, Class clazz) {
        this.paramName = paramName;
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return paramName;
    }

    @Override
    public Class getClazz() {
        return clazz;
    }
}